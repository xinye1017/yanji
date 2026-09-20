package com.example.yanji.ui.journal

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 标记「隐形渲染」的行为契约。
 *
 * 最核心的两条不变量（改动前务必守住）：
 *  1. **渲染结果里不含标记字符** —— 用户看到的是加粗文字，不是星号。
 *  2. **偏移映射双向单调不减** —— 否则光标会随方向键乱跳、IME 候选框会飘。
 *     第 2 条用 [assertMonotonic] 穷举所有偏移来守。
 */
class JournalRichTextTest {

    // ---- 渲染：标记必须隐形 ----

    @Test
    fun boldMarkersAreHiddenFromRenderedText() {
        val rendered = journalMarkdownTransformation().filter(androidx.compose.ui.text.AnnotatedString("**粗体**")).text
        assertEquals("渲染结果不应包含星号", "粗体", rendered.text)
    }

    @Test
    fun italicAndUnderlineMarkersAreHidden() {
        fun render(s: String) =
            journalMarkdownTransformation().filter(androidx.compose.ui.text.AnnotatedString(s)).text

        assertEquals("斜体", render("_斜体_").text)
        assertEquals("下划线", render("__下划线__").text)
        assertEquals("加粗 与 斜体", render("**加粗** 与 _斜体_").text)
    }

    @Test
    fun inlineMarkersCarryExpectedStyles() {
        val annotated = journalMarkdownTransformation()
            .filter(androidx.compose.ui.text.AnnotatedString("**粗** _斜_ __下__")).text

        val styles = annotated.spanStyles.map { it.item }
        assertTrue("应有加粗", styles.any { it.fontWeight == FontWeight.SemiBold })
        assertTrue("应有斜体", styles.any { it.fontStyle == FontStyle.Italic })
        assertTrue("应有下划线", styles.any { it.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun unclosedMarkerStaysLiteral() {
        // 用户刚敲下 ** 的瞬间，不该吞字、不该整行变形。
        val rendered = journalMarkdownTransformation()
            .filter(androidx.compose.ui.text.AnnotatedString("正在输入 **粗体")).text
        assertEquals("正在输入 **粗体", rendered.text)
        assertTrue("未闭合标记不应产生样式", rendered.spanStyles.isEmpty())
    }

    @Test
    fun emptyMarkedPairIsLeftAlone() {
        val rendered = journalMarkdownTransformation()
            .filter(androidx.compose.ui.text.AnnotatedString("****")).text
        assertEquals("****", rendered.text)
        assertTrue(rendered.spanStyles.isEmpty())
    }

    @Test
    fun doubleUnderscoreDoesNotLeakItalic() {
        val annotated = journalMarkdownTransformation()
            .filter(androidx.compose.ui.text.AnnotatedString("__x__")).text
        assertEquals("x", annotated.text)
        assertTrue("应产生下划线", annotated.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
        assertTrue("不应产生斜体", annotated.spanStyles.none { it.item.fontStyle == FontStyle.Italic })
    }

    // ---- 偏移映射：单调性是硬要求 ----

    /** 穷举所有偏移，断言两个方向都单调不减。 */
    private fun assertMonotonic(source: String) {
        val spans = findMarkupSpans(source)
        val mapping = MarkupOffsetMapping(source, spans)
        val visibleLength = journalMarkdownTransformation()
            .filter(androidx.compose.ui.text.AnnotatedString(source)).text.length

        var prev = -1
        for (o in 0..source.length) {
            val t = mapping.originalToTransformed(o)
            assertTrue(
                "originalToTransformed 必须单调不减：$source 在 $o 处从 $prev 变成 $t",
                t >= prev
            )
            assertTrue("原始 $o 越界映射到 $t", t in 0..visibleLength)
            prev = t
        }

        prev = -1
        for (t in 0..visibleLength) {
            val o = mapping.transformedToOriginal(t)
            assertTrue(
                "transformedToOriginal 必须单调不减：$source 在 $t 处从 $prev 变成 $o",
                o >= prev
            )
            assertTrue("视觉 $t 越界映射到 $o", o in 0..source.length)
            prev = o
        }
    }

    @Test
    fun offsetMappingIsMonotonicForMarkedText() {
        assertMonotonic("**粗体**")
        assertMonotonic("**加粗** 与 _斜体_")
        assertMonotonic("__下划线__")
        assertMonotonic("前**中**后")
        assertMonotonic("**a****b**")
        assertMonotonic("混合 **粗** _斜_ __下__ 结束")
        assertMonotonic("未闭合 **粗体")
        assertMonotonic("普通文本没有任何标记")
        assertMonotonic("")
    }

    @Test
    fun caretAfterOpeningMarkerLandsAtStartOfVisibleText() {
        // 「按下 B 之后直接打字就是加粗」依赖这条：
        // 光标紧跟开标记之后（源码 2）在视觉上就是正文开头（0）。
        val source = "**粗**"
        val mapping = MarkupOffsetMapping(source, findMarkupSpans(source))
        assertEquals(0, mapping.originalToTransformed(2))
    }

    @Test
    fun caretBeforeOpeningMarkerStaysBeforeVisibleText() {
        val source = "**粗**"
        val mapping = MarkupOffsetMapping(source, findMarkupSpans(source))
        assertEquals(0, mapping.originalToTransformed(0))
    }

    @Test
    fun caretAtEndMapsToVisualEnd() {
        val source = "**粗**"
        val mapping = MarkupOffsetMapping(source, findMarkupSpans(source))
        assertEquals(1, mapping.originalToTransformed(source.length))
    }

    @Test
    fun visibleStartMapsBackToStartOfVisibleTextNotBeforeMarker() {
        // "**粗**" 渲染成 "粗"（长度 1）。
        // 视觉 0 反查回源码 2（正文「粗」的首字符），而不是源码 0（标记之前）。
        //
        // 为什么不映射到 0：光标停在视觉开头时，用户眼前是「粗」这个字，
        // 光标理应贴在**这个字**上。若映射到 0，光标会跑到标记里面，
        // 视觉上表现为「光标在字前，但按键却先吃掉标记」，方向键也会来回抖。
        // 左方向键从源码 2 继续往左仍可到达 0，逃出路径依然通畅。
        val source = "**粗**"
        val mapping = MarkupOffsetMapping(source, findMarkupSpans(source))
        assertEquals(2, mapping.transformedToOriginal(0))
    }

    @Test
    fun caretInsideMarkerRunDoesNotMapPastVisibleText() {
        // 光标落在标记内部（源码 0..2）时，视觉上应视为正文开头，
        // 不能越过「粗」跑到后面去。
        val source = "**粗**"
        val mapping = MarkupOffsetMapping(source, findMarkupSpans(source))
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(0, mapping.originalToTransformed(1))
        assertEquals(0, mapping.originalToTransformed(2))
    }

    // ---- 工具栏切换 ----

    @Test
    fun toggleOnEmptySelectionInsertsPairAndParksCaretInside() {
        val (text, sel) = toggleInlineMarkup("ab", 1, 1, MarkupKind.Bold)
        assertEquals("a****b", text)
        // 光标是位于两标记之间的空选区：start == end == 3。
        assertEquals(3, sel.start)
        assertEquals(3, sel.end)
        // 此时打字应落在加粗范围内。
        assertEquals("a**X**b", text.substring(0, sel.start) + "X" + text.substring(sel.end))
    }

    @Test
    fun toggleWrapsSelectionAndKeepsItSelected() {
        val (text, sel) = toggleInlineMarkup("abc", 1, 3, MarkupKind.Bold)
        assertEquals("a**bc**", text)
        // 选区仍覆盖正文 "bc"：源码 3..5（半开区间）。
        assertEquals(3, sel.start)
        assertEquals(5, sel.end)
        assertEquals("bc", text.substring(sel.start, sel.end))
    }

    @Test
    fun toggleOnWrappedSelectionUnwraps() {
        val (text, sel) = toggleInlineMarkup("a**bc**", 3, 5, MarkupKind.Bold)
        assertEquals("abc", text)
        // 正文回到源码 1..3。
        assertEquals(1, sel.start)
        assertEquals(3, sel.end)
        assertEquals("bc", text.substring(sel.start, sel.end))
    }

    @Test
    fun wrapThenUnwrapRoundTrips() {
        val original = "今天复盘"
        val (wrapped, sel) = toggleInlineMarkup(original, 0, original.length, MarkupKind.Italic)
        assertEquals("_今天复盘_", wrapped)
        val (unwrapped, _) = toggleInlineMarkup(wrapped, sel.start, sel.end, MarkupKind.Italic)
        assertEquals("再点一次应还原成原文", original, unwrapped)
    }

    @Test
    fun hasInlineMarkupReportsActiveStyleForSelection() {
        assertTrue(hasInlineMarkup("a**bc**", 3, 5, MarkupKind.Bold))
        assertFalse(hasInlineMarkup("a**bc**", 3, 5, MarkupKind.Italic))
        assertFalse(hasInlineMarkup("abc", 1, 2, MarkupKind.Bold))
    }

    @Test
    fun hasInlineMarkupReportsActiveStyleForCaretInsideMarkedRange() {
        // 无选区但光标在加粗区间内时，按钮应显示为选中态。
        assertTrue(hasInlineMarkup("**粗**", 3, 3, MarkupKind.Bold))
        assertFalse(hasInlineMarkup("普通", 1, 1, MarkupKind.Bold))
    }

    // ---- 行级样式 ----

    @Test
    fun bulletLineDetection() {
        assertTrue(isBulletLine("• 第一条", 3))
        assertFalse(isBulletLine("第一条", 2))
        assertTrue(isBulletLine("上\n• 下", 4))
    }

    @Test
    fun dividerLineDetection() {
        assertTrue(isDividerLineAt("———", 1))
        assertFalse(isDividerLineAt("正文", 1))
    }

    // ---- stripJournalMarkup ----

    @Test
    fun stripRemovesInlineMarkers() {
        assertEquals("重点 这是 斜体", stripJournalMarkup("**重点** 这是 _斜体_"))
    }

    @Test
    fun stripRemovesBulletAndDivider() {
        assertEquals("第一条\n第二条", stripJournalMarkup("• 第一条\n• 第二条"))
        assertEquals("上\n下", stripJournalMarkup("上\n———\n下"))
    }

    @Test
    fun stripKeepsUnclosedMarkersAsLiteralText() {
        assertEquals("正在输入 **粗体", stripJournalMarkup("正在输入 **粗体"))
    }

    @Test
    fun stripOfEmptyStringIsEmpty() {
        assertEquals("", stripJournalMarkup(""))
    }
}
