package com.example.yanji.bridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.ScheduleBlock
import com.example.yanji.data.ScheduleBlockInput
import com.example.yanji.data.ScheduleDay
import com.example.yanji.data.ScheduleTemplate
import com.example.yanji.data.ScheduleValidationException
import com.example.yanji.data.StudyTaskNotCarryableException
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 「研迹」React Native ↔ Kotlin 数据桥接模块。
 *
 * 职责边界：**不绕过 Repository 直接管理 Room**（AGENTS.md §七）。
 * 所有读写都经过 [YanjiRepository]，JS 侧拿到的永远是与原生业务层同一口径的真实本地数据。
 *
 * 事件：
 * - `onDataChanged`：`{ type: 'tasks' | 'notes' | 'sessions' | 'settings' }`
 */
class YanjiDataModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository: YanjiRepository get() = YanjiRepository.getInstance()

    private var pendingPhotoPromise: Promise? = null
    private var pendingPhotoFile: File? = null

    private val activityListener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode != REQUEST_TAKE_PHOTO) return
            val promise = pendingPhotoPromise ?: return
            val photoFile = pendingPhotoFile
            pendingPhotoPromise = null
            pendingPhotoFile = null

            if (resultCode == Activity.RESULT_OK && photoFile != null && photoFile.exists() && photoFile.length() > 0) {
                promise.resolve(Uri.fromFile(photoFile).toString())
            } else {
                if (photoFile != null && photoFile.exists() && photoFile.length() == 0L) {
                    photoFile.delete()
                }
                promise.resolve(null)
            }
        }
    }

    private val lifecycleListener = object : LifecycleEventListener {
        override fun onHostResume() = Unit
        override fun onHostPause() = Unit
        override fun onHostDestroy() {
            val waiting = pendingPhotoPromise
            pendingPhotoPromise = null
            pendingPhotoFile?.let { file ->
                if (file.exists() && file.length() == 0L) file.delete()
            }
            pendingPhotoFile = null
            waiting?.resolve(null)
        }
    }

    init {
        observeDataChanges()
        reactContext.addActivityEventListener(activityListener)
        reactContext.addLifecycleEventListener(lifecycleListener)
    }

    override fun getName(): String = "YanjiDataModule"

    @ReactMethod
    fun getDailySnapshot(date: String, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(dailySnapshotMap(repository.getDailySnapshot(date)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject(
                    "E_DAILY_SNAPSHOT",
                    error.message ?: "Failed to read daily snapshot"
                )
            }
        }
    }

    override fun invalidate() {
        reactContext.removeActivityEventListener(activityListener)
        reactContext.removeLifecycleEventListener(lifecycleListener)
        pendingPhotoPromise?.resolve(null)
        pendingPhotoPromise = null
        pendingPhotoFile = null
        scope.cancel()
        super.invalidate()
    }

    // ------------------------------------------------------------ 任务

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
        scope.launchPersistedWrite(promise, "E_TASK_CREATE_FAILED") {
            val task = com.example.yanji.data.StudyTask(
                id = UUID.randomUUID().toString(),
                date = date,
                subjectId = subjectId,
                subjectName = subjectName,
                title = cleanTitle,
                plannedMinutes = minutes
            )
            repository.saveStudyTask(task)
            promise.resolve(taskMap(task))
        }
    }

    @ReactMethod
    fun updateTask(taskId: String, title: String, plannedMinutes: Double, promise: Promise) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || !plannedMinutes.isFinite() || plannedMinutes < 1.0) {
            promise.reject("E_INVALID_TASK", "A title and positive duration are required")
            return
        }
        scope.launch {
            val updated = repository.updateStudyTaskDetails(taskId, cleanTitle, plannedMinutes.toInt())
            if (!updated) {
                promise.reject("E_TASK_NOT_FOUND", "Task no longer exists")
                return@launch
            }
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun toggleTask(taskId: String, completed: Boolean, promise: Promise) {
        scope.launchPersistedWrite(promise, "E_TASK_SAVE_FAILED") {
            if (!repository.setStudyTaskCompleted(taskId, completed)) {
                throw IllegalStateException("Task no longer exists")
            }
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun getCarryOverTasks(promise: Promise) {
        scope.launch {
            try {
                val tasks = Arguments.createArray()
                repository.getCarryOverTasks().forEach { item ->
                    tasks.pushMap(taskMap(item.task, item.actualSeconds))
                }
                promise.resolve(tasks)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject("E_CARRY_OVER_READ", "读取待处理任务失败", error)
            }
        }
    }

    @ReactMethod
    fun carryTaskToToday(taskId: String, promise: Promise) {
        scope.launch {
            try {
                val carry = repository.carryStudyTaskToToday(taskId)
                promise.resolve(taskMap(carry.task, carry.actualSeconds))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: StudyTaskNotCarryableException) {
                promise.reject("E_TASK_NOT_CARRYABLE", "Task can no longer be carried over")
            } catch (error: Exception) {
                promise.reject("E_TASK_CARRY_OVER", "加入今日失败，请重试", error)
            }
        }
    }

    @ReactMethod
    fun deleteTask(taskId: String, promise: Promise) {
        scope.launchPersistedWrite(promise, "E_TASK_DELETE_FAILED") {
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
    fun saveQuickNote(
        content: String,
        date: String,
        sessionId: String?,
        category: String?,
        imageUri: String?,
        promise: Promise
    ) {
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) {
            promise.reject("E_EMPTY_NOTE", "Note content cannot be empty")
            return
        }
        scope.launch {
            val now = System.currentTimeMillis()
            val validCategory = if (category?.equals("DOUBT", ignoreCase = true) == true) "DOUBT" else "INSPIRATION"
            val entry = NoteEntry(
                id = UUID.randomUUID().toString(),
                date = date,
                content = cleanContent,
                createdAt = now,
                updatedAt = now,
                sessionId = sessionId?.takeIf { it.isNotBlank() },
                category = validCategory,
                isResolved = false,
                resolvedAt = null,
                imageUri = imageUri?.takeIf { it.isNotBlank() }
            )
            repository.addOrUpdateNoteAndAwait(entry)
            promise.resolve(noteMap(BridgeMappers.noteFields(entry)))
        }
    }

    @ReactMethod
    fun takePhoto(promise: Promise) {
        val activity = reactContext.currentActivity
        if (activity == null) {
            promise.reject("E_ACTIVITY_NULL", "当前活动页面不可用，无法唤起相机")
            return
        }
        if (pendingPhotoPromise != null) {
            promise.reject("E_PHOTO_IN_PROGRESS", "拍照正在进行中")
            return
        }
        try {
            val photosDir = File(reactContext.filesDir, "journal_photos").apply {
                if (!exists()) mkdirs()
            }
            val photoFile = File(photosDir, "${UUID.randomUUID()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                reactContext,
                "${reactContext.packageName}.fileprovider",
                photoFile
            )
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            if (cameraIntent.resolveActivity(reactContext.packageManager) == null) {
                promise.reject("E_NO_CAMERA", "未找到可用的系统相机应用")
                return
            }
            pendingPhotoPromise = promise
            pendingPhotoFile = photoFile
            @Suppress("DEPRECATION")
            activity.startActivityForResult(cameraIntent, REQUEST_TAKE_PHOTO)
        } catch (e: Exception) {
            pendingPhotoPromise = null
            pendingPhotoFile = null
            promise.reject("E_CAMERA_LAUNCH_FAILED", e.message ?: "唤起相机失败", e)
        }
    }

    @ReactMethod
    fun getRandomNotes(limit: Double, promise: Promise) {
        val count = limit.toInt().coerceIn(1, 100)
        scope.launch {
            try {
                val notes = repository.getRandomNotes(count)
                val array = Arguments.createArray()
                for (note in notes) {
                    array.pushMap(noteMap(BridgeMappers.noteFields(note)))
                }
                promise.resolve(array)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject("E_RANDOM_NOTES", error.message ?: "Failed to get random notes")
            }
        }
    }

    @ReactMethod
    fun getAllNotes(promise: Promise) {
        scope.launch {
            try {
                val notes = repository.getAllNotes()
                val array = Arguments.createArray()
                for (note in notes) {
                    array.pushMap(noteMap(BridgeMappers.noteFields(note)))
                }
                promise.resolve(array)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject("E_ALL_NOTES", error.message ?: "Failed to get all notes")
            }
        }
    }

    @ReactMethod
    fun toggleFavoriteNote(noteId: String, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(repository.toggleNoteFavoriteAndAwait(noteId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject("E_NOTE_FAVORITE", "收藏状态保存失败", error)
            }
        }
    }

    @ReactMethod
    fun toggleNoteResolved(noteId: String, promise: Promise) {
        scope.launchPersistedWrite(promise, "E_NOTE_RESOLVED") {
            promise.resolve(repository.toggleNoteResolvedAndAwait(noteId))
        }
    }

    /**
     * 人工纠正一条记录的分类（灵感 ⇄ 疑点）。
     *
     * 决策模型只给建议：它失败或未配置 Key 时 [saveQuickNote] 会静默回落
     * `INSPIRATION`，这个回落**不能成为无法纠正的事实**，所以这里提供显式改写入口。
     *
     * 采用「显式目标值」而不是 toggle：目标分类由调用方给出，无需先读内存里可能尚未
     * 回灌的列表来推断当前值，也就不存在先查后写的竞态；重复调用同一目标是幂等的。
     *
     * 只接受白名单里的两个值，其他一律 reject——把未知输入悄悄当成灵感，等于用一次
     * 静默的错误写入掩盖调用方的 bug。分类真正变化时由 DAO 一并清空
     * `isResolved` / `resolvedAt`（解决状态只对疑点有意义）。
     *
     * 返回时机与其他可等待写路径一致：Room 写入落库之后才 resolve，
     * id 不存在 reject `E_NOTE_NOT_FOUND`，写入异常 reject `E_NOTE_CATEGORY`。
     */
    @ReactMethod
    fun setNoteCategory(noteId: String, category: String, promise: Promise) {
        val validCategory = when (category.uppercase()) {
            "DOUBT" -> "DOUBT"
            "INSPIRATION" -> "INSPIRATION"
            else -> {
                promise.reject("E_INVALID_CATEGORY", "Unknown note category: $category")
                return
            }
        }
        scope.launchPersistedWrite(promise, "E_NOTE_CATEGORY") {
            val updated = repository.setNoteCategoryAndAwait(noteId, validCategory)
            if (!updated) {
                promise.reject("E_NOTE_NOT_FOUND", "Note not found")
                return@launchPersistedWrite
            }
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun deleteNote(noteId: String, promise: Promise) {
        scope.launch {
            val success = repository.deleteNoteAndAwait(noteId)
            promise.resolve(success)
        }
    }

    /**
     * 更新一条快速记录的正文。content 为空串 / 纯空白一律拒绝（与 [saveQuickNote] 同规则）；
     * id 不存在 reject `E_NOTE_NOT_FOUND`。
     *
     * 只更新 content 与 updatedAt：日期、绑定的 sessionId、收藏状态与 createdAt 均保持不变，
     * 「记录此刻」的归属事实不会被编辑动作改写。
     */
    @ReactMethod
    fun updateNote(noteId: String, content: String, promise: Promise) {
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) {
            promise.reject("E_EMPTY_NOTE", "Note content cannot be empty")
            return
        }
        scope.launch {
            val updated = repository.updateNoteContentAndAwait(noteId, cleanContent)
            if (!updated) {
                promise.reject("E_NOTE_NOT_FOUND", "Note not found")
                return@launch
            }
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

    @ReactMethod
    fun renameSubject(subjectId: String, newName: String, promise: Promise) {
        val clean = newName.trim()
        if (clean.isEmpty()) {
            promise.resolve(false)
            return
        }
        scope.launch {
            try {
                repository.renameSubject(subjectId, clean)
                emitDataChanged("subjects")
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("E_RENAME_SUBJECT", e.message)
            }
        }
    }

    @ReactMethod
    fun addSubSubject(parentId: String, name: String, promise: Promise) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            promise.resolve(null)
            return
        }
        scope.launch {
            try {
                val created = repository.addSubSubject(parentId, clean)
                if (created != null) {
                    emitDataChanged("subjects")
                    val map = Arguments.createMap().apply {
                        putString("id", created.id)
                        putString("name", created.name)
                        putString("colorHex", created.colorHex)
                        putInt("sortOrder", created.sortOrder)
                        putBoolean("enabled", created.enabled)
                        putString("parentId", created.parentId)
                        putBoolean("isCategory", created.isCategory)
                    }
                    promise.resolve(map)
                } else {
                    promise.resolve(null)
                }
            } catch (e: Exception) {
                promise.reject("E_ADD_SUB_SUBJECT", e.message)
            }
        }
    }

    @ReactMethod
    fun addSubjectCategory(name: String, promise: Promise) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            promise.resolve(null)
            return
        }
        scope.launch {
            try {
                val created = repository.addSubjectCategory(clean)
                emitDataChanged("subjects")
                val map = Arguments.createMap().apply {
                    putString("id", created.id)
                    putString("name", created.name)
                    putString("colorHex", created.colorHex)
                    putInt("sortOrder", created.sortOrder)
                    putBoolean("enabled", created.enabled)
                    putNull("parentId")
                    putBoolean("isCategory", created.isCategory)
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("E_ADD_CATEGORY", e.message)
            }
        }
    }

    @ReactMethod
    fun deleteSubject(subjectId: String, promise: Promise) {
        scope.launch {
            try {
                repository.deleteSubject(subjectId)
                emitDataChanged("subjects")
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("E_DELETE_SUBJECT", e.message)
            }
        }
    }

    @ReactMethod
    fun restoreDefaultSubjects(promise: Promise) {
        scope.launch {
            try {
                repository.restoreDefaultSubjects()
                emitDataChanged("subjects")
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("E_RESTORE_SUBJECTS", e.message)
            }
        }
    }

    // ------------------------------------------------------------ 统计

    /**
     * 回顾页概览：一次拿到窗口内的逐日时长 + 展示桶分布。
     *
     * `scope` 只有三个合法取值，**不在白名单里直接 reject** —— 猜一个默认窗口会让
     * 「上周」按钮静默变成别的区间，用户看到的区间与请求的不一致。
     *
     * 窗口语义：
     *  - `TODAY`：锚点那一天（缺省为今天）的单日窗口，`periodsBack` 被强制为 0
     *    （单日窗口没有「往前翻期」这回事）；
     *  - `CALENDAR_WEEK`：自然周，`periodsBack` = 往回翻几个整周（0 = 本周）；
     *  - `CALENDAR_MONTH`：自然月，`periodsBack` = 往回翻几个月（0 = 本月）。
     *
     * 自然周边界必须落到周一（`previousOrSame(MONDAY)`，与
     * `ReviewOverviewReader.windowOf` 同一写法），
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
        readReviewOverviews(
            listOf(ReviewOverviewReader.Request(scope, periodsBack.toLong(), anchorDate)),
            promise
        )
    }

    /**
     * 批量回顾概览：一次桥调用、一次区间扫描拿到多个窗口。
     *
     * 回顾页切换日期时同时需要「当前窗口 / 环比窗口 / 所选日所在自然周」，日期预取
     * 还要再读前后两个锚点。逐个窗口走 [getReviewOverview] 会产生 N 次桥往返与
     * 4N 条 Room 查询；批量接口把窗口合并成区间并集，每张表只扫一次，
     * 返回顺序与请求顺序严格一致。
     *
     * 任一请求的 `scope` 不在白名单内即整体 reject：部分成功会让页面拿到与请求
     * 错位的数组，比直接失败更难排查。
     */
    @ReactMethod
    fun getReviewOverviews(requests: com.facebook.react.bridge.ReadableArray, promise: Promise) {
        val specs = ArrayList<ReviewOverviewReader.Request>(requests.size())
        for (index in 0 until requests.size()) {
            val map = requests.getMap(index)
            if (map == null) {
                promise.reject("E_INVALID_REVIEW_REQUEST", "Review request #$index is null")
                return
            }
            val scope = map.getString("scope") ?: ""
            if (scope !in BridgeMappers.REVIEW_SCOPES) {
                promise.reject("E_INVALID_SCOPE", "Unknown review scope: $scope")
                return
            }
            val periodsBack = if (map.hasKey("periodsBack")) map.getDouble("periodsBack") else 0.0
            val anchorDate = if (map.hasKey("anchorDate")) map.getString("anchorDate") else null
            specs.add(ReviewOverviewReader.Request(scope, periodsBack.toLong(), anchorDate))
        }
        readReviewOverviews(specs, promise)
    }

    /**
     * 读取并聚合多个窗口。
     *
     * 聚合（逐条记录按窗口过滤、按科目分桶、排序）是 O(记录数) 的纯计算，必须离开
     * 主线程：旧实现把这一步留在 `Dispatchers.Main.immediate` 上，月度窗口数据一多，
     * 一次日期切换就会阻塞 UI 线程。Room 扫描本身在 [YanjiRepository.readCompletedStatsRows]
     * 的 IO 事务里完成，这里只负责把计算放到 Default。
     */
    private fun readReviewOverviews(
        requests: List<ReviewOverviewReader.Request>,
        promise: Promise
    ) {
        if (requests.isEmpty()) {
            promise.resolve(Arguments.createArray())
            return
        }
        this.scope.launch {
            try {
                // 连 WritableMap 装配也留在 Default：批量预取一次可能返回近十个窗口，
                // 在主线程拼装数组与每日分布只会白白占用首帧之后的 UI 时间。
                val payload = withContext(Dispatchers.Default) {
                    val overviews = ReviewOverviewReader.read(repository, requests)
                    Arguments.createArray().apply {
                        overviews.forEach { pushMap(reviewOverviewMap(it)) }
                    }
                }
                promise.resolve(payload)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject(
                    "E_REVIEW_OVERVIEW",
                    error.message ?: "Failed to read review overview",
                    error
                )
            }
        }
    }

    /**
     * 编辑某次专注的随笔。
     *
     * 模考**没有**可编辑随笔（[YanjiRepository] 侧的 focus-session 更新路径对 exam 是
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
        scope.launchPersistedWrite(promise, "E_SESSION_NOTE_SAVE_FAILED") {
            // 模考不支持可编辑随笔：先于查库拒绝，免得为一条注定失败的调用去读一次库，
            // 也免得「模考 + 空白」报出 E_EMPTY_NOTE 这种与真实原因不符的错。
            if (isExam) {
                promise.reject("E_UNSUPPORTED", "模考记录暂不支持编辑随笔")
                return@launchPersistedWrite
            }
            if (!repository.updateFocusSessionNote(sessionId, cleanNote)) {
                promise.reject("E_SESSION_NOT_FOUND", "No focus session with id $sessionId")
                return@launchPersistedWrite
            }
            promise.resolve(true)
        }
    }

    /**
     * 补录一条**已经发生**的专注记录。
     *
     * 与计时器无关：不触碰活动会话、不产生 tick，只把一段真实的过去写进
     * `focus_sessions`，口径与计时结束后落库的那条完全一致（`status = COMPLETED`），
     * 于是今天 / 回顾 / 统计在它面前不需要区分"是补的还是计的"。
     *
     * 入参是**开始时刻 + 时长**而不是结束时刻：用户记得的是"四点半开始的、学了五十分钟"，
     * 结束时刻由原生侧推导（`end = start + duration`），避免两侧各算一次而出现偏差。
     *
     * @param taskId 可选的「今日计划」关联；计划 id 必须真实存在，否则 reject 而不是
     *   写一条指向空计划的记录——那条记录永远回灌不到任何计划上。
     */
    @ReactMethod
    fun createFocusRecord(args: com.facebook.react.bridge.ReadableMap, promise: Promise) {
        // 一个 ReadableMap 而不是六个标量参数：React Native 只能把 JS 的**对象**
        // 绑定到一个 ReadableMap，多标量参数会收到 undefined 并静默失败。
        val subjectId = args.getString("subjectId")?.trim().orEmpty()
        val subjectName = args.getString("subjectName")?.trim().orEmpty()
        val startTime = if (args.hasKey("startTime")) args.getDouble("startTime") else 0.0
        val durationMinutes = if (args.hasKey("durationMinutes")) args.getDouble("durationMinutes") else 0.0
        val taskId = args.getString("taskId")?.takeIf { it.isNotBlank() }
        val note = args.getString("note")
        val startedAt = startTime.toLong()
        val minutes = durationMinutes.toInt();
        if (subjectId.isEmpty()) {
            promise.reject("E_INVALID_SUBJECT", "Subject id cannot be empty")
            return
        }
        // 时长下限挡住"0 分钟"这种无法在任何统计里成立的记录；上限是一天，
        // 用来挡住误输入（比如把 50 打成 5000）。
        if (minutes < MIN_RECORDED_MINUTES || minutes > MAX_RECORDED_MINUTES) {
            promise.reject("E_INVALID_DURATION", "Duration must be between ${MIN_RECORDED_MINUTES} and ${MAX_RECORDED_MINUTES} minutes")
            return
        }
        if (startedAt <= 0L) {
            promise.reject("E_INVALID_START", "Start time must be a positive epoch-millis value")
            return
        }
        if (startedAt > System.currentTimeMillis()) {
            promise.reject("E_FUTURE_SESSION", "A focus record cannot start in the future")
            return
        }
        val cleanSubject = subjectName.trim()
        if (cleanSubject.isEmpty()) {
            promise.reject("E_INVALID_SUBJECT", "Subject name cannot be empty")
            return
        }
        scope.launchPersistedWrite(promise, "E_SESSION_CREATE_FAILED") {
            if (!repository.subjectExists(subjectId)) {
                promise.reject("E_UNKNOWN_SUBJECT", "No subject with id $subjectId")
                return@launchPersistedWrite
            }
            if (taskId != null && !repository.taskExists(taskId)) {
                promise.reject("E_UNKNOWN_TASK", "No task with id $taskId")
                return@launchPersistedWrite
            }
            val started = startedAt
            val durationSeconds = minutes * 60L
            val record = com.example.yanji.data.FocusSession(
                id = UUID.randomUUID().toString(),
                subjectId = subjectId,
                subjectName = cleanSubject,
                startTime = started,
                endTime = started + durationSeconds * 1000L,
                durationSeconds = durationSeconds,
                mode = "补录",
                note = note?.trim().orEmpty(),
                taskId = taskId,
                status = com.example.yanji.data.SessionStatus.COMPLETED,
            )
            repository.addFocusSession(record)
            emitDataChanged("sessions")
            promise.resolve(true)
        }
    }

    /**
     * 删除一条专注 / 模考记录。
     *
     * 以 DELETE 的受影响行数判断记录是否存在，避免先查后删之间的竞态。
     */
    @ReactMethod
    fun deleteSessionRecord(sessionId: String, isExam: Boolean, promise: Promise) {
        scope.launchPersistedWrite(promise, "E_SESSION_DELETE_FAILED") {
            val deleted = if (isExam) {
                repository.deleteExamSession(sessionId)
            } else {
                repository.deleteFocusSession(sessionId)
            }
            if (!deleted) {
                promise.reject("E_SESSION_NOT_FOUND", "No session with id $sessionId")
                return@launchPersistedWrite
            }
            promise.resolve(true)
        }
    }

    // ------------------------------------------------------------ 设置

    @ReactMethod
    fun getUserSettings(promise: Promise) {
        val settings = repository.settings.value
        promise.resolve(settingsMap(repository, settings))
    }

    @ReactMethod
    fun updateUserSettings(settings: com.facebook.react.bridge.ReadableMap, promise: Promise) {
        scope.launchPersistedWrite(promise, "E_SETTINGS_SAVE_FAILED") {
            val result = repository.updateSettings { current -> current.copy(
                targetExamDate = if (settings.hasKey("examDate")) settings.getString("examDate") ?: current.targetExamDate else current.targetExamDate,
                dailyGoalHours = if (settings.hasKey("dailyGoalHours")) settings.getDouble("dailyGoalHours").toFloat() else current.dailyGoalHours,
                validStudyThresholdMinutes = if (settings.hasKey("validStudyThresholdMinutes")) settings.getInt("validStudyThresholdMinutes") else current.validStudyThresholdMinutes,
                themeMode = if (settings.hasKey("themePreference")) settings.getString("themePreference") ?: current.themeMode else current.themeMode,
                decisionProvider = if (settings.hasKey("decisionProvider")) settings.getString("decisionProvider") ?: current.decisionProvider else current.decisionProvider,
                decisionModel = if (settings.hasKey("decisionModel")) settings.getString("decisionModel") ?: current.decisionModel else current.decisionModel,
                decisionEndpoint = if (settings.hasKey("decisionEndpoint")) settings.getString("decisionEndpoint") ?: current.decisionEndpoint else current.decisionEndpoint
            ) }
            if (result !is com.example.yanji.data.SettingsUpdateResult.Saved) {
                promise.reject("E_SETTINGS_SAVE_FAILED", "Failed to persist settings")
                return@launchPersistedWrite
            }
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun saveDecisionApiKey(apiKey: String, promise: Promise) {
        promise.resolve(repository.saveDecisionApiKey(apiKey))
    }

    @ReactMethod
    fun getDecisionApiKey(promise: Promise) {
        promise.resolve(repository.getDecisionApiKey())
    }

    @ReactMethod
    fun clearDecisionApiKey(promise: Promise) {
        promise.resolve(repository.clearDecisionApiKey())
    }

    /** 考研倒计时。考试日期早于今天时夹紧为 0，不返回负值。 */
    @ReactMethod
    fun getExamCountdown(promise: Promise) {
        promise.resolve(examCountdownMap(repository.settings.value.targetExamDate))
    }

    // ------------------------------------------------------------ 每日时间表

    @ReactMethod
    fun getScheduleTemplates(promise: Promise) {
        scope.launch {
            try {
                val templates = repository.getScheduleTemplates()
                promise.resolve(Arguments.createArray().apply {
                    templates.forEach { pushMap(scheduleTemplateMap(it)) }
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_READ", "读取时间表模板失败", error)
            }
        }
    }

    @ReactMethod
    fun getScheduleDay(date: String, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(scheduleDayMap(repository.getScheduleDay(date)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_READ", "读取时间表失败", error)
            }
        }
    }

    @ReactMethod
    fun createScheduleTemplate(name: String, blocks: ReadableArray, promise: Promise) {
        val inputs = readScheduleBlocks(blocks, promise) ?: return
        scope.launch {
            try {
                promise.resolve(scheduleTemplateMap(repository.createScheduleTemplate(name, inputs)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ScheduleValidationException) {
                promise.reject(error.code, error.message)
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_SAVE", "保存模板失败，请重试", error)
            }
        }
    }

    @ReactMethod
    fun updateScheduleTemplate(
        templateId: String,
        name: String,
        blocks: ReadableArray,
        promise: Promise
    ) {
        val inputs = readScheduleBlocks(blocks, promise) ?: return
        scope.launch {
            try {
                promise.resolve(
                    scheduleTemplateMap(repository.updateScheduleTemplate(templateId, name, inputs))
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ScheduleValidationException) {
                promise.reject(error.code, error.message)
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_SAVE", "保存模板失败，请重试", error)
            }
        }
    }

    @ReactMethod
    fun renameScheduleTemplate(templateId: String, name: String, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(repository.renameScheduleTemplate(templateId, name))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ScheduleValidationException) {
                promise.reject(error.code, error.message)
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_SAVE", "重命名失败，请重试", error)
            }
        }
    }

    @ReactMethod
    fun deleteScheduleTemplate(templateId: String, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(repository.deleteScheduleTemplate(templateId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_DELETE", "删除模板失败，请重试", error)
            }
        }
    }

    @ReactMethod
    fun setScheduleDayTemplate(date: String, templateId: String, promise: Promise) {
        scope.launch {
            try {
                promise.resolve(repository.setScheduleDayTemplate(date, templateId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ScheduleValidationException) {
                promise.reject(error.code, error.message)
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_SAVE", "切换模板失败，请重试", error)
            }
        }
    }

    @ReactMethod
    fun setScheduleBlockCompleted(
        date: String,
        blockId: String,
        completed: Boolean,
        promise: Promise
    ) {
        scope.launch {
            try {
                promise.resolve(repository.setScheduleBlockCompleted(date, blockId, completed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ScheduleValidationException) {
                promise.reject(error.code, error.message)
            } catch (error: Exception) {
                promise.reject("E_SCHEDULE_SAVE", "保存完成状态失败，请重试", error)
            }
        }
    }

    // ------------------------------------------------------------ 事件

    private fun observeDataChanges() {
        scope.launch {
            repository.observeDataTableChanges().collect { tables ->
                dataEventTypesForTables(tables).forEach(::emitDataChanged)
            }
        }
    }

    private fun emitDataChanged(type: String) {
        if (!reactContext.hasActiveReactInstance()) return
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(EVENT_DATA_CHANGED, Arguments.createMap().apply { putString("type", type) })
    }

    // ------------------------------------------------------------ 映射

    private fun scheduleTemplateMap(template: ScheduleTemplate): WritableMap =
        Arguments.createMap().apply {
            putString("id", template.id)
            putString("name", template.name)
            putInt("sortOrder", template.sortOrder)
            putArray("blocks", Arguments.createArray().apply {
                template.blocks.forEach { pushMap(scheduleBlockMap(it)) }
            })
        }

    private fun scheduleBlockMap(block: ScheduleBlock): WritableMap =
        Arguments.createMap().apply {
            putString("id", block.id)
            putInt("startMinute", block.startMinute)
            putInt("endMinute", block.endMinute)
            putArray("subjectIds", Arguments.fromList(block.subjectIds))
            putString("subjectLabel", block.subjectLabel)
            putString("title", block.title)
        }

    private fun scheduleDayMap(day: ScheduleDay): WritableMap =
        Arguments.createMap().apply {
            putString("date", day.date)
            if (day.template != null) putMap("template", scheduleTemplateMap(day.template))
            else putNull("template")
            putBoolean("assigned", day.assigned)
            putArray("completedBlockIds", Arguments.fromList(day.completedBlockIds))
        }

    /**
     * 把 JS 传来的时间段数组解析成领域输入。
     *
     * 解析失败直接 reject 并返回 null：调用方不得在参数不完整时继续写库，
     * 否则一个缺字段的 map 会被 `getInt` 默认成 0，静默变成一段 00:00–00:00 的假计划。
     */
    private fun readScheduleBlocks(blocks: ReadableArray, promise: Promise): List<ScheduleBlockInput>? {
        return try {
            (0 until blocks.size()).map { index ->
                val map = blocks.getMap(index)
                    ?: throw IllegalArgumentException("时间段数据无效")
                val subjectArray = map.getArray("subjectIds")
                val subjectIds = if (subjectArray == null) {
                    emptyList()
                } else {
                    (0 until subjectArray.size()).mapNotNull { subjectArray.getString(it) }
                }
                ScheduleBlockInput(
                    id = map.getString("id")?.takeIf { it.isNotBlank() },
                    startMinute = map.getInt("startMinute"),
                    endMinute = map.getInt("endMinute"),
                    subjectIds = subjectIds,
                    subjectLabel = map.getString("subjectLabel") ?: "",
                    title = map.getString("title") ?: ""
                )
            }
        } catch (error: Exception) {
            promise.reject("E_SCHEDULE_BLOCK_INVALID", "时间段数据无效", error)
            null
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

    companion object {
        private const val REQUEST_TAKE_PHOTO = 9201
        const val EVENT_DATA_CHANGED = "onDataChanged"

        /** 一次补录至少要有一分钟，否则它在任何统计里都不成立。 */
        private const val MIN_RECORDED_MINUTES = 1

        /** 上限取一天：挡住把「50」打成「5000」这类误输入。 */
        private const val MAX_RECORDED_MINUTES = 24 * 60
    }
}

internal fun dataEventTypesForTables(tables: Set<String>): Set<String> = buildSet {
    if ("study_tasks" in tables) add("tasks")
    if ("journal_entries" in tables) add("notes")
    if ("focus_sessions" in tables || "exam_sessions" in tables) add("sessions")
    if ("user_settings" in tables) add("settings")
    if ("subjects" in tables) add("subjects")
    if (tables.any { it.startsWith("schedule_") }) add("schedule")
}
