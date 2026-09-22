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
import androidx.compose.foundation.selection.selectable
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
 * 分段控制器承载场景变体：
 * - [InCard] 位于卡片容器内部（Card Surface 垫底）：外轨凹槽微柔内敛，药丸与卡片表面质感相呼应；
 * - [OnPage] 位于页面背景上（Page Background 垫底）：外轨具备独立对比度，确保在全屏背景上边界轮廓清晰醒目。
 */
enum class YanjiSegmentedControlVariant {
    InCard,
    OnPage
}

/**
 * 研迹统一分段控制器 (YanjiSegmentedControl)
 *
 * 统一标准：采用 iOS/macOS 现代设计系统级的「微投影悬浮药丸」方案：
 * 1. 外层凹槽底轨：根据 [variant] 自动适配卡片内（InCard）或页面级（OnPage）对比度；
 * 2. 滑动指示器：纯白/深色浮层悬浮药丸，配合浅柔阴影与高光细描边，物理弹簧曲线（YanjiMotion.NavigationSpring）平滑位移；
 * 3. 文本排版：选中项为鲜明主题蓝（primary，Bold），未选中项为柔和次级灰（onSurfaceVariant，Normal），
 *    文字颜色伴随弹簧平滑过渡；
 * 4. 无障碍支持：完整适配 Role.Tab 与 selected 语义树。
 */
@Composable
fun <T> YanjiSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: YanjiSegmentedControlVariant = YanjiSegmentedControlVariant.InCard,
    height: Dp = 40.dp,
    shape: Shape = CircleShape,
    itemLabel: (T) -> String = { it.toString() },
    itemModifier: (T, Int) -> Modifier = { _, _ -> Modifier }
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

    val trackBackground = when (variant) {
        YanjiSegmentedControlVariant.InCard -> {
            if (isDark) {
                Color.Black.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        }
        YanjiSegmentedControlVariant.OnPage -> {
            if (isDark) {
                Color(0xFF1A2333) // 独立于页面夜幕深黑底 #0D111A 的清晰次表面深蓝轨
            } else {
                Color(0xFFE2E7EE) // 独立于页面浅灰底 #F2F4F7 的清晰凹槽灰轨
            }
        }
    }

    val trackBorderModifier = when (variant) {
        YanjiSegmentedControlVariant.InCard -> {
            if (isDark) Modifier
            else Modifier.border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                shape
            )
        }
        YanjiSegmentedControlVariant.OnPage -> {
            if (isDark) Modifier
            else Modifier.border(
                1.dp,
                Color.Black.copy(alpha = 0.05f),
                shape
            )
        }
    }

    val pillBackground = when (variant) {
        YanjiSegmentedControlVariant.InCard -> {
            if (isDark) {
                MaterialTheme.colorScheme.surfaceBright
            } else {
                MaterialTheme.colorScheme.surface
            }
        }
        YanjiSegmentedControlVariant.OnPage -> {
            if (isDark) {
                Color(0xFF2A374F) // 浮于 #1A2333 轨道上的提亮药丸，与主题蓝字 #4F7DF3 对比鲜明
            } else {
                MaterialTheme.colorScheme.surface
            }
        }
    }

    val pillElevation = when (variant) {
        YanjiSegmentedControlVariant.InCard -> if (isDark) 3.dp else 2.dp
        YanjiSegmentedControlVariant.OnPage -> if (isDark) 4.dp else 2.5.dp
    }

    val pillBorderModifier = when (variant) {
        YanjiSegmentedControlVariant.InCard -> {
            if (isDark) Modifier
            else Modifier.border(1.dp, Color.White.copy(alpha = 0.70f), shape)
        }
        YanjiSegmentedControlVariant.OnPage -> {
            if (isDark) Modifier
            else Modifier.border(1.dp, Color.White.copy(alpha = 0.85f), shape)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(trackBackground)
            .then(trackBorderModifier)
            .padding(3.dp)
            .height(height)
    ) {
        val totalWidth = maxWidth
        val count = items.size
        val itemWidth = totalWidth / count.toFloat()
        val indicatorOffset = itemWidth * animatedPosition.value

        // 滑动胶囊指示器（表面纯白/夜幕蓝，微投影与高光描边）
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = pillElevation,
                    shape = shape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.08f),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.12f)
                )
                .clip(shape)
                .background(pillBackground)
                .then(pillBorderModifier)
        )

        // 交互项文字
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val textStyle = if (height <= 34.dp) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            }

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
                        .then(itemModifier(item, index))
                        .clip(shape)
                        .selectable(
                            selected = isSelected,
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
                        style = textStyle,
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
