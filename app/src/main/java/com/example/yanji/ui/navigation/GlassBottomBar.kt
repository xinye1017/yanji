package com.example.yanji.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
private val DockIndicatorSize = 36.dp
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
    val hasGlass = glassTokens.blurRadius > 0.dp
    // 中性底色让模糊后的页面内容提供环境色，避免叠加一层主色光晕。
    val dockBaseColor = if (isDark) {
        colors.surfaceContainerHigh
    } else {
        lerp(colors.surface, colors.surfaceVariant, 0.18f)
    }
    val dockSurfaceColor = dockBaseColor.copy(
        alpha = if (hasGlass) { if (isDark) 0.80f else 0.82f } else 1f
    )

    // 从悬浮胶囊的上直边开始，向系统导航区逐渐增强模糊。
    // 与胶囊共用同一 blurRadius，保证两层玻璃在交界处没有光学强度断层。
    //
    // 防重影铁律：progressive 的 **startIntensity 不得为 0**。
    // 顶边带是底栏唯一直接透出 hazeSource 的区域，强度 0 = 不模糊 = 原样透出；
    // 而滚动订阅源里此刻正压在底栏上方的，恰是「今日计划 / 打卡 / 看板」那几张卡，
    // 用户读起来就是「页面下方把这三块又渲染了一遍」。
    // 0.35f 起跳把这一带压成"糊住的浅色玻璃"，滚动残影不再成句；
    // tint 同步加厚一档，双保险。
    val gradientBackdrop = if (hazeState != null && glassTokens.blurRadius > 0.dp) {
        Modifier.hazeEffect(state = hazeState) {
            blurRadius = glassTokens.blurRadius
            tints = listOf(HazeTint(dockSurfaceColor.copy(alpha = if (isDark) 0.32f else 0.28f)))
            noiseFactor = glassTokens.noiseFactor * 0.5f
            progressive = HazeProgressive.verticalGradient(
                startIntensity = 0.35f,
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
                    dockBaseColor.copy(alpha = if (isDark) 0.40f else 0.30f)
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

        // 浅色与深色模式均采用鲜明的主题主色（primary），确保色彩辨识度高度一致
        val selectedColor = primaryColor

        // 边缘与表面共用左上方光源，向右下方衰减；减少透明度时只保留中性轮廓。
        val glassBorder: BorderStroke = if (hasGlass) {
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (isDark) 0.16f else 0.38f),
                    0.45f to outlineVariant.copy(alpha = 0.20f),
                    1f to onSurfaceColor.copy(alpha = if (isDark) 0.10f else 0.07f)
                )
            )
        } else {
            BorderStroke(1.dp, outlineVariant.copy(alpha = 0.70f))
        }

        // 选中态用柔和的主色面区分，不再叠加亮白环线。
        val dropletBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    primaryColor.copy(alpha = 0.22f),
                    primaryColor.copy(alpha = 0.14f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    primaryColor.copy(alpha = 0.16f),
                    primaryColor.copy(alpha = 0.10f)
                )
            )
        }

        val reflectionColor = Color.White.copy(
            alpha = if (hasGlass) { if (isDark) 0.08f else 0.16f } else 0f
        )
        val shadowColor = if (isDark) colors.scrim else onSurfaceColor

        GlassSurface(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = shadowColor.copy(alpha = if (isDark) 0.28f else 0.06f),
                    spotColor = shadowColor.copy(alpha = if (isDark) 0.36f else 0.10f)
                )
                .width(dockWidth)
                .height(DockHeight),
            hazeState = hazeState,
            shape = CircleShape,
            fallbackColor = dockSurfaceColor,
            border = glassBorder
        ) {
            // 宽而柔和的局部反光，避免整条上沿出现均匀的亮白光带。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .drawWithCache {
                        val reflection = Brush.radialGradient(
                            colors = listOf(reflectionColor, Color.Transparent),
                            center = Offset(size.width * 0.22f, -size.height * 0.8f),
                            radius = size.width * 0.72f
                        )
                        onDrawBehind { drawRect(reflection) }
                    }
            )
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
