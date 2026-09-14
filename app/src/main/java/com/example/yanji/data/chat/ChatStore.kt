package com.example.yanji.data.chat

import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.ChatSession
import com.example.yanji.data.UserSettings
import com.example.yanji.data.ai.AiClient
import com.example.yanji.data.ai.AiFailure
import com.example.yanji.data.ai.ChatReplyState
import com.example.yanji.data.db.ChatMessageEntity
import com.example.yanji.data.db.ChatSessionEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Session-scoped chat state and persistence.
 *
 * Only the current session's newest [DEFAULT_PAGE_SIZE] messages are observed initially. Loading
 * older pages increases that bounded query; the app never subscribes to the whole message table.
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

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _hasMoreMessages = MutableStateFlow(false)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages.asStateFlow()

    /** Reply progress/failure is keyed by session so switching cannot leak another session's UI. */
    private val _replyStates = MutableStateFlow<Map<String, ChatReplyState>>(emptyMap())
    val replyStates: StateFlow<Map<String, ChatReplyState>> = _replyStates.asStateFlow()

    private val messageLimit = MutableStateFlow(DEFAULT_PAGE_SIZE)
    private val pendingRetries = mutableMapOf<String, PendingReply>()
    private val replyJobs = mutableMapOf<String, Job>()
    private var sessionJob: Job? = null
    private var bindingJob: Job? = null

    /** Only the bounded current page is sent to the prompt builder (which takes the last eight). */
    fun currentMessages(): List<ChatMessage> = _chatMessages.value

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun bind(db: YanjiDatabase) {
        if (bindingJob != null) return
        sessionJob = scope.launch {
            db.chatSessionDao().getAll().collect { entities ->
                _chatSessions.value = entities.map { it.toDomainModel() }
                ensureCurrentSessionIsValid()
            }
        }
        bindingJob = scope.launch {
            combine(_currentSessionId, messageLimit) { sessionId, limit -> sessionId to limit }
                .flatMapLatest { (sessionId, limit) ->
                    if (sessionId.isBlank()) {
                        flowOf(MessagePage(emptyList(), 0))
                    } else {
                        combine(
                            db.chatMessageDao().observeRecentBySessionId(sessionId, limit),
                            db.chatMessageDao().observeCountBySessionId(sessionId)
                        ) { rows, total -> MessagePage(rows, total) }
                    }
                }
                .collect { page ->
                    _chatMessages.value = page.rows.asReversed().map { it.toDomainModel() }
                    _hasMoreMessages.value = page.total > page.rows.size
                }
        }
    }

    fun close() {
        synchronized(replyJobs) {
            replyJobs.values.forEach { it.cancel() }
            replyJobs.clear()
        }
        sessionJob?.cancel()
        sessionJob = null
        bindingJob?.cancel()
        bindingJob = null
    }

    fun ensureCurrentSessionIsValid() {
        val sessions = _chatSessions.value
        val current = _currentSessionId.value
        if (current.isBlank() || sessions.none { it.id == current }) {
            _currentSessionId.value = sessions.firstOrNull()?.id.orEmpty()
            messageLimit.value = DEFAULT_PAGE_SIZE
        }
    }

    fun createNewChatSession(initialModel: String? = null): String {
        val newId = UUID.randomUUID().toString()
        val model = initialModel ?: settingsProvider().aiModel.ifBlank { "deepseek-chat" }
        val now = System.currentTimeMillis()
        val newSession = ChatSession(newId, "新对话", now, now, model)
        val greeting = ChatMessage(
            UUID.randomUUID().toString(), newId, ChatSender.JUANJUAN, GREETING, now
        )

        _chatSessions.value = listOf(newSession) + _chatSessions.value.filter { it.id != newId }
        _currentSessionId.value = newId
        messageLimit.value = DEFAULT_PAGE_SIZE
        _chatMessages.value = listOf(greeting)
        _hasMoreMessages.value = false

        scope.launch {
            val db = dbProvider() ?: return@launch
            db.chatSessionDao().insert(ChatSessionEntity.fromDomainModel(newSession))
            db.chatMessageDao().insert(ChatMessageEntity.fromDomainModel(greeting))
        }
        return newId
    }

    fun switchChatSession(sessionId: String) {
        if (_chatSessions.value.none { it.id == sessionId }) return
        val previousSessionId = _currentSessionId.value
        if (previousSessionId.isNotBlank() && previousSessionId != sessionId) {
            cancelReply(previousSessionId)
        }
        messageLimit.value = DEFAULT_PAGE_SIZE
        _currentSessionId.value = sessionId
    }

    fun loadMoreMessages() {
        if (_hasMoreMessages.value) messageLimit.value += PAGE_SIZE
    }

    fun deleteChatSession(sessionId: String) {
        cancelReply(sessionId)
        val remaining = _chatSessions.value.filter { it.id != sessionId }
        _chatSessions.value = remaining
        synchronized(pendingRetries) { pendingRetries.remove(sessionId) }
        _replyStates.update { it - sessionId }
        scope.launch {
            dbProvider()?.let { db ->
                db.chatSessionDao().delete(sessionId)
                db.chatMessageDao().deleteBySessionId(sessionId)
            }
        }
        if (_currentSessionId.value == sessionId) {
            if (remaining.isNotEmpty()) switchChatSession(remaining.first().id) else createNewChatSession()
        }
    }

    fun updateSessionModel(sessionId: String, model: String) {
        val now = System.currentTimeMillis()
        _chatSessions.value = _chatSessions.value.map {
            if (it.id == sessionId) it.copy(model = model, updatedAt = now) else it
        }
        scope.launch { dbProvider()?.chatSessionDao()?.updateModel(sessionId, model, now) }
    }

    fun onSessionsReplaced() {
        ensureCurrentSessionIsValid()
        messageLimit.value = DEFAULT_PAGE_SIZE
    }

    fun sendChatMessage(text: String, model: String? = null, onFinished: () -> Unit = {}) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        var sessionId = _currentSessionId.value
        if (sessionId.isBlank() || _chatSessions.value.none { it.id == sessionId }) {
            sessionId = createNewChatSession(model)
        }
        if (_replyStates.value[sessionId]?.isReplying == true) return

        val session = _chatSessions.value.find { it.id == sessionId }
        val activeModel = model ?: session?.model ?: settingsProvider().aiModel.ifBlank { "deepseek-chat" }
        val userMessage = ChatMessage(
            UUID.randomUUID().toString(), sessionId, ChatSender.USER, cleanText, System.currentTimeMillis()
        )
        if (_currentSessionId.value == sessionId) _chatMessages.value = _chatMessages.value + userMessage
        requestReply(userMessage, activeModel, insertUserMessage = true, onFinished)
    }

    /** Retry the failed request without inserting the original user message a second time. */
    fun retryFailedReply(sessionId: String = _currentSessionId.value, onFinished: () -> Unit = {}) {
        val pending = synchronized(pendingRetries) { pendingRetries[sessionId] } ?: return
        if (_replyStates.value[sessionId]?.isReplying == true) return
        requestReply(pending.userMessage, pending.model, insertUserMessage = false, onFinished)
    }

    /** Cancels the socket-owning coroutine without turning cancellation into a network error. */
    fun cancelReply(sessionId: String = _currentSessionId.value) {
        if (sessionId.isBlank()) return
        val job = synchronized(replyJobs) { replyJobs.remove(sessionId) } ?: return
        job.cancel()
        setReplyState(sessionId, ChatReplyState(failure = AiFailure.Cancelled))
    }

    private fun requestReply(
        userMessage: ChatMessage,
        model: String,
        insertUserMessage: Boolean,
        onFinished: () -> Unit
    ) {
        val sessionId = userMessage.sessionId
        synchronized(pendingRetries) { pendingRetries[sessionId] = PendingReply(userMessage, model) }
        setReplyState(sessionId, ChatReplyState(isReplying = true))

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val db = dbProvider() ?: error("Database not available")
                if (insertUserMessage) db.chatMessageDao().insert(ChatMessageEntity.fromDomainModel(userMessage))

                val replyContent = replyProvider(userMessage, model)
                val reply = ChatMessage(
                    UUID.randomUUID().toString(), sessionId, ChatSender.JUANJUAN,
                    replyContent, System.currentTimeMillis()
                )
                db.chatMessageDao().insert(ChatMessageEntity.fromDomainModel(reply))
                if (_currentSessionId.value == sessionId && _chatMessages.value.none { it.id == reply.id }) {
                    _chatMessages.value = (_chatMessages.value + reply).takeLast(messageLimit.value)
                }

                val now = System.currentTimeMillis()
                _chatSessions.value = _chatSessions.value.map {
                    if (it.id == sessionId) it.copy(updatedAt = now, model = model) else it
                }
                db.chatSessionDao().updateModel(sessionId, model, now)

                val currentSession = _chatSessions.value.find { it.id == sessionId }
                if (
                    currentSession != null &&
                    (currentSession.title == "新对话" || currentSession.title.isBlank()) &&
                    db.chatMessageDao().countUserMessages(sessionId) == 1
                ) {
                    generateSessionTitle(sessionId, userMessage.content, replyContent, model)
                }

                synchronized(pendingRetries) { pendingRetries.remove(sessionId) }
                setReplyState(sessionId, ChatReplyState())
            } catch (cancelled: CancellationException) {
                setReplyState(sessionId, ChatReplyState(failure = AiFailure.Cancelled))
                throw cancelled
            } catch (error: Throwable) {
                runCatching { android.util.Log.e("YanjiAI", "AI reply failed (${error::class.java.simpleName})") }
                setReplyState(sessionId, ChatReplyState(failure = AiFailure.from(error)))
            } finally {
                synchronized(replyJobs) {
                    if (replyJobs[sessionId] === coroutineContext[Job]) replyJobs.remove(sessionId)
                }
                onFinished()
            }
        }
        synchronized(replyJobs) {
            replyJobs.remove(sessionId)?.cancel()
            replyJobs[sessionId] = job
        }
        job.start()
    }

    private fun setReplyState(sessionId: String, state: ChatReplyState) {
        _replyStates.update { current -> current + (sessionId to state) }
    }

    fun clearChatMessages() {
        val sessionId = _currentSessionId.value
        if (sessionId.isBlank()) return
        scope.launch {
            val db = dbProvider() ?: return@launch
            db.chatMessageDao().deleteBySessionId(sessionId)
            val greeting = ChatMessage(
                UUID.randomUUID().toString(), sessionId, ChatSender.JUANJUAN,
                "当前对话已清空。我是卷卷，我们随时可以开启新的对话。",
                System.currentTimeMillis()
            )
            db.chatMessageDao().insert(ChatMessageEntity.fromDomainModel(greeting))
        }
    }

    private suspend fun generateSessionTitle(
        sessionId: String,
        userQuery: String,
        assistantReply: String,
        model: String
    ) {
        val settings = settingsProvider()
        val generated = if (settings.aiApiKey.isNotBlank()) {
            try {
                aiClient.generateTitle(userQuery, assistantReply, settings, model)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ""
            }
        } else ""

        val cleanTitle = (generated.ifBlank { localTitle(userQuery) })
            .replace(Regex("""[《》"“”、，。！？!?,.\s#*：:]"""), "")
            .trim().take(10).ifBlank { "考研复习对话" }
        val now = System.currentTimeMillis()
        _chatSessions.value = _chatSessions.value.map {
            if (it.id == sessionId) it.copy(title = cleanTitle, updatedAt = now) else it
        }
        dbProvider()?.chatSessionDao()?.updateTitle(sessionId, cleanTitle, now)
    }

    private fun localTitle(userQuery: String): String {
        val clean = userQuery.replace(Regex("""[^一-龥a-zA-Z0-9]"""), " ").trim()
        return when {
            clean.contains("草稿") || clean.contains("二次型") -> "二次型草稿防错"
            clean.contains("408") || clean.contains("知识点") -> "408知识点串联"
            clean.contains("大题") && clean.contains("做不完") -> "数学大题提速策略"
            clean.contains("受挫") || clean.contains("焦虑") -> "做题受挫心态调整"
            clean.contains("英语") || clean.contains("阅读") -> "英语真题阅读精读"
            clean.contains("政治") || clean.contains("大题") -> "政治大题背诵技巧"
            clean.length in 3..10 -> clean
            clean.length > 10 -> clean.take(10)
            else -> "考研复习对话"
        }.take(10)
    }

    private data class PendingReply(val userMessage: ChatMessage, val model: String)
    private data class MessagePage(val rows: List<ChatMessageEntity>, val total: Int)

    private companion object {
        const val DEFAULT_PAGE_SIZE = 40
        const val PAGE_SIZE = 40
        const val GREETING =
            "嗨！我是卷卷，你的考研全科专属学伴。\n\n无论遇到攻克不下的难题卡点、做题受挫时的烦躁，" +
                "还是单纯想找人说说话，我都在这里随时陪着你。今天想聊点什么呢？"
    }
}
