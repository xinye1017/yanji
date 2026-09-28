package com.example.yanji.ui.stats

import com.example.yanji.data.MonthBarData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 本年趋势图的数据装配。
 *
 * 这里是「月 → 柱」和「月 → 累计曲线」的唯一出处，图表只负责画。
 * 累计值必须逐月单调不减：一旦某月比上一月小，曲线就会掉头，读者会以为数据错了。
 */
class StatsYearTrendBarsTest {

    private fun month(month: Int, seconds: Long, current: Boolean = false) = MonthBarData(
        year = 2026,
        month = month,
        label = "${month}月",
        durationSeconds = seconds,
        activeDays = if (seconds > 0) 1 else 0,
        isCurrentMonth = current
    )

    @Test
    fun `bar mode keeps per month values and marks current month`() {
        val bars = yearTrendBars(
            months = listOf(month(1, 3600), month(2, 0), month(3, 7200, current = true)),
            mode = TrendMode.BAR
        )

        assertEquals(3, bars.size)
        assertEquals(listOf("1月", "2月", "3月"), bars.map { it.dayLabel })
        assertEquals(listOf(3600L, 0L, 7200L), bars.map { it.durationSeconds })
        // 图表用 isToday 表达「当前所在格」：本年视角下就是当月。
        assertEquals(true, bars.last().isToday)
        assertEquals(false, bars.first().isToday)
        assertEquals("2026-3", bars.last().date)
    }

    @Test
    fun `line mode accumulates monotonically`() {
        val bars = yearTrendBars(
            months = listOf(month(1, 3600), month(2, 1800), month(3, 0), month(4, 900, current = true)),
            mode = TrendMode.LINE
        )

        assertEquals(listOf(3600L, 5400L, 5400L, 6300L), bars.map { it.durationSeconds })
        // 单调不减：最后一个月必须不小于之前任何一个值。
        assertEquals(bars.maxOf { it.durationSeconds }, bars.last().durationSeconds)
    }

    @Test
    fun `empty months produce empty bars`() {
        assertEquals(emptyList<com.example.yanji.data.DayBarData>(), yearTrendBars(emptyList(), TrendMode.BAR))
        assertEquals(emptyList<com.example.yanji.data.DayBarData>(), yearTrendBars(emptyList(), TrendMode.LINE))
    }
}
