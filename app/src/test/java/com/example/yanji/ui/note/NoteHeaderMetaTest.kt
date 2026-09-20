package com.example.yanji.ui.note

import com.example.yanji.data.NoteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「随笔顶部信息栏」配置功能已移除（时间固定展示、天气不再展示），
 * 仅保留 NoteEntry 标签编解码测试：既有随笔的 weather:/location: 标签仍需原样保留。
 */
class NoteHeaderMetaTest {

    @Test
    fun noteEntry_tagsEncodingAndDecoding() {
        val entry = NoteEntry(
            id = "test-id",
            date = "2026-01-01",
            createdAt = 1000L,
            updatedAt = 1000L,
            content = "hello",
            tags = listOf("weather:sunny", "location:library")
        )
        assertEquals(listOf("weather:sunny", "location:library"), entry.tags)
        assertTrue(entry.tags.any { it.startsWith("weather:") })
    }
}
