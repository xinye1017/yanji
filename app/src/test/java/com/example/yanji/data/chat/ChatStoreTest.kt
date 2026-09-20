package com.example.yanji.data.chat

import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.UserSettings
import com.example.yanji.data.ai.AiClient
import com.example.yanji.data.ai.AiFailure
import com.example.yanji.data.ai.ChatReplyState
import com.example.yanji.data.db.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class FakeChatSessionDao : ChatSessionDao {
    private val sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())

    override fun getAll(): Flow<List<ChatSessionEntity>> = sessions.asStateFlow()

    override suspend fun getById(id: String): ChatSessionEntity? =
        sessions.value.find { it.id == id }

    override suspend fun count(): Int = sessions.value.size

    override suspend fun insert(session: ChatSessionEntity) {
        sessions.update { list -> listOf(session) + list.filter { it.id != session.id } }
    }

    override suspend fun insertAll(sessions: List<ChatSessionEntity>) {
        this.sessions.update { list -> sessions + list.filter { old -> sessions.none { it.id == old.id } } }
    }

    override suspend fun updateTitle(id: String, title: String, updatedAt: Long) {
        sessions.update { list -> list.map { if (it.id == id) it.copy(title = title, updatedAt = updatedAt) else it } }
    }

    override suspend fun updateModel(id: String, model: String, updatedAt: Long) {
        sessions.update { list -> list.map { if (it.id == id) it.copy(model = model, updatedAt = updatedAt) else it } }
    }

    override suspend fun delete(id: String) {
        sessions.update { list -> list.filter { it.id != id } }
    }

    override suspend fun clearAll() {
        sessions.value = emptyList()
    }
}

class FakeChatMessageDao : ChatMessageDao {
    private val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    override fun getAll(): Flow<List<ChatMessageEntity>> = messages.map { list -> list.sortedBy { it.timestamp } }

    override fun getBySessionId(sessionId: String): Flow<List<ChatMessageEntity>> =
        messages.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.timestamp } }

    override fun observeRecentBySessionId(sessionId: String, limit: Int): Flow<List<ChatMessageEntity>> =
        messages.map { list ->
            list.filter { it.sessionId == sessionId }
                .sortedWith(compareByDescending<ChatMessageEntity> { it.timestamp }.thenByDescending { it.id })
                .take(limit)
        }

    override fun observeCountBySessionId(sessionId: String): Flow<Int> =
        messages.map { list -> list.count { it.sessionId == sessionId } }

    override suspend fun countUserMessages(sessionId: String): Int =
        messages.value.count { it.sessionId == sessionId && it.sender == "USER" }

    override suspend fun count(): Int = messages.value.size

    override suspend fun insert(message: ChatMessageEntity) {
        messages.update { list -> list.filter { it.id != message.id } + message }
    }

    override suspend fun insertAll(messages: List<ChatMessageEntity>) {
        this.messages.update { list -> list.filter { old -> messages.none { it.id == old.id } } + messages }
    }

    override suspend fun deleteBySessionId(sessionId: String) {
        messages.update { list -> list.filter { it.sessionId != sessionId } }
    }

    override suspend fun clearAll() {
        messages.value = emptyList()
    }
}

class FakeYanjiDatabase(
    private val sessionDao: ChatSessionDao = FakeChatSessionDao(),
    private val messageDao: ChatMessageDao = FakeChatMessageDao()
) : YanjiDatabase() {
    override fun chatSessionDao(): ChatSessionDao = sessionDao
    override fun chatMessageDao(): ChatMessageDao = messageDao
    override fun focusSessionDao(): FocusSessionDao = throw UnsupportedOperationException()
    override fun examSessionDao(): ExamSessionDao = throw UnsupportedOperationException()
    override fun noteEntryDao(): NoteEntryDao = throw UnsupportedOperationException()
    override fun userSettingsDao(): UserSettingsDao = throw UnsupportedOperationException()
    override fun checkInDao(): CheckInDao = throw UnsupportedOperationException()
    override fun achievementDao(): AchievementDao = throw UnsupportedOperationException()
    override fun quickStartPresetDao(): QuickStartPresetDao = throw UnsupportedOperationException()
    override fun subjectDao(): SubjectDao = throw UnsupportedOperationException()
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
        androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "chat_sessions", "chat_messages")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatStoreTest {

    @Test
    fun testAiFailureMapping() {
        assertEquals(AiFailure.Timeout, AiFailure.from(SocketTimeoutException("timeout")))
        assertEquals(AiFailure.Network, AiFailure.from(IOException("Failed to connect to host")))
        assertEquals(AiFailure.Authentication, AiFailure.from(RuntimeException("401 Unauthorized: Invalid API Key")))
        assertEquals(AiFailure.Quota, AiFailure.from(RuntimeException("429 Too Many Requests: 额度已用尽")))
        assertEquals(AiFailure.Endpoint, AiFailure.from(RuntimeException("404 Not Found: 检查 Base URL")))
        assertEquals(AiFailure.InvalidResponse, AiFailure.from(RuntimeException("返回内容为空")))
        assertEquals(AiFailure.Service, AiFailure.from(RuntimeException("500 Internal Server Error")))
    }

    @Test
    fun testSendChatMessageFailsSetsTypedFailureInSessionScope() = runTest {
        val fakeDb = FakeYanjiDatabase()
        var replyShouldFail = true
        val store = ChatStore(
            scope = this,
            dbProvider = { fakeDb },
            settingsProvider = { UserSettings() },
            replyProvider = { _, _ ->
                if (replyShouldFail) throw RuntimeException("401 Invalid API key")
                "AI 回复成功"
            },
            aiClient = AiClient()
        )
        try {
            store.bind(fakeDb)
            advanceUntilIdle()

            val sessionId = store.createNewChatSession()
            advanceUntilIdle()

            // 1. 发送消息，抛出 401
            store.sendChatMessage("你好，卷卷")
            advanceUntilIdle()

            val replyState = store.replyStates.value[sessionId]
            assertNotNull(replyState)
            assertFalse(replyState!!.isReplying)
            assertEquals(AiFailure.Authentication, replyState.failure)

            // 2. Retry 修复后成功
            replyShouldFail = false
            store.retryFailedReply(sessionId)
            advanceUntilIdle()

            val retryState = store.replyStates.value[sessionId]
            assertNotNull(retryState)
            assertFalse(retryState!!.isReplying)
            assertNull(retryState.failure)

            // 验证 Retry 不重复插入用户消息：数据库中用户消息数量必须恰好为 1
            val userMsgCount = fakeDb.chatMessageDao().countUserMessages(sessionId)
            assertEquals("Retry 绝不应重复插入用户消息", 1, userMsgCount)
        } finally {
            store.close()
        }
    }

    @Test
    fun testSessionSwitchingIsolatesReplyStates() = runTest {
        val fakeDb = FakeYanjiDatabase()
        val store = ChatStore(
            scope = this,
            dbProvider = { fakeDb },
            settingsProvider = { UserSettings() },
            replyProvider = { _, _ ->
                throw SocketTimeoutException("网络超时")
            },
            aiClient = AiClient()
        )
        try {
            store.bind(fakeDb)
            advanceUntilIdle()

            val sessionA = store.createNewChatSession()
            val sessionB = store.createNewChatSession()
            advanceUntilIdle()

            // 在 sessionA 中发送并触发超时失败
            store.switchChatSession(sessionA)
            store.sendChatMessage("Session A 提问")
            advanceUntilIdle()

            val stateA = store.replyStates.value[sessionA]
            val stateB = store.replyStates.value[sessionB]

            assertNotNull(stateA)
            assertEquals(AiFailure.Timeout, stateA!!.failure)
            assertNull("Session B 不应串用 Session A 的错误状态", stateB)

            // 切换到 Session B
            store.switchChatSession(sessionB)
            val currentSessionReplyState = store.replyStates.value[store.currentSessionId.value]
            assertNull("切换后当前会话不应有前一个会话的错误状态", currentSessionReplyState)
        } finally {
            store.close()
        }
    }

    @Test
    fun testBoundedMessagePaginationDoesNotLoadEntireHistory() = runTest {
        val fakeDb = FakeYanjiDatabase()
        val store = ChatStore(
            scope = this,
            dbProvider = { fakeDb },
            settingsProvider = { UserSettings() },
            replyProvider = { _, _ -> "回复" },
            aiClient = AiClient()
        )
        try {
            store.bind(fakeDb)
            advanceUntilIdle()

            val sessionId = store.createNewChatSession()
            advanceUntilIdle()

            // 向该会话批量插入 50 条历史消息
            val initialMessages = (1..50).map { i ->
                ChatMessageEntity(
                    id = "msg-$i",
                    sessionId = sessionId,
                    sender = if (i % 2 == 0) ChatSender.JUANJUAN.name else ChatSender.USER.name,
                    content = "历史消息 $i",
                    timestamp = 1_000_000L + i
                )
            }
            fakeDb.chatMessageDao().insertAll(initialMessages)
            advanceUntilIdle()

            // 默认每页 40 条，不能一次性全量加载 50 条
            assertEquals(40, store.chatMessages.value.size)
            assertTrue("存在更多历史消息时 hasMoreMessages 必须为 true", store.hasMoreMessages.value)

            // 点击加载更早消息
            store.loadMoreMessages()
            advanceUntilIdle()

            // 全部 51 条（含开局 greeting）加载完成
            assertEquals(51, store.chatMessages.value.size)
            assertFalse(store.hasMoreMessages.value)
        } finally {
            store.close()
        }
    }
}
