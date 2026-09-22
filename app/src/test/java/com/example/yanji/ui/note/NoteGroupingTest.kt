package com.example.yanji.ui.note

import com.example.yanji.data.NoteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 随笔历史页「按日期分组」的纯函数测试。
 *
 * 这是一天多篇（v15）的显示不变量守门测试：分组顺序、组内排序与日期头文案
 * 一旦被改动，用户看到的「9月21日 → 该日所有随笔」结构就会错乱。
 */
class NoteGroupingTest {

    private fun entry(
        id: String,
        date: String,
        createdAt: Long,
        favorite: Boolean = false
    ) = NoteEntry(
        id = id,
        date = date,
        content = "内容-$id",
        createdAt = createdAt,
        updatedAt = createdAt,
        isFavorite = favorite
    )

    @Test
    fun groupsByDateDescendingAndEntriesByCreatedAtDescending() {
        val entries = listOf(
            // DAO 已按 date DESC, createdAt DESC 返回
            entry("a", "2026-09-21", createdAt = 300L),
            entry("b", "2026-09-21", createdAt = 100L),
            entry("c", "2026-09-20", createdAt = 200L)
        )

        val state = NoteViewModel.groupsOf(entries)

        assertEquals("应有 2 个日期分组", 2, state.groups.size)
        assertEquals("2026-09-21", state.groups[0].date)
        assertEquals("2026-09-20", state.groups[1].date)
        assertEquals("同一天可有任意篇数", 2, state.groups[0].entries.size)
        // 组内最新在上
        assertEquals(listOf("a", "b"), state.groups[0].entries.map { it.id })
        assertEquals(listOf("c"), state.groups[1].entries.map { it.id })
    }

    @Test
    fun groupOrderIsStableEvenWhenInputIsUnsorted() {
        val entries = listOf(
            entry("old", "2026-09-19", createdAt = 10L),
            entry("new", "2026-09-22", createdAt = 30L),
            entry("mid", "2026-09-21", createdAt = 20L)
        )

        val state = NoteViewModel.groupsOf(entries)

        assertEquals(
            listOf("2026-09-22", "2026-09-21", "2026-09-19"),
            state.groups.map { it.date }
        )
    }

    @Test
    fun multipleEntriesInOneDayAreAllRetained() {
        val entries = (1..5).map { i ->
            entry("j$i", "2026-09-21", createdAt = i * 100L)
        }

        val state = NoteViewModel.groupsOf(entries)

        assertEquals(1, state.groups.size)
        assertEquals(5, state.groups[0].entries.size)
        // 全部保留，且倒序
        assertEquals(listOf("j5", "j4", "j3", "j2", "j1"), state.groups[0].entries.map { it.id })
    }

    @Test
    fun emptyInputProducesNoGroups() {
        val state = NoteViewModel.groupsOf(emptyList())
        assertTrue(state.groups.isEmpty())
        assertTrue(state.notes.isEmpty())
    }

    @Test
    fun favoriteFlagSurvivesGrouping() {
        val entries = listOf(
            entry("fav", "2026-09-21", createdAt = 200L, favorite = true),
            entry("plain", "2026-09-21", createdAt = 100L)
        )

        val state = NoteViewModel.groupsOf(entries)

        assertTrue(state.groups[0].entries.first { it.id == "fav" }.isFavorite)
        assertTrue(!state.groups[0].entries.first { it.id == "plain" }.isFavorite)
    }

    @Test
    fun dayHeaderLabelRendersChineseMonthAndDay() {
        assertEquals("9月21日", noteDayHeaderLabel("2026-09-21"))
        assertEquals("1月1日", noteDayHeaderLabel("2026-01-01"))
        assertEquals("12月31日", noteDayHeaderLabel("2026-12-31"))
    }

    @Test
    fun dayHeaderLabelFallsBackToRawValueWhenUnparseable() {
        assertEquals("not-a-date", noteDayHeaderLabel("not-a-date"))
    }

    // ------------------------------------------------------------ 搜索过滤

    @Test
    fun emptyQueryReturnsEverything() {
        val entries = listOf(entry("a", "2026-09-21", 1L), entry("b", "2026-09-20", 2L))
        assertEquals(entries, filterNotes(entries, ""))
        assertEquals(entries, filterNotes(entries, "   "))
    }

    @Test
    fun keywordMatchesTitleContentAndTags() {
        val target = NoteEntry(id = "x", date = "2026-09-21", title = "数学复盘", content = "中值定理失分")
        val tagged = NoteEntry(id = "y", date = "2026-09-20", content = "今天", tags = listOf("待办计划"))
        val other = NoteEntry(id = "z", date = "2026-09-19", content = "英语阅读")
        val all = listOf(target, tagged, other)

        assertEquals(listOf("x"), filterNotes(all, "定理").map { it.id })
        assertEquals(listOf("x"), filterNotes(all, "复盘").map { it.id })
        assertEquals(listOf("y"), filterNotes(all, "待办").map { it.id })
        assertTrue(filterNotes(all, "不存在的词").isEmpty())
    }

    @Test
    fun keywordMatchIsCaseInsensitive() {
        val e = NoteEntry(id = "x", date = "2026-09-21", content = "DeepSeek 分析")
        assertEquals(listOf("x"), filterNotes(listOf(e), "deepseek").map { it.id })
    }

    @Test
    fun dateQueryMatchesIsoAndChineseForms() {
        val a = NoteEntry(id = "a", date = "2026-09-21", content = "x")
        val b = NoteEntry(id = "b", date = "2026-09-20", content = "y")
        val all = listOf(a, b)

        // ISO 全写
        assertEquals(listOf("a"), filterNotes(all, "2026-09-21").map { it.id })
        // 月-日
        assertEquals(listOf("a"), filterNotes(all, "09-21").map { it.id })
        // 中文「9月21日」
        assertEquals(listOf("a"), filterNotes(all, "9月21日").map { it.id })
        // 斜杠
        assertEquals(listOf("a"), filterNotes(all, "9/21").map { it.id })
    }

    @Test
    fun searchThenGroupKeepsDayStructure() {
        val all = listOf(
            NoteEntry(id = "m1", date = "2026-09-21", content = "数学一"),
            NoteEntry(id = "m2", date = "2026-09-21", content = "数学二"),
            NoteEntry(id = "e1", date = "2026-09-21", content = "英语")
        )
        val filtered = filterNotes(all, "数学")
        val groups = NoteViewModel.groupsOf(filtered).groups

        assertEquals(1, groups.size)
        assertEquals("2026-09-21", groups[0].date)
        assertEquals(listOf("m1", "m2"), groups[0].entries.map { it.id }.sorted())
    }

    @Test
    fun prebuiltIndexMatchesDirectFilteringForEveryQueryShape() {
        val all = listOf(
            NoteEntry(
                id = "a",
                date = "2026-09-21",
                title = "数学复盘",
                content = "**深** 定理",
                tags = listOf("HighP")
            ),
            NoteEntry(
                id = "b",
                date = "2026-09-20",
                title = "english",
                content = "DEEPSEEK 用法",
                tags = emptyList()
            )
        )
        val index = searchableNotes(all)
        val queries = listOf(
            "", "   ", "定理", "deepseek", "HIGHp", "DEEP", "复盘",
            "9月21日", "09-20", "2026-09", "不存在的词", "*深*"
        )
        queries.forEach { query ->
            assertEquals(
                "查询「$query」下索引路径与直算路径结果必须一致",
                filterNotes(all, query),
                filterSearchable(index, query)
            )
        }
    }

    @Test
    fun searchMatchesDisplayTextRatherThanMarkup() {
        val all = listOf(
            NoteEntry(id = "s1", date = "2026-09-21", content = "今天完成 **数学复盘** 与 _英语_")
        )

        assertEquals(listOf("s1"), filterNotes(all, "数学复盘").map { it.id })
        assertTrue("markdown 标记本身不应成为可搜内容", filterNotes(all, "**").isEmpty())
        assertTrue("按原文带标记的查询词不应命中", filterNotes(all, "**数学**").isEmpty())
    }
}
