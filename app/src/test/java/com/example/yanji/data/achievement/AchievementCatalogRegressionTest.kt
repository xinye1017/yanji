package com.example.yanji.data.achievement

import com.example.yanji.data.AchievementCategory
import com.example.yanji.data.AchievementRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogRegressionTest {

    @Test
    fun testExact59AchievementsPreserved() {
        assertEquals("Must contain exactly 59 achievements", 59, AchievementCatalog.definitions.size)
    }

    @Test
    fun testAllIdsAreUnique() {
        val ids = AchievementCatalog.definitions.map { it.id }
        assertEquals("Every achievement ID must be unique", ids.toSet().size, ids.size)
    }

    @Test
    fun testAllFieldsArePopulated() {
        for (def in AchievementCatalog.definitions) {
            assertTrue("ID must not be blank: ${def.id}", def.id.isNotBlank())
            assertTrue("Title must not be blank: ${def.id}", def.title.isNotBlank())
            assertTrue("Description must not be blank: ${def.id}", def.description.isNotBlank())
            assertTrue("IconKey must not be blank: ${def.id}", def.iconKey.isNotBlank())
            assertTrue("RewardQuote must not be blank: ${def.id}", def.rewardQuote.isNotBlank())
            assertTrue("Unit must not be blank: ${def.id}", def.unit.isNotBlank())
            assertTrue("Target must be positive: ${def.id}", def.target > 0)
            assertNotNull("Category must not be null: ${def.id}", def.category)
            assertNotNull("Rarity must not be null: ${def.id}", def.rarity)
        }
    }

    @Test
    fun testRarityDistributionIsExact() {
        val counts = AchievementCatalog.definitions.groupBy { it.rarity }.mapValues { it.value.size }
        assertEquals(10, counts[AchievementRarity.COMMON])
        assertEquals(7, counts[AchievementRarity.UNCOMMON])
        assertEquals(13, counts[AchievementRarity.RARE])
        assertEquals(14, counts[AchievementRarity.EPIC])
        assertEquals(9, counts[AchievementRarity.LEGENDARY])
        assertEquals(6, counts[AchievementRarity.MYTHIC])
        assertEquals(59, AchievementCatalog.definitions.size)
    }

    @Test
    fun testSixMythicAchievementsExactIds() {
        val mythicIds = AchievementCatalog.definitions
            .filter { it.rarity == AchievementRarity.MYTHIC }
            .map { it.id }
            .toSet()

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
    fun testCategoriesCoverage() {
        val categories = AchievementCatalog.definitions.map { it.category }.toSet()
        assertEquals(
            setOf(
                AchievementCategory.JOURNEY,
                AchievementCategory.FOCUS,
                AchievementCategory.STREAK,
                AchievementCategory.EXAM,
                AchievementCategory.MATH,
                AchievementCategory.REVIEW,
                AchievementCategory.HIDDEN
            ),
            categories
        )
    }

    @Test
    fun testSeriesDefinitionsOrdering() {
        val allSeriesIds = listOf(
            AchievementCatalog.SERIES_FOCUS_HOURS,
            AchievementCatalog.SERIES_SINGLE_FOCUS,
            AchievementCatalog.SERIES_STREAK,
            AchievementCatalog.SERIES_EXAM_COUNT,
            AchievementCatalog.SERIES_MATH,
            AchievementCatalog.SERIES_REVIEW_COUNT
        )

        for (seriesId in allSeriesIds) {
            val seriesItems = AchievementCatalog.getSeries(seriesId)
            assertTrue("Series $seriesId must not be empty", seriesItems.isNotEmpty())
            // Orders must be strictly 1, 2, 3...
            val orders = seriesItems.map { it.seriesOrder }
            assertEquals("Series $seriesId orders must be sequential", (1..seriesItems.size).toList(), orders)
        }
    }

    @Test
    fun testHiddenAchievementsExactIds() {
        val hiddenDefs = AchievementCatalog.definitions.filter { it.isHidden }
        val hiddenIds = hiddenDefs.map { it.id }.toSet()
        val expectedHidden = setOf(
            "hidden_morning_3d",
            "hidden_night_owl",
            "hidden_day_and_night",
            "hidden_daily_10h",
            "hidden_extreme_12h"
        )
        assertEquals(expectedHidden, hiddenIds)
    }
}
