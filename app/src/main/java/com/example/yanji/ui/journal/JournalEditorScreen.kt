package com.example.yanji.ui.journal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.journal.JournalHeaderPreferences
import com.example.yanji.data.JournalEntry
import com.example.yanji.theme.*
import com.example.yanji.ui.components.WarmTooltip
import com.example.yanji.ui.components.WarmTooltipGroup
import java.util.UUID

/** UI 测试定位锚点：与 JournalEditorScreenInstrumentedTest 共享，避免断言依赖中文文案。 */
object JournalEditorTags {
    const val ContentInput = "journal_content_input"

    /** 完成按钮。tag 名沿用历史上的 save_button，插桩测试契约不可改名。 */
    const val SaveButton = "journal_save_button"

    /** 顶栏编辑 / 预览圆形模式按钮。 */
    const val ModeToggleButton = "journal_mode_toggle_button"
    const val BoldButton = "journal_format_bold"
    const val ItalicButton = "journal_format_italic"
    const val UnderlineButton = "journal_format_underline"
    const val ListButton = "journal_format_list"
    const val DividerButton = "journal_format_divider"

    /** 新增的 Markdown 工具栏与模式切换 Tag */
    const val HeadingButton = "journal_format_heading"
    const val StrikeButton = "journal_format_strike"
    const val CodeButton = "journal_format_code"
    const val QuoteButton = "journal_format_quote"
    const val TaskButton = "journal_format_task"
    const val NumberedListButton = "journal_format_num_list"
    const val LinkButton = "journal_format_link"
}

/** 底部格式栏高度 + 与正文区的留白，作为正文区底部内边距，避免最后几行被遮挡。 */
private val EditorToolbarHeight = 68.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class JournalEditorMode {
    Edit,
    Preview
}

/**
 * 研途随笔 Markdown 编辑器（支持实时高亮编辑与排版预览）。
 *
 * 核心架构：
 *  - 实时编辑：标准 CommonMark 源码编辑，带 1:1 等长语法高亮（[MarkdownSyntaxTransformation]），
 *    零输入法跳光标问题，支持快捷 Markdown 工具栏。
 *  - 实时预览：一键切换「阅读预览」模式（[JournalMarkdownPreview]），支持富文本排版与交互式待办勾选。
 *  - 顶部吸顶：PeekRating 5 星状态打分、时间与天气元数据固定展示，滚动正文平滑穿透。
 */
@Composable
fun JournalEditorScreen(
    journalId: String?,
    date: String,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    viewModel: JournalViewModel = com.example.yanji.di.yanjiViewModel { container ->
        JournalViewModel(container.repository, container.statisticsRepository)
    }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val journals = state.journals

    // MainActivity 使用 edge-to-edge + adjustResize。部分系统键盘会在大型文本框获得焦点时
    // 额外平移整个 Window，导致页头移出屏幕，同时又与 imePadding 叠加。
    // 编辑页由 Compose 统一消费 IME Insets，离开页面后恢复 Activity 原配置。
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val originalSoftInputMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {
            if (window != null && originalSoftInputMode != null) {
                window.setSoftInputMode(originalSoftInputMode)
            }
        }
    }

    val existingEntry = remember(journalId, date, journals) {
        if (!journalId.isNullOrBlank()) {
            journals.find { it.id == journalId }
        } else {
            journals.find { it.date == date }
        }
    }

    // 草稿状态管理
    var draftInitialized by rememberSaveable(journalId, date) { mutableStateOf(false) }
    var content by rememberSaveable(journalId, date, stateSaver = TextFieldValue.Saver) {
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
    var editorMode by rememberSaveable(journalId, date) {
        mutableStateOf(JournalEditorMode.Edit)
    }

    // 收藏态
    var isFavorite by rememberSaveable(journalId, date) { mutableStateOf(false) }
    var favoriteInitialized by rememberSaveable(journalId, date) { mutableStateOf(false) }
    LaunchedEffect(existingEntry?.id, existingEntry?.isFavorite) {
        val entry = existingEntry
        if (!favoriteInitialized && entry != null) {
            isFavorite = entry.isFavorite
            favoriteInitialized = true
        }
    }

    // 顶部信息栏偏好配置与元数据状态
    val journalHeaderPrefs = remember { JournalHeaderPreferences.getInstance(context) }
    val headerConfig by journalHeaderPrefs.configFlow.collectAsStateWithLifecycle()

    var moodScore by rememberSaveable(journalId, date) {
        mutableIntStateOf(existingEntry?.moodScore ?: 5)
    }
    var weather by rememberSaveable(journalId, date) {
        mutableStateOf(existingEntry?.tags?.find { it.startsWith("weather:") }?.removePrefix("weather:") ?: "")
    }
    val createdAt = remember(existingEntry?.id) {
        existingEntry?.createdAt ?: System.currentTimeMillis()
    }
    var metaInitialized by rememberSaveable(journalId, date) { mutableStateOf(false) }
    LaunchedEffect(existingEntry?.id) {
        val entry = existingEntry ?: return@LaunchedEffect
        if (!metaInitialized) {
            moodScore = entry.moodScore
            weather = entry.tags.find { it.startsWith("weather:") }?.removePrefix("weather:") ?: ""
            metaInitialized = true
        }
    }

    fun buildCurrentTags(): List<String> {
        val otherTags = (existingEntry?.tags ?: emptyList()).filterNot { it.startsWith("weather:") }
        return buildList {
            addAll(otherTags)
            if (weather.isNotBlank()) add("weather:$weather")
        }
    }

    fun saveJournal() {
        if (content.text.isBlank()) {
            Toast.makeText(context, "请写点今日内容吧", Toast.LENGTH_SHORT).show()
            return
        }
        val finalEntry = JournalEntry(
            id = existingEntry?.id ?: UUID.randomUUID().toString(),
            date = date,
            title = existingEntry?.title ?: "",
            content = content.text.trim(),
            moodScore = moodScore,
            energyScore = existingEntry?.energyScore ?: 4,
            studySatisfaction = existingEntry?.studySatisfaction ?: 5,
            tomorrowPlan = existingEntry?.tomorrowPlan ?: "",
            blockers = existingEntry?.blockers ?: "",
            tags = buildCurrentTags(),
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            isFavorite = isFavorite
        )
        viewModel.saveJournal(finalEntry)
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
        val draft = JournalEntry(
            id = existingEntry?.id ?: UUID.randomUUID().toString(),
            date = date,
            title = existingEntry?.title ?: "",
            content = trimmed,
            moodScore = moodScore,
            energyScore = existingEntry?.energyScore ?: 4,
            studySatisfaction = existingEntry?.studySatisfaction ?: 5,
            tomorrowPlan = existingEntry?.tomorrowPlan ?: "",
            blockers = existingEntry?.blockers ?: "",
            tags = buildCurrentTags(),
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            isFavorite = isFavorite
        )
        viewModel.saveJournal(draft)
        Toast.makeText(context, "已保存为草稿", Toast.LENGTH_SHORT).show()
        onBack()
    }

    fun requestBack() {
        val hasContent = content.text.isNotBlank()
        if (hasContent) {
            showBackConfirm = true
        } else {
            onBack()
        }
    }

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
    var measuredTopBarHeight by remember { mutableStateOf(104.dp) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ====================================================================
        // 核心展示区：根据模式展示「编辑输入框」或「排版预览」
        // ====================================================================
        when (editorMode) {
            JournalEditorMode.Edit -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = measuredTopBarHeight + 16.dp, bottom = EditorToolbarHeight)
                ) {
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag(JournalEditorTags.ContentInput),
                            textStyle = TextStyle(
                                fontSize = 17.sp,
                                lineHeight = 28.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = remember(colorScheme) { MarkdownSyntaxTransformation(colorScheme) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Default
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxSize()) {
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
                }
            }

            JournalEditorMode.Preview -> {
                val previewScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(top = measuredTopBarHeight + 12.dp, bottom = 24.dp)
                ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(previewScrollState)
                        ) {
                            JournalMarkdownPreview(
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

        // ---- 顶部固定区域：导航、模式切换、完成操作与紧凑元信息 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .zIndex(10f)
                .onGloballyPositioned { coordinates ->
                    measuredTopBarHeight = with(density) { coordinates.size.height.toDp() }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(YanjiSpacing.TopBarHeight)
                    .padding(horizontal = YanjiSpacing.PageHorizontalPadding),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterStart)
                        .testTag("detail_top_bar_back")
                        .clip(CircleShape)
                        .clickable(onClick = { requestBack() }),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shadowElevation = 0.dp
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
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(JournalEditorTags.ModeToggleButton)
                            .clip(CircleShape)
                            .clickable {
                                editorMode = when (editorMode) {
                                    JournalEditorMode.Edit -> JournalEditorMode.Preview
                                    JournalEditorMode.Preview -> JournalEditorMode.Edit
                                }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (editorMode == JournalEditorMode.Edit) {
                                    PhosphorIcons.Regular.Eye
                                } else {
                                    PhosphorIcons.Regular.PencilSimple
                                },
                                contentDescription = if (editorMode == JournalEditorMode.Edit) "预览" else "继续编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(JournalEditorTags.SaveButton)
                            .clip(CircleShape)
                            .clickable(onClick = { saveJournal() }),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "完成",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            JournalEditorHeaderBar(
                moodScore = moodScore,
                onMoodScoreChange = { moodScore = it },
                createdAt = createdAt,
                config = headerConfig,
                weather = weather,
                onWeatherChange = { weather = it },
                modifier = Modifier.padding(horizontal = YanjiSpacing.PageHorizontalPadding)
            )

        }

        // ---- 底部快捷格式栏：固定在页面底部；输入法弹出时由键盘覆盖，不随 IME 上浮 ----
        if (editorMode == JournalEditorMode.Edit) {
            JournalFormatToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
                    // 键盘出现时贴住 IME 上沿；隐藏时继续避让系统导航栏。
                    .imePadding()
                    .navigationBarsPadding(),
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

    if (showBackConfirm) {
        JournalDiscardOrDraftDialog(
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
private fun JournalDiscardOrDraftDialog(
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
private fun JournalFormatToolbar(
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
                            tag = JournalEditorTags.TaskButton,
                            active = isTask,
                            onClick = onTask
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.ListBullets,
                            activeIcon = PhosphorIcons.Bold.ListBullets,
                            label = "列表",
                            tag = JournalEditorTags.ListButton,
                            active = isBullet,
                            onClick = onList
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextB,
                            activeIcon = PhosphorIcons.Bold.TextB,
                            label = "加粗",
                            tag = JournalEditorTags.BoldButton,
                            active = isBold,
                            onClick = onBold
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextH,
                            activeIcon = PhosphorIcons.Bold.TextH,
                            label = "标题",
                            tag = JournalEditorTags.HeadingButton,
                            active = isHeading,
                            onClick = onHeading
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Quotes,
                            activeIcon = PhosphorIcons.Bold.Quotes,
                            label = "引用",
                            tag = JournalEditorTags.QuoteButton,
                            active = isQuote,
                            onClick = onQuote
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextItalic,
                            activeIcon = PhosphorIcons.Bold.TextItalic,
                            label = "斜体",
                            tag = JournalEditorTags.ItalicButton,
                            active = isItalic,
                            onClick = onItalic
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.ListNumbers,
                            activeIcon = PhosphorIcons.Bold.ListNumbers,
                            label = "编号",
                            tag = JournalEditorTags.NumberedListButton,
                            active = isNumbered,
                            onClick = onNumberedList
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextAUnderline,
                            activeIcon = PhosphorIcons.Bold.TextAUnderline,
                            label = "下划线",
                            tag = JournalEditorTags.UnderlineButton,
                            active = isUnderline,
                            onClick = onUnderline
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.TextStrikethrough,
                            activeIcon = PhosphorIcons.Bold.TextStrikethrough,
                            label = "删除线",
                            tag = JournalEditorTags.StrikeButton,
                            active = isStrike,
                            onClick = onStrike
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Code,
                            activeIcon = PhosphorIcons.Bold.Code,
                            label = "代码",
                            tag = JournalEditorTags.CodeButton,
                            active = isCode,
                            onClick = onCode
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Minus,
                            activeIcon = PhosphorIcons.Bold.Minus,
                            label = "分割线",
                            tag = JournalEditorTags.DividerButton,
                            active = isDivider,
                            onClick = onDivider
                        )
                        FormatButton(
                            icon = PhosphorIcons.Regular.Link,
                            activeIcon = PhosphorIcons.Bold.Link,
                            label = "链接",
                            tag = JournalEditorTags.LinkButton,
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
