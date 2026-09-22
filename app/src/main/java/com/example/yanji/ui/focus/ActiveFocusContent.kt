package com.example.yanji.ui.focus

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.timer.FocusPreferences
import com.example.yanji.data.timer.formatFocusClock
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.rememberPressScale
import com.example.yanji.ui.components.YanjiPrimaryButton
import com.example.yanji.ui.components.YanjiSecondaryButton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    val focusPrefs = remember(context) { FocusPreferences.getInstance(context) }
    val autoPowerSavingEnabled by focusPrefs.autoPowerSavingEnabled.collectAsStateWithLifecycle()
    val timeoutSeconds by focusPrefs.timeoutSeconds.collectAsStateWithLifecycle()

    val targetSeconds = FocusModes.targetSeconds(session.mode)
    var showCancelDialog by rememberSaveable(session.id) { mutableStateOf(false) }
    var lastInteractionElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var isPowerSavingMode by rememberSaveable(session.id) { mutableStateOf(false) }

    val timerColor by animateColorAsState(
        targetValue = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        animationSpec = tween(360),
        label = "focusTimerColor"
    )
    val onTogglePauseResume = remember(isPaused, onPause, onResume) {
        {
            lastInteractionElapsedMs = SystemClock.elapsedRealtime()
            if (isPaused) onResume() else onPause()
        }
    }

    // 根据用户设置自动检测静置无触碰时长，超时自动进入省电模式（§37 零额外 Compose 重组）
    LaunchedEffect(isPaused, isPowerSavingMode, showCancelDialog, autoPowerSavingEnabled, timeoutSeconds) {
        if (autoPowerSavingEnabled && !isPaused && !isPowerSavingMode && !showCancelDialog) {
            val timeoutMs = timeoutSeconds * 1000L
            while (isActive) {
                delay(1000L)
                val idleMs = SystemClock.elapsedRealtime() - lastInteractionElapsedMs
                if (idleMs >= timeoutMs) {
                    isPowerSavingMode = true
                }
            }
        }
    }

    val handleCancelRequest = {
        if (elapsedSeconds.value < 60L) {
            onCancel()
        } else {
            showCancelDialog = true
        }
    }

    BackHandler(enabled = true) {
        if (isPowerSavingMode) {
            isPowerSavingMode = false
        } else if (showCancelDialog) {
            showCancelDialog = false
        } else {
            handleCancelRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionElapsedMs = SystemClock.elapsedRealtime()
                    }
                }
            }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val density = LocalDensity.current
            val screenHeightPx = with(density) { maxHeight.roundToPx() }
            val statusBarTop = WindowInsets.statusBars.getTop(density)
            // Measure real text/control heights before placing the three anchors. On a short
            // window or large font, the canvas grows and scrolls instead of overlapping actions.
            Layout(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                content = {
                    FocusSessionHeader(
                        session = session,
                        targetSeconds = targetSeconds,
                        isPaused = isPaused,
                        onEnterPowerSaving = {
                            lastInteractionElapsedMs = SystemClock.elapsedRealtime()
                            isPowerSavingMode = true
                        }
                    )
                    if (targetSeconds > 0L) {
                        CountdownFocusBody(
                            elapsedSeconds = elapsedSeconds,
                            targetSeconds = targetSeconds,
                            timerColor = timerColor,
                            isPaused = isPaused,
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
                        onFinish = onFinish,
                        onCancel = handleCancelRequest
                    )
                }
            ) { measurables, constraints ->
                val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
                val (header, body, controls) = measurables.map { it.measure(childConstraints) }
                val gap = 16.dp.roundToPx()
                val minTotalHeight = header.height + body.height + controls.height + gap * 2
                val height = maxOf(screenHeightPx, minTotalHeight)
                val controlsTop = height - controls.height
                val headerTop = 0
                // 精准设备屏幕物理中心计算（Mathematical Device Centering）：
                // 外层容器已被 Navigation.kt 的 statusBarsPadding() 占位向下推移了 statusBarTop 像素。
                // 整台设备物理屏幕的物理中心为 (screenHeightPx + statusBarTop) / 2。
                // 因此圆环在局部容器内的精确位置必须扣除顶部状态栏的占位：
                // idealBodyTop = (H_device - body.height) / 2 - statusBarTop
                //              = (screenHeightPx - body.height - statusBarTop) / 2
                // 这保证了从手机物理顶边到圆环顶部的距离，与从圆环底部到手机物理底边的距离像素级绝对相等。
                val idealBodyTop = (screenHeightPx - body.height - statusBarTop) / 2
                val minBodyTop = headerTop + header.height + gap
                val maxBodyTop = controlsTop - body.height - gap
                val bodyTop = if (minBodyTop <= maxBodyTop) {
                    idealBodyTop.coerceIn(minBodyTop, maxBodyTop)
                } else {
                    minBodyTop
                }
                layout(constraints.maxWidth, height) {
                    header.placeRelative((constraints.maxWidth - header.width) / 2, headerTop)
                    body.placeRelative((constraints.maxWidth - body.width) / 2, bodyTop)
                    controls.placeRelative((constraints.maxWidth - controls.width) / 2, controlsTop)
                }
            }
        }

        AnimatedVisibility(
            visible = isPowerSavingMode,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(300))
        ) {
            PowerSavingFocusOverlay(
                targetSeconds = targetSeconds,
                elapsedSeconds = elapsedSeconds,
                onWakeUp = {
                    lastInteractionElapsedMs = SystemClock.elapsedRealtime()
                    isPowerSavingMode = false
                }
            )
        }
    }


    if (showCancelDialog) {
        FocusCancelDialog(
            session = session,
            elapsedSeconds = elapsedSeconds.value,
            onDismiss = { showCancelDialog = false },
            onSaveAndFinish = {
                showCancelDialog = false
                onFinish()
            },
            onConfirmDiscard = {
                showCancelDialog = false
                onCancel()
            }
        )
    }
}

/** 专注计时无触碰自动进入省电模式的静置时长阈值：30 秒。 */
internal const val INACTIVITY_TIMEOUT_MS = 30_000L


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
private fun FocusSessionHeader(
    session: FocusSession,
    targetSeconds: Long,
    isPaused: Boolean,
    onEnterPowerSaving: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (isPaused) MaterialTheme.colorScheme.error else YanjiColors.success,
        animationSpec = tween(360),
        label = "focusStatusIndicatorColor"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第一行：学科名称与全屏按钮在同一行，背景透明
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = session.subjectName,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            IconButton(
                onClick = onEnterPowerSaving,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "全屏沉浸省电",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 第二行：计时状态（位于学科名称下方）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(6.dp).background(indicatorColor, CircleShape))
            Text(
                text = when {
                    isPaused -> "已暂停"
                    targetSeconds > 0L -> "倒计时"
                    else -> "计时中"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

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
    isPaused: Boolean,
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
                Text(
                    text = if (isPaused) "已暂停 · 点击继续" else "剩余时间",
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
        FlowBreathingField(isPaused, Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                        text = "已暂停 · 点击继续",
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
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YanjiSecondaryButton(
            text = "放弃",
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        YanjiPrimaryButton(
            text = "完成",
            onClick = onFinish,
            modifier = Modifier.weight(1f)
        )
    }
}


@Composable
private fun FocusCancelDialog(
    session: FocusSession,
    elapsedSeconds: Long,
    onDismiss: () -> Unit,
    onSaveAndFinish: () -> Unit,
    onConfirmDiscard: () -> Unit
) {
    val durationText = formatFocusClock(elapsedSeconds)
    val totalMinutes = elapsedSeconds / 60
    val durationSummary = if (totalMinutes >= 60) {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        "${h}小时 ${m}分钟"
    } else {
        "${totalMinutes}分钟"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "放弃本次专注？",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = session.subjectName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "已专注 $durationSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "如需保留当前已专注时长，请点击「保存并结束」；若直接放弃，本次计时将不予保存。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YanjiPrimaryButton(
                    text = "保存并结束",
                    onClick = onSaveAndFinish,
                    modifier = Modifier.fillMaxWidth()
                )
                YanjiSecondaryButton(
                    text = "继续专注",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onConfirmDiscard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                ) {
                    Text(
                        text = "放弃不保存",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        shape = RoundedCornerShape(YanjiRadius.DialogRadius),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
