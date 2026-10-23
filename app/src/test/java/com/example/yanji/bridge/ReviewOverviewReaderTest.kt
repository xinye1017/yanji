package com.example.yanji.bridge

import com.example.yanji.data.YanjiTime
import com.example.yanji.data.db.ExamStatsRow
import com.example.yanji.data.db.FocusStatsRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 批量回顾读取器的窗口与聚合口径。
 *
 * 这些用例锁的是「一次区间扫描 + 多窗口过滤」不能改变数字：窗口外的行必须被排除、
 * 模考取 actualDurationSeconds、分钟只在求和后截断一次。
 */
class ReviewOverviewReaderTest {

    private val day = LocalDate.of(2026, 10, 10)
    private val dayRange = YanjiTime.dayRange(day)
    private val dayIso = "2026-10-10"

    private fun window(dates: List<String>) = ReviewOverviewReader.Window(dayRange, dates)

    @Test
    fun build_excludesRowsOutsideTheWindowAndKeepsExamSeconds() {
        val focus = listOf(
            FocusStatsRow(dayRange.startInclusive + 3_600_000, "math_linear", "线性代数", 1_800),
            // 次日凌晨：不在窗口内，绝不能计入
            FocusStatsRow(dayRange.endExclusive + 1_000, "math_linear", "线性代数", 9_999)
        )
        val exams = listOf(
            ExamStatsRow(dayRange.startInclusive + 7_200_000, "english", "英语一", 600)
        )

        val overview = ReviewOverviewReader.build(
            scope = BridgeMappers.SCOPE_TODAY,
            periodsBack = 0,
            window = window(listOf(dayIso)),
            focus = focus,
            exams = exams,
            todayIso = "2026-10-11"
        )

        assertEquals(2_400L, overview.totalSeconds)
        assertEquals(1, overview.examCount)
        assertEquals(1, overview.windowDays)
        assertEquals(2_400L, overview.days.single().durationSeconds)
        assertFalse(overview.days.single().isFuture)
        assertEquals(40, overview.subjectDistribution.sumOf { it.minutes })
        assertEquals(
            30,
            overview.subjectDistribution.first { it.subjectId == "math_linear" }.dailyMinutes[dayIso]
        )
        assertEquals(1.0, overview.subjectDistribution.sumOf { it.share }, 0.0001)
    }

    @Test
    fun build_truncatesMinutesOnceAfterSummingSeconds() {
        val almostHalfHour = 1_799L
        val focus = (0..6).map { offset ->
            FocusStatsRow(
                dayRange.startInclusive + (offset + 1) * 60_000L,
                "math_linear",
                "线性代数",
                almostHalfHour
            )
        }

        val overview = ReviewOverviewReader.build(
            scope = BridgeMappers.SCOPE_TODAY,
            periodsBack = 0,
            window = window(listOf(dayIso)),
            focus = focus,
            exams = emptyList(),
            todayIso = dayIso
        )

        assertEquals(7L * almostHalfHour, overview.totalSeconds)
        // 唯一截断点：7×1799 = 12593 秒 → 209 分钟；逐行先截断会得到 7×29 = 203。
        assertEquals(209, overview.subjectDistribution.single().minutes)
    }

    @Test
    fun calendarWeekWindowStartsOnMondayAndHonoursPeriodsBack() {
        val current = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2026-10-10")
        )
        assertEquals(listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"), current.dates)

        val previous = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 1, "2026-10-10")
        )
        assertEquals("2026-09-28", previous.dates.first())
        assertEquals("2026-10-04", previous.dates.last())
    }

    @Test
    fun todayWindowIgnoresPeriodsBack() {
        val window = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_TODAY, 30, dayIso)
        )
        assertEquals(listOf(dayIso), window.dates)
        assertEquals(dayRange.startInclusive, window.range.startInclusive)
        assertEquals(dayRange.endExclusive, window.range.endExclusive)
    }

    @Test
    fun calendarMonthWindowUsesTheAnchorMonthLength() {
        val february = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_MONTH, 0, "2026-02-17")
        )
        assertEquals(28, february.dates.size)
        assertTrue(february.dates.first() == "2026-02-01")
        assertTrue(february.dates.last() == "2026-02-28")
    }
}
