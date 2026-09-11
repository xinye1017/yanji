package com.example.yanji.data.chat

import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.ChatSession
import com.example.yanji.data.UserSettings
import com.example.yanji.data.ai.AiClient
import com.example.yanji.data.db.ChatMessageEntity
import com.example.yanji.data.db.ChatSessionEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 聊天会话的状态与动作归属者。
 *
 * 从 `YanjiRepository` 里剥出来的那部分：会话列表 / 当前会话 / 消息缓存 / 「AI 正在回复」/
 * 会话增删改 / 发消息与标题生成。学习数据（专注、模考、日记）**不在这里**——
 * 组装 AI 上下文是业务层的事，本类通过 [replyProvider] 拿到最终回复。
 *
 * Repository 仍然以同名属性/方法向外委托，UI 层零改动。
 *
 * @param dbProvider 数据库，可能在 bind 之前为 null
 * @param settingsProvider 当前用户设置
 * @param replyProvider 生成卷卷回复（含"没配 Key 走本地知识库"的兜底决策）
 */
internal class ChatStore(
    private val scope: CoroutineScope,
    private val dbProvider: () -> YanjiDatabase?,
    private val settingsProvider: () -> UserSettings,
    private val replyProvider: suspend (ChatMessage, String) -> String,
    private val aiClient: AiClient
) {

    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    /** 全量消息缓存。当前会话的展示列表由它派生。 */
    private val _allChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiReplying = MutableStateFlow(false)
    val isAiReplying: StateFlow<Boolean> = _isAiReplying.asStateFlow()

    /** 供业务层组装「本次对话引用了哪些数据来源」。 */
    fun currentMessages(): List<ChatMessage> = _chatMessages.value

    // ------------------------------------------------------------ 绑定

    /**
     * 订阅数据库。DB 是唯一事实来源，这里的内存列表只是同步读缓存。
     */
    fun bind(db: YanjiDatabase) {
        scope.launch {
            db.chatSessionDao().getAll().collect { entities ->
                _chatSessions.value = entities.map { it.toDomainModel() }
                ensureCurrentSessionIsValid()
            }
        }
        scope.launch {
            db.chatMessageDao().getAll().collect { entities ->
                val messages = entities.map { it.toDomainModel() }
                _allChatMessages.value = messages
                val activeId = _currentSessionId.value
                _chatMessages.value = messages.filter { it.sessionId == activeId }
            }
        }
    }

    /**
     * 把"当前会话"校正到一个真实存在的会话上。
     *
     * 旧实现只在 `currentSessionId` 为空时才挑一个，于是备份导入（会话列表被整体替换）
     * 或旧数据里残留的 `session-initial` 都会让 UI 停在一个**不存在**的会话上，
     * 看起来像"聊天记录全没了"。
     */
    fun ensureCurrentSessionIsValid() {
        val sessions = _chatSessions.value
        val current = _currentSessionId.value
        if (current.isBlank() || sessions.none { it.id == current }) {
            _currentSessionId.value = sessions.firstOrNull()?.id.orEmpty()
            _chatMessages.value = _allChatMessages.value.filter { it.sessionId == _currentSessionId.value }
        }
    }

    // ------------------------------------------------------------ 会话

    fun createNewChatSession(initialModel: String? = null): String {
        val newId = UUID.randomUUID().toString()
        val model = initialModel ?: settingsProvider().aiModel.ifBlank { "deepseek-chat" }
        val now = System.currentTimeMillis()
        val newSession = ChatSession(
            id = newId,
            title = "新对话",
            createdAt = now,
            updatedAt = now,
            model = model
        )
        val greetingMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = newId,
            sender = ChatSender.JUANJUAN,
            content = GREETING,
            timestamp = now
        )
        _chatSessions.value = listOf(newSession) + _chatSessions.value.filter { it.id != newId }
        _currentSessionId.value = newId
        _allChatMessages.value = _allChatMessages.value + greetingMsg
        _chatMessages.value = listOf(greetingMsg)

        scope.launch {
            val db = dbProvider() ?: return@launch
            db.chatSessionDao().insert(ChatSessionEntity.fromDomainModel(newSession))
            db.chatMessageDao().insert(ChatMessageEntity.fromDomainModel(greetingMsg))
        }
        return newId
    }

    fun switchChatSession(sessionId: String) {
        val target = _chatSessions.value.find { it.id == sessionId } ?: return
        _currentSessionId.value = target.id
        _chatMessages.value = _allChatMessages.value.filter { it.sessionId == target.id }
    }

    fun deleteChatSession(sessionId: String) {
        val remaining = _chatSessions.value.filter { it.id != sessionId }
        _chatSessions.value = remaining
        _allChatMessages.value = _allChatMessages.value.filter { it.sessionId != sessionId }

        scope.launch {
            dbProvider()?.let { db ->
                db.chatSessionDao().delete(sessionId)
                db.chatMessageDao().deleteBySessionId(sessionId)
            }
        }

        if (_currentSessionId.value == sessionId) {
            if (remaining.isNotEmpty()) {
                switchChatSession(remaining.first().id)
            } else {
                createNewChatSession()
            }
        }
    }

    fun updateSessionModel(sessionId: String, model: String) {
        _chatSessions.value = _chatSessions.value.map {
            if (it.id == sessionId) it.copy(model = model, updatedAt = System.currentTimeMillis()) else it
        }
        scope.launch {
            dbProvider()?.chatSessionDao()?.updateModel(sessionId, model, System.currentTimeMillis())
        }
    }

    /** 备份导入后由业务层调用：会话列表被整体替换，当前会话指针必须重新校正。 */
    fun onSessionsReplaced() {
        ensureCurrentSessionIsValid()
        _chatMessages.value = _allChatMessages.value.filter { it.sessionId == _currentSessionId.value }
    }

    // ------------------------------------------------------------ 消息

    fun sendChatMessage(text: String, model: String? = null, onFinished: () -> Unit = {}) {
        if (text.isBlank()) return
        var currSessionId = _currentSessionId.value
        if (currSessionId.isBlank() || _chatSessions.value.none { it.id == currSessionId }) {
            currSessionId = createNewChatSession(model)
        }

        val session = _chatSessions.value.find { it.id == currSessionId }
        val activeModel = model ?: session?.model ?: settingsProvider().aiModel.ifBlank { "deepseek-chat" }

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = currSessionId,
            sender = ChatSender.USER,
            content = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        _allChatMessages.value = _allChatMessages.value + userMsg
        _chatMessages.value = _chatMessages.value + userMsg

        scope.launch {
            dbProvider()?.chatMessageDao()?.insert(ChatMessageEntity.fromDomainModel(userMsg))
        }

        _isAiReplying.value = true
        scope.launch {
            try {
                val replyContent = replyProvider(userMsg, activeModel)
                val juanjuanMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = currSessionId,
                    sender = ChatSender.JUANJUAN,
                    content = replyContent,
                    timestamp = System.currentTimeMillis()
                )
                _allChatMessages.value = _allChatMessages.value + juanjuanMsg
                dbProvider()?.chatMessageDao()?.insert(ChatMessageEntity.fromDomainModel(juanjuanMsg))
                // 只在"用户仍停在这个会话"时刷新 UI buffer。
                // 无条件追加会让在途回复短暂出现在别的会话里，直到下一次 DB emission 才校正。
                if (_currentSessionId.value == currSessionId) {
                    _chatMessages.value = _allChatMessages.value.filter { it.sessionId == currSessionId }
                }

                val now = System.currentTimeMillis()
                _chatSessions.value = _chatSessions.value.map {
                    if (it.id == currSessionId) it.copy(updatedAt = now, model = activeModel) else it
                }
                dbProvider()?.chatSessionDao()?.updateModel(currSessionId, activeModel, now)

                // 首轮问答之后生成会话标题
                val currentSession = _chatSessions.value.find { it.id == currSessionId }
                val userMsgCountInSession =
                    _allChatMessages.value.count { it.sessionId == currSessionId && it.sender == ChatSender.USER }
                if (currentSession != null &&
                    (currentSession.title == "新对话" || currentSession.title.isBlank()) &&
                    userMsgCountInSession == 1
                ) {
                    generateSessionTitle(currSessionId, userMsg.content, replyContent, activeModel)
                }
            } catch (e: Exception) {
                // 回复失败时用户至少要能看到"出错了"这个事实，而不是界面静默卡在"回复中"。
                android.util.Log.e("YanjiAI", "生成卷卷回复失败", e)
            } finally {
                _isAiReplying.value = false
                withContext(Dispatchers.Main) {
                    onFinished()
                }
            }
        }
    }

    fun clearChatMessages() {
        val activeId = _currentSessionId.value
        scope.launch {
            val db = dbProvider() ?: return@launch
            db.chatMessageDao().deleteBySessionId(activeId)
            val greeting = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = activeId,
                sender = ChatSender.JUANJUAN,
                content = "当前对话已清空。我是卷卷，我们随时可以开启新的对话。",
                timestamp = System.currentTimeMillis()
            )
            _allChatMessages.value = _allChatMessages.value.filter { it.sessionId != activeId } + greeting
            _chatMessages.value = listOf(greeting)
            db.chatMessageDao().insert(ChatMessageEntity.fromDomainModel(greeting))
        }
    }

    // ------------------------------------------------------------ 标题

    private suspend fun generateSessionTitle(
        sessionId: String,
        userQuery: String,
        assistantReply: String,
        model: String
    ) {
        val settings = settingsProvider()
        var generatedTitle = ""
        if (settings.aiApiKey.isNotBlank()) {
            generatedTitle = runCatching { aiClient.generateTitle(userQuery, assistantReply, settings, model) }
                .getOrDefault("")
        }
        if (generatedTitle.isBlank()) {
            generatedTitle = localTitle(userQuery)
        }

        val cleanTitle = generatedTitle
            .replace(Regex("""[《》"“”、，。！？!?,.\s#*：:]"""), "")
            .trim()
            .take(10)
            .ifBlank { "考研复习对话" }

        _chatSessions.value = _chatSessions.value.map { s ->
            if (s.id == sessionId) s.copy(title = cleanTitle, updatedAt = System.currentTimeMillis()) else s
        }
        scope.launch {
            dbProvider()?.chatSessionDao()?.updateTitle(sessionId, cleanTitle, System.currentTimeMillis())
        }
    }

    /** 没配 AI Key 时的本地标题兜底。规则保持与抽取前一致。 */
    private fun localTitle(userQuery: String): String {
        val clean = userQuery.replace(Regex("""[^\u4e00-\u9fa5a-zA-Z0-9]"""), " ").trim()
        val candidate = when {
            clean.contains("草稿") || clean.contains("二次型") -> "二次型草稿防错"
            clean.contains("408") || clean.contains("知识点") -> "408知识点串联"
            clean.contains("大题") && clean.contains("做不完") -> "数学大题提速策略"
            clean.contains("受挫") || clean.contains("焦虑") -> "做题受挫心态调整"
            clean.contains("英语") || clean.contains("阅读") -> "英语真题阅读精读"
            clean.contains("政治") || clean.contains("大题") -> "政治大题背诵技巧"
            clean.length in 3..10 -> clean
            clean.length > 10 -> clean.take(10)
            else -> "考研复习对话"
        }
        return candidate.take(10)
    }

    private companion object {
        const val GREETING =
            "嗨！我是卷卷，你的考研全科专属学伴。\n\n无论遇到攻克不下的难题卡点、做题受挫时的烦躁，" +
                "还是单纯想找人说说话，我都在这里随时陪着你。今天想聊点什么呢？"
    }
}
