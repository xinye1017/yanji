package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiRadius

/**
 * 卡片层级变体（语义化区分卡片圆角与层级，消除局部 magic number）：
 * - [Hero]     页面顶层主看板、倒计时大卡（28dp，DESIGN.md large hero card）
 * - [Standard] 标准容器卡片（24dp，DESIGN.md standard card，默认）
 * - [Compact]  列表条目卡片、嵌套子卡片、紧凑信息块（16dp）
 */
enum class YanjiCardVariant {
    Hero,
    Standard,
    Compact
}

fun YanjiCardVariant.toShape(): Shape = when (this) {
    YanjiCardVariant.Hero -> RoundedCornerShape(YanjiRadius.HeroCardRadius)
    YanjiCardVariant.Standard -> RoundedCornerShape(YanjiRadius.StandardCardRadius)
    YanjiCardVariant.Compact -> RoundedCornerShape(YanjiRadius.CompactCardRadius)
}

/**
 * App-wide card primitive. Every standard card receives the same quiet edge so
 * surfaces remain distinguishable across light and dark modes, and consistent
 * rounded geometry family based on semantic variant.
 */
@Composable
fun YanjiCard(
    modifier: Modifier = Modifier,
    variant: YanjiCardVariant = YanjiCardVariant.Standard,
    shape: Shape = variant.toShape(),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    variant: YanjiCardVariant = YanjiCardVariant.Standard,
    shape: Shape = variant.toShape(),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
