package com.example.yanji.data.timer

import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.achievement.AchievementEvent
import com.example.yanji.data.achievement.AchievementTestDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ManualFocusLoggingTest {
    @Test fun manualFocusIsPersistedBeforeItsAchievementEvent() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val store = TimerStore(fixture.scope, { fixture.db })
            val session = FocusSession("manual", "math", "高等数学", 1000, 1801000, 1800, mode = "补记专注")
            var emitted: AchievementEvent? = null
            store.onAchievementEvent = { event ->
                emitted = event
                assertEquals(session, fixture.db.focusSessionDao().getById(session.id)!!.toDomainModel())
            }
            store.addFocusSession(session)
            assertEquals(AchievementEvent.FocusCompleted(session), emitted)
            assertEquals(session, store.getFocusSessionByIdFromDb(session.id)!!.toDomainModel())
        }
    }

    @Test fun manualExamIsPersistedBeforeItsAchievementEvent() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val store = TimerStore(fixture.scope, { fixture.db })
            val session = com.example.yanji.data.ExamSession("manual-exam", "math", "数学", actualDurationSeconds = 10800)
            val event = CompletableDeferred<AchievementEvent>()
            store.onAchievementEvent = {
                assertEquals(session, fixture.db.examSessionDao().getById(session.id)!!.toDomainModel())
                event.complete(it)
            }
            store.addExamSession(session)
            assertEquals(AchievementEvent.ExamCompleted(session), withTimeout(5000) { event.await() })
            fixture.stopScope()
        }
    }

    @Test fun timerCompletionKeepsLeafEventsAndTheRecordingThreshold() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val store = TimerStore(fixture.scope, { fixture.db })
            val events = mutableListOf<AchievementEvent>()
            store.onAchievementEvent = { events.add(it) }
            val active = ActiveSession("focus", ActiveSessionKind.FOCUS, "math", "数学", note = "真实随笔", taskId = "task", startedAtEpochMs = 1000)
            store.persistence.completeFocus(active, 59, 7, 2, 67000)
            assertEquals(0, fixture.db.focusSessionDao().count())
            assertNull(store.lastCompletedFocus.value)
            assertTrue(events.isEmpty())
            store.persistence.completeFocus(active, 60, 7, 2, 68000)
            val focus = fixture.db.focusSessionDao().getById("focus")!!.toDomainModel()
            assertEquals(60L, focus.durationSeconds)
            assertEquals(7L, focus.pausedDurationSeconds)
            assertEquals(2, focus.pauseCount)
            assertEquals("真实随笔", focus.note)
            assertEquals("task", focus.taskId)
            assertEquals(SessionStatus.COMPLETED, focus.status)
            assertEquals(focus, store.lastCompletedFocus.value)
            store.acknowledgeCompletedFocus()
            assertNull(store.lastCompletedFocus.value)
            val exam = active.copy(sessionId = "exam", kind = ActiveSessionKind.EXAM, plannedDurationSeconds = 10800)
            store.persistence.completeExam(exam, 10000, 10001000)
            assertEquals(fixture.db.examSessionDao().getById("exam")!!.toDomainModel(), store.lastCompletedExam.value)
            assertEquals(2, events.size)
            store.acknowledgeCompletedExam()
            assertNull(store.lastCompletedExam.value)
        }
    }
}
