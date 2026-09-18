package com.example.yanji.data.timer

import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.achievement.AchievementEvent
import com.example.yanji.ui.focus.FocusViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun testValidationTooShortDurationRejected() {
        val now = System.currentTimeMillis()
        val error = FocusViewModel.validateManualFocusSession(
            startTime = now - 30_000L,
            endTime = now,
            now = now
        )
        assertNotNull(error)
        assertTrue(error?.contains("不足 1 分钟") == true)
    }

    @Test
    fun testValidationFutureEndTimeRejected() {
        val now = System.currentTimeMillis()
        val error = FocusViewModel.validateManualFocusSession(
            startTime = now,
            endTime = now + 7200_000L,
            now = now
        )
        assertNotNull(error)
        assertTrue(error?.contains("未来") == true)
    }

    @Test
    fun testValidationExceeds16HoursRejected() {
        val now = System.currentTimeMillis()
        val error = FocusViewModel.validateManualFocusSession(
            startTime = now - 17 * 3600 * 1000L,
            endTime = now,
            now = now
        )
        assertNotNull(error)
        assertTrue(error?.contains("16 小时") == true)
    }

    @Test
    fun testValidationStartTimeAfterEndTimeRejected() {
        val now = System.currentTimeMillis()
        val error = FocusViewModel.validateManualFocusSession(
            startTime = now,
            endTime = now - 1000L,
            now = now
        )
        assertNotNull(error)
        assertTrue(error?.contains("早于结束时间") == true)
    }

    @Test
    fun testValidManualFocusSessionValidationPasses() {
        val now = System.currentTimeMillis()
        val error = FocusViewModel.validateManualFocusSession(
            startTime = now - 45 * 60 * 1000L,
            endTime = now,
            now = now
        )
        assertTrue(error == null)
    }
}
