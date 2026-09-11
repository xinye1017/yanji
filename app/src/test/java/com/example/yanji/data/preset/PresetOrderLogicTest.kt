package com.example.yanji.data.preset

import com.example.yanji.data.QuickStartPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 快捷操作排序的纯逻辑测试：拖动排序是自研实现，错位会直接体现在首页。 */
class PresetOrderLogicTest {

    private fun preset(id: String, order: Int) =
        QuickStartPreset(id = id, type = QuickStartPreset.TYPE_CUSTOM, label = id, sortOrder = order)

    private fun three() = listOf(preset("a", 0), preset("b", 1), preset("c", 2))

    @Test
    fun `withNextOrder appends after the max sortOrder`() {
        val stored = PresetOrderLogic.withNextOrder(three(), preset("d", 99))
        assertEquals(3, stored.sortOrder)
    }

    @Test
    fun `withNextOrder on empty list starts at zero`() {
        val stored = PresetOrderLogic.withNextOrder(emptyList(), preset("d", 99))
        assertEquals(0, stored.sortOrder)
    }

    @Test
    fun `moved reorders and renumbers`() {
        val result = PresetOrderLogic.moved(three(), "c", 0)
        assertEquals(listOf("c", "a", "b"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.sortOrder })
    }

    @Test
    fun `moved to the end keeps relative order of the rest`() {
        val result = PresetOrderLogic.moved(three(), "a", 2)
        assertEquals(listOf("b", "c", "a"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.sortOrder })
    }

    @Test
    fun `moved with unknown id returns the original list unchanged`() {
        val original = three()
        assertSame(original, PresetOrderLogic.moved(original, "zzz", 0))
    }

    @Test
    fun `moved with out of range index returns the original list unchanged`() {
        val original = three()
        assertSame(original, PresetOrderLogic.moved(original, "a", 5))
        assertSame(original, PresetOrderLogic.moved(original, "a", -1))
    }

    @Test
    fun `normalized sorts by sortOrder and renumbers densely`() {
        val messy = listOf(preset("b", 5), preset("a", 1), preset("c", 9))
        val result = PresetOrderLogic.normalized(messy)
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.sortOrder })
    }
}
