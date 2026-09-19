package com.example.yanji.data

import com.example.yanji.theme.MascotThemes

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.yanji.data.backup.BackupCodec
import com.example.yanji.data.backup.BackupDecodeResult
import com.example.yanji.data.backup.BackupImportResult
import com.example.yanji.data.achievement.AchievementCatalog
import com.example.yanji.data.achievement.AchievementDef
import com.example.yanji.data.achievement.AchievementEvaluator
import com.example.yanji.data.achievement.AchievementEvent
import com.example.yanji.data.ai.AiClient
import com.example.yanji.data.ai.ChatReplyState
import com.example.yanji.data.backup.BackupTransfer
import com.example.yanji.data.chat.ChatStore
import com.example.yanji.data.checkin.CheckInStore
import com.example.yanji.data.checkin.DayCheckInStatus
import com.example.yanji.data.journal.JournalStore
import com.example.yanji.data.study.StudyStats
import com.example.yanji.data.timer.TimerStore
import com.example.yanji.data.backup.UserSettingsBackup
import com.example.yanji.data.backup.YanjiBackup
import com.example.yanji.data.db.*
import com.example.yanji.data.security.KeystoreSecretStore
import com.example.yanji.data.security.SecretStore
import com.example.yanji.data.security.SecretWriteResult
import com.example.yanji.data.timer.ActiveSession
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.data.timer.FileTimerSessionPersistence
import com.example.yanji.data.timer.SystemMonotonicClock
import com.example.yanji.data.timer.TimerSessionPersistence
import com.example.yanji.service.FocusTimerService
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
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

    val defaultSubjects = SubjectCatalog.defaults

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
            colorHex = parent.colorHex,
            sortOrder = parent.sortOrder * 100 + siblings.size + 1,
            parentId = parentId
        )
        persistSubjects { it.insert(SubjectEntity.fromDomainModel(subject)) }
        return subject
    }

    /** 重命名学科（类别或子学科）。 */
    suspend fun renameSubject(subjectId: String, newName: String) {
        val existing = SubjectCatalog.find(subjectId) ?: return
        persistSubjects {
            it.insert(SubjectEntity.fromDomainModel(existing.copy(name = newName.trim())))
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
        val palette = listOf(
            "#356AE6", "#8B7CF6", "#2F9E6D", "#E67E22",
            "#B8426B", "#3B78B8", "#A95822", "#2F7F55"
        )
        val used = _subjects.value.filter { it.isCategory }.map { it.colorHex }.toSet()
        return palette.firstOrNull { it !in used } ?: palette[_subjects.value.size % palette.size]
    }

    // ---- 领域 Store：状态与动作各自归属，Repository 只做同名委托（UI 层零改动）----
    private val timerStore = TimerStore(scope = repoScope, dbProvider = { database })
    private val journalStore = JournalStore(scope = repoScope, dbProvider = { database })
    private val checkInStore = CheckInStore(scope = repoScope, dbProvider = { database })
    val achievementEvaluator = AchievementEvaluator()

    init {
        timerStore.onAchievementEvent = { event ->
            triggerAchievementEvaluation(event)
        }
        journalStore.onAchievementEvent = { event ->
            triggerAchievementEvaluation(event)
        }
        checkInStore.onAchievementEvent = { event ->
            triggerAchievementEvaluation(event)
        }
    }

    val focusSessions: StateFlow<List<FocusSession>> get() = timerStore.focusSessions
    val examSessions: StateFlow<List<ExamSession>> get() = timerStore.examSessions

    val journalEntries: StateFlow<List<JournalEntry>> get() = journalStore.journalEntries

    private val _aiAnalyses = MutableStateFlow<List<AiAnalysis>>(emptyList())
    val aiAnalyses: StateFlow<List<AiAnalysis>> = _aiAnalyses.asStateFlow()

    private val _availableAiModels = MutableStateFlow<List<String>>(emptyList())
    val availableAiModels: StateFlow<List<String>> = _availableAiModels.asStateFlow()

    fun setAvailableAiModels(models: List<String>) {
        _availableAiModels.value = models
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("yanji_ai_models", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        models.forEach { jsonArray.put(it) }
        prefs.edit { putString("cached_models", jsonArray.toString()) }
    }

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    // 聊天的状态与动作归 [ChatStore] 所有；这里只做同名委托，UI 层调用方式不变。
    // 注意构造顺序：chatStore 依赖 repoScope / aiClient / _settings，必须声明在它们之后。
    private val chatStore = ChatStore(
        scope = repoScope,
        dbProvider = { database },
        settingsProvider = { _settings.value },
        replyProvider = { message, model -> generateJuanjuanReply(message, model) },
        aiClient = aiClient
    )

    val chatSessions: StateFlow<List<ChatSession>> get() = chatStore.chatSessions
    val chatMessages: StateFlow<List<ChatMessage>> get() = chatStore.chatMessages
    val currentSessionId: StateFlow<String> get() = chatStore.currentSessionId
    val chatReplyStates: StateFlow<Map<String, ChatReplyState>> get() = chatStore.replyStates
    val hasMoreChatMessages: StateFlow<Boolean> get() = chatStore.hasMoreMessages

    val checkIns: StateFlow<List<CheckIn>> get() = checkInStore.checkIns

    private val _unlockedAchievements = MutableStateFlow<Map<String, Long>>(emptyMap())
    val unlockedAchievements: StateFlow<Map<String, Long>> = _unlockedAchievements.asStateFlow()

    private val _achievementUnlockChannel = kotlinx.coroutines.channels.Channel<AchievementDef>(capacity = kotlinx.coroutines.channels.Channel.BUFFERED)
    val achievementUnlockEvents: Flow<AchievementDef> = _achievementUnlockChannel.receiveAsFlow()

    fun emitCelebration(def: AchievementDef) {
        _achievementUnlockChannel.trySend(def)
    }

    // Current running active session if any
    val activeFocus: StateFlow<FocusSession?> get() = timerStore.activeFocus

    // 由业务层（而不是 Compose 页面）宣告"刚刚完成了一次专注 / 一场模考"。
    // UI 只是这个事件的观察者：即使页面当时没有组合，记录也已经落库。
    val lastCompletedFocus: StateFlow<FocusSession?> get() = timerStore.lastCompletedFocus
    val lastCompletedExam: StateFlow<ExamSession?> get() = timerStore.lastCompletedExam

    fun bindDatabase(db: YanjiDatabase) {
        if (database != null) return
        database = db

        // 升级路径上从 v7 抢救出来的明文 API Key → Keystore 加密存储，随后删除明文文件。
        // 必须在任何 settings 读取之前执行。
        secretStore?.migrateLegacyApiKeyIfPresent()
        cachedAiApiKey = secretStore?.readAiApiKey().orEmpty()

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
            journalStore.bind(db)
            launch {
                db.userSettingsDao().getSettings().collect { entity ->
                    if (entity != null) {
                        // 凭据不在 Room 中，读取时从 SecretStore 注入，保证 UI 拿到的 settings 是完整的。
                        _settings.value = entity.toDomainModel().copy(aiApiKey = cachedAiApiKey)
                    }
                }
            }
            // 聊天的 DB 订阅由 ChatStore 自己负责
            chatStore.bind(db)
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
                }
            }
            launch {
                db.achievementDao().getAllFlow().collect { entities ->
                    _unlockedAchievements.value = entities.associate { it.id to it.unlockedAt }
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
     * @return 新建的会话，调用方需要把 [FocusSession.id] 传给前台 Service；
     *         若已有正在进行的专注或模考（两者互斥），返回 null 且不改变任何状态。
     */
    suspend fun startFocus(
        subjectId: String,
        subjectName: String,
        note: String,
        mode: String = FocusModes.COUNT_UP
    ): FocusSession? = timerStore.startFocus(subjectId, subjectName, note, mode)

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
    fun pauseFocus(elapsedSeconds: Long = 0L) = timerStore.pauseFocus(elapsedSeconds)

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

    /**
     * 由 [ActiveSessionCoordinator] 调用的落库出口：专注完成。
     *
     * 数据只写 Room，内存列表交给 DAO 的 Flow 回灌，避免"内存一份缓存 + DB 一份"的双写不一致。
     */


    /** 由 [ActiveSessionCoordinator] 调用的落库出口：模考完成。 */


    /** 探测可用模型列表。协议与传输细节见 [com.example.yanji.data.ai.AiClient]。 */
    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): Result<List<String>> =
        runCatching { aiClient.fetchModels(baseUrl, apiKey) }



    suspend fun addFocusSession(session: FocusSession) = timerStore.addFocusSession(session)

    fun addExamSession(session: ExamSession) = timerStore.addExamSession(session)

    fun deleteFocusSession(id: String) = timerStore.deleteFocusSession(id)

    fun updateFocusSessionNote(id: String, note: String) = timerStore.updateFocusSessionNote(id, note)

    fun deleteExamSession(id: String) = timerStore.deleteExamSession(id)

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
     * 保存日记。**以 Room 为唯一事实来源**，不再维护"内存一份 + DB 一份"的双缓存。
     *
     * 修复了旧实现的两个问题：
     * 1. 旧代码把 `updatedAt` 只写进内存副本，却把**原始 entry** 写进数据库，
     *    导致同一条记录在内存与 DB 中 `updatedAt` 不一致。
     * 2. 旧代码按 `date || id` 匹配内存行、却按 `id` 覆盖写库；当传入的 entry 用了新 id
     *    但日期已存在时，会产生两条同日期日记。现在统一按日期归一化 id/createdAt。
     */
    /**
     * 保存日记。**以 Room 为唯一事实来源**；按日期归一化 id/createdAt 的规则见
     * [JournalStore.addOrUpdate]。
     */
    fun addOrUpdateJournal(entry: JournalEntry) = journalStore.addOrUpdate(entry)

    fun deleteJournal(id: String) = journalStore.delete(id)

    /**
     * 保存用户设置。
     *
     * AI API Key 与其余字段**分离持久化**：Key 走 [SecretStore]（Keystore 加密 + no-backup 目录），
     * 其余字段走 Room。凭据因此不会随可云备份的学习数据库一起离开设备。
     */
    fun updateSettings(newSettings: UserSettings): SettingsUpdateResult {
        val apiKey = newSettings.aiApiKey.trim()
        if (apiKey != cachedAiApiKey) {
            // 先同步落盘凭据，再广播新状态，避免出现"UI 显示已保存、读回却为空"的中间态。
            val store = secretStore
                ?: return SettingsUpdateResult.SecretUnavailable
            when (store.saveAiApiKey(apiKey)) {
                SecretWriteResult.Saved,
                SecretWriteResult.Cleared -> cachedAiApiKey = apiKey
                is SecretWriteResult.Failure -> return SettingsUpdateResult.SecretUnavailable
            }
        }
        _settings.value = newSettings.copy(aiApiKey = apiKey)
        repoScope.launch {
            database?.userSettingsDao()?.saveSettings(UserSettingsEntity.fromDomainModel(newSettings))
        }
        return SettingsUpdateResult.Saved
    }

    // ==================================================================
    // 备份导出 / 导入
    //
    // 关键约定：
    //  - 导出内容**不含 AI API Key**（凭据不在 Room 里；UserSettingsBackup 结构上就没有这个字段）
    //  - 导入是「整表替换」，全部写操作在**单个事务**内完成；任一步失败则整体回滚
    //  - 导入前自动把当前数据快照到 filesDir/pre_import_snapshots/，出问题还能找回
    //  - 计时进行中拒绝导入，避免正在跑的会话与恢复后的数据打架
    // ==================================================================

    /** 采集当前全部数据，生成可序列化的备份负载。实现见 [BackupTransfer.collect]。 */
    private suspend fun buildBackupPayload(): YanjiBackup {
        val db = database ?: return YanjiBackup(exportedAt = System.currentTimeMillis())
        return BackupTransfer.collect(db, appVersionName())
    }

    private fun appVersionName(): String = runCatching {
        val ctx = appContext ?: return@runCatching ""
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 导出为 JSON 字符串。写文件（SAF）由 UI 层负责，这里只产出内容。 */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        BackupCodec.encode(buildBackupPayload())
    }

    /**
     * 从 JSON 导入并整表替换本机数据。
     *
     * @return 成功时带回落盘快照路径与来自 [BackupCodec] 的提醒；失败时 message 可直接展示给用户。
     */
    suspend fun importBackupJson(rawJson: String): BackupImportResult = withContext(Dispatchers.IO) {
        if (ActiveSessionCoordinator.isBusy) {
            return@withContext BackupImportResult.Failure("正在计时中，请先结束当前的专注或模考再导入")
        }

        val decoded = BackupCodec.decode(rawJson)
        if (decoded is BackupDecodeResult.Failure) {
            return@withContext BackupImportResult.Failure(decoded.message)
        }
        val backup = (decoded as BackupDecodeResult.Success).backup

        val db = database
            ?: return@withContext BackupImportResult.Failure("数据库尚未初始化，请重启研迹后重试")

        // 先留一份「导入前」快照。即使导入事务回滚，这份快照也不受影响。
        val snapshotPath = runCatching { writePreImportSnapshot() }.getOrNull()

        val applied = runCatching {
            BackupTransfer.applyInTransaction(db, backup)
        }
        if (applied.isFailure) {
            val reason = applied.exceptionOrNull()?.localizedMessage ?: "未知错误"
            return@withContext BackupImportResult.Failure("导入失败，已回滚，本机数据未改变（$reason）")
        }

        // 会话列表被整体替换后，"当前会话"指针必须重新校正到一个真实存在的会话上
        chatStore.onSessionsReplaced()
        timerStore.clearActiveFocus()

        // 学科镜像也要立即刷新：导入是整表替换，若不在这里同步，紧接着的同步查询
        // （统计分桶、AI 提示词）会读到导入前的旧学科列表。
        val restoredSubjects = db.subjectDao().getAll().map { it.toDomainModel() }
        if (restoredSubjects.isNotEmpty()) {
            SubjectCatalog.replaceAll(restoredSubjects)
            _subjects.value = SubjectCatalog.all
        }

        BackupImportResult.Success(backup, snapshotPath, decoded.warnings)
    }

    private suspend fun writePreImportSnapshot(): String? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "pre_import_snapshots").apply { mkdirs() }
        val stamp = YanjiTime.backupStamp(Instant.now())
        val file = File(dir, "yanji-pre-import-$stamp.json")
        file.writeText(BackupCodec.encode(buildBackupPayload()))
        return file.absolutePath
    }

    suspend fun generateAiAnalysis(periodDays: Int = 7): AiAnalysis = withContext(Dispatchers.IO) {
        val settings = _settings.value
        if (!settings.isAiConfigured) {
            throw com.example.yanji.data.ai.AiException("尚未配置 AI 模型 API Key，无法生成学情诊断。请在右上角【设置】中配置你的 API 密钥。")
        }
        val db = database
        val safePeriodDays = periodDays.coerceIn(1, 90)
        val (focusList, examList) = if (db != null) {
            val now = System.currentTimeMillis()
            val endDay = YanjiTime.localDate(now)
            val startDay = endDay.minusDays((safePeriodDays - 1).toLong())
            val start = YanjiTime.dayRange(startDay).startInclusive
            val focus = db.focusSessionDao().getSessionsSince(start).map { it.toDomainModel() }
            val exams = db.examSessionDao().getSessionsSince(start).map { it.toDomainModel() }
            focus to exams
        } else {
            timerStore.focusSessions.value to timerStore.examSessions.value
        }

        val snapshot = StudyDiagnosticSnapshot.from(
            periodDays = periodDays,
            settings = settings,
            focusSessions = focusList,
            examSessions = examList,
            journalEntries = journalStore.journalEntries.value
        )
        if (snapshot.sessionCount == 0) {
            throw com.example.yanji.data.ai.AiException("本周期内暂无有效专注记录，无法生成阶段学情诊断。完成学习后再来诊断吧。")
        }
        val analysis = callAiDiagnosticApi(snapshot, settings)
        _aiAnalyses.value = listOf(analysis) + _aiAnalyses.value
        analysis
    }

    // ---------------------------------------------------------------- 聊天
    // 会话/消息的状态与动作都在 [ChatStore]；这里只做同名委托。

    fun createNewChatSession(initialModel: String? = null): String = chatStore.createNewChatSession(initialModel)

    fun switchChatSession(sessionId: String) = chatStore.switchChatSession(sessionId)

    fun deleteChatSession(sessionId: String) = chatStore.deleteChatSession(sessionId)

    fun updateSessionModel(sessionId: String, model: String) = chatStore.updateSessionModel(sessionId, model)

    fun sendChatMessage(text: String, model: String? = null, onFinished: () -> Unit = {}) =
        chatStore.sendChatMessage(text, model, onFinished)

    fun retryChatReply(sessionId: String, onFinished: () -> Unit = {}) =
        chatStore.retryFailedReply(sessionId, onFinished)

    fun cancelChatReply(sessionId: String = currentSessionId.value) =
        chatStore.cancelReply(sessionId)

    fun loadMoreChatMessages() = chatStore.loadMoreMessages()

    fun clearChatMessages() = chatStore.clearChatMessages()

    suspend fun generateJuanjuanReply(userMessage: ChatMessage, model: String? = null): String {
        val settings = _settings.value
        val targetModel = model ?: settings.aiModel

        if (settings.isAiConfigured) {
            return callAiApi(userMessage, settings, targetModel)
        }

        // When user has not configured API Key or custom backend
        val mascotName = MascotThemes.fromStorage(settings.mascotTheme).name
        return "【$mascotName 提示 · 尚未接入 AI】\n\n当前尚未配置大模型 API 密钥。\n\n👉 请点击右上角设置图标（⚙️），填入你的 API Key 并测试连接，即可开启与${mascotName}的实时在线伴学！"
    }

    private suspend fun callAiApi(
        userMessage: ChatMessage,
        settings: UserSettings,
        model: String = settings.aiModel
    ): String {
        // 历史消息与运行时上下文由业务层提供；AiClient 只负责传输与协议。
        val history = chatStore.currentMessages().toMutableList()
        if (history.lastOrNull()?.id != userMessage.id) history += userMessage

        val db = database
        val (focusList, examList) = if (db != null) {
            val sevenDaysAgo = YanjiTime.lastDaysRange(7).startInclusive
            val focus = db.focusSessionDao().getSessionsSince(sevenDaysAgo).map { it.toDomainModel() }
            val exams = db.examSessionDao().getSessionsSince(sevenDaysAgo).map { it.toDomainModel() }
            focus to exams
        } else {
            timerStore.focusSessions.value to timerStore.examSessions.value
        }

        return aiClient.completeChat(
            systemPrompt = JuanjuanPrompt.systemPrompt(MascotThemes.fromStorage(settings.mascotTheme).name),
            runtimeContext = JuanjuanPrompt.buildRuntimeContext(
                settings = settings,
                focusSessions = focusList,
                examSessions = examList,
                journalEntries = journalStore.journalEntries.value,
                activeFocus = timerStore.activeFocus.value
            ),
            history = history,
            settings = settings,
            model = model
        )
    }

    private suspend fun callAiDiagnosticApi(
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings
    ): AiAnalysis {
        val content = aiClient.diagnoseRaw(snapshot, settings)
        return parseAiDiagnostic(content, snapshot, settings)
            ?: throw IllegalArgumentException("AI diagnosis response is not valid JSON")
    }

    private fun parseAiDiagnostic(
        content: String,
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings
    ): AiAnalysis? = runCatching {
        val jsonStart = content.indexOf('{')
        val jsonEnd = content.lastIndexOf('}')
        require(jsonStart >= 0 && jsonEnd > jsonStart)
        val json = JSONObject(content.substring(jsonStart, jsonEnd + 1))
        fun stringList(key: String): List<String> = json.optJSONArray(key)?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).trim().takeIf { it.isNotEmpty() }
            }
        }.orEmpty()
        val overview = json.optString("overview").trim()
        val strengths = stringList("strengths").take(3)
        val weaknesses = stringList("weaknesses").take(3)
        val trend = json.optString("trendAnalysis").trim()
        val suggestions = stringList("threeDayPlan").take(3)
        require(overview.isNotBlank() && trend.isNotBlank() && suggestions.isNotEmpty())
        AiAnalysis(
            id = UUID.randomUUID().toString(),
            periodStart = snapshot.periodStart,
            periodEnd = snapshot.periodEnd,
            provider = settings.aiProvider,
            model = settings.aiModel,
            requestSnapshot = snapshot.requestSnapshot(),
            overview = overview,
            strengths = strengths.ifEmpty { listOf("本周期已形成 ${snapshot.activeDays} 天有效学习记录。") },
            weaknesses = weaknesses.ifEmpty { listOf("继续补充学习与复盘记录，诊断会更准确。") },
            trendAnalysis = trend,
            suggestions = suggestions
        )
    }.getOrNull()

    // Helper query computations (Unified Single Source of Truth)
    fun getTodayFocusDurationSeconds(): Long =
        StudyStats.durationOnDay(timerStore.focusSessions.value, timerStore.examSessions.value, System.currentTimeMillis())

    fun getStudyDurationForPeriod(days: Int): Long =
        StudyStats.durationSince(
            timerStore.focusSessions.value,
            timerStore.examSessions.value,
            System.currentTimeMillis() - days * 86400000L
        )

    fun getTotalStudyDurationSeconds(): Long =
        StudyStats.totalDuration(timerStore.focusSessions.value, timerStore.examSessions.value)

    fun getTodaySubjectDistribution(): Map<String, Long> =
        StudyStats.subjectDistributionOnDay(
            timerStore.focusSessions.value,
            timerStore.examSessions.value,
            System.currentTimeMillis()
        )

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
        val journal = journalEntries.value
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
        val currentJournal = when (event) {
            is AchievementEvent.JournalCreated -> {
                if (journal.any { it.id == event.entry.id }) journal else listOf(event.entry) + journal
            }
            else -> journal
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
            journalEntries = currentJournal,
            checkIns = currentCheckIns,
            unlockedIds = unlockedIds
        )
        if (newlyUnlocked.isNotEmpty()) {
            val now = System.currentTimeMillis()
            _unlockedAchievements.value = _unlockedAchievements.value + newlyUnlocked.associateWith { now }
            for (id in newlyUnlocked) {
                AchievementCatalog.find(id)?.let { def ->
                    _achievementUnlockChannel.trySend(def)
                }
            }
        }
    }

    suspend fun reconcileAchievements() = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        val focus = db.focusSessionDao().getAllOnce().map { it.toDomainModel() }
        val exam = db.examSessionDao().getAllOnce().map { it.toDomainModel() }
        val journal = db.journalEntryDao().getAllOnce().map { it.toDomainModel() }
        val checkInList = db.checkInDao().getAllOnce().map { it.toDomainModel() }
        val unlockedIds = db.achievementDao().getUnlockedIds().toSet()

        val newlyUnlocked = achievementEvaluator.evaluateAndPersist(
            event = AchievementEvent.ReconcileAll,
            db = db,
            focusSessions = focus,
            examSessions = exam,
            journalEntries = journal,
            checkIns = checkInList,
            unlockedIds = unlockedIds
        )
        if (newlyUnlocked.isNotEmpty()) {
            val now = System.currentTimeMillis()
            _unlockedAchievements.value = _unlockedAchievements.value + newlyUnlocked.associateWith { now }
        }
    }

    /** 解锁成就。幂等：已解锁的直接忽略。 */
    fun unlockAchievement(id: String) {
        val now = System.currentTimeMillis()
        if (_unlockedAchievements.value.containsKey(id)) return
        _unlockedAchievements.value = _unlockedAchievements.value + (id to now)
        repoScope.launch {
            database?.achievementDao()?.unlock(UnlockedAchievementEntity(id, now))
            AchievementCatalog.find(id)?.let { def ->
                _achievementUnlockChannel.trySend(def)
            }
        }
    }

    /**
     * 初始化成就系统与学习记录（用于用户开启全新备考旅程）。
     * 清空测试生成的专注、模考、打卡、日记、对话与成就解锁记录；
     * 严格保留目标院校、专业、倒计时与 AI Key 等基础设置。
     */
    suspend fun resetAchievementsAndStudyRecords() = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        ActiveSessionCoordinator.cancel()
        db.achievementDao().deleteAll()
        _unlockedAchievements.value = emptyMap()
        db.focusSessionDao().deleteAll()
        db.examSessionDao().deleteAll()
        db.journalEntryDao().deleteAll()
        db.checkInDao().deleteAll()
        db.chatMessageDao().clearAll()
        db.chatSessionDao().clearAll()
    }

    /**
     * 返回本次对话已关联的真实研迹学习数据来源。
     * 每一项都基于真实本地数据；无数据时返回空列表（UI 展示为「暂无关联记录」）。
     */
    fun currentContextSources(): List<ChatContextSource> {
        val sources = mutableListOf<ChatContextSource>()
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86400000L

        // 数学模考
        val mathExams = timerStore.examSessions.value
            .filter { it.score != null && SubjectCatalog.categoryIdOf(it.subjectId) == "math" && it.startTime >= sevenDaysAgo }
        if (mathExams.isNotEmpty()) {
            sources += ChatContextSource(
                ContextSourceType.MATH_EXAM,
                mathExams.size,
                "最近 ${mathExams.size} 次数学模考（最高 ${mathExams.mapNotNull { it.score }.maxOrNull()?.toInt() ?: 0} 分）"
            )
        }

        // 错题/学习记录（近 7 天 focus + exam note 含「错」+ journal 含「错」）
        val recentFocus = timerStore.focusSessions.value.filter { it.startTime >= sevenDaysAgo }
        val wrongNotesCount = recentFocus.count { it.note.contains("错") }
            + timerStore.examSessions.value.filter { it.startTime >= sevenDaysAgo }.count { it.note.contains("错") }
            + journalStore.journalEntries.value.filter { it.updatedAt >= sevenDaysAgo }.count { it.content.contains("错") }
        if (wrongNotesCount > 0) {
            sources += ChatContextSource(
                ContextSourceType.WRONG_NOTES,
                wrongNotesCount,
                "近 7 天错题/复盘记录 $wrongNotesCount 条"
            )
        }

        // 专注记录
        if (recentFocus.isNotEmpty()) {
            val hours = (recentFocus.sumOf { it.durationSeconds } / 3600.0)
                .let { String.format(Locale.US, "%.1f", it) }
            sources += ChatContextSource(
                ContextSourceType.FOCUS,
                recentFocus.size,
                "近 7 天专注 $hours 小时（共 ${recentFocus.size} 段）"
            )
        }

        // 当前对话（最后 8 条）
        val recentChat = chatStore.currentMessages().takeLast(8)
        if (recentChat.isNotEmpty()) {
            sources += ChatContextSource(
                ContextSourceType.CURRENT_CONVERSATION,
                recentChat.size,
                "本次对话最近 ${recentChat.size} 条消息"
            )
        }

        return sources
    }

    /**
     * 执行卷卷回复中嵌入的行动指令。
     * 返回 true 表示成功执行并已给用户 Toast 反馈（UI 层自行显示）。
     */
    fun executeAction(action: JuanjuanAction, context: android.content.Context): Boolean = when (action.type) {
        JuanjuanActionType.CREATE_PLAN -> {
            // 把动作写入今日/明日计划
            addPlanToJournalInternal(context, action.payload)
            true
        }
        JuanjuanActionType.SAVE_TO_JOURNAL -> {
            // 存入日记（内容即当前回复全文；这里需要调用方传完整文本）
            saveTipToJournalInternal(context, action.payload)
            true
        }
        JuanjuanActionType.START_FOCUS -> {
            // payload 格式 "subjectId|mode|note"
            val parts = action.payload.split("|")
            if (parts.size >= 2) {
                val subjectId = parts[0]
                val mode = parts[1]
                val note = parts.getOrNull(2) ?: ""
                val subject = SubjectCatalog.find(subjectId)
                if (subject != null) {
                    repoScope.launch { startFocus(subjectId, subject.name, note, mode) }
                    true
                } else {
                    false
                }
            } else false
        }
        JuanjuanActionType.OPEN_JOURNAL -> {
            // UI 跳转处理，Repository 仅标记意图
            true
        }
        JuanjuanActionType.OPEN_EXAM -> {
            true
        }
        JuanjuanActionType.SET_REMINDER -> {
            // 暂不实现，占位
            true
        }
        JuanjuanActionType.GENERATE_TEMPLATE -> {
            // payload 是模板 ID，UI 处理复制/下载
            true
        }
    }

    /**
     * Internal: save to journal (extracted from old saveTipToJournal).
     */
    private fun saveTipToJournalInternal(context: Context, content: String) {
        val todayStr = YanjiTime.todayIso()
        val existing = journalStore.journalEntries.value.firstOrNull { it.date == todayStr }
        val mascotName = MascotThemes.fromStorage(_settings.value.mascotTheme).name
        val appendText = "\n\n### ${mascotName}说考研方法锦囊\n$content"
        if (existing != null) {
            addOrUpdateJournal(existing.copy(content = existing.content + appendText))
        } else {
            addOrUpdateJournal(
                JournalEntry(
                    id = UUID.randomUUID().toString(),
                    date = todayStr,
                    title = "今日复盘与${mascotName}建议",
                    content = content,
                    tags = listOf("${mascotName}建议", "方法精练")
                )
            )
        }
        // Toast handled by caller
    }

    /**
     * Internal: add plan to journal (extracted from old addPlanToJournal).
     */
    private fun addPlanToJournalInternal(context: Context, planText: String) {
        val todayStr = YanjiTime.todayIso()
        val existing = journalStore.journalEntries.value.firstOrNull { it.date == todayStr }
        val cleanPlan = planText.replace("要将『", "").replace("』加为明早计划吗？", "").replace("？", "").trim()
        val planItem = "\n- [ ] 明早实践：$cleanPlan"
        if (existing != null) {
            addOrUpdateJournal(existing.copy(content = existing.content + planItem))
        } else {
            addOrUpdateJournal(
                JournalEntry(
                    id = UUID.randomUUID().toString(),
                    date = todayStr,
                    title = "今日复盘与明日计划",
                    content = "### 备考待办计划\n$planItem",
                    tags = listOf("待办计划")
                )
            )
        }
        // Toast handled by caller
    }
}
