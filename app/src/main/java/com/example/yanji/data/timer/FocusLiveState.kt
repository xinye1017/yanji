package com.example.yanji.data.timer

/**
 * 专注会话的**统一实时状态**。
 *
 * 设计目标：让 App UI、系统通知、Android 16 Live Update、ColorOS 流体云
 * **消费同一份状态**，而不是各自维护一套业务字段（旧实现里通知文案与页面文案
 * 是两条独立的推导链，改一处必漏另一处）。
 *
 * 单一事实源依旧是 [TimerMachine]（单调时钟差值）。本类型只是一份**派生快照**：
 * 由 [focusLiveStateOf] 从 `TimerSnapshot` + 当前 elapsed 生成，不含任何计时逻辑，
 * 也不允许被写入。
 *
 * 关键约束：`elapsedSeconds` 是**语义转换时刻**的取值。控制器只在
 * START / PAUSE / RESUME / FINISH / DISCARD 时消费它；运行期间的每秒推进由
 * 系统 Chronometer（通知）与 UI 自己的展示镜像负责，不通过重建本状态实现。
 */
sealed interface FocusLiveState {

    val sessionId: String
    val subject: String
    val startedAtEpochMs: Long

    /** 有效专注秒数（暂停区间已扣除）。 */
    val elapsedSeconds: Long

    /** 目标时长；0 表示正向计时（不限时长）。 */
    val targetSeconds: Long

    /**
     * 倒计时剩余秒数；正向计时恒为 0。
     * 派生属性而非独立字段——存两份必然出现不一致。
     */
    val remainingSeconds: Long
        get() = if (targetSeconds > 0L) (targetSeconds - elapsedSeconds).coerceAtLeast(0L) else 0L

    val isCountdown: Boolean get() = targetSeconds > 0L
    val isRunning: Boolean
    val isPaused: Boolean

    /** 终态：不再需要 ongoing / Live Update / 流体云。 */
    val isTerminal: Boolean get() = this is Finished || this is Discarded || this === Idle
}

/** 运行 / 暂停中的会话共享的附加信息。 */
sealed interface ActiveFocusState : FocusLiveState {
    val pauseCount: Int
}

data class CountdownRunning(
    override val sessionId: String,
    override val subject: String,
    override val startedAtEpochMs: Long,
    override val elapsedSeconds: Long,
    override val targetSeconds: Long,
    override val pauseCount: Int = 0
) : ActiveFocusState {
    override val isRunning: Boolean = true
    override val isPaused: Boolean = false
}

data class CountdownPaused(
    override val sessionId: String,
    override val subject: String,
    override val startedAtEpochMs: Long,
    override val elapsedSeconds: Long,
    override val targetSeconds: Long,
    override val pauseCount: Int = 0
) : ActiveFocusState {
    override val isRunning: Boolean = false
    override val isPaused: Boolean = true
}

data class CountUpRunning(
    override val sessionId: String,
    override val subject: String,
    override val startedAtEpochMs: Long,
    override val elapsedSeconds: Long,
    override val targetSeconds: Long = 0L,
    override val pauseCount: Int = 0
) : ActiveFocusState {
    override val isRunning: Boolean = true
    override val isPaused: Boolean = false
}

data class CountUpPaused(
    override val sessionId: String,
    override val subject: String,
    override val startedAtEpochMs: Long,
    override val elapsedSeconds: Long,
    override val targetSeconds: Long = 0L,
    override val pauseCount: Int = 0
) : ActiveFocusState {
    override val isRunning: Boolean = false
    override val isPaused: Boolean = true
}

/** 正常收尾：倒计时归零，或用户主动「结束并保存」。记录已落库。 */
data class Finished(
    override val sessionId: String,
    override val subject: String,
    override val startedAtEpochMs: Long,
    override val elapsedSeconds: Long,
    override val targetSeconds: Long = 0L,
    val endEpochMs: Long = 0L,
    /** true = 倒计时自然归零；false = 用户提前结束。 */
    val reachedTarget: Boolean = false
) : FocusLiveState {
    override val isRunning: Boolean = false
    override val isPaused: Boolean = false
}

/** 用户放弃：不产生记录，需要立刻拆除所有常驻展示。 */
data class Discarded(
    override val sessionId: String,
    override val subject: String,
    override val startedAtEpochMs: Long,
    override val elapsedSeconds: Long,
    override val targetSeconds: Long = 0L
) : FocusLiveState {
    override val isRunning: Boolean = false
    override val isPaused: Boolean = false
}

/** 没有任何会话。 */
data object Idle : FocusLiveState {
    override val sessionId: String = ""
    override val subject: String = ""
    override val startedAtEpochMs: Long = 0L
    override val elapsedSeconds: Long = 0L
    override val targetSeconds: Long = 0L
    override val isRunning: Boolean = false
    override val isPaused: Boolean = false
}

/**
 * 由单调时钟快照派生统一状态。纯函数，可在 JVM 单测里穷举所有迁移。
 *
 * @param elapsedSeconds 当前有效专注秒数（来自 `TimerMachine.elapsedSeconds()`）
 */
fun focusLiveStateOf(
    snapshot: TimerSnapshot,
    sessionId: String,
    subject: String,
    elapsedSeconds: Long
): FocusLiveState {
    val target = snapshot.targetDurationSeconds
    val elapsed = elapsedSeconds.coerceAtLeast(0L)
    return when (snapshot.phase) {
        TimerPhase.IDLE -> Idle
        TimerPhase.RUNNING -> if (snapshot.isCountdown) {
            CountdownRunning(sessionId, subject, snapshot.startedAtEpochMs, elapsed, target, snapshot.pauseCount)
        } else {
            CountUpRunning(sessionId, subject, snapshot.startedAtEpochMs, elapsed, target, snapshot.pauseCount)
        }
        TimerPhase.PAUSED -> if (snapshot.isCountdown) {
            CountdownPaused(sessionId, subject, snapshot.startedAtEpochMs, elapsed, target, snapshot.pauseCount)
        } else {
            CountUpPaused(sessionId, subject, snapshot.startedAtEpochMs, elapsed, target, snapshot.pauseCount)
        }
        TimerPhase.COMPLETED -> Finished(
            sessionId, subject, snapshot.startedAtEpochMs, elapsed, target,
            endEpochMs = System.currentTimeMillis(),
            reachedTarget = snapshot.isCountdown && elapsed >= target
        )
        TimerPhase.CANCELLED -> Discarded(sessionId, subject, snapshot.startedAtEpochMs, elapsed, target)
    }
}

/**
 * 计时展示格式，全局唯一实现。
 *
 * 1 小时以内 `mm:ss`（18:42），超过 1 小时 `h:mm:ss`（1:18:42）——
 * 不强制补成 `00:18:42`，避免工业仪表感（设计规范 §8）。
 */
fun formatFocusClock(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600 / 60).toString().padStart(2, '0')
    val remainder = (safe % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:$minutes:$remainder" else "$minutes:$remainder"
}
