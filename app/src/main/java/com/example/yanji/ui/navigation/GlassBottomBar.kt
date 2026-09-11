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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.yanji.YanjiTab
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiPrimarySoft
import com.example.yanji.theme.YanjiTextSecondary
import kotlin.math.abs

private val DockPanelColor = Color.White
private val DockPanelBorder = Color(0xFFE4EAF2)
private val DockPillColor = YanjiPrimarySoft
private val DockWidth = 280.dp
private val DockHeight = 58.dp
private val DockIndicatorSize = 48.dp
private val DockIndicatorMaxStretch = 8.dp

/**
 * Compact floating bottom navigation dock.
 *
 * - Text labels are removed for a clean, modern dock aesthetic.
 * - Icon size is increased to 26dp.
 * - Width is reduced to a compact 280dp capsule.
 * - Solid white panel with a quiet soft shadow (DESIGN.md: no glassmorphism,
 *   no translucent panels).
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

    val slotWidth = DockWidth / tabs.size.toFloat()
    val remainingDistance = abs(selectedIndex - indicatorPosition.value).coerceIn(0f, 1f)
    val indicatorWidth = DockIndicatorSize + DockIndicatorMaxStretch * remainingDistance
    val indicatorCenter = slotWidth * (indicatorPosition.value + 0.5f)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = Color(0x18000000),
                spotColor = Color(0x1F000000)
            )
            .clip(CircleShape)
            .background(DockPanelColor)
            .border(1.dp, DockPanelBorder, CircleShape)
            .width(DockWidth)
            .height(DockHeight)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = indicatorCenter - indicatorWidth / 2f)
                .width(indicatorWidth)
                .height(DockIndicatorSize)
                .clip(CircleShape)
                .background(DockPillColor)
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
