package com.example.yanji.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.yanjiIsDarkTheme

/**
 * 研迹统一分段导航栏 (YanjiSegmentedControl)
 *
 * 统一标准：以「设置专注节奏」分段导航栏样式为基准，背景色为纯实色。
 * 1. 外层轨道：纯实色背景（亮色柔和浅灰实色 Color(0xFFECEFF5)，暗色为实色 surfaceVariant Color(0xFF1C2331)），
 *    无半透明透出，外加精致轮廓边框；
 * 2. 滑动指示器：饱满的主题色 (primary) 实色圆角胶囊，附带柔和环境微光与投影 (shadow)，
 *    采用高响应阻尼物理弹簧曲线跟随平滑位移；
 * 3. 文本排版：选中项高对比反白 (onPrimary, SemiBold)，未选中项自然次级色 (onSurfaceVariant, Medium)，
 *    颜色切换平滑过渡；
 * 4. 无障碍支持：完整适配 Role.Tab 与 selected 语义树。
 */
@Composable
fun <T> YanjiSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    shape: Shape = CircleShape,
    itemLabel: (T) -> String = { it.toString() }
) {
    if (items.isEmpty()) return

    val isDark = yanjiIsDarkTheme()
    // 纯实色背景（杜绝 alpha 半透明透出）
    val trackColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color(0xFFECEFF5)
    }
    val trackBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.70f)

    val trackPadding = 4.dp
    val segmentSpacing = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(trackColor)
            .border(1.dp, trackBorderColor, shape)
            .padding(trackPadding)
    ) {
        val totalInnerWidth = maxWidth
        val count = items.size
        val segmentWidth = (totalInnerWidth - segmentSpacing * (count - 1)) / count.toFloat()
        val targetOffset = (segmentWidth + segmentSpacing) * selectedIndex.toFloat()

        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 500f
            ),
            label = "segmentedControlSlider"
        )

        // 选中的圆角胶囊滑动指示器（显眼饱满的主色实色背景与柔和微光投影）
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (isDark) 4.dp else 2.dp,
                    shape = shape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.30f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    spotColor = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                )
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
        )

        // 交互项文字
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(segmentSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
                    label = "segmentedTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .semantics {
                            this.selected = selected
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
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
