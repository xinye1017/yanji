package com.example.yanji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AchievementRepositoryTest {

    private val repo = AchievementRepository.getInstance()

    @Test
    fun testTotalAchievementCountIs59() {
        assertEquals(59, repo.totalCount)
        assertEquals(59, repo.definitions.size)
    }

    @Test
    fun testMythicCountIsExactly6() {
        val mythics = repo.definitions.filter { it.rarity == AchievementRarity.MYTHIC }
        assertEquals("Mythic achievements must be strictly limited to 6", 6, mythics.size)
        
        val mythicIds = mythics.map { it.id }.toSet()
        val expected = setOf(
            "focus_1000h",
            "streak_300_days",
            "exam_50_count",
            "math_score_150",
            "review_100",
            "hidden_extreme_12h"
        )
        assertEquals(expected, mythicIds)
    }

    @Test
    fun testRarityDistribution() {
        val common = repo.definitions.count { it.rarity == AchievementRarity.COMMON }
        val uncommon = repo.definitions.count { it.rarity == AchievementRarity.UNCOMMON }
        val rare = repo.definitions.count { it.rarity == AchievementRarity.RARE }
        val epic = repo.definitions.count { it.rarity == AchievementRarity.EPIC }
        val legendary = repo.definitions.count { it.rarity == AchievementRarity.LEGENDARY }
        val mythic = repo.definitions.count { it.rarity == AchievementRarity.MYTHIC }

        assertTrue(common >= 6)
        assertTrue(uncommon >= 6)
        assertTrue(rare >= 10)
        assertTrue(epic >= 10)
        assertTrue(legendary >= 8)
        assertEquals(6, mythic)
        assertEquals(59, common + uncommon + rare + epic + legendary + mythic)
    }

    @Test
    fun testCategoriesCoverage() {
        val categories = repo.definitions.map { it.category }.toSet()
        val expectedCategories = setOf(
            AchievementCategory.JOURNEY,
            AchievementCategory.FOCUS,
            AchievementCategory.STREAK,
            AchievementCategory.EXAM,
            AchievementCategory.MATH,
            AchievementCategory.REVIEW,
            AchievementCategory.HIDDEN
        )
        assertEquals(expectedCategories, categories)
    }

    @Test
    fun testAllDefinitionsValid() {
        val seenIds = mutableSetOf<String>()
        for (def in repo.definitions) {
            assertTrue("Duplicate id ${def.id}", seenIds.add(def.id))
            assertTrue("Title empty for ${def.id}", def.title.isNotBlank())
            assertTrue("Description empty for ${def.id}", def.description.isNotBlank())
            assertTrue("Quote empty for ${def.id}", def.rewardQuote.isNotBlank())
            assertTrue("Unit empty for ${def.id}", def.unit.isNotBlank())
            assertTrue("Target must be positive for ${def.id}", def.target > 0)
        }
    }

    @Test
    fun testSeriesMathLadder() {
        val mathSeries = repo.getSeries(AchievementRepository.SERIES_MATH)
        assertEquals(8, mathSeries.size)
        val targets = mathSeries.map { it.target }
        assertEquals(listOf(90L, 100L, 110L, 120L, 130L, 140L, 145L, 150L), targets)
        for (i in 0 until mathSeries.size) {
            assertEquals(i + 1, mathSeries[i].seriesOrder)
        }
    }

    @Test
    fun testSeriesFocusHoursLadder() {
        val series = repo.getSeries(AchievementRepository.SERIES_FOCUS_HOURS)
        assertTrue(series.size >= 6)
        val targets = series.map { it.target }
        assertEquals(targets.sorted(), targets)
    }

    @Test
    fun testExamFull180mRequiresActualDuration() {
        val def = repo.definitions.first { it.id == "exam_full_180m" }

        // Case 1: Planned 180m (10800s), but actual only 1800s -> Must NOT count!
        val fakeLongExam = ExamSession(
            id = "e1",
            subjectId = "math",
            subjectName = "数学一",
            plannedDurationSeconds = 10800L,
            actualDurationSeconds = 1800L
        )
        val p1 = def.calculateProgress(emptyList(), listOf(fakeLongExam), emptyList(), emptyList())
        assertEquals(0L, p1)

        // Case 2: Actual 10800s -> Must count!
        val realExam = ExamSession(
            id = "e2",
            subjectId = "math",
            subjectName = "数学一",
            plannedDurationSeconds = 10800L,
            actualDurationSeconds = 10800L
        )
        val p2 = def.calculateProgress(emptyList(), listOf(realExam), emptyList(), emptyList())
        assertEquals(1L, p2)
    }

    @Test
    fun testMathScoreLadderProgression() {
        val def140 = repo.definitions.first { it.id == "math_score_140" }
        val def145 = repo.definitions.first { it.id == "math_score_145" }

        val exam142 = ExamSession(
            id = "e1",
            subjectId = "math",
            subjectName = "数学一",
            actualDurationSeconds = 10800L,
            score = 142.0
        )

        val p140 = def140.calculateProgress(emptyList(), listOf(exam142), emptyList(), emptyList())
        val p145 = def145.calculateProgress(emptyList(), listOf(exam142), emptyList(), emptyList())

        assertEquals(140L, p140) // capped at 140 target
        assertEquals(142L, p145) // reached 142, but target is 145, so not yet unlocked
    }

    @Test
    fun testExtreme12hCalculation() {
        val def = repo.definitions.first { it.id == "hidden_extreme_12h" }
        val now = System.currentTimeMillis()

        // 12 hours = 12 * 3600 = 43200s
        val longSession = FocusSession(
            id = "f1",
            subjectId = "math",
            subjectName = "高等数学",
            startTime = now,
            endTime = now + 43200 * 1000L,
            durationSeconds = 43200L
        )

        val progress = def.calculateProgress(listOf(longSession), emptyList(), emptyList(), emptyList())
        assertEquals(12L, progress)
    }
}
