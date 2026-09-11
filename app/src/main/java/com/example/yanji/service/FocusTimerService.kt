package com.example.yanji.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.yanji.MainActivity
import com.example.yanji.R
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.data.timer.SystemMonotonicClock
import com.example.yanji.data.timer.TimerMachine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerServiceMode {
    FOCUS, EXAM
}

/**
 * Service 对外暴露的 UI 状态。
 *
 * 注意：`elapsedSeconds` / `remainingSeconds` 都是从 [TimerMachine] 的单调时钟差值推出来的
 * 展示值，**不是**计时事实本身。旧实现把"loop 被调度了多少次"当作时间事实，
 * 协程调度延迟与系统省电都会累积误差。
 */
data class TimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val mode: TimerServiceMode = TimerServiceMode.FOCUS,
    val sessionId: String = "",
    val subjectName: String = "",
    val elapsedSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val targetDurationSeconds: Long = 0,
    val isFinished: Boolean = false
)

/**
 * 前台计时服务。
 *
 * 职责边界（刻意收窄）：
 *  - **只负责时间事实**：用单调时钟推算真实 elapsed / remaining，刷新通知与 UI 状态；
 *  - 结束 / 放弃时把结果交给 [ActiveSessionCoordinator]（业务层）落库，
 *    不由任何 Compose 页面决定"这次学习算不算数"；
 *  - 完成瞬间立即释放 WakeLock，绝不把唤醒锁拖到服务生命周期结束。
 *
 * 「结束」语义统一定义为：
 *  - 倒计时归零 → 正常完成（COMPLETED，记录计划时长）
 *  - 通知栏「结束」/ 页面「结束」 → 主动结束（COMPLETED，记录实际时长）
 *  - 页面「放弃 / 退出」 → 取消（CANCELLED，不产生记录）
 */
class FocusTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "yanji_timer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_FOCUS = "com.example.yanji.ACTION_START_FOCUS"
        const val ACTION_START_EXAM = "com.example.yanji.ACTION_START_EXAM"
        const val ACTION_PAUSE = "com.example.yanji.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.yanji.ACTION_RESUME"
        const val ACTION_COMPLETE = "com.example.yanji.ACTION_COMPLETE"
        const val ACTION_DISCARD = "com.example.yanji.ACTION_DISCARD"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"

        private const val UI_TICK_MS = 1000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

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

        private fun dispatch(context: Context, action: String, sessionId: String, subject: String, seconds: Long) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                this.action = action
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_DURATION_SECONDS, seconds)
            }
            if (action == ACTION_START_FOCUS || action == ACTION_START_EXAM) {
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
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yanji:TimerWakeLock")
        // 保证落库出口可用：进程可能只为了这个 Service 而被创建。
        runCatching { YanjiRepository.init(applicationContext) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "日常专注"
                val target = intent.getLongExtra(EXTRA_DURATION_SECONDS, 0L)
                startTimer(TimerServiceMode.FOCUS, subject, target)
            }
            ACTION_START_EXAM -> {
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "全真模考"
                val target = intent.getLongExtra(EXTRA_DURATION_SECONDS, 10800L)
                startTimer(TimerServiceMode.EXAM, subject, target)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_COMPLETE -> completeByUser()
            ACTION_DISCARD -> discard()
        }
        // 不返回 START_STICKY：进程被回收后系统用 null intent 重启本服务只会得到一个
        // "前台通知还在、计时已归零"的假状态。活动会话的恢复由业务层负责（见 ActiveSessionCoordinator）。
        return START_NOT_STICKY
    }

    private fun startTimer(mode: TimerServiceMode, subject: String, targetSeconds: Long) {
        timerJob?.cancel()
        acquireWakeLock()

        machine.start(System.currentTimeMillis(), targetSeconds)

        val sessionId = ActiveSessionCoordinator.activeSessionId().orEmpty()
        _timerState.value = TimerState(
            isRunning = true,
            isPaused = false,
            mode = mode,
            sessionId = sessionId,
            subjectName = subject,
            elapsedSeconds = 0,
            remainingSeconds = targetSeconds,
            targetDurationSeconds = targetSeconds,
            isFinished = false
        )

        promoteToForeground()

        // 每秒只做一件事：根据单调时钟刷新展示值。计时精度不依赖这里的调度频率。
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(UI_TICK_MS)
                val current = _timerState.value
                if (!current.isRunning) break
                if (current.isPaused) continue

                val elapsed = machine.elapsedSeconds()
                val remaining = machine.remainingSeconds()

                if (machine.hasReachedTarget()) {
                    _timerState.value = current.copy(
                        isRunning = false,
                        elapsedSeconds = machine.snapshot.targetDurationSeconds,
                        remainingSeconds = 0,
                        isFinished = true
                    )
                    onCountdownFinished()
                    break
                }

                _timerState.value = current.copy(
                    elapsedSeconds = elapsed,
                    remainingSeconds = remaining
                )
                updateNotification()
            }
        }
    }

    private fun pause() {
        val current = _timerState.value
        if (current.isRunning && !current.isPaused) {
            machine.pause()
            _timerState.value = current.copy(isPaused = true, elapsedSeconds = machine.elapsedSeconds())
            ActiveSessionCoordinator.update { it.copy(paused = true, accumulatedActiveMs = machine.elapsedMs()) }
            updateNotification()
        }
    }

    private fun resume() {
        val current = _timerState.value
        if (current.isRunning && current.isPaused) {
            machine.resume()
            _timerState.value = current.copy(isPaused = false)
            ActiveSessionCoordinator.update { it.copy(paused = false) }
            updateNotification()
        }
    }

    /** 倒计时自然归零：完成并落库。 */
    private fun onCountdownFinished() {
        val seconds = machine.snapshot.targetDurationSeconds
        ActiveSessionCoordinator.complete(
            actualSeconds = seconds,
            pausedSeconds = 0L,
            pauseCount = machine.snapshot.pauseCount,
            endEpochMs = System.currentTimeMillis()
        )
        finishAndRelease(showFinishedNotification = true)
    }

    /** 用户主动结束：按实际时长完成并落库。 */
    private fun completeByUser() {
        val elapsed = machine.elapsedSeconds()
        machine.finish()
        ActiveSessionCoordinator.complete(
            actualSeconds = elapsed,
            pausedSeconds = machine.pausedMs(System.currentTimeMillis()) / 1000L,
            pauseCount = machine.snapshot.pauseCount,
            endEpochMs = System.currentTimeMillis()
        )
        finishAndRelease(showFinishedNotification = machine.snapshot.isCountdown)
    }

    /** 用户放弃：不产生记录。 */
    private fun discard() {
        machine.cancel()
        ActiveSessionCoordinator.cancel()
        timerJob?.cancel()
        _timerState.value = TimerState()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishAndRelease(showFinishedNotification: Boolean) {
        timerJob?.cancel()
        // 关键修复：完成瞬间立刻释放 WakeLock。
        // 旧实现只在 stop()/onDestroy() 释放，如果完成事件没有及时被 UI 接管，
        // 唤醒锁会一直持有到 12 小时超时或服务销毁。
        releaseWakeLock()

        if (showFinishedNotification) {
            buildFinishedNotification()?.let { notification ->
                stopForeground(STOP_FOREGROUND_DETACH)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, notification)
                triggerAlertSoundAndVibration()
            } ?: run { stopForeground(STOP_FOREGROUND_REMOVE) }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
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

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildFinishedNotification(): Notification? {
        val current = _timerState.value
        val title = if (current.mode == TimerServiceMode.EXAM) "全真模考已交卷时间到！" else "专注时间已完成！"
        val message = "${current.subjectName} 计时已结束，点击返回研迹查看并录入成绩复盘。"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(): Notification {
        val current = _timerState.value
        val title = if (current.mode == TimerServiceMode.EXAM) {
            "研迹 · ${current.subjectName} 模考中"
        } else {
            "研迹 · ${current.subjectName} 专注中"
        }

        val formattedTime = if (current.targetDurationSeconds > 0) {
            formatDuration(current.remainingSeconds, "倒计时")
        } else {
            formatDuration(current.elapsedSeconds, "已专注")
        }

        val statusText = if (current.isPaused) "已暂停" else "保持专注，心无旁骛"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText("$formattedTime | $statusText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (current.isRunning) {
            val toggleIntent = if (current.isPaused) {
                serviceAction(1, ACTION_RESUME)
            } else {
                serviceAction(2, ACTION_PAUSE)
            }
            builder.addAction(0, if (current.isPaused) "继续" else "暂停", toggleIntent)
            builder.addAction(0, "结束", serviceAction(3, ACTION_COMPLETE))
        }

        return builder.build()
    }

    private fun serviceAction(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, FocusTimerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun formatDuration(totalSeconds: Long, suffix: String): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d %s", h, m, s, suffix)
        else String.format("%02d:%02d %s", m, s, suffix)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "研迹 · 专注与模考前台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在锁屏和后台持续显示专注或模考计时进度，防止系统被杀"
                setShowBadge(false)
                // VISIBILITY_PRIVATE：锁屏上只显示来自研迹的通知，不展开科目与计时内容。
                // 学习内容属于个人隐私，公共场合的锁屏预览不应该暴露在学什么。
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
