package com.example.yanji.data.ai

import org.json.JSONObject

/** 调用类型，决定错误文案（不同入口面向用户的措辞不同）。 */
internal enum class AiCallKind { MODELS, DIAGNOSIS }

/**
 * 第三方 AI 接口的**协议层**：URL 拼接、响应解析、错误文案。
 *
 * 全部是纯函数，不碰网络、不碰 Android，因此可以被 JVM 单元测试完整覆盖。
 * 从 `YanjiRepository` 里抽出来之前，这些逻辑与 `HttpURLConnection` 的读写、
 * 业务分支混在一个 92 行的私有方法里，既测不到也难以复用到别的入口。
 */
internal object AiProtocol {

    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_MODEL = "deepseek-chat"

    fun normalizedBaseUrl(baseUrl: String): String {
        var clean = baseUrl.trim()
        return if (clean.isEmpty()) DEFAULT_BASE_URL else clean.trimEnd('/')
    }

    /** 对话补全端点。允许用户直接填 `/chat/completions` 结尾的完整地址。 */
    fun chatCompletionsUrl(baseUrl: String): String {
        val clean = normalizedBaseUrl(baseUrl)
        return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
    }

    /**
     * 模型列表候选端点。
     * 不同厂商的 `/models` 位置不一致（有的在根、有的在 `/v1` 下），所以返回候选列表依次尝试。
     */
    fun modelsUrls(baseUrl: String): List<String> {
        val clean = normalizedBaseUrl(baseUrl)
        return if (clean.endsWith("/models")) {
            listOf(clean)
        } else {
            buildList {
                add("$clean/models")
                if (!clean.endsWith("/v1")) add("$clean/v1/models")
            }
        }
    }

    /** 解析 OpenAI 风格 `/models` 响应。容忍 `data` / `models` 两种字段，以及字符串数组。 */
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

    /** 取 `choices[0].message.content`。结构不符时返回 null，由调用方决定报错还是回退。 */
    fun extractContent(json: String): String? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val choices = root.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null
        return message.optString("content").takeIf { it.isNotBlank() }
    }

    /**
     * 从第三方错误响应里提取**给用户看的那一小段**描述。
     *
     * 优先取 `error.message`；否则截断原始正文。注意：完整正文只用于这里的一次性提取，
     * **不写日志**（可能含 request id 与调试回显）。
     */
    fun extractErrorDetail(body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        if (!message.isNullOrBlank()) {
            return message.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(150)
        }
        return "服务未提供可读的 JSON 错误信息"
    }

    /** 统一的 HTTP 错误文案。保持与抽取前完全一致的措辞，避免改变用户已熟悉的提示。 */
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

/** AI 调用失败。message 已经是**可直接展示给用户**的文案。 */
internal class AiException(
    message: String,
    cause: Throwable? = null,
    val failure: AiFailure? = null
) : Exception(message, cause)
