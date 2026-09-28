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
    // 参考霜化药丸质感：胶囊本体更 milky，透光但不透形；降级无 blur 时保持原高不透明。
    val dockSurfaceColor = if (isDark) {
        lerp(colors.surfaceContainerHigh, colors.primary, 0.08f).copy(
            alpha = if (glassTokens.blurRadius > 0.dp) 0.86f else 0.95f
        )
    } else {
        lerp(colors.surface, colors.surfaceVariant, 0.35f).copy(
            alpha = if (glassTokens.blurRadius > 0.dp) 0.88f else 0.95f
        )
    }

    // 从悬浮胶囊的上直边开始，向系统导航区逐渐增强模糊。
    // 与胶囊共用同一 blurRadius，保证两层玻璃在交界处没有光学强度断层。
    val gradientBackdrop = if (hazeState != null && glassTokens.blurRadius > 0.dp) {
        Modifier.hazeEffect(state = hazeState) {
            blurRadius = glassTokens.blurRadius
            tints = listOf(HazeTint(dockSurfaceColor.copy(alpha = if (isDark) 0.36f else 0.30f)))
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

        // 浅色与深色模式均采用鲜明的主题主色（primary），确保色彩辨识度高度一致
        val selectedColor = primaryColor

        // 真实物理聚光边框（Top Specular Rim）：模拟漫反射环境光从上方投射在弧面玻璃上的自然折射
        // 深浅同配方（4 段：顶高光 → 主色 → 描边 → 底微光），数值按主题映射。
        // 浅色顶高光强、深色整体收敛，呼应霜化药丸的上缘反光。
        val glassBorder: BorderStroke = if (isDark) {
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        primaryColor.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.06f)
                    )
                )
            )
        } else {
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.12f),
                        outlineVariant.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.25f)
                    )
                )
            )
        }

        // 滑动选中态指示器水滴材质：深浅同配方（primary 半透明 wash），数值按主题映射。
        // 浅色此前是近不透的 primaryContainer 实色，与深色的通透 wash 是两种材质语言，现统一。
        val dropletBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    primaryColor.copy(alpha = 0.28f),
                    primaryColor.copy(alpha = 0.14f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    primaryColor.copy(alpha = 0.22f),
                    primaryColor.copy(alpha = 0.12f)
                )
            )
        }

        val dropletBorderModifier = if (isDark) {
            Modifier.border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        primaryColor.copy(alpha = 0.30f)
                    )
                ),
                CircleShape
            )
        } else {
            Modifier.border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.70f),
                        primaryColor.copy(alpha = 0.15f)
                    )
                ),
                CircleShape
            )
        }

        GlassSurface(
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.45f) else onSurfaceColor.copy(alpha = 0.09f),
                    spotColor = if (isDark) primaryColor.copy(alpha = 0.20f) else primaryColor.copy(alpha = 0.10f)
                )
                .width(dockWidth)
                .height(DockHeight),
            hazeState = hazeState,
            shape = CircleShape,
            fallbackColor = dockSurfaceColor,
            border = glassBorder
        ) {
            // 表面光学漫反射微光层：模拟弧面玻璃透镜顶部的自然反光与底部微透环境光。
            // 参考霜化药丸：顶部高光带加亮，还原稿子里那圈沿上缘的强反光。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.34f),
                                Color.Transparent,
                                if (isDark) primaryColor.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
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
