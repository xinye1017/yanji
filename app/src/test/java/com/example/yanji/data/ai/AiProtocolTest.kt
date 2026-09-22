package com.example.yanji.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 协议层的纯 JVM 测试。
 *
 * 不同厂商返回的 `/models` 结构并不一致（`data` / `models`、对象数组 /
 * 字符串数组），解析失败会直接导致"测试连接"报错。
 */
class AiProtocolTest {
    @Test
    fun nonJsonProviderErrorBodyIsNeverReturnedToUi() {
        val detail = AiProtocol.extractErrorDetail("<html>secret provider diagnostics</html>")
        assertFalse(detail.contains("secret"))
        assertEquals("服务未提供可读的 JSON 错误信息", detail)
    }

    // ---------------------------------------------------------------- URL 拼接

    @Test
    fun `chatCompletionsUrl appends endpoint only when missing`() {
        assertEquals("https://api.deepseek.com/v1/chat/completions", AiProtocol.chatCompletionsUrl("https://api.deepseek.com/v1"))
        assertEquals("https://api.deepseek.com/v1/chat/completions", AiProtocol.chatCompletionsUrl("https://api.deepseek.com/v1/"))
        assertEquals(
            "用户直接填了完整端点时不重复追加",
            "https://gw.example.com/v1/chat/completions",
            AiProtocol.chatCompletionsUrl("https://gw.example.com/v1/chat/completions")
        )
    }

    @Test
    fun `chatCompletionsUrl falls back to deepseek when blank`() {
        assertEquals(AiProtocol.DEFAULT_BASE_URL + "/chat/completions", AiProtocol.chatCompletionsUrl("  "))
    }

    @Test
    fun `modelsUrls adds v1 candidate only when base is not already v1`() {
        assertEquals(
            listOf("https://api.deepseek.com/v1/models"),
            AiProtocol.modelsUrls("https://api.deepseek.com/v1")
        )
        assertEquals(
            "非 v1 前缀要给出 /v1/models 兜底候选",
            listOf("https://gw.example.com/openai/models", "https://gw.example.com/openai/v1/models"),
            AiProtocol.modelsUrls("https://gw.example.com/openai")
        )
        assertEquals(
            listOf("https://x/models"),
            AiProtocol.modelsUrls("https://x/models")
        )
    }

    // ---------------------------------------------------------------- /models 解析

    @Test
    fun `parseModels handles the data array of objects`() {
        val json = """
            {"object":"list","data":[{"id":"deepseek-chat","object":"model"},{"id":"deepseek-reasoner"}]}
        """.trimIndent()
        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), AiProtocol.parseModels(json))
    }

    @Test
    fun `parseModels handles the models array of strings`() {
        val json = """{"models":["qwen2.5:latest","llama3.1"]}"""
        assertEquals(listOf("qwen2.5:latest", "llama3.1"), AiProtocol.parseModels(json))
    }

    @Test
    fun `parseModels ignores blank entries and survives malformed json`() {
        assertEquals(listOf("a"), AiProtocol.parseModels("""{"data":[{"id":"a"},{"id":""}]}"""))
        assertTrue("解析失败要返回空列表而不是抛异常", AiProtocol.parseModels("not json at all").isEmpty())
        assertTrue("没有已知字段时要返回空列表", AiProtocol.parseModels("""{"unexpected":1}""").isEmpty())
    }

    // ---------------------------------------------------------------- 内容提取

    @Test
    fun `extractContent reads choices-0-message-content`() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"你好"}}]}"""
        assertEquals("你好", AiProtocol.extractContent(json))
    }

    @Test
    fun `extractContent returns null on structural mismatch or blank content`() {
        assertTrue(AiProtocol.extractContent("""{"choices":[]}""") == null)
        assertTrue(AiProtocol.extractContent("""{"choices":[{"message":{"content":""}}]}""") == null)
        assertTrue(AiProtocol.extractContent("""{"error":{"message":"x"}}""") == null)
        assertTrue("坏 JSON 不能抛异常", AiProtocol.extractContent("!!!") == null)
    }

    // ---------------------------------------------------------------- 错误提取与文案

    @Test
    fun `extractErrorDetail prefers error-message over raw body`() {
        val body = """{"error":{"message":"Insufficient Balance","type":"insufficient_balance"}}"""
        assertEquals("Insufficient Balance", AiProtocol.extractErrorDetail(body))
    }

    @Test
    fun `extractErrorDetail does not expose unstructured provider bodies`() {
        val body = "x".repeat(500)
        val detail = AiProtocol.extractErrorDetail(body)
        assertEquals("服务未提供可读的 JSON 错误信息", detail)
        assertFalse(detail.contains("x"))
    }

    @Test
    fun `describeHttpError keeps the user-facing wording per call kind`() {
        // MODELS：用户在配置页点「测试连接」，需要能看到具体原因
        assertTrue(
            AiProtocol.describeHttpError(AiCallKind.MODELS, 401, "bad key").contains("API Key 错误或失效")
        )
        assertTrue(
            AiProtocol.describeHttpError(AiCallKind.MODELS, 404, "", "https://x/v1/models").contains("https://x/v1/models")
        )
        assertTrue(AiProtocol.describeHttpError(AiCallKind.DIAGNOSIS, 429, "").contains("额度已用尽"))
        // DIAGNOSIS：正文不进用户可见文案
        assertEquals("AI 诊断接口返回 HTTP 500", AiProtocol.describeHttpError(AiCallKind.DIAGNOSIS, 500, "whatever"))
    }
}
