package com.example.yanji.data.timer

import com.example.yanji.data.FocusModes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
enum class ActiveSessionKind { FOCUS, EXAM }

/**
 * 协调器状态机状态：
 * IDLE -> ACTIVE -> COMPLETING -> IDLE
 */
enum class CoordinatorState {
    IDLE,
    STARTING,
    ACTIVE,
    COMPLETING
}

/**
 * 一个正在进行的计时会话的元信息。
 *
 * 它由业务层持有，而不是 Compose 的 `remember` —— 这样切 Tab、Activity 重建、
 * 甚至前台 Service 独立运行都不会丢失"我正在进行哪一场专注 / 模考"这件事。
 */
@Serializable
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

/**
 * 活动会话的持久化载荷，保存于 context.noBackupFilesDir。
 */
@Serializable
data class ActiveSessionRecord(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val activeSession: ActiveSession,
    val timerSnapshot: TimerSnapshot,
    val lastPersistedWallClockMs: Long = System.currentTimeMillis(),
    val bootId: String = ""
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

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

    /** 把活动会话快照写入持久层，用于进程死亡后的恢复。 */
    suspend fun saveActiveSession(record: ActiveSessionRecord)

    /** 兼容旧接口：把活动会话写入持久层。 */
    suspend fun saveActiveSession(session: ActiveSession)

    /** 活动会话结束时清除持久化记录。 */
    suspend fun clearActiveSession()

    /** 读回上次未结束的活动会话快照记录。 */
    suspend fun loadActiveSessionRecord(): ActiveSessionRecord?

    /** 读回上次未结束的活动会话。 */
    suspend fun loadActiveSession(): ActiveSession?
}

/**
 * 活动计时会话的核心协调逻辑。
 *
 * 状态机迁移：
 * ```
 * IDLE ──begin──► ACTIVE ──complete──► COMPLETING ──write DB success──► IDLE
 *                   │                      │
 *                   │                      └──write DB error──► ACTIVE (可重试)
 *                   └──cancel──► IDLE
 * ```
 *
 * 具备：
 * 1. 严格领域层互斥（多线程/并发 begin 保证最多一个成功）；
 * 2. 状态机 IDLE -> ACTIVE -> COMPLETING -> IDLE；
 * 3. 完成时先写 Room，Room 成功后才清空持久化快照；Room 失败 active 不丢失且可重试；
 * 4. 区分同一 boot（恢复真实计时）与 reboot（恢复为暂停等待用户确认，不伪造 elapsed）。
 */
open class ActiveSessionCoordinatorCore(
    persistence: TimerSessionPersistence? = null,
    val clock: MonotonicClock = SystemMonotonicClock,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    protected val stateLock = Any()

    @Volatile
    var persistence: TimerSessionPersistence? = persistence
        protected set

    protected val _state = MutableStateFlow(CoordinatorState.IDLE)
    val coordinatorState: StateFlow<CoordinatorState> = _state.asStateFlow()

    protected val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    @Volatile
    var currentTimerSnapshot: TimerSnapshot? = null
        protected set

    @Volatile
    var lastPersistenceJob: Job? = null
        protected set

    private val persistenceMutex = Mutex()

    suspend fun awaitPersistence() {
        lastPersistenceJob?.join()
    }

    open fun bind(persistence: TimerSessionPersistence) {
        this.persistence = persistence
    }

    /** 当前是否有活动会话在跑或在完成中。 */
    val isBusy: Boolean
        get() = synchronized(stateLock) { _state.value != CoordinatorState.IDLE }

    fun activeKind(): ActiveSessionKind? = _active.value?.kind

    fun activeSessionId(): String? = _active.value?.sessionId

    /**
     * 登记一个活动会话。
     *
     * 领域层并发控制：同一时刻只允许存在一个活动计时会话（专注与模考互斥）。
     * 无论在任何线程并发调用，原子性保证最多一个成功。
     *
     * @return 是否接受；已有其它会话在跑或正在完成时返回 false。
     */
    suspend fun begin(session: ActiveSession): Boolean {
        val recordToSave = synchronized(stateLock) {
            if (_state.value != CoordinatorState.IDLE) {
                // 同一 session 的重复登记是幂等读取，绝不能重置计时快照。
                return _state.value == CoordinatorState.ACTIVE &&
                    _active.value?.sessionId == session.sessionId
            }
            val snapshot = TimerSnapshot(
                phase = if (session.paused) TimerPhase.PAUSED else TimerPhase.RUNNING,
                startedAtEpochMs = session.startedAtEpochMs,
                targetDurationSeconds = session.targetDurationSeconds,
                accumulatedActiveMs = session.accumulatedActiveMs,
                resumedAtMonotonicMs = if (session.paused) null else clock.nowMs(),
                pauseCount = session.pauseCount
            )
            currentTimerSnapshot = snapshot
            _active.value = session
            _state.value = CoordinatorState.STARTING
            ActiveSessionRecord(
                formatVersion = ActiveSessionRecord.CURRENT_FORMAT_VERSION,
                activeSession = session,
                timerSnapshot = snapshot,
                lastPersistedWallClockMs = System.currentTimeMillis(),
                bootId = clock.currentBootId()
            )
        }

        val p = persistence
        if (p == null) {
            rollbackStartingSession(session.sessionId)
            return false
        }
        return try {
            persistenceMutex.withLock { p.saveActiveSession(recordToSave) }
            synchronized(stateLock) {
                if (_state.value != CoordinatorState.STARTING || _active.value?.sessionId != session.sessionId) {
                    return false
                }
                _state.value = CoordinatorState.ACTIVE
            }
            true
        } catch (_: Exception) {
            rollbackStartingSession(session.sessionId)
            false
        }
    }

    private fun rollbackStartingSession(sessionId: String) {
        synchronized(stateLock) {
            if (_state.value == CoordinatorState.STARTING && _active.value?.sessionId == sessionId) {
                _state.value = CoordinatorState.IDLE
                _active.value = null
                currentTimerSnapshot = null
            }
        }
    }

    /** 更新正在进行的会话状态（如暂停/恢复/时段累积）。 */
    fun update(transform: (ActiveSession) -> ActiveSession) {
        val recordToSave: ActiveSessionRecord? = synchronized(stateLock) {
            if (_state.value != CoordinatorState.ACTIVE) return@synchronized null
            val current = _active.value ?: return@synchronized null
            val requested = transform(current)

            val oldSnap = currentTimerSnapshot ?: TimerSnapshot(
                startedAtEpochMs = requested.startedAtEpochMs,
                targetDurationSeconds = requested.targetDurationSeconds
            )
            val newSnap = if (requested.paused) {
                val runningPart = oldSnap.resumedAtMonotonicMs?.let { clock.nowMs() - it } ?: 0L
                val newAccum = maxOf(requested.accumulatedActiveMs, oldSnap.accumulatedActiveMs + runningPart)
                oldSnap.copy(
                    phase = TimerPhase.PAUSED,
                    accumulatedActiveMs = newAccum,
                    resumedAtMonotonicMs = null,
                    pauseCount = requested.pauseCount
                )
            } else {
                oldSnap.copy(
                    phase = TimerPhase.RUNNING,
                    accumulatedActiveMs = maxOf(requested.accumulatedActiveMs, oldSnap.accumulatedActiveMs),
                    resumedAtMonotonicMs = clock.nowMs(),
                    pauseCount = requested.pauseCount
                )
            }
            val updated = requested.copy(
                accumulatedActiveMs = newSnap.accumulatedActiveMs,
                pauseCount = newSnap.pauseCount,
                paused = newSnap.phase == TimerPhase.PAUSED
            )
            _active.value = updated
            currentTimerSnapshot = newSnap
            ActiveSessionRecord(
                formatVersion = ActiveSessionRecord.CURRENT_FORMAT_VERSION,
                activeSession = updated,
                timerSnapshot = newSnap,
                lastPersistedWallClockMs = System.currentTimeMillis(),
                bootId = clock.currentBootId()
            )
        }
        val p = persistence ?: return
        recordToSave?.let { record ->
            lastPersistenceJob = scope.launch {
                persistenceMutex.withLock { p.saveActiveSession(record) }
            }
        }
    }

    /**
     * 计时结束（倒计时归零 / 用户主动结束）→ 按实际时长落库。
     *
     * 状态机流转：
     * 1. ACTIVE -> COMPLETING；
     * 2. 先写 Room 数据库；
     * 3. Room 成功后才清空持久化 active 快照，COMPLETING -> IDLE；
     * 4. 若 DB 失败，状态回退为 ACTIVE，active 不丢失且可重试。
     */
    suspend fun complete(
        actualSeconds: Long,
        pausedSeconds: Long = 0L,
        pauseCount: Int = 0,
        endEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        val session = synchronized(stateLock) {
            if (_state.value != CoordinatorState.ACTIVE) return false
            val current = _active.value ?: return false
            _state.value = CoordinatorState.COMPLETING
            current
        }

        val p = persistence ?: run {
            synchronized(stateLock) { _state.value = CoordinatorState.ACTIVE }
            return false
        }

        return try {
            awaitPersistence()
            // 步骤 1 & 2：先写 Room 数据库，此时不清除 active 数据
            when (session.kind) {
                ActiveSessionKind.FOCUS ->
                    p.completeFocus(session, actualSeconds, pausedSeconds, pauseCount, endEpochMs)
                ActiveSessionKind.EXAM ->
                    p.completeExam(session, actualSeconds, endEpochMs)
            }
            // 步骤 3：Room 写入成功后，清除持久化记录并归位为 IDLE
            p.clearActiveSession()
            synchronized(stateLock) {
                _state.value = CoordinatorState.IDLE
                _active.value = null
                currentTimerSnapshot = null
            }
            true
        } catch (e: Exception) {
            // 步骤 4：DB 失败，active 数据仍然可恢复和重试
            synchronized(stateLock) {
                _state.value = CoordinatorState.ACTIVE
            }
            throw e
        }
    }

    /** 异步非阻塞完成入口。 */
    fun completeAsync(
        actualSeconds: Long,
        pausedSeconds: Long = 0L,
        pauseCount: Int = 0,
        endEpochMs: Long = System.currentTimeMillis()
    ): Job = scope.launch {
        runCatching {
            complete(actualSeconds, pausedSeconds, pauseCount, endEpochMs)
        }
    }

    /** 用户放弃本次计时：不产生任何记录，清空持久化快照。 */
    fun cancel() {
        val sessionId = synchronized(stateLock) {
            if (_state.value != CoordinatorState.ACTIVE) return
            _state.value = CoordinatorState.COMPLETING
            _active.value?.sessionId ?: return
        }
        val p = persistence ?: run {
            synchronized(stateLock) { _state.value = CoordinatorState.ACTIVE }
            return
        }
        lastPersistenceJob = scope.launch {
            try {
                persistenceMutex.withLock { p.clearActiveSession() }
                synchronized(stateLock) {
                    if (_active.value?.sessionId == sessionId) {
                        _state.value = CoordinatorState.IDLE
                        _active.value = null
                        currentTimerSnapshot = null
                    }
                }
            } catch (_: Exception) {
                synchronized(stateLock) {
                    if (_active.value?.sessionId == sessionId) _state.value = CoordinatorState.ACTIVE
                }
            }
        }
    }

    /**
     * 同步/挂起恢复未结束的活动会话。
     *
     * 恢复语义：
     * - 同一 boot（bootId 相同且单调时钟未倒退）：恢复真实 ActiveSession，包含后台流逝时间；若倒计时已结束则夹紧为 0 待结算；
     * - Reboot（bootId 变更或单调时钟失效）：单调基准失效，严禁伪造 elapsed，明确恢复为暂停状态待用户确认。
     */
    suspend fun restorePersistedNow(): ActiveSession? {
        val p = persistence ?: return null
        val record = runCatching { p.loadActiveSessionRecord() }.getOrNull()
            ?: runCatching { p.loadActiveSession() }.getOrNull()?.let { s ->
                ActiveSessionRecord(
                    activeSession = s,
                    timerSnapshot = TimerSnapshot(
                        phase = if (s.paused) TimerPhase.PAUSED else TimerPhase.RUNNING,
                        startedAtEpochMs = s.startedAtEpochMs,
                        targetDurationSeconds = s.targetDurationSeconds,
                        accumulatedActiveMs = s.accumulatedActiveMs,
                        pauseCount = s.pauseCount
                    ),
                    lastPersistedWallClockMs = System.currentTimeMillis()
                )
            }
            ?: return null

        val restored = synchronized(stateLock) {
            if (_state.value != CoordinatorState.IDLE) return@synchronized null

            val isSameBoot = record.bootId.isNotBlank() &&
                record.bootId == clock.currentBootId() &&
                (record.timerSnapshot.resumedAtMonotonicMs == null || clock.nowMs() >= record.timerSnapshot.resumedAtMonotonicMs)

            if (isSameBoot) {
                // 同一 boot：单调时钟有效，计算真实流逝
                val runningMs = if (record.timerSnapshot.phase == TimerPhase.RUNNING && record.timerSnapshot.resumedAtMonotonicMs != null) {
                    maxOf(0L, clock.nowMs() - record.timerSnapshot.resumedAtMonotonicMs)
                } else {
                    0L
                }
                val totalElapsedMs = record.timerSnapshot.accumulatedActiveMs + runningMs
                val isCountdown = record.timerSnapshot.isCountdown
                val targetMs = record.timerSnapshot.targetDurationSeconds * 1000L
                val effectiveElapsedMs = if (isCountdown && totalElapsedMs >= targetMs) {
                    targetMs
                } else {
                    totalElapsedMs
                }

                val session = record.activeSession.copy(
                    accumulatedActiveMs = effectiveElapsedMs,
                    paused = record.timerSnapshot.phase == TimerPhase.PAUSED
                )
                val snapshot = record.timerSnapshot.copy(
                    accumulatedActiveMs = effectiveElapsedMs,
                    resumedAtMonotonicMs = if (record.timerSnapshot.phase == TimerPhase.RUNNING) clock.nowMs() else null
                )
                currentTimerSnapshot = snapshot
                _active.value = session
                _state.value = CoordinatorState.ACTIVE
                session
            } else {
                // Reboot：单调时钟基准已失效，恢复为暂停待用户确认，绝不伪造 elapsed
                val session = record.activeSession.copy(
                    paused = true,
                    accumulatedActiveMs = record.timerSnapshot.accumulatedActiveMs
                )
                val snapshot = record.timerSnapshot.copy(
                    phase = TimerPhase.PAUSED,
                    resumedAtMonotonicMs = null,
                    accumulatedActiveMs = record.timerSnapshot.accumulatedActiveMs
                )
                currentTimerSnapshot = snapshot
                _active.value = session
                _state.value = CoordinatorState.ACTIVE

                // 将暂停状态持久化回盘，保证后续重启也是暂停态
                val updatedRecord = record.copy(
                    activeSession = session,
                    timerSnapshot = snapshot,
                    bootId = clock.currentBootId(),
                    lastPersistedWallClockMs = System.currentTimeMillis()
                )
                lastPersistenceJob = scope.launch { runCatching { p.saveActiveSession(updatedRecord) } }
                session
            }
        }
        return restored
    }

    /** 进程启动时把上次未结束的活动会话读回内存。 */
    fun restorePersisted(onRestored: ((ActiveSession?) -> Unit)? = null): Job = scope.launch {
        val session = restorePersistedNow()
        onRestored?.invoke(session)
    }

    fun resetForTesting() {
        synchronized(stateLock) {
            _state.value = CoordinatorState.IDLE
            _active.value = null
            currentTimerSnapshot = null
            persistence = null
        }
    }
}

/**
 * 活动计时会话的**业务层归属者**全局单例。
 */
object ActiveSessionCoordinator : ActiveSessionCoordinatorCore()
