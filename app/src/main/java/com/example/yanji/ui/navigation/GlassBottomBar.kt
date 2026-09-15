package com.example.yanji.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.yanji.YanjiTab
import com.example.yanji.theme.YanjiDarkDockPanel
import com.example.yanji.theme.yanjiIsDarkTheme
import kotlin.math.abs

private val DockHeight = 60.dp
private val DockIndicatorSize = 44.dp
private val DockIndicatorMaxStretch = 4.dp
private val MinDockWidth = 260.dp
private val MaxDockWidth = 360.dp
private val HorizontalMargin = 64.dp

/**
 * 响应式浮动液态玻璃导航栏（GlassBottomBar）：
 * 1. 紧凑胶囊尺寸：横向更加聚合灵动（[MinDockWidth]..[MaxDockWidth]），纵向增高至 60.dp，比例更加饱满舒适；
 * 2. 纯净半透玻璃质感：亮色通透晶莹、暗色深邃通透，杜绝多余发白色块与毛刺边缘；
 * 3. 暗色模式高对比度优化：暗色下滑块底色采用轻量柔和的微光蓝容器（避免过深色块与图标混淆），选中图标采用高亮天蓝，层级分明通透清晰；
 * 4. 硬件级柔和阴影：亮色模式柔和接地、暗色模式微泛幽蓝流光，悬浮自然；
 * 5. 灵动水滴滑块：指示器随切换弹性拉伸，提供细腻微反光边缘与平滑阻尼动效。
 */
@Composable
fun GlassBottomBar(
    currentTab: YanjiTab,
    onTabSelected: (YanjiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = YanjiTab.entries
    val selectedIndex = tabs.indexOf(currentTab)
    val indicatorPosition = remember {
        Animatable(selectedIndex.toFloat())
    }

    LaunchedEffect(selectedIndex) {
        indicatorPosition.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = 420f,
                visibilityThreshold = 0.001f
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth - HorizontalMargin * 2
        val dockWidth = availableWidth.coerceIn(MinDockWidth, MaxDockWidth)
        val slotWidth = dockWidth / tabs.size.toFloat()
        val remainingDistance = abs(selectedIndex - indicatorPosition.value).coerceIn(0f, 1f)
        val indicatorWidth = DockIndicatorSize + DockIndicatorMaxStretch * remainingDistance
        val indicatorCenter = slotWidth * (indicatorPosition.value + 0.5f)

        val isDark = yanjiIsDarkTheme()
        val primaryColor = MaterialTheme.colorScheme.primary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val outlineVariant = MaterialTheme.colorScheme.outlineVariant

        // 暗色模式下图标采用高亮天蓝（onPrimaryContainer），亮色模式采用标准主色（primary）
        val selectedColor = if (isDark) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        }

        // 1. 玻璃面板底色：通透微渐变，纯净自然
        val glassBodyBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    YanjiDarkDockPanel.copy(alpha = 0.88f),
                    YanjiDarkDockPanel.copy(alpha = 0.78f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.92f),
                    Color.White.copy(alpha = 0.82f)
                )
            )
        }

        // 2. 玻璃微光边框：暗色低调收敛（杜绝高反差白边），亮色通透清晰
        val glassBorderBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    outlineVariant.copy(alpha = 0.35f)
                )
            )
        }

        // 3. 滑块指示圆框底色：
        // 暗色模式：采用轻盈通透的微光蓝容器（18%~10%），对齐亮色 primaryContainer 的通透层级，
        // 彻底解决此前因色块过深过浓导致与选中图标颜色重合混淆的问题。
        val dropletBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    primaryColor.copy(alpha = 0.18f),
                    primaryColor.copy(alpha = 0.10f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
                )
            )
        }

        val dropletBorderBrush = if (isDark) {
            SolidColor(primaryColor.copy(alpha = 0.32f))
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.60f),
                    primaryColor.copy(alpha = 0.12f)
                )
            )
        }

        Box(
            modifier = Modifier
                .shadow(
                    elevation = if (isDark) 8.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.40f) else onSurfaceColor.copy(alpha = 0.06f),
                    spotColor = if (isDark) primaryColor.copy(alpha = 0.16f) else onSurfaceColor.copy(alpha = 0.10f)
                )
                .width(dockWidth)
                .height(DockHeight)
                .clip(CircleShape)
                .background(glassBodyBrush)
                .border(1.dp, glassBorderBrush, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorCenter - indicatorWidth / 2f)
                    .width(indicatorWidth)
                    .height(DockIndicatorSize)
                    .clip(CircleShape)
                    .background(dropletBrush)
                    .border(1.dp, dropletBorderBrush, CircleShape)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selectionProgress =
                        (1f - abs(indicatorPosition.value - index)).coerceIn(0f, 1f)

                    DockBarItem(
                        tab = tab,
                        isSelected = tab == currentTab,
                        selectionProgress = selectionProgress,
                        selectedColor = selectedColor,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockBarItem(
    tab: YanjiTab,
    isSelected: Boolean,
    selectionProgress: Float,
    selectedColor: Color,
    onTabSelected: (YanjiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = lerp(unselectedColor, selectedColor, selectionProgress)

    val iconSize by animateDpAsState(
        targetValue = if (isPressed) 22.dp else (23f + selectionProgress).dp,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 650f),
        label = "dockBarItemIconSize"
    )
    val iconOffset by animateDpAsState(
        targetValue = if (isPressed) 0.dp else (-selectionProgress).dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "dockBarItemIconOffset"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .minimumInteractiveComponentSize()
            .testTag("nav_tab_${tab.name.lowercase()}")
            .semantics {
                this.selected = isSelected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!isSelected) onTabSelected(tab)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = tab.unselectedIcon,
                contentDescription = if (isSelected) "${tab.title}，已选中" else tab.title,
                tint = tint.copy(alpha = 1f - selectionProgress),
                modifier = Modifier
                    .offset { IntOffset(0, iconOffset.roundToPx()) }
                    .size(iconSize)
            )
            Icon(
                imageVector = tab.selectedIcon,
                contentDescription = null,
                tint = tint.copy(alpha = selectionProgress),
                modifier = Modifier
                    .offset { IntOffset(0, iconOffset.roundToPx()) }
                    .size(iconSize)
            )
        }
    }
}
