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
     * 最近 N 天趋势与科目分布。
     *
     * 窗口是**滚动 N 天**（含今天，不含明天），绝不按自然周（周一为起点）裁剪：
     * 否则周一之后调用会得到未来日期的零柱、并把周一的真实数据整块丢掉。
     * `days` 被如实尊重；`dailyAverageMinutes` 的分母是**窗口长度**，
     * `activeDays` 用 30 分钟阈值 —— 与 `test-e2e/contracts/mock-bridge.js` 的
     * `getReviewStats` 逐字对齐。
     */
    @ReactMethod
    fun getReviewStats(days: Double, promise: Promise) {
        scope.launch {
            val windowDays = days.toLong().coerceAtLeast(1L).toInt()
            val today = YanjiTime.today()
            val windowDates = BridgeMappers.rollingWindowDates(today.minusDays((windowDays - 1).toLong()), windowDays)
            val range = YanjiTime.lastDaysRange(windowDays.toLong())
            val activeDayThresholdSeconds =
                repository.settings.value.validStudyThresholdMinutes.coerceAtLeast(1).toLong() * 60L

            val focusSessions = repository.observeFocusSessionsInRange(range.startInclusive, range.endExclusive).first()
            val examSessions = repository.observeExamSessionsInRange(range.startInclusive, range.endExclusive).first()
            val focusTotals = repository.observeFocusSubjectTotals(range.startInclusive, range.endExclusive).first()
            val examTotals = repository.observeExamSubjectTotals(range.startInclusive, range.endExclusive).first()

            val windowKeys = windowDates.toSet()

            // 每个窗口日期的当日秒数（含今天；未来日期永远不会出现）
            val daySecondsByDate = LinkedHashMap<String, Long>()
            windowDates.forEach { daySecondsByDate[it] = 0L }

            // FocusSession 与 ExamSession 都只有 startTime + 各自的实际时长，
            // 先归一成 (日期, 秒数) 再按窗口键聚合，避免在混合列表上做强转。
            val windowSeconds: List<Pair<Long, Long>> =
                focusSessions.map { it.startTime to it.durationSeconds } +
                    examSessions.map { it.startTime to it.actualDurationSeconds }
            windowSeconds.forEach { (startTime, seconds) ->
                val date = YanjiTime.localDate(startTime).format(YanjiTime.isoDateFormatter)
                if (date in windowKeys) {
                    daySecondsByDate[date] = (daySecondsByDate[date] ?: 0L) + seconds
                }
            }

            // 科目分布：按展示名聚合分钟数（只统计窗口内的会话）
            val subjectMinutes = LinkedHashMap<String, Int>()
            (focusTotals + examTotals).forEach { row ->
                subjectMinutes[row.subjectName] =
                    (subjectMinutes[row.subjectName] ?: 0) + (row.durationSeconds / 60L).toInt()
            }

            val stats = BridgeMappers.reviewStatsFields(
                windowDays = windowDays,
                daySecondsByDate = daySecondsByDate,
                subjectMinutesBySubject = subjectMinutes,
                activeDayThresholdSeconds = activeDayThresholdSeconds
            )

            val dailyFocusMinutes = Arguments.createMap()
            stats.dailyFocusMinutes.forEach { (date, minutes) ->
                dailyFocusMinutes.putInt(date, minutes)
            }
            val subjectDistribution = Arguments.createMap()
            stats.subjectDistribution.forEach { (name, minutes) ->
                subjectDistribution.putInt(name, minutes)
            }
            val payload = Arguments.createMap().apply {
                putInt("days", stats.days)
                putMap("dailyFocusMinutes", dailyFocusMinutes)
                putMap("subjectDistribution", subjectDistribution)
                putDouble("totalFocusHours", stats.totalFocusHours)
                putInt("dailyAverageMinutes", stats.dailyAverageMinutes)
                putInt("activeDays", stats.activeDays)
            }
            promise.resolve(payload)
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

    private fun observeSessions() {
        scope.launch {
            repository.focusSessions.collectLatest { emitDataChanged("sessions") }
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
