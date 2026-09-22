package com.example.yanji.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.yanji.YanjiTab
import com.example.yanji.theme.YanjiLiquidGlass
import com.example.yanji.theme.yanjiIsDarkTheme
import com.example.yanji.ui.components.GlassSurface
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs

private val DockHeight = 60.dp
private val DockIndicatorSize = 44.dp
private val DockIndicatorMaxStretch = 4.dp
private val MinDockWidth = 260.dp
private val MaxDockWidth = 360.dp
private val HorizontalMargin = 44.dp
private val DockBottomGap = 8.dp

/**
 * 浮动玻璃导航栏：使用 Haze 模糊与原有的轻边缘，表面色与内容卡片保持区别。
 * 指示器动画只在图层阶段读取位置，避免逐帧重新测量导航栏。
 */
@Composable
fun GlassBottomBar(
    currentTab: YanjiTab,
    onTabSelected: (YanjiTab) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val tabs = YanjiTab.entries
    val selectedIndex = tabs.indexOf(currentTab)
    val indicatorPosition = remember {
        Animatable(selectedIndex.toFloat())
    }
    val reduceMotion = com.example.yanji.theme.YanjiMotion.isReduceMotionEnabled()
    val isDark = yanjiIsDarkTheme()
    val glassTokens = YanjiLiquidGlass
    val colors = MaterialTheme.colorScheme
    val dockSurfaceColor = if (isDark) {
        lerp(colors.surfaceContainerHigh, colors.primary, 0.07f).copy(alpha = 0.94f)
    } else {
        lerp(colors.surfaceVariant, colors.primary, 0.12f).copy(alpha = 0.93f)
    }

    // 从悬浮胶囊的上直边开始，向系统导航区逐渐增强模糊。
    // 与胶囊共用同一 blurRadius，保证两层玻璃在交界处没有光学强度断层。
    val gradientBackdrop = if (hazeState != null && glassTokens.blurRadius > 0.dp) {
        Modifier.hazeEffect(state = hazeState) {
            blurRadius = glassTokens.blurRadius
            tints = listOf(HazeTint(dockSurfaceColor.copy(alpha = if (isDark) 0.42f else 0.38f)))
            noiseFactor = glassTokens.noiseFactor
            progressive = HazeProgressive.verticalGradient(
                startIntensity = 0f,
                endIntensity = 1f,
                preferPerformance = true
            )
            backgroundColor = Color.Transparent
        }
    } else {
        Modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    dockSurfaceColor.copy(alpha = if (isDark) 0.70f else 0.62f)
                )
            )
        )
    }

    LaunchedEffect(selectedIndex, reduceMotion) {
        if (reduceMotion) {
            indicatorPosition.snapTo(selectedIndex.toFloat())
        } else {
            indicatorPosition.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 450f,
                    visibilityThreshold = 0.001f
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(gradientBackdrop)
            .navigationBarsPadding()
            .padding(bottom = DockBottomGap),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth - HorizontalMargin * 2
        val dockWidth = availableWidth.coerceIn(MinDockWidth, MaxDockWidth)

        val endcapCenter = DockHeight / 2f
        val firstTabCenter = endcapCenter
        val lastTabCenter = dockWidth - endcapCenter
        val totalSpan = lastTabCenter - firstTabCenter
        val step = totalSpan / (tabs.size - 1).toFloat()

        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        val primaryColor = MaterialTheme.colorScheme.primary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val outlineVariant = MaterialTheme.colorScheme.outlineVariant

        // 暗色模式下图标采用高亮天蓝（onPrimaryContainer），亮色模式采用标准主色（primary）
        val selectedColor = if (isDark) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        }

        val glassBorder: BorderStroke? = if (isDark) {
            null
        } else {
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        outlineVariant.copy(alpha = 0.35f)
                    )
                )
            )
        }

        // 延续原有选中态圆片。
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

        val dropletBorderModifier = if (isDark) {
            Modifier
        } else {
            Modifier.border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.60f),
                        primaryColor.copy(alpha = 0.12f)
                    )
                ),
                CircleShape
            )
        }

        GlassSurface(
            modifier = Modifier
                .shadow(
                    elevation = if (isDark) 8.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.40f) else onSurfaceColor.copy(alpha = 0.06f),
                    spotColor = if (isDark) primaryColor.copy(alpha = 0.16f) else onSurfaceColor.copy(alpha = 0.10f)
                )
                .width(dockWidth)
                .height(DockHeight),
            hazeState = hazeState,
            shape = CircleShape,
            fallbackColor = dockSurfaceColor,
            border = glassBorder
        ) {
            // 滑动指示器（按同心圆几何中心与动态拉伸渲染）
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(DockIndicatorSize)
                    // Animation reads stay in the layer phase: no per-frame composition/measurement.
                    .graphicsLayer {
                        val position = indicatorPosition.value
                        val distance = abs(selectedIndex - position).coerceIn(0f, 1f)
                        val offset = (firstTabCenter + step * position - DockIndicatorSize / 2f).toPx()
                        translationX = if (isRtl) -offset else offset
                        scaleX = if (reduceMotion) 1f else
                            1f + DockIndicatorMaxStretch / DockIndicatorSize * distance
                    }
                    .clip(CircleShape)
                    .background(dropletBrush)
                    .then(dropletBorderModifier)
            )

            // Tab 触控交互项：各 Tab 的中心点与同心圆中心严格重合，触控区域无缝衔接
            tabs.forEachIndexed { index, tab ->
                val tabCenter = firstTabCenter + step * index

                DockBarItem(
                    tab = tab,
                    isSelected = tab == currentTab,
                    selectionProgress = { (1f - abs(indicatorPosition.value - index)).coerceIn(0f, 1f) },
                    selectedColor = selectedColor,
                    onTabSelected = onTabSelected,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = tabCenter - step / 2f)
                        .width(step)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun DockBarItem(
    tab: YanjiTab,
    isSelected: Boolean,
    selectionProgress: () -> Float,
    selectedColor: Color,
    onTabSelected: (YanjiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = com.example.yanji.theme.YanjiMotion.isReduceMotionEnabled()

    // 未选中态更克制单色，选中态使用清晰 Accent
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    // Only a press changes the spring target; selection follows the shared position directly.
    val pressScale = animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) 22f / 24f else 1f,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.82f, stiffness = 550f),
        label = "dockBarItemPressScale"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .minimumInteractiveComponentSize()
            .testTag("nav_tab_${tab.name.lowercase()}")
            .semantics {
                this.selected = isSelected
                this.role = Role.Tab
                this.stateDescription = if (isSelected) "已选中" else "未选中"
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                val progress = selectionProgress()
                val selectionScale = if (reduceMotion) 1f else (23.5f + progress * 0.5f) / 24f
                scaleX = selectionScale * pressScale.value
                scaleY = scaleX
                translationY = if (isPressed || reduceMotion) 0f else (-0.5f * progress).dp.toPx()
            }
        ) {
            Icon(
                imageVector = tab.unselectedIcon,
                contentDescription = tab.title,
                tint = unselectedColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { alpha = 1f - selectionProgress() }
            )
            Icon(
                imageVector = tab.selectedIcon,
                contentDescription = null,
                tint = selectedColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { alpha = selectionProgress() }
            )
        }
    }
}
