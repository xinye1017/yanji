package com.example.yanji.data.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMonotonicClock(private var nowMs: Long = 0L) : MonotonicClock {
    override fun nowMs(): Long = nowMs
    fun advanceBy(deltaMs: Long) {
        nowMs += deltaMs
    }
}

/**
 * 计时状态机的纯 JVM 测试。
 *
 * 这是整个项目里风险最高、此前却完全没有被测试覆盖的一段逻辑：
 * 旧实现把"协程循环被调度了多少次"当作计时事实，暂停区间、系统省电、
 * 进程被冻结都会让记录失真，且没有任何断言保护。
 */
class TimerMachineTest {

    private val epoch = 1_700_000_000_000L

    @Test
    fun `paused interval is excluded from elapsed time`() {
        val clock = FakeMonotonicClock(nowMs = 1_000)
        val timer = TimerMachine(clock)

        timer.start(nowEpochMs = epoch, targetDurationSeconds = 0L)

        clock.advanceBy(60_000)
        timer.pause()
        assertEquals(60_000L, timer.elapsedMs())

        clock.advanceBy(5 * 60_000)
        assertEquals("暂停期间不应继续累计", 60_000L, timer.elapsedMs())

        timer.resume()
        clock.advanceBy(30_000)

        assertEquals(90_000L, timer.elapsedMs())
        assertEquals(90L, timer.elapsedSeconds())
    }

    @Test
    fun `countdown remaining never goes negative and reports target reached`() {
        val clock = FakeMonotonicClock()
        val timer = TimerMachine(clock)

        timer.start(nowEpochMs = epoch, targetDurationSeconds = 2_700L)
        assertEquals(2_700L, timer.remainingSeconds())
        assertFalse(timer.hasReachedTarget())

        clock.advanceBy(2_700_000)
        assertEquals(0L, timer.remainingSeconds())
        assertTrue(timer.hasReachedTarget())

        // 即使继续走过去，剩余时间也不能变成负数
        clock.advanceBy(600_000)
        assertEquals(0L, timer.remainingSeconds())
    }

    @Test
    fun `finish freezes elapsed and later clock movement is ignored`() {
        val clock = FakeMonotonicClock()
        val timer = TimerMachine(clock)

        timer.start(nowEpochMs = epoch, targetDurationSeconds = 3_600L)
        clock.advanceBy(120_000)

        val finished = timer.finish()
        assertEquals(TimerPhase.COMPLETED, finished.phase)
        assertEquals(120_000L, timer.elapsedMs())

        clock.advanceBy(60_000)
        assertEquals("结束后不应继续累计", 120_000L, timer.elapsedMs())
    }

    @Test
    fun `cancel is terminal and does not accumulate`() {
        val clock = FakeMonotonicClock()
        val timer = TimerMachine(clock)

        timer.start(nowEpochMs = epoch, targetDurationSeconds = 0L)
        clock.advanceBy(45_000)
        val cancelled = timer.cancel()

        assertEquals(TimerPhase.CANCELLED, cancelled.phase)
        assertEquals(45_000L, timer.elapsedMs())

        // 已终止的状态机不应再响应后续迁移请求
        assertEquals(TimerPhase.CANCELLED, timer.resume().phase)
        clock.advanceBy(10_000)
        assertEquals(45_000L, timer.elapsedMs())
    }

    @Test
    fun `pause count increments only from running state`() {
        val clock = FakeMonotonicClock()
        val timer = TimerMachine(clock)

        timer.start(nowEpochMs = epoch, targetDurationSeconds = 0L)
        clock.advanceBy(1_000)
        assertEquals(1, timer.pause().pauseCount)

        // 已经在暂停中，重复 pause 不应重复计数
        assertEquals(1, timer.pause().pauseCount)

        timer.resume()
        clock.advanceBy(1_000)
        assertEquals(2, timer.pause().pauseCount)
    }

    @Test
    fun `count up mode has no target and never self-completes`() {
        val clock = FakeMonotonicClock()
        val timer = TimerMachine(clock)

        val snapshot = timer.start(nowEpochMs = epoch, targetDurationSeconds = 0L)
        assertFalse(snapshot.isCountdown)

        clock.advanceBy(10 * 60 * 60 * 1000L)
        assertEquals(0L, timer.remainingSeconds())
        assertFalse(timer.hasReachedTarget())
        assertTrue(timer.snapshot.phase == TimerPhase.RUNNING)
    }

    @Test
    fun `restore continues from persisted snapshot`() {
        val clock = FakeMonotonicClock(nowMs = 50_000)
        val timer = TimerMachine(clock)

        timer.restore(
            TimerSnapshot(
                phase = TimerPhase.RUNNING,
                startedAtEpochMs = epoch,
                targetDurationSeconds = 0L,
                accumulatedActiveMs = 5_000L,
                resumedAtMonotonicMs = clock.nowMs(),
                pauseCount = 1
            )
        )

        clock.advanceBy(1_000)
        assertEquals(6_000L, timer.elapsedMs())
        assertEquals(1, timer.snapshot.pauseCount)
    }

    @Test
    fun `pausedMs reports wall time minus active time`() {
        val clock = FakeMonotonicClock()
        val timer = TimerMachine(clock)

        timer.start(nowEpochMs = epoch, targetDurationSeconds = 0L)
        clock.advanceBy(60_000)
        timer.pause()
        clock.advanceBy(300_000)
        timer.resume()
        clock.advanceBy(30_000)

        val endEpoch = epoch + 390_000
        assertEquals(300_000L, timer.pausedMs(endEpoch))
    }

    @Test
    fun `long running countdown does not accumulate drift`() {
        // 模拟"每秒 tick 漏了几次"：跳着推进时钟，elapsed 应完全由时钟差值决定。
        val clock = FakeMonotonicClock()
        val timer = TimerMachine(clock)
        timer.start(nowEpochMs = epoch, targetDurationSeconds = 10_800L)

        repeat(36) {
            clock.advanceBy(5 * 60_000) // 每次推进 5 分钟，而非 1 秒
        }

        assertEquals(10_800L, timer.elapsedSeconds())
        assertTrue(timer.hasReachedTarget())
    }
}
