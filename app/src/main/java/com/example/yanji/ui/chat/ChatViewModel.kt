package com.example.yanji.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.ChatContextSource
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSession
import com.example.yanji.data.AiAction
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.ai.ChatReplyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 伴学对话不可变 UiState：消息流、会话目录、回复状态与上下文来源统计。 */
data class ChatUiState(
    val messages: List<ChatMessage>,
    val replyState: ChatReplyState,
    val hasMoreMessages: Boolean,
    val settings: UserSettings,
    val sessions: List<ChatSession>,
    val currentSessionId: String,
    val contextSources: List<ChatContextSource>,
    val availableModels: List<String> = emptyList()
) {
    val isAiReplying: Boolean get() = replyState.isReplying
    val isAiConfigured: Boolean get() = settings.isAiConfigured

    val currentSession: ChatSession?
        get() = sessions.find { it.id == currentSessionId }
}

/**
 * 卷卷对话 Feature ViewModel。
 * contextSources 跟随消息/会话流重算（与原 remember(messages.size, sessions.size) 等价）。
 */
class ChatViewModel(
    private val repo: YanjiRepository
) : ViewModel() {

    private val conversationState = combine(
        repo.chatMessages,
        repo.currentSessionId,
        repo.chatReplyStates,
        repo.hasMoreChatMessages
    ) { messages, sessionId, replyStates, hasMore ->
        ConversationState(messages, sessionId, replyStates[sessionId] ?: ChatReplyState(), hasMore)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        conversationState,
        repo.settings,
        repo.chatSessions,
        repo.availableAiModels
    ) { conversation, settings, sessions, models ->
        ChatUiState(
            messages = conversation.messages,
            replyState = conversation.replyState,
            hasMoreMessages = conversation.hasMoreMessages,
            settings = settings,
            sessions = sessions,
            currentSessionId = conversation.sessionId,
            contextSources = repo.currentContextSources(),
            availableModels = models
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(
            messages = repo.chatMessages.value,
            replyState = repo.chatReplyStates.value[repo.currentSessionId.value] ?: ChatReplyState(),
            hasMoreMessages = repo.hasMoreChatMessages.value,
            settings = repo.settings.value,
            sessions = repo.chatSessions.value,
            currentSessionId = repo.currentSessionId.value,
            contextSources = repo.currentContextSources(),
            availableModels = repo.availableAiModels.value
        )
    )

    // ---- 动作 ----

    fun createNewChatSession(initialModel: String? = null): String =
        repo.createNewChatSession(initialModel)

    fun sendChatMessage(text: String, model: String? = null) = repo.sendChatMessage(text, model)

    fun retryFailedReply() {
        val sessionId = uiState.value.currentSessionId
        if (sessionId.isNotBlank()) repo.retryChatReply(sessionId)
    }

    fun cancelReply() = repo.cancelChatReply(uiState.value.currentSessionId)

    fun loadMoreMessages() = repo.loadMoreChatMessages()

    fun executeAction(action: AiAction, context: android.content.Context): Boolean =
        repo.executeAction(action, context)

    fun switchChatSession(sessionId: String) = repo.switchChatSession(sessionId)

    fun deleteChatSession(sessionId: String) = repo.deleteChatSession(sessionId)

    fun updateModel(model: String) {
        val currentSessionId = uiState.value.currentSessionId
        if (currentSessionId.isNotBlank()) {
            repo.updateSessionModel(currentSessionId, model)
        }
        val currentSettings = uiState.value.settings
        repo.updateSettings(currentSettings.copy(aiModel = model))
    }

    override fun onCleared() {
        repo.cancelChatReply(uiState.value.currentSessionId)
        super.onCleared()
    }

    private data class ConversationState(
        val messages: List<ChatMessage>,
        val sessionId: String,
        val replyState: ChatReplyState,
        val hasMoreMessages: Boolean
    )
}
