package com.example.yanji.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
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

val YanjiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun YanjiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = YanjiShapes,
        content = content
    )
}
