package com.example.yanji.data.timer

import kotlinx.serialization.Serializable

/** 计时会话的生命周期状态。 */
@Serializable
enum class TimerPhase {
    IDLE,
    RUNNING,
    PAUSED,
    /** 正常走完（倒计时归零）或用户主动结束并保存。 */
    COMPLETED,
    /** 用户放弃，不产生记录。 */
    CANCELLED
}

/**
 * 计时快照。这是一个**纯值对象**，可以直接序列化后持久化，用于进程死亡后的恢复。
 *
 * @param accumulatedActiveMs 已完成区间的有效计时总长（不含当前正在跑的这一段）
 * @param resumedAtMonotonicMs 当前 running 段的单调时钟起点；暂停 / 终止时为 null
 */
@Serializable
data class TimerSnapshot(
    val phase: TimerPhase = TimerPhase.IDLE,
    val startedAtEpochMs: Long = 0L,
    val targetDurationSeconds: Long = 0L,
    val accumulatedActiveMs: Long = 0L,
    val resumedAtMonotonicMs: Long? = null,
    val pauseCount: Int = 0
) {
    val isActive: Boolean get() = phase == TimerPhase.RUNNING || phase == TimerPhase.PAUSED
    val isTerminal: Boolean get() = phase == TimerPhase.COMPLETED || phase == TimerPhase.CANCELLED
    /** 目标时长为 0 表示正向计时（不限时长）。 */
    val isCountdown: Boolean get() = targetDurationSeconds > 0L
}

/**
 * 纯 Kotlin 计时状态机。
 *
 * 状态迁移：
 * ```
 * IDLE ──start──► RUNNING ──pause──► PAUSED
 *                   ▲                  │
 *                   └────resume────────┘
 *                   │
 *                   ├──finish──► COMPLETED
 *                   └──cancel──► CANCELLED
 * ```
 *
 * 关键设计：**计时的"事实"来自单调时钟差值，而不是"循环被调度了多少次"**。
 * 旧实现每 `delay(1000)` 就把 elapsedSeconds + 1，协程调度延迟、系统省电、进程被冻结
 * 都会累积误差，且无法在恢复后重建正确进度。现在每秒回调只用于刷新 UI / 通知。
 */
class TimerMachine(private val clock: MonotonicClock) {

    var snapshot: TimerSnapshot = TimerSnapshot()
        private set

    /** 开始一个新的计时。targetDurationSeconds 传 0 表示正向计时。 */
    fun start(nowEpochMs: Long, targetDurationSeconds: Long): TimerSnapshot {
        snapshot = TimerSnapshot(
            phase = TimerPhase.RUNNING,
            startedAtEpochMs = nowEpochMs,
            targetDurationSeconds = targetDurationSeconds.coerceAtLeast(0L),
            accumulatedActiveMs = 0L,
            resumedAtMonotonicMs = clock.nowMs(),
            pauseCount = 0
        )
        return snapshot
    }

    /** 用持久化的快照恢复（进程重建 / Service 重启）。 */
    fun restore(snapshot: TimerSnapshot) {
        this.snapshot = snapshot
    }

    fun pause(): TimerSnapshot {
        val current = snapshot
        if (current.phase != TimerPhase.RUNNING) return current
        snapshot = current.copy(
            phase = TimerPhase.PAUSED,
            accumulatedActiveMs = elapsedMs(),
            resumedAtMonotonicMs = null,
            pauseCount = current.pauseCount + 1
        )
        return snapshot
    }

    fun resume(): TimerSnapshot {
        val current = snapshot
        if (current.phase != TimerPhase.PAUSED) return current
        snapshot = current.copy(phase = TimerPhase.RUNNING, resumedAtMonotonicMs = clock.nowMs())
        return snapshot
    }

    /**
     * Freeze elapsed time while a completion transaction is committing.
     * This is not a user pause, so it deliberately does not increment [TimerSnapshot.pauseCount].
     */
    fun freezeForCommit(): TimerSnapshot {
        val current = snapshot
        if (current.phase != TimerPhase.RUNNING) return current
        snapshot = current.copy(
            phase = TimerPhase.PAUSED,
            accumulatedActiveMs = elapsedMs(),
            resumedAtMonotonicMs = null
        )
        return snapshot
    }

    fun finish(): TimerSnapshot = terminate(TimerPhase.COMPLETED)

    fun cancel(): TimerSnapshot = terminate(TimerPhase.CANCELLED)

    private fun terminate(phase: TimerPhase): TimerSnapshot {
        val current = snapshot
        snapshot = when (current.phase) {
            TimerPhase.RUNNING -> current.copy(
                phase = phase,
                accumulatedActiveMs = elapsedMs(),
                resumedAtMonotonicMs = null
            )
            TimerPhase.PAUSED -> current.copy(phase = phase)
            else -> current
        }
        return snapshot
    }

    /** 已累计的有效计时（毫秒）。暂停区间不计入。 */
    fun elapsedMs(): Long {
        val current = snapshot
        val runningPart = current.resumedAtMonotonicMs?.let { clock.nowMs() - it } ?: 0L
        return current.accumulatedActiveMs + runningPart
    }

    fun elapsedSeconds(): Long = elapsedMs() / 1000L

    /** 倒计时剩余秒数；正向计时返回 0。 */
    fun remainingSeconds(): Long {
        val current = snapshot
        if (!current.isCountdown) return 0L
        return (current.targetDurationSeconds - elapsedSeconds()).coerceAtLeast(0L)
    }

    /** 倒计时是否已走完。 */
    fun hasReachedTarget(): Boolean =
        snapshot.isCountdown && elapsedSeconds() >= snapshot.targetDurationSeconds

    /** 暂停掉的总时长（毫秒）。用于 FocusSession.pausedDurationSeconds。 */
    fun pausedMs(nowEpochMs: Long): Long =
        (nowEpochMs - snapshot.startedAtEpochMs - elapsedMs()).coerceAtLeast(0L)
}
