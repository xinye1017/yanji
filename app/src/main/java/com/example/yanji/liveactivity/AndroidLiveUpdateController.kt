package com.example.yanji.liveactivity

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Android 16 Live Update（Promoted Ongoing Notification）适配层。
 *
 * 全部基于官方 API，**不使用自定义 RemoteViews**（规范 §21/§39）：
 *  - `NotificationCompat.Builder.setRequestPromotedOngoing(true)`
 *    —— 实测实现只是往 extras 写 `android.requestPromotedOngoing`，不调用 framework 方法，
 *    因此在任何 API 级别调用都不会崩，老系统直接忽略该 extra。
 *  - `NotificationCompat.ProgressStyle` —— 倒计时的进度模板（官方允许被提升的四种模板之一）。
 *  - `NotificationCompat.Builder.setShortCriticalText` —— 状态栏小胶囊文本，
 *    `SDK_INT < 36` 时退化为写 extra（androidx 已守卫）。
 *  - `NotificationCompat.hasPromotableCharacteristics` —— 下发前自检，条件不满足就降级。
 *
 * 提升**不是**应用能单方面决定的：系统会综合权限、渠道重要性、通知结构决定是否真的提升。
 * 所以这里只做「申请 + 自检 + 记录」，最终是否出现在状态栏胶囊 / 锁屏由 SystemUI 决定。
 */
class AndroidLiveUpdateController(private val context: Context) {

    /** 一次下发的结果，用于日志与真机验证时判断到底走了哪条路径。 */
    data class Outcome(
        val notification: Notification,
        val promotionRequested: Boolean,
        val hasPromotableCharacteristics: Boolean
    )

    /**
     * 在已构建好的 builder 上追加 Live Update 相关设置并产出 notification。
     *
     * @return [Outcome.promotionRequested] 为 false 时表示当前设备不具备 promoted 能力，
     *         调用方拿到的是一份**完全可用的普通常驻通知**（降级路径）。
     */
    @Suppress("UnusedReturnValue")
    fun decorate(
        builder: NotificationCompat.Builder,
        spec: FocusNotificationSpec,
        capability: LiveActivityCapability
    ): Outcome {
        if (!capability.canPostPromotedOngoing || !spec.requestPromoted) {
            val plain = builder.build()
            return Outcome(plain, promotionRequested = false, hasPromotableCharacteristics = false)
        }

        builder.setRequestPromotedOngoing(true)

        if (spec.showProgress) {
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgress(spec.progressPercent.coerceIn(0, 100))
            )
        }

        spec.shortCriticalText?.let { builder.setShortCriticalText(it) }

        val notification = builder.build()
        val promotable = runCatching {
            NotificationCompat.hasPromotableCharacteristics(notification)
        }.getOrDefault(false)

        if (!promotable) {
            // 自检不通过时系统不会提升，但我们仍然下发这份通知——它是一份合法的常驻通知。
            Log.w(
                TAG,
                "promoted ongoing 自检未通过，按普通常驻通知降级下发：tier=${capability.tier}"
            )
        }
        return Outcome(notification, promotionRequested = true, hasPromotableCharacteristics = promotable)
    }

    companion object {
        private const val TAG = "YanjiLiveUpdate"

        /**
         * 供诊断：本应用当前能否发 promoted 通知。异常一律视为不可用。
         * （`androidx` 内部已按 SDK_INT>=36 守卫，这里再兜一层 ROM 差异。）
         */
        fun canPostPromoted(context: Context): Boolean = runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).canPostPromotedNotifications()
        }.getOrDefault(false)
    }
}
