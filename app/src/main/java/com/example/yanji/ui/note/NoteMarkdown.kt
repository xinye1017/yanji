package com.example.yanji.ui.note

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * 研迹 Markdown 源码编辑工具。
 *
 * Markdown 的 CommonMark / GFM 解析与阅读预览交给成熟渲染库；本文件只保留输入层能力：
 *  1. 1:1 等长语法高亮（[MarkdownSyntaxTransformation]），使用 [OffsetMapping.Identity]，
 *     避免字符隐形导致的输入法丢字、光标乱跳和退格异常。
 *  2. 随笔列表所需的纯文本摘要提取。
 *  3. 编辑器快捷 Markdown 格式操作。
 */

/** 行内相对坐标的一条样式，供 [MarkdownSyntaxTransformation] 按行缓存复用。 */
internal class NoteInlineSpan(val style: SpanStyle, val start: Int, val end: Int)

/**
 * 随笔 Markdown 语法模式常量。
 *
 * 高亮器由 `VisualTransformation.filter` 在**每次文本变更**时调用，原先每次调用、甚至每行都重新
 * 构造 `Regex`（一次按键要编译上百个模式）。`Regex` 不可变，提升为文件级常量即可安全共享复用。
 */
internal object NoteMarkdownPattern {
    // 行级（块）语法
    val TaskLine = Regex("""^[-*+•]\s+\[([ xX])\].*""")
    val BulletLine = Regex("""^[•\-*+]\s+.*""")
    val NumberedLine = Regex("""^\d+\.\s+.*""")
    val ListPrefix = Regex("""^([•\-*+]|\d+\.)\s+.*""")
    val DividerLine = Regex("""^(—{3,}|-{3,}|\*{3,}|_{3,})$""")

    // 行内语法：对整篇文档扫描，字符类排除换行以免跨行匹配
    val InlineCode = Regex("""`([^`\n]+)`""")
    val BoldItalic = Regex("""(?<!\*)\*\*\*([^*\n]+)\*\*\*(?!\*)""")
    val Bold = Regex("""\*\*([^*\n]+)\*\*""")
    val Underline = Regex("""__([^_\n]+)__""")
    val Strike = Regex("""~~([^~\n]+)~~""")
    val ItalicStar = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")
    val ItalicUnderscore = Regex("""(?<!_)_([^_\n]+)_(?!_)""")
    val Link = Regex("""\[([^\]\n]+)\]\(([^)\n]+)\)""")

    // 纯文本摘要剥离用：逐行处理，故无需排除换行
    val StripHeading = Regex("""^#{1,6}\s+""")
    val StripQuote = Regex("""^>\s?""")
    val StripTask = Regex("""^[-*+•]\s+\[([ xX])\]\s+""")
    val StripListPrefix = Regex("""^([•\-*+]|\d+\.)\s+""")
    val StripBoldItalic = Regex("""\*\*\*([^*]+)\*\*\*""")
    val StripBold = Regex("""\*\*([^*]+)\*\*""")
    val StripUnderline = Regex("""__([^_]+)__""")
    val StripStrike = Regex("""~~([^~]+)~~""")
    val StripItalicStar = Regex("""(?<!\*)\*([^*]+)\*(?!\*)""")
    val StripItalicUnderscore = Regex("""(?<!_)_([^_]+)_(?!_)""")
    val StripInlineCode = Regex("""`([^`]+)`""")
    val StripLink = Regex("""\[([^\]]+)\]\([^)]+\)""")
}


// ============================================================================
// 一、实时语法高亮（VisualTransformation）
// ============================================================================

/**
 * 真正的等长 Markdown 语法高亮转换器。
 *
 * 核心保证：transformed.length == original.length，使用 [OffsetMapping.Identity]，
 * 输入法拼音组合、光标移动、撤销重做 100% 稳定丝滑。
 */
class MarkdownSyntaxTransformation(
    private val colorScheme: ColorScheme
) : VisualTransformation {

    // 同一段文本在一次输入里会被多次取用（文本布局 + 屏幕体二次重组），
    // 单条目记忆让重复调用直接复用已构建好的 AnnotatedString；它不可变，可安全共享。
    // 实例由 `remember(colorScheme)` 持有，换配色即新建实例，故无需把配色纳入键。
    private var cachedSource: String? = null
    private var cachedHighlighted: AnnotatedString? = null

    // 行级样式缓存：一次按键通常只改动一两行，其余行的行内正则匹配结果可原样复用，
    // 于是每键成本从「全文档 × 8 次扫描」降到「被改动的行 × 8 次扫描 + 其余行的哈希查表」。
    // 实例由 `remember(colorScheme)` 持有，换配色即新建实例，故键无需带配色。
    private val lineCache = mutableMapOf<String, List<NoteInlineSpan>>()

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val cached = cachedHighlighted
        val highlighted = if (cached != null && cachedSource == source) {
            cached
        } else {
            highlightMarkdown(source, colorScheme, lineCache).also {
                cachedSource = source
                cachedHighlighted = it
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    companion object {
        /** 行缓存条数上限：超限直接清空重建，避免长文档长会话下无界增长。 */
        private const val MaxCachedLines = 2048

        fun highlightMarkdown(source: String, colorScheme: ColorScheme): AnnotatedString =
            highlightMarkdown(source, colorScheme, null)

        internal fun highlightMarkdown(
            source: String,
            colorScheme: ColorScheme,
            lineCache: MutableMap<String, List<NoteInlineSpan>>?
        ): AnnotatedString {
            if (source.isEmpty()) return AnnotatedString("")

            val lines = source.split('\n')
            return buildAnnotatedString {
                append(source)

                var lineStartOffset = 0
                for (line in lines) {
                    val lineEndOffset = lineStartOffset + line.length

                    // 1. 标题高亮 (# ~ ######)
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("#")) {
                        val spaceIndex = line.indexOf(' ')
                        if (spaceIndex in 1..6 && line.substring(0, spaceIndex).all { it == '#' }) {
                            val level = spaceIndex
                            val headerTokenEnd = lineStartOffset + spaceIndex + 1
                            // Token 高亮
                            addStyle(
                                SpanStyle(
                                    color = colorScheme.primary.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                ),
                                lineStartOffset,
                                headerTokenEnd.coerceAtMost(lineEndOffset)
                            )
                            // 标题文字放大与加粗
                            val style = when (level) {
                                1 -> SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                                2 -> SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                                3 -> SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                                else -> SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                            }
                            addStyle(style, headerTokenEnd.coerceAtMost(lineEndOffset), lineEndOffset)
                        }
                    } else if (line.startsWith("> ") || line == ">") {
                        // 2. 引用块
                        val markerEnd = (lineStartOffset + 2).coerceAtMost(lineEndOffset)
                        addStyle(
                            SpanStyle(color = colorScheme.primary.copy(alpha = 0.8f), fontWeight = FontWeight.Bold),
                            lineStartOffset,
                            markerEnd
                        )
                        addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic, color = colorScheme.onSurfaceVariant),
                            markerEnd,
                            lineEndOffset
                        )
                    } else if (line.matches(NoteMarkdownPattern.TaskLine)) {
                        // 3. 待办清单 (- [ ] / - [x])
                        val isDone = line.contains("[x]") || line.contains("[X]")
                        val prefixEnd = (lineStartOffset + line.indexOf(']') + 2).coerceAtMost(lineEndOffset)
                        addStyle(
                            SpanStyle(color = if (isDone) colorScheme.secondary else colorScheme.primary, fontWeight = FontWeight.Bold),
                            lineStartOffset,
                            prefixEnd
                        )
                        if (isDone) {
                            addStyle(
                                SpanStyle(
                                    textDecoration = TextDecoration.LineThrough,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                ),
                                prefixEnd,
                                lineEndOffset
                            )
                        }
                    } else if (line.matches(NoteMarkdownPattern.ListPrefix)) {
                        // 4. 列表前缀
                        val spaceIdx = line.indexOf(' ')
                        if (spaceIdx > 0) {
                            addStyle(
                                SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.SemiBold),
                                lineStartOffset,
                                lineStartOffset + spaceIdx + 1
                            )
                        }
                    } else if (line.trim().matches(NoteMarkdownPattern.DividerLine)) {
                        // 5. 分割线
                        addStyle(
                            SpanStyle(color = colorScheme.outline.copy(alpha = 0.6f), fontWeight = FontWeight.Bold),
                            lineStartOffset,
                            lineEndOffset
                        )
                    }

                    // 6. 行内样式高亮（粗体、斜体、下划线、删除线、代码、链接）
                    //    按行计算并可缓存：8 个行内模式的字符类都排除换行，行首 `(?<!\*)` 在整篇
                    //    扫描里看到的前一字符恒为 '\n'，故逐行结果与整篇扫描逐字符等价。
                    var inlineSpans = lineCache?.get(line)
                    if (inlineSpans == null) {
                        if (lineCache != null && lineCache.size >= MaxCachedLines) lineCache.clear()
                        inlineSpans = inlineSpansFor(line, colorScheme)
                        lineCache?.put(line, inlineSpans)
                    }
                    inlineSpans.forEach { addStyle(it.style, lineStartOffset + it.start, lineStartOffset + it.end) }

                    lineStartOffset = lineEndOffset + 1
                }
            }
        }

        private fun inlineSpansFor(line: String, colorScheme: ColorScheme): List<NoteInlineSpan> {
            val out = mutableListOf<NoteInlineSpan>()
            // 局部同名 helper：让下面 8 段匹配逻辑与整篇扫描版逐字保持一致，只改数据去向。
            fun addStyle(style: SpanStyle, start: Int, end: Int) { out.add(NoteInlineSpan(style, start, end)) }

            val subtleToken = SpanStyle(color = colorScheme.onSurfaceVariant.copy(alpha = 0.42f))

            // 行内代码 `...`
            val codeRegex = NoteMarkdownPattern.InlineCode
            codeRegex.findAll(line).forEach { match ->
                val range = match.range
                addStyle(SpanStyle(color = colorScheme.primary.copy(alpha = 0.5f)), range.first, range.first + 1)
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.primary,
                        background = colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    range.first + 1,
                    range.last
                )
                addStyle(SpanStyle(color = colorScheme.primary.copy(alpha = 0.5f)), range.last, range.last + 1)
            }

            // 粗斜体 ***...***
            val boldItalicRegex = NoteMarkdownPattern.BoldItalic
            boldItalicRegex.findAll(line).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 3)
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = colorScheme.onSurface
                    ),
                    range.first + 3,
                    range.last - 2
                )
                addStyle(subtleToken, range.last - 2, range.last + 1)
            }

            // 粗体 **...**
            val boldRegex = NoteMarkdownPattern.Bold
            boldRegex.findAll(line).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 2)
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, color = colorScheme.onSurface),
                    range.first + 2,
                    range.last - 1
                )
                addStyle(subtleToken, range.last - 1, range.last + 1)
            }

            // 下划线 __...__
            val underlineRegex = NoteMarkdownPattern.Underline
            underlineRegex.findAll(line).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 2)
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline, color = colorScheme.onSurface),
                    range.first + 2,
                    range.last - 1
                )
                addStyle(subtleToken, range.last - 1, range.last + 1)
            }

            // 删除线 ~~...~~
            val strikeRegex = NoteMarkdownPattern.Strike
            strikeRegex.findAll(line).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 2)
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough, color = colorScheme.onSurfaceVariant),
                    range.first + 2,
                    range.last - 1
                )
                addStyle(subtleToken, range.last - 1, range.last + 1)
            }

            // 单星斜体 *...*（排除双星）
            val italicStarRegex = NoteMarkdownPattern.ItalicStar
            italicStarRegex.findAll(line).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 1)
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                addStyle(subtleToken, range.last, range.last + 1)
            }

            // 单下划线斜体 _..._（排除双下划线）
            val italicUnderscoreRegex = NoteMarkdownPattern.ItalicUnderscore
            italicUnderscoreRegex.findAll(line).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 1)
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                addStyle(subtleToken, range.last, range.last + 1)
            }

            // 链接 [title](url)
            val linkRegex = NoteMarkdownPattern.Link
            linkRegex.findAll(line).forEach { match ->
                val titleGroup = match.groups[1] ?: return@forEach
                val urlGroup = match.groups[2] ?: return@forEach
                val left = match.range.first
                val right = match.range.last + 1
                val titleStart = titleGroup.range.first
                val titleEnd = titleGroup.range.last + 1
                val urlStart = urlGroup.range.first - 1
                addStyle(SpanStyle(color = colorScheme.primary.copy(alpha = 0.6f)), left, titleStart)
                addStyle(
                    SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline),
                    titleStart,
                    titleEnd
                )
                addStyle(SpanStyle(color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)), urlStart, right)
            }
            return out
        }
    }
}

// ============================================================================
// 二、随笔列表纯文本摘要提取（stripNoteMarkdown）
// ============================================================================

/**
 * 剔除所有 Markdown 标记，返回纯净文本供随笔列表/卡片摘要展示。
 */
fun stripNoteMarkdown(content: String): String {
    if (content.isBlank()) return ""

    return content.lineSequence()
        .map { line ->
            var l = line.trim()
            // 忽略纯分割线
            if (l.matches(NoteMarkdownPattern.DividerLine)) return@map ""
            // 去除标题前缀
            l = l.replace(NoteMarkdownPattern.StripHeading, "")
            // 去除引用前缀
            l = l.replace(NoteMarkdownPattern.StripQuote, "")
            // 去除待办前缀
            l = l.replace(NoteMarkdownPattern.StripTask, "")
            // 去除列表前缀
            l = l.replace(NoteMarkdownPattern.StripListPrefix, "")
            // 去除行内标记
            l = l.replace(NoteMarkdownPattern.StripBoldItalic, "$1")
            l = l.replace(NoteMarkdownPattern.StripBold, "$1")
            l = l.replace(NoteMarkdownPattern.StripUnderline, "$1")
            l = l.replace(NoteMarkdownPattern.StripStrike, "$1")
            l = l.replace(NoteMarkdownPattern.StripItalicStar, "$1")
            l = l.replace(NoteMarkdownPattern.StripItalicUnderscore, "$1")
            l = l.replace(NoteMarkdownPattern.StripInlineCode, "$1")
            l = l.replace(NoteMarkdownPattern.StripLink, "$1")
            l
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

/**
 * 返回 [offset] 所在行的起止区间（`end` 指向该行末尾换行符，即不含换行符）。
 * 编辑器快捷操作与预览共用这一份行边界口径。
 */
internal fun lineBoundsOf(text: String, offset: Int): Pair<Int, Int> {
    val safe = offset.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', (safe - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', safe).let { if (it < 0) text.length else it }
    return start to end
}

/**
 * 随笔列表摘要：剥掉 markdown 样式标记后展示。整篇只剩标记（如只有一条分割线）时剥完为空，
 * 此时退回原文，避免列表里出现一行看起来完全空白的条目。
 */
fun noteListSnippet(content: String): String = stripNoteMarkdown(content).ifBlank { content }

// ============================================================================
// 三、Markdown 编辑器快捷输入与格式操作助手
// ============================================================================

object MarkdownEditorOps {

    /**
     * 包裹或解包行内标记（如粗体、斜体、删除线、行内代码）。
     *
     *  - 有选区且已包裹该标记：解包还原。
     *  - 有选区且未包裹：两侧包裹标记，保持正文被选中。
     *  - 无选区：插入空标记对并将光标停留在标记中间。
     */
    /**
     * 返回选区左右紧邻处成对出现的连续分隔符数量。
     * 例如 `*text*` -> 1，`**text**` -> 2，`***text***` -> 3。
     */
    fun surroundingDelimiterRun(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        delimiter: Char
    ): Int {
        val selMin = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val selMax = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)

        var leftCount = 0
        var left = selMin - 1
        while (left >= 0 && text[left] == delimiter) {
            leftCount++
            left--
        }

        var rightCount = 0
        var right = selMax
        while (right < text.length && text[right] == delimiter) {
            rightCount++
            right++
        }

        return minOf(leftCount, rightCount)
    }

    fun toggleInlineToken(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        openToken: String,
        closeToken: String = openToken
    ): Pair<String, TextRange> {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        val selMin = minOf(start, end)
        val selMax = maxOf(start, end)

        // 1. 无选区：插入空标记对，光标落在正中
        if (selMin == selMax) {
            // 如果光标恰好在成对标记内部，可视为向外跳出或解包
            val newText = text.substring(0, selMin) + openToken + closeToken + text.substring(selMax)
            val caret = selMin + openToken.length
            return newText to TextRange(caret, caret)
        }

        // 2. 有选区：检查是否已被包裹。
        // 对 * / _ 这种会与双分隔符共享字符的标记，需要按连续数量的奇偶判断，
        // 避免 **粗体** 被误认为同时处于 *斜体*。
        val selectedText = text.substring(selMin, selMax)
        val isAmbiguousSingleDelimiter = openToken == closeToken &&
            openToken.length == 1 && openToken[0] in charArrayOf('*', '_')

        if (isAmbiguousSingleDelimiter) {
            val delimiter = openToken[0]
            var selectedLeftRun = 0
            while (selectedLeftRun < selectedText.length && selectedText[selectedLeftRun] == delimiter) {
                selectedLeftRun++
            }
            var selectedRightRun = 0
            while (
                selectedRightRun < selectedText.length &&
                selectedText[selectedText.length - 1 - selectedRightRun] == delimiter
            ) {
                selectedRightRun++
            }
            val selectedRun = minOf(selectedLeftRun, selectedRightRun)
            if (selectedRun > 0 && selectedRun % 2 == 1) {
                val unwrapped = selectedText.substring(1, selectedText.length - 1)
                val newText = text.replaceRange(selMin, selMax, unwrapped)
                return newText to TextRange(selMin, selMin + unwrapped.length)
            }
        } else if (
            selectedText.startsWith(openToken) && selectedText.endsWith(closeToken) &&
            selectedText.length >= openToken.length + closeToken.length
        ) {
            val unwrapped = selectedText.substring(openToken.length, selectedText.length - closeToken.length)
            val newText = text.replaceRange(selMin, selMax, unwrapped)
            return newText to TextRange(selMin, selMin + unwrapped.length)
        }

        // 检查选区外部紧邻处是否正是标记。
        if (isAmbiguousSingleDelimiter) {
            val delimiterRun = surroundingDelimiterRun(text, selMin, selMax, openToken[0])
            if (delimiterRun % 2 == 1) {
                // 单星或三星时，移除一层斜体；三星会自然退回双星粗体。
                val newText = text.substring(0, selMin - 1) + selectedText + text.substring(selMax + 1)
                return newText to TextRange(selMin - 1, selMax - 1)
            }
        } else {
            val outerLeft = (selMin - openToken.length).coerceAtLeast(0)
            val outerRight = (selMax + closeToken.length).coerceAtMost(text.length)
            if (text.substring(outerLeft, selMin) == openToken && text.substring(selMax, outerRight) == closeToken) {
                val newText = text.substring(0, outerLeft) + selectedText + text.substring(outerRight)
                return newText to TextRange(outerLeft, outerLeft + selectedText.length)
            }
        }

        // 未包裹：进行包裹
        val wrapped = openToken + selectedText + closeToken
        val newText = text.replaceRange(selMin, selMax, wrapped)
        return newText to TextRange(selMin + openToken.length, selMax + openToken.length)
    }

    /**
     * 循环切换标题等级：无 -> # -> ## -> ### -> 无
     */
    fun cycleHeading(text: String, selection: TextRange): Pair<String, TextRange> {
        val min = selection.min.coerceIn(0, text.length)
        val (lineStart, lineEnd) = lineBoundsOf(text, min)
        val currentLine = text.substring(lineStart, lineEnd)

        val nextLine = when {
            currentLine.startsWith("### ") -> currentLine.removePrefix("### ")
            currentLine.startsWith("## ") -> "### " + currentLine.removePrefix("## ")
            currentLine.startsWith("# ") -> "## " + currentLine.removePrefix("# ")
            else -> "# $currentLine"
        }

        val newText = text.replaceRange(lineStart, lineEnd, nextLine)
        val diff = nextLine.length - currentLine.length
        val newPos = (selection.min + diff).coerceIn(lineStart, lineStart + nextLine.length)
        return newText to TextRange(newPos, newPos)
    }

    /**
     * 切换行首前缀（引用、列表、待办等）。
     */
    fun toggleLinePrefix(
        text: String,
        selection: TextRange,
        prefix: String,
        altPrefixes: List<String> = emptyList()
    ): Pair<String, TextRange> {
        val min = selection.min.coerceIn(0, text.length)
        val (lineStart, lineEnd) = lineBoundsOf(text, min)
        val currentLine = text.substring(lineStart, lineEnd)

        val allPrefixes = listOf(prefix) + altPrefixes
        val matchedPrefix = allPrefixes.firstOrNull { currentLine.startsWith(it) }

        val nextLine = if (matchedPrefix != null) {
            currentLine.removePrefix(matchedPrefix)
        } else {
            prefix + currentLine
        }

        val newText = text.replaceRange(lineStart, lineEnd, nextLine)
        val diff = nextLine.length - currentLine.length
        val newPos = (selection.min + diff).coerceIn(lineStart, lineStart + nextLine.length)
        return newText to TextRange(newPos, newPos)
    }

    /**
     * 插入分割线。若当前行为空行则直接替换，否则在光标前后换行插入。
     */
    fun insertDivider(text: String, selection: TextRange): Pair<String, TextRange> {
        val min = selection.min.coerceIn(0, text.length)
        val (lineStart, lineEnd) = lineBoundsOf(text, min)
        val currentLine = text.substring(lineStart, lineEnd)

        return if (currentLine.isBlank()) {
            val replacement = "---"
            val newText = text.replaceRange(lineStart, lineEnd, replacement)
            val newPos = lineStart + replacement.length
            newText to TextRange(newPos, newPos)
        } else {
            val insertStr = "\n---\n"
            val newText = text.substring(0, min) + insertStr + text.substring(min)
            val newPos = min + insertStr.length
            newText to TextRange(newPos, newPos)
        }
    }

    /**
     * 切换代码（多行选区包裹为代码块，单行/无选区包裹为行内代码）。
     */
    fun toggleCode(text: String, selection: TextRange): Pair<String, TextRange> {
        val min = selection.min.coerceIn(0, text.length)
        val max = selection.max.coerceIn(0, text.length)

        if (min != max && text.substring(min, max).contains('\n')) {
            val selected = text.substring(min, max)
            val block = "```\n$selected\n```"
            val newText = text.replaceRange(min, max, block)
            return newText to TextRange(min + 4, min + 4 + selected.length)
        }
        return toggleInlineToken(text, min, max, "`")
    }

    /**
     * 切换链接语法 [text](url)。
     */
    fun toggleLink(text: String, selection: TextRange): Pair<String, TextRange> {
        val min = selection.min.coerceIn(0, text.length)
        val max = selection.max.coerceIn(0, text.length)

        if (min == max) {
            val template = "[](https://)"
            val newText = text.substring(0, min) + template + text.substring(min)
            return newText to TextRange(min + 1, min + 1)
        } else {
            val selected = text.substring(min, max)
            val wrapped = "[$selected](https://)"
            val newText = text.replaceRange(min, max, wrapped)
            val urlStart = min + selected.length + 3
            return newText to TextRange(urlStart, urlStart + 8)
        }
    }

    /**
     * 在预览模式下点击切换特定行的待办勾选状态 (- [ ] <-> - [x])。
     *
     * [TaskBullets] × [TaskMarks] 的遍历顺序即旧实现 12 组 `contains`/`replaceFirst` 分支的
     * 书写顺序：同一行含多个标记时，仍按符号优先级 `- * + •` 与勾选态优先级 `空格 x X`
     * 命中第一处，因此行为与逐条硬编码时完全一致。
     */
    fun toggleTaskItemAtLine(source: String, targetLineIndex: Int): String {
        val lines = source.lines().toMutableList()
        if (targetLineIndex !in lines.indices) return source

        val target = lines[targetLineIndex]
        var replaced = target
        outer@ for (bullet in TaskBullets) {
            for (mark in TaskMarks) {
                val from = "$bullet [$mark] "
                if (replaced.contains(from)) {
                    val to = if (mark == ' ') "$bullet [x] " else "$bullet [ ] "
                    replaced = replaced.replaceFirst(from, to)
                    break@outer
                }
            }
        }
        lines[targetLineIndex] = replaced
        return lines.joinToString("\n")
    }

    private val TaskBullets = charArrayOf('-', '*', '+', '•')
    private val TaskMarks = charArrayOf(' ', 'x', 'X')
}
