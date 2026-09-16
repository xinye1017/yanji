package com.example.yanji.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CleartextPolicy 与 res/xml/network_security_config.xml 的镜像一致性回归。
 *
 * 关键不变量：代码层宣称放行的明文 host 集合必须与平台网络配置一致；
 * IPv6 回环无法被 network_security_config 放行，因此这里也必须拒绝，
 * 否则 UI 不提示、运行时却被系统拦截，行为分裂。
 */
class CleartextPolicyTest {

    @Test
    fun `https urls are always permitted`() {
        assertTrue(CleartextPolicy.isCleartextPermitted("https://api.deepseek.com/v1"))
        assertNull(CleartextPolicy.warningFor("https://api.deepseek.com/v1"))
    }

    @Test
    fun `loopback and emulator hosts are permitted over http`() {
        listOf(
            "http://127.0.0.1:11434/v1",
            "http://localhost:11434/v1",
            "http://10.0.2.2:11434/v1"
        ).forEach { url ->
            assertTrue("$url should be permitted", CleartextPolicy.isCleartextPermitted(url))
            assertNull(CleartextPolicy.warningFor(url))
        }
    }

    @Test
    fun `ipv6 loopback is not permitted because platform config cannot whitelist it`() {
        // network_security_config.xml 的 <domain> 只按主机名匹配，放不了 IPv6 字面量；
        // 平台网络层会拒绝该请求，所以 UI 必须在保存前就提示。
        assertFalse(CleartextPolicy.isCleartextPermitted("http://[::1]:11434/v1"))
        assertTrue(
            CleartextPolicy.warningFor("http://[::1]:11434/v1").orEmpty().contains("[::1]")
        )
    }

    @Test
    fun `lan http urls are rejected with a warning naming the host`() {
        assertFalse(CleartextPolicy.isCleartextPermitted("http://192.168.1.10:11434/v1"))
        assertTrue(
            CleartextPolicy.warningFor("http://192.168.1.10:11434/v1").orEmpty()
                .contains("192.168.1.10")
        )
    }

    @Test
    fun `blank url carries no warning`() {
        assertNull(CleartextPolicy.warningFor(""))
        assertNull(CleartextPolicy.warningFor("   "))
    }

    @Test
    fun `allowed set mirrors network security config exactly`() {
        assertEquals(
            setOf("127.0.0.1", "localhost", "10.0.2.2"),
            CleartextPolicy.ALLOWED_CLEARTEXT_HOSTS
        )
    }
}
