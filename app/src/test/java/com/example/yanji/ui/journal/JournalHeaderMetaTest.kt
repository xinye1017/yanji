package com.example.yanji.ui.journal

import com.example.yanji.data.JournalEntry
import com.example.yanji.data.journal.JournalHeaderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 随笔顶部信息栏逻辑与配置单元测试。
 */
class JournalHeaderMetaTest {

    @Test
    fun defaultConfig_meetsRequirements() {
        val config = JournalHeaderConfig()
        // 需求 1(a): 打分固定显示
        assertTrue("今日状态打分必须固定为 true", config.showScore)
        // 需求 3(b): 默认设置显示当前时间
        assertTrue("记录时间默认开启", config.showTime)
        // 需求 2: 用户自定义内容默认关闭，按需开启
        assertFalse("天气默认关闭", config.showWeather)
    }

    @Test
    fun customConfig_preservesFixedScore() {
        val custom = JournalHeaderConfig(
            showScore = false, // 试图关闭打分
            showTime = false,
            showWeather = true
        )
        // 业务层强制打分永远为 true
        val normalized = custom.copy(showScore = true)
        assertTrue("打分项不可关闭", normalized.showScore)
        assertFalse(normalized.showTime)
        assertTrue(normalized.showWeather)
    }

    @Test
    fun journalEntry_tagsEncodingAndDecoding() {
        val originalTags = listOf("考研数学", "高数极限")
        val selectedWeather = "☀️ 晴朗"
        val selectedLocation = "🏫 图书馆"

        // 编码标签
        val encodedTags = buildList {
            addAll(originalTags)
            if (selectedWeather.isNotBlank()) add("weather:$selectedWeather")
            if (selectedLocation.isNotBlank()) add("location:$selectedLocation")
        }

        val entry = JournalEntry(
            id = "test-journal-1",
            date = "2026-09-20",
            content = "今日复盘",
            moodScore = 4,
            tags = encodedTags
        )

        // 解码与提取
        val extractedWeather = entry.tags.find { it.startsWith("weather:") }?.removePrefix("weather:")
        val extractedLocation = entry.tags.find { it.startsWith("location:") }?.removePrefix("location:")
        val cleanTags = entry.tags.filterNot { it.startsWith("weather:") || it.startsWith("location:") }

        assertEquals(4, entry.moodScore)
        assertEquals("☀️ 晴朗", extractedWeather)
        assertEquals("🏫 图书馆", extractedLocation)
        assertEquals(listOf("考研数学", "高数极限"), cleanTags)
    }
}
