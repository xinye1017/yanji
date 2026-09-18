package com.example.yanji.data.timer

/**
 * 纯计算工具对象：根据快照与时间戳计算消耗、剩余、暂停时长。
 * 无任何内部状态，确保计时语义在 Service、UI 和状态机之间保持单一事实来源。
 */
object TimerCalculator {

    fun calculateElapsedMs(snapshot: TimerSnapshot, nowMonotonicMs: Long): Long {
        val runningPart = if (snapshot.phase == TimerPhase.RUNNING && snapshot.resumedAtMonotonicMs != null) {
            (nowMonotonicMs - snapshot.resumedAtMonotonicMs).coerceAtLeast(0L)
        } else {
            0L
        }
        return snapshot.accumulatedActiveMs + runningPart
    }

    fun calculateElapsedSeconds(snapshot: TimerSnapshot, nowMonotonicMs: Long): Long =
        calculateElapsedMs(snapshot, nowMonotonicMs) / 1000L

    fun calculateRemainingSeconds(snapshot: TimerSnapshot, nowMonotonicMs: Long): Long {
        if (!snapshot.isCountdown) return 0L
        val elapsed = calculateElapsedSeconds(snapshot, nowMonotonicMs)
        return (snapshot.targetDurationSeconds - elapsed).coerceAtLeast(0L)
    }

    fun hasReachedTarget(snapshot: TimerSnapshot, nowMonotonicMs: Long): Boolean =
        snapshot.isCountdown && calculateElapsedSeconds(snapshot, nowMonotonicMs) >= snapshot.targetDurationSeconds

    fun calculatePausedMs(snapshot: TimerSnapshot, nowEpochMs: Long, nowMonotonicMs: Long): Long {
        val elapsed = calculateElapsedMs(snapshot, nowMonotonicMs)
        return (nowEpochMs - snapshot.startedAtEpochMs - elapsed).coerceAtLeast(0L)
    }
}
