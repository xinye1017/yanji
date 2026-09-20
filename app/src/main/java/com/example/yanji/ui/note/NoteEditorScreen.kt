package com.example.yanji.ui.note

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.*
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.CaretLeft
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.CheckSquare
import com.adamglin.phosphoricons.regular.Code
import com.adamglin.phosphoricons.regular.Eye
import com.adamglin.phosphoricons.regular.Link
import com.adamglin.phosphoricons.regular.ListBullets
import com.adamglin.phosphoricons.regular.ListNumbers
import com.adamglin.phosphoricons.regular.Minus
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Quotes
import com.adamglin.phosphoricons.regular.TextAUnderline
import com.adamglin.phosphoricons.regular.TextB
import com.adamglin.phosphoricons.regular.TextH
import com.adamglin.phosphoricons.regular.TextItalic
import com.adamglin.phosphoricons.regular.TextStrikethrough
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.NoteEntry
import com.example.yanji.theme.*
import com.example.yanji.ui.components.WarmTooltip
import com.example.yanji.ui.components.WarmTooltipGroup
import kotlin.math.roundToInt
import java.util.UUID

/** UI 测试定位锚点：与 NoteEditorScreenInstrumentedTest 共享，避免断言依赖中文文案。 */
object NoteEditorTags {
    const val ContentInput = "note_content_input"

    /** 完成按钮。tag 名沿用历史上的 save_button，插桩测试契约不可改名。 */
    const val SaveButton = "note_save_button"

    /** 顶栏编辑 / 预览圆形模式按钮。 */
    const val ModeToggleButton = "note_mode_toggle_button"
    const val BoldButton = "note_format_bold"
    const val ItalicButton = "note_format_italic"
    const val UnderlineButton = "note_format_underline"
    const val ListButton = "note_format_list"
    const val DividerButton = "note_format_divider"

    /** 新增的 Markdown 工具栏与模式切换 Tag */
    const val HeadingButton = "note_format_heading"
    const val StrikeButton = "note_format_strike"
    const val CodeButton = "note_format_code"
    const val QuoteButton = "note_format_quote"
    const val TaskButton = "note_format_task"
    const val NumberedListButton = "note_format_num_list"
    const val LinkButton = "note_format_link"
}

/** 底部格式栏高度 + 与正文区的留白，作为正文区底部内边距，避免最后几行被遮挡。 */
private val EditorToolbarHeight = 68.dp

enum class NoteEditorMode {
    Edit,
    Preview
}

/**
 * 编辑页「初值快照」：进入页面时记录的正文 / 打分 / 收藏。
 * 用来判断当前是否有未保存改动（决定完成键是否高亮、返回是否提示），
 * 用值对比而非「有没有动过」标志位，改回原样时按钮不会停在点亮态。
 */
private data class EditorSnapshot(
    val content: String,
    val moodScore: Int,
    val isFavorite: Boolean
)

/**
 * 研途随笔 Markdown 编辑器（支持实时高亮编辑与排版预览）。
 *
 * 核心架构：
 *  - 实时编辑：标准 CommonMark 源码编辑，带 1:1 等长语法高亮（[MarkdownSyntaxTransformation]），
 *    零输入法跳光标问题，支持快捷 Markdown 工具栏。
 *  - 实时预览：一键切换「阅读预览」模式（[NoteMarkdownPreview]），支持富文本排版与交互式待办勾选。
 *  - 顶部吸顶：PeekRating 5 星状态打分与记录时间固定展示（同行，时间靠右），滚动正文平滑穿透。
 */
@Composable
fun NoteEditorScreen(
    noteId: String?,
    date: String,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    draftKeySuffix: String = "",
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    viewModel: NoteViewModel = com.example.yanji.di.yanjiViewModel { container ->
        NoteViewModel(container.repository, container.statisticsRepository)
    }
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notes = state.notes

    // 仅当带着明确的 id（从历史页点进某篇）才复用旧内容。
    // 不带 id 代表「新建一篇」：即使今天已经有随笔，也必须进入空白稿，
    // 否则一天多篇随笔无法落地（会反复打开当天最早的那篇）。
    val existingEntry = remember(noteId, notes) {
        if (!noteId.isNullOrBlank()) notes.find { it.id == noteId } else null
    }

    // rememberSaveable 的 key 前缀：新建页用唯一 nonce 断开继承，
    // 否则同一天连续新建（noteId 同为 null、date 相同）会复用上一篇的草稿与打分。
    val editorStateKey = if (noteId.isNullOrBlank()) "new:$draftKeySuffix:$date" else "edit:$noteId"

    // 草稿状态管理
    var draftInitialized by rememberSaveable(editorStateKey) { mutableStateOf(false) }
    var content by rememberSaveable(editorStateKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    LaunchedEffect(existingEntry?.id) {
        val entry = existingEntry ?: return@LaunchedEffect
        if (!draftInitialized) {
            content = TextFieldValue(entry.content)
            draftInitialized = true
        }
    }

    // 当前视图模式：编辑 vs 预览
    var editorMode by rememberSaveable(editorStateKey) {
        mutableStateOf(NoteEditorMode.Edit)
    }

    // 收藏态
    var isFavorite by rememberSaveable(editorStateKey) { mutableStateOf(false) }
    var favoriteInitialized by rememberSaveable(editorStateKey) { mutableStateOf(false) }
    LaunchedEffect(existingEntry?.id, existingEntry?.isFavorite) {
        val entry = existingEntry
        if (!favoriteInitialized && entry != null) {
            isFavorite = entry.isFavorite
            favoriteInitialized = true
        }
    }

    var moodScore by rememberSaveable(editorStateKey) {
        mutableIntStateOf(existingEntry?.moodScore ?: 5)
    }
    val createdAt = remember(existingEntry?.id) {
        existingEntry?.createdAt ?: System.currentTimeMillis()
    }
    var metaInitialized by rememberSaveable(editorStateKey) { mutableStateOf(false) }
    LaunchedEffect(existingEntry?.id) {
        val entry = existingEntry ?: return@LaunchedEffect
        if (!metaInitialized) {
            moodScore = entry.moodScore
            metaInitialized = true
        }
    }

    fun saveNote() {
        if (content.text.isBlank()) {
            Toast.makeText(context, "请写点今日内容吧", Toast.LENGTH_SHORT).show()
            return
        }
        val finalEntry = NoteEntry(
            id = existingEntry?.id ?: UUID.randomUUID().toString(),
            date = date,
            title = existingEntry?.title ?: "",
            content = content.text.trim(),
            moodScore = moodScore,
            energyScore = existingEntry?.energyScore ?: 4,
            studySatisfaction = existingEntry?.studySatisfaction ?: 5,
            tomorrowPlan = existingEntry?.tomorrowPlan ?: "",
            blockers = existingEntry?.blockers ?: "",
            tags = existingEntry?.tags ?: emptyList(),
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            isFavorite = isFavorite
        )
        viewModel.saveNote(finalEntry)
        Toast.makeText(context, "随笔已保存", Toast.LENGTH_SHORT).show()
        onSaveSuccess()
    }

    var showBackConfirm by remember { mutableStateOf(false) }

    fun saveAsDraft() {
        val trimmed = content.text.trim()
        if (trimmed.isEmpty()) {
            onBack()
            return
        }
        val draft = NoteEntry(
            id = existingEntry?.id ?: UUID.randomUUID().toString(),
            date = date,
            title = existingEntry?.title ?: "",
            content = trimmed,
            moodScore = moodScore,
            energyScore = existingEntry?.energyScore ?: 4,
            studySatisfaction = existingEntry?.studySatisfaction ?: 5,
            tomorrowPlan = existingEntry?.tomorrowPlan ?: "",
            blockers = existingEntry?.blockers ?: "",
            tags = existingEntry?.tags ?: emptyList(),
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            isFavorite = isFavorite
        )
        viewModel.saveNote(draft)
        Toast.makeText(context, "已保存为草稿", Toast.LENGTH_SHORT).show()
        onBack()
    }

    // 「还没保存」提示与保存键高亮共用同一份脏值判断：
    // 以进入页面时的初值快照对比，改回原样不算有改动，返回也不再弹卡片。
    val initialSnapshot = remember(existingEntry?.id) {
        val entry = existingEntry
        EditorSnapshot(
            content = entry?.content ?: "",
            moodScore = entry?.moodScore ?: 5,
            isFavorite = entry?.isFavorite ?: false
        )
    }
    // 编辑既有随笔时，正文由 LaunchedEffect 异步回填；回填前 content 还是空串，
    // 直接比对会误判为「有改动」而让保存键闪一下。这里以 draftInitialized 收口。
    val contentForDiff = if (existingEntry != null && !draftInitialized) initialSnapshot.content else content.text
    val isDirty = contentForDiff.trim() != initialSnapshot.content.trim() ||
        moodScore != initialSnapshot.moodScore ||
        isFavorite != initialSnapshot.isFavorite

    fun requestBack() {
        // 只有真正发生编辑 / 修改时才提示保存；原样返回直接退出，不再每次点返回都弹卡片。
        if (isDirty) {
            showBackConfirm = true
        } else {
            onBack()
        }
    }

    // 系统返回（手势 / 返回键）与左上角返回按钮走同一套判定。
    BackHandler { requestBack() }

    // Markdown 语法操作助手
    fun applyInline(token: String) {
        val (newText, newSel) = MarkdownEditorOps.toggleInlineToken(
            content.text,
            content.selection.min,
            content.selection.max,
            token
        )
        content = TextFieldValue(newText, newSel)
    }

    fun applyHeading() {
        val (newText, newSel) = MarkdownEditorOps.cycleHeading(content.text, content.selection)
        content = TextFieldValue(newText, newSel)
    }

    fun applyPrefix(prefix: String, altPrefixes: List<String> = emptyList()) {
        val (newText, newSel) = MarkdownEditorOps.toggleLinePrefix(
            content.text,
            content.selection,
            prefix,
            altPrefixes
        )
        content = TextFieldValue(newText, newSel)
    }

    fun applyDivider() {
        val (newText, newSel) = MarkdownEditorOps.insertDivider(content.text, content.selection)
        content = TextFieldValue(newText, newSel)
    }

    fun applyCode() {
        val (newText, newSel) = MarkdownEditorOps.toggleCode(content.text, content.selection)
        content = TextFieldValue(newText, newSel)
    }

    fun applyLink() {
        val (newText, newSel) = MarkdownEditorOps.toggleLink(content.text, content.selection)
        content = TextFieldValue(newText, newSel)
    }

    // 格式状态判断（工具栏高亮指示）
    fun isTokenActive(token: String): Boolean {
        val text = content.text
        val min = content.selection.min.coerceIn(0, text.length)
        val max = content.selection.max.coerceIn(0, text.length)
        val left = (min - token.length).coerceAtLeast(0)
        val right = (max + token.length).coerceAtMost(text.length)
        return text.substring(left, min) == token && text.substring(max, right) == token
    }

    val starDelimiterRun = remember(content) {
        MarkdownEditorOps.surroundingDelimiterRun(
            content.text,
            content.selection.min,
            content.selection.max,
            '*'
        )
    }
    val underscoreDelimiterRun = remember(content) {
        MarkdownEditorOps.surroundingDelimiterRun(
            content.text,
            content.selection.min,
            content.selection.max,
            '_'
        )
    }
    // 单星 = 斜体，双星 = 加粗，三星 = 加粗 + 斜体；下划线同理避免互相误亮。
    val isBold = starDelimiterRun >= 2
    val isItalic = starDelimiterRun % 2 == 1 || underscoreDelimiterRun % 2 == 1
    val isUnderline = underscoreDelimiterRun >= 2
    val isStrike = remember(content) { isTokenActive("~~") }
    val isCode = remember(content) { isTokenActive("`") }

    val currentLine = remember(content) {
        val text = content.text
        val min = content.selection.min.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (min - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', min).let { if (it < 0) text.length else it }
        text.substring(lineStart, lineEnd)
    }

    val isHeading = currentLine.startsWith("#")

    val isQuote = currentLine.startsWith("> ")

    val isTask = currentLine.matches(Regex("""^[-*+•]\s+\[[ xX]\].*"""))

    val isBullet = !isTask && currentLine.matches(Regex("""^[•\-*+]\s+.*"""))

    val isNumbered = currentLine.matches(Regex("""^\d+\.\s+.*"""))

    val isDivider = currentLine.trim().matches(Regex("""^(—{3,}|-{3,}|\*{3,}|_{3,})$"""))

    val density = LocalDensity.current
    // 底部格式栏的实际占位（含其内边距与 IME / 导航栏 inset）。
    // 正文区据此预留底部留白，避免最后几行被工具栏遮住。
    var measuredToolbarHeight by remember { mutableStateOf(EditorToolbarHeight) }
    // 底部 inset：键盘弹起时取 IME 高度，否则取导航栏高度。二者取一而非相加——
    // IME inset 本身已涵盖屏幕底到键盘顶，再叠加导航栏会重复扣减（对齐 ComposerBar 的做法）。
    val imeBottomInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = if (imeBottomInset > 0.dp) imeBottomInset else navBottomInset
    // 光标跟随滚动：把当前光标位置 bringIntoView，实现「点到哪儿就滑到哪儿」。
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    // scroll state 按编辑对象隔离：切换随笔 / 新建时必须从顶部开始，
    // 否则会继承上一篇章的滚动位置，把状态行一并顶出视野。
    val editorScrollState = key(editorStateKey) { rememberScrollState() }
    // 最近一次文本排版结果：用来把「光标 offset」换算成可视坐标矩形。
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 进入页面时禁止自动跟随：编辑既有随笔时正文回填会改变 selection，
    // 若此时 bringIntoView 就会把页面滚离顶部（状态行也会被一起滚走）。
    // 只有用户真正操作过（点击 / 输入）之后才启用跟随。
    var userInteracted by remember(editorStateKey) { mutableStateOf(false) }

    // 「点到哪儿就滑到哪儿」：光标位置（含点击落点与输入推进）一变，就把它滚入可视区。
    // 用 TextLayoutResult.getCursorRect 把光标 offset 换算成正文内的坐标矩形后再
    // bringIntoView —— 直接 bringIntoView() 只会把「整个文本框」纳入视野（等价于滚到顶部），
    // 无法定位到具体行。触摸按下即更新 selection，故点空白处也会立即把该行带入视野。
    //
    // 只以 selection 为 key：排版结果每次 layout 都会是新对象，若把它也当 key 会自我触发成环。
    // 最新的排版结果通过 rememberUpdatedState 读取即可。
    val latestLayout by rememberUpdatedState(textLayoutResult)
    LaunchedEffect(content.selection, editorMode) {
        if (editorMode != NoteEditorMode.Edit) return@LaunchedEffect
        if (!userInteracted) return@LaunchedEffect
        val layout = latestLayout ?: return@LaunchedEffect
        val offset = content.selection.end.coerceIn(0, layout.layoutInput.text.length)
        // 光标行向上扩一点，避免贴住底部工具栏；再加一段下沿留白，保证正在输入的行完整可见。
        val cursor = layout.getCursorRect(offset)
        val target = Rect(
            left = 0f,
            top = (cursor.top - with(density) { 48.dp.toPx() }).coerceAtLeast(0f),
            right = layout.size.width.toFloat(),
            bottom = cursor.bottom + with(density) { 88.dp.toPx() }
        )
        bringIntoViewRequester.bringIntoView(target)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ====================================================================
        // 核心展示区：根据模式展示「编辑输入框」或「排版预览」
        // ====================================================================
        when (editorMode) {
            NoteEditorMode.Edit -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // 顶部让位：滚动视口已位于状态栏下方，这里只需再让出导航行高度。
                        // 再向上收 8dp，让作为正文首行的「状态 / 记录时间」行紧贴导航按钮下沿，
                        // 避免编辑区与顶部导航栏之间出现大片空白。
                        .padding(
                            top = (YanjiSpacing.TopBarHeight - 8.dp).coerceAtLeast(0.dp)
                        )
                        .padding(bottom = bottomInset)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = measuredToolbarHeight)
                ) {
                    Box(
                        modifier = Modifier
                            // weight 决定可视区高度（占满工具栏以上），fillMaxWidth 撑满宽度；
                            // verticalScroll 让超出部分在可视区内滚动。
                            .fillMaxWidth()
                            .weight(1f)
                            // 正文自行滚动：内容超出可视高度时整体平移，配合下方的光标跟随。
                            .verticalScroll(editorScrollState)
                    ) {
                        // 滚动内容必须放进 Column：Box 的子项会全部堆叠在同一位置，
                        // 「状态行 + 正文」会相互重叠（曾导致状态行压住正文首行）。
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 「状态 / 记录时间」行作为**正文第一行**：随正文一起滚动。
                            NoteEditorHeaderBar(
                                moodScore = moodScore,
                                onMoodScoreChange = { moodScore = it },
                                createdAt = createdAt
                            )
                            Spacer(modifier = Modifier.height(HeaderRowGap))
                            BasicTextField(
                                value = content,
                                onValueChange = {
                                    userInteracted = true
                                    content = it
                                },
                                // 光标随点击 / 输入移动时就地滚入可视区（且避开底部工具栏），
                                // 对应「点到哪儿页面就滑到哪儿」。
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .testTag(NoteEditorTags.ContentInput),
                                textStyle = TextStyle(
                                    fontSize = 17.sp,
                                    lineHeight = 28.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                // 记录排版结果，供上面的光标跟随滚动换算光标矩形。
                                onTextLayout = { textLayoutResult = it },
                                visualTransformation = remember(colorScheme) { MarkdownSyntaxTransformation(colorScheme) },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default
                                ),
                                decorationBox = { innerTextField ->
                                    // 高度随内容增长（外层是 verticalScroll，给的是无限高约束）；
                                    // 用 fillMaxSize 会试图撑满无限高，故这里只约束宽度。
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (content.text.isEmpty()) {
                                            Text(
                                                text = "写下今天值得记住的事…",
                                                fontSize = 17.sp,
                                                lineHeight = 28.sp,
                                                color = YanjiColors.textTertiary
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                              // 末尾缓冲：给最后一行留出足够的可滚动余量，
                              // 避免光标停在第最后一行时被底部格式栏 / 键盘遮住而滚不上来。
                              Spacer(modifier = Modifier.height(measuredToolbarHeight + 24.dp))
                          }
                      }
                  }
              }

              NoteEditorMode.Preview -> {
                  val previewScrollState = rememberScrollState()
                  Column(
                      modifier = Modifier
                          .fillMaxSize()
                          .padding(
                              // 滚动视口已在状态栏下方，只需让出导航行 + 编辑模式状态行的高度。
                              top = (YanjiSpacing.TopBarHeight + HeaderRowHeight + HeaderRowGap - 8.dp)
                                  .coerceAtLeast(0.dp)
                          )
                          .padding(horizontal = 20.dp)
                          .padding(bottom = 24.dp)
                  ) {
                      Box(
                          modifier = Modifier
                              .fillMaxSize()
                              .verticalScroll(previewScrollState)
                      ) {
                          NoteMarkdownPreview(
                              content = content.text,
                              onToggleTask = { lineIndex ->
                                  val updatedText = MarkdownEditorOps.toggleTaskItemAtLine(content.text, lineIndex)
                                  if (updatedText != content.text) {
                                      content = content.copy(text = updatedText)
                                  }
                              }
                          )
                      }
                  }
              }
          }
        // 顶部导航栏：透明渐变玻璃底，参数与「卷卷说」聊天页完全一致
        // （见 AiChatScreen 的 Top Gradient Blur Overlay）：
        // 高度 = 状态栏 + 导航行，背景色自上而下由实渐隐到全透明。
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight + 56.dp)
                .align(Alignment.TopCenter)
                .zIndex(5f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 顶部操作栏：样式与「卷卷说」聊天页一致 —— 36dp 圆形浮层按钮，
        // surface 底色 + 1dp 阴影 + 0.5dp outline 描边，图标 20dp。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(10f)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .testTag("detail_top_bar_back")
                    .clip(CircleShape)
                    .clickable(onClick = { requestBack() }),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.ArrowLeft,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 编辑 / 预览模式切换
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(NoteEditorTags.ModeToggleButton)
                        .clip(CircleShape)
                        .clickable {
                            editorMode = when (editorMode) {
                                NoteEditorMode.Edit -> NoteEditorMode.Preview
                                NoteEditorMode.Preview -> NoteEditorMode.Edit
                            }
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (editorMode == NoteEditorMode.Edit) {
                                PhosphorIcons.Regular.Eye
                            } else {
                                PhosphorIcons.Regular.PencilSimple
                            },
                            contentDescription = if (editorMode == NoteEditorMode.Edit) "预览" else "继续编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 完成（有未保存改动时点亮为主题色）
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(NoteEditorTags.SaveButton)
                        .clip(CircleShape)
                        .clickable(onClick = { saveNote() }),
                    shape = CircleShape,
                    color = if (isDirty) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shadowElevation = 1.dp,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "完成",
                            tint = if (isDirty) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ---- 底部快捷格式栏：固定在页面底部；输入法弹出时由键盘覆盖，不随 IME 上浮 ----
        if (editorMode == NoteEditorMode.Edit) {
            // inset 放在外层、只测量内层工具栏的「固有高度」，
            // 正文区就能用「固有高度」这个稳定值预留留白，不会被键盘高度污染。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
                    // 与正文区共用同一套底部 inset：键盘弹起贴住 IME，隐藏时避让系统导航栏。
                    .padding(bottom = bottomInset)
            ) {
                NoteFormatToolbar(
                    // 实测工具栏固有高度，供正文区预留底部留白。
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        measuredToolbarHeight = with(density) { coordinates.size.height.toDp() }
                    },
                    isHeading = isHeading,
                    isBold = isBold,
                    isItalic = isItalic,
                    isUnderline = isUnderline,
                    isStrike = isStrike,
                    isCode = isCode,
                    isQuote = isQuote,
                    isTask = isTask,
                    isBullet = isBullet,
                    isNumbered = isNumbered,
                    isDivider = isDivider,
                    isLink = false,
                    onHeading = ::applyHeading,
                    onBold = { applyInline("**") },
                    onItalic = { applyInline("*") },
                    onUnderline = { applyInline("__") },
                    onStrike = { applyInline("~~") },
                    onCode = ::applyCode,
                    onQuote = { applyPrefix("> ") },
                    onTask = { applyPrefix("- [ ] ", listOf("- [x] ", "- [X] ")) },
                    onList = { applyPrefix("• ", listOf("- ", "* ", "+ ")) },
                    onNumberedList = { applyPrefix("1. ") },
                    onDivider = ::applyDivider,
                    onLink = ::applyLink
                )
            }
        }
    }

    if (showBackConfirm) {
        NoteDiscardOrDraftDialog(
            onSaveAsDraft = {
                showBackConfirm = false
                saveAsDraft()
            },
            onDiscard = {
                showBackConfirm = false
                onBack()
            },
            onDismiss = { showBackConfirm = false }
        )
    }
}

/**
 * 未点「完成」直接返回时的去向确认。
 */
@Composable
private fun NoteDiscardOrDraftDialog(
    onSaveAsDraft: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onSaveAsDraft,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("保存为草稿", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("继续编辑", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDiscard) {
                    Text("直接放弃", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        title = {
            Text("还没保存", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "先把这篇随笔存为草稿，还是直接放弃本次修改？",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(YanjiRadius.DialogRadius),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * 底部 Markdown 格式工具栏。
 *
 * 具备 Markdown 核心快捷工具：
 * 标题、加粗、斜体、下划线、删除线、代码、引用、待办、列表、有序列表、分割线、链接。
 * 水平平滑滚动，适应各种屏幕宽度，随软键盘升降。
 */
@Composable
private fun NoteFormatToolbar(
    isHeading: Boolean,
    isBold: Boolean,
    isItalic: Boolean,
    isUnderline: Boolean,
    isStrike: Boolean,
    isCode: Boolean,
    isQuote: Boolean,
    isTask: Boolean,
    isBullet: Boolean,
    isNumbered: Boolean,
    isDivider: Boolean,
    isLink: Boolean,
    onHeading: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onCode: () -> Unit,
    onQuote: () -> Unit,
    onTask: () -> Unit,
    onList: () -> Unit,
    onNumberedList: () -> Unit,
    onDivider: () -> Unit,
    onLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val toolbarColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = YanjiSpacing.ItemGap, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        WarmTooltipGroup(delay = 400, warmWindow = 300, travel = 320) {
            Surface(
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
                color = toolbarColor,
                shadowElevation = 0.dp
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 先放学习随笔最常用的结构动作，再放低频排版能力。
                        FormatButton(
                            icon = PhosphorIcons.Regular.CheckSquare,
                            activeIcon = PhosphorIcons.Bold.CheckSquare,
                            label = "待办",
                            tag = NoteEditorTags.TaskButton,
                            active = isTask,
                            onClick = onTask
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.ListBullets,
                            activeIcon = PhosphorIcons.Bold.ListBullets,
                            label = "列表",
                            tag = NoteEditorTags.ListButton,
                            active = isBullet,
                            onClick = onList
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextB,
                            activeIcon = PhosphorIcons.Bold.TextB,
                            label = "加粗",
                            tag = NoteEditorTags.BoldButton,
                            active = isBold,
                            onClick = onBold
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextH,
                            activeIcon = PhosphorIcons.Bold.TextH,
                            label = "标题",
                            tag = NoteEditorTags.HeadingButton,
                            active = isHeading,
                            onClick = onHeading
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Quotes,
                            activeIcon = PhosphorIcons.Bold.Quotes,
                            label = "引用",
                            tag = NoteEditorTags.QuoteButton,
                            active = isQuote,
                            onClick = onQuote
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextItalic,
                            activeIcon = PhosphorIcons.Bold.TextItalic,
                            label = "斜体",
                            tag = NoteEditorTags.ItalicButton,
                            active = isItalic,
                            onClick = onItalic
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.ListNumbers,
                            activeIcon = PhosphorIcons.Bold.ListNumbers,
                            label = "编号",
                            tag = NoteEditorTags.NumberedListButton,
                            active = isNumbered,
                            onClick = onNumberedList
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextAUnderline,
                            activeIcon = PhosphorIcons.Bold.TextAUnderline,
                            label = "下划线",
                            tag = NoteEditorTags.UnderlineButton,
                            active = isUnderline,
                            onClick = onUnderline
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextStrikethrough,
                            activeIcon = PhosphorIcons.Bold.TextStrikethrough,
                            label = "删除线",
                            tag = NoteEditorTags.StrikeButton,
                            active = isStrike,
                            onClick = onStrike
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Code,
                            activeIcon = PhosphorIcons.Bold.Code,
                            label = "代码",
                            tag = NoteEditorTags.CodeButton,
                            active = isCode,
                            onClick = onCode
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Minus,
                            activeIcon = PhosphorIcons.Bold.Minus,
                            label = "分割线",
                            tag = NoteEditorTags.DividerButton,
                            active = isDivider,
                            onClick = onDivider
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Link,
                            activeIcon = PhosphorIcons.Bold.Link,
                            label = "链接",
                            tag = NoteEditorTags.LinkButton,
                            active = isLink,
                            onClick = onLink
                        )
                    }

                    if (scrollState.canScrollBackward) {
                        ToolbarScrollHint(
                            icon = PhosphorIcons.Regular.CaretLeft,
                            modifier = Modifier.align(Alignment.CenterStart),
                            color = toolbarColor,
                            reverse = true
                        )
                    }
                    if (scrollState.canScrollForward) {
                        ToolbarScrollHint(
                            icon = PhosphorIcons.Regular.CaretRight,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = toolbarColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarScrollHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    reverse: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(30.dp)
            .background(
                Brush.horizontalGradient(
                    colors = if (reverse) {
                        listOf(color, color.copy(alpha = 0f))
                    } else {
                        listOf(color.copy(alpha = 0f), color)
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(14.dp)
        )
    }
}

/** 格式栏中的单个纯图标工具项；激活时切换为粗描边图标与主题色。 */
@Composable
private fun FormatButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    active: Boolean,
    onClick: () -> Unit
) {
    WarmTooltip(content = label) {
        Box(
            modifier = Modifier
                .testTag(tag)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(9.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (active) activeIcon else icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
