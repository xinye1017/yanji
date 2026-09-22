package com.example.yanji.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** 与科目时长分布、今日学习目标一致的胶囊进度条。 */
@Composable
fun YanjiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 8.dp
) {
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
    ) {
        val radius = size.height / 2f
        val corner = CornerRadius(radius, radius)
        drawRoundRect(color = trackColor, cornerRadius = corner)

        val filledWidth = size.width * fraction
        if (filledWidth > 0f) {
            val filledRadius = min(radius, filledWidth / 2f)
            drawRoundRect(
                color = color,
                size = Size(filledWidth, size.height),
                cornerRadius = CornerRadius(filledRadius, filledRadius)
            )
        }
    }
}
