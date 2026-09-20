package com.example.yanji.liveactivity

import com.example.yanji.data.timer.ActiveFocusState
import com.example.yanji.data.timer.Finished
import com.example.yanji.data.timer.FocusLiveState
import com.example.yanji.data.timer.formatFocusClock

/** 通知栏可执行的动作。与 Service 的 ACTION_* 字符串解耦，便于纯 JVM 单测。 */
enum class FocusNotificationAction { PAUSE, RESUME, COMPLETE }

/** 展示语境的差异（专注 / 模考）。避免把「专注完成」硬套到模考上。 */
enum class FocusKind {
    FOCUS,
    EXAM;

    /** 完成通知标题，例如「高等数学 专注完成」。 */
    fun completionTitle(subject: String): String = when (this) {
        FOCUS -> "$subject 专注完成"
        EXAM -> "$subject 已交卷"
    }

    /** 运行中 contentText。计时数字由系统 Chronometer 渲染，这里只给语义后缀。 */
    fun runningLabel(countdown: Boolean): String = when (this) {
        FOCUS -> if (countdown) "剩余时间" else "已专注"
        EXAM -> if (countdown) "剩余时间" else "已用时"
    }

    /** 完成通知正文，例如「本次专注 45 分钟」。 */
    fun completionBody(state: FocusLiveState): String {
        val noun = if (this == FOCUS) "本次专注" else "本次作答"
        return if (state.isCountdown && state.elapsedSeconds >= state.targetSeconds) {
            "$noun ${state.targetSeconds / 60} 分钟"
        } else {
            "$noun ${formatFocusClock(state.elapsedSeconds)}"
        }
    }
}

/**
 * 与平台无关的通知内容描述。
 *
 * 把「该显示什么」和「怎么 setXxx」分开：
 *  - 本类型与 [FocusNotificationSpecs] 是纯 Kotlin，可在 JVM 单测里穷举所有状态；
 *  - [StandardNotificationController] 负责把它翻译成 `NotificationCompat.Builder` 调用。
 *
 * 普通通知、Android 16 Live Update、ColorOS 流体云共用同一份文案与动作语义，
 * 不允许任何一方私自改文案（规范 §33）。
 */
data class FocusNotificationSpec(
    val notificationId: Int,
    val title: String,
    val contentText: String,
    val subText: String?,
    val ongoing: Boolean,
    val autoCancel: Boolean,
    val usesChronometer: Boolean,
    val chronometerCountDown: Boolean,
    /** 传给 `setWhen` 的墙钟毫秒；[showWhen] 为 false 时忽略。 */
    val referenceWallClockMs: Long,
    val showWhen: Boolean,
    /** 0..100，仅在 [showProgress] 为 true 时使用。 */
    val progressPercent: Int,
    val showProgress: Boolean,
    /** 是否请求提升为 Android 16 Live Update（promoted ongoing）。 */
    val requestPromoted: Boolean,
    /**
     * 状态栏小胶囊文本。
     *
     * null = 交给系统 Chronometer 渲染实时数字（运行中，数字必须真实；
     * 用静态字符串冒充会显示一个不走的假时间）。
     * 非 null = 暂停等静态场景，此时数字本身就是冻结的，可以安全下发。
     */
    val shortCriticalText: String?,
    val primaryAction: FocusNotificationAction?,
    val secondaryAction: FocusNotificationAction?,
    /**
     * 覆盖主/次动作按钮文案。null 时用 [FocusNotificationAction] 的默认标签
     * （暂停/继续/结束）。失败通知需要把 COMPLETE 显示成「重试」而不改动作语义。
     */
    val primaryActionLabel: String? = null,
    val secondaryActionLabel: String? = null
)

/**
 * 状态 → 通知描述。纯函数。
 *
 * 这里不存在「每秒重新构建」的路径：同一语义状态下本函数返回恒等结果，
 * 因此调用方只在语义转换时调用它（规范 §19）。
 */
object FocusNotificationSpecs {

    /** 常驻计时通知，与 `startForeground` 使用同一个 id。 */
    const val ONGOING_NOTIFICATION_ID = 1001

    /** 完成后的普通通知。 */
    const val COMPLETION_NOTIFICATION_ID = 1002

    /**
     * 落库失败后的兜底通知。复用完成后通知的 id 段，确保不会与完成通知同时存在两条矛盾胶囊。
     */
    const val COMPLETION_FAILURE_NOTIFICATION_ID = 1003

    const val CHANNEL_ID = "yanji_timer_channel"

    /**
     * 构建常驻通知描述。
     *
     * @param nowWallClockMs 当前墙钟毫秒，仅用于把单调时钟推导出的剩余/已用时长
     *        投影成 `setWhen` 需要的绝对时间点。
     * @return null 表示当前没有需要常驻展示的活动会话。
     */
    fun ongoing(
        state: FocusLiveState,
        nowWallClockMs: Long,
        kind: FocusKind = FocusKind.FOCUS
    ): FocusNotificationSpec? {
        if (state !is com.example.yanji.data.timer.ActiveFocusState) return null

        val countdown = state.isCountdown
        val progressPercent = if (countdown) {
            (state.elapsedSeconds * 100 / state.targetSeconds).coerceIn(0, 100).toInt()
        } else {
            0
        }

        return if (state.isPaused) {
            // 暂停：必须冻结数字。Chronometer 是系统驱动的，暂停时绝不能继续走，
            // 否则用户看到的计时会比真实有效时长多出整个暂停区间。
            val frozen = if (countdown) state.remainingSeconds else state.elapsedSeconds
            FocusNotificationSpec(
                notificationId = ONGOING_NOTIFICATION_ID,
                title = state.subject,
                contentText = "${formatFocusClock(frozen)} · 已暂停",
                subText = "已暂停",
                ongoing = true,
                autoCancel = false,
                usesChronometer = false,
                chronometerCountDown = false,
                referenceWallClockMs = nowWallClockMs,
                showWhen = false,
                progressPercent = progressPercent,
                showProgress = countdown,
                requestPromoted = true,
                shortCriticalText = formatFocusClock(frozen),
                primaryAction = FocusNotificationAction.RESUME,
                secondaryAction = FocusNotificationAction.COMPLETE
            )
        } else {
            // 运行：数字交给系统 Chronometer，Service 不再每秒 notify。
            val referenceWallClockMs = if (countdown) {
                nowWallClockMs + state.remainingSeconds * 1000L
            } else {
                nowWallClockMs - state.elapsedSeconds * 1000L
            }
            FocusNotificationSpec(
                notificationId = ONGOING_NOTIFICATION_ID,
                title = state.subject,
                contentText = kind.runningLabel(countdown) + " · 保持专注",
                subText = null,
                ongoing = true,
                autoCancel = false,
                usesChronometer = true,
                chronometerCountDown = countdown,
                referenceWallClockMs = referenceWallClockMs,
                showWhen = true,
                progressPercent = progressPercent,
                showProgress = countdown,
                requestPromoted = true,
                shortCriticalText = null,
                primaryAction = FocusNotificationAction.PAUSE,
                secondaryAction = FocusNotificationAction.COMPLETE
            )
        }
    }

    /** 构建完成后的一次性通知描述。 */
    fun completion(
        state: FocusLiveState,
        kind: FocusKind = FocusKind.FOCUS
    ): FocusNotificationSpec? {
        if (state !is Finished) return null
        return FocusNotificationSpec(
            notificationId = COMPLETION_NOTIFICATION_ID,
            title = kind.completionTitle(state.subject),
            contentText = kind.completionBody(state),
            subText = null,
            ongoing = false,
            autoCancel = true,
            usesChronometer = false,
            chronometerCountDown = false,
            referenceWallClockMs = state.endEpochMs,
            showWhen = false,
            progressPercent = 0,
            showProgress = false,
            requestPromoted = false,
            shortCriticalText = null,
            primaryAction = null,
            secondaryAction = null
        )
    }

    /**
     * 构建落库失败后的一次性通知描述。
     *
     * 用固定的 [COMPLETION_FAILURE_NOTIFICATION_ID] 复用常驻通知 id，避免残留两条互相矛盾的胶囊；
     * 主操作为 [FocusNotificationAction.COMPLETE]（语义即「重试保存」），对应 action 字符串
     * 仍是 `ACTION_COMPLETE`——失败时 coordinator 保留 ACTIVE，重入会再次尝试落库。
     */
    fun completionFailure(subject: String, elapsedSeconds: Long, nowWallClockMs: Long): FocusNotificationSpec =
        FocusNotificationSpec(
            notificationId = COMPLETION_FAILURE_NOTIFICATION_ID,
            title = "保存失败",
            contentText = "${subject.ifBlank { "本次专注" }} · ${formatFocusClock(elapsedSeconds)} 未保存，点「重试」再试一次",
            subText = null,
            ongoing = false,
            autoCancel = true,
            usesChronometer = false,
            chronometerCountDown = false,
            referenceWallClockMs = nowWallClockMs,
            showWhen = false,
            progressPercent = 0,
            showProgress = false,
            requestPromoted = false,
            shortCriticalText = null,
            primaryAction = FocusNotificationAction.COMPLETE,
            secondaryAction = null,
            primaryActionLabel = "重试"
        )
}
