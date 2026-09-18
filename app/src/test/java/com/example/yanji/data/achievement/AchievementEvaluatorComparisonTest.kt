package com.example.yanji.data.achievement

import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementEvaluatorComparisonTest {

    private val evaluator = AchievementEvaluator()

    @Test
    fun testEmptyDatabaseUnlocksNothing() {
        val unlocked = evaluator.evaluate(
            event = AchievementEvent.ReconcileAll,
            focusSessions = emptyList(),
            examSessions = emptyList(),
            journalEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )
        assertTrue("No achievements should be unlocked on empty database", unlocked.isEmpty())
    }

    @Test
    fun testTargetMinusOneBoundary() {
        // focus_1h target is 3600 seconds. 3599 seconds should NOT unlock focus_1h.
        val now = System.currentTimeMillis()
        val s = FocusSession(
            id = "f1",
            subjectId = "math",
            subjectName = "数学一",
            startTime = now - 3599_000,
            endTime = now,
            durationSeconds = 3599,
            status = SessionStatus.COMPLETED
        )

        val unlocked = evaluator.evaluate(
            event = AchievementEvent.FocusCompleted(s),
            focusSessions = listOf(s),
            examSessions = emptyList(),
            journalEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )

        // journey_focus_first (target 1 count) is satisfied
        assertTrue(unlocked.contains("journey_focus_first"))
        // focus_1h (target 1h = 3600s) is NOT satisfied (3599 / 3600 = 0)
        assertFalse("3599s should not unlock focus_1h", unlocked.contains("focus_1h"))
    }

    @Test
    fun testExactTargetBoundary() {
        // 3600 seconds should unlock both journey_focus_first and focus_1h
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
            journalEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )

        assertTrue("journey_focus_first must unlock", unlocked.contains("journey_focus_first"))
        assertTrue("focus_1h must unlock", unlocked.contains("focus_1h"))
        assertFalse("focus_5h must not unlock", unlocked.contains("focus_5h"))
    }

    @Test
    fun testTargetPlusOneBoundary() {
        val now = System.currentTimeMillis()
        val s = FocusSession(
            id = "f1",
            subjectId = "math",
            subjectName = "数学一",
            startTime = now - 3601_000,
            endTime = now,
            durationSeconds = 3601,
            status = SessionStatus.COMPLETED
        )

        val unlocked = evaluator.evaluate(
            event = AchievementEvent.FocusCompleted(s),
            focusSessions = listOf(s),
            examSessions = emptyList(),
            journalEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )

        assertTrue("focus_1h must unlock with 3601 seconds", unlocked.contains("focus_1h"))
    }

    @Test
    fun testEventFilteringEfficiency() {
        // When JournalCreated occurs, only review & journey_journal achievements can unlock
        val j = JournalEntry(
            id = "j1",
            date = "2026-09-18",
            title = "今日复盘",
            content = "数学极限完成",
            moodScore = 4,
            energyScore = 4,
            studySatisfaction = 5,
            tomorrowPlan = "",
            blockers = "",
            tags = listOf("数学")
        )

        val unlocked = evaluator.evaluate(
            event = AchievementEvent.JournalCreated(j),
            focusSessions = emptyList(),
            examSessions = emptyList(),
            journalEntries = listOf(j),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        )

        assertEquals(
            setOf("journey_journal_first", "review_1"),
            unlocked.toSet()
        )
    }

    @Test
    fun testHistoricalAlreadyUnlockedAreSkipped() {
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

        val firstUnlocked = evaluator.evaluate(
            event = AchievementEvent.FocusCompleted(s),
            focusSessions = listOf(s),
            examSessions = emptyList(),
            journalEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = emptySet()
        ).toSet()

        assertTrue("First evaluation should unlock achievements", firstUnlocked.isNotEmpty())

        val secondUnlocked = evaluator.evaluate(
            event = AchievementEvent.FocusCompleted(s),
            focusSessions = listOf(s),
            examSessions = emptyList(),
            journalEntries = emptyList(),
            checkIns = emptyList(),
            unlockedIds = firstUnlocked
        )

        assertTrue("Already unlocked achievements must be skipped on subsequent evaluation", secondUnlocked.isEmpty())
    }

    @Test
    fun testReconcileAllParityWithOldCatalogCalculation() {
        val now = System.currentTimeMillis()
        val focusList = listOf(
            FocusSession("f1", "sub_math", "数学一", now - 7200_000, now - 3600_000, 3600, status = SessionStatus.COMPLETED),
            FocusSession("f2", "sub_408", "408专业课", now - 3600_000, now, 3600, status = SessionStatus.COMPLETED)
        )
        val examList = listOf(
            ExamSession("e1", "sub_math", "数学一", 10800, 10800, now - 20000_000, now - 9200_000, score = 105.0, maxScore = 150.0, status = SessionStatus.COMPLETED)
        )
        val journalList = listOf(
            JournalEntry(
                id = "j1",
                date = "2026-09-17",
                title = "复盘1",
                content = "content",
                moodScore = 4,
                energyScore = 4,
                studySatisfaction = 4
            )
        )
        val checkInList = listOf(
            CheckIn("2026-09-18", now, 1, "打卡1", "good")
        )

        // Old implementation parity check:
        val expected = AchievementCatalog.definitions
            .filter { it.calculateProgress(focusList, examList, journalList, checkInList) >= it.target }
            .map { it.id }
            .toSet()

        // New evaluator ReconcileAll check:
        val actual = evaluator.evaluate(
            event = AchievementEvent.ReconcileAll,
            focusSessions = focusList,
            examSessions = examList,
            journalEntries = journalList,
            checkIns = checkInList,
            unlockedIds = emptySet()
        ).toSet()

        assertEquals("New evaluator must produce 100% identical unlock set as old catalog calculation", expected, actual)
    }
}
