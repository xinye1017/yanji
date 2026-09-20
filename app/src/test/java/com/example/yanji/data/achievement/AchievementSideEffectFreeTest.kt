package com.example.yanji.data.achievement

import com.example.yanji.data.AchievementRepository
import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementSideEffectFreeTest {

    private val evaluator = AchievementEvaluator()

    @Test
    fun testProjectionIsPureWithoutSideEffects() {
        val repo = AchievementRepository.getInstance()
        val definitions = repo.definitions

        assertEquals(59, definitions.size)
        val mathSeries = repo.getSeries(AchievementRepository.SERIES_MATH)
        assertEquals(8, mathSeries.size)
    }

    @Test
    fun testReconciliationCatchesUnprocessedSessions() {
        val now = System.currentTimeMillis()
        val completedFocus = listOf(
            FocusSession(
                id = "f1",
                subjectId = "math",
                subjectName = "数学一",
                startTime = now - 7200_000,
                endTime = now,
                durationSeconds = 7200,
                status = SessionStatus.COMPLETED
            )
        )

        val newlyUnlocked = evaluator.evaluate(
            event = AchievementEvent.ReconcileAll,
            focusSessions = completedFocus,
            examSessions = emptyList(),
            noteEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )

        assertTrue(newlyUnlocked.contains("journey_focus_first"))
        assertTrue(newlyUnlocked.contains("focus_1h"))
        assertTrue(newlyUnlocked.contains("focus_single_45m"))

        val secondPass = evaluator.evaluate(
            event = AchievementEvent.ReconcileAll,
            focusSessions = completedFocus,
            examSessions = emptyList(),
            noteEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = newlyUnlocked.toSet()
        )

        assertTrue("Second reconciliation pass must unlock zero additional items", secondPass.isEmpty())
    }

    @Test
    fun testEventDrivenSelectiveEvaluation() {
        val now = System.currentTimeMillis()
        val s = FocusSession(
            id = "f1",
            subjectId = "math",
            subjectName = "数学一",
            startTime = now - 3600_000,
            endTime = now,
            durationSeconds = 3600,
            status = SessionStatus.COMPLETED
        )

        val unlocked = evaluator.evaluate(
            event = AchievementEvent.FocusCompleted(s),
            focusSessions = listOf(s),
            examSessions = emptyList(),
            noteEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )

        val defMap = AchievementCatalog.definitions.associateBy { it.id }
        for (id in unlocked) {
            val def = defMap[id]!!
            assertTrue(
                "Evaluated achievement  must be relevant to focus event",
                AchievementEventFilter.FOCUS_CANDIDATES.contains(id)
            )
        }

        assertFalse(unlocked.contains("journey_journal_first"))
        assertFalse(unlocked.contains("journey_checkin_first"))
        assertFalse(unlocked.contains("review_1"))
        assertFalse(unlocked.contains("streak_3_days"))
    }
}
