package com.example.yanji.ui.note

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.text.AnnotatedString
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

    @Test
    fun markupOnlyDocumentStripsToEmpty() {
        // 整篇只有标记时会被剥成空串：列表摘要因此需要一个「退回原文」的兜底分支。
        assertEquals("", stripNoteMarkdown("---\n\n———\n"))
    }

    @Test
    fun listSnippetStripsMarkersButKeepsText() {
        assertEquals(
            "标题\n今天完成了 数学复盘。",
            noteListSnippet("# 标题\n今天完成了 **数学复盘**。")
        )
    }

    @Test
    fun listSnippetFallsBackToRawTextWhenNothingSurvivesStripping() {
        assertEquals("---", noteListSnippet("---"))
        assertEquals("———", noteListSnippet("———"))
        assertEquals("", noteListSnippet(""))
    }

    // ========================================================================
    // 四、行内高亮的跨行边界不变量：逐行扫描必须与整篇扫描得出同一批样式区间
    // ========================================================================

    private fun lineBounds(doc: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var base = 0
        for (line in doc.split('\n')) {
            out.add(base until base + line.length)
            base += line.length + 1
        }
        return out
    }

    private fun styleRanges(doc: String, predicate: (androidx.compose.ui.text.SpanStyle) -> Boolean): List<IntRange> =
        MarkdownSyntaxTransformation.highlightMarkdown(doc, darkColorScheme())
            .spanStyles
            .filter { predicate(it.item) }
            .map { it.start until it.end }

    @Test
    fun everyHighlightStaysInsideASingleLine() {
        val doc = "# 标题 **粗**\n- [ ] 任务 *斜* `code`\n> 引用 _under_ 与 [链接](https://a.b)\n***\n分割线下一段\n"
        val bounds = lineBounds(doc)
        val all = styleRanges(doc) { true }
        assertEquals("应确实产生了高亮样式，否则本用例没有覆盖到任何东西", true, all.isNotEmpty())
        all.forEach { range ->
            assertTrue(
                "样式区间 $range 必须完整落在某一行内（行界：$bounds）",
                bounds.any { line -> range.first >= line.first && range.last <= line.last }
            )
        }
    }

    @Test
    fun italicsOnSeparateLinesAreBothHighlighted() {
        assertEquals(
            "两行各自独立的 *斜体* 都应被点亮",
            2,
            styleRanges("*重点*\n*另一段*") { it.fontStyle == FontStyle.Italic && it.fontWeight == null }.size
        )
    }

    @Test
    fun boldMarkersSplitAcrossLinesDoNotMatch() {
        assertTrue(
            "`**` 与 `**` 分居两行不构成粗体",
            styleRanges("**\n**") { it.fontWeight == FontWeight.Bold }.isEmpty()
        )
    }

    @Test
    fun lineStartLookbehindSeesNewlineInsteadOfPreviousLineMarker() {
        // 上一行以 '*' 结尾不得抑制本行行首的斜体：整篇扫描里前一个字符是 '\n'。
        assertEquals(
            1,
            styleRanges("a*\n*b*") { it.fontStyle == FontStyle.Italic && it.fontWeight == null }.size
        )
    }

    // ========================================================================
    // 五、待办切换的字面量特征测试（收敛 12 组硬编码分支前的等价性基线）
    // ========================================================================

    private fun toggleAt(doc: String, index: Int) = MarkdownEditorOps.toggleTaskItemAtLine(doc, index)

    @Test
    fun taskToggleCoversEveryBulletMarker() {
        for (b in listOf('-', '*', '+', '•')) {
            assertEquals("$b [x] 项目", toggleAt("$b [ ] 项目", 0))
            assertEquals("$b [ ] 项目", toggleAt("$b [x] 项目", 0))
            assertEquals("$b [ ] 项目", toggleAt("$b [X] 项目", 0))
        }
    }

    @Test
    fun taskToggleLeavesNonTaskLinesAndOutOfRangeIndicesUntouched() {
        assertEquals("普通一行", toggleAt("普通一行", 0))
        assertEquals("# 标题", toggleAt("# 标题", 0))
        assertEquals("a\nb", toggleAt("a\nb", 5))
    }

    @Test
    fun taskToggleAppliesFirstMarkerInLegacyOrderWhenLineHasSeveral() {
        // 旧实现按 - * + • 顺序、同种符号内按 [ ] → [x] → [X] 检查，并只替换第一处。
        assertEquals("- [x] 甲 - [ ] 乙", toggleAt("- [ ] 甲 - [ ] 乙", 0))
        assertEquals("- [x] 甲 * [ ] 乙", toggleAt("- [ ] 甲 * [ ] 乙", 0))
        // 首列已是 [x] 时，同一行里更靠后的 - [ ] 会被取消勾选（replaceFirst 命中第一个 "- [ ] "）。
        assertEquals("- [ ] 甲 * [ ] 乙", toggleAt("- [x] 甲 * [ ] 乙", 0))
    }

    @Test
    fun cachedLineSpansProduceIdenticalHighlightingToAColdCache() {
        val scheme = darkColorScheme()
        val docA = "# 标题 **粗**\n- [ ] 任务 *斜* `code`\n> 引用 _under_ 与 [链接](https://a.b)\n***\n再来一行 ***混*** ~~删~~\n"

        fun spansOf(t: MarkdownSyntaxTransformation, doc: String) =
            t.filter(AnnotatedString(doc)).text.spanStyles.map { Triple(it.item, it.start, it.end) }

        val cold = spansOf(MarkdownSyntaxTransformation(scheme), docA)

        val hot = MarkdownSyntaxTransformation(scheme)
        hot.filter(AnnotatedString(docA))                                 // 填充行缓存
        hot.filter(AnnotatedString("另一篇 **文档**"))                     // 挤掉整篇级单条目记忆
        val warm = spansOf(hot, docA)                                     // 走行缓存命中路径

        assertEquals("行缓存命中后必须与冷缓存产出完全一致的样式集合", cold, warm)
        assertTrue("用例本身要有产出，否则比较的是两个空集合", cold.isNotEmpty())
    }

    @Test
    fun cacheEvictionStillProducesIdenticalHighlighting() {
        val scheme = darkColorScheme()

        fun spansOf(t: MarkdownSyntaxTransformation, line: String) =
            t.filter(AnnotatedString(line)).text.spanStyles.map { Triple(it.item, it.start, it.end) }

        val probe = "第 7 行 **粗** 与 *斜*"
        val cold = spansOf(MarkdownSyntaxTransformation(scheme), probe)

        val hot = MarkdownSyntaxTransformation(scheme)
        // 灌入远超 2048 上限的互不相同行，强制行缓存走「超限清空」分支。
        for (i in 1..3000) hot.filter(AnnotatedString("第 $i 行 **粗** 与 *斜*"))
        val after = spansOf(hot, probe)

        assertEquals("跨缓存上限被清空重建后，产出必须与冷启动完全一致", cold, after)
        assertTrue("用例须真的产生样式，否则比较无意义", cold.isNotEmpty())
    }

    // ========================================================================
    // 六、lineBoundsOf 的边界契约（F10 把 7 份复制收敛成 1 个 helper，
    //     它决定工具栏作用于「哪一行」，off-by-one 会静默改错行）
    // ========================================================================

    private val boundsDoc = "abc\ndefgh\nijk"   // 索引: 0-2 / 4-8 / 10-12

    @Test
    fun lineBoundsCoversFirstMiddleAndLastLine() {
        assertEquals(0 to 3, lineBoundsOf(boundsDoc, 0))
        assertEquals(4 to 9, lineBoundsOf(boundsDoc, 4))
        assertEquals(4 to 9, lineBoundsOf(boundsDoc, 6))
        assertEquals(10 to 13, lineBoundsOf(boundsDoc, 11))
    }

    @Test
    fun lineBoundsOnNewlineCharacterStaysOnPrecedingLine() {
        assertEquals(0 to 3, lineBoundsOf(boundsDoc, 3))
        assertEquals(4 to 9, lineBoundsOf(boundsDoc, 8))
    }

    @Test
    fun lineBoundsAtEndOfTextAndBeyondLengthClamp() {
        assertEquals(10 to 13, lineBoundsOf(boundsDoc, 13))
        assertEquals(10 to 13, lineBoundsOf(boundsDoc, 999))
    }

    @Test
    fun lineBoundsHandlesEmptyAndTrailingNewlineText() {
        assertEquals(0 to 0, lineBoundsOf("", 0))
        // 以换行结尾时，光标落在末尾就是一个真实的空行（长度为 0），不应回退到上一行。
        assertEquals(4 to 4, lineBoundsOf("abc\n", 4))
        assertEquals(0 to 3, lineBoundsOf("abc\n", 0))
        assertEquals(1 to 1, lineBoundsOf("\n", 1))
    }

    @Test
    fun lineBoundsResultIsAlwaysASubstringInTheText() {
        for (offset in 0..boundsDoc.length) {
            val (start, end) = lineBoundsOf(boundsDoc, offset)
            assertTrue("offset=$offset 时区间越界: $start..$end", start in 0..boundsDoc.length && end in start..boundsDoc.length)
            val line = boundsDoc.substring(start, end)
            assertTrue("offset=$offset 取出的行不应含换行: [$line]", !line.contains('\n'))
            assertTrue("offset=$offset 应落在取出的行内", offset in start..end)
        }
    }

    @Test
    fun lineCacheLowersPerKeystrokeHighlightCost() {
        val scheme = darkColorScheme()

        // 交替喂两份不同文本：让 F2b 的整篇级单条目记忆每轮都未命中，
        // 这样测到的才是一行输入触发的真实高亮成本，而不是缓存命中路径。
        fun timeAlternating(t: MarkdownSyntaxTransformation, a: AnnotatedString, b: AnnotatedString): Long {
            var i = 0
            while (i < 6) { t.filter(a); t.filter(b); i++ }
            val started = System.nanoTime()
            i = 0
            while (i < 60) { t.filter(a); t.filter(b); i++ }
            return (System.nanoTime() - started) / 120 / 1000
        }

        for (lines in listOf(240, 1200)) {
            val stem = (1..lines).joinToString("\n") { "第 $it 条复习记录 **高数** 与 *英语* 已完成" }
            val docA = AnnotatedString(stem + "！")
            val docB = AnnotatedString(stem + "？")
            val cold = timeAlternating(MarkdownSyntaxTransformation(scheme), docA, docB)
            val hot = MarkdownSyntaxTransformation(scheme)
            hot.filter(docA); hot.filter(docB)
            val warm = timeAlternating(hot, docA, docB)
            println("HILITE-COST lines=$lines cold=${cold}us warm=${warm}us")
            assertTrue("lines=$lines 热路径不得慢于冷路径 (cold=$cold, warm=$warm)", warm <= cold)
        }
    }
}
