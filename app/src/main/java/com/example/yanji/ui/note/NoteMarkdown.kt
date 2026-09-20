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

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlightMarkdown(text.text, colorScheme)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    companion object {
        fun highlightMarkdown(source: String, colorScheme: ColorScheme): AnnotatedString {
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
                    } else if (line.matches(Regex("""^[-*+•]\s+\[([ xX])\].*"""))) {
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
                    } else if (line.matches(Regex("""^([•\-*+]|\d+\.)\s+.*"""))) {
                        // 4. 列表前缀
                        val spaceIdx = line.indexOf(' ')
                        if (spaceIdx > 0) {
                            addStyle(
                                SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.SemiBold),
                                lineStartOffset,
                                lineStartOffset + spaceIdx + 1
                            )
                        }
                    } else if (line.trim().matches(Regex("""^(—{3,}|-{3,}|\*{3,}|_{3,})$"""))) {
                        // 5. 分割线
                        addStyle(
                            SpanStyle(color = colorScheme.outline.copy(alpha = 0.6f), fontWeight = FontWeight.Bold),
                            lineStartOffset,
                            lineEndOffset
                        )
                    }

                    lineStartOffset = lineEndOffset + 1
                }

                // 6. 行内样式高亮（粗体、斜体、下划线、删除线、代码、链接）
                highlightInlines(source, colorScheme)
            }
        }

        private fun AnnotatedString.Builder.highlightInlines(source: String, colorScheme: ColorScheme) {
            val subtleToken = SpanStyle(color = colorScheme.onSurfaceVariant.copy(alpha = 0.42f))

            // 行内代码 `...`
            val codeRegex = Regex("""`([^`\n]+)`""")
            codeRegex.findAll(source).forEach { match ->
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
            val boldItalicRegex = Regex("""(?<!\*)\*\*\*([^*\n]+)\*\*\*(?!\*)""")
            boldItalicRegex.findAll(source).forEach { match ->
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
            val boldRegex = Regex("""\*\*([^*\n]+)\*\*""")
            boldRegex.findAll(source).forEach { match ->
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
            val underlineRegex = Regex("""__([^_\n]+)__""")
            underlineRegex.findAll(source).forEach { match ->
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
            val strikeRegex = Regex("""~~([^~\n]+)~~""")
            strikeRegex.findAll(source).forEach { match ->
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
            val italicStarRegex = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")
            italicStarRegex.findAll(source).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 1)
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                addStyle(subtleToken, range.last, range.last + 1)
            }

            // 单下划线斜体 _..._（排除双下划线）
            val italicUnderscoreRegex = Regex("""(?<!_)_([^_\n]+)_(?!_)""")
            italicUnderscoreRegex.findAll(source).forEach { match ->
                val range = match.range
                addStyle(subtleToken, range.first, range.first + 1)
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), range.first + 1, range.last)
                addStyle(subtleToken, range.last, range.last + 1)
            }

            // 链接 [title](url)
            val linkRegex = Regex("""\[([^\]\n]+)\]\(([^)\n]+)\)""")
            linkRegex.findAll(source).forEach { match ->
                val titleGroup = match.groups[1] ?: return@forEach
                val urlGroup = match.groups[2] ?: return@forEach
                addStyle(SpanStyle(color = colorScheme.primary.copy(alpha = 0.6f)), match.range.first, titleGroup.range.first)
                addStyle(
                    SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline),
                    titleGroup.range.first,
                    titleGroup.range.last + 1
                )
                addStyle(SpanStyle(color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)), urlGroup.range.first - 1, match.range.last + 1)
            }
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
            if (l.matches(Regex("""^(—{3,}|-{3,}|\*{3,}|_{3,})$"""))) return@map ""
            // 去除标题前缀
            l = l.replace(Regex("""^#{1,6}\s+"""), "")
            // 去除引用前缀
            l = l.replace(Regex("""^>\s?"""), "")
            // 去除待办前缀
            l = l.replace(Regex("""^[-*+•]\s+\[([ xX])\]\s+"""), "")
            // 去除列表前缀
            l = l.replace(Regex("""^([•\-*+]|\d+\.)\s+"""), "")
            // 去除行内标记
            l = l.replace(Regex("""\*\*\*([^*]+)\*\*\*"""), "$1")
            l = l.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            l = l.replace(Regex("""__([^_]+)__"""), "$1")
            l = l.replace(Regex("""~~([^~]+)~~"""), "$1")
            l = l.replace(Regex("""(?<!\*)\*([^*]+)\*(?!\*)"""), "$1")
            l = l.replace(Regex("""(?<!_)_([^_]+)_(?!_)"""), "$1")
            l = l.replace(Regex("""`([^`]+)`"""), "$1")
            l = l.replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")
            l
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

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
        val lineStart = text.lastIndexOf('\n', (min - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', min).let { if (it < 0) text.length else it }
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
        val lineStart = text.lastIndexOf('\n', (min - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', min).let { if (it < 0) text.length else it }
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
        val lineStart = text.lastIndexOf('\n', (min - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', min).let { if (it < 0) text.length else it }
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
     */
    fun toggleTaskItemAtLine(source: String, targetLineIndex: Int): String {
        val lines = source.lines().toMutableList()
        if (targetLineIndex !in lines.indices) return source

        val target = lines[targetLineIndex]
        val replaced = when {
            target.contains("- [ ] ") -> target.replaceFirst("- [ ] ", "- [x] ")
            target.contains("- [x] ") -> target.replaceFirst("- [x] ", "- [ ] ")
            target.contains("- [X] ") -> target.replaceFirst("- [X] ", "- [ ] ")
            target.contains("* [ ] ") -> target.replaceFirst("* [ ] ", "* [x] ")
            target.contains("* [x] ") -> target.replaceFirst("* [x] ", "* [ ] ")
            target.contains("* [X] ") -> target.replaceFirst("* [X] ", "* [ ] ")
            target.contains("+ [ ] ") -> target.replaceFirst("+ [ ] ", "+ [x] ")
            target.contains("+ [x] ") -> target.replaceFirst("+ [x] ", "+ [ ] ")
            target.contains("+ [X] ") -> target.replaceFirst("+ [X] ", "+ [ ] ")
            target.contains("• [ ] ") -> target.replaceFirst("• [ ] ", "• [x] ")
            target.contains("• [x] ") -> target.replaceFirst("• [x] ", "• [ ] ")
            target.contains("• [X] ") -> target.replaceFirst("• [X] ", "• [ ] ")
            else -> target
        }
        lines[targetLineIndex] = replaced
        return lines.joinToString("\n")
    }
}
