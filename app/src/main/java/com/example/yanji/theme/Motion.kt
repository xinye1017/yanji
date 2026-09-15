package com.example.yanji.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * StudyOS 统一动效系统参数 (YanjiMotion)
 *
 * 集中管理 Spring 弹性物理阻尼、转场渐变与微交互缩放，
 * 确保全应用交互响应具有一致的有机流体质感。
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

    const val CrossfadeMs = 180
    const val DataRevealMs = 280
}

/**
 * 统一的按压轻微弹性缩放动效（默认 0.97f），用于卡片与控制器的即时触觉反馈
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.97f
): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = YanjiMotion.ControlSpring,
        label = "pressScale"
    )
    return scale
}
