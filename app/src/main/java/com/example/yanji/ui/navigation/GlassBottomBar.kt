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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.yanji.theme.yanjiIsDarkTheme
import kotlin.math.abs

private val DockHeight = 52.dp
private val DockIndicatorSize = 40.dp
private val DockIndicatorMaxStretch = 4.dp
private val MinDockWidth = 260.dp
private val MaxDockWidth = 360.dp
private val HorizontalMargin = 24.dp

/**
 * 液态流光玻璃底栏（Liquid Glass Bottom Bar）：
 * 1. 外层流光晕染与环境阴影：模拟 CSS `box-shadow: 0px 0px 21px -8px rgba(255, 255, 255, 0.3)`，亮色模式下产生纯白微发光光晕，暗色模式下散发深邃幽蓝流光；
 * 2. 晶体液态折射基底：多段垂直微渐变透光面板，呈现如透亮圆柱玻璃晶体般的视觉厚度；
 * 3. 倒角内高光折射（Inner Specular Bevel）：模拟 CSS `box-shadow: inset 0 0 6px -3px rgba(255, 255, 255, 0.7)`，在胶囊内边缘绘制精致的内凹高光弧线；
 * 4. 流体微弧波纹（Liquid Meniscus Sheen）：模拟 SVG 湍流折射滤镜（feTurbulence + feDisplacementMap），在上半部呈现有机液体折射弧光；
 * 5. 水滴滑块（Liquid Mercury Droplet）：指示器采用流体水滴质感渐变、微光边缘与动态拉伸，随切换灵动流动；
 * 6. 响应式自适应：在 [MinDockWidth]..[MaxDockWidth] 之间弹性伸缩，无缝适配各种屏幕尺寸。
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

        // 1. 晶体液态多段渐变底色（Liquid Glass Body）
        val glassBodyBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    YanjiDarkDockPanel.copy(alpha = 0.90f),
                    YanjiDarkDockPanel.copy(alpha = 0.76f),
                    YanjiDarkDockPanel.copy(alpha = 0.86f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.94f),
                    Color.White.copy(alpha = 0.78f),
                    Color.White.copy(alpha = 0.88f)
                )
            )
        }

        // 2. 双层折射高光边框（Outer Specular Border）
        val glassBorderBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.36f),
                    outlineVariant.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.12f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.92f),
                    outlineVariant.copy(alpha = 0.36f),
                    Color.White.copy(alpha = 0.65f)
                )
            )
        }

        // 3. 水滴滑块底色与微高光边框（Liquid Droplet Pill）
        val dropletBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    YanjiDarkDockPill.copy(alpha = 0.85f),
                    YanjiDarkDockPill.copy(alpha = 0.50f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                )
            )
        }

        val dropletBorderBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.35f),
                    primaryColor.copy(alpha = 0.25f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.75f),
                    primaryColor.copy(alpha = 0.15f)
                )
            )
        }

        Box(
            modifier = Modifier
                // 外层流光晕染与环境阴影（Outer Ambient Aura & Halo - 对应 box-shadow: 0px 0px 21px -8px rgba(255, 255, 255, 0.3)）
                .drawBehind {
                    val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    if (isDark) {
                        // 暗色：底部柔和深色阴影
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.40f),
                            topLeft = Offset(0f, 4.dp.toPx()),
                            size = size,
                            cornerRadius = cornerRadius
                        )
                        // 暗色：外层幽蓝流光微晕（Aura Glow）
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.18f),
                            topLeft = Offset(-4.dp.toPx(), -2.dp.toPx()),
                            size = Size(size.width + 8.dp.toPx(), size.height + 4.dp.toPx()),
                            cornerRadius = CornerRadius((size.height + 4.dp.toPx()) / 2f, (size.height + 4.dp.toPx()) / 2f)
                        )
                    } else {
                        // 亮色：外层流光光晕（box-shadow: 0px 0px 21px -8px rgba(255, 255, 255, 0.7)）
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.75f),
                            topLeft = Offset(-4.dp.toPx(), -3.dp.toPx()),
                            size = Size(size.width + 8.dp.toPx(), size.height + 6.dp.toPx()),
                            cornerRadius = CornerRadius((size.height + 6.dp.toPx()) / 2f, (size.height + 6.dp.toPx()) / 2f)
                        )
                        // 亮色：柔和接地环境阴影
                        drawRoundRect(
                            color = onSurfaceColor.copy(alpha = 0.06f),
                            topLeft = Offset(0f, 3.dp.toPx()),
                            size = size,
                            cornerRadius = cornerRadius
                        )
                        drawRoundRect(
                            color = onSurfaceColor.copy(alpha = 0.03f),
                            topLeft = Offset(0f, 6.dp.toPx()),
                            size = size,
                            cornerRadius = cornerRadius
                        )
                    }
                }
                .width(dockWidth)
                .height(DockHeight)
                .clip(CircleShape)
                .background(glassBodyBrush)
                .border(1.dp, glassBorderBrush, CircleShape)
                // 内凹高光倒角与流体折射弧光（对应 inset 0 0 6px -3px rgba(255, 255, 255, 0.7) 与 feDisplacementMap 扭曲流光）
                .drawWithContent {
                    drawContent()

                    val width = size.width
                    val height = size.height
                    val innerStrokeWidth = 1.2.dp.toPx()

                    // 倒角内高光（Inner Specular Bevel）：沿胶囊内圈上沿渲染高透亮线
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = if (isDark) 0.32f else 0.75f),
                            0.45f to Color.White.copy(alpha = if (isDark) 0.08f else 0.20f),
                            1.0f to Color.Transparent,
                            startY = 1.dp.toPx(),
                            endY = height
                        ),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(width - 2.dp.toPx(), height - 2.dp.toPx()),
                        cornerRadius = CornerRadius((height - 2.dp.toPx()) / 2f, (height - 2.dp.toPx()) / 2f),
                        style = Stroke(width = innerStrokeWidth)
                    )

                    // 流体微弧波纹（Liquid Refraction Sheen）：模拟液态折射光斑
                    val liquidWavePath = Path().apply {
                        moveTo(height / 2f, 2.dp.toPx())
                        cubicTo(
                            width * 0.28f, height * 0.38f,
                            width * 0.72f, height * 0.38f,
                            width - height / 2f, 2.dp.toPx()
                        )
                        close()
                    }
                    drawPath(
                        path = liquidWavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.12f else 0.26f),
                                Color.White.copy(alpha = if (isDark) 0.02f else 0.06f),
                                Color.Transparent
                            ),
                            startY = 2.dp.toPx(),
                            endY = height * 0.42f
                        )
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorCenter - indicatorWidth / 2f)
                    .width(indicatorWidth)
                    .height(DockIndicatorSize)
                    // 水滴阴影/发光
                    .drawBehind {
                        if (isDark) {
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.22f),
                                topLeft = Offset(-2.dp.toPx(), -1.dp.toPx()),
                                size = Size(size.width + 4.dp.toPx(), size.height + 2.dp.toPx()),
                                cornerRadius = CornerRadius((size.height + 2.dp.toPx()) / 2f, (size.height + 2.dp.toPx()) / 2f)
                            )
                        } else {
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.08f),
                                topLeft = Offset(0f, 2.dp.toPx()),
                                size = size,
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                            )
                        }
                    }
                    .clip(CircleShape)
                    .background(dropletBrush)
                    .border(1.dp, dropletBorderBrush, CircleShape)
                    // 水滴顶部微光反射（Droplet Specular Highlight）
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isDark) 0.30f else 0.55f),
                                    Color.Transparent
                                )
                            ),
                            topLeft = Offset(size.width * 0.18f, 2.dp.toPx()),
                            size = Size(size.width * 0.64f, size.height * 0.36f),
                            cornerRadius = CornerRadius(size.height * 0.2f, size.height * 0.2f)
                        )
                    }
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
