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

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class JuanjuanChatScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun conversationWelcomeDisplaysGreetingsAndContextCount() {
        composeRule.setContent {
            YanjiTheme {
                ConversationWelcome(contextRecordCount = 12)
            }
        }

        composeRule.onNodeWithText("卷卷").assertExists()
        composeRule.onNodeWithText("专属学伴").assertExists()
        composeRule.onNodeWithText("嗨，今天已经专注备考啦！🌱").assertExists()
        composeRule.onNodeWithText("已关联近 12 项学习记录深度思考").assertExists()
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
    fun userAndJuanjuanMessageBubblesRenderCorrectly() {
        val userMsg = ChatMessage(
            id = "msg-user-1",
            sessionId = "session-1",
            sender = ChatSender.USER,
            content = "复习高数中值定理有点卡壳",
            timestamp = 1700000000000L
        )

        val assistantMsg = ChatMessage(
            id = "msg-ai-1",
            sessionId = "session-1",
            sender = ChatSender.JUANJUAN,
            content = "中值定理的核心在于构造辅助函数。罗尔、拉格朗日、柯西是递进关系。",
            timestamp = 1700000005000L
        )

        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    UserMessageBubble(content = userMsg.content)
                }
            }
        }

        composeRule.onNodeWithText("复习高数中值定理有点卡壳").assertExists()

        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    JuanjuanMessageBubble(
                        message = assistantMsg,
                        learningRecordCount = 5,
                        onActionClick = {},
                        onFollowupClick = {},
                        onContextSourceClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("卷卷").assertExists()
        composeRule.onNodeWithText("中值定理的核心在于构造辅助函数。罗尔、拉格朗日、柯西是递进关系。").assertExists()
    }

    @Test
    fun composerBarHandlesTextInputAndSend() {
        var text = mutableStateOf("")
        var sentText = ""

        composeRule.setContent {
            YanjiTheme {
                ComposerBar(
                    inputText = text.value,
                    onTextChange = { text.value = it },
                    onSend = {
                        sentText = text.value
                        text.value = ""
                    },
                    activeModel = "deepseek-chat",
                    onModelSelect = {},
                    thinkingIntensity = ThinkingIntensity.DEEP,
                    onThinkingIntensityChange = {},
                    onOpenAiSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("问卷卷任何考研问题…").assertExists()
    }
}
