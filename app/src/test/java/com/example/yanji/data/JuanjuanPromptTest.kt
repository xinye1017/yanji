package com.example.yanji.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JuanjuanPromptTest {
    @Test
    fun systemPromptDefinesJuanjuanAndAppBoundaries() {
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("卷卷"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("研迹"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("上下文安全"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("要将『具体动作』加为明早计划吗？"))
    }

    @Test
    fun runtimeContextIncludesStudyDataButNeverApiKey() {
        val context = JuanjuanPrompt.buildRuntimeContext(
            settings = UserSettings(aiApiKey = "secret-key", targetSchool = "测试大学"),
            focusSessions = listOf(
                FocusSession("focus-1", "math", "数学", 1_700_000_000_000, 1_700_000_003_600, 3600)
            ),
            examSessions = emptyList(),
            journalEntries = listOf(JournalEntry("journal-1", "2026-09-05", content = "复盘积分题")),
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
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("禁止编造") || JuanjuanPrompt.SYSTEM_PROMPT.contains("严禁编造"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("百分比") || JuanjuanPrompt.SYSTEM_PROMPT.contains("统计数据"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("从你的描述来看"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("一般情况下"))
    }

    @Test
    fun systemPromptIncludesStructuredOutputTokens() {
        // 新增结构化输出 token 规则
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("【诊断】"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("【证据】"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("原因："))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("[动作:"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("[追问:"))
        assertTrue(JuanjuanPrompt.SYSTEM_PROMPT.contains("CREATE_PLAN"))
    }
}
