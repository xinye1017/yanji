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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.yanji.YanjiTab
import com.example.yanji.theme.YanjiDarkDockPanel
import com.example.yanji.theme.YanjiDarkDockPill
import com.example.yanji.theme.YanjiDockPanel
import com.example.yanji.theme.yanjiThemeColor
import kotlin.math.abs

private val DockHeight = 52.dp
private val DockIndicatorSize = 40.dp
private val DockIndicatorMaxStretch = 4.dp
private val MinDockWidth = 260.dp
private val MaxDockWidth = 360.dp
private val HorizontalMargin = 24.dp

/**
 * 响应式浮动导航栏（GlassBottomBar）：
 * 1. 宽度自适应：在可用宽度（maxWidth - 左右安全边距）与 [MinDockWidth]..[MaxDockWidth] 之间弹性调整，
 *    在 compact phone、主屏、折叠屏、平板和横屏下均有良好表现；
 * 2. 主题适配：背景和描边基于 [MaterialTheme.colorScheme]，无缝适配深色/浅色模式；
 * 3. 无障碍增强：提供标准的 Tab 角色、选中状态指示与完整的 contentDescription，交互区域达到无障碍标准。
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

        // design_dark.md §3.2：暗色底栏是**半透明**玻璃面板（rgba(21,27,40,0.78)），
        // 激活胶囊用微发光蓝（rgba(79,125,243,0.22)）；亮色保持不透明白，视觉不变。
        // 注：规范里的 backdrop-filter blur 在 Compose 没有「背景滤镜」等价物
        //（Modifier.blur 模糊的是自身内容而非身后内容，且需 API 31+），
        // 因此以半透明面板 + 既有柔和投影表达同一意图，不引入平台版本分叉。
        val dockPanelColor = yanjiThemeColor(YanjiDockPanel, YanjiDarkDockPanel)
        val dockPillColor = yanjiThemeColor(MaterialTheme.colorScheme.primaryContainer, YanjiDarkDockPill)
        val dockBorderColor = MaterialTheme.colorScheme.outlineVariant
        val dockShadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        val dockShadowColorStrong = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)

        Box(
            modifier = Modifier
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = dockShadowColor,
                    spotColor = dockShadowColorStrong
                )
                .clip(CircleShape)
                .background(dockPanelColor)
                .border(1.dp, dockBorderColor, CircleShape)
                .width(dockWidth)
                .height(DockHeight)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorCenter - indicatorWidth / 2f)
                    .width(indicatorWidth)
                    .height(DockIndicatorSize)
                    .clip(CircleShape)
                    .background(dockPillColor)
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
    onTabSelected: (YanjiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.primary
    val tint = lerp(unselectedColor, selectedColor, selectionProgress)

    val iconSize by animateDpAsState(
        targetValue = if (isPressed) 21.dp else (22f + selectionProgress).dp,
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
