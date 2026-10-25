package com.example.yanji.bridge

import com.example.yanji.data.YanjiTime
import com.example.yanji.data.db.ExamStatsRow
import com.example.yanji.data.db.FocusStatsRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

    private class CountingList<T>(private val rows: List<T>) : AbstractList<T>() {
        var visits = 0
            private set
        override val size: Int get() = rows.size
        override fun get(index: Int): T {
            visits++
            return rows[index]
        }
    }

    @Test
    fun buildAll_traversesRowsOncePerUniqueWindowAndPreservesRequestMetadata() {
        val requests = listOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2026-10-06"),
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2026-10-07"),
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2026-10-08"),
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 1, "2026-10-13"),
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_MONTH, 0, "2026-10-01"),
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_MONTH, 0, "2026-10-31"),
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_MONTH, 1, "2026-11-30")
        )
        val windows = requests.map { ReviewOverviewReader.windowOf(it) }
        val focusRows = listOf(
            FocusStatsRow(YanjiTime.dayRange(LocalDate.of(2026, 10, 6)).startInclusive, "math_linear", "线性代数", 1_799),
            FocusStatsRow(YanjiTime.dayRange(LocalDate.of(2026, 10, 2)).startInclusive, "math_linear", "线性代数", 61)
        )
        val examRows = listOf(
            ExamStatsRow(YanjiTime.dayRange(LocalDate.of(2026, 10, 6)).startInclusive, "math_linear", "线性代数", 61)
        )
        val focus = CountingList(focusRows)
        val exams = CountingList(examRows)
        val overviews = ReviewOverviewReader.buildAll(requests, windows, focus, exams, "2026-10-10")

        // Seven raw requests used to traverse every row seven times. Two unique
        // windows now visit the two focus rows four times, and the exam twice.
        assertEquals(4, focus.visits)
        assertEquals(2, exams.visits)
        assertEquals(7, overviews.size)
        requests.indices.forEach { index ->
            val request = requests[index]
            val expected = ReviewOverviewReader.build(
                request.scope, request.effectivePeriodsBack.toInt(), windows[index],
                focusRows, examRows, "2026-10-10"
            )
            assertEquals(expected, overviews[index])
        }
        assertEquals(1, overviews[3].periodsBack)
        assertEquals(1, overviews[6].periodsBack)
        assertEquals(1_860L, overviews[0].totalSeconds)
        assertEquals(31, overviews[0].subjectDistribution.single().minutes)
        assertEquals(1_921L, overviews[4].totalSeconds)
        assertEquals(32, overviews[4].subjectDistribution.single().minutes)
        assertEquals(1, overviews[0].examCount)
        assertSame(overviews[0].days, overviews[3].days)
        assertSame(overviews[0].subjectDistribution, overviews[3].subjectDistribution)
        assertSame(overviews[4].days, overviews[6].days)
    }

    @Test
    fun equivalentWindowsKeepCalendarBoundariesAcrossYearsAndLeapMonths() {
        val crossYear = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2027-01-03")
        )
        val previousWeek = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 1, "2027-01-10")
        )
        assertEquals(crossYear, previousWeek)
        assertEquals("2026-12-28", crossYear.dates.first())
        assertEquals("2027-01-03", crossYear.dates.last())
        val monday = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2027-01-04")
        )
        assertEquals("2027-01-04", monday.dates.first())
        val february = ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_MONTH, 1, "2028-03-31")
        )
        assertEquals(29, february.dates.size)
        assertEquals("2028-02-01", february.dates.first())
        assertEquals("2028-02-29", february.dates.last())
        assertEquals(february, ReviewOverviewReader.windowOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_MONTH, 0, "2028-02-01")
        ))
    }

    @Test
    fun buildAll_keepsTodayCutoffAndDoesNotReuseYesterdayFlagsAcrossBatches() {
        val requests = listOf(
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2026-10-06"),
            ReviewOverviewReader.Request(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, "2026-10-08")
        )
        val windows = requests.map { ReviewOverviewReader.windowOf(it) }
        val focus = listOf(FocusStatsRow(
            YanjiTime.dayRange(LocalDate.of(2026, 10, 6)).startInclusive, "math_linear", "线性代数", 84
        ))
        val wednesday = ReviewOverviewReader.buildAll(requests, windows, focus, emptyList(), "2026-10-07")
        val thursday = ReviewOverviewReader.buildAll(requests, windows, focus, emptyList(), "2026-10-08")
        assertEquals(28L, wednesday[0].dailyAverageSeconds)
        assertEquals(21L, thursday[0].dailyAverageSeconds)
        assertEquals("2026-10-07", wednesday[0].days.single { it.isToday }.date)
        assertEquals("2026-10-08", thursday[0].days.single { it.isToday }.date)
        assertTrue(wednesday[1].days.first { it.date == "2026-10-08" }.isFuture)
        assertFalse(thursday[1].days.first { it.date == "2026-10-08" }.isFuture)
        assertSame(thursday[0].days, thursday[1].days)
    }

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
