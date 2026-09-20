package com.example.yanji.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptTest {
    @Test
    fun systemPromptDefinesAiAndAppBoundaries() {
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("卷卷"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("研迹"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("上下文安全"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("要将『具体动作』加为明早计划吗？"))
    }

    @Test
    fun runtimeContextIncludesStudyDataButNeverApiKey() {
        val context = AiPrompt.buildRuntimeContext(
            settings = UserSettings(aiApiKey = "secret-key", targetSchool = "测试大学"),
            focusSessions = listOf(
                FocusSession("focus-1", "math", "数学", 1_700_000_000_000, 1_700_000_003_600, 3600)
            ),
            examSessions = emptyList(),
            noteEntries = listOf(NoteEntry("notes-1", "2026-09-05", content = "复盘积分题")),
            activeFocus = null,
            now = 1_700_000_004_000
        )

        assertTrue(context.contains("测试大学"))
        assertTrue(context.contains("数学"))
        assertTrue(context.contains("复盘积分题"))
        assertFalse(context.contains("secret-key"))
    }

    @Test
    fun systemPromptForbidsFabricatedStatistics() {
        // 禁止编造百分比、统计、用户群体结论
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("禁止编造") || AiPrompt.SYSTEM_PROMPT.contains("严禁编造"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("百分比") || AiPrompt.SYSTEM_PROMPT.contains("统计数据"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("从你的描述来看"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("一般情况下"))
    }

    @Test
    fun systemPromptIncludesStructuredOutputTokens() {
        // 新增结构化输出 token 规则
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("【诊断】"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("【证据】"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("原因："))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("[动作:"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("[追问:"))
        assertTrue(AiPrompt.SYSTEM_PROMPT.contains("CREATE_PLAN"))
    }
}
