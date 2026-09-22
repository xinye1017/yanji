package com.example.yanji.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.theme.YanjiTheme
import com.example.yanji.ui.chat.components.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 卷卷伴学 Chat UI 契约测试。
 *
 * 定位策略：气泡 / 输入区 / 发送键一律通过组件导出的 testTag 常量定位
 * （`ChatInputTestTag` / `ChatSendButtonTestTag` / `UserMessageBubbleTestTag` /
 * `AiMessageBubbleTestTag`），不再依赖中文整句文案，避免文案迭代就把测试打红。
 * 只有「欢迎语」这类「内容即产品」的区块才断言其文案。
 *
 * 注意：一个 test method 内**只能调用一次 `setContent`**——Compose 测试宿主 Activity
 * 二次 setContent 会抛 `IllegalStateException: Activity has already called setContent`。
 * 因此每个被测组件各自独立成一个测试。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AiChatScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    private fun userMessage() = ChatMessage(
        id = "msg-user-1",
        sessionId = "session-1",
        sender = ChatSender.USER,
        content = "复习高数中值定理有点卡壳",
        timestamp = 1700000000000L
    )

    private fun aiMessage() = ChatMessage(
        id = "msg-ai-1",
        sessionId = "session-1",
        sender = ChatSender.JUANJUAN,
        content = "中值定理的核心在于构造辅助函数。罗尔、拉格朗日、柯西是递进关系。",
        timestamp = 1700000005000L
    )

    @Test
    fun conversationWelcomeDisplaysGreetingsAndContextCount() {
        composeRule.setContent {
            YanjiTheme {
                ConversationWelcome(contextRecordCount = 12)
            }
        }

        composeRule.onNodeWithText("卷卷").assertExists()
        composeRule.onNodeWithText("专属学伴").assertExists()
        // 欢迎语与记录数是该区块的全部产品价值，文案即契约（与当前生产 UI 对齐）。
        composeRule.onNodeWithText("你好，我是卷卷！🌱").assertExists()
        composeRule.onNodeWithText("已关联近 12 项真实学习记录").assertExists()
    }

    @Test
    fun quickQuestionsDispatchesSelectedQuestion() {
        var clickedQuestion = ""
        val questions = listOf("数学大题做不完怎么办？", "408知识点多怎么串联？")

        composeRule.setContent {
            YanjiTheme {
                QuickQuestionsRow(
                    questions = questions,
                    onQuestionClick = { clickedQuestion = it }
                )
            }
        }

        composeRule.onNodeWithText("可以从这些开始").assertExists()
        composeRule.onNodeWithText("数学大题做不完怎么办？").assertExists().performClick()
        assertEquals("数学大题做不完怎么办？", clickedQuestion)
    }

    @Test
    fun userMessageBubbleRendersCorrectly() {
        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    UserMessageBubble(content = userMessage().content)
                }
            }
        }

        composeRule.onNodeWithTag(UserMessageBubbleTestTag).assertExists()
        composeRule.onNodeWithText("复习高数中值定理有点卡壳").assertExists()
    }

    @Test
    fun aiMessageBubbleRendersCorrectly() {
        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    AiMessageBubble(
                        message = aiMessage(),
                        learningRecordCount = 5,
                        onActionClick = {},
                        onFollowupClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AiMessageBubbleTestTag).assertExists()
        composeRule.onNodeWithText("卷卷").assertExists()
        composeRule.onNodeWithText("中值定理的核心在于构造辅助函数。罗尔、拉格朗日、柯西是递进关系。")
            .assertExists()
    }

    @Test
    fun composerBarAcceptsInputAndDispatchesSend() {
        val text = mutableStateOf("")
        var sentText: String? = null

        composeRule.setContent {
            YanjiTheme {
                ComposerBar(
                    inputText = text.value,
                    onTextChange = { text.value = it },
                    onSend = { sentText = text.value },
                    isAiConfigured = true,
                    activeModel = "deepseek-chat",
                    thinkingIntensity = ThinkingIntensity.DEEP
                )
            }
        }

        val draft = "中值定理怎么构造辅助函数？"
        composeRule.onNodeWithTag(ChatInputTestTag).assertExists().performTextInput(draft)
        assertEquals("输入应回写到受控状态", draft, text.value)

        composeRule.onNodeWithTag(ChatSendButtonTestTag).performClick()
        assertEquals("点击发送应把当前草稿交给 onSend", draft, sentText)
    }
}
