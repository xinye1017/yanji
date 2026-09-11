package com.example.yanji.data.timer

import com.example.yanji.data.FocusModes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ActiveSessionKind { FOCUS, EXAM }

/**
 * 一个正在进行的计时会话的元信息。
 *
 * 它由业务层持有，而不是 Compose 的 `remember` —— 这样切 Tab、Activity 重建、
 * 甚至前台 Service 独立运行都不会丢失"我正在进行哪一场专注 / 模考"这件事。
 */
data class ActiveSession(
    val sessionId: String,
    val kind: ActiveSessionKind,
    val subjectId: String,
    val subjectName: String,
    val mode: String = FocusModes.COUNT_UP,
    val note: String = "",
    /** 0 表示正向计时（不限时长）。 */
    val targetDurationSeconds: Long = 0L,
    /** 模考的试卷计划时长，用于复盘展示。 */
    val plannedDurationSeconds: Long = 0L,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val accumulatedActiveMs: Long = 0L,
    val pauseCount: Int = 0,
    val paused: Boolean = false
)

/** 计时结束后的落库出口。由数据层实现。 */
interface TimerSessionPersistence {
    suspend fun completeFocus(
        session: ActiveSession,
        actualSeconds: Long,
        pausedSeconds: Long,
        pauseCount: Int,
        endEpochMs: Long
    )

    suspend fun completeExam(
        session: ActiveSession,
        actualSeconds: Long,
        endEpochMs: Long
    )

    /** 可选：把活动会话写入持久层，用于进程死亡后的恢复。 */
    suspend fun saveActiveSession(session: ActiveSession) {}

    /** 可选：活动会话结束时清除持久化记录。 */
    suspend fun clearActiveSession() {}

    /** 可选：进程启动时读回上次未结束的活动会话。 */
    suspend fun loadActiveSession(): ActiveSession? = null
}

/**
 * 活动计时会话的**业务层归属者**。
 *
 * 解决的问题：旧实现里"倒计时结束 → 落库"发生在 `FocusScreen` / `ExamScreen` 的
 * `LaunchedEffect` 中。只要这两个页面没有被 Compose（切到别的 Tab、Activity 重建、
 * 进程被回收），一次真实完成的学习记录就可能永远不落库；而前台 Service 却仍在跑。
 *
 * 现在职责边界是：
 *  - Service 只负责"时间事实"（单调时钟 + 通知 + 响铃），
 *  - 本协调器负责"业务事实"（谁在计时、结束了要写什么），
 *  - UI 只是观察者。
 */
object ActiveSessionCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var persistence: TimerSessionPersistence? = null

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    fun bind(persistence: TimerSessionPersistence) {
        this.persistence = persistence
    }

    /** 进程启动时把上次未结束的活动会话读回内存。 */
    fun restorePersisted() {
        val p = persistence ?: return
        scope.launch {
            val restored = runCatching { p.loadActiveSession() }.getOrNull()
            if (restored != null && _active.value == null) {
                _active.value = restored
            }
        }
    }

    /**
     * 登记一个活动会话。
     *
     * **不变量：同一时刻只允许存在一个活动计时会话（专注与模考互斥）。**
     * 旧实现里 Focus 与 Exam 各自维护一套 `remember` 状态，理论上可以并行启动、
     * 由同一个前台 Service 争抢，导致双方都拿到错误的计时。现在由业务层统一拒绝。
     *
     * @return 是否接受；已有其它会话在跑时返回 false。
     */
    fun begin(session: ActiveSession): Boolean {
        val current = _active.value
        if (current != null && current.sessionId != session.sessionId) return false
        _active.value = session
        val p = persistence ?: return true
        scope.launch { runCatching { p.saveActiveSession(session) } }
        return true
    }

    /** 当前是否有活动会话在跑。 */
    val isBusy: Boolean get() = _active.value != null

    fun update(transform: (ActiveSession) -> ActiveSession) {
        val current = _active.value ?: return
        val updated = transform(current)
        _active.value = updated
        val p = persistence ?: return
        scope.launch { runCatching { p.saveActiveSession(updated) } }
    }

    /**
     * 计时结束（倒计时归零 / 用户主动结束）→ 按实际时长落库。
     * 与任何 UI 是否正在组合无关。
     */
    fun complete(
        actualSeconds: Long,
        pausedSeconds: Long = 0L,
        pauseCount: Int = 0,
        endEpochMs: Long = System.currentTimeMillis()
    ) {
        val session = _active.value ?: return
        _active.value = null
        val p = persistence ?: return
        scope.launch {
            runCatching {
                when (session.kind) {
                    ActiveSessionKind.FOCUS ->
                        p.completeFocus(session, actualSeconds, pausedSeconds, pauseCount, endEpochMs)
                    ActiveSessionKind.EXAM ->
                        p.completeExam(session, actualSeconds, endEpochMs)
                }
                p.clearActiveSession()
            }
        }
    }

    /** 用户放弃本次计时：不产生任何记录。 */
    fun cancel() {
        _active.value = null
        val p = persistence ?: return
        scope.launch { runCatching { p.clearActiveSession() } }
    }

    fun activeKind(): ActiveSessionKind? = _active.value?.kind

    fun activeSessionId(): String? = _active.value?.sessionId
}
