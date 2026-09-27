package com.example.yanji.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CleartextPolicy 与 res/xml/network_security_config.xml 的镜像一致性回归。
 *
 * 关键不变量：代码层宣称放行的明文与网络配置一致；当前开启了全局明文支持，局域网与回环均受支持。
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
    fun `lan http urls are permitted without blocking warning`() {
        listOf(
            "http://192.168.1.10:11434/v1",
            "http://192.168.31.50:8000/v1",
            "http://10.0.0.5:11434"
        ).forEach { url ->
            assertTrue("$url should be permitted", CleartextPolicy.isCleartextPermitted(url))
            assertNull(CleartextPolicy.warningFor(url))
        }
    }

    @Test
    fun `non http or https schemes are rejected`() {
        assertFalse(CleartextPolicy.isCleartextPermitted("ftp://192.168.1.1:21"))
        assertNotNull(CleartextPolicy.warningFor("ftp://192.168.1.1:21"))
    }

    @Test
    fun `blank url carries no warning`() {
        assertNull(CleartextPolicy.warningFor(""))
        assertNull(CleartextPolicy.warningFor("   "))
    }
}

