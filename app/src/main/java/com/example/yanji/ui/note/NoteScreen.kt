package com.example.yanji.ui.note

import com.example.yanji.ui.icons.RemixIcons
import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.YanjiCard as Card
import com.example.yanji.ui.components.YanjiPrimaryButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.R
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.NoteEntry
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.data.YanjiTime
import kotlin.math.roundToInt

/** UI 测试定位锚点（与插桩/单测共享，避免断言依赖中文文案）。 */
object NoteScreenTags {
    const val SearchInput = "note_search_input"
}

@Composable
fun NoteScreen(
    modifier: Modifier = Modifier,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToNoteEditor: (noteId: String?, date: String) -> Unit = { _, _ -> },
    viewModel: NoteViewModel = yanjiViewModel { container ->
        NoteViewModel(container.repository, container.statisticsRepository)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val todayStr = remember {
        YanjiTime.todayIso()
    }

    // 待删除确认的目标：非 null 时弹出二次确认对话框。
    var pendingDelete by remember { mutableStateOf<NoteEntry?>(null) }

    // 搜索：关键词（标题/正文/标签）或日期（如「9月21日」「09-21」「2026-09-21」）。
    var query by rememberSaveable { mutableStateOf("") }
    // 小写索引只随笔记集合变化重建一次，避免每个按键重抄一遍全部正文。
    val searchIndex = remember(state.notes) { searchableNotes(state.notes) }
    val filteredGroups = remember(searchIndex, query) {
        filterSearchable(searchIndex, query)
            .let { NoteViewModel.groupsOf(it).groups }
    }

    // 当前处于滑开状态的行 id：全局唯一。拖动新行会抢占它，滚动 / 点空白处会收起它。
    var openRowId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    // 点空白 / 滚动列表时收起：先收滑开行，再退搜索。
    // 只清 query 与焦点，不依赖 isSearchFocused 状态——焦点真假以系统为准，
    // 避免「点击搜索框 → 同步聚焦 → 父容器同帧再把它清掉」的手势竞争。
    val cancelSearchAndClearFocus = {
        openRowId = null
        if (query.isNotEmpty()) query = ""
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // 列表滚动就收起滑开行与软键盘（保留已输入查询，方便滚动浏览结果）。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    openRowId = null
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }
    }
    // 搜索词变化后列表结构改变，顺手收起，避免滑开行错位到别的日期分组。
    LaunchedEffect(query) { openRowId = null }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ---- 标题栏：点击标题或上方留白空白处，收起滑开行与取消搜索 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            cancelSearchAndClearFocus()
                        }
                    }
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "随笔",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = YanjiColors.primaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                // 页眉「随笔」与下方分隔线：保持贴身，收紧垂直间距。
                Spacer(modifier = Modifier.height(8.dp))

                // ---- 标题栏下的分隔线 ----
                NoteRowDivider()
            }

            // ---- 搜索框：支持关键词或日期 ----
            // 搜索框的点击在 NoteSearchBar 内部同步 requestFocus()，不靠外层状态驱动；
            // 因此上面标题栏 / 下面列表的「点空白收起」不会把它刚拿到的焦点清掉。
            NoteSearchBar(
                query = query,
                onQueryChange = { query = it },
                isFocused = isSearchFocused,
                onFocusChange = { isSearchFocused = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )

            // ---- 列表：整幅通铺到屏幕边缘，滑块从屏幕边缘滑出；点击列表空白处收起滑开行与取消搜索 ----
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            cancelSearchAndClearFocus()
                        }
                    },
                contentPadding = PaddingValues(bottom = AppContentInsets.BottomBarPadding + 76.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (filteredGroups.isEmpty()) {
                    item {
                        if (state.notes.isEmpty()) {
                            NoteEmptyState(
                                onStartRecording = { onNavigateToNoteEditor(null, todayStr) }
                            )
                        } else {
                            NoteNoResultState(query = query)
                        }
                    }
                } else {
                    // 以日期划分：每天一个分组头，组内一天可以不限篇数。
                    // 非卡片式列表：仅在跨日期（不同分组）前画分隔线，
                    // 同一天的多篇随笔之间、日期头与当日随笔之间均不再画线。
                    filteredGroups.forEachIndexed { groupIndex, group ->
                        if (groupIndex > 0) {
                            item(key = "divider-${group.date}", contentType = "note-divider") {
                                NoteRowDivider()
                            }
                        }
                        item(key = "header-${group.date}", contentType = "note-header") {
                            NoteDayHeaderItem(
                                viewModel = viewModel,
                                date = group.date,
                                onStudyDurationClick = { onNavigateToDailyDetail(group.date) }
                            )
                        }
                        items(
                            count = group.entries.size,
                            key = { index -> group.entries[index].id },
                            contentType = { "note-row" }
                        ) { index ->
                            val entry = group.entries[index]
                            NoteSwipeableRow(
                                entry = entry,
                                isOpen = openRowId == entry.id,
                                onOpenChange = { open ->
                                    openRowId = if (open) entry.id else null
                                },
                                onClick = {
                                    // 有菜单滑开时，点击任何行都只负责收起（微信 / SwipeDelMenuLayout 习惯）。
                                    if (openRowId != null) openRowId = null
                                    else onNavigateToNoteEditor(entry.id, entry.date)
                                },
                                onToggleFavorite = { viewModel.setFavorite(entry.id, !entry.isFavorite) },
                                onDelete = { pendingDelete = entry }
                            )
                        }
                    }
                }
            }
        }

        // ---- 右下角圆形悬浮加号：新建随笔（原「记今天」胶囊按钮迁移至此） ----
        Surface(
            onClick = {
                openRowId = null
                onNavigateToNoteEditor(null, todayStr)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = AppContentInsets.BottomBarPadding + 20.dp)
                .size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = RemixIcons.AddLine,
                    contentDescription = "记今天",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    pendingDelete?.let { target ->
        NoteDeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteNote(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/**
 * 随笔搜索。空查询返回全部；否则按**关键词**（标题/正文/标签，忽略大小写）或
 * **日期**（匹配 `2026-09-21`、`09-21`、`9月21日`、`9/21` 等写法）过滤。
 * 纯函数，便于单测。
 */
/**
 * 搜索用的预小写视图：每篇笔记持有一份「标题 + 正文纯文本 + 标签」的小写副本。
 *
 * 正文先经 [noteListSnippet] 剥掉 markdown 标记，与列表展示看到的是同一份文本：
 * 否则搜「数学」会命中 `**数学**` 的星号、搜星号也能命中正文，检索语义与展示语义相反。
 * 副本只在笔记集合变化时重建，避免搜索框每个按键都重抄一遍全部正文。
 */
internal data class SearchableNote(
    val entry: NoteEntry,
    val lowerTitle: String,
    val lowerContent: String,
    val lowerTags: List<String>
)

internal fun searchableNotes(entries: List<NoteEntry>): List<SearchableNote> =
    entries.map {
        SearchableNote(
            entry = it,
            lowerTitle = it.title.lowercase(),
            lowerContent = noteListSnippet(it.content).lowercase(),
            lowerTags = it.tags.map { tag -> tag.lowercase() }
        )
    }

internal fun filterNotes(entries: List<NoteEntry>, query: String): List<NoteEntry> =
    filterSearchable(searchableNotes(entries), query)

internal fun filterSearchable(index: List<SearchableNote>, query: String): List<NoteEntry> {
    val q = query.trim()
    if (q.isEmpty()) return index.map { it.entry }

    val lower = q.lowercase()
    // 抽出查询里所有数字：既支持「2026-09-21」全写，也支持「9月21日」「09-21」「9/21」。
    val digits = q.filter { it.isDigit() }

    return index.filter { note ->
        val entry = note.entry
        val inText = note.lowerTitle.contains(lower) ||
            note.lowerContent.contains(lower) ||
            note.lowerTags.any { it.contains(lower) }

        // 日期匹配：把日期压成 yyyyMMdd，再拿查询里的数字串去比。
        val iso = entry.date.replace("-", "") // yyyyMMdd
        val monthDay = iso.drop(4) // MMdd
        val inDate = q.let { entry.date.contains(it) } ||
            (digits.isNotEmpty() && (
                iso.contains(digits) ||
                    monthDay == digits.padStart(4, '0') ||
                    monthDay.trimStart('0') == digits.trimStart('0')
                ))

        inText || inDate
    }.map { it.entry }
}

/** 搜索框：大圆角、浅底。未聚焦且无输入时图标与占位词居中；聚焦后图标移至左侧、光标在左侧且去掉占位词。 */
@Composable
private fun NoteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val showCenteredPlaceholder = query.isEmpty() && !isFocused

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

    // 聚焦过渡进度：0 = 居中占位态，1 = 左侧输入态。
    // 放大镜图标据此做水平平移（居中图标向左淡出、左侧图标自右滑入），
    // 让两种状态之间的切换是连续滑动而不是瞬间跳变。
    val focusProgress by animateFloatAsState(
        targetValue = if (showCenteredPlaceholder) 0f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "searchIconSlide"
    )
    // 居中占位行常驻挂载，只用 alpha 控制可见性。
    // 不用 `if (showCenteredPlaceholder) { ... }` 之类的条件挂载：聚焦那一帧 isFocused
    // 已翻转、而 animateFloatAsState 还停在旧值，条件挂载会出现「先卸载再挂载」的一帧，
    // 表现为占位词闪一下。常驻 + alpha 从 1f 平滑过渡到 0f，没有挂载/卸载缝，绝不会闪。
    val placeholderAlpha = 1f - focusProgress
    // 居中图标向左滑出的距离（仅图标本身，不牵动占位文字）。
    val centeredIconShift = with(LocalDensity.current) { (-14.dp * focusProgress).toPx() }
    // 左侧图标自右滑入的距离：未聚焦时右移 14dp，聚焦后归位到 0。
    val leadingIconShift = with(LocalDensity.current) { (14.dp * (1f - focusProgress)).toPx() }

    BackHandler(enabled = isFocused || query.isNotEmpty()) {
        onQueryChange("")
        onFocusChange(false)
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val focusSearch = {
        focusRequester.requestFocus()
        showKeyboard()
    }

    val isDark = yanjiIsDarkTheme()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                // 已聚焦时不再拦截点击，把事件让给内层 BasicTextField 处理光标定位。
                enabled = !isFocused,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusSearch() },
        shape = RoundedCornerShape(YanjiRadius.Pill),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isFocused) 1.dp else 0.8.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else if (isDark) {
                MaterialTheme.colorScheme.outline
            } else {
                YanjiColors.opaqueSeparator
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // 未聚焦且无输入：图标与占位词居中展示（常驻，仅淡出）。
            // 聚焦过渡时整行淡出，其中放大镜额外向左滑出（与左侧图标滑入方向连成一体）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(placeholderAlpha),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = RemixIcons.SearchLine,
                    contentDescription = null,
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier
                        .offset { IntOffset(centeredIconShift.roundToInt(), 0) }
                        .size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "搜索关键词或日期",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiColors.textTertiary
                )
            }

            // 聚焦或有输入：图标在左侧，光标在左侧，从左往右输入。
            // 放大镜自右侧滑入；只给图标加偏移，输入框位置保持稳定以免光标跟着抖。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(focusProgress),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = RemixIcons.SearchLine,
                    contentDescription = null,
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier
                        .offset { IntOffset(leadingIconShift.roundToInt(), 0) }
                        .size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            onFocusChange(state.isFocused)
                            if (state.isFocused) {
                                showKeyboard()
                            }
                        }
                        .testTag(NoteScreenTags.SearchInput)
                )

                if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = RemixIcons.CloseLine,
                            contentDescription = "清除",
                            tint = YanjiColors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 无搜索结果时的提示。 */
@Composable
private fun NoteNoResultState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = RemixIcons.SearchLine,
            contentDescription = null,
            tint = YanjiColors.textTertiary,
            modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "没有找到「$query」相关的随笔",
            style = MaterialTheme.typography.bodyMedium,
            color = YanjiColors.secondaryLabel,
            textAlign = TextAlign.Center
        )
    }
}

/** 日期分组头：左侧「9月21日」，右侧该日真实学习时长 chip。 */
/**
 * 日期头：学时从 [NoteViewModel.dailySummaryFlow] 取，避免在组合过程中做全量会话扫描。
 */
@Composable
private fun NoteDayHeaderItem(
    viewModel: NoteViewModel,
    date: String,
    onStudyDurationClick: () -> Unit
) {
    val summary by viewModel.dailySummaryFlow(date).collectAsStateWithLifecycle()
    NoteDayHeader(
        date = date,
        studyDurationSeconds = summary.totalDurationSeconds,
        onStudyDurationClick = onStudyDurationClick
    )
}

@Composable
private fun NoteDayHeader(
    date: String,
    studyDurationSeconds: Long,
    onStudyDurationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = noteDayHeaderLabel(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = YanjiColors.primaryLabel
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(YanjiRadius.Small))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onStudyDurationClick)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = DurationFormatter.formatHoursMinutes(studyDurationSeconds),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 分组头日期文案：把 ISO 日期渲染为「9月21日」。
 * 复用 [YanjiTime] 既有解析器，避免另造一套日期格式化。
 */
internal fun noteDayHeaderLabel(isoDate: String): String {
    val parsed = YanjiTime.parseIsoDate(isoDate) ?: return isoDate
    return "${parsed.monthValue}月${parsed.dayOfMonth}日"
}

@Composable
private fun NoteEmptyState(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
        border = BorderStroke(0.8.dp, YanjiColors.separator),
        color = YanjiColors.elevatedSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(YanjiRadius.RowRadius))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = RemixIcons.DraftLine,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "当前还没有研途随笔",
                style = YanjiTypography.title2,
                fontWeight = FontWeight.Bold,
                color = YanjiColors.primaryLabel,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "记录真实备考心境、反思与知识薄弱点\n每一篇随笔都是通往上岸的坚实脚印",
                style = YanjiTypography.subheadline,
                color = YanjiColors.secondaryLabel,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            YanjiPrimaryButton(
                text = "开始记录",
                onClick = onStartRecording,
                icon = {
                    Icon(
                        imageVector = RemixIcons.AddLine,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
            )
        }
    }
}
