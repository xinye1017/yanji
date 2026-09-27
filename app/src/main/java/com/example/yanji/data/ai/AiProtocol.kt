package com.example.yanji.data.ai

import org.json.JSONArray
import org.json.JSONObject

/** 调用类型，决定错误文案（不同入口面向用户的措辞不同）。 */
internal enum class AiCallKind { MODELS, DIAGNOSIS }

/**
 * 支持的 AI 协议格式：
 * 1. OPENAI_CHAT: OpenAI Chat Completions 协议 (/chat/completions)
 * 2. OPENAI_RESPONSE: OpenAI Response 协议 (/responses)
 * 3. ANTHROPIC: Anthropic Messages 协议 (/messages)
 */
enum class AiProtocolType(
    val id: String,
    val displayName: String,
    val defaultPath: String
) {
    OPENAI_CHAT("OPENAI_CHAT", "OpenAI Chat Completions", "/chat/completions"),
    OPENAI_RESPONSE("OPENAI_RESPONSE", "OpenAI Response", "/responses"),
    ANTHROPIC("ANTHROPIC", "Anthropic Messages", "/messages");

    companion object {
        fun fromId(raw: String?): AiProtocolType =
            entries.find { it.id.equals(raw?.trim(), ignoreCase = true) } ?: OPENAI_CHAT
    }
}

/**
 * 第三方 AI 接口的**协议层**：URL 拼接、请求体构造、鉴权头配置、响应解析与错误文案。
 *
 * 全部是纯函数，不碰网络、不碰 Android，因此可以被 JVM 单元测试完整覆盖。
 */
internal object AiProtocol {

    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_MODEL = "deepseek-chat"

    fun normalizedBaseUrl(baseUrl: String): String {
        val clean = baseUrl.trim()
        return if (clean.isEmpty()) DEFAULT_BASE_URL else clean.trimEnd('/')
    }

    /** 对话补全/诊断端点。允许用户直接填完整地址，否则根据协议拼装。 */
    fun endpointUrl(baseUrl: String, protocol: AiProtocolType): String {
        val clean = normalizedBaseUrl(baseUrl)
        return when (protocol) {
            AiProtocolType.OPENAI_CHAT -> {
                if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
            }
            AiProtocolType.OPENAI_RESPONSE -> {
                if (clean.endsWith("/responses")) clean else "$clean/responses"
            }
            AiProtocolType.ANTHROPIC -> {
                if (clean.endsWith("/messages")) clean else "$clean/messages"
            }
        }
    }

    /**
     * 模型列表候选端点。
     * 不同厂商与协议的 `/models` 位置不同，返回候选列表依次尝试。
     */
    fun modelsUrls(baseUrl: String, protocol: AiProtocolType = AiProtocolType.OPENAI_CHAT): List<String> {
        val clean = normalizedBaseUrl(baseUrl)
        if (clean.endsWith("/models")) return listOf(clean)
        return when (protocol) {
            AiProtocolType.ANTHROPIC -> {
                buildList {
                    add("$clean/v1/models")
                    add("$clean/models")
                }
            }
            else -> {
                buildList {
                    add("$clean/models")
                    if (!clean.endsWith("/v1")) add("$clean/v1/models")
                }
            }
        }
    }

    /** 协议特定的请求体组装 */
    fun buildRequestBody(
        protocol: AiProtocolType,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2,
        maxTokens: Int = 1600
    ): JSONObject = when (protocol) {
        AiProtocolType.OPENAI_CHAT -> {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
            }
        }
        AiProtocolType.OPENAI_RESPONSE -> {
            JSONObject().apply {
                put("model", model)
                put("instructions", systemPrompt)
                put("input", userPrompt)
                put("temperature", temperature)
                put("max_output_tokens", maxTokens)
            }
        }
        AiProtocolType.ANTHROPIC -> {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            JSONObject().apply {
                put("model", model)
                put("max_tokens", maxTokens)
                put("temperature", temperature)
                put("system", systemPrompt)
                put("messages", messages)
            }
        }
    }

    /** 解析模型列表响应。容忍 data / models 字段与字符串数组。 */
    fun parseModels(json: String): List<String> {
        val result = mutableListOf<String>()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return result
        val array = when {
            root.has("data") -> root.optJSONArray("data")
            root.has("models") -> root.optJSONArray("models")
            else -> null
        } ?: return result

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            val id = if (item != null) {
                item.optString("id", item.optString("name", ""))
            } else {
                array.optString(i, "")
            }
            if (id.isNotBlank()) result.add(id)
        }
        return result
    }

    /** 协议特定的返回内容提取。 */
    fun extractContent(json: String, protocol: AiProtocolType = AiProtocolType.OPENAI_CHAT): String? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return when (protocol) {
            AiProtocolType.OPENAI_CHAT -> {
                val choices = root.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null
                message.optString("content").takeIf { it.isNotBlank() }
            }
            AiProtocolType.OPENAI_RESPONSE -> {
                if (root.has("output_text")) {
                    root.optString("output_text").takeIf { it.isNotBlank() }
                } else if (root.has("output")) {
                    val outputArray = root.optJSONArray("output")
                    if (outputArray != null && outputArray.length() > 0) {
                        val first = outputArray.optJSONObject(0)
                        if (first != null) {
                            val contentArray = first.optJSONArray("content")
                            if (contentArray != null && contentArray.length() > 0) {
                                contentArray.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
                            } else {
                                first.optString("text").takeIf { it.isNotBlank() }
                            }
                        } else null
                    } else null
                } else {
                    // Fallback to choices[0].message.content if proxy returned standard chat response
                    val choices = root.optJSONArray("choices") ?: return null
                    if (choices.length() == 0) return null
                    val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null
                    message.optString("content").takeIf { it.isNotBlank() }
                }
            }
            AiProtocolType.ANTHROPIC -> {
                val contentArray = root.optJSONArray("content") ?: return null
                val sb = StringBuilder()
                for (i in 0 until contentArray.length()) {
                    val item = contentArray.optJSONObject(i) ?: continue
                    if (item.optString("type", "text") == "text") {
                        val text = item.optString("text")
                        if (text.isNotBlank()) sb.append(text)
                    }
                }
                sb.toString().takeIf { it.isNotBlank() }
            }
        }
    }

    /** 从第三方错误响应里提取给用户展示的描述 */
    fun extractErrorDetail(body: String): String {
        val root = runCatching { JSONObject(body) }.getOrNull()
        val message = root?.optJSONObject("error")?.optString("message")
            ?: root?.optString("message")
        if (!message.isNullOrBlank()) {
            return message.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(150)
        }
        return "服务未提供可读的 JSON 错误信息"
    }

    /** 统一的 HTTP 错误文案。 */
    fun describeHttpError(kind: AiCallKind, code: Int, detail: String, url: String = ""): String = when (kind) {
        AiCallKind.MODELS -> when (code) {
            401 -> "HTTP 401 未授权：API Key 错误或失效 ($detail)"
            403 -> "HTTP 403 权限受限：访问被拒绝 ($detail)"
            404 -> "HTTP 404 路径不存在：请检查 Base URL ($url)"
            429 -> "HTTP 429 请求受限：API 额度已用尽或请求过多 ($detail)"
            else -> "HTTP $code: $detail"
        }
        AiCallKind.DIAGNOSIS -> when (code) {
            401 -> "API Key 未授权或失效 (HTTP 401)"
            403 -> "AI 分析接口访问受限 (HTTP 403)"
            404 -> "未找到 AI 分析接口 (HTTP 404，请核对 Base URL)"
            429 -> "AI 服务额度已用尽或请求过于频繁 (HTTP 429)"
            else -> "AI 分析接口返回 HTTP $code"
        }
    }
}

/** AI 调用失败。message 已经是可直接展示给用户的文案。 */
internal class AiException(
    message: String,
    cause: Throwable? = null,
    val failure: AiFailure? = null
) : Exception(message, cause)
