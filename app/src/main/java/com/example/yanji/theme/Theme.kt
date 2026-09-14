package com.example.yanji.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    errorContainer = YanjiDarkDangerSoft
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

@Composable
fun YanjiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = YanjiShapes,
        content = content
    )
}

