package com.example.yanji.data.timer

import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.db.ExamSessionEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.yanji.data.achievement.AchievementEvent
import java.util.UUID

/**
 * 专注 / 模考会话的状态与动作归属者。
 *
 * 职责边界：
 *  - 持有 `_focusSessions` / `_examSessions` / 活动会话 / 「刚完成」事件；
 *  - 实现 [TimerSessionPersistence]，作为 [ActiveSessionCoordinator] 的落库出口；
 *  - 计时的**时间事实**仍由前台 Service 的 [TimerMachine]（单调时钟）负责，
 *    本类只处理业务语义（谁在计时、结束之后写什么）。
 */
internal class TimerStore(
    private val scope: CoroutineScope,
    private val dbProvider: () -> YanjiDatabase?,
    private var diskPersistence: TimerSessionPersistence? = null
) {

    var onAchievementEvent: (suspend (AchievementEvent) -> Unit)? = null

    fun setDiskPersistence(persistence: TimerSessionPersistence) {
        this.diskPersistence = persistence
    }

    companion object {
        /** 最短可记录时长：与 UI 的「不足 1 分钟不予保存」提示保持同一条业务规则。 */
        private const val MIN_RECORDED_FOCUS_SECONDS = 60L
    }

    private val _focusSessions = MutableStateFlow<List<FocusSession>>(emptyList())
    val focusSessions: StateFlow<List<FocusSession>> = _focusSessions.asStateFlow()

    private val _examSessions = MutableStateFlow<List<ExamSession>>(emptyList())
    val examSessions: StateFlow<List<ExamSession>> = _examSessions.asStateFlow()

    private val _activeFocus = MutableStateFlow<FocusSession?>(null)
    val activeFocus: StateFlow<FocusSession?> = _activeFocus.asStateFlow()

    private val _lastCompletedFocus = MutableStateFlow<FocusSession?>(null)
    val lastCompletedFocus: StateFlow<FocusSession?> = _lastCompletedFocus.asStateFlow()

    private val _lastCompletedExam = MutableStateFlow<ExamSession?>(null)
    val lastCompletedExam: StateFlow<ExamSession?> = _lastCompletedExam.asStateFlow()

    fun bind(db: YanjiDatabase) {
        scope.launch {
            db.focusSessionDao().getAll().collect { entities ->
                _focusSessions.value = entities.map { it.toDomainModel() }
            }
        }
        scope.launch {
            db.examSessionDao().getAll().collect { entities ->
                _examSessions.value = entities.map { it.toDomainModel() }
            }
        }
        scope.launch {
            ActiveSessionCoordinator.active.collect { session ->
                if (session == null) {
                    _activeFocus.value = null
                } else if (session.kind == ActiveSessionKind.FOCUS) {
                    _activeFocus.value = FocusSession(
                        id = session.sessionId,
                        subjectId = session.subjectId,
                        subjectName = session.subjectName,
                        startTime = session.startedAtEpochMs,
                        endTime = session.startedAtEpochMs,
                        durationSeconds = session.accumulatedActiveMs / 1000L,
                        pausedDurationSeconds = 0L,
                        pauseCount = session.pauseCount,
                        mode = session.mode,
                        note = session.note,
                        status = if (session.paused) SessionStatus.PAUSED else SessionStatus.RUNNING
                    )
                } else {
                    _activeFocus.value = null
                }
            }
        }
    }

    val persistence: TimerSessionPersistence = object : TimerSessionPersistence {
        override suspend fun completeFocus(
            session: ActiveSession,
            actualSeconds: Long,
            pausedSeconds: Long,
            pauseCount: Int,
            endEpochMs: Long
        ) = persistCompletedFocus(session, actualSeconds, pausedSeconds, pauseCount, endEpochMs)

        override suspend fun completeExam(session: ActiveSession, actualSeconds: Long, endEpochMs: Long) =
            persistCompletedExam(session, actualSeconds, endEpochMs)

        override suspend fun saveActiveSession(record: ActiveSessionRecord) {
            requireDiskPersistence().saveActiveSession(record)
        }

        override suspend fun saveActiveSession(session: ActiveSession) {
            requireDiskPersistence().saveActiveSession(session)
        }

        override suspend fun clearActiveSession() {
            requireDiskPersistence().clearActiveSession()
        }

        override suspend fun loadActiveSession(): ActiveSession? {
            return requireDiskPersistence().loadActiveSession()
        }

        override suspend fun loadActiveSessionRecord(): ActiveSessionRecord? {
            return requireDiskPersistence().loadActiveSessionRecord()
        }
    }

    private fun requireDiskPersistence(): TimerSessionPersistence =
        checkNotNull(diskPersistence) { "Timer disk persistence is not bound" }

    // ------------------------------------------------------------ 会话生命周期

    /**
     * 开始一次专注。
     * @return 新建的会话（调用方把 id 交给前台 Service）；已有计时在跑时返回 null。
     */
    suspend fun startFocus(
        subjectId: String,
        subjectName: String,
        note: String,
        mode: String = FocusModes.COUNT_UP
    ): FocusSession? {
        val now = System.currentTimeMillis()
        val session = FocusSession(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            subjectName = subjectName,
            startTime = now,
            endTime = now,
            durationSeconds = 0,
            note = note,
            mode = mode,
            status = SessionStatus.RUNNING
        )
        val accepted = ActiveSessionCoordinator.begin(
            ActiveSession(
                sessionId = session.id,
                kind = ActiveSessionKind.FOCUS,
                subjectId = subjectId,
                subjectName = subjectName,
                mode = mode,
                note = note,
                targetDurationSeconds = FocusModes.targetSeconds(mode),
                startedAtEpochMs = now
            )
        )
        if (!accepted) return null
        _activeFocus.value = session
        return session
    }

    /** 登记一场模考。@return null 表示已有计时在跑（专注与模考互斥）。 */
    suspend fun startExamSession(
        subjectId: String,
        subjectName: String,
        plannedDurationSeconds: Long
    ): ExamSession? {
        val now = System.currentTimeMillis()
        val session = ExamSession(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            subjectName = subjectName,
            plannedDurationSeconds = plannedDurationSeconds,
            actualDurationSeconds = 0,
            startTime = now,
            endTime = now,
            status = SessionStatus.RUNNING
        )
        val accepted = ActiveSessionCoordinator.begin(
            ActiveSession(
                sessionId = session.id,
                kind = ActiveSessionKind.EXAM,
                subjectId = subjectId,
                subjectName = subjectName,
                targetDurationSeconds = plannedDurationSeconds,
                plannedDurationSeconds = plannedDurationSeconds,
                startedAtEpochMs = now
            )
        )
        if (!accepted) return null
        return session
    }

    /** UI 侧的暂停镜像；真实计时事实由前台 Service 的 [TimerMachine] 维护。 */
    fun pauseFocus(elapsedSeconds: Long = 0L) {
        val current = _activeFocus.value ?: return
        if (current.status == SessionStatus.RUNNING) {
            val newDuration = if (elapsedSeconds > 0) elapsedSeconds else current.durationSeconds
            _activeFocus.value = current.copy(
                status = SessionStatus.PAUSED,
                durationSeconds = newDuration,
                pauseCount = current.pauseCount + 1
            )
            ActiveSessionCoordinator.update {
                it.copy(
                    paused = true,
                    accumulatedActiveMs = newDuration * 1000L,
                    pauseCount = it.pauseCount + 1
                )
            }
        }
    }

    fun resumeFocus() {
        val current = _activeFocus.value ?: return
        if (current.status == SessionStatus.PAUSED) {
            _activeFocus.value = current.copy(status = SessionStatus.RUNNING)
            ActiveSessionCoordinator.update { it.copy(paused = false) }
        }
    }

    fun updateFocusDuration(seconds: Long) {
        val current = _activeFocus.value ?: return
        _activeFocus.value = current.copy(durationSeconds = seconds)
    }

    /** 用户放弃本次专注：不产生任何记录。 */
    fun cancelFocus() {
        _activeFocus.value = null
        ActiveSessionCoordinator.cancel()
    }

    /** 用户放弃本场模考：不产生记录。 */
    fun abandonExam() {
        ActiveSessionCoordinator.cancel()
    }

    /** 备份导入后由业务层调用：本机不存在"进行中"的会话了。 */
    fun clearActiveFocus() {
        _activeFocus.value = null
    }

    fun acknowledgeCompletedFocus() {
        _lastCompletedFocus.value = null
    }

    fun acknowledgeCompletedExam() {
        _lastCompletedExam.value = null
    }

    // ------------------------------------------------------------ 落库出口

    /** 专注完成。数据只写 Room，内存列表交给 DAO Flow 回灌。 */
    private suspend fun persistCompletedFocus(
        session: ActiveSession,
        actualSeconds: Long,
        pausedSeconds: Long,
        pauseCount: Int,
        endEpochMs: Long
    ) {
        if (actualSeconds < MIN_RECORDED_FOCUS_SECONDS) {
            _activeFocus.value = null
            return
        }

        val recorded = FocusSession(
            id = session.sessionId,
            subjectId = session.subjectId,
            subjectName = session.subjectName,
            startTime = session.startedAtEpochMs,
            endTime = endEpochMs,
            durationSeconds = actualSeconds,
            pausedDurationSeconds = pausedSeconds.coerceAtLeast(0L),
            pauseCount = pauseCount,
            mode = session.mode,
            note = session.note,
            status = SessionStatus.COMPLETED
        )
        val db = dbProvider() ?: throw IllegalStateException("Database not available")
        db.focusSessionDao().insert(FocusSessionEntity.fromDomainModel(recorded))
        _activeFocus.value = null
        _lastCompletedFocus.value = recorded
        onAchievementEvent?.invoke(AchievementEvent.FocusCompleted(recorded))
    }

    /** 模考完成。 */
    private suspend fun persistCompletedExam(session: ActiveSession, actualSeconds: Long, endEpochMs: Long) {
        val recorded = ExamSession(
            id = session.sessionId,
            subjectId = session.subjectId,
            subjectName = session.subjectName,
            plannedDurationSeconds = session.plannedDurationSeconds,
            actualDurationSeconds = actualSeconds.coerceAtLeast(0L),
            startTime = session.startedAtEpochMs,
            endTime = endEpochMs,
            score = null,
            maxScore = 150.0,
            note = session.note,
            status = SessionStatus.COMPLETED
        )
        val db = dbProvider() ?: throw IllegalStateException("Database not available")
        db.examSessionDao().insert(ExamSessionEntity.fromDomainModel(recorded))
        _lastCompletedExam.value = recorded
        onAchievementEvent?.invoke(AchievementEvent.ExamCompleted(recorded))
    }

    // ------------------------------------------------------------ 记录 CRUD

    suspend fun addFocusSession(session: FocusSession) {
        _focusSessions.value = listOf(session) + _focusSessions.value
        withContext(Dispatchers.IO) {
            dbProvider()?.focusSessionDao()?.insert(FocusSessionEntity.fromDomainModel(session))
            onAchievementEvent?.invoke(AchievementEvent.FocusCompleted(session))
        }
    }

    fun addExamSession(session: ExamSession) {
        _examSessions.value = listOf(session) + _examSessions.value
        scope.launch {
            dbProvider()?.examSessionDao()?.insert(ExamSessionEntity.fromDomainModel(session))
            onAchievementEvent?.invoke(AchievementEvent.ExamCompleted(session))
        }
    }

    fun deleteFocusSession(id: String) {
        _focusSessions.value = _focusSessions.value.filter { it.id != id }
        scope.launch {
            dbProvider()?.focusSessionDao()?.deleteById(id)
        }
    }

    fun updateFocusSessionNote(id: String, note: String) {
        _focusSessions.value = _focusSessions.value.map { if (it.id == id) it.copy(note = note) else it }
        scope.launch {
            dbProvider()?.focusSessionDao()?.updateNote(id, note)
        }
    }

    fun deleteExamSession(id: String) {
        _examSessions.value = _examSessions.value.filter { it.id != id }
        scope.launch {
            dbProvider()?.examSessionDao()?.deleteById(id)
        }
    }

    suspend fun getFocusSessionByIdFromDb(id: String): FocusSessionEntity? = withContext(Dispatchers.IO) {
        dbProvider()?.focusSessionDao()?.getById(id)
    }

    suspend fun getExamSessionByIdFromDb(id: String): ExamSessionEntity? = withContext(Dispatchers.IO) {
        dbProvider()?.examSessionDao()?.getById(id)
    }

    fun getFocusSessionByIdFlow(id: String): Flow<FocusSession?> {
        val db = dbProvider()
        return if (db != null) {
            db.focusSessionDao().getByIdFlow(id).map { it?.toDomainModel() }
        } else {
            _focusSessions.map { list -> list.find { it.id == id } }
        }
    }

    fun getExamSessionByIdFlow(id: String): Flow<ExamSession?> {
        val db = dbProvider()
        return if (db != null) {
            db.examSessionDao().getByIdFlow(id).map { it?.toDomainModel() }
        } else {
            _examSessions.map { list -> list.find { it.id == id } }
        }
    }
}
