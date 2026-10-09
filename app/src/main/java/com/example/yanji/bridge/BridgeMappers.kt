package com.example.yanji.bridge

import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyTask
import com.example.yanji.data.SubjectCatalog
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
 *
 * 复盘概览（[reviewOverviewFields]）另有一层额外约束：它是**秒 → 分钟的唯一截断点**，
 * 调用方必须把窗口内的原始秒数整份交进来，绝不能自己先转成分钟。
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
     * 桥接契约模式名 → 原生 `FocusModes` 展示层模式名。
     *
     * `COUNTDOWN` 的目标秒数由调用方给出的分钟数决定，所以这里不拍默认档位，
     * 分钟数通过 [countdownModeForMinutes] 编进模式名——`FocusModes.targetSeconds`
     * 正是靠模式名里的「N 分钟」解析目标秒数。
     *
     * 返回 null 表示调用方传了契约外的值：桥接层**拒绝**而不是猜一个模式，
     * 猜错会让用户拿到一段长度完全不是自己要求的计时。
     */
    fun nativeModeOf(bridgeMode: String, plannedMinutes: Int = 0): String? =
        when (bridgeMode.uppercase()) {
            MODE_COUNTDOWN -> countdownModeForMinutes(plannedMinutes)
            MODE_STOPWATCH -> com.example.yanji.data.FocusModes.COUNT_UP
            else -> null
        }

    /**
     * 分钟数 → 原生模式名：正好落在四个标准档位上复用该档位名，其余走「N分钟专注」。
     */
    fun countdownModeForMinutes(minutes: Int): String =
        com.example.yanji.data.FocusModes.forPlannedMinutes(minutes.coerceAtLeast(1))

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

    // ------------------------------------------------------------------ 复盘概览

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

    /** 回顾页时间粒度的词汇表：**只有这四个值**（与 TS `ReviewScope` 逐字一致）。 */
    const val SCOPE_ROLLING_7 = "ROLLING_7"
    const val SCOPE_ROLLING_30 = "ROLLING_30"
    const val SCOPE_CALENDAR_WEEK = "CALENDAR_WEEK"
    const val SCOPE_CALENDAR_MONTH = "CALENDAR_MONTH"

    val REVIEW_SCOPES: List<String> = listOf(
        SCOPE_ROLLING_7,
        SCOPE_ROLLING_30,
        SCOPE_CALENDAR_WEEK,
        SCOPE_CALENDAR_MONTH
    )

    /**
     * 科目归一：**id 与 name 必须成对传入**。
     *
     * 只看 `subjectName` 无法把自定义 id 归到大类里（`SubjectCatalog` 的推断依赖 id
     * 查表 + name 兜底两条线索），因此调用方少传一个参数就会产出一个「第二事实来源」。
     * 与 [com.example.yanji.data.StudyStatisticsRepository] 的
     * `buildNamedDistribution` / `buildSubjectDistributionFromAggregates` /
     * [com.example.yanji.data.study.StudyStats] 同口径。
     *
     * @return `(bucketId, 展示名)`，展示名 = `SubjectCatalog.displayName(bucketId) ?: subjectName`。
     */
    fun subjectBucket(subjectId: String, subjectName: String): Pair<String, String> {
        val bucketId = SubjectCatalog.subcategoryBucketId(subjectId, subjectName)
        return bucketId to (SubjectCatalog.displayName(bucketId) ?: subjectName)
    }

    /**
     * 展示桶的**秒数**切片（`subjectId` / `subjectName` 已经是 [subjectBucket] 的产物）。
     *
     * 秒而不是分钟：分钟截断必须等到「窗口内 / 每日」求和完成之后再做一次，
     * 逐行先截断再相加会让每天 29 分 59 秒的 7 天窗口凭空少掉 38 分钟。
     */
    data class SubjectSecondsSlice(
        val subjectId: String,
        val subjectName: String,
        val seconds: Long
    )

    /** 概览窗口内的一天（对应 TS `ReviewPeriodDay`）。 */
    data class ReviewDayFields(
        val date: String,
        val dayLabel: String,
        val durationSeconds: Long,
        val isToday: Boolean,
        val isFuture: Boolean
    )

    /** 概览窗口内的一个展示桶（对应 TS `ReviewSubjectSlice`）。 */
    data class ReviewSubjectFields(
        val subjectId: String,
        val subjectName: String,
        val subjectColor: String,
        val minutes: Int,
        val share: Double,
        val dailyMinutes: Map<String, Int>
    )

    /** 复盘概览载荷（对应 TS `ReviewOverview`），不含任何 RN 类型。 */
    data class ReviewOverviewFields(
        val scope: String,
        val periodsBack: Int,
        val label: String,
        val windowDays: Int,
        val days: List<ReviewDayFields>,
        val totalSeconds: Long,
        val dailyAverageSeconds: Long,
        val examCount: Int,
        val subjectDistribution: List<ReviewSubjectFields>
    )

    /**
     * 展示桶的配色：直接桶回落到所属大类，再回落到兜底中性灰。
     * 与 `StudyStatisticsRepository.getSubjectColor` 同口径。
     */
    fun subjectColorHex(bucketId: String, subjectName: String): String {
        val directId = bucketId.removeSuffix(SubjectCatalog.UNCLASSIFIED_SUFFIX)
        return SubjectCatalog.find(directId)?.colorHex
            ?: SubjectCatalog.find(SubjectCatalog.inferCategoryId(bucketId, subjectName))?.colorHex
            ?: SubjectCatalog.DEFAULT_FALLBACK_COLOR
    }

    /**
     * 一天的横轴标签（口径由 scope 决定，不猜、不回退到另一种粒度）：
     *  - 滚动窗口 → `MM-DD`；
     *  - 自然周 → `周一`…`周日`；
     *  - 自然月 → `D日`。
     */
    fun reviewDayLabel(scope: String, dateIso: String): String = when (scope) {
        // 滚动口径是 `MM-DD`（5..10），不是 `substringAfterLast('-')` 的 `DD`——
        // 只截最后一段会让 7 天窗口里 10-02 与 11-02 的横轴标签长得一模一样。
        SCOPE_ROLLING_7, SCOPE_ROLLING_30 -> dateIso.substring(5).takeIf { it.length == 5 } ?: dateIso
        SCOPE_CALENDAR_WEEK -> WEEKDAY_LABELS[
            (parseIsoOrNull(dateIso)?.dayOfWeek?.value ?: 1).coerceIn(1, 7) - 1
        ]
        SCOPE_CALENDAR_MONTH -> "${parseIsoOrNull(dateIso)?.dayOfMonth ?: 1}日"
        else -> dateIso
    }

    private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private fun parseIsoOrNull(dateIso: String): java.time.LocalDate? =
        try {
            java.time.LocalDate.parse(dateIso)
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }

    /**
     * 窗口标题。
     *
     * 自然周**如实显示首尾两个日期**（跨月也照实：「10月30日 - 11月5日」），
     * 绝不折算成「第 N 周」——那会让跨月周的视觉区间与实际数据区间对不上。
     *
     * 未知 scope 返回空串：[YanjiDataModule] 在调用前已按白名单 reject，
     * 空串只是「不该走到这里」的显式标记，不是兜底文案。
     */
    fun reviewScopeLabel(
        scope: String,
        @Suppress("UNUSED_PARAMETER") periodsBack: Int,
        dates: List<java.time.LocalDate>
    ): String = when (scope) {
        SCOPE_ROLLING_7 -> "最近 7 天"
        SCOPE_ROLLING_30 -> "最近 30 天"
        // 如实显示首尾两个日期，跨月也照实；不折算成「第 N 周」。
        SCOPE_CALENDAR_WEEK -> {
            val start = dates.firstOrNull()
            val end = dates.lastOrNull()
            if (start == null || end == null) "" else "${monthDayLabel(start)} - ${monthDayLabel(end)}"
        }
        SCOPE_CALENDAR_MONTH -> {
            val start = dates.firstOrNull()
            if (start == null) "" else "${start.year}年${start.monthValue}月"
        }
        else -> ""
    }

    private fun monthDayLabel(date: java.time.LocalDate): String =
        "${date.monthValue}月${date.dayOfMonth}日"

    /**
     * 把「逐日秒数 + 逐日逐桶秒数 + 窗口内桶合计」组装成 [ReviewOverviewFields]。
     *
     * 三条不可让步的口径：
     * 1. **分钟截断只发生一次**：先按秒累加，再 `seconds / 60`；
     * 2. `dailyAverageSeconds` 的分母是**窗口长度**（[days] 的键数），不是有效天数；
     * 3. [subjectDistribution] 排序**确定性**：分钟降序，同分按 `subjectId` 字典序升序，
     *    绝不依赖 map 插入顺序（同一份数据两次调用必须给出同一份数组）。
     *
     * @param windowDates 窗口内的本地日期键；内部去重并升序，绝不出现重复键。
     * @param todayIso 今天的 `yyyy-MM-dd`；`isFuture` 就是 `date > todayIso` 的字符串比较。
     * @param subjectSecondsByDate 日期 → 该日各展示桶的秒数切片（允许缺日）。
     * @param subjectTotals 窗口内各展示桶的**合计**秒数切片。
     */
    fun reviewOverviewFields(
        scope: String,
        periodsBack: Int,
        windowDates: List<String>,
        todayIso: String,
        daySecondsByDate: Map<String, Long>,
        subjectSecondsByDate: Map<String, List<SubjectSecondsSlice>>,
        subjectTotals: List<SubjectSecondsSlice>,
        examCount: Int
    ): ReviewOverviewFields {
        val dates = windowDates.distinct().sorted()
        val localDates = dates.mapNotNull(::parseIsoOrNull)
        val safeWindowDays = dates.size.coerceAtLeast(1)

        val days = dates.map { date ->
            ReviewDayFields(
                date = date,
                dayLabel = reviewDayLabel(scope, date),
                durationSeconds = daySecondsByDate[date]?.coerceAtLeast(0L) ?: 0L,
                isToday = date == todayIso,
                // ISO 定长日期串，字典序即时间序。
                isFuture = date > todayIso
            )
        }
        val totalSeconds = days.sumOf { it.durationSeconds }

        // 归一在这里再做一次：调用方漏做 subjectBucket 时，同一大类的自定义 id
        // 仍会落进同一个桶（否则会多出一条空的「综合/未细分」切片）。
        val dailySecondsByBucket = LinkedHashMap<String, LinkedHashMap<String, Long>>()
        val nameByBucket = LinkedHashMap<String, String>()
        val totalSecondsByBucket = LinkedHashMap<String, Long>()

        fun bucketOf(slice: SubjectSecondsSlice): String {
            val (bucketId, displayName) = subjectBucket(slice.subjectId, slice.subjectName)
            nameByBucket.putIfAbsent(bucketId, displayName)
            dailySecondsByBucket.getOrPut(bucketId) { LinkedHashMap() }
            return bucketId
        }

        subjectSecondsByDate.forEach { (date, slices) ->
            slices.forEach { slice ->
                val bucketId = bucketOf(slice)
                val seconds = slice.seconds.coerceAtLeast(0L)
                val perDay = dailySecondsByBucket.getValue(bucketId)
                perDay[date] = (perDay[date] ?: 0L) + seconds
            }
        }
        subjectTotals.forEach { slice ->
            val bucketId = bucketOf(slice)
            totalSecondsByBucket[bucketId] =
                (totalSecondsByBucket[bucketId] ?: 0L) + slice.seconds.coerceAtLeast(0L)
        }

        val distribution = nameByBucket.map { (bucketId, displayName) ->
            val perDay = dailySecondsByBucket.getValue(bucketId)
            val bucketSeconds = totalSecondsByBucket[bucketId] ?: 0L
            ReviewSubjectFields(
                subjectId = bucketId,
                subjectName = displayName,
                subjectColor = subjectColorHex(bucketId, displayName),
                // 唯一的截断点：秒先加完，再除 60。
                minutes = (bucketSeconds / 60L).toInt(),
                share = if (totalSeconds > 0L) {
                    bucketSeconds.toDouble() / totalSeconds.toDouble()
                } else {
                    0.0
                },
                // 键集合与 days[].date 完全一致：没有数据的那天也必须是 0，不能缺键。
                dailyMinutes = dates.associateWith { date ->
                    ((perDay[date] ?: 0L) / 60L).toInt()
                }
            )
        }.sortedWith(
            compareByDescending<ReviewSubjectFields> { it.minutes }
                .thenBy { it.subjectId }
        )

        return ReviewOverviewFields(
            scope = scope,
            periodsBack = periodsBack,
            label = reviewScopeLabel(scope, periodsBack, localDates),
            windowDays = dates.size,
            days = days,
            totalSeconds = totalSeconds,
            // 分母是窗口长度，不是有效天数。
            dailyAverageSeconds = totalSeconds / safeWindowDays,
            examCount = examCount.coerceAtLeast(0),
            subjectDistribution = distribution
        )
    }
}
