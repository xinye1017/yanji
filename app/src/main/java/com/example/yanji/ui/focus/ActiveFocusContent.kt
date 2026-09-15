package com.example.yanji.ui.focus

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.timer.formatFocusClock
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * 专注环境全屏页。
 *
 * 性能约定（规范 §37）：每秒推进的秒数通过 [State] 传入，**在本函数体内不读取**，
 * 只在真正要显示数字的叶子节点（倒计时体 / 正向计时体）里读 `.value`。
 * 这样每秒只会重组那两个小分支，Header、Controls、自定义 Layout 的构图逻辑不会被牵连。
 * 若在这里直接 `elapsedSeconds.value`，整页（含 `BoxWithConstraints` 与手工定位的
 * `Layout`）每秒都会重新组合 + 重新测量。
 *
 * Presentation only: all timer and persistence actions remain with FocusScreen.
 */
@Composable
fun ActiveFocusContent(
    session: FocusSession,
    elapsedSeconds: State<Long>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    FocusSystemBarAppearance()
    val isPaused = session.status == SessionStatus.PAUSED
    val targetSeconds = FocusModes.targetSeconds(session.mode)
    var showCancelDialog by rememberSaveable(session.id) { mutableStateOf(false) }
    val timerColor by animateColorAsState(
        targetValue = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        animationSpec = tween(360),
        label = "focusTimerColor"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        val viewportHeight = with(LocalDensity.current) { maxHeight.roundToPx() }
        // Measure real text/control heights before placing the three anchors. On a short
        // window or large font, the canvas grows and scrolls instead of overlapping actions.
        Layout(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            content = {
                FocusSessionHeader(session, targetSeconds, isPaused)
                if (targetSeconds > 0L) {
                    CountdownFocusBody(elapsedSeconds, targetSeconds, isPaused, timerColor)
                } else {
                    FlowFocusBody(elapsedSeconds, isPaused, timerColor)
                }
                FocusControls(
                    isPaused = isPaused,
                    onPause = onPause,
                    onResume = onResume,
                    onFinish = onFinish,
                    onCancel = { showCancelDialog = true }
                )
            }
        ) { measurables, constraints ->
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
            val (header, body, controls) = measurables.map { it.measure(childConstraints) }
            val gap = 24.dp.roundToPx()
            val height = maxOf(viewportHeight, header.height + body.height + controls.height + gap * 2)
            val spareHeight = height - header.height - body.height - controls.height - gap * 2
            val headerTop = minOf((height * 0.10f).toInt(), spareHeight)
            val controlsTop = height - controls.height
            val bodyTop = ((height * 0.46f).toInt() - body.height / 2).coerceIn(
                headerTop + header.height + gap,
                controlsTop - gap - body.height
            )
            layout(constraints.maxWidth, height) {
                header.placeRelative((constraints.maxWidth - header.width) / 2, headerTop)
                body.placeRelative((constraints.maxWidth - body.width) / 2, bodyTop)
                controls.placeRelative((constraints.maxWidth - controls.width) / 2, controlsTop)
            }
        }
    }

    if (showCancelDialog) {
        FocusCancelDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                showCancelDialog = false
                onCancel()
            }
        )
    }
}

/** This focus environment uses a light canvas even when Android itself is in dark mode. */
@Composable
private fun FocusSystemBarAppearance() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousStatusAppearance = controller?.isAppearanceLightStatusBars
        val previousNavigationAppearance = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            previousStatusAppearance?.let { controller.isAppearanceLightStatusBars = it }
            previousNavigationAppearance?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

@Composable
private fun FocusSessionHeader(session: FocusSession, targetSeconds: Long, isPaused: Boolean) {
    val statusColor by animateColorAsState(
        targetValue = if (isPaused) YanjiColors.warning else MaterialTheme.colorScheme.primary,
        animationSpec = tween(360),
        label = "focusStatusColor"
    )
    Column(
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(6.dp).background(statusColor, CircleShape))
            Text(
                text = when {
                    isPaused -> "已暂停"
                    targetSeconds > 0L -> "倒计时 · ${targetSeconds / 60} 分钟"
                    else -> "正向专注"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = session.subjectName,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (session.note.isNotBlank()) {
            Text(
                text = session.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CountdownFocusBody(elapsedSeconds: State<Long>, targetSeconds: Long, isPaused: Boolean, timerColor: Color) {
    // 第一个读取点：秒数只在这里被订阅，重组范围止于本分支。
    val elapsed = elapsedSeconds.value.coerceIn(0L, targetSeconds)
    val progress = elapsed.toFloat() / targetSeconds
    val displayedProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "focusCountdownProgress"
    )
    Column(
        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier.widthIn(max = 228.dp).fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // design_dark.md §3.3：亮色底轨是 8% 主蓝；暗色换成 6% 白，避免在深底上发闷。
            val darkRing = yanjiIsDarkTheme()
            val trackRingColor = yanjiThemeColor(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                YanjiDarkTrack
            )
            val ringGlowColor = if (darkRing) YanjiDarkPrimaryGlow else Color.Transparent
            // 暗色运行态：圆环由主蓝渐变到微光紫；暂停态仍走警示色，语义不丢。
            val ringBrush = if (darkRing && !isPaused) {
                Brush.sweepGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            } else {
                null
            }
            Canvas(Modifier.matchParentSize()) {
                val strokeWidth = 4.dp.toPx()
                val inset = strokeWidth / 2
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                if (ringGlowColor != Color.Transparent) {
                    // 夜间自发光呼吸微感：整圈铺一层更宽的低透明蓝，形成环境柔光。
                    drawCircle(
                        color = ringGlowColor,
                        radius = (size.minDimension - strokeWidth) / 2,
                        style = Stroke(strokeWidth * 2.6f)
                    )
                }
                drawCircle(
                    color = trackRingColor,
                    radius = (size.minDimension - strokeWidth) / 2,
                    style = Stroke(strokeWidth)
                )
                if (displayedProgress.value > 0f) {
                    // Butt ends preserve a genuinely tiny starting arc instead of a round dot.
                    if (ringBrush != null) {
                        drawArc(
                            brush = ringBrush,
                            startAngle = -90f,
                            sweepAngle = displayedProgress.value * 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                        )
                    } else {
                        drawArc(
                            color = timerColor,
                            startAngle = -90f,
                            sweepAngle = displayedProgress.value * 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FocusTimeText(targetSeconds - elapsed, timerColor, baseFontSize = 52)
                Text(
                    text = if (isPaused) "计时已暂停" else "剩余时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            text = "已完成 ${(elapsed * 100 / targetSeconds).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FlowFocusBody(elapsedSeconds: State<Long>, isPaused: Boolean, timerColor: Color) {
    Box(
        modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth().heightIn(min = 300.dp),
        contentAlignment = Alignment.Center
    ) {
        FlowBreathingField(isPaused, Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 第二个读取点：正向计时的时间数字。
            FocusTimeText(elapsedSeconds.value, timerColor, baseFontSize = 60)
            Text(
                text = if (isPaused) "计时已暂停" else "持续专注中",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FlowBreathingField(isPaused: Boolean, modifier: Modifier = Modifier) {
    val phase = remember { Animatable(if (isPaused) 0f else -1f) }
    val presence = animateFloatAsState(
        targetValue = if (isPaused) 0.2f else 1f,
        animationSpec = tween(360),
        label = "flowPresence"
    )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(isPaused, lifecycle) {
        val motionScale = currentCoroutineContext()[MotionDurationScale]
        snapshotFlow { motionScale?.scaleFactor ?: 1f }.collectLatest { scale ->
            if (scale <= 0f) {
                // A disabled system animator must not turn an infinite tween into a busy loop.
                phase.snapTo(0f)
            } else {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    if (isPaused) {
                        phase.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                    } else {
                        while (isActive) {
                            phase.animateTo(1f, tween(3600, easing = FastOutSlowInEasing))
                            phase.animateTo(-1f, tween(3600, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
        }
    }
    val glowColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier.graphicsLayer {
            // Read animation values in the drawing layer: no per-frame sizing or re-layout.
            scaleX = 1f + phase.value * 0.015f
            scaleY = scaleX
            alpha = (0.09f + phase.value * 0.03f) * presence.value
        }
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to glowColor,
                0.4f to glowColor.copy(alpha = 0.35f),
                1f to glowColor.copy(alpha = 0f),
                center = center,
                radius = size.minDimension / 2
            ),
            radius = size.minDimension / 2
        )
    }
}

@Composable
private fun FocusTimeText(seconds: Long, color: Color, baseFontSize: Int) {
    val time = formatFocusClock(seconds)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.displaySmall.copy(
        fontSize = baseFontSize.sp,
        lineHeight = (baseFontSize * 1.2f).sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum"
    )
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Fit against a stable digit template, so 59:59 -> 1:00:00 stays readable on
        // narrow windows and large system fonts without making every second change size.
        val template = time.map { if (it.isDigit()) '8' else it }.joinToString("")
        val fontSize = remember(template, maxWidth, density, baseStyle) {
            val naturalWidth = measurer.measure(template, baseStyle, maxLines = 1, softWrap = false).size.width
            val availableWidth = with(density) { maxWidth.toPx() }
            (baseFontSize * minOf(1f, availableWidth / naturalWidth.coerceAtLeast(1))).sp
        }
        Text(
            text = time,
            style = baseStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.2f),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun FocusControls(
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = if (isPaused) onResume else onPause,
            modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isPaused) "继续专注" else "暂停", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("结束并保存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("放弃本次记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FocusCancelDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("放弃本次专注？", fontWeight = FontWeight.SemiBold) },
        text = { Text("放弃后，本次计时时长将不予保存。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认放弃", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("保留记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
