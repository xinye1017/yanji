package com.example.yanji.data.ai

import java.io.IOException
import java.net.SocketTimeoutException

/** Stable domain failures consumed by UI and tests; raw provider bodies are never exposed. */
sealed interface AiFailure {
    val userMessage: String

    data object Authentication : AiFailure {
        override val userMessage: String = "API Key 无效或无权访问，请检查 AI 设置。"
    }

    data object Quota : AiFailure {
        override val userMessage: String = "AI 服务额度不足或请求过于频繁，请稍后重试。"
    }

    data object Endpoint : AiFailure {
        override val userMessage: String = "未找到 AI 分析接口，请检查 Base URL。"
    }

    data object Timeout : AiFailure {
        override val userMessage: String = "AI 请求超时，请检查网络后重试。"
    }

    data object Network : AiFailure {
        override val userMessage: String = "无法连接 AI 服务，请检查网络后重试。"
    }

    data object Cancelled : AiFailure {
        override val userMessage: String = "AI 请求已取消，可以重新生成。"
    }

    data object InvalidResponse : AiFailure {
        override val userMessage: String = "AI 返回了无法识别的内容，请重试。"
    }

    data object ClientRequest : AiFailure {
        override val userMessage: String = "AI 服务拒绝了请求，请检查模型与接口设置。"
    }

    data object Service : AiFailure {
        override val userMessage: String = "AI 服务暂时异常，请稍后重试。"
    }

    companion object {
        fun from(throwable: Throwable): AiFailure {
            if (throwable is AiException && throwable.failure != null) return throwable.failure
            if (throwable is SocketTimeoutException) return Timeout
            if (throwable is IOException) return Network
            val message = throwable.message.orEmpty().lowercase()
            return when {
                "401" in message || "403" in message || "unauthorized" in message || "api key" in message -> Authentication
                "429" in message || "额度" in message || "rate limit" in message -> Quota
                "404" in message || "base url" in message || "未找到分析接口" in message -> Endpoint
                "超时" in message || "timeout" in message -> Timeout
                "返回内容为空" in message || "invalid" in message || "parse" in message -> InvalidResponse
                else -> Service
            }
        }

        fun fromHttpStatus(code: Int): AiFailure = when (code) {
            401, 403 -> Authentication
            404 -> Endpoint
            429 -> Quota
            in 400..499 -> ClientRequest
            else -> Service
        }
    }
}
