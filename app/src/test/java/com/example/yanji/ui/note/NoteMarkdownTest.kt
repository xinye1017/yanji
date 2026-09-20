package com.example.yanji.ui.note

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 研迹 Markdown 引擎（解析器、语法高亮、操作助手、纯文本提取）行为契约与完整单测。
 */
class NoteMarkdownTest {

    // ========================================================================
    // 一、实时语法高亮（VisualTransformation）1:1 映射不变量
    // ========================================================================

    @Test
    fun syntaxHighlightPreservesExactLength() {
        val testStrings = listOf(
            "# 标题一",
            "**粗体文字** 和 _斜体文字_ 以及 `val x = 1`",
            "> 引用内容",
            "- [ ] 待办项",
            "- [x] 已完成项",
            "---",
            "混合内容 **粗** ~~删~~ [标题](https://example.com)",
            ""
        )

        val dummyColors = darkColorScheme()
        for (raw in testStrings) {
            val highlighted = MarkdownSyntaxTransformation.highlightMarkdown(raw, dummyColors)
            assertEquals("高亮结果长度必须完全等于原始文本长度（OffsetMapping.Identity 前提）", raw.length, highlighted.text.length)
            assertEquals(raw, highlighted.text)
        }
    }

    @Test
    fun syntaxHighlightAppliesSpanStyles() {
        val raw = "**加粗** _斜体_ ~~删除~~ `代码`"
        val dummyColors = darkColorScheme()
        val highlighted = MarkdownSyntaxTransformation.highlightMarkdown(raw, dummyColors)

        val styles = highlighted.spanStyles.map { it.item }
        assertTrue("应有加粗样式", styles.any { it.fontWeight == FontWeight.Bold })
        assertTrue("应有斜体样式", styles.any { it.fontStyle == FontStyle.Italic })
        assertTrue("应有删除线样式", styles.any { it.textDecoration == TextDecoration.LineThrough })
    }

    // ========================================================================
    // 二、编辑器操作助手（MarkdownEditorOps）
    // ========================================================================

    @Test
    fun toggleInlineTokenEmptySelectionInsertsPairAndParksCursor() {
        val (text, sel) = MarkdownEditorOps.toggleInlineToken("ab", 1, 1, "**")
        assertEquals("a****b", text)
        assertEquals(3, sel.min)
        assertEquals(3, sel.max)
    }

    @Test
    fun toggleInlineTokenWrapsSelectedText() {
        val (text, sel) = MarkdownEditorOps.toggleInlineToken("重点内容", 0, 2, "**")
        assertEquals("**重点**内容", text)
        assertEquals(2, sel.min)
        assertEquals(4, sel.max)
    }

    @Test
    fun toggleInlineTokenUnwrapsAlreadyWrappedText() {
        val (text, sel) = MarkdownEditorOps.toggleInlineToken("**重点**内容", 0, 6, "**")
        assertEquals("重点内容", text)
        assertEquals(0, sel.min)
        assertEquals(2, sel.max)
    }

    @Test
    fun italicAndBoldCanBeToggledIndependently() {
        // 已有粗体时再点斜体，应叠加为粗斜体，而不是拆掉一层粗体。
        val (boldItalic, boldItalicSel) = MarkdownEditorOps.toggleInlineToken("**重点**", 2, 4, "*")
        assertEquals("***重点***", boldItalic)
        assertEquals(3, boldItalicSel.min)
        assertEquals(5, boldItalicSel.max)

        // 再点斜体，只去掉斜体这一层，保留粗体。
        val (boldOnly, boldOnlySel) = MarkdownEditorOps.toggleInlineToken(
            boldItalic,
            boldItalicSel.min,
            boldItalicSel.max,
            "*"
        )
        assertEquals("**重点**", boldOnly)
        assertEquals(2, boldOnlySel.min)
        assertEquals(4, boldOnlySel.max)

        // 已有斜体时点粗体，应叠加成粗斜体。
        val (bothFromItalic, _) = MarkdownEditorOps.toggleInlineToken("*重点*", 1, 3, "**")
        assertEquals("***重点***", bothFromItalic)
    }

    @Test
    fun surroundingDelimiterRunDistinguishesItalicBoldAndCombined() {
        assertEquals(1, MarkdownEditorOps.surroundingDelimiterRun("*重点*", 1, 3, '*'))
        assertEquals(2, MarkdownEditorOps.surroundingDelimiterRun("**重点**", 2, 4, '*'))
        assertEquals(3, MarkdownEditorOps.surroundingDelimiterRun("***重点***", 3, 5, '*'))
    }

    @Test
    fun cycleHeadingProgression() {
        var text = "复习高数"
        var sel = TextRange(2, 2)

        // 无 -> #
        var res = MarkdownEditorOps.cycleHeading(text, sel)
        assertEquals("# 复习高数", res.first)

        // # -> ##
        res = MarkdownEditorOps.cycleHeading(res.first, res.second)
        assertEquals("## 复习高数", res.first)

        // ## -> ###
        res = MarkdownEditorOps.cycleHeading(res.first, res.second)
        assertEquals("### 复习高数", res.first)

        // ### -> 无
        res = MarkdownEditorOps.cycleHeading(res.first, res.second)
        assertEquals("复习高数", res.first)
    }

    @Test
    fun toggleLinePrefixTogglesOnAndOff() {
        val text = "背诵考研单词"
        val sel = TextRange(2, 2)

        val (added, selAdded) = MarkdownEditorOps.toggleLinePrefix(text, sel, "- [ ] ")
        assertEquals("- [ ] 背诵考研单词", added)

        val (removed, _) = MarkdownEditorOps.toggleLinePrefix(added, selAdded, "- [ ] ")
        assertEquals("背诵考研单词", removed)
    }

    @Test
    fun toggleTaskItemAtLineInPreview() {
        val doc = """
            # 今日任务
            - [ ] 数学真题
            - [x] 英语阅读
        """.trimIndent()

        // 勾选题 1
        val docChecked = MarkdownEditorOps.toggleTaskItemAtLine(doc, 1)
        assertTrue(docChecked.contains("- [x] 数学真题"))

        // 取消勾选题 2
        val docUnchecked = MarkdownEditorOps.toggleTaskItemAtLine(docChecked, 2)
        assertTrue(docUnchecked.contains("- [ ] 英语阅读"))
    }

    @Test
    fun insertDividerOnEmptyLineReplacesDirectly() {
        val text = ""
        val (res, _) = MarkdownEditorOps.insertDivider(text, TextRange(0, 0))
        assertEquals("---", res)
    }

    @Test
    fun toggleCodeWrapsMultilineAsFencedBlock() {
        val text = "line1\nline2"
        val (res, _) = MarkdownEditorOps.toggleCode(text, TextRange(0, text.length))
        assertEquals("```\nline1\nline2\n```", res)
    }

    // ========================================================================
    // 三、纯文本提取测试（stripNoteMarkdown）
    // ========================================================================

    @Test
    fun stripNoteMarkdownCleansTokens() {
        val md = """
            # 今日复盘
            今天完成了 **数学复盘** 与 _英语阅读_。
            > 笃行致远
            - [x] 任务一
            - [ ] 任务二
            重点公式：`E = mc^2`，详见 [链接](https://yanji.app)
            ---
        """.trimIndent()

        val plain = stripNoteMarkdown(md)
        assertFalse(plain.contains("#"))
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains("_"))
        assertFalse(plain.contains(">"))
        assertFalse(plain.contains("- [x]"))
        assertFalse(plain.contains("- [ ]"))
        assertFalse(plain.contains("`"))
        assertFalse(plain.contains("---"))
        assertTrue(plain.contains("今日复盘"))
        assertTrue(plain.contains("今天完成了 数学复盘 与 英语阅读。"))
        assertTrue(plain.contains("重点公式：E = mc^2，详见 链接"))
    }
}
