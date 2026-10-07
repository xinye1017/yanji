package com.example.yanji.ui.note

import com.example.yanji.ui.icons.RemixIcons
import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.example.yanji.ui.components.YanjiCardVariant
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.R
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
    const val SearchControl = "note_search_control"
}

@Composable
fun NoteScreen(
    modifier: Modifier = Modifier,
    onNavigateToNoteEditor: (noteId: String?, date: String) -> Unit = { _, _ -> },
    viewModel: NoteViewModel = yanjiViewModel { container ->
        NoteViewModel(container.repository)
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
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val shouldShowSearchBar by remember {
        derivedStateOf {
            isAtTop || isSearchFocused
        }
    }

    // 点空白 / 滚动列表时收起：先收滑开行，再退搜索。
    // 只清 query 与焦点，不依赖 isSearchFocused 状态——焦点真假以系统为准，
    // 避免「点击搜索框 → 同步聚焦 → 父容器同帧再把它清掉」的手势竞争。
    val cancelSearchAndClearFocus = {
        openRowId = null
        if (query.isNotEmpty()) query = ""
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // 返回键分层消费：先收起滑开的行，再退搜索（清词 + 收焦点 + 收键盘）。
    // 这层必须挂在 NoteScreen 顶层而不是搜索框内部 —— 原来的 BackHandler 只看
    // isFocused / query，滑开某行但没聚焦搜索时它处于禁用态，按返回会直接退出页面，
    // 把滑开的行留在原地。cancelSearchAndClearFocus 已含 openRowId = null，两层一次清完。
    BackHandler(enabled = openRowId != null || isSearchFocused || query.isNotEmpty()) {
        cancelSearchAndClearFocus()
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

    // 收藏没有二次确认，也没有撤销：横向滑错一次就静默改了状态，还没有任何反馈。
    // 删除本来就有二次确认对话框，收藏这条路径原本是整个页面唯一「点了就生效且无法回退」的操作。
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    /**
     * 切换收藏并给一条可撤销的提示。
     *
     * 撤销把该篇**切回本次切换之前**的状态（[entry] 是切换前的快照）。
     * 即使同一条随笔在提示消失前被再次切换，撤销也只是把它写回一个曾经真实存在过的值，
     * 不会破坏别的随笔，因此不需要额外的「是否为最新一次切换」判断。
     */
    fun toggleFavoriteWithUndo(entry: NoteEntry) {
        val next = !entry.isFavorite
        viewModel.setFavorite(entry.id, next)
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = if (next) "已收藏这篇随笔" else "已取消收藏",
                actionLabel = "撤销",
                withDismissAction = false,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.setFavorite(entry.id, entry.isFavorite)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- 列表：整幅通铺到屏幕边缘，向上滚动时穿透渐变页眉羽化消融 ----
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        cancelSearchAndClearFocus()
                    }
                },
            contentPadding = PaddingValues(
                top = 132.dp, // 顶端展开时让出页眉与搜索框的完整空间
                bottom = AppContentInsets.BottomBarPadding + 76.dp
            ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (filteredGroups.isEmpty()) {
                    item(key = "note-empty", contentType = "note-empty") {
                        // 空态 / 无结果态是「这一屏只有它」的状态，必须占满可视区并垂直居中。
                        // contentAlignment 不可省：漏掉它 Box 会顶对齐，空态就贴在页眉下方，
                        // 下面留出大半屏空白（1256×2760 上尤其明显），构图头重脚轻。
                        // 1.0 而不是 0.85：contentPadding 已经为底部栏与 FAB 让出空间，
                        // 真正的可视区就是「搜索框以下」的部分，取满即正中，不必再乘系数。
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillParentMaxHeight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.notes.isEmpty()) {
                                NoteEmptyState(
                                    onStartRecording = { onNavigateToNoteEditor(null, todayStr) }
                                )
                            } else {
                                NoteNoResultState(query = query)
                            }
                        }
                    }
                } else {
                    // 以日期划分：每天一个分组头，组内一天可以不限篇数。
                    // 每篇随笔都是一张圆角卡片，卡片本身就是边界：组与组、同日的
                    // 多篇之间都不再画分隔线，只保留页眉「随笔」标题下那条规则线。
                    filteredGroups.forEachIndexed { groupIndex, group ->
                        item(key = "header-${group.date}", contentType = "note-header") {
                            NoteDayHeader(
                                date = group.date,
                                // 第一组贴着搜索栏，之后各组的间距即「上一组末张卡片
                                // 与下一个日期」的呼吸：没有分隔线了，这个上间距就是分组线索。
                                topPadding = if (groupIndex == 0) {
                                    YanjiSpacing.ItemGap
                                } else {
                                    YanjiSpacing.CardGap
                                }
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
                                onToggleFavorite = { toggleFavoriteWithUndo(entry) },
                                onDelete = { pendingDelete = entry }
                            )
                        }
                    }
                }
            }

        // ---- 顶部页眉：从上到下透明度逐渐升高的渐变背景 + 居中标题 + 规则线 + 可折叠搜索框 ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(10f)
        ) {
            val bg = MaterialTheme.colorScheme.background
            // 渐变透明背景层：参考随笔编辑页 TopFadeScrim，从上到下透明度逐渐升高
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                bg.copy(alpha = 0.98f),
                                bg.copy(alpha = 0.94f),
                                bg.copy(alpha = 0.80f),
                                bg.copy(alpha = 0.45f),
                                bg.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 页眉内容：点击空白处收起滑开行与取消搜索
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp) // 给底部渐变留出自然羽化过渡区
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
                        .padding(horizontal = YanjiSpacing.PageHorizontalPadding)
                        // 声明为标题节点：读屏用户可以用「按标题跳转」在页面间移动，
                        // 否则这一屏没有任何可跳转的层级锚点。
                        .semantics { heading() }
                )

                // 页眉「随笔」与下方规则线：保持贴身，收紧垂直间距。
                // 规则线与下方随笔卡片取同一套页边距（20dp）—— 卡片化之后
                // 页眉若整幅通铺，这条线会伸出卡片左右边缘之外，页面就没有一条干净的左缘。
                Spacer(modifier = Modifier.height(8.dp))

                NoteRowDivider(
                    modifier = Modifier.padding(horizontal = YanjiSpacing.PageHorizontalPadding)
                )

                // 搜索框：向下滑动页面时自动隐藏，仅滑动到页面顶端时重新展开显示
                AnimatedVisibility(
                    visible = shouldShowSearchBar,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 200)
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 180)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NoteSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            isFocused = isSearchFocused,
                            onFocusChange = { isSearchFocused = it },
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .height(48.dp)
                        )
                    }
                }
            }
        }

        // ---- 右下角圆形悬浮加号：新建随笔（原「记今天」胶囊按钮迁移至此） ----
        // 呼吸区从 20dp 抬到 34dp：AppContentInsets.BottomBarPadding(112dp) 是给
        // **列表内容**留的底线，FAB 是浮层，只加 20dp 时它的下边沿离玻璃底栏
        // 顶边只剩 ~12dp，单手拇指扫过底栏时极易误触。34dp 把浮层与 dock 的
        // 净距拉到 ~26dp，够放一节指腹，也不再蹭系统返回手势。
        Surface(
            onClick = {
                openRowId = null
                onNavigateToNoteEditor(null, todayStr)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = AppContentInsets.BottomBarPadding + 34.dp)
                .size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = RemixIcons.AddLine,
                    contentDescription = "写随笔",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // ---- 收藏撤销提示 ----
        // 落在「列表底部留白」这条带子里（BottomBarPadding + 76dp，与 LazyColumn 的
        // contentPadding 同一个值）：刚好卡在 FAB 顶边之上，既不压住 FAB，也不遮住最后一行随笔。
        // 只给 BottomBarPadding 的话 snackbar 会和右下角 FAB 在 y 方向重叠。
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppContentInsets.BottomBarPadding + 76.dp)
        )    }

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

    val focusSearch = {
        focusRequester.requestFocus()
        showKeyboard()
    }

    // 触控槽 48dp（透明）：只负责承载点击。视觉胶囊只有 40dp，比触控槽矮一圈，
    // 列表里更轻薄，而点击区域仍然是完整的 48dp 最小目标。
    // testTag 必须留在这一层 —— 仪器化测试按 SearchControl >= 48dp 断言触控尺寸。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(NoteScreenTags.SearchControl)
            .clickable(
                // 已聚焦时不再拦截点击，把事件让给内层 BasicTextField 处理光标定位。
                enabled = !isFocused,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusSearch() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(YanjiRadius.Pill),
            // 取消描边后，识别度全部由「窄而长的形状 + 填充」承担。
            color = YanjiColors.inputFill,
            // 静止态完全不给边框：真机反馈实线太硬。聚焦时才给 1dp 主色描边作为交互反馈，
            // 那条线是「正在输入」的状态提示，不是控件的常态外观。
            border = if (isFocused) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 18.dp),
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
                    color = YanjiColors.textTertiary,
                    // 大字号下 7 个汉字放不进一行，必须显式收成单行省略：
                    // 不加 maxLines 时它会折行，把整条占位行撑破搜索栏高度。
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                            .size(48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onQueryChange("") },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = RemixIcons.CloseLine,
                            contentDescription = "清除",
                            tint = YanjiColors.textTertiary,
                            // 槽位扩到 48dp 后右贴边内缩 5dp，X 的落点与原来逐像素一致。
                            modifier = Modifier.padding(end = 5.dp).size(18.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * 无搜索结果时的提示。
 */
@Composable
private fun NoteNoResultState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YanjiSpacing.PageHorizontalPadding),
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

/**
 * 日期分组头：只留「9月21日」。
 *
 * 此前右侧还挂着一枚「当日学习时长」药丸并可点进当日详情。已按需求整条移除：
 * 时长属于统计域，随笔历史页只回答「写了什么、什么时候写的」，
 * 混进第二个指标会让分组头承担两套语义，也逼着每个日期多建一条统计 Flow。
 *
 * 高度不再写死。药丸连带它那格 48dp 触控槽消失后，原 52dp 里大半是空槽，
 * 日期与当日随笔之间多出一截空白，所以整体收紧。
 *
 * 间距拆成 [topPadding] 与固定 bottom 两段，而不是「固定高度 + 垂直居中」：
 * 居中会让日期上下各留一半，上下严重不对称。
 * 卡片化之后 bottom 固定取 [YanjiSpacing.SectionGap]（与全站「分组标题 → 卡片」同档），
 * 而 [topPadding] 由调用方按「是不是第一组」给 —— 没有分隔线之后，
 * 这个上间距就是跨日期分组的唯一线索。
 */
@Composable
private fun NoteDayHeader(date: String, topPadding: Dp) {
    Text(
        text = noteDayHeaderLabel(date),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = YanjiColors.primaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = YanjiSpacing.PageHorizontalPadding,
                end = YanjiSpacing.PageHorizontalPadding,
                top = topPadding,
                bottom = YanjiSpacing.SectionGap
            )
    )
}

/**
 * 分组头日期文案：把 ISO 日期渲染为「9月21日」。
 * 复用 [YanjiTime] 既有解析器，避免另造一套日期格式化。
 */
internal fun noteDayHeaderLabel(isoDate: String): String {
    val parsed = YanjiTime.parseIsoDate(isoDate) ?: return isoDate
    return "${parsed.monthValue}月${parsed.dayOfMonth}日"
}

/**
 * 空态：一张分组卡片承载「还没有随笔 + 写随笔」。
 *
 * 容器走 [Card]（[YanjiCardVariant.Grouped]）而不是手写 Surface：容器色即
 * `colorScheme.surface`，与列表里的每篇随笔、与全站其它卡片是同一个底色。
 * 此前用 `YanjiColors.elevatedSurface` 手写同值容器，两处必须各自跟着主题走。
 */
@Composable
private fun NoteEmptyState(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YanjiSpacing.PageHorizontalPadding),
        variant = YanjiCardVariant.Grouped
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
                // 与右下角 FAB 的 contentDescription 统一为「写随笔」：
                // 两条入口做的是同一件事（新建一篇），不能一个叫「记今天」一个叫「开始记录」。
                text = "写随笔",
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
