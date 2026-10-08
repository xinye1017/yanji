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

    @Test
    fun reviewStatsFields_dividesTheAverageByTheWindowLengthNotActiveDays() {
        val daySeconds = linkedMapOf(
            "2026-10-06" to 3600L, // 60 分钟
            "2026-10-07" to 0L,
            "2026-10-08" to 3600L // 60 分钟
        )

        val stats = BridgeMappers.reviewStatsFields(
            windowDays = 3,
            daySecondsByDate = daySeconds,
            subjectMinutesBySubject = mapOf("高等数学" to 120),
            activeDayThresholdSeconds = 1800L
        )

        // 120 分钟 / 3 天 = 40 —— 分母是窗口长度，不是有效天数 2
        assertEquals(40, stats.dailyAverageMinutes)
        assertEquals(3, stats.days)
        assertEquals(2, stats.activeDays)
        assertEquals(2.0, stats.totalFocusHours, 0.0001)
        assertEquals(60, stats.dailyFocusMinutes["2026-10-06"])
        assertEquals(0, stats.dailyFocusMinutes["2026-10-07"])
        assertEquals(60, stats.dailyFocusMinutes["2026-10-08"])
        assertEquals(120, stats.subjectDistribution["高等数学"])
    }

    @Test
    fun reviewStatsFields_activeDaysUsesTheThirtyMinuteThreshold() {
        val daySeconds = linkedMapOf(
            "2026-10-06" to 1799L, // 29 分 59 秒 → 不活跃
            "2026-10-07" to 1800L, // 恰好 30 分钟 → 活跃
            "2026-10-08" to 3600L
        )

        val stats = BridgeMappers.reviewStatsFields(
            windowDays = 3,
            daySecondsByDate = daySeconds,
            subjectMinutesBySubject = emptyMap(),
            activeDayThresholdSeconds = 1800L
        )

        assertEquals(2, stats.activeDays)
        // 29 分钟也被如实记录为当天的分钟数（只是不算「有效学习日」）
        assertEquals(29, stats.dailyFocusMinutes["2026-10-06"])
    }

    @Test
    fun reviewStatsFields_emptyWindowReportsZerosWithoutFabricatingAnything() {
        val stats = BridgeMappers.reviewStatsFields(
            windowDays = 7,
            daySecondsByDate = (1..7).associate { "2026-10-0$it" to 0L },
            subjectMinutesBySubject = emptyMap(),
            activeDayThresholdSeconds = 1800L
        )

        assertEquals(7, stats.days)
        assertEquals(0, stats.activeDays)
        assertEquals(0, stats.dailyAverageMinutes)
        assertEquals(0.0, stats.totalFocusHours, 0.0001)
        assertTrue(stats.subjectDistribution.isEmpty())
        assertEquals(7, stats.dailyFocusMinutes.size)
        assertTrue(stats.dailyFocusMinutes.values.all { it == 0 })
    }

    @Test
    fun reviewStatsFields_degenerateWindowIsCoercedInsteadOfDividingByZero() {
        val stats = BridgeMappers.reviewStatsFields(
            windowDays = 0,
            daySecondsByDate = mapOf("2026-10-08" to 1800L),
            subjectMinutesBySubject = emptyMap(),
            activeDayThresholdSeconds = 1800L
        )

        assertEquals("days <= 0 必须被夹紧为 1", 1, stats.days)
        assertEquals(30, stats.dailyAverageMinutes)
    }
}
