package com.example.yanji.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.ChatContextSource
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSession
import com.example.yanji.data.JuanjuanAction
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 伴学对话不可变 UiState：消息流、会话目录、回复状态与上下文来源统计。 */
data class ChatUiState(
    val messages: List<ChatMessage>,
    val isAiReplying: Boolean,
    val settings: UserSettings,
    val sessions: List<ChatSession>,
    val currentSessionId: String,
    val contextSources: List<ChatContextSource>
) {
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

    val uiState: StateFlow<ChatUiState> = combine(
        repo.chatMessages,
        repo.isAiReplying,
        repo.settings,
        repo.chatSessions,
        repo.currentSessionId
    ) { messages, replying, settings, sessions, sessionId ->
        ChatUiState(
            messages = messages,
            isAiReplying = replying,
            settings = settings,
            sessions = sessions,
            currentSessionId = sessionId,
            contextSources = repo.currentContextSources()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(
            messages = repo.chatMessages.value,
            isAiReplying = repo.isAiReplying.value,
            settings = repo.settings.value,
            sessions = repo.chatSessions.value,
            currentSessionId = repo.currentSessionId.value,
            contextSources = repo.currentContextSources()
        )
    )

    // ---- 动作 ----

    fun createNewChatSession(initialModel: String? = null): String =
        repo.createNewChatSession(initialModel)

    fun sendChatMessage(text: String, model: String? = null) = repo.sendChatMessage(text, model)

    fun executeAction(action: JuanjuanAction, context: android.content.Context): Boolean =
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
}
