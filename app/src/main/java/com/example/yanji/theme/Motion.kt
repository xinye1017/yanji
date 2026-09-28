package com.example.yanji.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.ui.platform.LocalContext

/**
 * StudyOS 统一动效系统参数 (YanjiMotion)
 *
 * 集中管理 Spring 弹性物理阻尼、转场渐变与微交互缩放，
 * 确保全应用交互响应具有一致的有机流体质感。
 * 遵循 iOS HIG 与 Android 无障碍规范，提供完善的 Reduce Motion 降级支持。
 */
object YanjiMotion {
    /**
     * 时长档位（ms）。取自全站现有用值的归并结果，刻意只有 5 档：
     * 此前 ui/ 层散落着 23 个互不相同的时长字面量（39 处 `tween(`），
     * 任何一次「把动效调快/调慢/关掉」都要改几十个地方。
     * 新过渡请从这里取值；守卫脚本 `check-design-tokens.sh` 的时长棘轮会阻止裸 `tween(` 继续增长。
     */
    const val DurationMicro = 100     // 按压反馈、图标微动
    const val DurationFast = 180      // 小范围显隐
    const val DurationStandard = 280  // 默认过渡（模式切换、滑块）
    const val DurationEmphasis = 360  // 需要被注意到的状态变化
    const val DurationAmbient = 1200  // 环境级循环（呼吸、进度滴答）

    /** 折线图「绘制」揭示：路径沿长度推进，560ms 走完全程，足够看清又不拖沓。 */
    const val DurationLineDraw = 560

    /** 柱状图单根「上升」：320ms 走完一根。 */
    const val DurationBarRise = 320

    /**
     * 柱状图逐根错峰步长。60ms × 6 根 = 360ms，加上单根 320ms，整排约 680ms 落定。
     *
     * 为什么从 26ms 提到 60ms：26ms 下一排柱子几乎同时起跳，整排像一整块板在升降，
     * 看不出「逐根」的意思。60ms 是一眼能分辨先后、又不显得拖的步长。
     */
    const val BarStaggerStep = 60

    /**
     * 揭示专用缓动：两端慢、中间快（`cubic-bezier(0.4, 0, 0.2, 1)`）。
     *
     * 不用 EaseEntering：那条曲线开头极快，80% 的位移挤在前 20% 的时间里，
     * 几帧内就冲完，肉眼跟不住。缓入缓出能让上升过程铺满整段时间。
     */
    val EaseReveal = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** 缓动档位：进入用 Decelerate、离开用 Accelerate、一般用 Standard，均为 Material 曲线。 */
    val EaseStandard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val EaseEntering = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val EaseExiting = CubicBezierEasing(0.4f, 0f, 1f, 1f)

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
     * 系统是否开启了「降低透明度」（Android 12+ 开发者选项 / 无障碍设置）。
     *
     * 为什么必须存在：本应用的液态玻璃（Dock、卡片、弹层）默认靠半透明 + 背景模糊表达层级，
     * 而 `ReducedGlassTokens`（零模糊 + 92% 不透明）此前从未被任何调用方取用 —— 参数写在
     * `YanjiTheme` 上却没人传，等于这个无障碍档位只存在于源码里。
     *
     * `reduce_transparency` 在 API 31 之前不存在，读不到即视为未开启（返回 false），不做降级猜测。
     */
    @Composable
    fun isReduceTransparencyEnabled(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
        val context = LocalContext.current
        return try {
            Settings.Global.getInt(context.contentResolver, "reduce_transparency", 0) == 1
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

    /**
     * 时长-缓动档位，图表揭示专用（与 [accessibleSpring] 同构，只是把弹簧换成定时曲线）。
     *
     * 为什么放在 theme/ 而不是 ui/：`check-design-tokens.sh` 的时长棘轮
     * （`PATTERN_TWEEN='tween\('`，`TWEEN_CEILING=48`）只扫 `app/.../ui/` 目录。
     * 档位集中定义在这里，ui/ 里就只剩 [accessibleTween] 一个入口，
     * 棘轮计数不会因为新增图表动效而增长。
     *
     * 为什么图表揭示不用弹簧：弹簧会过冲，而路径长度推进和柱高推进是**有明确终点**
     * 的量纲，过冲会让折线画过头、柱子冲过顶再弹回。定时曲线在终点处稳稳停住。
     *
     * 开启系统「减少动态效果」时返回 [snap]，图表直接以终态出现，不做空间位移。
     */
    @Composable
    fun <T> accessibleTween(
        durationMillis: Int = DurationStandard,
        easing: Easing = EaseEntering
    ): AnimationSpec<T> {
        return if (isReduceMotionEnabled()) {
            snap()
        } else {
            tween(durationMillis = durationMillis, easing = easing)
        }
    }
}

/**
 * 统一的按压轻微弹性缩放动效（默认 0.97f），用于卡片与控制器的即时触觉反馈。
 * 当系统开启 Reduce Motion 时，自动禁用缩放位移以避免诱发眩晕。
 *
 * 返回 [State] 而不是裸 Float：调用方只在 `graphicsLayer { }` 里读 `.value`，
 * 弹簧的逐帧变化就只走绘制阶段。若在组合期读成 Float，每帧都会让整棵内容子树重组。
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.97f
): State<Float> {
    val reduceMotion = YanjiMotion.isReduceMotionEnabled()
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (!reduceMotion && isPressed) targetScale else 1f,
        animationSpec = YanjiMotion.ControlSpring,
        label = "pressScale"
    )
}
