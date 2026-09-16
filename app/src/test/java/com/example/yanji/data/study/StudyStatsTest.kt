package com.example.yanji.data.study

import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 学习时长口径的纯逻辑测试。
 *
 * 这里钉住的是唯一口径：**只统计 COMPLETED**。正在跑的（RUNNING）和暂停中的（PAUSED）
 * 都不算数——"进行中的专注"不应该出现在统计里，否则一次长专注会被重复计入。
 */
class StudyStatsTest {

    private fun noonToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        // 必须归零毫秒：否则每次调用返回的瞬间都略有差异，两个"同一个时刻"之间用 >= 比较
        // 就成了随机结果。见 durationSince 测试的注释。
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun noonDaysAgo(days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = noonToday(); add(Calendar.DAY_OF_YEAR, -days)
    }.timeInMillis

    private fun focus(seconds: Long, start: Long, status: SessionStatus = SessionStatus.COMPLETED) =
        FocusSession(
            id = "fs-$seconds-$start",
            subjectId = "math_advanced",
            subjectName = "高等数学",
            startTime = start,
            endTime = start + seconds * 1000,
            durationSeconds = seconds,
            status = status
        )

    private fun exam(seconds: Long, start: Long, status: SessionStatus = SessionStatus.COMPLETED) =
        ExamSession(
            id = "es-$seconds-$start",
            subjectId = "math",
            subjectName = "数学一",
            plannedDurationSeconds = 10800,
            actualDurationSeconds = seconds,
            startTime = start,
            endTime = start + seconds * 1000,
            status = status
        )

    @Test
    fun `durationOnDay sums completed focus and exam of that day only`() {
        val today = noonToday()
        val total = StudyStats.durationOnDay(
            focus = listOf(focus(3600, today), focus(600, noonDaysAgo(1))),
            exams = listOf(exam(1800, today + 1000)),
            dayEpochMs = today
        )
        assertEquals(5400L, total)
    }

    @Test
    fun `durationOnDay ignores running and paused sessions`() {
        val today = noonToday()
        val total = StudyStats.durationOnDay(
            focus = listOf(
                focus(3600, today),
                focus(1200, today, SessionStatus.RUNNING),
                focus(900, today, SessionStatus.PAUSED)
            ),
            exams = emptyList(),
            dayEpochMs = today
        )
        assertEquals("进行中/暂停中的会话不得计入统计", 3600L, total)
    }

    @Test
    fun `durationSince only counts sessions starting at or after cutoff`() {
        // cutoff 只计算一次并复用，保证"起始时间恰好等于 cutoff"这条边界被确定性地覆盖：
        // 若分别调用两次 noonDaysAgo(1)，两次得到的瞬间可能落在不同毫秒上，
        // 断言就会依赖运行速度而随机失败（CI 上出现过 expected:<4200> but was:<3600>）。
        val cutoff = noonDaysAgo(1)
        val total = StudyStats.durationSince(
            focus = listOf(focus(3600, noonToday()), focus(600, cutoff)),
            exams = emptyList(),
            cutoffEpochMs = cutoff
        )
        assertEquals(4200L, total)
    }

    @Test
    fun `totalDuration sums everything completed regardless of date`() {
        val total = StudyStats.totalDuration(
            focus = listOf(focus(3600, noonToday()), focus(600, noonDaysAgo(3))),
            exams = listOf(exam(1800, noonDaysAgo(1)))
        )
        assertEquals(6000L, total)
    }

    @Test
    fun `subjectDistributionOnDay aggregates by display name`() {
        val today = noonToday()
        val dist = StudyStats.subjectDistributionOnDay(
            focus = listOf(focus(3600, today), focus(600, today)),
            exams = listOf(exam(1800, today)),
            dayEpochMs = today
        )
        assertEquals(false, dist.isEmpty())
        val grand = dist.values.sum()
        assertEquals(6000L, grand)
    }

    @Test
    fun `empty input yields zero and empty map`() {
        assertEquals(0L, StudyStats.durationOnDay(emptyList(), emptyList(), noonToday()))
        assertEquals(0L, StudyStats.totalDuration(emptyList(), emptyList()))
        assertEquals(true, StudyStats.subjectDistributionOnDay(emptyList(), emptyList(), noonToday()).isEmpty())
    }
}
