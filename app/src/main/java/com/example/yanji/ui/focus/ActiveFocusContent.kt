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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.Role
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
import com.example.yanji.theme.rememberPressScale
import com.example.yanji.ui.components.YanjiPrimaryButton
import com.example.yanji.ui.components.YanjiSecondaryButton
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
    isPaused: Boolean = session.status == SessionStatus.PAUSED,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    FocusSystemBarAppearance()
    val targetSeconds = FocusModes.targetSeconds(session.mode)
    var showCancelDialog by rememberSaveable(session.id) { mutableStateOf(false) }
    val timerColor by animateColorAsState(
        targetValue = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        animationSpec = tween(360),
        label = "focusTimerColor"
    )
    val onTogglePauseResume = remember(isPaused, onPause, onResume) {
        {
            if (isPaused) onResume() else onPause()
        }
    }

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
                    CountdownFocusBody(
                        elapsedSeconds = elapsedSeconds,
                        targetSeconds = targetSeconds,
                        timerColor = timerColor,
                        onTogglePauseResume = onTogglePauseResume
                    )
                } else {
                    FlowFocusBody(
                        elapsedSeconds = elapsedSeconds,
                        isPaused = isPaused,
                        timerColor = timerColor,
                        onTogglePauseResume = onTogglePauseResume
                    )
                }
                FocusControls(
                    isPaused = isPaused,
                    elapsedSeconds = elapsedSeconds,
                    onTogglePauseResume = onTogglePauseResume,
                    onFinish = onFinish,
                    onCancel = { showCancelDialog = true }
                )
            }
        ) { measurables, constraints ->
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
            val (header, body, controls) = measurables.map { it.measure(childConstraints) }
            val gap = 24.dp.roundToPx()
            val height = maxOf(viewportHeight, header.height + body.height + controls.height + gap * 2)
            val controlsTop = height - controls.height
            // 顶部锚点取可用高度的 9%，但不越过「内容本来就放得下」时的上限。
            val headerTop = minOf((height * 0.09f).toInt(), (height - controls.height - body.height - header.height).coerceAtLeast(0))
            // 主视觉居中于「header 底部」与「controls 顶部」之间的剩余空间，
            // 而不是钉在固定的 46%——后者在大屏上会留下两条很宽的空白带。
            val regionTop = headerTop + header.height + gap
            val regionBottom = controlsTop - gap
            val bodyTop = (regionTop + (regionBottom - regionTop - body.height) / 2)
                .coerceIn(regionTop, (regionBottom - body.height).coerceAtLeast(regionTop))
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

/** 专注环境系统栏跟随应用主题明暗，保证状态栏与导航栏图标在深浅色下均清晰可见。 */
@Composable
private fun FocusSystemBarAppearance() {
    val activity = LocalActivity.current
    val view = LocalView.current
    val isDark = yanjiIsDarkTheme()
    DisposableEffect(activity, view, isDark) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousStatusAppearance = controller?.isAppearanceLightStatusBars
        val previousNavigationAppearance = controller?.isAppearanceLightNavigationBars
        // 亮色主题使用深色图标（light bars = true），深色主题使用浅色图标（light bars = false）
        controller?.isAppearanceLightStatusBars = !isDark
        controller?.isAppearanceLightNavigationBars = !isDark
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
private fun CountdownFocusBody(
    elapsedSeconds: State<Long>,
    targetSeconds: Long,
    timerColor: Color,
    onTogglePauseResume: () -> Unit
) {
    // 第一个读取点：秒数只在这里被订阅，重组范围止于本分支。
    val elapsed = elapsedSeconds.value.coerceIn(0L, targetSeconds)
    val progress = elapsed.toFloat() / targetSeconds
    val displayedProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "focusCountdownProgress"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource, targetScale = 0.96f)

    // 底轨颜色：浅色下为 10% 主蓝，深色下为 8% 白，保证在两种背景上都真正看得见。
    val trackRingColor = yanjiThemeColor(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        Color.White.copy(alpha = 0.08f)
    )

    Column(
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = timerColor),
                    role = Role.Button,
                    onClick = onTogglePauseResume
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize()) {
                val strokeWidth = 5.dp.toPx()
                val inset = strokeWidth / 2
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                // 统一底轨绘制（深浅色仅有 trackRingColor 颜色不同，几何线宽完全一致）
                drawCircle(
                    color = trackRingColor,
                    radius = (size.minDimension - strokeWidth) / 2,
                    style = Stroke(strokeWidth)
                )

                // 统一进度弧绘制（深浅色均由 timerColor 纯净承载，暂停时均平滑过渡，无非对称渐变接缝）
                // 用 Butt 端帽而不是 Round：起始瞬间不会出现一颗停在 12 点方向的「小圆珠」。
                // 阈值取 0.5% 而非 0：避免浮点极小值画出几乎不可见的碎片。
                if (displayedProgress.value >= 0.005f) {
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

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FocusTimeText(targetSeconds - elapsed, timerColor, baseFontSize = 60)
                // 只标注「这个大数字是什么」，不再重复播报暂停状态：
                // 暂停已由顶部 header（已暂停）+ 主按钮（继续专注）+ 环变灰三处共同表达。
                Text(
                    text = "剩余时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FlowFocusBody(
    elapsedSeconds: State<Long>,
    isPaused: Boolean,
    timerColor: Color,
    onTogglePauseResume: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource, targetScale = 0.96f)

    Box(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = timerColor),
                role = Role.Button,
                onClick = onTogglePauseResume
            ),
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
            if (isPaused) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = timerColor
                    )
                    Text(
                        text = "已暂停",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "持续专注中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
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
    elapsedSeconds: State<Long>,
    onTogglePauseResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 只在本叶子读秒数：不足 1 分钟的「完成」与主按钮文案都需要它，
    // 但读取范围止于本函数，不会牵连 Header / 计时体。
    val recordedEnough = elapsedSeconds.value >= MIN_RECORDED_SECONDS
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 暂停 / 继续是专注时的主控件，不再隐藏在「点击圆环」里。
        YanjiPrimaryButton(
            text = if (isPaused) "继续专注" else "暂停",
            onClick = onTogglePauseResume,
            modifier = Modifier.fillMaxWidth(),
            icon = {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        // 不足 1 分钟时把「完成」置灰并说明原因，而不是点下去才静默丢弃：
        // 同一个按钮的语义永远只能是「保存」，不能一念之间变成「不保存」。
        if (!recordedEnough) {
            Text(
                text = "已专注不足 1 分钟，完成后不会生成记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YanjiSecondaryButton(
                text = "放弃",
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            YanjiSecondaryButton(
                text = "完成",
                onClick = onFinish,
                enabled = recordedEnough,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 最短可保存时长；与 `TimerStore.MIN_RECORDED_FOCUS_SECONDS` 保持同一条业务规则。 */
private const val MIN_RECORDED_SECONDS = 60L

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
