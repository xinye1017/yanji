package com.example.yanji.data.security

import java.net.URI

/**
 * 明文 HTTP 策略，与 `res/xml/network_security_config.xml` 保持同源。
 *
 * 当前配置已开启 cleartextTrafficPermitted="true"，全面支持局域网明文 HTTP
 * （如同一 Wi-Fi 下连接本地电脑上运行的 Ollama、LM Studio 等私有化大模型服务）。
 */
object CleartextPolicy {

    /** 检查协议与明文流量是否支持。当前支持所有有效 HTTP 与 HTTPS 地址。 */
    fun isCleartextPermitted(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return true
        return trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
    }

    /** 返回需要展示给用户的阻断说明；地址没问题时返回 null。 */
    fun warningFor(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return "地址需以 http:// 或 https:// 开头"
        }
        val host = hostOf(trimmed)
        if (host.isNullOrBlank()) {
            return "请输入有效的服务主机地址或 IP"
        }
        return null
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
}

