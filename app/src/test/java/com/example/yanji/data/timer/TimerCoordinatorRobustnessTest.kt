package com.example.yanji.data.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock(private var currentMs: Long = 1000L, private val boot: String = "boot-1") : MonotonicClock {
    override fun nowMs(): Long = currentMs
    override fun currentBootId(): String = boot
    fun advance(delta: Long) {
        currentMs += delta
    }
}

private class MemoryTimerPersistence : TimerSessionPersistence {
    var savedRecord: ActiveSessionRecord? = null
    var completedFocusSession: ActiveSession? = null
    var completedActualSeconds: Long? = null
    var failSave = false

    override suspend fun completeFocus(
        session: ActiveSession,
        actualSeconds: Long,
        pausedSeconds: Long,
        pauseCount: Int,
        endEpochMs: Long
    ) {
        completedFocusSession = session
        completedActualSeconds = actualSeconds
    }

    override suspend fun completeExam(session: ActiveSession, actualSeconds: Long, endEpochMs: Long) {}

    override suspend fun saveActiveSession(record: ActiveSessionRecord) {
        if (failSave) throw RuntimeException("Disk full")
        savedRecord = record
    }

    override suspend fun saveActiveSession(session: ActiveSession) {}

    override suspend fun clearActiveSession() {
        savedRecord = null
    }

    override suspend fun loadActiveSessionRecord(): ActiveSessionRecord? = savedRecord

    override suspend fun loadActiveSession(): ActiveSession? = savedRecord?.activeSession
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimerCoordinatorRobustnessTest {

    @Test
    fun testTimerCalculatorPureCalculations() {
        val snapshot = TimerSnapshot(
            phase = TimerPhase.RUNNING,
            startedAtEpochMs = 10000L,
            targetDurationSeconds = 1800L,
            accumulatedActiveMs = 300000L, // 300s
            resumedAtMonotonicMs = 5000L
        )

        // At nowMonotonicMs = 65000L (60s after resumed):
        val elapsedMs = TimerCalculator.calculateElapsedMs(snapshot, 65000L)
        assertEquals(360000L, elapsedMs) // 300s + 60s = 360s = 360000ms

        val elapsedSecs = TimerCalculator.calculateElapsedSeconds(snapshot, 65000L)
        assertEquals(360L, elapsedSecs)

        val remainingSecs = TimerCalculator.calculateRemainingSeconds(snapshot, 65000L)
        assertEquals(1800L - 360L, remainingSecs)

        assertFalse(TimerCalculator.hasReachedTarget(snapshot, 65000L))

        // Target reached check: 1800s - 300s = 1500s after resume (1505000L)
        assertTrue(TimerCalculator.hasReachedTarget(snapshot, 1505000L))
    }

    @Test
    fun testCoordinatorLifecycleTransitions() = runTest {
        val clock = FakeClock(1000L)
        val persistence = MemoryTimerPersistence()
        val coordinator = ActiveSessionCoordinatorCore(persistence, clock)

        val session = ActiveSession(
            sessionId = "test_s1",
            kind = ActiveSessionKind.FOCUS,
            subjectId = "sub_math",
            subjectName = "数学一",
            startedAtEpochMs = 1000L
        )

        // 1. Begin session
        val started = coordinator.begin(session)
        assertTrue("Session must start successfully", started)
        assertEquals(CoordinatorState.ACTIVE, coordinator.coordinatorState.value)
        assertNotNull(coordinator.active.value)
        assertNotNull(persistence.savedRecord)

        // 2. Mutual exclusion: concurrent begin rejected
        val duplicate = ActiveSession(
            sessionId = "test_s2",
            kind = ActiveSessionKind.FOCUS,
            subjectId = "sub_math",
            subjectName = "数学一",
            startedAtEpochMs = 1000L
        )
        val secondStarted = coordinator.begin(duplicate)
        assertFalse("Second concurrent session must be rejected", secondStarted)

        // 3. Pause
        clock.advance(60000L)
        coordinator.update {
            it.copy(paused = true, accumulatedActiveMs = 60000L, pauseCount = it.pauseCount + 1)
        }
        coordinator.awaitPersistence()
        assertTrue(coordinator.active.value!!.paused)
        assertEquals(1, coordinator.active.value!!.pauseCount)

        // 4. Resume
        coordinator.update {
            it.copy(paused = false)
        }
        coordinator.awaitPersistence()
        assertFalse(coordinator.active.value!!.paused)

        // 5. Complete
        val completed = coordinator.complete(60L)
        assertTrue(completed)
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
        assertNull(coordinator.active.value)
        assertNull(persistence.savedRecord)
        assertEquals(60L, persistence.completedActualSeconds)
    }

    @Test
    fun testPersistenceFailureRollsBackCleanly() = runTest {
        val clock = FakeClock(1000L)
        val persistence = MemoryTimerPersistence().apply { failSave = true }
        val coordinator = ActiveSessionCoordinatorCore(persistence, clock)

        val session = ActiveSession(
            sessionId = "fail_s1",
            kind = ActiveSessionKind.FOCUS,
            subjectId = "sub_math",
            subjectName = "数学一",
            startedAtEpochMs = 1000L
        )

        val started = coordinator.begin(session)
        assertFalse("Begin must fail when disk persistence fails", started)
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
        assertNull(coordinator.active.value)
    }
}
