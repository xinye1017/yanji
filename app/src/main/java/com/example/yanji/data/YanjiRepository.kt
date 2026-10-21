package com.example.yanji.data


import android.content.Context
import com.example.yanji.data.backup.BackupImportResult
import com.example.yanji.data.achievement.AchievementEvaluator
import com.example.yanji.data.achievement.AchievementEvent
import com.example.yanji.data.ai.AiClient
import com.example.yanji.data.ai.AiDiagnosticParser
import com.example.yanji.data.checkin.CheckInStore
import com.example.yanji.data.checkin.DayCheckInStatus
import com.example.yanji.data.note.NoteStore
import com.example.yanji.data.study.StudyStats
import com.example.yanji.data.timer.TimerStore
import com.example.yanji.data.db.*
import com.example.yanji.data.security.KeystoreSecretStore
import com.example.yanji.data.security.SecretStore
import com.example.yanji.data.security.SecretWriteResult
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.FileTimerSessionPersistence
import com.example.yanji.data.timer.SystemMonotonicClock
import com.example.yanji.service.FocusTimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class YanjiRepository private constructor() {

    companion object {
        @Volatile
        private var instance: YanjiRepository? = null

        fun getInstance(): YanjiRepository {
            return instance ?: synchronized(this) {
                instance ?: YanjiRepository().also { instance = it }
            }
        }

        fun init(context: Context) {
            val repo = getInstance()
            val appContext = context.applicationContext
            repo.appContext = appContext
            repo.secretStore = KeystoreSecretStore(appContext)
            val db = YanjiDatabase.getDatabase(appContext)
            repo.bindDatabase(db)
        }
    }

    /** 第三方 AI 的网络客户端。Repository 只决定「要不要发请求、失败怎么兜底」。 */
    private val aiClient = AiClient()

    /** 把 AI 返回的自由文本解析为结构化诊断；协议/解析细节与数据层隔离。 */
    private val aiDiagnosticParser = AiDiagnosticParser()

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var database: YanjiDatabase? = null
    @Volatile
    private var appContext: Context? = null

    /** AI 凭据的秘密存储（Keystore 加密 + no-backup 目录），与 Room 中的普通设置完全分离。 */
    @Volatile
    private var secretStore: SecretStore? = null

    /** 凭据在内存中的只读镜像，避免每次设置 Flow 发射都做一次 Keystore 解密。 */
    @Volatile
    private var cachedAiApiKey: String = ""

    /**
     * 冷启动预热用的就绪信号：设置与学科的首次 DB 快照都已回灌进内存镜像。
     *
     * `settings` / `SubjectCatalog` 都是异步回灌的，没等它们就同步读出来的
     * 「目标小时数」和「学科名」是默认值而不是用户真实配置。
     */
    private val settingsHydrated = CompletableDeferred<Unit>()
    private val subjectsHydrated = CompletableDeferred<Unit>()

    /**
     * 等待内存镜像与 DB 对齐。预热（见 `bridge/LaunchPreheat`）读快照前必须调用。
     */
    suspend fun awaitHydration() {
        awaitAll(settingsHydrated, subjectsHydrated)
    }

    private val _subjects = MutableStateFlow(SubjectCatalog.all)
    val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    /**
     * 写入学科并同步内存镜像。
     *
     * 这里刻意**不**只依赖 DB Flow 回灌：`SubjectCatalog` 是同步读的镜像，如果等 Flow
     * 下一帧才更新，紧接着的同步查询（如统计分桶）会读到旧数据。因此写完立刻
     * 用同一个列表刷新两侧。
     */
    private suspend fun persistSubjects(mutate: suspend (SubjectDao) -> Unit) {
        val dao = database?.subjectDao() ?: return
        if (dao.count() == 0) {
            dao.insertAll(SubjectCatalog.defaults.map { SubjectEntity.fromDomainModel(it) })
        }
        mutate(dao)
        val latest = dao.getAll().map { it.toDomainModel() }
        SubjectCatalog.replaceAll(latest)
        _subjects.value = SubjectCatalog.all
    }

    /** 新增一个顶级学科类别。 */
    suspend fun addSubjectCategory(name: String): Subject {
        val id = "custom_" + UUID.randomUUID().toString().take(8)
        val subject = Subject(
            id = id,
            name = name.trim(),
            colorHex = nextSubjectColor(),
            sortOrder = (_subjects.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        )
        persistSubjects { it.insert(SubjectEntity.fromDomainModel(subject)) }
        return subject
    }

    /** 在指定类别下新增子学科。 */
    suspend fun addSubSubject(parentId: String, name: String): Subject? {
        val parent = SubjectCatalog.find(parentId) ?: return null
        val id = "custom_" + UUID.randomUUID().toString().take(8)
        val siblings = SubjectCatalog.all.filter { it.parentId == parentId }
        val subject = Subject(
            id = id,
            name = name.trim(),
            colorHex = nextSubSubjectColor(parent.colorHex, siblings.size),
            sortOrder = parent.sortOrder * 100 + siblings.size + 1,
            parentId = parentId
        )
        persistSubjects { it.insert(SubjectEntity.fromDomainModel(subject)) }
        return subject
    }

    /** 重命名学科（类别或子学科），并同步更新历史专注记录与计划中的学科名称。 */
    suspend fun renameSubject(subjectId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        val existing = SubjectCatalog.find(subjectId) ?: return
        persistSubjects {
            it.updateName(subjectId, clean)
        }
        database?.focusSessionDao()?.updateSubjectName(subjectId, clean)
        database?.examSessionDao()?.updateSubjectName(subjectId, clean)
        database?.studyTaskDao()?.updateSubjectName(subjectId, clean)
        if (existing.isCategory) {
            val directBucket = SubjectCatalog.directBucketId(subjectId)
            val bucketName = "$clean（综合/未细分）"
            database?.focusSessionDao()?.updateSubjectName(directBucket, bucketName)
            database?.examSessionDao()?.updateSubjectName(directBucket, bucketName)
            database?.studyTaskDao()?.updateSubjectName(directBucket, bucketName)
        }
    }

    /**
     * 删除学科。删除类别时连同其子学科一起删除。
     *
     * 历史学习记录**不做级联改动**：它们保存的是 subjectId + subjectName 的字符串快照，
     * 删除后仍可独立展示，统计时按 id 反查不到学科会回落到「其他」分桶。
     */
    suspend fun deleteSubject(subjectId: String) {
        val existing = SubjectCatalog.find(subjectId) ?: return
        persistSubjects { dao ->
            if (existing.isCategory) dao.deleteChildrenOf(subjectId)
            dao.deleteById(subjectId)
        }
    }

    /** 把学科恢复为出厂默认（覆盖式）。 */
    suspend fun restoreDefaultSubjects() {
        persistSubjects { dao ->
            dao.deleteAll()
            dao.insertAll(SubjectCatalog.defaults.map { SubjectEntity.fromDomainModel(it) })
        }
    }

    private fun nextSubjectColor(): String {
        val palette = SubjectCatalog.CATEGORY_PALETTE
        val used = _subjects.value.filter { it.isCategory }.map { it.colorHex }.toSet()
        return palette.firstOrNull { it !in used } ?: palette[_subjects.value.size % palette.size]
    }

    /**
     * 子学科继承父级 hue，但把自己的 tone 直接写进 colorHex。
     * 颜色一旦创建即成为该学科的稳定数据，不再依赖 sibling 顺序参与渲染。
     */
    private fun nextSubSubjectColor(parentColorHex: String, siblingIndex: Int): String {
        val raw = parentColorHex.removePrefix("#")
        val rgb = raw.toIntOrNull(16) ?: return parentColorHex
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF

        val level = siblingIndex / 2 + 1
        val amount = (0.12f + (level - 1) * 0.08f).coerceAtMost(0.44f)
        val lighten = siblingIndex % 2 == 1

        fun tone(component: Int): Int {
            val value = if (lighten) {
                component + (255 - component) * amount
            } else {
                component * (1f - amount)
            }
            return value.toInt().coerceIn(0, 255)
        }

        return "#%02X%02X%02X".format(tone(r), tone(g), tone(b))
    }

    // ---- 领域 Store：状态与动作各自归属，Repository 只做同名委托（UI 层零改动）----
    private val timerStore = TimerStore(scope = repoScope, dbProvider = { database })
    private val noteStore = NoteStore(scope = repoScope, dbProvider = { database })
    private val checkInStore = CheckInStore(scope = repoScope, dbProvider = { database })
    val achievementEvaluator = AchievementEvaluator()

    init {
        timerStore.onAchievementEvent = { event ->
            triggerAchievementEvaluation(event)
        }
        noteStore.onAchievementEvent = { event ->
            triggerAchievementEvaluation(event)
        }
        checkInStore.onAchievementEvent = { event ->
            triggerAchievementEvaluation(event)
        }
    }

    val focusSessions: StateFlow<List<FocusSession>> get() = timerStore.focusSessions
    val examSessions: StateFlow<List<ExamSession>> get() = timerStore.examSessions

    val noteEntries: StateFlow<List<NoteEntry>> get() = noteStore.noteEntries

    private val _aiAnalyses = MutableStateFlow<List<AiAnalysis>>(emptyList())
    val aiAnalyses: StateFlow<List<AiAnalysis>> = _aiAnalyses.asStateFlow()

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()
    private val settingsWriteMutex = Mutex()

    val checkIns: StateFlow<List<CheckIn>> get() = checkInStore.checkIns

    private val _unlockedAchievements = MutableStateFlow<Map<String, Long>>(emptyMap())
    val unlockedAchievements: StateFlow<Map<String, Long>> = _unlockedAchievements.asStateFlow()

    // Current running active session if any
    val activeFocus: StateFlow<FocusSession?> get() = timerStore.activeFocus

    // 由业务层（而不是 UI 层）宣告"刚刚完成了一次专注 / 一场模考"。
    // UI 只是这个事件的观察者：即使 App 当时不在前台，记录也已经落库。
    val lastCompletedFocus: StateFlow<FocusSession?> get() = timerStore.lastCompletedFocus
    val lastCompletedExam: StateFlow<ExamSession?> get() = timerStore.lastCompletedExam

    fun bindDatabase(db: YanjiDatabase) {
        if (database != null) return
        database = db

        // 活动会话持久化：使用 context.noBackupFilesDir，不进入云备份
        val ctx = appContext
        if (ctx != null) {
            timerStore.setDiskPersistence(FileTimerSessionPersistence(ctx, SystemMonotonicClock))
        }

        // 把"计时结束如何落库"交给业务层：前台 Service 与任何页面都可以调用它。
        ActiveSessionCoordinator.bind(timerStore.persistence)
        ActiveSessionCoordinator.restorePersisted { restored ->
            if (restored != null && ctx != null) FocusTimerService.restoreActive(ctx)
        }

        repoScope.launch {
            // 升级路径上从 v7 抢救出来的明文 API Key → Keystore 加密存储，随后删除明文文件。
            // 移入后台 IO 协程执行，避免在冷启动主线程阻塞读取 Keystore 与磁盘。
            val apiKey = withContext(Dispatchers.IO) {
                secretStore?.migrateLegacyApiKeyIfPresent()
                secretStore?.readAiApiKey().orEmpty()
            }
            cachedAiApiKey = apiKey
            _settings.update { it.copy(aiApiKey = apiKey) }

            // 只初始化「系统配置默认值」。规则与理由见 AppInitializer 的 KDoc：
            // 「没有学习记录」是合法业务状态，不能与「首次安装」混为一谈。
            AppInitializer(db.userSettingsDao()).initialize()
            // Collect real-time DB changes
            // NOTE: The DB is the source of truth. The in-memory StateFlows are a cache for
            // synchronous reads from the UI. We unconditionally overwrite the cache with the
            // latest DB snapshot. This means a user-initiated write that has not yet landed
            // in the DB may briefly be overwritten by an in-flight DB emission, but the DB
            // will re-emit once the write completes and the UI will converge. This is a
            // known tradeoff: simpler than a merge-by-id strategy, and matches the previous
            // behavior. The inconsistency window is sub-frame and only visible under
            // rapid parallel writes.
            // 专注/模考、日记的 DB 订阅由各自的 Store 负责
            timerStore.bind(db)
            noteStore.bind(db)
            launch {
                db.userSettingsDao().getSettings().collect { entity ->
                    if (entity != null) {
                        // 凭据不在 Room 中，读取时从 SecretStore 注入，保证 UI 拿到的 settings 是完整的。
                        _settings.value = entity.toDomainModel().copy(aiApiKey = cachedAiApiKey)
                    }
                    settingsHydrated.complete(Unit)
                }
            }
            checkInStore.bind(db)
            launch {
                // 学科的 DB 订阅：内存镜像与 StateFlow 同步刷新。
                // 表为空时不覆盖默认值——「用户删光学科」是合法状态，但首帧仍需有内容可展示。
                db.subjectDao().getAllFlow().collect { entities ->
                    if (entities.isNotEmpty()) {
                        val subjects = entities.map { it.toDomainModel() }
                        SubjectCatalog.replaceAll(subjects)
                        _subjects.value = SubjectCatalog.all
                    }
                    subjectsHydrated.complete(Unit)
                }
            }
            launch {
                db.achievementDao().getAllFlow().collect { entities ->
                    _unlockedAchievements.value = entities.associate { it.id to it.unlockedAt }
                }
            }
            launch {
                // AI 诊断报告：DB 为唯一事实来源，内存 StateFlow 仅作同步读缓存。
                // 冷启动时从这里恢复上一次报告，避免重启即丢。
                db.aiAnalysisDao().getAll().collect { entities ->
                    _aiAnalyses.value = entities.map { it.toDomainModel() }
                }
            }
            launch {
                reconcileAchievements()
            }
        }
    }

    /**
     * 开始一次专注。
     *
     * 除了建立 UI 会话，还会把会话登记到 [ActiveSessionCoordinator]：从此这次计时归业务层所有，
     * 即使 `FocusScreen` 被销毁、用户切到别的 Tab，倒计时结束时的落库也不会丢。
     *
     * @param taskId 这次专注在执行的「今日计划」id；不属于任何计划时传 null。结束时
     *   [observeTaskActualSeconds] 会把用时记回该计划。
     * @return 新建的会话，调用方需要把 [FocusSession.id] 传给前台 Service；
     *         若已有正在进行的专注或模考（两者互斥），返回 null 且不改变任何状态。
     */
    suspend fun startFocus(
        subjectId: String,
        subjectName: String,
        note: String,
        mode: String = FocusModes.COUNT_UP,
        taskId: String? = null
    ): FocusSession? = timerStore.startFocus(subjectId, subjectName, note, mode, taskId)

    /**
     * 登记一场模考到业务层，供前台 Service 完成时落库。
     * @return null 表示已有计时在跑（专注与模考互斥），本次启动被拒绝。
     */
    suspend fun startExamSession(
        subjectId: String,
        subjectName: String,
        plannedDurationSeconds: Long
    ): ExamSession? = timerStore.startExamSession(subjectId, subjectName, plannedDurationSeconds)

    /** UI 侧的暂停镜像；真实计时事实由前台 Service 的 [com.example.yanji.data.timer.TimerMachine] 维护。 */
    fun pauseFocus() = timerStore.pauseFocus()

    fun resumeFocus() = timerStore.resumeFocus()

    fun updateFocusDuration(seconds: Long) = timerStore.updateFocusDuration(seconds)

    /**
     * 用户放弃本次计时：不产生任何记录。
     * 与 [ActiveSessionCoordinator.cancel] 成对使用。
     */
    fun cancelFocus() = timerStore.cancelFocus()

    /** 用户放弃本场模考：不产生记录。 */
    fun abandonExam() = timerStore.abandonExam()

    fun acknowledgeCompletedFocus() = timerStore.acknowledgeCompletedFocus()

    fun acknowledgeCompletedExam() = timerStore.acknowledgeCompletedExam()

    fun getFocusSessionByIdFlow(id: String): Flow<FocusSession?> = timerStore.getFocusSessionByIdFlow(id)

    fun getExamSessionByIdFlow(id: String): Flow<ExamSession?> = timerStore.getExamSessionByIdFlow(id)

    fun observeTodayStudyDurationSeconds(): Flow<Long> {
        val db = database ?: return combine(focusSessions, examSessions) { f, e ->
            StudyStats.durationOnDay(f, e, System.currentTimeMillis())
        }
        val todayRange = YanjiTime.dayRange(YanjiTime.today())
        val focusFlow = db.focusSessionDao().observeTotalSeconds(todayRange.startInclusive, todayRange.endExclusive)
        val examFlow = db.examSessionDao().observeTotalSeconds(todayRange.startInclusive, todayRange.endExclusive)
        return combine(focusFlow, examFlow) { f, e -> f + e }
    }

    /** Data-module events are driven only by changes to the persisted source tables. */
    fun observeDataTableChanges(): Flow<Set<String>> =
        requireDatabase().invalidationTracker.createFlow(
            "study_tasks",
            "journal_entries",
            "focus_sessions",
            "exam_sessions",
            "user_settings",
            "subjects",
            emitInitialState = false
        )

    suspend fun getDailySnapshot(date: String): DailyStudySnapshot =
        DailySnapshotReader.read(requireDatabase(), date)

    suspend fun saveStudyTask(task: StudyTask) {
        requireDatabase().studyTaskDao().insert(StudyTaskEntity.fromDomainModel(task))
    }

    suspend fun updateStudyTaskDetails(id: String, title: String, plannedMinutes: Int): Boolean =
        requireDatabase().studyTaskDao()
            .updateDetails(id, title, plannedMinutes, System.currentTimeMillis()) > 0

    suspend fun setStudyTaskCompleted(id: String, completed: Boolean) {
        requireDatabase().studyTaskDao().setCompleted(id, completed, System.currentTimeMillis())
    }

    suspend fun deleteStudyTask(id: String) {
        requireDatabase().studyTaskDao().deleteById(id)
    }

    /**
     * 「今日计划」每条计划已投入的秒数（专注时段已落库的部分）。
     *
     * 一张 map 覆盖全部计划而不是按 id 逐条查：首页一次要用完整张表，逐条查会变成
     * N 次订阅。聚合由 SQLite 的 GROUP BY 完成，不把整表明细拉进内存。
     */
    fun observeTaskActualSeconds(): Flow<Map<String, Long>> =
        requireDatabase().focusSessionDao().observeTaskActualSeconds()
            .map { rows -> rows.associate { it.taskId to it.seconds } }

    suspend fun addFocusSession(session: FocusSession) = timerStore.addFocusSession(session)

    fun addExamSession(session: ExamSession) = timerStore.addExamSession(session)

    suspend fun deleteFocusSession(id: String) = timerStore.deleteFocusSession(id)

    suspend fun updateFocusSessionNote(id: String, note: String) = timerStore.updateFocusSessionNote(id, note)

    suspend fun deleteExamSession(id: String) = timerStore.deleteExamSession(id)

    suspend fun getFocusSessionByIdFromDb(id: String): FocusSessionEntity? = timerStore.getFocusSessionByIdFromDb(id)

    suspend fun getExamSessionByIdFromDb(id: String): ExamSessionEntity? = timerStore.getExamSessionByIdFromDb(id)

    /** Range-bounded statistics sources. These queries use the startTime indexes and never
     * materialize the full Focus/Exam history for week, month, or day views. */
    internal fun observeFocusSessionsInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<FocusSession>> = requireDatabase().focusSessionDao()
        .observeCompletedInRange(startInclusive, endExclusive)
        .map { rows -> rows.map(FocusSessionEntity::toDomainModel) }

    internal fun observeExamSessionsInRange(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<ExamSession>> = requireDatabase().examSessionDao()
        .observeCompletedInRange(startInclusive, endExclusive)
        .map { rows -> rows.map(ExamSessionEntity::toDomainModel) }

    internal fun observeFocusSubjectTotals(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<StudySubjectAggregateRow>> = requireDatabase().focusSessionDao()
        .observeSubjectTotals(startInclusive, endExclusive)

    internal fun observeExamSubjectTotals(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<StudySubjectAggregateRow>> = requireDatabase().examSessionDao()
        .observeSubjectTotals(startInclusive, endExclusive)

    internal fun observeStudyDuration(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<Long> = combine(
        requireDatabase().focusSessionDao().observeTotalSeconds(startInclusive, endExclusive),
        requireDatabase().examSessionDao().observeTotalSeconds(startInclusive, endExclusive)
    ) { focusSeconds, examSeconds -> focusSeconds + examSeconds }

    private fun requireDatabase(): YanjiDatabase =
        checkNotNull(database) { "YanjiRepository.init(context) must run before database queries" }

    /**
     * 保存随笔。**以 Room 为唯一事实来源**，不再维护"内存一份 + DB 一份"的双缓存。
     *
     * 修复了旧实现的两个问题：
     * 1. 旧代码把 `updatedAt` 只写进内存副本，却把**原始 entry** 写进数据库，
     *    导致同一条记录在内存与 DB 中 `updatedAt` 不一致。
     * 2. 旧代码按 `date || id` 匹配内存行、却按 `id` 覆盖写库。
     *
     * v15 起改为按 **id** 归一化（一天允许多篇随笔），规则见 [NoteStore.addOrUpdate]。
     */
    fun addOrUpdateNote(entry: NoteEntry) = noteStore.addOrUpdate(entry)

    /**
     * [addOrUpdateNote] 的**可等待**版本：返回时写入已经落库。
     *
     * 桥接层 `saveQuickNote` 依赖这个语义：promise 必须在 Room 行真实存在之后才 resolve，
     * 否则 JS 侧紧接着读取会漏掉刚保存的记录。UI 侧的 `saveNote` 不需要等待，继续用
     * [addOrUpdateNote]。
     */
    suspend fun addOrUpdateNoteAndAwait(entry: NoteEntry) = noteStore.addOrUpdateAndAwait(entry)

    /** 等待随笔收藏状态在 Room 中完成切换，不依赖内存缓存。 */
    suspend fun toggleNoteFavoriteAndAwait(id: String) = noteStore.toggleFavoriteAndAwait(id)

    /** 更新随笔正文（可等待），返回 false 表示 id 不存在。 */
    suspend fun updateNoteContentAndAwait(id: String, content: String) =
        noteStore.updateContentAndAwait(id, content)

    /** 更新疑点解决状态（可等待），返回 false 表示 id 不存在。 */
    suspend fun setNoteResolvedAndAwait(id: String, isResolved: Boolean) =
        noteStore.setResolvedAndAwait(id, isResolved)

    /**
     * 人工纠正随笔分类（可等待），返回 false 表示 id 不存在。
     * AI 的建议只是建议：分类的最终决定权在这里落库，切换时同步清空解决状态。
     */
    suspend fun setNoteCategoryAndAwait(id: String, category: String) =
        noteStore.setCategoryAndAwait(id, category)

    suspend fun deleteNoteAndAwait(id: String) = noteStore.deleteAndAwait(id)

    fun deleteNote(id: String) = noteStore.delete(id)

    /** 决策模型 API Key 读写（通过 SecretStore 加密存储在 Keystore） */
    fun saveDecisionApiKey(key: String): Boolean {
        val store = secretStore ?: return false
        return when (store.saveDecisionApiKey(key)) {
            SecretWriteResult.Saved,
            SecretWriteResult.Cleared -> true
            is SecretWriteResult.Failure -> false
        }
    }

    fun getDecisionApiKey(): String =
        secretStore?.readDecisionApiKey().orEmpty()

    fun clearDecisionApiKey(): Boolean {
        val store = secretStore ?: return false
        return when (store.clearDecisionApiKey()) {
            SecretWriteResult.Saved,
            SecretWriteResult.Cleared -> true
            is SecretWriteResult.Failure -> false
        }
    }

    fun hasDecisionApiKey(): Boolean =
        getDecisionApiKey().isNotBlank()

    /**
     * 保存用户设置。
     *
     * AI API Key 与其余字段**分离持久化**：Key 走 [SecretStore]（Keystore 加密 + no-backup 目录），
     * 其余字段走 Room。凭据因此不会随可云备份的学习数据库一起离开设备。
     */
    suspend fun updateSettings(update: (UserSettings) -> UserSettings): SettingsUpdateResult = withContext(Dispatchers.IO) {
        // Build each patch from the last committed state, including concurrent theme/settings writes.
        settingsWriteMutex.withLock {
            val db = requireDatabase()
            val current = db.userSettingsDao().getSettings().first()?.toDomainModel()
                ?.copy(aiApiKey = cachedAiApiKey) ?: _settings.value
            val newSettings = update(current)
            val apiKey = newSettings.aiApiKey.trim()
            if (apiKey != cachedAiApiKey) {
                // 先同步落盘凭据，再广播新状态，避免出现"UI 显示已保存、读回却为空"的中间态。
                val store = secretStore
                    ?: return@withLock SettingsUpdateResult.SecretUnavailable
                when (store.saveAiApiKey(apiKey)) {
                    SecretWriteResult.Saved,
                    SecretWriteResult.Cleared -> cachedAiApiKey = apiKey
                    is SecretWriteResult.Failure -> return@withLock SettingsUpdateResult.SecretUnavailable
                }
            }
            db.userSettingsDao().saveSettings(UserSettingsEntity.fromDomainModel(newSettings))
            _settings.value = newSettings.copy(aiApiKey = apiKey)
            SettingsUpdateResult.Saved
        }
    }

    // ==================================================================
    // 备份导出 / 导入
    //
    // 职责已抽出到 [com.example.yanji.data.backup.BackupStore]（阶段 2 拆分第一刀）。
    // 这里只保留**同名委托方法**，行为与拆分前完全一致。
    //
    // 关键约定（实现见 BackupStore，语义逐字未变）：
    //  - 导出内容**不含 AI API Key**（凭据不在 Room 里；UserSettingsBackup 结构上就没有这个字段）
    //  - 导入是「整表替换」，全部写操作在**单个事务**内完成；任一步失败则整体回滚
    //  - 导入前自动把当前数据快照到 filesDir/pre_import_snapshots/，出问题还能找回
    //  - 计时进行中拒绝导入，避免正在跑的会话与恢复后的数据打架
    // ==================================================================

    /**
     * 备份导出 / 导入的委托实现。
     *
     * 导入成功后校正学科镜像和活动计时状态。
     */
    private val backupStore by lazy {
        com.example.yanji.data.backup.BackupStore(
            dbProvider = { database },
            contextProvider = { appContext },
            onActiveFocusCleared = { timerStore.clearActiveFocus() },
            onSubjectsReplaced = { restored ->
                SubjectCatalog.replaceAll(restored)
                _subjects.value = SubjectCatalog.all
            },
            onSettingsReplaced = { restored ->
                _settings.value = restored.copy(aiApiKey = cachedAiApiKey)
            }
        )
    }

    /** 导出为 JSON 字符串。写文件（SAF）由 UI 层负责，这里只产出内容。 */
    suspend fun exportBackupJson(): String = backupStore.exportJson()

    /**
     * 从 JSON 导入并整表替换本机数据。
     *
     * @return 成功时带回落盘快照路径与来自 [BackupCodec] 的提醒；失败时 message 可直接展示给用户。
     */
    suspend fun importBackupJson(rawJson: String): BackupImportResult = backupStore.importJson(rawJson)

    internal fun parseAiDiagnostic(
        content: String,
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings
    ): AiAnalysis? = aiDiagnosticParser.parse(content, snapshot, settings)

    // Helper query computations (Unified Single Source of Truth)
    fun getTodayFocusDurationSeconds(): Long =
        StudyStats.durationOnDay(timerStore.focusSessions.value, timerStore.examSessions.value, System.currentTimeMillis())

    // CheckIn & Streak operations
    fun isCheckedInToday(): Boolean = checkInStore.isCheckedInToday()

    fun getTodayCheckIn(): CheckIn? = checkInStore.getTodayCheckIn()

    fun getCurrentStreak(): Int = checkInStore.getCurrentStreak()

    fun checkInToday(note: String = "", mood: String = ""): CheckIn =
        checkInStore.checkInToday(note, mood)

    fun getPast7DaysCheckInStatus(): List<DayCheckInStatus> = checkInStore.getPast7DaysCheckInStatus()

    suspend fun triggerAchievementEvaluation(event: AchievementEvent) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        val focus = focusSessions.value
        val exam = examSessions.value
        val notes = noteEntries.value
        val checkInList = checkIns.value
        val unlockedIds = _unlockedAchievements.value.keys

        val currentFocus = when (event) {
            is AchievementEvent.FocusCompleted -> {
                if (focus.any { it.id == event.session.id }) focus else listOf(event.session) + focus
            }
            else -> focus
        }
        val currentExam = when (event) {
            is AchievementEvent.ExamCompleted -> {
                if (exam.any { it.id == event.session.id }) exam else listOf(event.session) + exam
            }
            else -> exam
        }
        val currentNote = when (event) {
            is AchievementEvent.NoteCreated -> {
                if (notes.any { it.id == event.entry.id }) notes else listOf(event.entry) + notes
            }
            else -> notes
        }
        val currentCheckIns = when (event) {
            is AchievementEvent.CheckInRecorded -> {
                if (checkInList.any { it.date == event.checkIn.date }) checkInList else listOf(event.checkIn) + checkInList
            }
            else -> checkInList
        }

        val newlyUnlocked = achievementEvaluator.evaluateAndPersist(
            event = event,
            db = db,
            focusSessions = currentFocus,
            examSessions = currentExam,
            noteEntries = currentNote,
            checkIns = currentCheckIns,
            unlockedIds = unlockedIds
        )
        if (newlyUnlocked.isNotEmpty()) {
            val now = System.currentTimeMillis()
            _unlockedAchievements.value = _unlockedAchievements.value + newlyUnlocked.associateWith { now }
        }
    }

    suspend fun reconcileAchievements() = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        val focus = db.focusSessionDao().getAllOnce().map { it.toDomainModel() }
        val exam = db.examSessionDao().getAllOnce().map { it.toDomainModel() }
        val notes = db.noteEntryDao().getAllOnce().map { it.toDomainModel() }
        val checkInList = db.checkInDao().getAllOnce().map { it.toDomainModel() }
        val unlockedIds = db.achievementDao().getUnlockedIds().toSet()

        val newlyUnlocked = achievementEvaluator.evaluateAndPersist(
            event = AchievementEvent.ReconcileAll,
            db = db,
            focusSessions = focus,
            examSessions = exam,
            noteEntries = notes,
            checkIns = checkInList,
            unlockedIds = unlockedIds
        )
        if (newlyUnlocked.isNotEmpty()) {
            val now = System.currentTimeMillis()
            _unlockedAchievements.value = _unlockedAchievements.value + newlyUnlocked.associateWith { now }
        }
    }
}
