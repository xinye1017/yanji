package com.example.yanji.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.*
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.data.timer.FocusLiveState
import com.example.yanji.data.timer.Idle
import com.example.yanji.data.timer.SystemMonotonicClock
import com.example.yanji.data.timer.TimerMachine
import com.example.yanji.data.timer.TimerPhase
import com.example.yanji.data.timer.focusLiveStateOf
import com.example.yanji.liveactivity.FocusKind
import com.example.yanji.liveactivity.FocusLiveActivityController
import com.example.yanji.liveactivity.FocusTimerActions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前台计时服务。
 *
 * 职责边界（刻意收窄，规范 §32）：
 *  - Service 生命周期、前台化、WakeLock；
 *  - 调 [TimerMachine] 取时间事实，调 [ActiveSessionCoordinator] 落库；
 *  - 语义转换时调 [FocusLiveActivityController] 刷新常驻展示。
 *
 * **不负责**：Compose、ColorOS SDK、Android 版本判断、通知模板细节、流体云布局。
 *
 * 关于每秒 tick：循环仍然存在，但只做两件事——推进 UI 展示镜像、检测倒计时归零。
 * 它**不再碰 NotificationManager**：运行期间 `44:59 → 44:58 → 44:57` 由系统
 * Chronometer 渲染，不再每秒走一次 Binder（规范 §15/§19）。
 *
 * 「结束」语义：
 *  - 倒计时归零 → 正常完成（COMPLETED，记录计划时长）
 *  - 通知栏/页面「结束」 → 主动结束（COMPLETED，记录实际时长）
 *  - 页面「放弃」 → 取消（CANCELLED，不产生记录）
 */
class FocusTimerService : Service() {

    companion object {
        const val CHANNEL_ID = com.example.yanji.liveactivity.FocusNotificationSpecs.CHANNEL_ID
        const val NOTIFICATION_ID = com.example.yanji.liveactivity.FocusNotificationSpecs.ONGOING_NOTIFICATION_ID

        // 动作字符串的唯一定义在 FocusTimerActions（通知层需要用它们构造 PendingIntent，
        // 放在那边可以避免 liveactivity 反向依赖 service）。
        const val ACTION_START_FOCUS = FocusTimerActions.ACTION_START_FOCUS
        const val ACTION_START_EXAM = FocusTimerActions.ACTION_START_EXAM
        const val ACTION_PAUSE = FocusTimerActions.ACTION_PAUSE
        const val ACTION_RESUME = FocusTimerActions.ACTION_RESUME
        const val ACTION_COMPLETE = FocusTimerActions.ACTION_COMPLETE
        const val ACTION_DISCARD = FocusTimerActions.ACTION_DISCARD
        private const val ACTION_RESTORE = "com.example.yanji.action.RESTORE_ACTIVE_TIMER"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"

        private const val UI_TICK_MS = 1000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        private val _liveState = MutableStateFlow<FocusLiveState>(Idle)

        /**
         * 统一语义状态。只在 START / PAUSE / RESUME / FINISH / DISCARD 时发射，
         * 是 App UI、通知、Live Update、流体云共同消费的那一份状态（规范 §2）。
         */
        val liveState: StateFlow<FocusLiveState> = _liveState.asStateFlow()

        private val _elapsedSecondsForUi = MutableStateFlow(0L)

        /**
         * 每秒推进的展示镜像，**只给 App UI 用**，任何通知/流体云控制器都不消费它。
         *
         * 它和 [liveState] 同源于 [TimerMachine]，不是第二套计时器——只是把「单调时钟差值
         * 在每个整秒上的取值」暴露给 Compose，让只有真正需要变化的文本节点重组（规范 §37）。
         */
        val elapsedSecondsForUi: StateFlow<Long> = _elapsedSecondsForUi.asStateFlow()

        private val _remainingSecondsForUi = MutableStateFlow(0L)

        /**
         * 倒计时/模考每秒推进的剩余秒数展示镜像，**只给 App UI 用**。
         *
         * 供 [ExamScreen] / [ImmersiveExamTimer] 以 State 消费，
         * 避免倒计时每秒触发模考页顶层全屏重组（规范 §37）。
         */
        val remainingSecondsForUi: StateFlow<Long> = _remainingSecondsForUi.asStateFlow()

        fun startFocus(context: Context, sessionId: String, subjectName: String, targetSeconds: Long = 0) {
            dispatch(context, ACTION_START_FOCUS, sessionId, subjectName, targetSeconds)
        }

        fun startExam(context: Context, sessionId: String, subjectName: String, durationSeconds: Long = 10800) {
            dispatch(context, ACTION_START_EXAM, sessionId, subjectName, durationSeconds)
        }

        fun pauseTimer(context: Context) = dispatch(context, ACTION_PAUSE, "", "", 0L)

        fun resumeTimer(context: Context) = dispatch(context, ACTION_RESUME, "", "", 0L)

        /** 主动结束并保存（通知栏「结束」走的也是这条语义）。 */
        fun completeTimer(context: Context) = dispatch(context, ACTION_COMPLETE, "", "", 0L)

        /** 放弃本次计时，不产生记录。 */
        fun discardTimer(context: Context) = dispatch(context, ACTION_DISCARD, "", "", 0L)

        /**
         * Rebuild the foreground timer from the durable coordinator snapshot after process death.
         * The repository calls this only after [ActiveSessionCoordinator] has finished restoring.
         */
        fun restoreActive(context: Context) = dispatch(context, ACTION_RESTORE, "", "", 0L)

        private fun dispatch(context: Context, action: String, sessionId: String, subject: String, seconds: Long) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                this.action = action
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_DURATION_SECONDS, seconds)
            }
            if (action == ACTION_START_FOCUS || action == ACTION_START_EXAM || action == ACTION_RESTORE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val machine = TimerMachine(SystemMonotonicClock)

    /**
     * 必须延迟初始化。
     *
     * Android 组件是「先构造、后 attachBaseContext」，因此字段初始化阶段
     * `applicationContext` 仍为 null，直接在这里取会 NPE（启动即崩）。
     */
    private lateinit var liveActivity: FocusLiveActivityController

    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var sessionId: String = ""
    private var subject: String = ""
    private var kind: FocusKind = FocusKind.FOCUS

    override fun onCreate() {
        super.onCreate()
        liveActivity = FocusLiveActivityController(applicationContext)
        // 渠道创建 + 能力探测集中在这里，Service 不再关心 Android 版本与厂商差异。
        liveActivity.initialize()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yanji:TimerWakeLock")
        // 保证落库出口可用：进程可能只为了这个 Service 而被创建。
        runCatching { YanjiRepository.init(applicationContext) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT) ?: "日常专注"
                startTimer(
                    ActiveSessionKind.FOCUS,
                    subjectName,
                    intent.getLongExtra(EXTRA_DURATION_SECONDS, 0L),
                    intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
                )
            }
            ACTION_START_EXAM -> {
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT) ?: "全真模考"
                startTimer(
                    ActiveSessionKind.EXAM,
                    subjectName,
                    intent.getLongExtra(EXTRA_DURATION_SECONDS, 10800L),
                    intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
                )
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_COMPLETE -> completeByUser()
            ACTION_DISCARD -> discard()
            ACTION_RESTORE -> restoreTimer()
        }
        // 不返回 START_STICKY：进程被回收后系统用 null intent 重启本服务只会得到一个
        // "前台通知还在、计时已归零"的假状态。活动会话的恢复由业务层负责（ActiveSessionCoordinator）。
        return START_NOT_STICKY
    }

    private fun startTimer(
        sessionKind: ActiveSessionKind,
        subjectName: String,
        targetSeconds: Long,
        requestedSessionId: String
    ) {
        val active = ActiveSessionCoordinator.active.value
        val snapshot = ActiveSessionCoordinator.currentTimerSnapshot
        if (
            active == null ||
            snapshot == null ||
            active.kind != sessionKind ||
            requestedSessionId.isBlank() ||
            requestedSessionId != active.sessionId
        ) {
            // A foreground service must never invent a second timer when the durable business
            // registration failed or another start won the coordinator race.
            stopSelf()
            return
        }

        timerJob?.cancel()
        machine.restore(snapshot)

        kind = if (sessionKind == ActiveSessionKind.EXAM) FocusKind.EXAM else FocusKind.FOCUS
        subject = active.subjectName.ifBlank { subjectName }
        sessionId = active.sessionId

        val elapsed = machine.elapsedSeconds()
        _elapsedSecondsForUi.value = elapsed
        _remainingSecondsForUi.value = machine.remainingSeconds()
        publishSemanticState(elapsedSeconds = elapsed)

        promoteToForeground()

        // Reboot 恢复出的会话是暂停态：不持唤醒锁、不跑 tick，等用户点「继续」再启动。
        if (snapshot.phase == TimerPhase.PAUSED) return

        acquireWakeLock()

        if (machine.hasReachedTarget()) {
            onCountdownFinished()
            return
        }

        startTicking()
    }

    /**
     * 每秒只做两件事：推进 UI 展示镜像、检测倒计时归零。
     * 计时精度不依赖这里的调度频率，通知也不在这里更新。
     *
     * 暂停 / 结束时协程会被 cancel，不再空转唤醒设备（省电）。
     */
    private fun startTicking() {
        if (timerJob?.isActive == true) return
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(UI_TICK_MS)
                if (machine.snapshot.phase != TimerPhase.RUNNING) break
                if (machine.hasReachedTarget()) {
                    _elapsedSecondsForUi.value = machine.snapshot.targetDurationSeconds
                    _remainingSecondsForUi.value = 0L
                    onCountdownFinished()
                    break
                }
                _elapsedSecondsForUi.value = machine.elapsedSeconds()
                _remainingSecondsForUi.value = machine.remainingSeconds()
            }
        }
    }

    private fun restoreTimer() {
        val active = ActiveSessionCoordinator.active.value ?: run {
            stopSelf()
            return
        }
        startTimer(active.kind, active.subjectName, active.targetDurationSeconds, active.sessionId)
    }

    private fun pause() {
        if (machine.snapshot.phase != TimerPhase.RUNNING) return
        machine.pause()
        // 暂停即停表：取消 tick 协程并释放唤醒锁，避免息屏后每秒空转 + 持锁耗电。
        timerJob?.cancel()
        releaseWakeLock()
        val elapsed = machine.elapsedSeconds()
        _elapsedSecondsForUi.value = elapsed
        _remainingSecondsForUi.value = machine.remainingSeconds()
        ActiveSessionCoordinator.update { it.copy(paused = true, accumulatedActiveMs = machine.elapsedMs()) }
        // 语义变化 → 重建通知：禁用 Chronometer，改为静态冻结时间 + 「继续」动作。
        publishSemanticState()
    }

    private fun resume() {
        if (machine.snapshot.phase != TimerPhase.PAUSED) return
        machine.resume()
        acquireWakeLock()
        _elapsedSecondsForUi.value = machine.elapsedSeconds()
        _remainingSecondsForUi.value = machine.remainingSeconds()
        ActiveSessionCoordinator.update { it.copy(paused = false) }
        // 语义变化 → 重新计算 Chronometer 基准，从冻结时间继续走。
        publishSemanticState()
        startTicking()
    }

    /** 倒计时自然归零：完成并落库。 */
    private fun onCountdownFinished() {
        val seconds = machine.snapshot.targetDurationSeconds
        serviceScope.launch {
            freezeWhileCommitting(seconds)
            val completed = runCatching {
                ActiveSessionCoordinator.complete(
                    actualSeconds = seconds,
                    // 倒计时自然归零也必须记录真实暂停时长：机器里存着 pauseCount 与单调时钟，
                    // 这里写死 0 会让「暂停过 10 分钟的番茄」在库里自相矛盾。
                    pausedSeconds = machine.pausedMs(System.currentTimeMillis()) / 1000L,
                    pauseCount = machine.snapshot.pauseCount,
                    endEpochMs = System.currentTimeMillis()
                )
            }.getOrDefault(false)
            if (!completed) return@launch
            machine.finish()
            publishSemanticState(elapsedSeconds = seconds)
            finishAndRelease(postCompletion = true)
        }
    }

    /** 用户主动结束：按实际时长完成并落库。 */
    private fun completeByUser() {
        val elapsed = machine.elapsedSeconds()
        val pausedSeconds = machine.pausedMs(System.currentTimeMillis()) / 1000L
        serviceScope.launch {
            freezeWhileCommitting(elapsed)
            val completed = runCatching {
                ActiveSessionCoordinator.complete(
                    actualSeconds = elapsed,
                    pausedSeconds = pausedSeconds,
                    pauseCount = machine.snapshot.pauseCount,
                    endEpochMs = System.currentTimeMillis()
                )
            }.getOrDefault(false)
            if (!completed) return@launch
            machine.finish()
            publishSemanticState(elapsedSeconds = elapsed)
            // 正向计时由用户主动收尾时，App 内结算弹窗已经给出反馈，不再额外打扰。
            finishAndRelease(postCompletion = machine.snapshot.isCountdown)
        }
    }

    /**
     * Freeze the display and durably record the final elapsed value before the Room transaction.
     * If Room rejects the completion, both the foreground service and the active file remain in a
     * paused, retryable state instead of silently disappearing.
     */
    private suspend fun freezeWhileCommitting(elapsedSeconds: Long) {
        timerJob?.cancel()
        machine.freezeForCommit()
        _elapsedSecondsForUi.value = elapsedSeconds
        _remainingSecondsForUi.value = machine.remainingSeconds()
        ActiveSessionCoordinator.update {
            it.copy(
                paused = true,
                accumulatedActiveMs = machine.snapshot.accumulatedActiveMs,
                pauseCount = machine.snapshot.pauseCount
            )
        }
        ActiveSessionCoordinator.awaitPersistence()
        publishSemanticState(elapsedSeconds)
    }

    /** 用户放弃：不产生记录。 */
    private fun discard() {
        machine.cancel()
        ActiveSessionCoordinator.cancel()
        timerJob?.cancel()
        publishSemanticState()
        _liveState.value = Idle
        _elapsedSecondsForUi.value = 0L
        _remainingSecondsForUi.value = 0L
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        liveActivity.cancelOngoing()
        stopSelf()
    }

    /**
     * 唯一的状态发布出口：先在语义转换时刻重建 [liveState]，再让控制器刷新展示。
     * 控制器**只在语义转换时**被调用，运行期间不会产生任何通知刷新。
     */
    private fun publishSemanticState(elapsedSeconds: Long? = null) {
        val elapsed = elapsedSeconds ?: machine.elapsedSeconds()
        val state = focusLiveStateOf(machine.snapshot, sessionId, subject, elapsed)
        _liveState.value = state
        liveActivity.updateOngoing(state, kind)
    }

    private fun finishAndRelease(postCompletion: Boolean) {
        timerJob?.cancel()
        // 完成瞬间立刻释放 WakeLock：旧实现只在 onDestroy 释放，
        // 如果完成事件没有及时被 UI 接管，唤醒锁会一直持有到 12 小时超时。
        releaseWakeLock()

        // 先撤掉 ongoing 前台通知，再按需发完成通知——顺序反了会出现
        // 「完成通知已到、常驻胶囊还挂着」的假状态。
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (postCompletion) {
            liveActivity.finish(_liveState.value, kind)
            triggerAlertSoundAndVibration()
        } else {
            liveActivity.cancelOngoing()
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) {
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
        }
    }

    private fun triggerAlertSoundAndVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 800), -1)
            }

            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            // 闹铃属于"尽力而为"的辅助能力：设备静音 / 振动权限缺失都不应影响计时结果。
            android.util.Log.w("FocusTimer", "提醒动作失败：${e.message}")
        }
    }

    /**
     * `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` 是编译期内联常量（API 34），
     * 调用点已有 `Build.VERSION.SDK_INT >= Q` 守卫，故按需抑制 `InlinedApi`。
     */
    @SuppressLint("InlinedApi")
    private fun promoteToForeground() {
        val notification = liveActivity.buildOngoing(_liveState.value, kind) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
