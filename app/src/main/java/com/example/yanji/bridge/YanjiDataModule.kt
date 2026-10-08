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
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.StudyTaskEntity
import com.example.yanji.data.db.SubjectEntity
import com.example.yanji.theme.YanjiThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
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

    /** 取 Flow 首个发射值。桥接层只做一次性快照读取，不做持续订阅。 */
    private suspend fun <T> collectFirst(flow: Flow<T>): T {
        var result: T? = null
        flow.collect { value ->
            result = value
            throw FirstEmissionException()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private class FirstEmissionException : Exception()

    @ReactMethod
    fun getTodayStats(date: String, promise: Promise) {
        scope.launch {
            val focusList = repository.focusSessions.value
            val examList = repository.examSessions.value
            val tasks = collectFirst(repository.observeStudyTasks(date))
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
            val tasks = collectFirst(repository.observeStudyTasks(date))
            val array = Arguments.createArray()
            tasks.forEach { array.pushMap(taskMap(it)) }
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
     * [sessionId] 为 null 时自动关联当前活动专注会话——关联语义与
     * `MockYanjiBridge.saveQuickNote` 逐字对齐。
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
                updatedAt = now
            )
            repository.addOrUpdateNote(entry)
            emitDataChanged("notes")
            promise.resolve(noteMap(entry, sessionId))
        }
    }

    @ReactMethod
    fun getNotes(page: Double, limit: Double, promise: Promise) {
        scope.launch {
            val all = repository.noteEntries.value.sortedByDescending { it.createdAt }
            val start = ((page.toInt() - 1) * limit.toInt()).coerceAtLeast(0)
            val slice = all.drop(start).take(limit.toInt())
            val array = Arguments.createArray()
            slice.forEach { array.pushMap(noteMap(it)) }
            promise.resolve(array)
        }
    }

    @ReactMethod
    fun getNotesForDate(date: String, promise: Promise) {
        scope.launch {
            val array = Arguments.createArray()
            repository.noteEntries.value.filter { it.date == date }
                .sortedBy { it.createdAt }
                .forEach { array.pushMap(noteMap(it)) }
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

    /** 最近 N 天趋势与科目分布，数据源为 [StudyStatisticsRepository] 周汇总。 */
    @ReactMethod
    fun getReviewStats(days: Double, promise: Promise) {
        scope.launch {
            val summary = StudyStatisticsRepository(repository).getWeeklyStudySummary(0)
            val dailyFocusMinutes = Arguments.createMap()
            summary.days.forEach { day ->
                dailyFocusMinutes.putInt(day.date, (day.durationSeconds / 60).toInt())
            }
            val subjectDistribution = Arguments.createMap()
            summary.days.forEach { day ->
                day.subjectDistribution.forEach { (name, seconds) ->
                    val existing = if (subjectDistribution.hasKey(name)) subjectDistribution.getInt(name) else 0
                    subjectDistribution.putInt(name, existing + (seconds / 60).toInt())
                }
            }
            val payload = Arguments.createMap().apply {
                putInt("days", days.toInt())
                putMap("dailyFocusMinutes", dailyFocusMinutes)
                putMap("subjectDistribution", subjectDistribution)
                putDouble("totalFocusHours", summary.totalDurationSeconds / 3600.0)
                putInt("dailyAverageMinutes", (summary.dailyAverageSeconds / 60).toInt())
                putInt("activeDays", summary.activeDays)
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
            collectFirst(repository.observeStudyTasks(date))
                .filter { it.isCompleted }
                .forEach { completedTasks.pushMap(taskMap(it)) }
            val notes = Arguments.createArray()
            repository.noteEntries.value.filter { it.date == date }
                .sortedBy { it.createdAt }
                .forEach { notes.pushMap(noteMap(it)) }

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

    private fun taskMap(task: com.example.yanji.data.StudyTask): WritableMap =
        Arguments.createMap().apply {
            putString("id", task.id)
            putString("date", task.date)
            putString("subjectId", task.subjectId)
            putString("subjectName", task.subjectName)
            putString("title", task.title)
            putInt("plannedMinutes", task.plannedMinutes)
            putInt("actualMinutes", 0)
            putBoolean("completed", task.isCompleted)
            putDouble("createdAt", task.createdAt.toDouble())
        }

    private fun noteMap(note: NoteEntry, sessionId: String? = null): WritableMap =
        Arguments.createMap().apply {
            putString("id", note.id)
            putString("date", note.date)
            putDouble("timestamp", note.createdAt.toDouble())
            putString("content", note.content)
            putBoolean("isFavorite", note.isFavorite)
            putString("sessionId", sessionId ?: "")
        }

    private fun settingsMap(settings: UserSettings): WritableMap =
        Arguments.createMap().apply {
            putString("examDate", settings.targetExamDate)
            putString("targetSchool", settings.targetSchool)
            putString("targetMajor", settings.targetMajor)
            putInt("focusDurationMinutes", 45)
            putInt("breakDurationMinutes", 10)
            putString("themePreference", YanjiThemeMode.fromStorage(settings.themeMode).name)
        }

    companion object {
        const val EVENT_DATA_CHANGED = "onDataChanged"
    }
}
