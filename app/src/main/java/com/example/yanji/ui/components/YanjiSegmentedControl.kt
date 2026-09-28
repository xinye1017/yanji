package com.example.yanji.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
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
import com.example.yanji.theme.YanjiDarkSegmentPillOnPage
import com.example.yanji.theme.YanjiDarkSegmentTrackOnPage
import com.example.yanji.theme.YanjiMotion
import com.example.yanji.theme.YanjiSegmentTrackOnPage
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
    val reduceMotion = YanjiMotion.isReduceMotionEnabled()
    val animatedPosition = remember { Animatable(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex) {
        animatedPosition.animateTo(
            targetValue = selectedIndex.toFloat(),
            // 滑块位移是真正的「运动」，而弹簧不吃系统动画时长缩放 → 关闭动画时瞬切到位。
            // 文字颜色过渡不是位移，保留原样。
            animationSpec = if (reduceMotion) snap() else YanjiMotion.NavigationSpring
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
            if (isDark) YanjiDarkSegmentTrackOnPage
            else YanjiSegmentTrackOnPage
        }
    }

    // 轨道**没有描边**。此前这里挂了一条 1dp `outline` 实线，理由写的是
    // 「WCAG 1.4.11 要求控件边界可辨识」——真机反馈这条实线又硬又脏。
    // 那条判据用错了地方：分段控件的边界靠「轨道填充 vs 页面底」表达，
    // 选中态靠「悬浮药丸 + 阴影」表达，都不需要再叠一条线。
    // 轨道填充已相应加深（见 YanjiSegmentTrackOnPage），保证无描边时边界依然读得出。

    val pillBackground = when (variant) {
        YanjiSegmentedControlVariant.InCard -> {
            if (isDark) {
                MaterialTheme.colorScheme.surfaceBright
            } else {
                MaterialTheme.colorScheme.surface
            }
        }
        YanjiSegmentedControlVariant.OnPage -> {
            if (isDark) YanjiDarkSegmentPillOnPage
            else MaterialTheme.colorScheme.surface
        }
    }

    val pillElevation = when (variant) {
        YanjiSegmentedControlVariant.InCard -> if (isDark) 3.dp else 2.dp
        YanjiSegmentedControlVariant.OnPage -> if (isDark) 4.dp else 2.5.dp
    }

    // 药丸同样不描边。原先亮色挂了一条白色高光边（白 @70%/85%），
    // 在纯白药丸上等于一条多余的硬线；药丸的立体感由 [pillElevation] 的阴影 + 与轨道的
    // 填充色差承担。暗色本来就是 `Modifier`（无描边），这里统一成无描边。

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(trackBackground)
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
