package com.example.yanji.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * 全站卡片的**唯一**边框样式（2026-09 起统一）。
 *
 * 此前卡片存在四种写法：`1dp` 纯色（YanjiCard / CheckInCard）、`0.8dp` 半透明
 * （YanjiGroupedCard）、`0.8dp` 配 `separator`（统计页两张卡）、以及硬编码圆角的无边框卡。
 * 同屏并排时粗细与浓度肉眼可辨，故收敛为一条规则，所有卡片容器共用本 token。
 *
 * 取值来源：iOS Grouped Inset List 的「超细低对比度描边」。
 *
 * 为什么带 alpha 衰减：`DESIGN.md` / `design_dark.md` 规定暗色不靠描边表达层次，
 * 而靠表面明度递进（背景 #0D111A → 卡片 #222C40）。因此暗色只留 12% 描边，
 * 亮色底卡对比弱，需要 45% 才够清晰。
 */
object YanjiCardBorder {
    val Width = 0.8.dp

    /** 暗色描边不透明度：极淡，层次交给表面明度差。 */
    private const val DarkAlpha = 0.12f

    /** 亮色描边不透明度：底色与白卡对比弱，需要更实的边。 */
    private const val LightAlpha = 0.45f

    /**
     * 取当前主题下的卡片边框。
     *
     * 用法：`border = YanjiCardBorder.stroke()`，或直接作为组件默认参数值。
     */
    @Composable
    @ReadOnlyComposable
    fun stroke(): BorderStroke = BorderStroke(
        Width,
        MaterialTheme.colorScheme.outlineVariant.copy(
            alpha = if (yanjiIsDarkTheme()) DarkAlpha else LightAlpha
        )
    )
}
