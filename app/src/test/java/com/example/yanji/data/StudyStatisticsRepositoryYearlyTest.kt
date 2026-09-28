package com.example.yanji.data

import com.example.yanji.data.study.StudyStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 本年汇总的口径测试。
 *
 * 钉住四件事，它们都是「本年」视角最容易算错的地方：
 * 1. 日均的分母是**有效学习天数**，不是自然日跨度（年初空档会把日均稀释到失真）；
 * 2. 有效天数阈值 30min 与周/月汇总一致；
 * 3. 连续天数是本年**最长的一段**，不是「当前连续」；
 * 4. 1 月..当月全部占位，没学过的月份也要有一根 0 柱，否则横轴会随数据跳动。
 *
 * 全部用注入的固定 today（2026-06-15）而不是系统当天：否则一到跨年/年初，\ * 「今天往前 5 天」就会落到上一个自然年，测试结论跟着漂移。
 */
class StudyStatisticsRepositoryYearlyTest {

    private val repo = StudyStatisticsRepository(YanjiRepository.getInstance())

    private val today: LocalDate = LocalDate.of(2026, 6, 15)

    private fun at(date: LocalDate, hour: Int = 12): Long =
        date.atTime(LocalTime.of(hour, 0)).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun focus(seconds: Long, date: LocalDate, status: SessionStatus = SessionStatus.COMPLETED) =
        FocusSession(
            id = "fs-$seconds-${date}",
            subjectId = "math_advanced",
            subjectName = "高等数学",
            startTime = at(date),
            endTime = at(date) + seconds * 1000,
            durationSeconds = seconds,
            status = status
        )

    private fun exam(seconds: Long, date: LocalDate, status: SessionStatus = SessionStatus.COMPLETED) =
        ExamSession(
            id = "es-$seconds-${date}",
            subjectId = "math",
            subjectName = "数学一",
            actualDurationSeconds = seconds,
            startTime = at(date),
            endTime = at(date) + seconds * 1000,
            status = status
        )

    @Test
    fun `daily average divides by active days not calendar span`() {
        // 两个有效日（>= 30min）+ 一个 10 分钟的无效日：跨度 3 天，有效天数 2 天。
        val summary = repo.buildYearlyStudySummary(
            focusList = listOf(
                focus(3600, LocalDate.of(2026, 3, 2)),
                focus(3600, LocalDate.of(2026, 5, 4)),
                focus(600, LocalDate.of(2026, 5, 5))
            ),
            examList = emptyList(),
            today = today
        )

        assertEquals(7800L, summary.totalDurationSeconds)
        assertEquals(2, summary.activeDays)
        // 7800 / 2 = 3900；若按自然日跨度（3 天）算会得到 2600，那就是错的。
        assertEquals(3900L, summary.dailyAverageSeconds)
    }

    @Test
    fun `active day threshold matches weekly and monthly summaries`() {
        val summary = repo.buildYearlyStudySummary(
            focusList = listOf(
                focus(1799, LocalDate.of(2026, 4, 1)), // 差 1 秒不算有效
                focus(1800, LocalDate.of(2026, 4, 2))  // 刚好 30min 算有效
            ),
            examList = emptyList(),
            today = today
        )

        assertEquals(1, summary.activeDays)
    }

    @Test
    fun `streak reports the longest run of the year`() {
        val summary = repo.buildYearlyStudySummary(
            focusList = listOf(
                focus(1800, LocalDate.of(2026, 2, 1)),
                focus(1800, LocalDate.of(2026, 2, 2)),
                focus(1800, LocalDate.of(2026, 2, 3)), // 连续 3 天
                focus(1800, LocalDate.of(2026, 2, 10)),
                focus(1800, LocalDate.of(2026, 2, 11)) // 连续 2 天
            ),
            examList = emptyList(),
            today = today
        )

        // 「当前连续」只有 2 天且早已断掉；年度指标要的是最长那段 3 天。
        assertEquals(3, summary.longestStreakDays)
    }

    @Test
    fun `months are placeheld from january through current month`() {
        val summary = repo.buildYearlyStudySummary(
            focusList = listOf(focus(3600, LocalDate.of(2026, 3, 9))),
            examList = emptyList(),
            today = today
        )

        assertEquals(6, summary.months.size)
        assertEquals("1月", summary.months.first().label)
        assertEquals("6月", summary.months.last().label)
        assertTrue(summary.months.last().isCurrentMonth)
        // 没学过的月份也占位：横轴读起来才是连续的 1..6 月，而不是从 3 月突然开始。
        assertEquals(0L, summary.months.first().durationSeconds)
        assertEquals(3600L, summary.months[2].durationSeconds)
        assertEquals(1, summary.months[2].activeDays)
    }

    @Test
    fun `only completed sessions inside this year and not in the future count`() {
        val summary = repo.buildYearlyStudySummary(
            focusList = listOf(
                focus(3600, LocalDate.of(2026, 6, 14)),
                focus(3600, LocalDate.of(2025, 12, 31), SessionStatus.RUNNING), // 上一年
                focus(3600, LocalDate.of(2026, 6, 16), SessionStatus.PAUSED),   // 未来 + 未完成
                focus(3600, LocalDate.of(2026, 1, 1), SessionStatus.RUNNING)    // 未完成
            ),
            examList = listOf(
                exam(1800, LocalDate.of(2026, 6, 14)),
                exam(1800, LocalDate.of(2026, 6, 20)) // 未来
            ),
            today = today
        )

        assertEquals(5400L, summary.totalDurationSeconds)
        assertEquals(1, summary.activeDays)
        assertEquals(1, summary.examCount)
    }

    @Test
    fun `longest session spans focus and exam records`() {
        val summary = repo.buildYearlyStudySummary(
            focusList = listOf(
                focus(3600, LocalDate.of(2026, 6, 1)),
                focus(7200, LocalDate.of(2026, 6, 2))
            ),
            examList = listOf(exam(10800, LocalDate.of(2026, 6, 3))),
            today = today
        )

        assertEquals(10800L, summary.longestSession?.durationSeconds)
        assertTrue(summary.longestSession!!.isExam)
    }

    @Test
    fun `month bars carry per month subject distribution`() {
        val summary = repo.buildYearlyStudySummary(
            focusList = listOf(
                focus(3600, LocalDate.of(2026, 3, 2)),
                focus(1800, LocalDate.of(2026, 3, 20)),
                focus(600, LocalDate.of(2026, 5, 4))
            ),
            examList = listOf(exam(1800, LocalDate.of(2026, 3, 9))),
            today = today
        )

        val march = summary.months[2]
        // 当月合计 3600 + 1800 + 1800 = 7200，专注与模考都算进来。
        assertEquals(7200L, march.durationSeconds)
        // 桶名走 SubjectCatalog（与日视图、科目分布同一口径）：
        // 模考记录 subjectId="math" 会被归到「数学一（综合/未细分）」桶。
        assertEquals(
            mapOf("高等数学" to 5400L, "数学一（综合/未细分）" to 1800L),
            march.subjectDistribution
        )
        // 按投入倒序：抽屉里第一行就是当月投得最多的科目。
        assertEquals("高等数学", march.subjectDistribution.keys.first())
        // 没学过的月份是空分布，不是 null。
        assertEquals(emptyMap<String, Long>(), summary.months[3].subjectDistribution)
    }

    @Test
    fun `empty year yields zeroed summary without dividing by zero`() {
        val summary = repo.buildYearlyStudySummary(emptyList(), emptyList(), today)

        assertEquals(0L, summary.totalDurationSeconds)
        assertEquals(0L, summary.dailyAverageSeconds)
        assertEquals(0, summary.activeDays)
        assertEquals(0, summary.longestStreakDays)
        assertNull(summary.longestSession)
        assertEquals(6, summary.months.size)
    }

    @Test
    fun `yearly aggregation agrees with the shared duration rule`() {
        // 与 StudyStats（唯一时长口径）对齐：本年汇总不该自己另算一套加法。
        val focusList = listOf(
            focus(3600, LocalDate.of(2026, 6, 1)),
            focus(1800, LocalDate.of(2026, 6, 2), SessionStatus.RUNNING)
        )
        val examList = listOf(exam(2700, LocalDate.of(2026, 6, 1)))
        val summary = repo.buildYearlyStudySummary(focusList, examList, today)

        val expected = StudyStats.totalDuration(
            focusList.filter { it.status == SessionStatus.COMPLETED },
            examList.filter { it.status == SessionStatus.COMPLETED }
        )
        assertEquals(expected, summary.totalDurationSeconds)
    }
}
