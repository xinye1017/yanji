package com.example.yanji.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.example.yanji.data.FocusModes
import com.example.yanji.data.timer.ActiveSession
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.data.timer.CoordinatorState
import com.example.yanji.data.timer.SystemMonotonicClock
import com.example.yanji.data.timer.TimerCalculator
import com.example.yanji.data.timer.TimerPhase
import com.example.yanji.service.FocusTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 「研迹」React Native ↔ Kotlin 计时桥接模块。
 *
 * 职责边界（严守 AGENTS.md §七「不要绕过 Kotlin Repository 直接从 JS 管理 Room 数据」）：
 * - 本模块**不**实现任何计时逻辑，也不持有第二套计时器；
 * - 所有动作委托给业务层唯一权威：[ActiveSessionCoordinator]（登记/暂停/结束语义）
 *   与 [FocusTimerService]（前台服务，单调时钟计时事实）；
 * - 计时数值的权威来源是 `TimerMachine + SystemClock.elapsedRealtime`，
 *   JS 侧每秒展示只消费原生 `onTimerTick` 事件推送。
 *
 * 事件：
 * - `onTimerTick`：`{ elapsedSeconds, remainingSeconds, phase, isPaused }`
 * - `onTimerStateChanged`：`{ state, session }`
 */
class YanjiTimerModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 上一帧 tick 是否已暂停，避免暂停期间继续高频推送。 */
    @Volatile
    private var lastEmittedPaused: Boolean? = null

    init {
        observeCoordinator()
        observeTicker()
    }

    override fun getName(): String = "YanjiTimerModule"

    // ---------------------------------------------------------------- 查询

    /**
     * 当前活动会话快照。字段命名与 [com.example.yanji.bridge.BridgeSchemas] 的
     * ActiveSessionState 契约逐字对齐。
     */
    @ReactMethod
    fun getActiveSession(promise: Promise) {
        val session = ActiveSessionCoordinator.active.value
        val snapshot = ActiveSessionCoordinator.currentTimerSnapshot
        if (session == null || snapshot == null) {
            promise.resolve(null)
            return
        }
        val elapsed = TimerCalculator.calculateElapsedSeconds(snapshot, SystemMonotonicClock.nowMs())
        promise.resolve(activeSessionMap(session, elapsed, remainingOf(snapshot.targetDurationSeconds, elapsed)))
    }

    // ---------------------------------------------------------------- 动作

    /**
     * 开始一次专注。
     *
     * @param mode 模式名（`正向计时` / `25分钟番茄` / `N分钟专注` 等），
     *   目标秒数由 [FocusModes.targetSeconds] 解析——不在 JS 侧重算时长。
     */
    @ReactMethod
    fun startFocus(
        subjectId: String,
        subjectName: String,
        mode: String,
        note: String,
        taskId: String?,
        promise: Promise
    ) {
        val context = reactContext.applicationContext
        scope.launch {
            val started = com.example.yanji.data.YanjiRepository.getInstance()
                .startFocus(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    note = note,
                    mode = mode.ifBlank { FocusModes.COUNT_UP },
                    taskId = taskId?.takeIf { it.isNotBlank() }
                )
            if (started == null) {
                promise.reject("E_SESSION_ACTIVE", "A focus session is already active")
                return@launch
            }
            FocusTimerService.startFocus(
                context = context,
                sessionId = started.id,
                subjectName = started.subjectName,
                targetSeconds = FocusModes.targetSeconds(started.mode)
            )
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun pauseTimer(promise: Promise) {
        val context = reactContext.applicationContext
        FocusTimerService.pauseTimer(context)
        promise.resolve(true)
    }

    @ReactMethod
    fun resumeTimer(promise: Promise) {
        val context = reactContext.applicationContext
        FocusTimerService.resumeTimer(context)
        promise.resolve(true)
    }

    /** 主动结束并保存（记录实际时长）。 */
    @ReactMethod
    fun completeTimer(promise: Promise) {
        val context = reactContext.applicationContext
        FocusTimerService.completeTimer(context)
        promise.resolve(true)
    }

    /** 放弃本次计时：不产生任何记录。 */
    @ReactMethod
    fun discardTimer(promise: Promise) {
        val context = reactContext.applicationContext
        FocusTimerService.discardTimer(context)
        promise.resolve(true)
    }

    // ---------------------------------------------------------------- 事件

    private fun observeCoordinator() {
        scope.launch {
            ActiveSessionCoordinator.coordinatorState.collectLatest { state ->
                emitStateChanged(state)
            }
        }
    }

    private fun observeTicker() {
        scope.launch {
            FocusTimerService.elapsedSecondsForUi.collectLatest { elapsed ->
                val session = ActiveSessionCoordinator.active.value ?: return@collectLatest
                val snapshot = ActiveSessionCoordinator.currentTimerSnapshot ?: return@collectLatest
                val paused = snapshot.phase == TimerPhase.PAUSED
                if (lastEmittedPaused == paused && paused) return@collectLatest
                lastEmittedPaused = paused
                emitTick(elapsed, remainingOf(snapshot.targetDurationSeconds, elapsed), paused, session)
            }
        }
    }

    private fun remainingOf(targetSeconds: Long, elapsedSeconds: Long): Long {
        if (targetSeconds <= 0L) return 0L
        return (targetSeconds - elapsedSeconds).coerceAtLeast(0L)
    }

    private fun emitTick(
        elapsedSeconds: Long,
        remainingSeconds: Long,
        isPaused: Boolean,
        session: ActiveSession
    ) {
        val payload = Arguments.createMap().apply {
            putDouble("elapsedSeconds", elapsedSeconds.toDouble())
            putDouble("remainingSeconds", remainingSeconds.toDouble())
            // 与 ActiveSessionState.phase 同一套词汇表（FOCUS/BREAK/IDLE），
            // 暂停只由 isPaused 承载，绝不外泄 TimerPhase 枚举名。
            putString("phase", BridgeMappers.tickPhase(hasSession = true))
            putBoolean("isPaused", isPaused)
        }
        emit(EVENT_TIMER_TICK, payload)
    }

    private fun emitStateChanged(state: CoordinatorState) {
        val session = ActiveSessionCoordinator.active.value
        val snapshot = ActiveSessionCoordinator.currentTimerSnapshot
        val payload = Arguments.createMap().apply {
            putString("state", state.name)
            if (session != null && snapshot != null) {
                val elapsed = TimerCalculator.calculateElapsedSeconds(snapshot, SystemMonotonicClock.nowMs())
                putMap(
                    "session",
                    activeSessionMap(session, elapsed, remainingOf(snapshot.targetDurationSeconds, elapsed))
                )
            } else {
                putNull("session")
            }
        }
        emit(EVENT_TIMER_STATE_CHANGED, payload)
    }

    /**
     * 活动会话载荷（TS `ActiveSessionState`）。
     *
     * 契约取值：
     *  - `phase` ∈ {FOCUS, BREAK, IDLE}，会话存在时恒为 FOCUS（暂停也由 isPaused 承载）；
     *  - `mode` ∈ {COUNTDOWN, STOPWATCH}，由 `targetDurationSeconds > 0` 推导，
     *    **绝不**把中文展示名（`正向计时`）透传过桥。
     *
     * 纯语义在 [BridgeMappers.sessionFields]，这里只装配 WritableMap。
     */
    private fun activeSessionMap(
        session: ActiveSession,
        elapsedSeconds: Long,
        remainingSeconds: Long
    ): WritableMap {
        val fields = BridgeMappers.sessionFields(session, elapsedSeconds, remainingSeconds)
        return Arguments.createMap().apply {
            putString("sessionId", fields.sessionId)
            putString("subjectId", fields.subjectId)
            putString("subjectName", fields.subjectName)
            putString("mode", fields.mode)
            putString("taskId", fields.taskId)
            putDouble("startTime", fields.startTime)
            putDouble("elapsedSeconds", fields.elapsedSeconds)
            putDouble("remainingSeconds", fields.remainingSeconds)
            putString("phase", fields.phase)
            putBoolean("isPaused", fields.isPaused)
            putBoolean("isCountdown", fields.isCountdown)
        }
    }

    private fun emit(eventName: String, payload: WritableMap) {
        if (!reactContext.hasActiveReactInstance()) return
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, payload)
    }

    companion object {
        const val EVENT_TIMER_TICK = "onTimerTick"
        const val EVENT_TIMER_STATE_CHANGED = "onTimerStateChanged"

        /**
         * 会话阶段常量（与 BridgeSchemas 契约一致）。
         * 真实定义见 [BridgeMappers] —— 这里只做转发，避免出现第二份词汇表。
         */
        const val PHASE_FOCUS = BridgeMappers.PHASE_FOCUS
        const val PHASE_BREAK = BridgeMappers.PHASE_BREAK
        const val PHASE_IDLE = BridgeMappers.PHASE_IDLE
    }
}
