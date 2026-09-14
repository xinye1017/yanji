package com.example.yanji.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val LightColorScheme = lightColorScheme(
    primary = YanjiPrimary,
    onPrimary = YanjiOnPrimary,
    primaryContainer = YanjiPrimarySoft,
    onPrimaryContainer = YanjiPrimaryStrong,
    secondary = YanjiLavender,
    onSecondary = Color.White,
    secondaryContainer = YanjiLavenderSoft,
    onSecondaryContainer = YanjiLavender,
    tertiary = YanjiSuccess,
    onTertiary = Color.White,
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
    onPrimary = Color.White,
    primaryContainer = YanjiDarkPrimarySoft,
    onPrimaryContainer = YanjiDarkPrimaryStrong,
    secondary = YanjiDarkLavender,
    onSecondary = Color.White,
    secondaryContainer = YanjiDarkLavenderSoft,
    onSecondaryContainer = YanjiDarkLavender,
    tertiary = YanjiDarkSuccess,
    onTertiary = Color.White,
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

