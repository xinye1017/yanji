package com.example.yanji.data.achievement

import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
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
    fun testCatalogProjectionIsStableAndPure() {
        assertEquals(59, AchievementCatalog.definitions.size)
        assertEquals(8, AchievementCatalog.getSeries(AchievementCatalog.SERIES_MATH).size)

        // 纯函数：同一份输入重复求值必须得到同一结果，且不修改入参集合。
        val now = System.currentTimeMillis()
        val focus = listOf(
            FocusSession(
                id = "f1",
                subjectId = "math",
                subjectName = "数学一",
                startTime = now - 3600_000,
                endTime = now,
                durationSeconds = 3600,
                status = SessionStatus.COMPLETED
            )
        )
        val exam = listOf<ExamSession>()
        val snapshot = focus.toList()

        val first = evaluator.evaluate(
            event = AchievementEvent.ReconcileAll,
            focusSessions = focus,
            examSessions = exam,
            noteEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )
        val second = evaluator.evaluate(
            event = AchievementEvent.ReconcileAll,
            focusSessions = focus,
            examSessions = exam,
            noteEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )

        assertEquals("evaluate must be deterministic", first, second)
        assertEquals("evaluate must not mutate its inputs", snapshot, focus)
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
            assertTrue(
                "Evaluated achievement $id must exist in catalog and be relevant to focus event",
                defMap.containsKey(id) && AchievementEventFilter.FOCUS_CANDIDATES.contains(id)
            )
        }

        assertFalse(unlocked.contains("journey_journal_first"))
        assertFalse(unlocked.contains("journey_checkin_first"))
        assertFalse(unlocked.contains("review_1"))
        assertFalse(unlocked.contains("streak_3_days"))
    }
}
