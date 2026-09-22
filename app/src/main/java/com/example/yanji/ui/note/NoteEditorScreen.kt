package com.example.yanji.ui.note

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.NoteEntry
import com.example.yanji.theme.*
import com.example.yanji.ui.components.TopFadeScrim
import com.example.yanji.ui.components.WarmTooltip
import com.example.yanji.ui.components.WarmTooltipGroup
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** UI 测试定位锚点：与 NoteEditorScreenInstrumentedTest 共享，避免断言依赖中文文案。 */
object NoteEditorTags {
    const val ContentInput = "note_content_input"

    /** 完成按钮。tag 名沿用历史上的 save_button，插桩测试契约不可改名。 */
    const val SaveButton = "note_save_button"

    /** 顶栏编辑 / 预览圆形模式按钮。 */
    const val ModeToggleButton = "note_mode_toggle_button"
    const val UndoButton = "note_format_undo"
    const val RedoButton = "note_format_redo"
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

    /** 返回未保存确认弹窗按钮 */
    const val DiscardConfirmButton = "note_dialog_discard"
    const val DiscardDraftButton = "note_dialog_draft"
}

/** 底部格式栏高度 + 与正文区的留白，作为正文区底部内边距，避免最后几行被遮挡。 */
private val EditorToolbarHeight = 60.dp

enum class NoteEditorMode {
    Edit,
    Preview
}

/**
 * 编辑页「初值快照」：进入页面时记录的正文 / 打分 / 收藏。
 * 用来判断当前是否有未保存改动（决定完成键是否高亮、返回是否提示），
 * 用值对比而非「有没有动过」标志位，改回原样时按钮不会停在点亮态。
 */
internal data class EditorSnapshot(
    val content: String,
    val moodScore: Int,
    val isFavorite: Boolean
)

/**
 * 判脏：正文按 `trim()` 后比较（把改动改回原样不算脏，返回时不该再弹确认卡片），
 * 心情分与收藏按值比较。抽成纯函数是为了让这条用户可见的判定能被直接单测覆盖。
 */
internal fun isNoteDraftDirty(
    currentContent: String,
    snapshot: EditorSnapshot,
    moodScore: Int,
    isFavorite: Boolean
): Boolean =
    currentContent.trim() != snapshot.content.trim() ||
        moodScore != snapshot.moodScore ||
        isFavorite != snapshot.isFavorite

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
    viewModel: NoteViewModel = com.example.yanji.di.yanjiViewModel { container ->
        NoteViewModel(container.repository, container.statisticsRepository)
    }
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val editorFocusRequester = remember { FocusRequester() }
    val colorScheme = MaterialTheme.colorScheme
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notes = state.notes

    fun showKeyboard() {
        coroutineScope.launch {
            yield()
            keyboardController?.show()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val view = (context as? Activity)?.currentFocus ?: (context as? Activity)?.window?.decorView
            if (view != null) {
                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

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
    val editorHistory = remember(editorStateKey) { EditorHistory() }
    var userInteracted by remember(editorStateKey) { mutableStateOf(false) }

    LaunchedEffect(existingEntry?.id) {
        val entry = existingEntry ?: return@LaunchedEffect
        if (!draftInitialized) {
            content = TextFieldValue(entry.content)
            draftInitialized = true
        }
    }

    // 当前视图模式：
    // 1. 如果是保存了的随笔，默认进入预览模式。
    // 2. 如果是暂存为草稿的随笔和新建的随笔，默认进入编辑模式。
    var editorModeInitialized by rememberSaveable(editorStateKey) {
        mutableStateOf(noteId.isNullOrBlank())
    }
    var editorMode by rememberSaveable(editorStateKey) {
        mutableStateOf(
            if (existingEntry != null && !existingEntry.isDraft) {
                NoteEditorMode.Preview
            } else {
                NoteEditorMode.Edit
            }
        )
    }
    LaunchedEffect(existingEntry?.id) {
        val entry = existingEntry ?: return@LaunchedEffect
        if (!editorModeInitialized) {
            editorMode = if (entry.isDraft) NoteEditorMode.Edit else NoteEditorMode.Preview
            editorModeInitialized = true
        }
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

    var currentNoteId by rememberSaveable(editorStateKey) {
        mutableStateOf(existingEntry?.id ?: noteId)
    }
    var lastSavedSnapshot by remember(editorStateKey) {
        mutableStateOf<EditorSnapshot?>(null)
    }

    fun saveNote() {
        if (content.text.isBlank()) {
            Toast.makeText(context, "请写点今日内容吧", Toast.LENGTH_SHORT).show()
            return
        }
        val targetId = currentNoteId ?: UUID.randomUUID().toString()
        currentNoteId = targetId
        val finalEntry = NoteEntry(
            id = targetId,
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
            isFavorite = isFavorite,
            isDraft = false
        )
        viewModel.saveNote(finalEntry)
        lastSavedSnapshot = EditorSnapshot(
            content = finalEntry.content,
            moodScore = moodScore,
            isFavorite = isFavorite
        )
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
        val targetId = currentNoteId ?: UUID.randomUUID().toString()
        currentNoteId = targetId
        val draft = NoteEntry(
            id = targetId,
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
            isFavorite = isFavorite,
            isDraft = true
        )
        viewModel.saveNote(draft)
        Toast.makeText(context, "已保存为草稿", Toast.LENGTH_SHORT).show()
        onBack()
    }

    // 「还没保存」提示与保存键高亮共用同一份脏值判断：
    // 以进入页面时的初值快照（或最近一次保存快照）对比，改回原样不算有改动，返回也不再弹卡片。
    val initialSnapshot = remember(existingEntry?.id) {
        val entry = existingEntry
        EditorSnapshot(
            content = entry?.content ?: "",
            moodScore = entry?.moodScore ?: 5,
            isFavorite = entry?.isFavorite ?: false
        )
    }
    val activeSnapshot = lastSavedSnapshot ?: initialSnapshot
    // 编辑既有随笔时，正文由 LaunchedEffect 异步回填；回填前 content 还是空串，
    // 直接比对会误判为「有改动」而让保存键闪一下。这里以 draftInitialized 收口。
    val contentForDiff = if (existingEntry != null && !draftInitialized && lastSavedSnapshot == null) {
        activeSnapshot.content
    } else {
        content.text
    }
    // 整篇 trim 两次只为判脏，成本随文档长度线性增长：缓存到输入/快照真正变化时才算。
    val isDirty = remember(contentForDiff, activeSnapshot, moodScore, isFavorite) {
        isNoteDraftDirty(
            currentContent = contentForDiff,
            snapshot = activeSnapshot,
            moodScore = moodScore,
            isFavorite = isFavorite
        )
    }

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

    fun handleUndo() {
        userInteracted = true
        val prev = editorHistory.undo(content)
        if (prev != null) {
            content = prev
        }
    }

    fun handleRedo() {
        userInteracted = true
        val next = editorHistory.redo(content)
        if (next != null) {
            content = next
        }
    }

    // Markdown 语法操作助手
    fun applyInline(token: String) {
        editorHistory.recordExplicitSnapshot(content)
        val (newText, newSel) = MarkdownEditorOps.toggleInlineToken(
            content.text,
            content.selection.min,
            content.selection.max,
            token
        )
        content = TextFieldValue(newText, newSel)
    }

    fun applyHeading() {
        editorHistory.recordExplicitSnapshot(content)
        val (newText, newSel) = MarkdownEditorOps.cycleHeading(content.text, content.selection)
        content = TextFieldValue(newText, newSel)
    }

    fun applyPrefix(prefix: String, altPrefixes: List<String> = emptyList()) {
        editorHistory.recordExplicitSnapshot(content)
        val (newText, newSel) = MarkdownEditorOps.toggleLinePrefix(
            content.text,
            content.selection,
            prefix,
            altPrefixes
        )
        content = TextFieldValue(newText, newSel)
    }

    fun applyDivider() {
        editorHistory.recordExplicitSnapshot(content)
        val (newText, newSel) = MarkdownEditorOps.insertDivider(content.text, content.selection)
        content = TextFieldValue(newText, newSel)
    }

    fun applyCode() {
        editorHistory.recordExplicitSnapshot(content)
        val (newText, newSel) = MarkdownEditorOps.toggleCode(content.text, content.selection)
        content = TextFieldValue(newText, newSel)
    }

    fun applyLink() {
        editorHistory.recordExplicitSnapshot(content)
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
        val (lineStart, lineEnd) = lineBoundsOf(content.text, content.selection.min)
        content.text.substring(lineStart, lineEnd)
    }

    val isHeading = currentLine.startsWith("#")

    val isQuote = currentLine.startsWith("> ")

    val isTask = currentLine.matches(NoteMarkdownPattern.TaskLine)

    val isBullet = !isTask && currentLine.matches(NoteMarkdownPattern.BulletLine)

    val isNumbered = currentLine.matches(NoteMarkdownPattern.NumberedLine)

    val isDivider = currentLine.trim().matches(NoteMarkdownPattern.DividerLine)

    val density = LocalDensity.current
    // 底部格式栏的实际占位（含其内边距与 IME / 导航栏 inset）。
    // 正文区据此预留底部留白，避免最后几行被工具栏遮住。
    var measuredToolbarHeight by remember { mutableStateOf(EditorToolbarHeight) }
    // 底部 inset：键盘弹起时取 IME 高度，否则取导航栏高度。二者取一而非相加——
    // IME inset 本身已涵盖屏幕底到键盘顶，再叠加导航栏会重复扣减（对齐 ComposerBar 的做法）。
    val imeBottomInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = if (imeBottomInset > 0.dp) imeBottomInset else navBottomInset
    var editorViewportHeightPx by remember { mutableIntStateOf(0) }
    // scroll state 按编辑对象隔离：切换随笔 / 新建时必须从顶部开始，
    // 否则会继承上一篇章的滚动位置，把状态行一并顶出视野。
    val editorScrollState = key(editorStateKey) { rememberScrollState() }
    val previewScrollState = key(editorStateKey) { rememberScrollState() }

    // 最近一次文本排版结果：用来把「光标 offset」换算成可视坐标矩形。
    // 只在下方的协程里读取，不在屏幕体里读取——屏幕体一旦订阅它，每次排版完成都会多跑一遍整屏重组。
    val textLayoutState = remember { mutableStateOf<TextLayoutResult?>(null) }

    // 滚动视口从屏幕顶端开始（与 AI 聊天页的 LazyColumn 一致），所以顶部让位必须算上状态栏高度，
    // 且让位要放进**滚动内容内部**：正文静止时首行仍落在导航按钮下沿，滚动时则能在导航栏区域里
    // 被 TopFadeScrim 渐隐穿过，而不是被硬切在导航栏下方（那看起来就像"导航栏把正文挡住了"）。
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 让位 = 状态栏 + 36dp，状态行进一步靠近顶栏导航按钮，消除空余留白。
    val topContentInset = (statusBarHeight + 36.dp).coerceAtLeast(0.dp)

    // 精准光标跟随与视口避让：
    // 当光标改变（点击文字/输入）、键盘弹起/收起、视口尺寸变化时，确保光标所处行拥有充足的可视空间。
    // 绝不让光标贴在视口底部或被底部工具栏与输入法遮挡，保证点击跳转幅度充足，露出上下文。
    LaunchedEffect(content.selection, editorMode, bottomInset, editorViewportHeightPx) {
        if (editorMode != NoteEditorMode.Edit) return@LaunchedEffect
        if (!userInteracted) return@LaunchedEffect
        // collectLatest 完整保留原有语义：新的排版结果到达时，取消上一次还在跑的跟随动画再重来。
        snapshotFlow { textLayoutState.value }.collectLatest { result ->
            val layout = result ?: return@collectLatest
            if (editorViewportHeightPx <= 0) return@collectLatest

            val offset = content.selection.end.coerceIn(0, layout.layoutInput.text.length)
            val cursorRect = layout.getCursorRect(offset)

            // 文本框在滚动容器内的起始 Y 偏移：
            // Column 内部依次是：Spacer(topContentInset) + NoteEditorHeaderBar(28dp) + Spacer(HeaderRowGap 14dp)
            val textFieldTopInScrollPx = with(density) { (topContentInset + HeaderRowHeight + HeaderRowGap).toPx() }
            val cursorTopInScroll = textFieldTopInScrollPx + cursorRect.top
            val cursorBottomInScroll = textFieldTopInScrollPx + cursorRect.bottom

            val currentScroll = editorScrollState.value.toFloat()
            val cursorVisibleTop = cursorTopInScroll - currentScroll
            val cursorVisibleBottom = cursorBottomInScroll - currentScroll

            // 安全视口边界：
            // 顶部安全区：留出顶部导航渐变遮罩高度，避免文字被顶栏遮盖
            val topSafeMarginPx = with(density) { (topContentInset + 12.dp).toPx() }
            // 底部安全区：留出充足的阅读与操作空间（至少 150dp），确保不仅避开底部工具栏与输入法，
            // 还能让被点击编辑的文字落在屏幕可视区的舒适黄金位置，并露出下方 2~3 行上下文。
            val bottomSafeMarginPx = with(density) { 150.dp.toPx() }

            if (cursorVisibleBottom > editorViewportHeightPx - bottomSafeMarginPx) {
                // 光标靠下或被输入法/工具栏遮挡：
                // 将光标平滑滚动到偏中上方的舒适阅读位置（约视口 40% 处），跳转幅度充足
                val targetVisibleY = (editorViewportHeightPx * 0.40f).coerceIn(
                    topSafeMarginPx + with(density) { 32.dp.toPx() },
                    (editorViewportHeightPx - bottomSafeMarginPx).coerceAtLeast(topSafeMarginPx)
                )
                val targetScroll = (cursorTopInScroll - targetVisibleY).roundToInt()
                    .coerceIn(0, editorScrollState.maxValue)
                editorScrollState.animateScrollTo(
                    targetScroll,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
            } else if (cursorVisibleTop < topSafeMarginPx) {
                // 光标靠上滚进顶栏渐变区：向下滚回安全可视区
                val targetScroll = (cursorTopInScroll - topSafeMarginPx).roundToInt()
                    .coerceIn(0, editorScrollState.maxValue)
                editorScrollState.animateScrollTo(
                    targetScroll,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val topBarHazeState = remember { HazeState() }

    // 「状态 / 记录时间」行 + 其后间距：编辑与预览两种模式版式一致，收成一个局部组合块，
    // 避免同一段布局在两个分支里各写一遍。
    val headerRow: @Composable () -> Unit = {
        NoteEditorHeaderBar(
            moodScore = moodScore,
            onMoodScoreChange = { moodScore = it },
            createdAt = createdAt
        )
        Spacer(modifier = Modifier.height(HeaderRowGap))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ====================================================================
        // 核心展示区：根据模式展示「编辑输入框」或「排版预览」
        // 挂载 hazeSource，供顶栏无边框半透明磨砂按钮获取底层真实滚动内容
        // ====================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = topBarHazeState)
        ) {
            when (editorMode) {
            NoteEditorMode.Edit -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                            .onGloballyPositioned { coordinates ->
                                editorViewportHeightPx = coordinates.size.height
                            }
                            // 正文自行滚动：内容超出可视高度时整体平移，配合下方的光标跟随。
                            .verticalScroll(editorScrollState)
                    ) {
                        // 滚动内容必须放进 Column：Box 的子项会全部堆叠在同一位置，
                        // 「状态行 + 正文」会相互重叠（曾导致状态行压住正文首行）。
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 顶部让位属于滚动内容：随正文一起上移，从而能渐隐穿过导航栏。
                            Spacer(modifier = Modifier.height(topContentInset))
                            // 「状态 / 记录时间」行作为**正文第一行**：随正文一起滚动。
                            headerRow()
                            BasicTextField(
                                value = content,
                                onValueChange = {
                                    userInteracted = true
                                    editorHistory.recordTyping(oldValue = content, newValue = it)
                                    content = it
                                },
                                // 光标随点击 / 输入移动时就地滚入可视区（且避开底部工具栏），
                                // 对应「点到哪儿页面就滑到哪儿」。
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(editorFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            userInteracted = true
                                            showKeyboard()
                                        }
                                    }
                                    .testTag(NoteEditorTags.ContentInput),
                                textStyle = TextStyle(
                                    fontSize = 17.sp,
                                    lineHeight = 28.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                // 记录排版结果，供上面的光标跟随滚动换算光标矩形。
                                onTextLayout = { textLayoutState.value = it },
                                visualTransformation = remember(colorScheme) { MarkdownSyntaxTransformation(colorScheme) },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default
                                ),
                                decorationBox = { innerTextField ->
                                    // 高度随内容增长（外层是 verticalScroll，给的是无限高约束）；
                                    // 最小 120dp 确保短文本也有充分的直接点击触控面积，且绝不能包 clickable 拦截 Compose 原生光标手势。
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 120.dp)
                                    ) {
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
                            // 正文下方大片空白点击区：点击下半屏空白处，自动聚焦并将光标移至文本末尾，唤起输入法
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 500.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        userInteracted = true
                                        content = content.copy(selection = TextRange(content.text.length))
                                        editorFocusRequester.requestFocus()
                                        showKeyboard()
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
                  Column(
                      modifier = Modifier
                          .fillMaxSize()
                          .padding(horizontal = 20.dp)
                          .padding(bottom = 24.dp)
                  ) {
                      Box(
                          modifier = Modifier
                              .fillMaxSize()
                              .verticalScroll(previewScrollState)
                      ) {
                          Column(modifier = Modifier.fillMaxWidth()) {
                              // 与编辑模式同理：让位属于滚动内容，预览正文也能渐隐穿过导航栏。
                              Spacer(modifier = Modifier.height(topContentInset))
                              // 预览同样展示「状态 / 记录时间」行，与编辑模式版式一致。
                              headerRow()
                              NoteMarkdownPreview(
                                  content = content.text,
                                  onToggleTask = { lineIndex ->
                                      val updatedText =
                                          MarkdownEditorOps.toggleTaskItemAtLine(content.text, lineIndex)
                                      if (updatedText != content.text) {
                                          editorHistory.recordExplicitSnapshot(content)
                                          content = content.copy(text = updatedText)
                                      }
                                  }
                              )
                          }
                      }
                  }
              }
          }
        }
        // 顶部渐隐遮罩：多段平滑背景渐变，与 AI 聊天页共用，正文向上滚动时自然羽化消融在背景中。
        TopFadeScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f)
        )

        // 顶部操作栏：无边框且半透明磨砂玻璃按钮，实时虚化穿透的滚动文字与底色。
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
            FrostedTopBarButton(
                onClick = { requestBack() },
                hazeState = topBarHazeState,
                modifier = Modifier.testTag("detail_top_bar_back")
            ) {
                Icon(
                    imageVector = RemixIcons.ArrowLeftLine,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            val isEdit = editorMode == NoteEditorMode.Edit
            val modeButtonOffsetX by animateDpAsState(
                targetValue = if (isEdit) (-44).dp else 0.dp,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "modeButtonOffsetX"
            )
            val topBarRightWidth by animateDpAsState(
                targetValue = if (isEdit) 80.dp else 36.dp,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "topBarRightWidth"
            )
            val saveButtonAlpha by animateFloatAsState(
                targetValue = if (isEdit) 1f else 0f,
                animationSpec = tween(durationMillis = if (isEdit) 240 else 160),
                label = "saveButtonAlpha"
            )
            val saveButtonScale by animateFloatAsState(
                targetValue = if (isEdit) 1f else 0.6f,
                animationSpec = tween(durationMillis = if (isEdit) 240 else 160),
                label = "saveButtonScale"
            )

            Box(
                modifier = Modifier
                    .width(topBarRightWidth)
                    .height(36.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                // 编辑 / 预览模式切换（在预览模式下自动平滑移位至右上角）
                FrostedTopBarButton(
                    onClick = {
                        editorMode = when (editorMode) {
                            NoteEditorMode.Edit -> NoteEditorMode.Preview
                            NoteEditorMode.Preview -> NoteEditorMode.Edit
                        }
                    },
                    hazeState = topBarHazeState,
                    modifier = Modifier
                        .offset { IntOffset(x = modeButtonOffsetX.roundToPx(), y = 0) }
                        .testTag(NoteEditorTags.ModeToggleButton)
                ) {
                    Icon(
                        imageVector = if (editorMode == NoteEditorMode.Edit) {
                            RemixIcons.EyeLine
                        } else {
                            RemixIcons.EditLine
                        },
                        contentDescription = if (editorMode == NoteEditorMode.Edit) "预览" else "继续编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 完成 / 保存（预览模式下隐藏，仅在编辑模式展示；有未保存改动时点亮为主题色）
                if (saveButtonAlpha > 0.001f) {
                    val saveBgColor = if (isDirty) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    }
                    FrostedTopBarButton(
                        onClick = { saveNote() },
                        enabled = isEdit,
                        hazeState = topBarHazeState,
                        tintColor = saveBgColor,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = saveButtonAlpha
                                scaleX = saveButtonScale
                                scaleY = saveButtonScale
                            }
                            .testTag(NoteEditorTags.SaveButton)
                    ) {
                        Icon(
                            imageVector = RemixIcons.CheckLine,
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

        // ---- 底部快捷格式栏：悬浮在页面底部，随软键盘或系统导航栏平滑升降 ----
        if (editorMode == NoteEditorMode.Edit) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
                    // 与正文区共用同一套底部 inset：键盘弹起贴住 IME，隐藏时避让系统导航栏
                    .padding(bottom = bottomInset)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    // 实测悬浮工具栏占用高度（含外边距），供正文区预留留白
                    .onGloballyPositioned { coordinates ->
                        measuredToolbarHeight = with(density) { coordinates.size.height.toDp() }
                    }
            ) {
                NoteFormatToolbar(
                    canUndo = editorHistory.canUndo,
                    canRedo = editorHistory.canRedo,
                    onUndo = ::handleUndo,
                    onRedo = ::handleRedo,
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
 * 未点「完成」直接返回时的确认卡片：
 * 采用极简悬浮卡片设计，直接提示「是否将本次修改暂存」，
 * 左侧「放弃」（红底），右侧「保存」（主题色底）。
 */
@Composable
private fun NoteDiscardOrDraftDialog(
    onSaveAsDraft: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            shape = RoundedCornerShape(YanjiRadius.DialogRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "是否将本次修改暂存",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDiscard,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(NoteEditorTags.DiscardConfirmButton),
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(
                            text = "放弃",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onSaveAsDraft,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(NoteEditorTags.DiscardDraftButton),
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "保存",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
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
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        WarmTooltipGroup(delay = 400, warmWindow = 300, travel = 320) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(YanjiRadius.Small),
                color = surfaceColor,
                shadowElevation = 3.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 撤回 / 反撤回按钮
                        FormatButton(
                            icon = RemixIcons.ArrowGoBackLine,
                            label = "撤回",
                            tag = NoteEditorTags.UndoButton,
                            enabled = canUndo,
                            onClick = onUndo
                        )
                        FormatButton(
                            icon = RemixIcons.ArrowGoForwardLine,
                            label = "反撤回",
                            tag = NoteEditorTags.RedoButton,
                            enabled = canRedo,
                            onClick = onRedo
                        )

                        // 细分隔线：区分历史撤销区与排版样式区
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(18.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )

                        // 先放学习随笔最常用的结构动作，再放低频排版能力。
                        FormatButton(
                            icon = RemixIcons.CheckboxLine,
                            activeIcon = RemixIcons.CheckboxFill,
                            label = "待办",
                            tag = NoteEditorTags.TaskButton,
                            active = isTask,
                            onClick = onTask
                        )
                        FormatButton(
                            icon = RemixIcons.ListUnordered,
                            label = "列表",
                            tag = NoteEditorTags.ListButton,
                            active = isBullet,
                            onClick = onList
                        )
                        FormatButton(
                            icon = RemixIcons.Bold,
                            label = "加粗",
                            tag = NoteEditorTags.BoldButton,
                            active = isBold,
                            onClick = onBold
                        )
                        FormatButton(
                            icon = RemixIcons.Heading,
                            label = "标题",
                            tag = NoteEditorTags.HeadingButton,
                            active = isHeading,
                            onClick = onHeading
                        )
                        FormatButton(
                            icon = RemixIcons.DoubleQuotesL,
                            label = "引用",
                            tag = NoteEditorTags.QuoteButton,
                            active = isQuote,
                            onClick = onQuote
                        )
                        FormatButton(
                            icon = RemixIcons.Italic,
                            label = "斜体",
                            tag = NoteEditorTags.ItalicButton,
                            active = isItalic,
                            onClick = onItalic
                        )
                        FormatButton(
                            icon = RemixIcons.ListOrdered,
                            label = "编号",
                            tag = NoteEditorTags.NumberedListButton,
                            active = isNumbered,
                            onClick = onNumberedList
                        )
                        FormatButton(
                            icon = RemixIcons.Underline,
                            label = "下划线",
                            tag = NoteEditorTags.UnderlineButton,
                            active = isUnderline,
                            onClick = onUnderline
                        )
                        FormatButton(
                            icon = RemixIcons.Strikethrough,
                            label = "删除线",
                            tag = NoteEditorTags.StrikeButton,
                            active = isStrike,
                            onClick = onStrike
                        )
                        FormatButton(
                            icon = RemixIcons.CodeLine,
                            label = "代码",
                            tag = NoteEditorTags.CodeButton,
                            active = isCode,
                            onClick = onCode
                        )
                        FormatButton(
                            icon = RemixIcons.Separator,
                            label = "分割线",
                            tag = NoteEditorTags.DividerButton,
                            active = isDivider,
                            onClick = onDivider
                        )
                        FormatButton(
                            icon = RemixIcons.Link,
                            label = "链接",
                            tag = NoteEditorTags.LinkButton,
                            active = isLink,
                            onClick = onLink
                        )
                    }

                    if (scrollState.canScrollBackward) {
                        ToolbarScrollHint(
                            icon = RemixIcons.ArrowLeftSLine,
                            modifier = Modifier.align(Alignment.CenterStart),
                            color = surfaceColor,
                            reverse = true
                        )
                    }
                    if (scrollState.canScrollForward) {
                        ToolbarScrollHint(
                            icon = RemixIcons.ArrowRightSLine,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            color = surfaceColor
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
            .width(26.dp)
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp)
        )
    }
}

/** 格式栏中的单个纯图标工具项；激活时切换为粗描边图标与主题色，支持禁用态。 */
@Composable
private fun FormatButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector = icon,
    label: String,
    tag: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    WarmTooltip(content = label) {
        Box(
            modifier = Modifier
                .testTag(tag)
                .size(36.dp)
                .clip(CircleShape)
                .then(
                    if (enabled) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            val tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = if (active) activeIcon else icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * 随笔编辑页顶栏按钮：无边框半透明磨砂玻璃质感。
 * 挂接 [HazeState] 实时虚化底层滚动穿透的文字与内容，配备微噪点磨砂颗粒。
 * 采用安静克制的触控反馈：移除容易遮挡转场动效的墨水暗色涟漪（indication = null），
 * 改用基于 [rememberPressScale] 的微物理弹性缩放与微透明度变化，触控干脆清爽、动画丝滑不僵硬。
 */
@Composable
private fun FrostedTopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hazeState: HazeState? = null,
    tintColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = YanjiLiquidGlass
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = rememberPressScale(interactionSource, targetScale = 0.92f)
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "frostedButtonPressAlpha"
    )

    val hazeModifier = if (hazeState != null && tokens.blurRadius > 0.dp) {
        Modifier.hazeEffect(state = hazeState) {
            blurRadius = tokens.blurRadius
            tints = listOf(HazeTint(tintColor))
            noiseFactor = 0.08f
            backgroundColor = Color.Transparent
        }
    } else {
        Modifier.background(tintColor)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
            }
            .size(36.dp)
            .clip(CircleShape)
            .then(hazeModifier)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

