package com.example.yanji.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * M3 `ColorScheme` **装不下**的那几个语义色。
 *
 * M3 的 colorScheme 只提供 primary / secondary / tertiary / surface / outline / error 这条主线，
 * 而本项目的设计系统还有几个真实存在、且亮暗必须各有一套值的角色：
 *
 *  - [textTertiary]  三级文字（提示、禁用、注脚）—— M3 没有三级文本槽；
 *  - [warning] / [warningSoft] 警示色 —— M3 只有 error，没有 warning；
 *  - [lavenderDeep]  浅紫容器上的深紫前景 —— 对比度要求与 secondary 不同；
 *  - [surfaceBlue]   极浅蓝 / 夜幕蓝特征卡 —— 与 surface 明度不同。
 *
 * 这几个角色如果继续用顶层 `val`（编译期常量），暗色模式下就会一直是亮色值，
 * 界面直接「暗底配亮字块」。因此把它们收进本调色板，由 `YanjiTheme` 随主题一起提供。
 *
 * 取值与亮色既有 token 完全一致（[LightExtraColors]），所以**不影响任何亮色视觉**。
 */
@Immutable
data class YanjiExtraColors(
    val textTertiary: Color,
    val warning: Color,
    val warningSoft: Color,
    val lavenderDeep: Color,
    val surfaceBlue: Color
)

/** 亮色实例：与既有 `YanjiTextTertiary` / `YanjiWarning` 等**逐值相同**。 */
internal val LightExtraColors = YanjiExtraColors(
    textTertiary = YanjiTextTertiary,
    warning = YanjiWarning,
    warningSoft = YanjiWarningSoft,
    lavenderDeep = YanjiLavenderDeep,
    surfaceBlue = YanjiSurfaceBlue
)

/** 暗色实例：design_dark.md §colors 的对应值。 */
internal val DarkExtraColors = YanjiExtraColors(
    textTertiary = YanjiDarkTextTertiary,
    warning = YanjiDarkWarning,
    warningSoft = YanjiDarkWarningSoft,
    lavenderDeep = YanjiDarkLavenderDeep,
    surfaceBlue = YanjiDarkSurfaceBlue
)

internal val LocalYanjiExtraColors = staticCompositionLocalOf { LightExtraColors }

/**
 * 取用扩展调色板。用法：`YanjiColors.warning`。
 *
 * 刻意做成 `object` + `@Composable get()` 而不是 `CompositionLocal.current.xxx`，
 * 是为了让调用点保持 `YanjiColors.xxx` 这样短且可读的形态。
 */
object YanjiColors {
    val textTertiary: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.textTertiary

    val warning: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.warning

    val warningSoft: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.warningSoft

    val lavenderDeep: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.lavenderDeep

    val surfaceBlue: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.surfaceBlue
}

// ---------------------------------------------------------------------------
// 学科序列色（design_dark.md §3.4）
// ---------------------------------------------------------------------------

/**
 * 亮色序列色 hex → 暗色序列色。
 *
 * 为什么在这里做映射：学科色的**唯一事实来源是数据层**（`SubjectCatalog.colorHex`，
 * 一个 hex 字符串，落库、进统计、进备份）。让数据层感知主题是错的架构方向，
 * 所以在**渲染层**做一次「亮色 hex → 当前主题色」的翻译：暗色下按 design_dark.md
 * §3.4 升调，未登记的色值原样返回（自定义科目不丢色）。
 */
internal val DarkSeriesColors: Map<String, Color> = mapOf(
    "356AE6" to SubjectMathDark,      // 数学一
    "6F91EA" to SubjectMajorDark,     // 408 专业课
    "8B7CF6" to SubjectEnglishDark,   // 英语一
    "7CB6D9" to SubjectPoliticsDark,  // 思想政治
    "B8C6DF" to SubjectOtherDark      // 其他
)

/**
 * 把（来自数据层的）学科色 hex 解析成当前主题下应显示的颜色。
 *
 * @param hex 形如 `#356AE6` / `356AE6`；未登记或解析失败时返回 [fallback]。
 */
@Composable
@ReadOnlyComposable
fun yanjiSeriesColor(hex: String, fallback: Color): Color {
    if (!LocalYanjiDarkTheme.current) return fallback
    return DarkSeriesColors[hex.removePrefix("#").trim().uppercase()] ?: fallback
}

/** 亮色序列色 token → 暗色序列色 token，供直接使用 token 的调用点取色。 */
@Composable
@ReadOnlyComposable
fun yanjiSeriesToken(light: Color): Color {
    if (!LocalYanjiDarkTheme.current) return light
    return DarkSeriesColors[light.toHexKey()] ?: light
}

private fun Color.toHexKey(): String =
    "%02X%02X%02X".format(
        (red * 255f + 0.5f).toInt(),
        (green * 255f + 0.5f).toInt(),
        (blue * 255f + 0.5f).toInt()
    )
