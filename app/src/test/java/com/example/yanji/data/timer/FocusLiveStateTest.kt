package com.example.yanji.data.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一状态派生的穷举测试。
 *
 * 这些断言是「App UI / 通知 / Live Update / 流体云看到同一份状态」这一约定的守门测试：
 * 一旦有人绕过 [focusLiveStateOf] 自己拼状态，这里的迁移矩阵就会失效。
 */
class FocusLiveStateTest {

    private fun snapshot(
        phase: TimerPhase,
        target: Long = 0L,
        accumulatedMs: Long = 0L,
        pauseCount: Int = 0
    ) = TimerSnapshot(
        phase = phase,
        startedAtEpochMs = 1_700_000_000_000L,
        targetDurationSeconds = target,
        accumulatedActiveMs = accumulatedMs,
        resumedAtMonotonicMs = if (phase == TimerPhase.RUNNING) 10_000L else null,
        pauseCount = pauseCount
    )

    private fun stateOf(phase: TimerPhase, target: Long, elapsed: Long, pauseCount: Int = 0) =
        focusLiveStateOf(snapshot(phase, target, pauseCount = pauseCount), "sid", "高等数学", elapsed)

    @Test
    fun runningCountdownMapsToCountdownRunningWithDerivedRemaining() {
        val state = stateOf(TimerPhase.RUNNING, target = 2700L, elapsed = 125L)
        assertTrue(state is CountdownRunning)
        assertEquals(2700L, state.targetSeconds)
        assertEquals(125L, state.elapsedSeconds)
        // 剩余时间是派生值，不可能与 target/elapsed 不一致。
        assertEquals(2575L, state.remainingSeconds)
        assertTrue(state.isRunning)
        assertFalse(state.isPaused)
        assertTrue(state.isCountdown)
    }

    @Test
    fun runningCountUpMapsToCountUpRunningWithZeroRemaining() {
        val state = stateOf(TimerPhase.RUNNING, target = 0L, elapsed = 1122L)
        assertTrue(state is CountUpRunning)
        assertFalse(state.isCountdown)
        // 正向计时没有剩余时间，不能因为减法算出负数。
        assertEquals(0L, state.remainingSeconds)
        assertTrue(state.isRunning)
    }

    @Test
    fun pausedStatesFreezeButKeepTheirKind() {
        val countdown = stateOf(TimerPhase.PAUSED, target = 1500L, elapsed = 300L, pauseCount = 2)
        assertTrue(countdown is CountdownPaused)
        assertTrue(countdown.isPaused)
        assertFalse(countdown.isRunning)
        assertEquals(1200L, countdown.remainingSeconds)
        assertEquals(2, (countdown as ActiveFocusState).pauseCount)

        val countUp = stateOf(TimerPhase.PAUSED, target = 0L, elapsed = 42L)
        assertTrue(countUp is CountUpPaused)
        assertTrue(countUp.isPaused)
        assertEquals(42L, countUp.elapsedSeconds)
    }

    @Test
    fun terminalPhasesMapToFinishedAndDiscarded() {
        val finished = stateOf(TimerPhase.COMPLETED, target = 1500L, elapsed = 1500L)
        assertTrue(finished is Finished)
        assertTrue(finished.isTerminal)
        assertTrue((finished as Finished).reachedTarget)

        val earlyFinish = stateOf(TimerPhase.COMPLETED, target = 1500L, elapsed = 600L)
        assertFalse((earlyFinish as Finished).reachedTarget)

        val discarded = stateOf(TimerPhase.CANCELLED, target = 0L, elapsed = 30L)
        assertTrue(discarded is Discarded)
        assertTrue(discarded.isTerminal)
    }

    @Test
    fun terminalStatesAreNeverRunningAndIdleHasNoSubject() {
        val idle = focusLiveStateOf(snapshot(TimerPhase.IDLE), "", "", 0L)
        assertEquals(Idle, idle)
        assertTrue(idle.isTerminal)
        assertFalse(idle.isRunning)
        assertEquals(0L, idle.remainingSeconds)
        assertEquals("", idle.subject)
    }

    @Test
    fun countdownNeverReportsNegativeRemainingPastTheTarget() {
        // elapsed 超过 target（时钟跳变 / 恢复时的边界）也必须夹到 0。
        val state = stateOf(TimerPhase.RUNNING, target = 100L, elapsed = 175L)
        assertEquals(0L, state.remainingSeconds)
    }

    @Test
    fun clockFormattingSwitchesAtOneHourWithoutForcingLeadingZeroHours() {
        assertEquals("00:00", formatFocusClock(0L))
        assertEquals("00:01", formatFocusClock(1L))
        assertEquals("18:42", formatFocusClock(1122L))
        assertEquals("59:59", formatFocusClock(3599L))
        assertEquals("1:00:00", formatFocusClock(3600L))
        assertEquals("1:18:42", formatFocusClock(4722L))
        // 负数（异常输入）不允许渲染出 "-1:-1"。
        assertEquals("00:00", formatFocusClock(-5L))
    }
}
