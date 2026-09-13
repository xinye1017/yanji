package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiBorder
import com.example.yanji.theme.YanjiRadius

private val DefaultYanjiCardBorder = BorderStroke(1.dp, YanjiBorder)

/**
 * Standard card geometry — DESIGN.md「Shapes」: standard card = 24px。
 *
 * 固定为常量而非 `CardDefaults.shape`，因为 Material3 的默认 shape 走
 * `Shapes.medium`（本项目为 16dp），会让标准卡片比设计稿更方。
 */
private val DefaultYanjiCardShape: Shape = RoundedCornerShape(YanjiRadius.StandardCardRadius)

/**
 * App-wide card primitive. Every standard card receives the same quiet edge so
 * white surfaces remain distinguishable from the near-white page background,
 * and the same 24dp corner radius so every screen shares one soft geometry family.
 */
@Composable
fun YanjiCard(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultYanjiCardShape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = DefaultYanjiCardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    MaterialCard(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content
    )
}

@Composable
fun YanjiCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = DefaultYanjiCardShape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = DefaultYanjiCardBorder,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    MaterialCard(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
        content = content
    )
}
