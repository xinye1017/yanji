package com.example.yanji.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 「研迹」React Native ↔ Kotlin 数据桥接模块。
 *
 * 职责边界：**不绕过 Repository 直接管理 Room**（AGENTS.md §七）。
 * 所有读写都经过 [YanjiRepository] / [StudyStatisticsRepository]，
 * JS 侧拿到的永远是与 Compose UI 同一口径的真实本地数据。
 *
 * 事件：
 * - `onDataChanged`：`{ type: 'tasks' | 'notes' | 'sessions' | 'settings' }`
 */
class YanjiDataModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository: YanjiRepository get() = YanjiRepository.getInstance()

    init {
        observeTasks()
        observeNotes()
        observeSessions()
        observeSettings()
    }

    override fun getName(): String = "YanjiDataModule"

    // ------------------------------------------------------------ 今日统计

    /**
     * 一次性快照读取用仓库自身惯用法 `flow.first()`。
     *
     * 历史教训：这里曾有一个私有的 `collectFirst`，在 collector 里 `throw` 一个私有异常
     * 来中断采集。异常会穿出 `suspend` 函数，`return result` 不可达，而三个调用点
     * （`getTodayStats` / `getTodayTasks` / `getDailyTimeline`）的 `promise.resolve`
     * 都在其后且没有 catch —— JS promise 因此永不 settle（Today 与 Review 全废）。
     * `app/src/main` 全树也没有 `CoroutineExceptionHandler` 兜底。
     * 语义契约见 `CollectFirstSemanticsTest`。
     */

    @ReactMethod
    fun getTodayStats(date: String, promise: Promise) {
        scope.launch {
            val focusList = repository.focusSessions.value
            val examList = repository.examSessions.value
            val tasks = repository.observeStudyTasks(date).first()
            val notes = repository.noteEntries.value.filter { it.date == date }

            val totalFocusSeconds = focusList.filter {
                it.status.name == "COMPLETED" && YanjiTime.localDate(it.startTime).toString() == date
            }.sumOf { it.durationSeconds } + examList.filter {
                it.status.name == "COMPLETED" && YanjiTime.localDate(it.startTime).toString() == date
            }.sumOf { it.actualDurationSeconds }

            val completed = tasks.count { it.isCompleted }
            val payload = Arguments.createMap().apply {
                putString("date", date)
                putDouble("totalFocusSeconds", totalFocusSeconds.toDouble())
                putInt("totalFocusMinutes", (totalFocusSeconds / 60).toInt())
                putInt("completedTasksCount", completed)
                putInt("totalTasksCount", tasks.size)
                putInt("notesCount", notes.size)
            }
            promise.resolve(payload)
        }
    }

    // ------------------------------------------------------------ 任务

    @ReactMethod
    fun getTodayTasks(date: String, promise: Promise) {
        scope.launch {
            val tasks = repository.observeStudyTasks(date).first()
            val actualSecondsByTask = repository.observeTaskActualSeconds().first()
            val array = Arguments.createArray()
            tasks.forEach { array.pushMap(taskMap(it, actualSecondsByTask[it.id])) }
            promise.resolve(array)
        }
    }

    @ReactMethod
    fun createTask(
        date: String,
        subjectId: String,
        subjectName: String,
        title: String,
        plannedMinutes: Double,
        promise: Promise
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            promise.reject("E_INVALID_TITLE", "Task title cannot be empty")
            return
        }
        val minutes = plannedMinutes.toInt().coerceAtLeast(1)
        scope.launch {
            val task = com.example.yanji.data.StudyTask(
                id = UUID.randomUUID().toString(),
                date = date,
                subjectId = subjectId,
                subjectName = subjectName,
                title = cleanTitle,
                plannedMinutes = minutes
            )
            repository.saveStudyTask(task)
            emitDataChanged("tasks")
            promise.resolve(taskMap(task))
        }
    }

    @ReactMethod
    fun toggleTask(taskId: String, completed: Boolean, promise: Promise) {
        scope.launch {
            repository.setStudyTaskCompleted(taskId, completed)
            emitDataChanged("tasks")
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun deleteTask(taskId: String, promise: Promise) {
        scope.launch {
            repository.deleteStudyTask(taskId)
            emitDataChanged("tasks")
            promise.resolve(true)
        }
    }

    // ------------------------------------------------------------ 记录此刻

    /**
     * 保存一条快速记录。content 为空串 / 纯空白一律拒绝（与 E2E 契约一致）。
     *
     * [sessionId] 由 JS 侧解析活动会话后传入（见 `RecordMomentModal.tsx`），
     * 桥接层**原样持久化**，不自行反查活动会话：那样会让"绑哪一场"出现第二个事实来源。
     * 参数为 null 表示这条记录不绑定任何时段，落库为 NULL，读回仍是 null（绝不是空串）。
     *
     * 返回时机：Room 写入**已落库**之后（[com.example.yanji.data.YanjiRepository.addOrUpdateNoteAndAwait]），
     * JS 侧紧接着读取一定能看到这一条。
     */
    @ReactMethod
    fun saveQuickNote(content: String, date: String, sessionId: String?, promise: Promise) {
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) {
            promise.reject("E_EMPTY_NOTE", "Note content cannot be empty")
            return
        }
        scope.launch {
            val now = System.currentTimeMillis()
            val entry = NoteEntry(
                id = UUID.randomUUID().toString(),
                date = date,
                content = cleanContent,
                createdAt = now,
                updatedAt = now,
                sessionId = sessionId?.takeIf { it.isNotBlank() }
            )
            repository.addOrUpdateNoteAndAwait(entry)
            emitDataChanged("notes")
            promise.resolve(noteMap(BridgeMappers.noteFields(entry)))
        }
    }

    @ReactMethod
    fun getNotes(page: Double, limit: Double, promise: Promise) {
        scope.launch {
            val all = repository.noteEntries.value.sortedByDescending { it.createdAt }
            val start = ((page.toInt() - 1) * limit.toInt()).coerceAtLeast(0)
            val slice = all.drop(start).take(limit.toInt())
            val array = Arguments.createArray()
            slice.forEach { array.pushMap(noteMap(BridgeMappers.noteFields(it))) }
            promise.resolve(array)
        }
    }

    @ReactMethod
    fun getNotesForDate(date: String, promise: Promise) {
        scope.launch {
            val array = Arguments.createArray()
            repository.noteEntries.value.filter { it.date == date }
                .sortedBy { it.createdAt }
                .forEach { array.pushMap(noteMap(BridgeMappers.noteFields(it))) }
            promise.resolve(array)
        }
    }

    @ReactMethod
    fun toggleFavoriteNote(noteId: String, promise: Promise) {
        scope.launch {
            val note = repository.noteEntries.value.find { it.id == noteId }
            if (note == null) {
                promise.resolve(false)
                return@launch
            }
            repository.setNoteFavorite(noteId, !note.isFavorite)
            emitDataChanged("notes")
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun deleteNote(noteId: String, promise: Promise) {
        scope.launch {
            repository.deleteNote(noteId)
            emitDataChanged("notes")
            promise.resolve(true)
        }
    }

    // ------------------------------------------------------------ 学科

    @ReactMethod
    fun getSubjects(promise: Promise) {
        val array = Arguments.createArray()
        SubjectCatalog.all.forEach { subject ->
            array.pushMap(Arguments.createMap().apply {
                putString("id", subject.id)
                putString("name", subject.name)
                putString("colorHex", subject.colorHex)
                putInt("sortOrder", subject.sortOrder)
                putBoolean("enabled", subject.enabled)
                putString("parentId", subject.parentId)
                putBoolean("isCategory", subject.isCategory)
            })
        }
        promise.resolve(array)
    }

    // ------------------------------------------------------------ 统计

    /**
     * 回顾页概览：一次拿到窗口内的逐日时长 + 展示桶分布。
     *
     * `scope` 只有四个合法取值，**不在白名单里直接 reject** —— 猜一个默认窗口会让
     * 「上周」按钮静默变成「最近 7 天」，用户看到的区间与请求的不一致。
     *
     * 窗口语义：
     *  - `ROLLING_7` / `ROLLING_30`：今天往前 N 天（含今天），恒不含未来日期，
     *    `periodsBack` 被强制为 0（滚动窗口没有「往前翻期」这回事）；
     *  - `CALENDAR_WEEK`：自然周，`periodsBack` = 往回翻几个整周（0 = 本周）；
     *  - `CALENDAR_MONTH`：自然月，`periodsBack` = 往回翻几个月（0 = 本月）。
     *
     * 自然周边界必须落到周一（`previousOrSame(MONDAY)`，与
     * `StudyStatisticsRepository.buildWeeklyStudySummary` 同一写法），
     * 拿 7 天滚动凑会让「上周」和本周的数据整块重叠。
     *
     * 科目分布按展示桶归一：**`row.subjectId` 与 `row.subjectName` 必须成对传入**
     * `BridgeMappers.subjectBucket`，只看名字无法把自定义 id 归进它的大类。
     *
     * 所有秒 → 分钟的换算只在 [BridgeMappers] 里发生一次：这里交出去的全部是原始秒数。
     */
    @ReactMethod
    fun getReviewOverview(scope: String, periodsBack: Double, anchorDate: String?, promise: Promise) {
        if (scope !in BridgeMappers.REVIEW_SCOPES) {
            promise.reject("E_INVALID_SCOPE", "Unknown review scope: $scope")
            return
        }
        val effectivePeriodsBack = when (scope) {
            BridgeMappers.SCOPE_ROLLING_7, BridgeMappers.SCOPE_ROLLING_30 -> 0L
            else -> periodsBack.toLong().coerceAtLeast(0L)
        }
        // 窗口在协程外算好：只需要本地日历推进，没有 IO，也就没有挂起语义。
        val window = reviewWindow(scope, effectivePeriodsBack, anchorDate)

        // `scope` 这个形参遮住了协程域，必须写全名，否则下面 launch 的对象是 String。
        this.scope.launch {
            val range = window.range
            val focusSessions = repository.observeFocusSessionsInRange(range.startInclusive, range.endExclusive).first()
            val examSessions = repository.observeExamSessionsInRange(range.startInclusive, range.endExclusive).first()
            val focusTotals = repository.observeFocusSubjectTotals(range.startInclusive, range.endExclusive).first()
            val examTotals = repository.observeExamSubjectTotals(range.startInclusive, range.endExclusive).first()

            val windowKeys = window.dates.toSet()
            val daySecondsByDate = LinkedHashMap<String, Long>()
            window.dates.forEach { daySecondsByDate[it] = 0L }
            val subjectSecondsByDate = LinkedHashMap<String, MutableList<BridgeMappers.SubjectSecondsSlice>>()

            // 专注取 durationSeconds、模考取 actualDurationSeconds，统一成 (日期, 桶, 秒数)。
            fun accumulate(startTime: Long, subjectId: String, subjectName: String, seconds: Long) {
                val date = YanjiTime.localDate(startTime).format(YanjiTime.isoDateFormatter)
                if (date !in windowKeys) return
                daySecondsByDate[date] = (daySecondsByDate[date] ?: 0L) + seconds
                val (bucketId, displayName) = BridgeMappers.subjectBucket(subjectId, subjectName)
                subjectSecondsByDate.getOrPut(date) { mutableListOf() }
                    .add(BridgeMappers.SubjectSecondsSlice(bucketId, displayName, seconds))
            }

            focusSessions.forEach { accumulate(it.startTime, it.subjectId, it.subjectName, it.durationSeconds) }
            examSessions.forEach {
                accumulate(it.startTime, it.subjectId, it.subjectName, it.actualDurationSeconds)
            }

            // 窗口内各展示桶的合计秒数（DAO 的 GROUP BY 行，已按 status='COMPLETED' 过滤）
            val subjectTotals = (focusTotals + examTotals).map { row ->
                val (bucketId, displayName) = BridgeMappers.subjectBucket(row.subjectId, row.subjectName)
                BridgeMappers.SubjectSecondsSlice(bucketId, displayName, row.durationSeconds)
            }

            val overview = BridgeMappers.reviewOverviewFields(
                scope = scope,
                periodsBack = effectivePeriodsBack.toInt(),
                windowDates = window.dates,
                todayIso = YanjiTime.todayIso(),
                daySecondsByDate = daySecondsByDate,
                subjectSecondsByDate = subjectSecondsByDate,
                subjectTotals = subjectTotals,
                examCount = examSessions.count { it.status.name == "COMPLETED" }
            )
            promise.resolve(reviewOverviewMap(overview))
        }
    }

    /** 回顾窗口 = 一个 [com.example.yanji.data.EpochRange] + 窗口内的本地日期键。 */
    private data class ReviewWindow(
        val range: com.example.yanji.data.EpochRange,
        val dates: List<String>
    )

    /**
     * scope → 窗口。日期键与 DAO 查询区间来自同一套日历推进，二者不会错位。
     * 当传入 anchorDate 时，滚动窗口以此日期为终点向前推。
     */
    private fun reviewWindow(scope: String, periodsBack: Long, anchorDate: String? = null): ReviewWindow {
        val anchorLocalDate = anchorDate?.let { dateStr ->
            try {
                java.time.LocalDate.parse(dateStr, YanjiTime.isoDateFormatter)
            } catch (_: Exception) {
                null
            }
        } ?: YanjiTime.today()
        val zoneId = java.time.ZoneId.systemDefault()

        return when (scope) {
            BridgeMappers.SCOPE_ROLLING_7 -> {
                val start = anchorLocalDate.minusDays(6L)
                val range = com.example.yanji.data.EpochRange(
                    start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    anchorLocalDate.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
                ReviewWindow(
                    range,
                    BridgeMappers.rollingWindowDates(start, 7)
                )
            }
            BridgeMappers.SCOPE_ROLLING_30 -> {
                val start = anchorLocalDate.minusDays(29L)
                val range = com.example.yanji.data.EpochRange(
                    start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    anchorLocalDate.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
                ReviewWindow(
                    range,
                    BridgeMappers.rollingWindowDates(start, 30)
                )
            }
            BridgeMappers.SCOPE_CALENDAR_WEEK -> {
                val monday = anchorLocalDate.minusWeeks(periodsBack)
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6L)
                val range = com.example.yanji.data.EpochRange(
                    monday.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    sunday.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
                ReviewWindow(range, BridgeMappers.rollingWindowDates(monday, 7))
            }
            BridgeMappers.SCOPE_CALENDAR_MONTH -> {
                val first = anchorLocalDate.minusMonths(periodsBack).withDayOfMonth(1)
                val last = first.withDayOfMonth(first.lengthOfMonth())
                val range = com.example.yanji.data.EpochRange(
                    first.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    last.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
                ReviewWindow(
                    range,
                    BridgeMappers.rollingWindowDates(first, first.lengthOfMonth())
                )
            }
            else -> throw IllegalArgumentException("Unsupported review scope: $scope")
        }
    }

    /**
     * 编辑某次专注的随笔。
     *
     * 模考**没有**可编辑随笔（`StudyStatisticsRepository.updateSessionNote` 对 exam 是
     * no-op），因此显式 reject 而不是静默 `resolve(true)` —— 静默成功会让用户以为存上了。
     *
     * 纯空白随笔一律拒绝（与 [saveQuickNote] 同一先例）：把空白写进库等于制造一条
     * 「有内容但是空的」记录，读回来没有任何信息量。
     */
    @ReactMethod
    fun updateSessionNote(sessionId: String, isExam: Boolean, note: String, promise: Promise) {
        val cleanNote = note.trim()
        if (cleanNote.isEmpty()) {
            promise.reject("E_EMPTY_NOTE", "Session note cannot be empty")
            return
        }
        scope.launch {
            // 模考不支持可编辑随笔：先于查库拒绝，免得为一条注定失败的调用去读一次库，
            // 也免得「模考 + 空白」报出 E_EMPTY_NOTE 这种与真实原因不符的错。
            if (isExam) {
                promise.reject("E_UNSUPPORTED", "模考记录暂不支持编辑随笔")
                return@launch
            }
            if (StudyStatisticsRepository(repository).getFocusSessionDetail(sessionId) == null) {
                promise.reject("E_SESSION_NOT_FOUND", "No focus session with id $sessionId")
                return@launch
            }
            repository.updateFocusSessionNote(sessionId, cleanNote)
            emitDataChanged("sessions")
            promise.resolve(true)
        }
    }

    /**
     * 删除一条专注 / 模考记录。
     *
     * 先确认记录存在再删：删不存在的 id 也 resolve(true) 会让前端把「删掉了」当成事实，
     * 而库里那条记录其实还在。
     */
    @ReactMethod
    fun deleteSessionRecord(sessionId: String, isExam: Boolean, promise: Promise) {
        scope.launch {
            val statistics = StudyStatisticsRepository(repository)
            val detail = if (isExam) {
                statistics.getExamSessionDetail(sessionId)
            } else {
                statistics.getFocusSessionDetail(sessionId)
            }
            if (detail == null) {
                promise.reject("E_SESSION_NOT_FOUND", "No session with id $sessionId")
                return@launch
            }
            if (isExam) {
                repository.deleteExamSession(sessionId)
            } else {
                repository.deleteFocusSession(sessionId)
            }
            emitDataChanged("sessions")
            promise.resolve(true)
        }
    }

    /** 指定日期的确定性时间线：专注/模考 + 已完成任务 + 记录，全部来自 Room。 */
    @ReactMethod
    fun getDailyTimeline(date: String, promise: Promise) {
        scope.launch {
            val summary = StudyStatisticsRepository(repository).getDailyStudySummary(date)
            val sessions = Arguments.createArray()
            summary.sessions.forEach { item ->
                sessions.pushMap(Arguments.createMap().apply {
                    putString("id", item.id)
                    putString("title", item.title)
                    putString("subjectId", item.subjectId)
                    putString("subjectName", item.subjectName)
                    putString("subjectColor", item.subjectColor)
                    putDouble("startTime", item.startTime.toDouble())
                    putDouble("endTime", item.endTime.toDouble())
                    putDouble("durationSeconds", item.durationSeconds.toDouble())
                    putBoolean("isExam", item.isExam)
                    putString("note", item.note)
                    putInt("pauseCount", item.pauseCount)
                    putString("mode", item.mode)
                })
            }
            val completedTasks = Arguments.createArray()
            val actualSecondsByTask = repository.observeTaskActualSeconds().first()
            repository.observeStudyTasks(date).first()
                .filter { it.isCompleted }
                .forEach { completedTasks.pushMap(taskMap(it, actualSecondsByTask[it.id])) }
            val notes = Arguments.createArray()
            repository.noteEntries.value.filter { it.date == date }
                .sortedBy { it.createdAt }
                .forEach { notes.pushMap(noteMap(BridgeMappers.noteFields(it))) }

            val payload = Arguments.createMap().apply {
                putString("date", date)
                putString("formattedDate", summary.formattedDate)
                putDouble("totalDurationSeconds", summary.totalDurationSeconds.toDouble())
                putInt("focusCount", summary.focusCount)
                putInt("examCount", summary.examCount)
                putArray("sessions", sessions)
                putArray("completedTasks", completedTasks)
                putArray("notes", notes)
            }
            promise.resolve(payload)
        }
    }

    // ------------------------------------------------------------ 设置

    @ReactMethod
    fun getUserSettings(promise: Promise) {
        val settings = repository.settings.value
        promise.resolve(settingsMap(settings))
    }

    @ReactMethod
    fun updateUserSettings(settings: com.facebook.react.bridge.ReadableMap, promise: Promise) {
        val current = repository.settings.value
        val next = current.copy(
            targetExamDate = if (settings.hasKey("examDate")) settings.getString("examDate") ?: current.targetExamDate else current.targetExamDate,
            dailyGoalHours = if (settings.hasKey("dailyGoalHours")) settings.getDouble("dailyGoalHours").toFloat() else current.dailyGoalHours,
            validStudyThresholdMinutes = if (settings.hasKey("validStudyThresholdMinutes")) settings.getInt("validStudyThresholdMinutes") else current.validStudyThresholdMinutes,
            themeMode = if (settings.hasKey("themePreference")) settings.getString("themePreference") ?: current.themeMode else current.themeMode
        )
        val result = repository.updateSettings(next)
        promise.resolve(result is com.example.yanji.data.SettingsUpdateResult.Saved)
    }

    /** 考研倒计时。考试日期早于今天时夹紧为 0，不返回负值。 */
    @ReactMethod
    fun getExamCountdown(promise: Promise) {
        val settings = repository.settings.value
        val examDateRaw = settings.targetExamDate
        if (examDateRaw.isBlank()) {
            promise.resolve(Arguments.createMap().apply {
                putString("examDate", "")
                putInt("daysRemaining", 0)
            })
            return
        }
        val examDate = YanjiTime.parseIsoDate(examDateRaw)
        if (examDate == null) {
            promise.resolve(Arguments.createMap().apply {
                putString("examDate", examDateRaw)
                putInt("daysRemaining", 0)
            })
            return
        }
        val today = YanjiTime.today()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, examDate).coerceAtLeast(0)
        promise.resolve(Arguments.createMap().apply {
            putString("examDate", examDateRaw)
            putInt("daysRemaining", days.toInt())
        })
    }

    // ------------------------------------------------------------ 事件

    private fun observeTasks() {
        scope.launch {
            repository.observeStudyTasks(YanjiTime.todayIso()).collectLatest {
                emitDataChanged("tasks")
            }
        }
    }

    private fun observeNotes() {
        scope.launch {
            repository.noteEntries.collectLatest { emitDataChanged("notes") }
        }
    }

    /**
     * 专注 **与** 模考都在监听：只订阅 `focusSessions` 时，删掉 / 改掉一条模考后
     * 回顾页收不到任何事件，界面上那条记录会一直留着。
     *
     * `combine` 在两条 Flow 中**任意一条**发射时都会发射，因此写路径
     * （`updateSessionNote` / `deleteSessionRecord`）里显式的 `emitDataChanged`
     * 与这里的 Flow 发射会对同一次写重复发事件。前端按 `type` 不去重、
     * 「可重复刷新」是已接受的现状 —— 这里刻意**不做**节流 / 去抖：
     * 桥接层少发一次事件，界面就少刷新一次。
     */
    private fun observeSessions() {
        scope.launch {
            combine(repository.focusSessions, repository.examSessions) { _, _ -> Unit }
                .collectLatest { emitDataChanged("sessions") }
        }
    }

    private fun observeSettings() {
        scope.launch {
            repository.settings.collectLatest { emitDataChanged("settings") }
        }
    }

    private fun emitDataChanged(type: String) {
        if (!reactContext.hasActiveReactInstance()) return
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(EVENT_DATA_CHANGED, Arguments.createMap().apply { putString("type", type) })
    }

    // ------------------------------------------------------------ 映射

    /**
     * 只装配 `WritableMap`；契约语义全部在 [BridgeMappers] 里，可在 JVM 单测逐字段断言。
     */
    private fun taskMap(
        task: com.example.yanji.data.StudyTask,
        actualSeconds: Long? = null
    ): WritableMap {
        val fields = BridgeMappers.taskFields(task, actualSeconds)
        return Arguments.createMap().apply {
            putString("id", fields.id)
            putString("date", fields.date)
            putString("subjectId", fields.subjectId)
            putString("subjectName", fields.subjectName)
            putString("title", fields.title)
            putInt("plannedMinutes", fields.plannedMinutes)
            // 真实累计分钟数（秒 / 60），不再是恒为 0 的占位值。
            putInt("actualMinutes", fields.actualMinutes)
            putBoolean("completed", fields.completed)
            putDouble("createdAt", fields.createdAt)
        }
    }

    /**
     * 回顾概览载荷（`ReviewOverview`）。这里只做 `WritableMap` 装配，
     * 所有秒 → 分钟 / 占比 / 排序都在 [BridgeMappers] 里算完了。
     */
    private fun reviewOverviewMap(fields: BridgeMappers.ReviewOverviewFields): WritableMap =
        Arguments.createMap().apply {
            putString("scope", fields.scope)
            putInt("periodsBack", fields.periodsBack)
            putString("label", fields.label)
            putInt("windowDays", fields.windowDays)
            putArray(
                "days",
                Arguments.createArray().apply {
                    fields.days.forEach { day ->
                        pushMap(Arguments.createMap().apply {
                            putString("date", day.date)
                            putString("dayLabel", day.dayLabel)
                            putDouble("durationSeconds", day.durationSeconds.toDouble())
                            putBoolean("isToday", day.isToday)
                            putBoolean("isFuture", day.isFuture)
                        })
                    }
                }
            )
            putDouble("totalSeconds", fields.totalSeconds.toDouble())
            putDouble("dailyAverageSeconds", fields.dailyAverageSeconds.toDouble())
            putInt("examCount", fields.examCount)
            putArray(
                "subjectDistribution",
                Arguments.createArray().apply {
                    fields.subjectDistribution.forEach { slice ->
                        pushMap(Arguments.createMap().apply {
                            putString("subjectId", slice.subjectId)
                            putString("subjectName", slice.subjectName)
                            putString("subjectColor", slice.subjectColor)
                            putInt("minutes", slice.minutes)
                            putDouble("share", slice.share)
                            putMap(
                                "dailyMinutes",
                                Arguments.createMap().apply {
                                    slice.dailyMinutes.forEach { (date, minutes) ->
                                        putInt(date, minutes)
                                    }
                                }
                            )
                        })
                    }
                }
            )
        }

    private fun noteMap(fields: BridgeMappers.NoteFields): WritableMap =
        Arguments.createMap().apply {
            putString("id", fields.id)
            putString("date", fields.date)
            putDouble("timestamp", fields.timestamp)
            putString("content", fields.content)
            putBoolean("isFavorite", fields.isFavorite)
            // 未绑定时必须是 null —— 契约 `isValidNoteEntry` 对每条 NoteEntry 载荷生效。
            // 显式 putNull（与 emitStateChanged 的写法一致），绝不退化成空串。
            if (fields.sessionId == null) putNull("sessionId") else putString("sessionId", fields.sessionId)
        }

    private fun settingsMap(settings: UserSettings): WritableMap {
        val fields = BridgeMappers.settingsFields(settings)
        return Arguments.createMap().apply {
            putString("examDate", fields.examDate)
            putString("targetSchool", fields.targetSchool)
            putString("targetMajor", fields.targetMajor)
            putString("themePreference", fields.themePreference)
        }
    }

    companion object {
        const val EVENT_DATA_CHANGED = "onDataChanged"
    }
}
