package com.example.yanji.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val LightColorScheme = lightColorScheme(
    primary = YanjiPrimary,
    onPrimary = YanjiOnPrimary,
    primaryContainer = YanjiPrimarySoft,
    onPrimaryContainer = YanjiPrimaryStrong,
    secondary = YanjiLavender,
    onSecondary = YanjiOnPrimary,
    secondaryContainer = YanjiLavenderSoft,
    onSecondaryContainer = YanjiLavender,
    tertiary = YanjiSuccess,
    onTertiary = YanjiOnPrimary,
    tertiaryContainer = YanjiSuccessSoft,
    onTertiaryContainer = YanjiSuccess,
    background = YanjiBackground,
    onBackground = YanjiTextPrimary,
    surface = YanjiSurface,
    onSurface = YanjiTextPrimary,
    surfaceVariant = YanjiSurfaceSoft,
    onSurfaceVariant = YanjiTextSecondary,
    outline = YanjiBorder,
    outlineVariant = YanjiDivider,
    error = YanjiDanger,
    errorContainer = YanjiDangerSoft
)

internal val DarkColorScheme = darkColorScheme(
    primary = YanjiDarkPrimary,
    onPrimary = YanjiOnPrimary,
    primaryContainer = YanjiDarkPrimarySoft,
    onPrimaryContainer = YanjiDarkPrimaryStrong,
    secondary = YanjiDarkLavender,
    onSecondary = YanjiOnPrimary,
    secondaryContainer = YanjiDarkLavenderSoft,
    onSecondaryContainer = YanjiDarkLavender,
    tertiary = YanjiDarkSuccess,
    onTertiary = YanjiOnPrimary,
    tertiaryContainer = YanjiDarkSuccessSoft,
    onTertiaryContainer = YanjiDarkSuccess,
    background = YanjiDarkBackground,
    onBackground = YanjiDarkTextPrimary,
    surface = YanjiDarkSurface,
    onSurface = YanjiDarkTextPrimary,
    surfaceVariant = YanjiDarkSurfaceSoft,
    onSurfaceVariant = YanjiDarkTextSecondary,
    outline = YanjiDarkBorder,
    outlineVariant = YanjiDarkDivider,
    error = YanjiDarkDanger,
    errorContainer = YanjiDarkDangerSoft,
    // design_dark.md §1.2：暗色靠「表面明度递进」而不是阴影表达海拔层级。
    surfaceDim = YanjiDarkBackground,
    surfaceBright = YanjiDarkSurfaceFloating,
    surfaceContainerLowest = YanjiDarkBackground,
    surfaceContainerLow = YanjiDarkSurface,
    surfaceContainer = YanjiDarkSurfaceSoft,
    surfaceContainerHigh = YanjiDarkSurfaceFloating,
    surfaceContainerHighest = YanjiDarkSurfaceFloating
)

private fun MascotThemeSpec.lightScheme() = lightColorScheme(
    primary = palette.lightPrimary,
    onPrimary = YanjiOnPrimary,
    primaryContainer = palette.lightPrimarySoft,
    onPrimaryContainer = palette.lightPrimaryStrong,
    secondary = palette.lightSecondary,
    onSecondary = YanjiOnPrimary,
    secondaryContainer = palette.lightSecondarySoft,
    onSecondaryContainer = palette.lightSecondaryStrong,
    tertiary = YanjiSuccess,
    onTertiary = YanjiOnPrimary,
    tertiaryContainer = YanjiSuccessSoft,
    onTertiaryContainer = YanjiSuccess,
    background = palette.lightBackground,
    onBackground = YanjiTextPrimary,
    surface = YanjiSurface,
    onSurface = YanjiTextPrimary,
    surfaceVariant = YanjiSurfaceSoft,
    onSurfaceVariant = YanjiTextSecondary,
    outline = YanjiBorder,
    outlineVariant = YanjiDivider,
    error = YanjiDanger,
    errorContainer = YanjiDangerSoft
)

private fun MascotThemeSpec.darkScheme() = darkColorScheme(
    primary = palette.darkPrimary,
    onPrimary = YanjiOnPrimary,
    primaryContainer = palette.darkPrimarySoft,
    onPrimaryContainer = palette.darkPrimaryStrong,
    secondary = palette.darkSecondary,
    onSecondary = YanjiOnPrimary,
    secondaryContainer = palette.darkSecondarySoft,
    onSecondaryContainer = palette.darkSecondaryStrong,
    tertiary = YanjiDarkSuccess,
    onTertiary = YanjiOnPrimary,
    tertiaryContainer = YanjiDarkSuccessSoft,
    onTertiaryContainer = YanjiDarkSuccess,
    background = palette.darkBackground,
    onBackground = YanjiDarkTextPrimary,
    surface = YanjiDarkSurface,
    onSurface = YanjiDarkTextPrimary,
    surfaceVariant = YanjiDarkSurfaceSoft,
    onSurfaceVariant = YanjiDarkTextSecondary,
    outline = YanjiDarkBorder,
    outlineVariant = YanjiDarkDivider,
    error = YanjiDarkDanger,
    errorContainer = YanjiDarkDangerSoft,
    surfaceDim = palette.darkBackground,
    surfaceBright = YanjiDarkSurfaceFloating,
    surfaceContainerLowest = palette.darkBackground,
    surfaceContainerLow = YanjiDarkSurface,
    surfaceContainer = YanjiDarkSurfaceSoft,
    surfaceContainerHigh = YanjiDarkSurfaceFloating,
    surfaceContainerHighest = YanjiDarkSurfaceFloating
)

/**
 * M3 `Shapes` 标尺的**定义处**——这是全项目唯一允许出现裸 dp 圆角的地方。
 *
 * 它刻意**不**与 [YanjiRadius] 的角色 token 互相引用：M3 的 extraSmall/small/medium/large/
 * extraLarge 是一套与产品语义无关的框架刻度，而 [YanjiRadius] 表达的是「为什么用这个圆角」。
 * 把两者绑在一起，会让改动产品角色 token 时悄悄改掉 M3 基线。
 */
val YanjiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 主题模式：跟随系统 / 强制浅色 / 强制深色。
 *
 * 持久化在 `user_settings.themeMode`（存 [name] 字符串，与 `aiProvider` 等同为 String 字段，
 * 便于 Room 迁移与旧备份兼容）。未知值一律回退 [SYSTEM]，保证前/后向兼容都不崩。
 */
enum class YanjiThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT = SYSTEM

        /** 宽松解析：null / 空串 / 历史遗留值 / 未来新增值 全部安全回退到 [SYSTEM]。 */
        fun fromStorage(raw: String?): YanjiThemeMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * 当前是否处于暗色主题。
 *
 * 为什么需要它：M3 的 `ColorScheme` 只覆盖语义色槽，而 design_dark.md 还规定了一批
 * **暗色专属值**（液态玻璃底栏面板、专注圆环自发光、学科序列色、AI 紫雾卡）。
 * 这些值无法塞进 ColorScheme，统一通过本 CompositionLocal 判定后再取色，
 * 保证「亮色视觉 100% 不变、暗色按规范单独呈现」。
 */
val LocalYanjiDarkTheme = staticCompositionLocalOf { false }

@Composable
fun yanjiIsDarkTheme(): Boolean = LocalYanjiDarkTheme.current

/** 按当前主题在「亮色值 / 暗色值」之间取色。 */
@Composable
fun yanjiThemeColor(light: Color, dark: Color): Color =
    if (LocalYanjiDarkTheme.current) dark else light

/** 把主题模式解析为「当前是否使用暗色」。 */
@Composable
fun YanjiThemeMode.resolveDarkTheme(): Boolean = when (this) {
    YanjiThemeMode.SYSTEM -> isSystemInDarkTheme()
    YanjiThemeMode.LIGHT -> false
    YanjiThemeMode.DARK -> true
}

@Composable
fun YanjiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceTransparency: Boolean = false,
    mascotTheme: MascotThemeSpec = MascotThemes.CLOUD,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) mascotTheme.darkScheme() else mascotTheme.lightScheme()
    val glassTokens = when {
        reduceTransparency -> ReducedGlassTokens
        darkTheme -> DarkGlassTokens
        else -> LightGlassTokens
    }
    val extraColors = if (darkTheme) {
        DarkExtraColors.copy(
            lavenderDeep = mascotTheme.palette.darkSecondaryStrong,
            surfaceBlue = mascotTheme.palette.darkSurfaceBlue,
            secondaryFill = mascotTheme.palette.darkPrimarySoft,
            accent = mascotTheme.palette.darkPrimary
        )
    } else {
        LightExtraColors.copy(
            lavenderDeep = mascotTheme.palette.lightSecondaryStrong,
            surfaceBlue = mascotTheme.palette.lightSurfaceBlue,
            secondaryFill = mascotTheme.palette.lightPrimarySoft,
            accent = mascotTheme.palette.lightPrimary
        )
    }
    CompositionLocalProvider(
        LocalYanjiDarkTheme provides darkTheme,
        LocalYanjiExtraColors provides extraColors,
        LocalMascotTheme provides mascotTheme,
        LocalLiquidGlassTokens provides glassTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = YanjiShapes,
            content = content
        )
    }
}


