package com.example.yanji.data.security

import java.net.URI

/**
 * 明文 HTTP 策略，与 `res/xml/network_security_config.xml` 保持同源。
 *
 * Manifest 已移除 `android:usesCleartextTraffic="true"`，平台默认拒绝明文流量；
 * 只有 [ALLOWED_CLEARTEXT_HOSTS] 中的回环 / 模拟器宿主被显式放行。
 *
 * 这里把同一套规则暴露给 UI，是为了让用户在填写 Base URL 时就被明确告知
 * 「这个地址会被系统拦掉」，而不是保存后拿到一个看不懂的网络异常。
 */
object CleartextPolicy {

    /** 与 network_security_config.xml 的 domain-config 一一对应。 */
    val ALLOWED_CLEARTEXT_HOSTS: Set<String> = setOf(
        "127.0.0.1",
        "localhost",
        "10.0.2.2",
        "::1",
        "0:0:0:0:0:0:0:1"
    )

    fun isCleartextPermitted(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true)) return true
        val host = hostOf(trimmed) ?: return false
        return host.lowercase() in ALLOWED_CLEARTEXT_HOSTS
    }

    /** 返回需要展示给用户的阻断说明；地址没问题时返回 null。 */
    fun warningFor(rawUrl: String): String? {
        if (rawUrl.isBlank() || isCleartextPermitted(rawUrl)) return null
        val host = hostOf(rawUrl.trim()).orEmpty()
        return buildString {
            append("系统已禁止明文 HTTP（当前仅放行本机回环地址），该地址（$host）的请求会在网络层被拒绝。\n")
            append("如果目标是局域网内的 HTTP 服务，请任选其一：\n")
            append("  1) 给它套一层 HTTPS 反向代理，然后把地址填成 https://…；\n")
            append("  2) 在电脑上执行 adb reverse tcp:<端口> tcp:<端口>，地址填 http://127.0.0.1:<端口>/v1。")
        }
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
}
