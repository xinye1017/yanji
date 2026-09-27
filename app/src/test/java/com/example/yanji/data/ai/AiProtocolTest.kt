package com.example.yanji.data.ai

import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProtocolTest {

    @Test
    fun `endpoint urls match protocols`() {
        val base = "https://api.example.com/v1"
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiProtocol.endpointUrl(base, AiProtocolType.OPENAI_CHAT)
        )
        assertEquals(
            "https://api.example.com/v1/responses",
            AiProtocol.endpointUrl(base, AiProtocolType.OPENAI_RESPONSE)
        )
        assertEquals(
            "https://api.example.com/v1/messages",
            AiProtocol.endpointUrl(base, AiProtocolType.ANTHROPIC)
        )
    }

    @Test
    fun `extract content from openai chat completions`() {
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "{\"overview\": \"学习良好\"}"
                        }
                    }
                ]
            }
        """.trimIndent()
        val content = AiProtocol.extractContent(json, AiProtocolType.OPENAI_CHAT)
        assertEquals("{\"overview\": \"学习良好\"}", content)
    }

    @Test
    fun `extract content from openai responses api`() {
        val jsonWithOutputText = """
            {
                "output_text": "{\"overview\": \"responses良好\"}"
            }
        """.trimIndent()
        assertEquals(
            "{\"overview\": \"responses良好\"}",
            AiProtocol.extractContent(jsonWithOutputText, AiProtocolType.OPENAI_RESPONSE)
        )

        val jsonWithOutputArray = """
            {
                "output": [
                    {
                        "content": [
                            {"type": "text", "text": "{\"overview\": \"output array良好\"}"}
                        ]
                    }
                ]
            }
        """.trimIndent()
        assertEquals(
            "{\"overview\": \"output array良好\"}",
            AiProtocol.extractContent(jsonWithOutputArray, AiProtocolType.OPENAI_RESPONSE)
        )
    }

    @Test
    fun `extract content from anthropic messages`() {
        val json = """
            {
                "id": "msg_123",
                "content": [
                    {
                        "type": "text",
                        "text": "{\"overview\": \"anthropic良好\"}"
                    }
                ]
            }
        """.trimIndent()
        val content = AiProtocol.extractContent(json, AiProtocolType.ANTHROPIC)
        assertEquals("{\"overview\": \"anthropic良好\"}", content)
    }

    @Test
    fun `parse models response handles different providers`() {
        val openaiModelsJson = """
            {
                "data": [
                    {"id": "gpt-4o"},
                    {"id": "deepseek-chat"}
                ]
            }
        """.trimIndent()
        assertEquals(listOf("gpt-4o", "deepseek-chat"), AiProtocol.parseModels(openaiModelsJson))

        val simpleArrayJson = """
            {
                "models": ["qwen2.5:latest", "llama3.1:latest"]
            }
        """.trimIndent()
        assertEquals(listOf("qwen2.5:latest", "llama3.1:latest"), AiProtocol.parseModels(simpleArrayJson))
    }

    @Test
    fun `study data provider injects target goals and companion name`() {
        val provider = DefaultStudyDataProvider()
        val settings = UserSettings(
            targetSchool = "北京大学",
            targetMajor = "计算机科学",
            targetExamDate = "2026-12-26",
            dailyGoalHours = 4.0f
        )
        val now = 1774396800000L // 2026-03-25

        val focusSessions = listOf(
            FocusSession(
                id = "fs-1",
                subjectId = "math_advanced",
                subjectName = "高等数学",
                startTime = now - 3600_000L,
                endTime = now,
                durationSeconds = 3600L,
                status = SessionStatus.COMPLETED,
                mode = "正向计时"
            )
        )

        val snapshot = provider.buildSnapshot(
            periodDays = 7,
            settings = settings,
            focusSessions = focusSessions,
            examSessions = emptyList(),
            noteEntries = emptyList(),
            companionName = "绵绵",
            now = now
        )

        assertEquals("绵绵", snapshot.companionName)
        assertEquals("北京大学", snapshot.targetSchool)
        assertEquals("计算机科学", snapshot.targetMajor)
        assertNotNull(snapshot.daysUntilExam)

        val promptData = snapshot.toPromptData()
        assertTrue(promptData.contains("北京大学"))
        assertTrue(promptData.contains("计算机科学"))
        assertTrue(promptData.contains("高等数学"))

        val systemPrompt = provider.buildSystemPrompt("绵绵")
        assertTrue(systemPrompt.contains("绵绵"))
    }
}
