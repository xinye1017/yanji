package com.example.yanji.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext

/**
 * StudyOS 统一动效系统参数 (YanjiMotion)
 *
 * 集中管理 Spring 弹性物理阻尼、转场渐变与微交互缩放，
 * 确保全应用交互响应具有一致的有机流体质感。
 * 遵循 iOS HIG 与 Android 无障碍规范，提供完善的 Reduce Motion 降级支持。
 */
object YanjiMotion {
    /** 控件级弹簧（按钮、分段选择、Tab 滑块等） */
    val ControlSpring = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 520f
    )

    /** 导航浮岛级弹簧（Dock 缩放、长距离指示器滑块） */
    val NavigationSpring = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 420f
    )

    /** 轻柔平滑弹簧（数字滚动、图表状态切换） */
    val GentleSpring = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = 380f
    )

    /** 无障碍减少动态（Reduce Motion）下的无回弹快速过渡 */
    val ReducedSpec = tween<Float>(durationMillis = 100)

    const val CrossfadeMs = 180
    const val DataRevealMs = 280

    /**
     * 检查系统是否开启了“减少动态效果”或将系统动画时长调整为 0。
     */
    @Composable
    fun isReduceMotionEnabled(): Boolean {
        val context = LocalContext.current
        return try {
            val resolver = context.contentResolver
            val animatorScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transitionScale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            animatorScale == 0f || transitionScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 根据当前无障碍偏好提供自适应动画规范。
     */
    @Composable
    fun <T> accessibleSpring(
        dampingRatio: Float = 0.86f,
        stiffness: Float = 420f,
        visibilityThreshold: T? = null
    ): AnimationSpec<T> {
        return if (isReduceMotionEnabled()) {
            snap()
        } else {
            spring(dampingRatio = dampingRatio, stiffness = stiffness, visibilityThreshold = visibilityThreshold)
        }
    }
}

/**
 * 统一的按压轻微弹性缩放动效（默认 0.97f），用于卡片与控制器的即时触觉反馈。
 * 当系统开启 Reduce Motion 时，自动禁用缩放位移以避免诱发眩晕。
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.97f
): Float {
    val reduceMotion = YanjiMotion.isReduceMotionEnabled()
    if (reduceMotion) return 1f

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = YanjiMotion.ControlSpring,
        label = "pressScale"
    )
    return scale
}
