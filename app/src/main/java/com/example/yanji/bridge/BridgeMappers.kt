package com.example.yanji.bridge

import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyTask
import com.example.yanji.data.UserSettings
import com.example.yanji.data.timer.ActiveSession
import com.example.yanji.theme.YanjiThemeMode

/**
 * 桥接层的**纯映射**函数集合。
 *
 * 为什么必须抽成顶层 `internal`：`WritableMap` 只能由 `Arguments.createMap()` 产生，
 * 而后者依赖 React Native 的 Android runtime，在 JVM 单元测试类路径上不可用。
 * 把「领域模型 → 普通 Kotlin 数据类」这一步单独拿出来，模块只剩
 * `WritableMap` 装配，契约语义就可以在 JVM 单测里逐字段断言。
 *
 * 契约权威：`test-e2e/contracts/bridge-schema.js` + `src/bridge/index.ts` 的类型声明。
 * 三边（Kotlin / TS / E2E 契约）的字段名与取值范围必须逐字一致。
 */
object BridgeMappers {

    // ------------------------------------------------------------------ 计时

    /**
     * 会话阶段词汇表：**只有这三个值**。
     *
     * 与 TS `TimerPhase` 逐字对齐：
     *  - `FOCUS`：会话存在（运行中**或**暂停中——暂停由 [BridgeMappers.SessionFields.isPaused]
     *    单独承载，不占用 phase 的取值空间）；
     *  - `BREAK`：为将来的「休息阶段」预留，当前**永不发射**；
     *  - `IDLE`：没有会话（`getActiveSession()` 返回 null），由调用方直接 resolve(null)，
     *    因此这里不产生 IDLE 的载荷。
     *
     * 绝不把 `TimerPhase` 的枚举名（RUNNING / PAUSED / COMPLETED / CANCELLED）
     * 或中文模式名（`正向计时`）送到桥对面。
     */
    const val PHASE_FOCUS = "FOCUS"
    const val PHASE_BREAK = "BREAK"
    const val PHASE_IDLE = "IDLE"

    /** 计时模式词汇表：**只有这两个值**。 */
    const val MODE_COUNTDOWN = "COUNTDOWN"
    const val MODE_STOPWATCH = "STOPWATCH"

    /**
     * `mode` 的唯一判据：**目标时长是否为正**。
     *
     * `targetDurationSeconds > 0` → `COUNTDOWN`；否则（正向计时，不限时长）→ `STOPWATCH`。
     * 不使用 [ActiveSession.mode] 那个中文展示名——它是给 Compose UI 看的，不是桥接契约。
     */
    fun timerModeOf(targetDurationSeconds: Long): String =
        if (targetDurationSeconds > 0L) MODE_COUNTDOWN else MODE_STOPWATCH

    /**
     * 活动会话的桥接载荷（对应 TS `ActiveSessionState`）。
     *
     * 纯数据：字段名与取值范围就是契约本身，可在 JVM 单测里直接断言。
     */
    data class SessionFields(
        val sessionId: String,
        val subjectId: String,
        val subjectName: String,
        val mode: String,
        val taskId: String?,
        val startTime: Double,
        val elapsedSeconds: Double,
        val remainingSeconds: Double,
        val phase: String,
        val isPaused: Boolean,
        val isCountdown: Boolean
    )

    /**
     * 会话存在 → `FOCUS`（暂停中也是 FOCUS，见 [PHASE_FOCUS]）。
     * 没有会话时调用方应直接 resolve(null)，不会走到这里。
     */
    fun sessionPhaseOf(hasSession: Boolean): String =
        if (hasSession) PHASE_FOCUS else PHASE_IDLE

    /** [com.example.yanji.bridge.YanjiTimerModule.activeSessionMap] 的纯内核。 */
    fun sessionFields(
        session: ActiveSession,
        elapsedSeconds: Long,
        remainingSeconds: Long
    ): SessionFields = SessionFields(
        sessionId = session.sessionId,
        subjectId = session.subjectId,
        subjectName = session.subjectName,
        mode = timerModeOf(session.targetDurationSeconds),
        taskId = session.taskId?.takeIf { it.isNotBlank() },
        startTime = session.startedAtEpochMs.toDouble(),
        elapsedSeconds = elapsedSeconds.toDouble(),
        remainingSeconds = remainingSeconds.toDouble(),
        phase = sessionPhaseOf(hasSession = true),
        isPaused = session.paused,
        isCountdown = session.targetDurationSeconds > 0L
    )

    /**
     * `onTimerTick` 的 `phase` 与 [sessionPhaseOf] **同一套词汇表**。
     * 暂停只在 tick 载荷里改变 `isPaused`，phase 仍是 FOCUS。
     */
    fun tickPhase(hasSession: Boolean): String = sessionPhaseOf(hasSession)

    // ------------------------------------------------------------------ 任务

    /**
     * 学习计划载荷（对应 TS `StudyTask`）。
     *
     * `actualMinutes` 来自 [actualMinutesOf]：真实累计秒数除以 60 向下取整。
     * 没有关联时段时为 0 —— 这是**真实值**（确实还没投入），不是占位假数据。
     */
    data class TaskFields(
        val id: String,
        val date: String,
        val subjectId: String,
        val subjectName: String,
        val title: String,
        val plannedMinutes: Int,
        val actualMinutes: Int,
        val completed: Boolean,
        val createdAt: Double
    )

    /**
     * 秒 → 分钟。恒为非负（秒数本身不可能是负的）。
     *
     * 数据源是 [com.example.yanji.data.YanjiRepository.observeTaskActualSeconds]
     * （`status='COMPLETED' AND taskId IS NOT NULL` 的 GROUP BY 聚合），
     * 未完成的时段与不挂计划的时段都不计入。
     */
    fun actualMinutesOf(seconds: Long): Int =
        (if (seconds > 0L) seconds else 0L).toInt() / 60

    fun taskFields(
        task: StudyTask,
        actualSeconds: Long?
    ): TaskFields = TaskFields(
        id = task.id,
        date = task.date,
        subjectId = task.subjectId,
        subjectName = task.subjectName,
        title = task.title,
        plannedMinutes = task.plannedMinutes,
        actualMinutes = actualMinutesOf(actualSeconds ?: 0L),
        completed = task.isCompleted,
        createdAt = task.createdAt.toDouble()
    )

    // ------------------------------------------------------------------ 记录

    /**
     * 快速记录载荷（对应 TS `NoteEntry`）。
     *
     * `sessionId` 是 `String?`：**未绑定时必须是 null，绝不能是空串**。
     * 契约 `isValidNoteEntry` 对每一条 NoteEntry 载荷生效（保存回调与全部读取路径）。
     */
    data class NoteFields(
        val id: String,
        val date: String,
        val timestamp: Double,
        val content: String,
        val isFavorite: Boolean,
        val sessionId: String?
    )

    fun noteFields(note: NoteEntry): NoteFields = NoteFields(
        id = note.id,
        date = note.date,
        timestamp = note.createdAt.toDouble(),
        content = note.content,
        isFavorite = note.isFavorite,
        sessionId = note.sessionId?.takeIf { it.isNotBlank() }
    )

    // ------------------------------------------------------------------ 设置

    /**
     * 用户设置载荷（对应 TS `UserSettings`）。
     *
     * **刻意不含 `focusDurationMinutes` / `breakDurationMinutes`**：
     * 领域模型 [UserSettings] 没有这两个字段，任何取值都是编造的（AGENTS.md §三.3）。
     * TS 类型、`isValidUserSettings`、mock 默认值与 SettingsScreen 已同步移除。
     */
    data class SettingsFields(
        val examDate: String,
        val targetSchool: String,
        val targetMajor: String,
        val themePreference: String
    )

    fun settingsFields(settings: UserSettings): SettingsFields = SettingsFields(
        examDate = settings.targetExamDate,
        targetSchool = settings.targetSchool,
        targetMajor = settings.targetMajor,
        themePreference = YanjiThemeMode.fromStorage(settings.themeMode).name
    )

    // ------------------------------------------------------------------ 复盘统计

    /**
     * 滚动窗口的本地日历日期键：从 [startDate] 起共 [windowDays] 天（含首尾）。
     *
     * 由本地日历逐日推进，**绝不用 24 小时倍数回减**（跨夏令时会漂移）。
     * 键按「早 → 晚」排序，且永远不含未来日期——窗口终点就是今天。
     */
    fun rollingWindowDates(startDate: java.time.LocalDate, windowDays: Int): List<String> {
        val safeWindowDays = if (windowDays > 0) windowDays else 1
        return (0 until safeWindowDays).map { offset ->
            startDate.plusDays(offset.toLong()).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    /**
     * 复盘统计载荷（对应 TS `ReviewStats`）。
     *
     * 窗口语义（与 `mock-bridge.js` 的 `getReviewStats` 逐字对齐）：
     *  - `days` 被如实尊重：恰好 `days` 个键，从 `today-(days-1)` 到 today；
     *  - 键是本地日历 `yyyy-MM-dd`，**不含未来日期**（明天永远不会出现）；
     *  - 不做自然周对齐（不裁剪到周一）；
     *  - 窗口外的时段不计入任何桶；
     *  - `dailyAverageMinutes` 的分母是**窗口长度**（`windowDays`），不是有效天数；
     *  - `activeDays` 用 30 分钟阈值（`StudyStatisticsRepository.activeDayThreshold`，
     *    来自 `user_settings.validStudyThresholdMinutes`）。
     */
    data class ReviewStatsFields(
        val days: Int,
        val dailyFocusMinutes: Map<String, Int>,
        val subjectDistribution: Map<String, Int>,
        val totalFocusHours: Double,
        val dailyAverageMinutes: Int,
        val activeDays: Int
    )

    /**
     * 把「窗口内各时段」汇总成 [ReviewStatsFields]。
     *
     * @param windowDates 已排序（早 → 晚）的本地日期键，长度即窗口长度。
     * @param daySecondsByDate 每个窗口日期当天的专注 + 模考秒数。
     * @param subjectMinutesBySubject 展示名 → 分钟数（窗口内聚合）。
     * @param activeDayThresholdSeconds 有效学习天数阈值（默认 30 分钟）。
     */
    fun reviewStatsFields(
        windowDays: Int,
        daySecondsByDate: Map<String, Long>,
        subjectMinutesBySubject: Map<String, Int>,
        activeDayThresholdSeconds: Long
    ): ReviewStatsFields {
        val safeWindowDays = if (windowDays > 0) windowDays else 1
        val dailyFocusMinutes = LinkedHashMap<String, Int>()
        var totalMinutes = 0L
        var activeDays = 0
        daySecondsByDate.forEach { (date, seconds) ->
            val safeSeconds = if (seconds > 0L) seconds else 0L
            val minutes = (safeSeconds / 60L).toInt()
            dailyFocusMinutes[date] = minutes
            totalMinutes += minutes
            if (safeSeconds >= activeDayThresholdSeconds) activeDays++
        }
        return ReviewStatsFields(
            days = safeWindowDays,
            dailyFocusMinutes = dailyFocusMinutes,
            subjectDistribution = subjectMinutesBySubject,
            totalFocusHours = totalMinutes / 60.0,
            // 分母是窗口长度，与 mock-bridge.js 的 `totalMinutes / windowDates.length` 一致。
            dailyAverageMinutes = Math.round(totalMinutes.toDouble() / safeWindowDays).toInt(),
            activeDays = activeDays
        )
    }
}
