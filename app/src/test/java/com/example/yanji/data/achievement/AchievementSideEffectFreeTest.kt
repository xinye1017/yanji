package com.example.yanji.data.achievement

import com.example.yanji.data.FocusSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AchievementSideEffectFreeTest {
    private val evaluator = AchievementEvaluator()

    @Test fun catalogProjectionIsStableAndPure() = runBlocking {
        assertEquals(59, AchievementCatalog.definitions.size)
        assertEquals(8, AchievementCatalog.getSeries(AchievementCatalog.SERIES_MATH).size)
        AchievementTestDatabase().use { fixture ->
            fixture.seed(focus = listOf(FocusSession("f", "math", "数学", 1, 3600001, 3600)))
            val facts = fixture.facts()
            fixture.reads.clear()
            val first = evaluator.evaluate(AchievementEvent.ReconcileAll, facts, emptySet())
            val second = evaluator.evaluate(AchievementEvent.ReconcileAll, facts, emptySet())
            assertEquals(first, second)
            assertEquals(1L, facts.sessionCount)
            assertTrue("Pure evaluation must not read or write Room", fixture.reads.isEmpty())
            assertTrue(fixture.db.achievementDao().getUnlockedIds().isEmpty())
        }
    }

    @Test fun reconciliationCatchesUnprocessedSessionsAndIsIdempotent() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            fixture.seed(focus = listOf(FocusSession("f", "math", "数学", 1, 7200001, 7200)))
            val unlocked = evaluator.evaluateAndPersist(AchievementEvent.ReconcileAll, fixture.db, 1234)
            assertTrue(unlocked.containsAll(listOf("journey_focus_first", "focus_1h", "focus_single_45m")))
            assertTrue(evaluator.evaluateAndPersist(AchievementEvent.ReconcileAll, fixture.db, 9999).isEmpty())
            assertEquals(unlocked.toSet(), fixture.db.achievementDao().getUnlockedIds().toSet())
            assertEquals(1, fixture.db.focusSessionDao().count())
        }
    }

    @Test fun focusEventOnlyEvaluatesItsCandidates() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val focus = FocusSession("f", "math", "数学", 1, 3600001, 3600)
            fixture.seed(focus = listOf(focus))
            val unlocked = evaluator.evaluateAndPersist(AchievementEvent.FocusCompleted(focus), fixture.db)
            assertTrue(unlocked.isNotEmpty())
            assertTrue(unlocked.all { it in AchievementEventFilter.FOCUS_CANDIDATES })
            assertFalse("review_1" in unlocked)
            assertFalse("streak_3_days" in unlocked)
        }
    }
}
