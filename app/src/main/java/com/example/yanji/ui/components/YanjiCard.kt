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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiCardBorder
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.rememberPressScale

/**
 * 卡片层级变体（语义化区分卡片圆角与层级，消除局部 magic number）：
 * - [Hero]     页面顶层主看板、倒计时大卡（28dp，DESIGN.md large hero card）
 * - [Standard] 标准容器卡片（24dp，DESIGN.md standard card，默认）
 * - [Grouped]  分组容器卡片、图表设置大卡（20dp，YanjiRadius.GroupedCardRadius）
 * - [Compact]  列表条目卡片、嵌套子卡片、紧凑信息块（16dp）
 */
enum class YanjiCardVariant {
    Hero,
    Standard,
    Grouped,
    Compact
}

fun YanjiCardVariant.toShape(): Shape = when (this) {
    YanjiCardVariant.Hero -> RoundedCornerShape(YanjiRadius.HeroCardRadius)
    YanjiCardVariant.Standard -> RoundedCornerShape(YanjiRadius.StandardCardRadius)
    YanjiCardVariant.Grouped -> RoundedCornerShape(YanjiRadius.GroupedCardRadius)
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
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = YanjiCardBorder.stroke(),
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
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = YanjiCardBorder.stroke(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val currentInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val scaleState = rememberPressScale(currentInteractionSource)
    MaterialCard(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scaleState.value
            scaleY = scaleState.value
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = currentInteractionSource,
        content = content
    )
}
