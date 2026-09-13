package com.example.yanji.liveactivity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.yanji.MainActivity
import com.example.yanji.R
import com.example.yanji.service.FocusTimerService

/**
 * 把与平台无关的 [FocusNotificationSpec] 翻译成 `NotificationCompat.Builder`。
 *
 * 这一层只做「平台调用」，不含任何业务判断——所有文案与动作语义都来自 spec，
 * 因此普通通知 / Live Update / 流体云三方看到的内容天然一致。
 *
 * 关键点（规范 §15 / §19）：
 *  - 时间数字由**系统 Chronometer** 渲染，Service 不再每秒 `notify()`；
 *  - small icon 使用单色 `ic_stat_focus`，不拿 Launcher 图标凑数，也不用 LargeIcon；
 *  - 锁屏可见性沿用项目既有的隐私决定（渠道 VISIBILITY_PRIVATE），
 *    需要把计时内容显示到锁屏时只改 [LOCK_SCREEN_VISIBILITY] 一处。
 */
class StandardNotificationController(private val context: Context) {

    companion object {
        /**
         * 渠道级锁屏可见性。默认沿用项目的隐私取向：锁屏不展开学习内容。
         * 若希望锁屏直接显示倒计时数字，把它改成 [Notification.VISIBILITY_PUBLIC] 即可。
         */
        const val CHANNEL_LOCK_SCREEN_VISIBILITY = Notification.VISIBILITY_PRIVATE

        /**
         * 单条通知级可见性。取值与渠道常量相同，但 `setVisibility` 只接受
         * `NotificationCompat.VISIBILITY_*` 常量（lint WrongConstant 会拦下混用）。
         */
        const val NOTIFICATION_VISIBILITY = NotificationCompat.VISIBILITY_PRIVATE

        private const val REQUEST_CODE_TOGGLE = 1
        private const val REQUEST_CODE_COMPLETE = 2
        private const val REQUEST_CODE_CONTENT = 3
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            FocusNotificationSpecs.CHANNEL_ID,
            "研迹 · 专注与模考前台服务",
            // 不能是 IMPORTANCE_MIN：promoted Live Update 明确要求高于最小值，且 MIN 会折叠展示。
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在锁屏和后台持续显示专注或模考计时进度，防止系统被杀"
            setShowBadge(false)
            lockscreenVisibility = CHANNEL_LOCK_SCREEN_VISIBILITY
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 暴露 builder 而不是成品 notification：Live Update 需要在同一 builder 上追加样式。 */
    fun builderFor(spec: FocusNotificationSpec): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, FocusNotificationSpecs.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(spec.title)
            .setContentText(spec.contentText)
            .setOngoing(spec.ongoing)
            .setAutoCancel(spec.autoCancel)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NOTIFICATION_VISIBILITY)

        spec.subText?.let { builder.setSubText(it) }

        if (spec.usesChronometer) {
            // 计时数字完全交给 SystemUI：不再需要每次 tick 都走一遍 Binder。
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(spec.chronometerCountDown)
            builder.setWhen(spec.referenceWallClockMs)
            builder.setShowWhen(spec.showWhen)
        } else {
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        }

        if (spec.showProgress) {
            builder.setProgress(100, spec.progressPercent, false)
        }

        spec.primaryAction?.let { action ->
            builder.addAction(0, labelOf(action), serviceAction(REQUEST_CODE_TOGGLE, action))
        }
        spec.secondaryAction?.let { action ->
            builder.addAction(0, labelOf(action), serviceAction(REQUEST_CODE_COMPLETE, action))
        }

        return builder
    }

    fun build(spec: FocusNotificationSpec): Notification = builderFor(spec).build()

    private fun labelOf(action: FocusNotificationAction): String = when (action) {
        FocusNotificationAction.PAUSE -> "暂停"
        FocusNotificationAction.RESUME -> "继续"
        FocusNotificationAction.COMPLETE -> "结束"
    }

    private fun serviceAction(requestCode: Int, action: FocusNotificationAction): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, FocusTimerService::class.java).apply {
                this.action = FocusTimerActions.actionOf(action)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_CONTENT,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
