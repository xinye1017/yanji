package com.example.yanji.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiMotion
import com.example.yanji.theme.yanjiIsDarkTheme

/**
 * 统一分段控制器 (GlassSegmentedControl)
 *
 * 1. 灵动滑动胶囊：使用物理弹簧曲线跟随选中项平滑位移，并带有柔和阴影与边缘微反光；
 * 2. 视觉对比分明：亮色模式下通透立体、暗色模式下深邃收敛，文字对比度完全满足 WCAG AA 标准；
 * 3. 完整无障碍：集成 Role.Tab 与 selected 语义，TalkBack 可精准朗读选项及选中状态。
 */
@Composable
fun <T> GlassSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    shape: Shape = CircleShape,
    itemLabel: (T) -> String = { it.toString() }
) {
    if (items.isEmpty()) return

    val isDark = yanjiIsDarkTheme()
    val animatedPosition = remember { Animatable(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex) {
        animatedPosition.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = YanjiMotion.NavigationSpring
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(
                if (isDark) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                }
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.15f else 0.40f),
                shape
            )
            .padding(3.dp)
            .height(height)
    ) {
        val totalWidth = maxWidth
        val count = items.size
        val itemWidth = totalWidth / count.toFloat()
        val indicatorOffset = itemWidth * animatedPosition.value

        // 滑动胶囊指示器
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (isDark) 4.dp else 2.dp,
                    shape = shape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f),
                    spotColor = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f)
                )
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (isDark) {
                        Color.White.copy(alpha = 0.08f)
                    } else {
                        Color.White.copy(alpha = 0.70f)
                    },
                    shape
                )
        )

        // 交互项文字
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 500f),
                    label = "segmentedTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .semantics {
                            this.selected = isSelected
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (index != selectedIndex) {
                                    onItemSelected(index)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
