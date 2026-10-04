package com.example.yanji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「上周 / 上月」回看的口径测试。
 *
 * 回看最容易错的地方不是加减日期，而是**边界的锚点**：
 * 一个自然周必须是周一至周日、一个自然月必须是 1 日至月末，
 * 视频「上周」时把 7 根柱子当成「今天往前 7 天」滚动窗口画，
 * 图表第一格就成了上周三，读者完全对不上「昨天」在哪。
 *
 * 全部用注入的固定 today（2026-06-15，周一）而不是系统当天：
 * 否则一到跨周 / 跨月 / 闰年二月，测试结论跟着漂移。
 */
class StatsPreviousPeriodTest {

    private val repo = StudyStatisticsRepository(YanjiRepository.getInstance())

    /** 2026-06-15 是周一，本周 = 6/15..6/21，上周 = 6/8..6/14。 */
    private val today: LocalDate = LocalDate.of(2026, 6, 15)

    private val zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Shanghai")

    /** 固定时钟：区间断言必须用同一个「今天」，否则结论随跑测试的那天漂移。 */
    private val clock: java.time.Clock = java.time.Clock.fixed(
        today.atTime(LocalTime.NOON).atZone(zone).toInstant(), zone
    )

    private fun at(date: LocalDate, hour: Int = 12): Long =
        date.atTime(LocalTime.of(hour, 0)).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun focus(seconds: Long, date: LocalDate, status: SessionStatus = SessionStatus.COMPLETED) =
        FocusSession(
            id = "fs-$seconds-$date",
            subjectId = "math_advanced",
            subjectName = "高等数学",
            startTime = at(date),
            endTime = at(date) + seconds * 1000,
            durationSeconds = seconds,
            status = status
        )

    // ---- 区间边界 ----

    @Test
    fun `week range is a calendar monday through sunday window`() {
        val thisWeek = YanjiTime.weekRange(clock = clock, zoneId = zone)
        val lastWeek = YanjiTime.weekRange(weeksBack = 1, clock = clock, zoneId = zone)
        val twoWeeksBack = YanjiTime.weekRange(weeksBack = 2, clock = clock, zoneId = zone)

        val start = YanjiTime.localDate(thisWeek.startInclusive, zone)
        assertEquals(LocalDate.of(2026, 6, 15), start)
        assertEquals(java.time.DayOfWeek.MONDAY, start.dayOfWeek)
        // 本周周一 = 上周周一 + 7 天，且上周终点正好接本周起点：两段无缝且不重叠。
        assertEquals(start, YanjiTime.localDate(lastWeek.startInclusive, zone).plusWeeks(1))
        assertEquals(thisWeek.startInclusive, lastWeek.endExclusive)
        assertEquals(lastWeek.startInclusive, twoWeeksBack.endExclusive)
    }

    @Test
    fun `month range is a calendar first through last day window`() {
        val thisMonth = YanjiTime.monthRange(clock = clock, zoneId = zone)
        val lastMonth = YanjiTime.monthRange(monthsBack = 1, clock = clock, zoneId = zone)

        assertEquals(LocalDate.of(2026, 6, 1), YanjiTime.localDate(thisMonth.startInclusive, zone))
        assertEquals(LocalDate.of(2026, 5, 1), YanjiTime.localDate(lastMonth.startInclusive, zone))
        // 上月终点 = 本月 1 号，两段无缝且不重叠。
        assertEquals(thisMonth.startInclusive, lastMonth.endExclusive)
        // 5 月 31 天，所以区间恰好覆盖 31 天。
        assertEquals(31L, daysIn(lastMonth))
    }

    @Test
    fun `previous month of a short month keeps its real length`() {
        // 从 3 月回看 2 月：2026 不是闰年，2 月必须是 28 天，
        // 不能被「本月」的 31 天带偏成 30 / 31 天。
        val marchClock = java.time.Clock.fixed(
            LocalDate.of(2026, 3, 17).atTime(LocalTime.NOON).atZone(zone).toInstant(), zone
        )
        val february = YanjiTime.monthRange(monthsBack = 1, clock = marchClock, zoneId = zone)

        assertEquals(28L, daysIn(february))
        assertEquals(LocalDate.of(2026, 2, 1), YanjiTime.localDate(february.startInclusive, zone))
    }

    /** 区间跨的天数（该时区无夏令时，按 86400000ms 整除即天数）。 */
    private fun daysIn(range: EpochRange): Long =
        (range.endExclusive - range.startInclusive) / 86_400_000L

    // ---- 周汇总回看 ----

    @Test
    fun `previous week summary spans monday through sunday of that week`() {
        val summary = repo.buildWeeklyStudySummary(
            focusList = listOf(
                focus(3600, LocalDate.of(2026, 6, 8)),  // 上周一
                focus(3600, LocalDate.of(2026, 6, 14)), // 上周日
                focus(3600, LocalDate.of(2026, 6, 15))  // 本周一，不属于上周
            ),
            examList = emptyList(),
            weeksBack = 1,
            today = today
        )

        assertEquals(7, summary.days.size)
        assertEquals("2026-06-08", summary.days.first().date)
        assertEquals("周一", summary.days.first().dayLabel)
        assertEquals("2026-06-14", summary.days.last().date)
        assertEquals("周日", summary.days.last().dayLabel)
        // 只有上周那两条计入；本周一的记录必须被挡在窗口外。
        assertEquals(7200L, summary.totalDurationSeconds)
        // 整周都在过去，所以没有任何一格是「今日」。
        assertTrue(summary.days.none { it.isToday })
    }

    @Test
    fun `current week still marks today`() {
        val summary = repo.buildWeeklyStudySummary(
            focusList = listOf(focus(3600, today)),
            examList = emptyList(),
            weeksBack = 0,
            today = today
        )

        assertEquals("2026-06-15", summary.days.first().date)
        assertTrue(summary.days.first { it.date == "2026-06-15" }.isToday)
        assertTrue(summary.days.drop(1).none { it.isToday })
    }

    @Test
    fun `previous week streak is the trailing run reaching sunday`() {
        // 上周四到周日连续有效：回看时「连续研读」是 4 天。
        val summary = repo.buildWeeklyStudySummary(
            focusList = listOf(
                focus(1800, LocalDate.of(2026, 6, 11)), // 上周四
                focus(1800, LocalDate.of(2026, 6, 12)), // 上周五
                focus(1800, LocalDate.of(2026, 6, 13)), // 上周六
                focus(1800, LocalDate.of(2026, 6, 14))  // 上周日
            ),
            examList = emptyList(),
            weeksBack = 1,
            today = today
        )

        assertEquals(4, summary.streakDays)
        assertEquals(4, summary.activeDays)
    }

    @Test
    fun `previous week streak is truncated by a resting tail`() {
        // 上周五、周六有效，周日休息：连续段在周日就断了。
        // 口径与本周一致 —— 「连续」到尾日才成立，不从一周中段凭空攒一个 streak 出来。
        val summary = repo.buildWeeklyStudySummary(
            focusList = listOf(
                focus(1800, LocalDate.of(2026, 6, 12)), // 上周五
                focus(1800, LocalDate.of(2026, 6, 13))  // 上周六
            ),
            examList = emptyList(),
            weeksBack = 1,
            today = today
        )

        assertEquals(0, summary.streakDays)
        assertEquals(2, summary.activeDays)
    }

    // ---- 月汇总回看 ----

    @Test
    fun `previous month summary spans the first through the last day of that month`() {
        val summary = repo.buildMonthlyStudySummary(
            focusList = listOf(
                focus(3600, LocalDate.of(2026, 5, 1)),
                focus(3600, LocalDate.of(2026, 5, 31))
            ),
            examList = emptyList(),
            monthsBack = 1,
            today = today
        )

        assertEquals(31, summary.days.size)
        assertEquals("2026-05-01", summary.days.first().date)
        assertEquals("2026-05-31", summary.days.last().date)
        assertEquals(7200L, summary.totalDurationSeconds)
        // 报的是展示中的那个月，不是今天所在的 6 月：抽屉和热力图靠这两个数认月份。
        assertEquals(2026, summary.year)
        assertEquals(5, summary.month)
        assertFalse(summary.days.any { it.isToday })
    }

    @Test
    fun `previous month february has 28 days in a common year`() {
        val summary = repo.buildMonthlyStudySummary(
            focusList = emptyList(),
            examList = emptyList(),
            monthsBack = 1,
            today = LocalDate.of(2026, 3, 17)
        )

        assertEquals(28, summary.days.size)
        assertEquals(2, summary.month)
    }

    @Test
    fun `previous month first day of week matches that month`() {
        // 2026 年 5 月 1 日是周五：热力图的前导空格按它算，
        // 拿本月（6 月 1 日是周一）的首星期几去画上月的格子会整体错位。
        val summary = repo.buildMonthlyStudySummary(
            focusList = emptyList(),
            examList = emptyList(),
            monthsBack = 1,
            today = today
        )

        assertEquals(java.time.DayOfWeek.FRIDAY, summary.firstDayOfWeek)
    }
}
