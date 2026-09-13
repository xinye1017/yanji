package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.*

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
        selected -> Triple(YanjiPrimary, YanjiOnPrimary, null)
        style == YanjiChipStyle.PRIMARY -> Triple(YanjiPrimarySoft, YanjiPrimaryStrong, YanjiBorder)
        style == YanjiChipStyle.LAVENDER -> Triple(YanjiLavenderSoft, YanjiLavenderDeep, null)
        style == YanjiChipStyle.SUCCESS -> Triple(YanjiSuccessSoft, YanjiSuccess, null)
        style == YanjiChipStyle.WARNING -> Triple(YanjiWarningSoft, YanjiWarning, null)
        style == YanjiChipStyle.DANGER -> Triple(YanjiDangerSoft, YanjiDanger, null)
        else -> Triple(YanjiSurface, YanjiTextSecondary, YanjiBorder)
    }

    val shape = RoundedCornerShape(YanjiRadius.ChipRadius)
    var boxModifier = modifier
        .clip(shape)
        .background(backgroundColor)
    if (borderColor != null) {
        boxModifier = boxModifier.border(BorderStroke(1.dp, borderColor), shape)
    }
    if (onClick != null) {
        boxModifier = boxModifier.clickable(onClick = onClick)
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
