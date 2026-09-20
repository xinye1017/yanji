package com.example.yanji.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass 动态材质令牌 (LiquidGlassTokens)
 *
 * 统一管理毛玻璃材质的物理光学参数，支持浅色、深色与无障碍降级三档配置，
 * 避免在组件内散落硬编码的透明度与模糊半径。
 */
@Immutable
data class LiquidGlassTokens(
    val blurRadius: Dp,
    val tintAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    val noiseFactor: Float,
)

val LightGlassTokens = LiquidGlassTokens(
    blurRadius = 26.dp,
    tintAlpha = 0.58f,
    borderAlpha = 0.42f,
    highlightAlpha = 0.72f,
    noiseFactor = 0.04f,
)

val DarkGlassTokens = LiquidGlassTokens(
    blurRadius = 28.dp,
    tintAlpha = 0.68f,
    borderAlpha = 0.18f,
    highlightAlpha = 0.28f,
    noiseFactor = 0.05f,
)

val ReducedGlassTokens = LiquidGlassTokens(
    blurRadius = 0.dp,
    tintAlpha = 0.92f,
    borderAlpha = 0.20f,
    highlightAlpha = 0.0f,
    noiseFactor = 0.0f,
)

val LocalLiquidGlassTokens = staticCompositionLocalOf { LightGlassTokens }

/**
 * 读取当前主题环境下的 Liquid Glass 令牌
 */
val YanjiLiquidGlass: LiquidGlassTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalLiquidGlassTokens.current
