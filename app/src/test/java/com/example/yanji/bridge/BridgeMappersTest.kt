package com.example.yanji.bridge

import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyTask
import com.example.yanji.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桥接纯映射测试（F3 / F4 / F8）。
 *
 * 这些映射全部接收普通 Kotlin 类型、返回普通数据类 —— `Arguments.createMap()`
 * 只在 Android runtime 上可用，因此契约语义必须在纯 JVM 侧断言。
 */
class BridgeMappersTest {

    // ------------------------------------------------------------------ F4 actualMinutes

    @Test
    fun taskFields_emitsRealActualMinutesFromSeconds() {
        val task = StudyTask(
            id = "t-1",
            date = "2026-10-08",
            subjectId = "math",
            subjectName = "高等数学",
            title = "刷完 1800",
            plannedMinutes = 90
        )

        // 5400 秒 = 90 分钟
        val fields = BridgeMappers.taskFields(task, actualSeconds = 5400L)
        assertEquals(90, fields.actualMinutes)
        assertEquals(90, fields.plannedMinutes)
        assertFalse(fields.completed)
    }

    @Test
    fun taskFields_truncatesSubMinuteRemainderInsteadOfRoundingUp() {
        val task = StudyTask(id = "t-2", date = "2026-10-08", subjectId = "math", subjectName = "数学", title = "x", plannedMinutes = 10)

        // 3599 秒 → 59 分钟（不是 60）：与 mock-bridge.js 的整数除法一致
        assertEquals(59, BridgeMappers.taskFields(task, 3599L).actualMinutes)
        // 61 秒 → 1 分钟
        assertEquals(1, BridgeMappers.taskFields(task, 61L).actualMinutes)
    }

    @Test
    fun taskFields_missingEntryMeansZeroBecauseNothingWasActuallyInvested() {
        val task = StudyTask(id = "t-3", date = "2026-10-08", subjectId = "math", subjectName = "数学", title = "x", plannedMinutes = 10)

        // 没有关联时段时是**真实值 0**，不是占位假数据
        assertEquals(0, BridgeMappers.taskFields(task, null).actualMinutes)
        assertEquals(0, BridgeMappers.taskFields(task, 0L).actualMinutes)
    }

    @Test
    fun actualMinutesOf_neverGoesNegative() {
        assertEquals(0, BridgeMappers.actualMinutesOf(-5L))
        assertEquals(0, BridgeMappers.actualMinutesOf(0L))
        assertEquals(1, BridgeMappers.actualMinutesOf(60L))
    }

    // ------------------------------------------------------------------ F3 note sessionId

    @Test
    fun noteFields_emitsNullSessionIdWhenUnbound() {
        val note = NoteEntry(id = "n-1", date = "2026-10-08", content = "卡在中值定理")

        val fields = BridgeMappers.noteFields(note)
        // 契约要求 string | null，空串是违法的
        assertNull(fields.sessionId)
    }

    @Test
    fun noteFields_emitsPersistedSessionIdWhenBound() {
        val note = NoteEntry(id = "n-2", date = "2026-10-08", content = "卡在中值定理", sessionId = "focus-42")

        val fields = BridgeMappers.noteFields(note)
        assertEquals("focus-42", fields.sessionId)
    }

    @Test
    fun noteFields_blankSessionIdIsNormalisedToNull() {
        val note = NoteEntry(id = "n-3", date = "2026-10-08", content = "x", sessionId = "   ")

        assertNull(BridgeMappers.noteFields(note).sessionId)
    }

    @Test
    fun noteFields_carriesTheRemainingContractFields() {
        val note = NoteEntry(
            id = "n-4",
            date = "2026-10-08",
            content = "复盘",
            createdAt = 1_700_000_000_000L,
            isFavorite = true,
            sessionId = "focus-1"
        )

        val fields = BridgeMappers.noteFields(note)
        assertEquals("n-4", fields.id)
        assertEquals("2026-10-08", fields.date)
        assertEquals(1_700_000_000_000.0, fields.timestamp, 0.0)
        assertEquals("复盘", fields.content)
        assertTrue(fields.isFavorite)
        assertEquals("focus-1", fields.sessionId)
    }

    // ------------------------------------------------------------------ F8 removed settings

    @Test
    fun settingsFields_noLongerContainsFabricatedFocusOrBreakDurations() {
        val settings = UserSettings(
            targetExamDate = "2026-12-19",
            targetSchool = "浙江大学",
            targetMajor = "电子信息",
            themeMode = "DARK"
        )

        val fields = BridgeMappers.settingsFields(settings)

        // 反射级别的防线：字段被删掉后，任何人重新加回来都会让这个测试失败
        val fieldNames = fields.javaClass.declaredFields.map { it.name }.toSet()
        assertFalse(
            "focusDurationMinutes 已被移除（领域模型没有这个字段，任何取值都是编造）",
            "focusDurationMinutes" in fieldNames
        )
        assertFalse(
            "breakDurationMinutes 已被移除（同上）",
            "breakDurationMinutes" in fieldNames
        )

        // 剩下的字段仍然如实映射
        assertEquals("2026-12-19", fields.examDate)
        assertEquals("浙江大学", fields.targetSchool)
        assertEquals("电子信息", fields.targetMajor)
        assertEquals("DARK", fields.themePreference)
    }

    // ------------------------------------------------------------------ F5 rolling window

    @Test
    fun rollingWindowDates_endsAtTodayAndNeverIncludesTheFuture() {
        val today = java.time.LocalDate.of(2026, 10, 8)

        val dates = BridgeMappers.rollingWindowDates(today.minusDays(6), 7)

        assertEquals(7, dates.size)
        assertEquals("2026-10-02", dates.first())
        assertEquals("2026-10-08", dates.last())
        assertFalse("窗口绝不可含未来日期", dates.contains("2026-10-09"))
        // 键按「早 → 晚」排序
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun rollingWindowDates_honoursDaysExactly() {
        val today = java.time.LocalDate.of(2026, 10, 8)

        assertEquals(1, BridgeMappers.rollingWindowDates(today, 1).size)
        assertEquals(30, BridgeMappers.rollingWindowDates(today.minusDays(29), 30).size)
    }

    @Test
    fun rollingWindowDates_isCalendarBasedNotEpochBased() {
        // 跨夏令时边界（2026-03-29 欧洲切换）逐日推进，绝不跳号或重复
        val start = java.time.LocalDate.of(2026, 3, 28)
        val dates = BridgeMappers.rollingWindowDates(start, 3)

        assertEquals(listOf("2026-03-28", "2026-03-29", "2026-03-30"), dates)
    }

    // ------------------------------------------------------------------ 复盘概览

    /** 一天的 25 分 59 秒：逐日先截断与窗口内先累加的结果差着 6 分钟（203 vs 209）。 */
    private val almostHalfHour = 1799L

    @Test
    fun subjectBucket_mergesCustomIdsOfTheSameCategoryIntoOneDirectBucket() {
        // 目录里没有任何 id 为 custom-math-x 的学科，但名字里有「数学」→
        // 必须归到 math 的直接桶，而不是自成一个桶。
        val (bucketId, displayName) = BridgeMappers.subjectBucket("custom-math-x", "数学一（强化）")

        assertEquals("math__unclassified", bucketId)
        assertEquals("数学一（综合/未细分）", displayName)
    }

    @Test
    fun subjectBucket_keepsKnownSubcategoryIdAndName() {
        assertEquals(
            "math_linear" to "线性代数",
            BridgeMappers.subjectBucket("math_linear", "线性代数")
        )
    }

    @Test
    fun reviewOverviewFields_truncatesMinutesOnceAfterSummingSeconds() {
        val dates = (2..8).map { "2026-10-%02d".format(it) }
        val daySeconds = dates.associateWith { almostHalfHour }
        // 每天恰好一条 25 分 59 秒的专注，全部落在同一个展示桶里。
        val slice = BridgeMappers.SubjectSecondsSlice("math_linear", "线性代数", almostHalfHour)

        val overview = BridgeMappers.reviewOverviewFields(
            scope = BridgeMappers.SCOPE_ROLLING_7,
            periodsBack = 0,
            windowDates = dates,
            todayIso = "2026-10-08",
            daySecondsByDate = daySeconds,
            subjectSecondsByDate = dates.associateWith { listOf(slice) },
            subjectTotals = listOf(slice.copy(seconds = almostHalfHour * dates.size)),
            examCount = 0
        )

        assertEquals("秒数不被截断", 7L * almostHalfHour, overview.totalSeconds)
        assertEquals(7, overview.windowDays)
        // 窗口终点就是今天，7 天全部已过，所以「已过去天数」与窗口长度在这里相等。
        // 两者分道扬镳的场景见 reviewOverviewFields_marksDaysAfterTodayAsFutureWithZeroSeconds。
        assertEquals(7L * almostHalfHour / 7L, overview.dailyAverageSeconds)
        val subject = overview.subjectDistribution.single()
        // 唯一的截断点：7×1799 = 12593 秒 → 12593/60 = 209 分钟
        assertEquals(209, subject.minutes)
        // 单日仍然是如实的 29 分 59 秒 → 29 分钟
        assertEquals(29, subject.dailyMinutes["2026-10-02"])
        // 若逐日先截断再相加，这里会是 7×29 = 203；subject.minutes 必须是 209
        assertTrue("逐日先截断会得到 203", subject.minutes != 29 * 7)
    }

    @Test
    fun reviewOverviewFields_shareIsZeroWhenTheWindowHasNoSeconds() {
        val dates = listOf("2026-10-06", "2026-10-07", "2026-10-08")
        val zeroSlice = BridgeMappers.SubjectSecondsSlice("english", "英语一", 0L)

        val overview = BridgeMappers.reviewOverviewFields(
            scope = BridgeMappers.SCOPE_ROLLING_7,
            periodsBack = 0,
            windowDates = dates,
            todayIso = "2026-10-08",
            daySecondsByDate = emptyMap(),
            subjectSecondsByDate = emptyMap(),
            subjectTotals = listOf(zeroSlice),
            examCount = 0
        )

        assertEquals(0L, overview.totalSeconds)
        assertEquals(0L, overview.dailyAverageSeconds)
        assertTrue(overview.subjectDistribution.all { it.share == 0.0 })
        assertTrue(overview.subjectDistribution.all { it.share in 0.0..1.0 })
    }

    @Test
    fun reviewOverviewFields_shareSumsToOneWhenThereAreSeconds() {
        val dates = listOf("2026-10-06", "2026-10-07", "2026-10-08")
        // 逐日分布与窗口合计必须自洽：每天 1800 + 900，合计 × 3 天
        val perDay = listOf(
            BridgeMappers.SubjectSecondsSlice("math_linear", "线性代数", 1800L),
            BridgeMappers.SubjectSecondsSlice("english", "英语一", 900L)
        )
        val totals = perDay.map { it.copy(seconds = it.seconds * dates.size) }

        val overview = BridgeMappers.reviewOverviewFields(
            scope = BridgeMappers.SCOPE_ROLLING_7,
            periodsBack = 0,
            windowDates = dates,
            todayIso = "2026-10-08",
            daySecondsByDate = dates.associateWith { 1800L + 900L },
            subjectSecondsByDate = dates.associateWith { perDay },
            subjectTotals = totals,
            examCount = 0
        )

        assertEquals(3L * 2700L, overview.totalSeconds)
        val shareSum = overview.subjectDistribution.sumOf { it.share }
        assertEquals(1.0, shareSum, 0.0001)
        assertTrue(overview.subjectDistribution.all { it.share >= 0.0 && it.share <= 1.0 })
    }

    @Test
    fun reviewOverviewFields_subjectOrderIsDeterministicOnTieBreaks() {
        val dates = listOf("2026-10-06", "2026-10-07", "2026-10-08")
        val slices = listOf(
            BridgeMappers.SubjectSecondsSlice("politics", "政治", 600L),
            BridgeMappers.SubjectSecondsSlice("english", "英语一", 600L),
            BridgeMappers.SubjectSecondsSlice("math", "数学一", 1200L)
        )
        fun build(): List<String> = BridgeMappers.reviewOverviewFields(
            scope = BridgeMappers.SCOPE_ROLLING_7,
            periodsBack = 0,
            windowDates = dates,
            todayIso = "2026-10-08",
            daySecondsByDate = dates.associateWith { if (it == "2026-10-06") 2400L else 0L },
            subjectSecondsByDate = mapOf("2026-10-06" to slices),
            subjectTotals = slices,
            examCount = 0
        ).subjectDistribution.map { "${it.subjectId}:${it.minutes}" }

        // 「数学一」是子类 id，但这里的桶归一走 `subcategoryBucketId`：`math` 本身是
        // 一个**有子类的大类**，直接记在大类上的会话会落进它的 direct bucket
        // （`math__unclassified`），而没有子类的 english / politics 保持自身 id。
        val expected = listOf("math__unclassified:20", "english:10", "politics:10")
        assertEquals(expected, build())
        repeat(100) { assertEquals(expected, build()) }
    }

    @Test
    fun reviewOverviewFields_dailyMinutesCoverEveryWindowDayIncludingEmptyOnes() {
        val dates = listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09")
        val slice = BridgeMappers.SubjectSecondsSlice("major_data_structure", "数据结构", 3600L)

        val overview = BridgeMappers.reviewOverviewFields(
            scope = BridgeMappers.SCOPE_ROLLING_7,
            periodsBack = 0,
            windowDates = dates,
            todayIso = "2026-10-08",
            daySecondsByDate = mapOf("2026-10-07" to 3600L),
            subjectSecondsByDate = mapOf("2026-10-07" to listOf(slice)),
            subjectTotals = listOf(slice),
            examCount = 0
        )

        val windowDates = overview.days.map { it.date }
        assertEquals("窗口内不得出现重复键", windowDates.size, windowDates.toSet().size)
        assertEquals(windowDates, windowDates.sorted())
        overview.subjectDistribution.forEach { subject ->
            assertEquals("键集合必须与 days 完全相等", windowDates.toSet(), subject.dailyMinutes.keys)
            // 缺键的那天必须是 0，不能没有这条记录
            assertEquals(0, subject.dailyMinutes["2026-10-05"])
            assertEquals(60, subject.dailyMinutes["2026-10-07"])
        }
    }

    @Test
    fun reviewScopeLabel_matchesEachScope() {
        // 自然周：周一 2026-10-05 → 周日 2026-10-11
        val week = (5..11).map { java.time.LocalDate.of(2026, 10, it) }
        // 自然月 2026-10
        val month = (1..5).map { java.time.LocalDate.of(2026, 10, it) }
        // 滚动窗口各自的真实长度，终点都落在今天 2026-10-11。
        val today = java.time.LocalDate.of(2026, 10, 11)
        val rolling7 = List(7) { today.minusDays((6 - it).toLong()) }
        val rolling30 = List(30) { today.minusDays((29 - it).toLong()) }

        assertEquals(
            "最近 7 天",
            BridgeMappers.reviewScopeLabel(BridgeMappers.SCOPE_ROLLING_7, 0, rolling7, "2026-10-11")
        )
        assertEquals(
            "最近 30 天",
            BridgeMappers.reviewScopeLabel(BridgeMappers.SCOPE_ROLLING_30, 0, rolling30, "2026-10-11")
        )
        assertEquals(
            "10月5日 - 10月11日",
            BridgeMappers.reviewScopeLabel(BridgeMappers.SCOPE_CALENDAR_WEEK, 0, week, "2026-10-11")
        )
        assertEquals(
            "2026年10月",
            BridgeMappers.reviewScopeLabel(BridgeMappers.SCOPE_CALENDAR_MONTH, 0, month, "2026-10-11")
        )
    }

    @Test
    fun reviewScopeLabel_reportsRealDatesWhenARollingWindowIsAnchoredInThePast() {
        // 用户把日期翻到 9 月 15 日，滚动窗口就是 9 月 9 日–15 日。
        // 照抄「最近 7 天」等于用一个假区间标题描述一段真区间。
        val anchored = List(7) { java.time.LocalDate.of(2026, 9, 15).minusDays((6 - it).toLong()) }

        assertEquals(
            "9月9日 - 9月15日",
            BridgeMappers.reviewScopeLabel(BridgeMappers.SCOPE_ROLLING_7, 0, anchored, "2026-10-10")
        )
    }

    @Test
    fun reviewScopeLabel_showsBothDatesForACalendarWeekThatCrossesMonths() {
        // 自然周跨月时如实显示两段日期，不折算成「第 N 周」
        val crossMonth = List(7) { java.time.LocalDate.of(2026, 10, 30).plusDays(it.toLong()) }

        assertEquals(
            "10月30日 - 11月5日",
            BridgeMappers.reviewScopeLabel(BridgeMappers.SCOPE_CALENDAR_WEEK, 1, crossMonth, "2026-11-05")
        )
    }

    @Test
    fun reviewOverviewFields_marksDaysAfterTodayAsFutureWithZeroSeconds() {
        val month = (1..5).map { "2026-10-%02d".format(it) }

        val overview = BridgeMappers.reviewOverviewFields(
            scope = BridgeMappers.SCOPE_CALENDAR_MONTH,
            periodsBack = 0,
            windowDates = month,
            todayIso = "2026-10-03",
            daySecondsByDate = mapOf("2026-10-02" to 1800L),
            subjectSecondsByDate = emptyMap(),
            subjectTotals = emptyList(),
            examCount = 0
        )

        // 滚动口径是 MM-DD，日历月口径是「D日」——两者绝不可串
        assertEquals("1日", overview.days.first().dayLabel)
        assertEquals("3日", overview.days[2].dayLabel)
        assertEquals("10-01", BridgeMappers.reviewDayLabel(BridgeMappers.SCOPE_ROLLING_7, "2026-10-01"))
        val future = overview.days.filter { it.isFuture }
        assertEquals(listOf("2026-10-04", "2026-10-05"), future.map { it.date })
        assertTrue(future.all { it.durationSeconds == 0L })
        assertFalse(overview.days[1].isFuture)
        assertTrue(overview.days[2].isToday)

        // 日均的分母是**已过去的 3 天**（10-01..10-03），不是窗口的 5 天。
        // 旧口径给 1800/5 = 360，把「每天 10 分钟」稀释成用户无法解释的数字。
        assertEquals(5, overview.windowDays)
        assertEquals(1800L / 3L, overview.dailyAverageSeconds)
    }

    @Test
    fun reviewDayLabel_usesWeekdayNamesForCalendarWeek() {
        assertEquals("周一", BridgeMappers.reviewDayLabel(BridgeMappers.SCOPE_CALENDAR_WEEK, "2026-10-05"))
        assertEquals("周日", BridgeMappers.reviewDayLabel(BridgeMappers.SCOPE_CALENDAR_WEEK, "2026-10-11"))
        assertEquals("10-09", BridgeMappers.reviewDayLabel(BridgeMappers.SCOPE_ROLLING_7, "2026-10-09"))
    }
}
