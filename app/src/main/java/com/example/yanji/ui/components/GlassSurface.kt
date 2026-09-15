package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiLiquidGlass
import com.example.yanji.theme.yanjiIsDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 通用液态玻璃表面容器 (GlassSurface)
 *
 * 核心设计原则：
 * 1. 真实背景虚化：挂接 [HazeState] 获取底层滚动内容的真实像素并应用高斯模糊与噪点质感；
 * 2. 动态光感边框：顶部聚光高亮、侧面微透、底侧柔和反光；
 * 3. 优雅降级能力：当 hazeState 为空或处于减少透明度模式（blurRadius == 0.dp）时，自动回退为纯净的半透明/纯色面板。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    shape: Shape = CircleShape,
    fallbackColor: Color? = null,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = YanjiLiquidGlass
    val defaultSurface = MaterialTheme.colorScheme.surface
    val baseTint = fallbackColor ?: defaultSurface.copy(alpha = tokens.tintAlpha)

    val effectiveBorder = border ?: run {
        val borderBrush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = tokens.highlightAlpha),
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = tokens.borderAlpha),
                Color.White.copy(alpha = tokens.borderAlpha * 0.5f)
            )
        )
        BorderStroke(1.dp, borderBrush)
    }

    val hazeModifier = if (hazeState != null && tokens.blurRadius > 0.dp) {
        Modifier.hazeEffect(state = hazeState) {
            blurRadius = tokens.blurRadius
            tints = listOf(HazeTint(baseTint))
            noiseFactor = tokens.noiseFactor
            // haze 1.5.4 的 RenderEffect 路径（API 31+）会在 drawScaledContentLayer 中
            // 硬性校验 resolveBackgroundColor().isSpecified，其解析链为
            // node.backgroundColor → style.backgroundColor → compositionLocalStyle.backgroundColor。
            // 三级全空时抛 IllegalArgumentException("backgroundColor not specified")，
            // 导致真机启动即崩溃（模拟器同样受影响，仅取决于是否走 RenderEffect 分支）。
            // 玻璃面板本身需要透出底层内容，故以 Transparent 垫底：
            // 绘制透明矩形是 no-op，不改变原本的通透观感。着色仍由上面的 tints 负责。
            backgroundColor = Color.Transparent
        }
    } else {
        Modifier.background(baseTint)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(hazeModifier)
            .border(effectiveBorder, shape),
        content = content
    )
}
