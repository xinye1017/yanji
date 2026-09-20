package com.example.yanji.ui.journal

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * 随笔正文的 Markdown 标记解析与「标记隐形」渲染。
 *
 * ## 核心模型
 *
 * 存储层始终是**带标记的纯文本**（`**粗体**`），渲染层把标记藏起来、只留下样式，
 * 用户看到的是真正的加粗文字，看不到星号。
 *
 * 因此渲染后的文本**比原文短**，`OffsetMapping.Identity` 不再成立，
 * 必须自己维护「源码偏移 ↔ 视觉偏移」的双向映射，见 [MarkupOffsetMapping]。
 * 这是整个模块最容易出错的地方，改动前务必读懂下面两条规则。
 *
 * ## 两条映射规则（单调性是硬要求）
 *
 * 设 [MarkupSpan] 覆盖的源码区间里，前导标记落在 `contentStart` 之前、
 * 尾随标记落在 `contentEnd` 之后（隐藏）。两个方向都必须**单调不减**，
 * 否则光标会随方向键乱跳、候选框会飘：
 *
 *  - `originalToTransformed`：数「该位置之前的可见字符数」。
 *    光标紧跟在 `**` 之后（源码 `contentStart`）会映射到视觉上的正文开头 ——
 *    这正是「按下 B 之后直接打字就是加粗」需要的行为。
 *
 *  - `transformedToOriginal`：从左往右走，遇标记跳过。
 *    视觉上的正文开头（0）映射回源码 `openStart`（`**` 之前），
 *    这样按左方向键能自然「逃出」加粗区间，而不是被卡在标记里。
 *
 * ## 为什么行级标记（列表 / 分隔线）也隐藏
 *
 * 需求是「全部隐形」。代价是用户看不到 `• ` 与 `———` 这些字符，
 * 因此**只能靠工具栏按钮切换**行级样式 —— 手打标记不会得到特殊待遇，
 * 会被当成普通文本原样显示。这是刻意取舍，不是缺陷。
 */

/** 一处行内标记覆盖的源码区间（含标记本身）。 */
internal data class MarkupSpan(
    val openStart: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val closeEnd: Int,
    val kind: MarkupKind
)

internal enum class MarkupKind { Bold, Italic, Underline }

/**
 * 行内标记的字面定义。
 *
 * 工具栏按钮通过这里拿 token，保证「插入的标记」与「解析的标记」永远是同一份事实。
 */
object JournalMarkup {
    const val Bold = "**"
    const val Underline = "__"
    const val Italic = "_"

    /** 列表项行首前缀。 */
    const val Bullet = "• "

    /** 分隔线整行内容。 */
    const val Divider = "———"

    /** 分隔线最短识别长度：至少 3 个连续破折号才算。 */
    const val DividerMinRun = 3
}

// ---------------------------------------------------------------------------
// 解析
// ---------------------------------------------------------------------------

/**
 * 扫描出所有成对的行内标记。
 *
 * 返回区间按 [MarkupSpan.openStart] 升序，且互不重叠（先匹配到的优先）。
 * `__` 先于 `_` 扫描，避免下划线内部被误判成斜体。
 */
internal fun findMarkupSpans(content: String): List<MarkupSpan> {
    val spans = mutableListOf<MarkupSpan>()
    val claimed = BooleanArray(content.length)

    fun scan(token: String, kind: MarkupKind) {
        var cursor = 0
        while (cursor <= content.length - token.length) {
            val open = content.indexOf(token, cursor)
            if (open < 0) return
            if (claimed[open]) {
                cursor = open + 1
                continue
            }
            val contentStart = open + token.length
            val close = content.indexOf(token, contentStart)
            // 未闭合：整个 token 当字面文本，不做任何修饰。
            if (close < 0) return
            if (close == contentStart) {
                // 空内容（如 `****`）：跳过这一对。
                cursor = close + token.length
                continue
            }
            // 区间与已claim的区间重叠时放弃这一对，避免嵌套导致的映射歧义。
            var overlaps = false
            for (i in open until close + token.length) if (claimed[i]) { overlaps = true; break }
            if (overlaps) {
                cursor = open + 1
                continue
            }
            for (i in open until close + token.length) claimed[i] = true
            spans += MarkupSpan(open, contentStart, close, close + token.length, kind)
            cursor = close + token.length
        }
    }

    scan(JournalMarkup.Underline, MarkupKind.Underline)
    scan(JournalMarkup.Bold, MarkupKind.Bold)
    scan(JournalMarkup.Italic, MarkupKind.Italic)

    return spans.sortedBy { it.openStart }
}

/** 该行是否为分隔线（至少 [JournalMarkup.DividerMinRun] 个破折号，允许前后空白）。 */
internal fun isDividerLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    return trimmed.all { it == '—' || it == '-' } && trimmed.length >= JournalMarkup.DividerMinRun
}

// ---------------------------------------------------------------------------
// 渲染
// ---------------------------------------------------------------------------

/** 把 [findMarkupSpans] 的结果施加到 [content] 上，得到纯样式（不含标记字符）。 */
private fun renderInline(content: String, spans: List<MarkupSpan>): AnnotatedString {
    // 先按「删除所有标记字符」构造可见文本，同时记录每个可见字符对应的源码位置，
    // 以便把样式区间换算到可见坐标上。
    val hidden = BooleanArray(content.length)
    for (span in spans) {
        for (i in span.openStart until span.contentStart) hidden[i] = true
        for (i in span.contentEnd until span.closeEnd) hidden[i] = true
    }

    val visible = StringBuilder(content.length)
    val sourceOfVisible = ArrayList<Int>(content.length)
    for (i in content.indices) {
        if (!hidden[i]) {
            visible.append(content[i])
            sourceOfVisible += i
        }
    }

    // 源码位置 -> 可见位置（单调不减），用于把样式区间换算过去。
    val visibleIndexOf = IntArray(content.length + 1)
    run {
        var v = 0
        for (i in content.indices) {
            visibleIndexOf[i] = v
            if (!hidden[i]) v++
        }
        visibleIndexOf[content.length] = v
    }

    return buildAnnotatedString {
        append(visible.toString())
        for (span in spans) {
            val start = visibleIndexOf[span.contentStart]
            val end = visibleIndexOf[span.contentEnd]
            if (end <= start) continue
            addStyle(
                when (span.kind) {
                    MarkupKind.Bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
                    MarkupKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                    MarkupKind.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
                },
                start,
                end
            )
        }
    }
}

/**
 * 编辑器用的 [VisualTransformation]：隐藏标记、施加样式。
 *
 * 行级标记（`• ` / `———`）也会被隐藏，因此视觉文本与源码文本长度不同，
 * 必须配套 [MarkupOffsetMapping]。
 */
fun journalMarkdownTransformation(): VisualTransformation = VisualTransformation { annotated ->
    val source = annotated.text
    val spans = findMarkupSpans(source)
    val rendered = renderInline(source, spans)
    TransformedText(rendered, MarkupOffsetMapping(source, spans))
}

/**
 * 源码偏移 ↔ 视觉偏移的双向映射。
 *
 * 不变量：两个方向都**单调不减**。这是光标稳定、IME 候选框不飘的前提。
 * 构造时预计算两张表，避免每次按键都重新扫描。
 */
internal class MarkupOffsetMapping(source: String, spans: List<MarkupSpan>) : OffsetMapping {

    /** 源码位置 -> 可见位置。 */
    private val toVisible = IntArray(source.length + 1)

    /** 可见位置 -> 源码位置（取该可见位置之前最近的源码位置）。 */
    private val toSource: IntArray

    init {
        val hidden = BooleanArray(source.length)
        for (span in spans) {
            for (i in span.openStart until span.contentStart) hidden[i] = true
            for (i in span.contentEnd until span.closeEnd) hidden[i] = true
        }

        var visibleCount = 0
        for (i in source.indices) {
            toVisible[i] = visibleCount
            if (!hidden[i]) visibleCount++
        }
        toVisible[source.length] = visibleCount

        toSource = IntArray(visibleCount + 1)
        var i = 0
        var v = 0
        while (i < source.length) {
            if (!hidden[i]) {
                toSource[v] = i
                v++
            }
            i++
        }
        // 末尾可见位置映射到源码末尾。
        toSource[visibleCount] = source.length
    }

    /**
     * 源码 -> 视觉。
     *
     * 光标落在尾随标记之后时，`toVisible` 已经把它算成「正文结尾」，
     * 因为隐藏字符不贡献可见计数 —— 这正是我们要的效果。
     */
    override fun originalToTransformed(offset: Int): Int =
        toVisible[offset.coerceIn(0, toVisible.size - 1)]

    /**
     * 视觉 -> 源码。
     *
     * 视觉 0 映射到正文首字符的源码位置（如 `**粗**` 的视觉 0 -> 源码 2），
     * 而不是标记之前的 openStart。理由：光标停在视觉开头时，用户眼前就是那个字，
     * 光标应贴在该字符上；映射到 openStart 会让光标「落在标记里」，
     * 表现为按一次左键不吃字、方向键来回抖。
     * 从左继续按仍可走到 openStart，逃出路径不受影响。
     */
    override fun transformedToOriginal(offset: Int): Int =
        toSource[offset.coerceIn(0, toSource.size - 1)]
}

// ---------------------------------------------------------------------------
// 纯文本化（列表摘要等场景）
// ---------------------------------------------------------------------------

/**
 * 去掉所有标记，返回可直接展示的纯文本。
 *
 * 与渲染共用 [findMarkupSpans]，保证「编辑器里隐藏的标记」
 * 与「摘要里去掉的标记」不会出现两套不一致的解析。
 */
fun stripJournalMarkup(content: String): String {
    if (content.isEmpty()) return content

    val spans = findMarkupSpans(content)
    val hidden = BooleanArray(content.length)
    for (span in spans) {
        for (i in span.openStart until span.contentStart) hidden[i] = true
        for (i in span.contentEnd until span.closeEnd) hidden[i] = true
    }

    val kept = buildString(content.length) {
        for (i in content.indices) if (!hidden[i]) append(content[i])
    }

    // 逐行清理行级标记：整行分隔线丢弃，列表行去掉行首 `• `。
    return kept.lineSequence()
        .mapNotNull { raw ->
            when {
                isDividerLine(raw) -> null
                else -> raw.trimEnd().removePrefix(JournalMarkup.Bullet)
            }
        }
        .joinToString("\n")
}

// ---------------------------------------------------------------------------
// 工具栏：切换当前选区的样式
// ---------------------------------------------------------------------------

/**
 * 切换行内样式，返回新的文本与新的**选区**。
 *
 * 返回的 [MarkupSelection] 用的是源码偏移（与 `TextFieldValue.selection` 一致），
 * 即半开区间 `[start, end)`：`start == end` 表示光标。
 * 刻意不用 `IntRange` —— 空选区在 `IntRange` 里会表现为 `3..2`（last < first），
 * 这个反直觉的表示非常容易在调用点写出错误逻辑。
 *
 *  - 选区已带该样式 → 去掉标记（样式取消）。
 *  - 选区未带该样式 → 包裹标记。
 *  - 无选区 → 插入一对空标记并把光标停在中间，之后输入即带样式。
 */
internal data class MarkupSelection(val start: Int, val end: Int)

internal fun toggleInlineMarkup(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    kind: MarkupKind
): Pair<String, MarkupSelection> {
    val token = when (kind) {
        MarkupKind.Bold -> JournalMarkup.Bold
        MarkupKind.Italic -> JournalMarkup.Italic
        MarkupKind.Underline -> JournalMarkup.Underline
    }
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(0, text.length)

    // 已有该样式 → 解包。要求选区与某个样式区间的正文严格对齐，
    // 这样「选中已加粗的文字再点 B」才会取消加粗，而不是套一层新的。
    val spans = findMarkupSpans(text)
    val enclosing = spans.firstOrNull {
        it.kind == kind && it.contentStart == start && it.contentEnd == end && end > start
    }
    if (enclosing != null) {
        val newText = text.removeRange(enclosing.contentEnd, enclosing.closeEnd)
            .removeRange(enclosing.openStart, enclosing.contentStart)
        // 解包后选区落在正文原位（前导标记被删除，因此整体左移）。
        val newStart = enclosing.openStart
        val newEnd = newStart + (enclosing.contentEnd - enclosing.contentStart)
        return newText to MarkupSelection(newStart, newEnd)
    }

    // 无选区：插入空标记对，光标停在两标记之间。
    if (start == end) {
        val newText = text.substring(0, start) + token + token + text.substring(end)
        val caret = start + token.length
        return newText to MarkupSelection(caret, caret)
    }

    // 有选区：包裹，选区保持在正文上（便于连续施加样式）。
    val newText = text.substring(0, start) + token + text.substring(start, end) + token + text.substring(end)
    return newText to MarkupSelection(start + token.length, end + token.length)
}

/**
 * 判断 [selection] 所在的选区是否已经带上了 [kind] 样式，供工具栏按钮显示选中态。
 *
 * 判定依据是源码里的标记，而不是渲染结果 —— 因为渲染结果里标记已经隐藏了。
 */
internal fun hasInlineMarkup(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    kind: MarkupKind
): Boolean {
    val spans = findMarkupSpans(text)
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(0, text.length)
    return spans.any { span ->
        span.kind == kind && when {
            // 有选区：选区落在该样式区间内。
            end > start -> span.contentStart <= start && end <= span.contentEnd
            // 无选区：光标在区间内（含两端），此时继续输入会继承该样式。
            else -> span.contentStart <= start && start <= span.contentEnd
        }
    }
}

/** 光标所在行是否已是列表项。 */
internal fun isBulletLine(text: String, offset: Int): Boolean {
    val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
    return text.substring(lineStart, lineEnd).startsWith(JournalMarkup.Bullet)
}

/** 光标所在行是否已是分隔线。 */
internal fun isDividerLineAt(text: String, offset: Int): Boolean {
    val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
    return isDividerLine(text.substring(lineStart, lineEnd))
}
