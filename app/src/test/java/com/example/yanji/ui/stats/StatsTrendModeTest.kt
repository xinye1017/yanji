package com.example.yanji.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证统计图表模式与时间 Tab 的联动及容错保护逻辑。
 *
 * 核心保障：
 * 1. 切换至月视角（本月 / 上月）时，即使传入旧状态 BAR，也必须强制收敛为 HEATMAP，绝不能向月视图渲染 BAR。
 * 2. 切换至周视角（本周 / 上周）或「本年」时，即使传入旧状态 HEATMAP，也必须强制收敛为 BAR。
 * 3. LINE 模式作为通用折线图，在所有时间 Tab 之间自由保持，不被降级。
 */
class StatsTrendModeTest {

    @Test
    fun `month tab converts BAR to HEATMAP`() {
        val effective = resolveEffectiveTrendMode(StatsTimeTab.THIS_MONTH, TrendMode.BAR)
        assertEquals(TrendMode.HEATMAP, effective)
    }

    @Test
    fun `previous month tab converts BAR to HEATMAP`() {
        // 回看上月还是月视图：默认热力图这条规则按「单位」走，不因为翻期而失效。
        val effective = resolveEffectiveTrendMode(StatsTimeTab.PREVIOUS_MONTH, TrendMode.BAR)
        assertEquals(TrendMode.HEATMAP, effective)
    }

    @Test
    fun `month tab preserves LINE`() {
        val effective = resolveEffectiveTrendMode(StatsTimeTab.THIS_MONTH, TrendMode.LINE)
        assertEquals(TrendMode.LINE, effective)
    }

    @Test
    fun `month tab accepts HEATMAP`() {
        val effective = resolveEffectiveTrendMode(StatsTimeTab.THIS_MONTH, TrendMode.HEATMAP)
        assertEquals(TrendMode.HEATMAP, effective)
    }

    @Test
    fun `week tab converts HEATMAP to BAR`() {
        val effective = resolveEffectiveTrendMode(StatsTimeTab.THIS_WEEK, TrendMode.HEATMAP)
        assertEquals(TrendMode.BAR, effective)
    }

    @Test
    fun `previous week tab converts HEATMAP to BAR`() {
        // 从「本月」跳到「上周」时，只能带着 BAR / 折线走，热力图是月视角专属。
        val effective = resolveEffectiveTrendMode(StatsTimeTab.PREVIOUS_WEEK, TrendMode.HEATMAP)
        assertEquals(TrendMode.BAR, effective)
    }

    @Test
    fun `week tab preserves BAR`() {
        val effective = resolveEffectiveTrendMode(StatsTimeTab.THIS_WEEK, TrendMode.BAR)
        assertEquals(TrendMode.BAR, effective)
    }

    @Test
    fun `week tab preserves LINE`() {
        val effective = resolveEffectiveTrendMode(StatsTimeTab.THIS_WEEK, TrendMode.LINE)
        assertEquals(TrendMode.LINE, effective)
    }

    @Test
    fun `year tab converts HEATMAP to BAR`() {
        val effective = resolveEffectiveTrendMode(StatsTimeTab.THIS_YEAR, TrendMode.HEATMAP)
        assertEquals(TrendMode.BAR, effective)
    }

    @Test
    fun `year tab labels describe month granularity instead of chart shape`() {
        // 本年的 BAR 已经是月粒度、LINE 是累计曲线：
        // 再叫「柱状/折线」就说不出这张图在讲什么。
        assertEquals("按月", trendModeLabel(TrendMode.BAR, StatsTimeTab.THIS_YEAR))
        assertEquals("累计", trendModeLabel(TrendMode.LINE, StatsTimeTab.THIS_YEAR))
        // 周/本月保持原词，不跟着换。
        assertEquals("柱状", trendModeLabel(TrendMode.BAR, StatsTimeTab.THIS_WEEK))
        assertEquals("折线", trendModeLabel(TrendMode.LINE, StatsTimeTab.THIS_MONTH))
    }

    @Test
    fun `available modes definition matches requirements`() {
        assertEquals(
            listOf(TrendMode.HEATMAP, TrendMode.LINE),
            resolveAvailableTrendModes(StatsTimeTab.THIS_MONTH)
        )
        assertEquals(
            listOf(TrendMode.HEATMAP, TrendMode.LINE),
            resolveAvailableTrendModes(StatsTimeTab.PREVIOUS_MONTH)
        )
        assertEquals(
            listOf(TrendMode.BAR, TrendMode.LINE),
            resolveAvailableTrendModes(StatsTimeTab.THIS_WEEK)
        )
        assertEquals(
            listOf(TrendMode.BAR, TrendMode.LINE),
            resolveAvailableTrendModes(StatsTimeTab.PREVIOUS_WEEK)
        )
        assertEquals(
            listOf(TrendMode.BAR, TrendMode.LINE),
            resolveAvailableTrendModes(StatsTimeTab.THIS_YEAR)
        )
    }

    @Test
    fun `tab labels read as the period they open`() {
        // 分段控制器的文案就是用户在统计页上看到的那五个格子。
        assertEquals(
            listOf("本周", "上周", "本月", "上月", "本年"),
            StatsTimeTab.entries.map { it.label }
        )
    }
}
