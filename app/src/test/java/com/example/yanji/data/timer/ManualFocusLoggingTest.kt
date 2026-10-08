package com.example.yanji.data.timer

import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.achievement.AchievementEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualFocusLoggingTest {

    @Test
    fun testTimerStoreAddFocusSessionEmitsAchievementEvent() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val timerStore = TimerStore(
            scope = testScope,
            dbProvider = { null }
        )

        var emittedEvent: AchievementEvent? = null
        timerStore.onAchievementEvent = { event ->
            emittedEvent = event
        }

        val session = FocusSession(
            id = "test-session-1",
            subjectId = "math",
            subjectName = "高等数学",
            startTime = 1000L,
            endTime = 1000L + 1800 * 1000L,
            durationSeconds = 1800L,
            mode = "补记专注",
            status = SessionStatus.COMPLETED
        )

        timerStore.addFocusSession(session)

        assertEquals(1, timerStore.focusSessions.value.size)
        assertEquals("test-session-1", timerStore.focusSessions.value.first().id)
        assertTrue(emittedEvent is AchievementEvent.FocusCompleted)
        assertEquals(session, (emittedEvent as AchievementEvent.FocusCompleted).session)
    }
}
