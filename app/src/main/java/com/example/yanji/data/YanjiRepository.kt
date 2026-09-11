package com.example.yanji.data

import android.content.Context
import android.util.Log
import com.example.yanji.data.backup.BackupCodec
import com.example.yanji.data.backup.BackupDecodeResult
import com.example.yanji.data.backup.BackupImportResult
import com.example.yanji.data.ai.AiClient
import com.example.yanji.data.backup.BackupTransfer
import com.example.yanji.data.chat.ChatStore
import com.example.yanji.data.checkin.CheckInStore
import com.example.yanji.data.checkin.DayCheckInStatus
import com.example.yanji.data.journal.JournalStore
import com.example.yanji.data.preset.QuickStartPresetStore
import com.example.yanji.data.study.StudyStats
import com.example.yanji.data.timer.TimerStore
import com.example.yanji.data.backup.UserSettingsBackup
import com.example.yanji.data.backup.YanjiBackup
import com.example.yanji.data.db.*
import com.example.yanji.data.security.KeystoreSecretStore
import com.example.yanji.data.security.SecretStore
import com.example.yanji.data.timer.ActiveSession
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.data.timer.TimerSessionPersistence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class YanjiRepository private constructor() {

    companion object {
        @Volatile
        private var instance: YanjiRepository? = null
        private const val KEY_QUICK_ACTIONS_SEEDED = "quick_actions_seeded_v1"

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
            repo.seedDefaultQuickActionsIfNeeded()
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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val defaultSubjects = SubjectCatalog.all

    private val _subjects = MutableStateFlow(defaultSubjects)
    val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    fun addCustomSubject(name: String, color: String = "#356AE6"): Subject {
        val id = "custom_" + UUID.randomUUID().toString().take(8)
        val newSub = Subject(id, name, color, sortOrder = _subjects.value.size + 1)
        _subjects.value = _subjects.value + newSub
        return newSub
    }

    // ---- 领域 Store：状态与动作各自归属，Repository 只做同名委托（UI 层零改动）----
    private val timerStore = TimerStore(scope = repoScope, dbProvider = { database })
    private val journalStore = JournalStore(scope = repoScope, dbProvider = { database })
    private val checkInStore = CheckInStore(scope = repoScope, dbProvider = { database })
    private val presetStore = QuickStartPresetStore(scope = repoScope, dbProvider = { database })

    val focusSessions: StateFlow<List<FocusSession>> get() = timerStore.focusSessions
    val examSessions: StateFlow<List<ExamSession>> get() = timerStore.examSessions

    val journalEntries: StateFlow<List<JournalEntry>> get() = journalStore.journalEntries

    private val _aiAnalyses = MutableStateFlow<List<AiAnalysis>>(emptyList())
    val aiAnalyses: StateFlow<List<AiAnalysis>> = _aiAnalyses.asStateFlow()

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
    val isAiReplying: StateFlow<Boolean> get() = chatStore.isAiReplying

    val checkIns: StateFlow<List<CheckIn>> get() = checkInStore.checkIns

    private val _unlockedAchievements = MutableStateFlow<Map<String, Long>>(emptyMap())
    val unlockedAchievements: StateFlow<Map<String, Long>> = _unlockedAchievements.asStateFlow()

    // 首页自定义快捷操作（科目 + 计时模式 + 备注 组合）
    val quickStartPresets: StateFlow<List<QuickStartPreset>> get() = presetStore.quickStartPresets

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

        // 把"计时结束如何落库"交给业务层：前台 Service 与任何页面都可以调用它。
        ActiveSessionCoordinator.bind(timerStore.persistence)
        ActiveSessionCoordinator.restorePersisted()

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
                db.achievementDao().getAllFlow().collect { entities ->
                    _unlockedAchievements.value = entities.associate { it.id to it.unlockedAt }
                }
            }
            presetStore.bind(db)
        }
    }

    /**
     * 首次启动时把默认三个快捷操作（开始专注 / 模拟考试 / 写今日日记）写入 quick_start_presets，
     * 使它们与自定义组合走同一份存储，统一支持长按删除。
     * SharedPreferences 标志保证只 seed 一次：用户删掉默认项后不会被重新插入。
     */
    private fun seedDefaultQuickActionsIfNeeded() {
        val ctx = appContext ?: return
        repoScope.launch {
            val prefs = ctx.getSharedPreferences("yanji_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_QUICK_ACTIONS_SEEDED, false)) return@launch
            try {
                val dao = database?.quickStartPresetDao() ?: return@launch
                if (dao.count() == 0) {
                    val defaults = listOf(
                        QuickStartPreset(
                            type = QuickStartPreset.TYPE_START_FOCUS,
                            label = "开始专注",
                            subLabel = "高效计时",
                            sortOrder = 0
                        ),
                        QuickStartPreset(
                            type = QuickStartPreset.TYPE_EXAM,
                            label = "模拟考试",
                            subLabel = "全真计时",
                            sortOrder = 1
                        ),
                        QuickStartPreset(
                            type = QuickStartPreset.TYPE_JOURNAL,
                            label = "写今日日记",
                            subLabel = "复盘沉淀",
                            sortOrder = 2
                        )
                    )
                    defaults.forEach { dao.insert(QuickStartPresetEntity.fromDomainModel(it)) }
                }
                prefs.edit().putBoolean(KEY_QUICK_ACTIONS_SEEDED, true).apply()
            } catch (e: Exception) {
                // seed 失败不阻塞应用启动；下次启动会重试
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
    fun startFocus(
        subjectId: String,
        subjectName: String,
        note: String,
        mode: String = FocusModes.COUNT_UP
    ): FocusSession? = timerStore.startFocus(subjectId, subjectName, note, mode)

    /**
     * 登记一场模考到业务层，供前台 Service 完成时落库。
     * @return null 表示已有计时在跑（专注与模考互斥），本次启动被拒绝。
     */
    fun startExamSession(
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

    /**
     * 由 [ActiveSessionCoordinator] 调用的落库出口：专注完成。
     *
     * 数据只写 Room，内存列表交给 DAO 的 Flow 回灌，避免"内存一份缓存 + DB 一份"的双写不一致。
     */


    /** 由 [ActiveSessionCoordinator] 调用的落库出口：模考完成。 */


    /** 探测可用模型列表。协议与传输细节见 [com.example.yanji.data.ai.AiClient]。 */
    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): Result<List<String>> =
        runCatching { aiClient.fetchModels(baseUrl, apiKey) }



    fun addExamSession(session: ExamSession) = timerStore.addExamSession(session)

    fun deleteFocusSession(id: String) = timerStore.deleteFocusSession(id)

    fun updateFocusSessionNote(id: String, note: String) = timerStore.updateFocusSessionNote(id, note)

    fun deleteExamSession(id: String) = timerStore.deleteExamSession(id)

    suspend fun getFocusSessionByIdFromDb(id: String): FocusSessionEntity? = timerStore.getFocusSessionByIdFromDb(id)

    suspend fun getExamSessionByIdFromDb(id: String): ExamSessionEntity? = timerStore.getExamSessionByIdFromDb(id)

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
    fun updateSettings(newSettings: UserSettings) {
        val apiKey = newSettings.aiApiKey.trim()
        if (apiKey != cachedAiApiKey) {
            // 先同步落盘凭据，再广播新状态，避免出现"UI 显示已保存、读回却为空"的中间态。
            secretStore?.saveAiApiKey(apiKey)
            cachedAiApiKey = apiKey
        }
        _settings.value = newSettings.copy(aiApiKey = apiKey)
        repoScope.launch {
            database?.userSettingsDao()?.saveSettings(UserSettingsEntity.fromDomainModel(newSettings))
        }
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

        BackupImportResult.Success(backup, snapshotPath, decoded.warnings)
    }

    private suspend fun writePreImportSnapshot(): String? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "pre_import_snapshots").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "yanji-pre-import-$stamp.json")
        file.writeText(BackupCodec.encode(buildBackupPayload()))
        return file.absolutePath
    }

    // === 首页自定义快捷操作 ===
    // 写入顺序：先更新内存 StateFlow（UI 立即响应），再落库；DB Flow 回灌时保持一致。
    fun addQuickStartPreset(preset: QuickStartPreset): QuickStartPreset = presetStore.add(preset)

    fun updateQuickStartPreset(preset: QuickStartPreset) = presetStore.update(preset)

    fun deleteQuickStartPreset(id: String) = presetStore.delete(id)

    /** 长按拖动排序：把 id 项移动到 toIndex 位置（内存即时生效，其余项顺移）。 */
    fun moveQuickStartPreset(id: String, toIndex: Int) = presetStore.move(id, toIndex)

    /** 拖动结束后把当前内存顺序持久化到 DB。 */
    fun commitQuickStartPresetOrder() = presetStore.commitOrder()

    suspend fun generateAiAnalysis(periodDays: Int = 7): AiAnalysis = withContext(Dispatchers.IO) {
        val settings = _settings.value
        val snapshot = StudyDiagnosticSnapshot.from(
            periodDays = periodDays,
            settings = settings,
            focusSessions = timerStore.focusSessions.value,
            examSessions = timerStore.examSessions.value,
            journalEntries = journalStore.journalEntries.value
        )
        val analysis = if (settings.aiApiKey.isNotBlank() && snapshot.sessionCount > 0) {
            runCatching { callAiDiagnosticApi(snapshot, settings) }
                .getOrNull()
                ?: buildLocalAiAnalysis(snapshot, settings)
        } else {
            buildLocalAiAnalysis(snapshot, settings)
        }
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

    fun clearChatMessages() = chatStore.clearChatMessages()

        private suspend fun generateJuanjuanReply(userMessage: ChatMessage, model: String = "deepseek-chat"): String {
        val query = userMessage.content
        val currentSettings = _settings.value
        val hasKey = currentSettings.aiApiKey.isNotBlank()
        val hasCustomUrl = currentSettings.aiBaseUrl.isNotBlank() && !currentSettings.aiBaseUrl.contains("api.deepseek.com")

        if (hasKey || hasCustomUrl) {
            try {
                Log.i("YanjiAI", "Requesting real AI backend: ${currentSettings.aiBaseUrl}, model: $model")
                return callAiApi(userMessage, currentSettings, model)
            } catch (e: Exception) {
                Log.e("YanjiAI", "Real AI backend call failed", e)
                val errorMsg = e.localizedMessage ?: e.message ?: "未知网络错误"
                return "【卷卷提醒 · AI 连接异常】\n\n未能从 AI 后端获取回复：$errorMsg\n\n📌 检查建议：\n1. 点击右上角「设置」图标（⚙️）核对 API Key 与 Base URL；\n2. 确保手机当前已连接可用 Wi-Fi 或移动数据；\n3. 确认大模型服务商账户额度是否充足。\n\n---\n以下是本地考研知识库建议：\n\n" + generateLocalFallbackReply(query)
            }
        }

        // When user has not configured API Key or custom backend
        return "【卷卷提醒 · 尚未配置 AI 后端】\n\n当前尚未配置大模型 API Key（已预置 DeepSeek 接口，兼容 OpenAI / 硅基流动 / 智谱等主流平台）。\n\n👉 请点击右上角设置图标（⚙️），填入你的 API Key 并测试连接，即可开启与卷卷的实时在线伴学！\n\n---\n以下是本地考研知识库建议：\n\n" + generateLocalFallbackReply(query)
    }

    private fun generateLocalFallbackReply(query: String): String {
        return when {
            query.contains("二次型") || query.contains("草稿") || query.contains("抄错") -> {
                // 真实数据片段：最近 7 天的模考分数概况（无数据时不显示数字）。
                val examStats = buildLocalExamStats()
                val evidence = if (examStats != null) {
                    "从你的描述来看，这是典型的过程性失分。结合$examStats，根因很可能是：草稿混乱 → 定位困难 → 转抄错误。"
                } else {
                    "从你的描述来看，这是典型的过程性失分。在没有更多模考分数佐证前，先不评判得分高低，根因很可能是：草稿混乱 → 定位困难 → 转抄错误。"
                }
                """
                抱抱你，别自责！

                $evidence

                1. **草稿纸十字四折法**：拿到大草稿纸立刻横竖两折，划分出 4 个象限并从 ① 到 ④ 标号。每一道解答大题严格只允许占用一个象限，杜绝“见缝插针”式心算。
                2. **特征值“迹和”双步秒核对**：求出特征多项式解出 λ 之后，务必花 5 秒口算验证：tr(A) = Σλ。如果不相等，不用往下抄答案，直接返回这一步纠错。
                3. **初等行变换宁写勿跳**：规范答题卡上多写一行行变换，绝不心算负号倍加。考场上慢半拍，卷面就是稳稳的 12 分。

                要将『草稿纸四分区』加为明早计划吗？
                """.trimIndent()
            }
            query.contains("数学") || query.contains("线代") || query.contains("高数") || query.contains("积分") -> {
                """
                做数学题遇到瓶颈太正常了，别慌。

                做真题和模拟卷时，遇到大题写不完往往是节奏被前置计算拖垮了：

                1. **拆解大题卡点**：如果是计算量卡住，说明是常规积分技巧未熟练；如果是完全没有思路，检查是否忽略了隐蔽条件或定理推论。
                2. **限时跳过原则**：在真实考场上，一道大题超过 12 分钟没有明确切入点，先做标记跳过，把能拿的基础分（选填60+）稳稳收下。
                3. **错题重做法则**：今天先放过它，明天早上精力最好的前 30 分钟，不看答案重新做一遍。

                要将『限时跳过原则』加为明早模考实践吗？
                """.trimIndent()
            }
            query.contains("408") || query.contains("专业课") || query.contains("数据结构") || query.contains("计网") -> {
                """
                别被 408 庞大的知识网吓倒，深呼吸！

                408 的四座大山之间有极强的内在联动逻辑：

                1. **建立物理-逻辑映射**：比如计组流水线和操作系统进程调度联系起来看，计网的各层封装在草稿纸上画一遍协议头。
                2. **真题代码白纸手写**：数据结构算法题不要只在脑海里想，务必在白纸上手写递归终止条件和指针边界操作。
                3. **抓大放小建立直觉**：选择题考查面极广，每天刷 20 道错题建立直觉，大题重点突破树、图、虚拟内存与TCP拥塞控制。

                要将『白纸手写算法』加为明日专业课复习计划吗？
                """.trimIndent()
            }
            query.contains("焦虑") || query.contains("受挫") || query.contains("错误率") || query.contains("慌") || query.contains("来不及") || query.contains("别人") -> {
                val todayHours = getTodayFocusDurationSeconds() / 3600.0f
                val todayText = if (todayHours > 0) "今天你已经专注 ${String.format("%.1f", todayHours)} 小时，" else "虽然今天还没有完整的专注记录，"
                """
                抱抱你。请把手放在胸口，先缓缓吐出一口气。

                ${todayText}请相信：研迹的轨迹不会骗人。

                1. **暴露问题即是得分**：现在的每一道错题，都是在为你扫清考场上的地雷，这是天大的好事。
                2. **专注微小确定性**：今天哪怕只弄懂了一个极限公式、记住了五个生词，那也是实打实刻进脑海的分数。
                3. **学时轨迹从不骗人**：研迹记录着你这些天踏踏实实的学时，这些轨迹是骗不了人的。

                放下手机，闭目养神 3 分钟，卷卷陪你专注当下的这一页。

                要将『早睡调适与心态复盘』加为今晚计划吗？
                """.trimIndent()
            }
            query.contains("英语") || query.contains("单词") || query.contains("阅读") -> {
                """
                英语的提升往往有明显的滞后效应，别因为近几篇阅读错得多就自我怀疑。

                1. **真题精读而非刷量**：一篇真题阅读，把每一个长难句的主谓宾切分清楚，比浮光掠影做三篇管用得多。
                2. **词汇语境化**：孤立背词容易遗忘，利用研迹的碎片时间，把错题里的核心动词和形容词摘录在今日日记标签里。
                3. **分析命题人套路**：错题选项是无中生有、偷换概念还是张冠李戴？搞清出题逻辑，准确率自然回归。
                """.trimIndent()
            }
            query.contains("政治") || query.contains("背") || query.contains("马原") -> {
                """
                政治复习讲究节奏感：

                • 当前阶段以客观选择题为主，理解马原哲学框架（唯物论、辩证法、认识论、唯物史观），不要死记硬背。
                • 毛中特和史纲结合时间轴去串联关键历史节点和主要矛盾。
                • 大题背诵可以放在冲刺阶段（考前1个月），现在的重点是把高频选择题考点彻底扫盲。
                """.trimIndent()
            }
            query.contains("时间") || query.contains("分析") || query.contains("进度") -> {
                val todayHours = getTodayFocusDurationSeconds() / 3600.0f
                val dist = getTodaySubjectDistribution()
                val distText = dist.entries.joinToString("，") { "${it.key} ${(it.value / 3600.0f).let { h -> String.format("%.1f", h) }}h" }
                val extraText = if (distText.isNotBlank()) "各科分布为：$distText。\n\n" else ""
                """
                为你盘点今天的学习情况：

                今天你已经累计专注了 ${String.format("%.1f", todayHours)} 小时！
                ${extraText}整体专注状态保持得很好。如果觉得疲惫，不妨停下来做一组伸展，或者写一篇简短的日记复盘今日心得。
                """.trimIndent()
            }
            else -> {
                """
                收到了你的心声。备考是一场独自穿越风雨的修行，但你并不是孤身一人。

                每一次遇到难题、每一次感到困倦时的咬牙坚持，都在为你积累破局的力量。只要今天的你比昨天多掌握一个考点，你就在无限接近梦想。

                如果需要更深度的全学科生成式辅导，可以在【设置】页面填入你的 DeepSeek / OpenAI API Key，卷卷就能为你做更强大的实时学术与解题推演啦！
                """.trimIndent()
            }
        }
    }

    /**
     * 构造一段「来自真实数据」的模考概况描述。**绝不编造任何分数或统计**。
     * 若无已记录模考，返回 null，调用方应改用「从你的描述来看…」。
     */
    private fun buildLocalExamStats(): String? {
        val recent = timerStore.examSessions.value
            .filter { it.score != null && SubjectCatalog.categoryIdOf(it.subjectId) == "math" }
            .sortedByDescending { it.startTime }
            .take(8)
        if (recent.isEmpty()) return null
        val scores = recent.mapNotNull { it.score }
        val avg = scores.average()
        val max = scores.max()
        val min = scores.min()
        val range = if (max - min < 0.5) "稳定在 ${max.toInt()} 分" else "${min.toInt()}～${max.toInt()} 分"
        return "你近 ${recent.size} 套数学模考成绩$range，平均 ${String.format("%.1f", avg)} 分"
    }

    private suspend fun callAiApi(
        userMessage: ChatMessage,
        settings: UserSettings,
        model: String = settings.aiModel
    ): String {
        // 历史消息与运行时上下文由业务层提供；AiClient 只负责传输与协议。
        val history = chatStore.currentMessages().takeLast(8).toMutableList()
        if (history.lastOrNull()?.id != userMessage.id) history += userMessage
        return aiClient.completeChat(
            systemPrompt = JuanjuanPrompt.SYSTEM_PROMPT,
            runtimeContext = JuanjuanPrompt.buildRuntimeContext(
                settings = settings,
                focusSessions = timerStore.focusSessions.value,
                examSessions = timerStore.examSessions.value,
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

    private fun buildLocalAiAnalysis(snapshot: StudyDiagnosticSnapshot, settings: UserSettings): AiAnalysis {
        val primarySubject = snapshot.subjectStats.firstOrNull()
        val weakestSubject = snapshot.subjectStats.lastOrNull()?.takeIf { snapshot.subjectStats.size > 1 }
        val strengths = buildList {
            if (snapshot.activeDays > 0) add("本周期有 ${snapshot.activeDays} 天达到有效学习门槛，学习记录已具备复盘基础。")
            primarySubject?.let { add("投入最多的科目是${it.name}（${formatHours(it.seconds)}），是当前主要复习重心。") }
            if (snapshot.recentExams.isNotEmpty()) add("已记录 ${snapshot.recentExams.size} 次近期模考，可持续用分数与错因校准复习。")
        }.ifEmpty { listOf("当前没有完成的专注记录，先完成一段有效计时后再诊断会更准确。") }
        val weaknesses = buildList {
            if (snapshot.goalDays < snapshot.periodDays) add("${snapshot.periodDays} 天中有 ${snapshot.periodDays - snapshot.goalDays} 天未达到 ${snapshot.dailyGoalHours} 小时目标，先关注节奏稳定性。")
            weakestSubject?.let { add("${it.name}占比为${formatDecimal(it.share * 100)}%，请结合报考科目权重确认是否需要补足。") }
            if (snapshot.recentExams.none { it.score != null }) add("近期模考缺少分数记录，暂时无法判断得分趋势与薄弱题型。")
        }.ifEmpty { listOf("暂未发现明显的时长风险；后续结合模考分数和日记错因继续校准。") }
        val dailyPlanHours = maxOf(1.0, snapshot.dailyGoalHours.toDouble())
        val firstAction = weakestSubject?.name ?: primarySubject?.name ?: "核心科目"
        return AiAnalysis(
            id = UUID.randomUUID().toString(),
            periodStart = snapshot.periodStart,
            periodEnd = snapshot.periodEnd,
            provider = "研迹本地诊断",
            model = "规则引擎",
            requestSnapshot = snapshot.requestSnapshot(),
            overview = "基于 ${snapshot.periodStart} 至 ${snapshot.periodEnd} 的 ${snapshot.sessionCount} 条完成专注记录：累计${formatHours(snapshot.totalSeconds)}，日均${formatDecimal(snapshot.averageDailyHours)}小时。以下结论仅来自已记录的数据。",
            strengths = strengths.take(3),
            weaknesses = weaknesses.take(3),
            trendAnalysis = if (snapshot.dailyHours.size >= 2 && snapshot.dailyHours.last() >= snapshot.dailyHours.first()) {
                "最近一天的记录时长不低于周期首日；仍需连续记录以判断稳定趋势。"
            } else {
                "本周期日学习时长存在波动；优先建立固定开始时间，再逐步提高有效学习时长。"
            },
            suggestions = listOf(
                "第 1 天：安排 ${formatDecimal(dailyPlanHours)} 小时有效专注，其中先给 $firstAction 留出一段 ${maxOf(60L, snapshot.longestSessionMinutes.coerceAtMost(120L))} 分钟的完整时段。",
                "第 2 天：完成一次 $firstAction 错题或真题复盘；结束后在日记写下 1 个具体卡点和明天的处理动作。",
                "第 3 天：按 ${formatDecimal(dailyPlanHours)} 小时目标学习，并补录一次模考/自测的分数、总分和主要失分原因。"
            )
        )
    }

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

    /** 解锁成就。幂等：已解锁的直接忽略。 */
    fun unlockAchievement(id: String) {
        val now = System.currentTimeMillis()
        if (_unlockedAchievements.value.containsKey(id)) return
        _unlockedAchievements.value = _unlockedAchievements.value + (id to now)
        repoScope.launch {
            database?.achievementDao()?.unlock(UnlockedAchievementEntity(id, now))
        }
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
            val hours = (recentFocus.sumOf { it.durationSeconds } / 3600.0).let { String.format("%.1f", it) }
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
                    startFocus(subjectId, subject.name, note, mode)
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
        else -> false
    }

    /**
     * Internal: save to journal (extracted from old saveTipToJournal).
     */
    private fun saveTipToJournalInternal(context: Context, content: String) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existing = journalStore.journalEntries.value.firstOrNull { it.date == todayStr }
        val appendText = "\n\n### 卷卷说考研方法锦囊\n$content"
        if (existing != null) {
            addOrUpdateJournal(existing.copy(content = existing.content + appendText))
        } else {
            addOrUpdateJournal(
                JournalEntry(
                    id = UUID.randomUUID().toString(),
                    date = todayStr,
                    title = "今日复盘与卷卷建议",
                    content = content,
                    tags = listOf("卷卷建议", "方法精练")
                )
            )
        }
        // Toast handled by caller
    }

    /**
     * Internal: add plan to journal (extracted from old addPlanToJournal).
     */
    private fun addPlanToJournalInternal(context: Context, planText: String) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
