package com.example.yanji.ui.focus

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.yanji.data.timer.formatFocusClock
import com.example.yanji.theme.YanjiPowerSavingBackground
import com.example.yanji.theme.YanjiPowerSavingProgress
import com.example.yanji.theme.YanjiPowerSavingText
import com.example.yanji.theme.YanjiPowerSavingTrack

/**
 * 专注计时自动省电模式全屏蒙层（Power Saving Mode）。
 *
 * 核心特性：
 * 1. 全屏纯黑背景：基于 OLED 极黑（#000000）实现像素熄灭级省电；
 * 2. 隐藏系统栏：通过 [WindowInsetsControllerCompat] 隐藏状态栏与导航栏，避免烧屏并增强沉浸感；
 * 3. 屏幕常亮：维持 [android.view.View.setKeepScreenOn] 为 true，便于置于桌面自习时作为极简时钟随时查阅；
 * 4. 触碰唤醒：整屏消费任意点击手势，轻触即刻退出省电模式，避免误触底层按钮；
 * 5. 性能隔离（规范 §37）：[elapsedSeconds] 只在 [PowerSavingTimeText] 与 [PowerSavingProgressBar] 叶子节点解包，
 *    整屏容器与动画结构每秒零重组。
 */
@Composable
fun PowerSavingFocusOverlay(
    targetSeconds: Long,
    elapsedSeconds: State<Long>,
    onWakeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    PowerSavingScreenAndBarController()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiPowerSavingBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onWakeUp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PowerSavingTimeText(
                targetSeconds = targetSeconds,
                elapsedSeconds = elapsedSeconds
            )

            Spacer(modifier = Modifier.height(24.dp))

            PowerSavingProgressBar(
                targetSeconds = targetSeconds,
                elapsedSeconds = elapsedSeconds
            )
        }

        Text(
            text = "轻触屏幕任意位置退出",
            style = MaterialTheme.typography.labelMedium,
            color = YanjiPowerSavingText.copy(alpha = 0.25f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 28.dp)
        )
    }
}

/** 控制全屏沉浸与屏幕常亮 */
@Composable
private fun PowerSavingScreenAndBarController() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        val insetsController = if (window != null) WindowCompat.getInsetsController(window, view) else null
        val prevBehavior = insetsController?.systemBarsBehavior

        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (prevBehavior != null) {
                insetsController.systemBarsBehavior = prevBehavior
            }
            view.keepScreenOn = false
        }
    }
}

/** 叶子节点：倒计时 / 正向时间数字展示 */
@Composable
private fun PowerSavingTimeText(
    targetSeconds: Long,
    elapsedSeconds: State<Long>
) {
    val elapsed = elapsedSeconds.value
    val displaySeconds = if (targetSeconds > 0L) {
        (targetSeconds - elapsed).coerceAtLeast(0L)
    } else {
        elapsed
    }
    val time = formatFocusClock(displaySeconds)

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseFontSize = 76
    val baseStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = baseFontSize.sp,
        lineHeight = (baseFontSize * 1.15f).sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val template = time.map { if (it.isDigit()) '8' else it }.joinToString("")
        val fontSize = remember(template, maxWidth, density, baseStyle) {
            val naturalWidth = measurer.measure(template, baseStyle, maxLines = 1, softWrap = false).size.width
            val availableWidth = with(density) { maxWidth.toPx() }
            (baseFontSize * minOf(1f, availableWidth / naturalWidth.coerceAtLeast(1))).sp
        }

        Text(
            text = time,
            style = baseStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.15f),
            color = YanjiPowerSavingText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** 叶子节点：极简线性进度条 */
@Composable
private fun PowerSavingProgressBar(
    targetSeconds: Long,
    elapsedSeconds: State<Long>
) {
    val progress = if (targetSeconds > 0L) {
        (elapsedSeconds.value.toFloat() / targetSeconds).coerceIn(0f, 1f)
    } else {
        1f
    }
    val displayedProgress = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "powerSavingProgress"
    )

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(YanjiPowerSavingTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(displayedProgress.value)
                .clip(CircleShape)
                .background(YanjiPowerSavingProgress)
        )
    }
}
