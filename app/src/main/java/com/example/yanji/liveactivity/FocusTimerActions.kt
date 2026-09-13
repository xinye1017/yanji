package com.example.yanji.liveactivity

/**
 * 通知栏 Action 的**唯一**契约。
 *
 * App UI、系统通知、Android Live Update、ColorOS 流体云最终都走这几个字符串进入同一个
 * `FocusTimerService`，不允许出现 `pauseFromNotification()` / `pauseFromApp()` 两套业务路径
 * （规范 §33）。常量放在 liveactivity 层而不是 Service 里，是为了让控制器无需反向依赖 Service。
 */
object FocusTimerActions {
    const val ACTION_START_FOCUS = "com.example.yanji.ACTION_START_FOCUS"
    const val ACTION_START_EXAM = "com.example.yanji.ACTION_START_EXAM"
    const val ACTION_PAUSE = "com.example.yanji.ACTION_PAUSE"
    const val ACTION_RESUME = "com.example.yanji.ACTION_RESUME"
    const val ACTION_COMPLETE = "com.example.yanji.ACTION_COMPLETE"
    const val ACTION_DISCARD = "com.example.yanji.ACTION_DISCARD"

    fun actionOf(action: FocusNotificationAction): String = when (action) {
        FocusNotificationAction.PAUSE -> ACTION_PAUSE
        FocusNotificationAction.RESUME -> ACTION_RESUME
        FocusNotificationAction.COMPLETE -> ACTION_COMPLETE
    }
}
