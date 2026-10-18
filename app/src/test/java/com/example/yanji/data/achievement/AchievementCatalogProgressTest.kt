package com.example.yanji.data.achievement

import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就**进度计算**契约：直接针对 [AchievementCatalog] 的静态定义。
 *
 * 这些断言原先写在 `AchievementRepositoryTest` 里，靠构造仓储再读 `definitions` /
 * `getSeries` 间接拿到同一批数据。仓储层已删除（生产代码零消费者，运行时评估由
 * [AchievementEvaluator] 承担），契约本身没有变，因此改为直接断言目录与定义：
 * 系列阶梯、真实时长判定、隐藏成就的分档计算都不允许在清理中丢失。
 */
class AchievementCatalogProgressTest {

    private fun def(id: String): AchievementDef =
        requireNotNull(AchievementCatalog.find(id)) { "achievement $id must exist in catalog" }

    @Test
    fun testSeriesSizesAreStable() {
        assertEquals(8, AchievementCatalog.getSeries(AchievementCatalog.SERIES_MATH).size)
        assertEquals(9, AchievementCatalog.getSeries(AchievementCatalog.SERIES_FOCUS_HOURS).size)
        assertEquals(4, AchievementCatalog.getSeries(AchievementCatalog.SERIES_SINGLE_FOCUS).size)
        assertEquals(8, AchievementCatalog.getSeries(AchievementCatalog.SERIES_STREAK).size)
        assertEquals(6, AchievementCatalog.getSeries(AchievementCatalog.SERIES_EXAM_COUNT).size)
        assertEquals(7, AchievementCatalog.getSeries(AchievementCatalog.SERIES_REVIEW_COUNT).size)
    }

    @Test
    fun testSeriesMathLadder() {
        val mathSeries = AchievementCatalog.getSeries(AchievementCatalog.SERIES_MATH)
        assertEquals(8, mathSeries.size)
        assertEquals(listOf(90L, 100L, 110L, 120L, 130L, 140L, 145L, 150L), mathSeries.map { it.target })
        for (i in mathSeries.indices) {
            assertEquals(i + 1, mathSeries[i].seriesOrder)
        }
    }

    @Test
    fun testSeriesFocusHoursLadderIsAscending() {
        val series = AchievementCatalog.getSeries(AchievementCatalog.SERIES_FOCUS_HOURS)
        assertTrue(series.size >= 6)
        val targets = series.map { it.target }
        assertEquals(targets.sorted(), targets)
    }

    @Test
    fun testExamFull180mRequiresActualDuration() {
        val def = def("exam_full_180m")

        // 计划 180 分钟但实际只学了 30 分钟：不得计入。
        val fakeLongExam = ExamSession(
            id = "e1",
            subjectId = "math",
            subjectName = "数学一",
            plannedDurationSeconds = 10800L,
            actualDurationSeconds = 1800L
        )
        assertEquals(0L, def.calculateProgress(emptyList(), listOf(fakeLongExam), emptyList(), emptyList()))

        // 实际时长达到 180 分钟：计入。
        val realExam = ExamSession(
            id = "e2",
            subjectId = "math",
            subjectName = "数学一",
            plannedDurationSeconds = 10800L,
            actualDurationSeconds = 10800L
        )
        assertEquals(1L, def.calculateProgress(emptyList(), listOf(realExam), emptyList(), emptyList()))
    }

    @Test
    fun testMathScoreLadderProgression() {
        val def140 = def("math_score_140")
        val def145 = def("math_score_145")

        val exam142 = ExamSession(
            id = "e1",
            subjectId = "math",
            subjectName = "数学一",
            actualDurationSeconds = 10800L,
            score = 142.0
        )

        assertEquals(140L, def140.calculateProgress(emptyList(), listOf(exam142), emptyList(), emptyList()))
        assertEquals(142L, def145.calculateProgress(emptyList(), listOf(exam142), emptyList(), emptyList()))
    }

    @Test
    fun testExtreme12hCalculation() {
        val def = def("hidden_extreme_12h")
        val now = System.currentTimeMillis()

        // 12 小时 = 43200 秒
        val longSession = FocusSession(
            id = "f1",
            subjectId = "math",
            subjectName = "高等数学",
            startTime = now,
            endTime = now + 43200 * 1000L,
            durationSeconds = 43200L,
            status = SessionStatus.COMPLETED
        )

        assertEquals(12L, def.calculateProgress(listOf(longSession), emptyList(), emptyList(), emptyList()))
    }
}
