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
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val lavenderDeep: Color,
    val surfaceBlue: Color,
    // --- iOS-inspired Semantic Roles ---
    val elevatedSurface: Color = YanjiElevatedSurface,
    val primaryLabel: Color = YanjiTextPrimary,
    val secondaryLabel: Color = YanjiTextSecondary,
    val tertiaryLabel: Color = YanjiTextTertiary,
    val quaternaryLabel: Color = YanjiQuaternaryLabel,
    val separator: Color = YanjiDivider,
    val opaqueSeparator: Color = YanjiBorder,
    val fill: Color = YanjiSurfaceSoft,
    val secondaryFill: Color = YanjiPrimarySoft,
    val accent: Color = YanjiPrimary
)

/** 亮色实例：与既有 `YanjiTextTertiary` / `YanjiWarning` 等**逐值相同**。 */
internal val LightExtraColors = YanjiExtraColors(
    textTertiary = YanjiTextTertiary,
    success = YanjiSuccess,
    successSoft = YanjiSuccessSoft,
    warning = YanjiWarning,
    warningSoft = YanjiWarningSoft,
    lavenderDeep = YanjiLavenderDeep,
    surfaceBlue = YanjiSurfaceBlue,
    elevatedSurface = YanjiElevatedSurface,
    primaryLabel = YanjiTextPrimary,
    secondaryLabel = YanjiTextSecondary,
    tertiaryLabel = YanjiTextTertiary,
    quaternaryLabel = YanjiQuaternaryLabel,
    separator = YanjiDivider,
    opaqueSeparator = YanjiBorder,
    fill = YanjiSurfaceSoft,
    secondaryFill = YanjiPrimarySoft,
    accent = YanjiPrimary
)

/** 暗色实例：design_dark.md §colors 的对应值。 */
internal val DarkExtraColors = YanjiExtraColors(
    textTertiary = YanjiDarkTextTertiary,
    success = YanjiDarkSuccess,
    successSoft = YanjiDarkSuccessSoft,
    warning = YanjiDarkWarning,
    warningSoft = YanjiDarkWarningSoft,
    lavenderDeep = YanjiDarkLavenderDeep,
    surfaceBlue = YanjiDarkSurfaceBlue,
    elevatedSurface = YanjiDarkSurface,
    primaryLabel = YanjiDarkTextPrimary,
    secondaryLabel = YanjiDarkTextSecondary,
    tertiaryLabel = YanjiDarkTextTertiary,
    quaternaryLabel = YanjiDarkQuaternaryLabel,
    separator = YanjiDarkDivider,
    opaqueSeparator = YanjiDarkBorder,
    fill = YanjiDarkSurfaceSoft,
    secondaryFill = YanjiDarkPrimarySoft,
    accent = YanjiDarkPrimary
)

internal val LocalYanjiExtraColors = staticCompositionLocalOf { LightExtraColors }

/**
 * 取用扩展调色板。用法：`YanjiColors.warning`、`YanjiColors.elevatedSurface`。
 *
 * 刻意做成 `object` + `@Composable get()` 而不是 `CompositionLocal.current.xxx`，
 * 是为了让调用点保持 `YanjiColors.xxx` 这样短且可读的形态。
 */
object YanjiColors {
    val textTertiary: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.textTertiary

    val success: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.success

    val successSoft: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.successSoft

    val warning: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.warning

    val warningSoft: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.warningSoft

    val lavenderDeep: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.lavenderDeep

    val surfaceBlue: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.surfaceBlue

    // --- iOS-inspired Semantic Roles ---
    val elevatedSurface: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.elevatedSurface

    val primaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.primaryLabel

    val secondaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.secondaryLabel

    val tertiaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.tertiaryLabel

    val quaternaryLabel: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.quaternaryLabel

    val separator: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.separator

    val opaqueSeparator: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.opaqueSeparator

    val fill: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.fill

    val secondaryFill: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.secondaryFill

    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalYanjiExtraColors.current.accent
}
