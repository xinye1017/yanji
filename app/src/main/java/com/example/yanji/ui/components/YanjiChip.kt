package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.rememberPressScale

enum class YanjiChipStyle {
    PRIMARY,
    LAVENDER,
    SURFACE,
    SUCCESS,
    WARNING,
    DANGER
}

/**
 * 研迹标准 Pill 形状标签组件（DESIGN.md chip-blue / chip-lavender）。
 */
@Composable
fun YanjiChip(
    text: String,
    modifier: Modifier = Modifier,
    style: YanjiChipStyle = YanjiChipStyle.PRIMARY,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val (backgroundColor, textColor, borderColor) = when {
        selected -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, null)
        style == YanjiChipStyle.PRIMARY -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.outline)
        style == YanjiChipStyle.LAVENDER -> Triple(MaterialTheme.colorScheme.secondaryContainer, YanjiColors.lavenderDeep, null)
        style == YanjiChipStyle.SUCCESS -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary, null)
        style == YanjiChipStyle.WARNING -> Triple(YanjiColors.warningSoft, YanjiColors.warning, null)
        style == YanjiChipStyle.DANGER -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, null)
        else -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outline)
    }

    val shape = RoundedCornerShape(YanjiRadius.ChipRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = if (onClick != null) rememberPressScale(interactionSource, targetScale = 0.96f) else 1f

    var boxModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(backgroundColor)
    if (borderColor != null) {
        boxModifier = boxModifier.border(BorderStroke(1.dp, borderColor), shape)
    }
    if (onClick != null) {
        boxModifier = boxModifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            role = Role.Button,
            onClick = onClick
        )
    }

    Box(
        modifier = boxModifier.padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
