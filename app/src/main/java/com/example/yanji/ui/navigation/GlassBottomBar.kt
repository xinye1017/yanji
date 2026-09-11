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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.yanji.YanjiTab
import com.example.yanji.theme.YanjiBackground
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiPrimarySoft
import com.example.yanji.theme.YanjiTextSecondary
import kotlin.math.abs

private val DockPanelColor = Color.White
private val DockPanelBorder = Color(0xFFE4EAF2)
private val DockPillColor = YanjiPrimarySoft
private val DockHeight = 58.dp
private val DockIndicatorHeight = 44.dp
private val DockIndicatorMaxStretch = 8.dp

/**
 * 悬浮底部导航栏（对齐主页面卡片宽度，上下具有渐变毛玻璃效果）
 *
 * - 宽度：与主页面卡片保持一致（fillMaxWidth + 水平 20.dp 边距）。
 * - 悬浮效果：上下具有双向平滑渐变毛玻璃遮罩，因为悬空于界面上方（上下均有间隙），
 *   内容向上/向下穿过时呈现轻柔的背景虚化过渡。
 * - 纯净现代胶囊样式，带有跟随手指与 Tab 切换的物理弹性药丸指示器。
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

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val backdropHeight = navBottom + 12.dp + DockHeight + 36.dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. 上下双向渐变毛玻璃效果背景遮罩
        // 因为底部导航栏是悬空的（上下都有间隙），所以渐变上下对称延伸：
        // 顶部从完全透明平滑过渡到底色，中部提供柔和遮罩，底部再平滑渐变回完全透明。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(backdropHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.18f to YanjiBackground.copy(alpha = 0.25f),
                            0.32f to YanjiBackground.copy(alpha = 0.72f),
                            0.50f to YanjiBackground.copy(alpha = 0.94f),
                            0.68f to YanjiBackground.copy(alpha = 0.90f),
                            0.84f to YanjiBackground.copy(alpha = 0.52f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )

        // 2. 悬浮底栏胶囊（宽度与主页面卡片完全同宽：fillMaxWidth + padding(horizontal = 20.dp)）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .padding(horizontal = 20.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x18000000),
                    spotColor = Color(0x1F000000)
                )
                .clip(CircleShape)
                .background(DockPanelColor)
                .border(1.dp, DockPanelBorder, CircleShape)
                .height(DockHeight)
        ) {
            val totalWidth = maxWidth
            val slotWidth = totalWidth / tabs.size.toFloat()
            val remainingDistance = abs(selectedIndex - indicatorPosition.value).coerceIn(0f, 1f)
            val baseIndicatorWidth = (slotWidth - 14.dp).coerceIn(46.dp, 60.dp)
            val indicatorWidth = baseIndicatorWidth + DockIndicatorMaxStretch * remainingDistance
            val indicatorCenter = slotWidth * (indicatorPosition.value + 0.5f)

            // 滑动胶囊指示器
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorCenter - indicatorWidth / 2f)
                    .width(indicatorWidth)
                    .height(DockIndicatorHeight)
                    .clip(CircleShape)
                    .background(DockPillColor)
            )

            // Tab 选项
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

    val tint = lerp(YanjiTextSecondary, YanjiPrimary, selectionProgress)
    val iconSize by animateDpAsState(
        targetValue = if (isPressed) 23.dp else (25f + selectionProgress).dp,
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
            .semantics { selected = isSelected }
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
                    .offset(y = iconOffset)
                    .size(iconSize)
            )
            Icon(
                imageVector = tab.selectedIcon,
                contentDescription = null,
                tint = tint.copy(alpha = selectionProgress),
                modifier = Modifier
                    .offset(y = iconOffset)
                    .size(iconSize)
            )
        }
    }
}
