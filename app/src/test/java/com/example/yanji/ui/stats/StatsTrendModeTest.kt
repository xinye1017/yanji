package com.example.yanji.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证统计图表模式与时间范围 Tab 的联动及容错保护逻辑。
 *
 * 核心保障：
 * 1. 切换至「本月」(tab == 1) 时，即使传入旧状态 BAR，也必须强制收敛为 HEATMAP，绝不能向月视图渲染 BAR。
 * 2. 切换至「本周」(tab == 0) 或「本年」(tab == 2) 时，即使传入旧状态 HEATMAP，也必须强制收敛为 BAR。
 * 3. LINE 模式作为通用折线图，在所有时间 Tab 之间自由保持，不被降级。
 */
class StatsTrendModeTest {

    @Test
    fun `month tab converts BAR to HEATMAP`() {
        val effective = resolveEffectiveTrendMode(selectedTimeTab = 1, trendChartMode = TrendMode.BAR)
        assertEquals(TrendMode.HEATMAP, effective)
    }

    @Test
    fun `month tab preserves LINE`() {
        val effective = resolveEffectiveTrendMode(selectedTimeTab = 1, trendChartMode = TrendMode.LINE)
        assertEquals(TrendMode.LINE, effective)
    }

    @Test
    fun `month tab accepts HEATMAP`() {
        val effective = resolveEffectiveTrendMode(selectedTimeTab = 1, trendChartMode = TrendMode.HEATMAP)
        assertEquals(TrendMode.HEATMAP, effective)
    }

    @Test
    fun `week tab converts HEATMAP to BAR`() {
        val effective = resolveEffectiveTrendMode(selectedTimeTab = 0, trendChartMode = TrendMode.HEATMAP)
        assertEquals(TrendMode.BAR, effective)
    }

    @Test
    fun `week tab preserves BAR`() {
        val effective = resolveEffectiveTrendMode(selectedTimeTab = 0, trendChartMode = TrendMode.BAR)
        assertEquals(TrendMode.BAR, effective)
    }

    @Test
    fun `week tab preserves LINE`() {
        val effective = resolveEffectiveTrendMode(selectedTimeTab = 0, trendChartMode = TrendMode.LINE)
        assertEquals(TrendMode.LINE, effective)
    }

    @Test
    fun `year tab converts HEATMAP to BAR`() {
        val effective = resolveEffectiveTrendMode(selectedTimeTab = STATS_TAB_YEAR, trendChartMode = TrendMode.HEATMAP)
        assertEquals(TrendMode.BAR, effective)
    }

    @Test
    fun `year tab labels describe month granularity instead of chart shape`() {
        // 本年的 BAR 已经是月粒度、LINE 是累计曲线：
        // 再叫「柱状/折线」就说不出这张图在讲什么。
        assertEquals("按月", trendModeLabel(TrendMode.BAR, STATS_TAB_YEAR))
        assertEquals("累计", trendModeLabel(TrendMode.LINE, STATS_TAB_YEAR))
        // 周/本月保持原词，不跟着换。
        assertEquals("柱状", trendModeLabel(TrendMode.BAR, STATS_TAB_WEEK))
        assertEquals("折线", trendModeLabel(TrendMode.LINE, STATS_TAB_MONTH))
    }

    @Test
    fun `available modes definition matches requirements`() {
        assertEquals(listOf(TrendMode.HEATMAP, TrendMode.LINE), resolveAvailableTrendModes(selectedTimeTab = 1))
        assertEquals(listOf(TrendMode.BAR, TrendMode.LINE), resolveAvailableTrendModes(selectedTimeTab = 0))
        assertEquals(listOf(TrendMode.BAR, TrendMode.LINE), resolveAvailableTrendModes(selectedTimeTab = STATS_TAB_YEAR))
    }
}
