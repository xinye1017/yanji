package com.example.yanji.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.JournalEntry
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.data.YanjiTime

/** UI 测试定位锚点（与插桩/单测共享，避免断言依赖中文文案）。 */
object JournalScreenTags {
    const val SearchInput = "journal_search_input"
}

@Composable
fun JournalScreen(
    modifier: Modifier = Modifier,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToJournalEditor: (journalId: String?, date: String) -> Unit = { _, _ -> },
    viewModel: JournalViewModel = yanjiViewModel { container ->
        JournalViewModel(container.repository, container.statisticsRepository)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val todayStr = remember {
        YanjiTime.todayIso()
    }

    // 待删除确认的目标：非 null 时弹出二次确认对话框。
    var pendingDelete by remember { mutableStateOf<JournalEntry?>(null) }

    // 搜索：关键词（标题/正文/标签）或日期（如「9月21日」「09-21」「2026-09-21」）。
    var query by rememberSaveable { mutableStateOf("") }
    val filteredGroups = remember(state.journals, query) {
        filterJournals(state.journals, query)
            .let { JournalViewModel.groupsOf(it).groups }
    }

    // 当前处于滑开状态的行 id：全局唯一。拖动新行会抢占它，滚动 / 点空白处会收起它。
    var openRowId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // 列表一开始滚动就收起滑开行（对齐 SwipeDelMenuLayout 在 RecyclerView 中的行为）。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) openRowId = null }
    }
    // 搜索词变化后列表结构改变，顺手收起，避免滑开行错位到别的日期分组。
    LaunchedEffect(query) { openRowId = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 有行滑开时，点击页面空白区域（标题、搜索框、分隔线等）先收起。
            .pointerInput(openRowId != null) {
                if (openRowId != null) {
                    detectTapGestures { openRowId = null }
                }
            }
    ) {
        // ---- 标题栏：历史页信息密度更高，缩短顶部留白，避免页眉离状态栏过远 ----
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "随笔",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = YanjiColors.primaryLabel,
                modifier = Modifier.align(Alignment.Center)
            )
            // 「记今天」胶囊按钮
            Surface(
                onClick = {
                    openRowId = null
                    onNavigateToJournalEditor(null, todayStr)
                },
                modifier = Modifier.align(Alignment.CenterEnd),
                shape = RoundedCornerShape(YanjiRadius.Pill),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "记今天",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---- 标题栏下的分隔线 ----
        JournalRowDivider()

        // ---- 搜索框：支持关键词或日期 ----
        JournalSearchBar(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        // ---- 列表：整幅通铺到屏幕边缘，滑块从屏幕边缘滑出；行内容自行保留 20dp 内边距 ----
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AppContentInsets.BottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (filteredGroups.isEmpty()) {
                item {
                    if (state.journals.isEmpty()) {
                        JournalEmptyState(
                            onStartRecording = { onNavigateToJournalEditor(null, todayStr) }
                        )
                    } else {
                        JournalNoResultState(query = query)
                    }
                }
            } else {
                // 以日期划分：每天一个分组头，组内一天可以不限篇数。
                // 条目之间以分隔线区分（非卡片式）。
                filteredGroups.forEach { group ->
                    item(key = "header-${group.date}") {
                        JournalDayHeader(
                            date = group.date,
                            studyDurationSeconds = viewModel.dailySummaryFor(group.date).totalDurationSeconds,
                            onStudyDurationClick = { onNavigateToDailyDetail(group.date) }
                        )
                    }
                    items(
                        count = group.entries.size,
                        key = { index -> group.entries[index].id }
                    ) { index ->
                        val entry = group.entries[index]
                        Column {
                            JournalRowDivider()
                            JournalSwipeableRow(
                                entry = entry,
                                isOpen = openRowId == entry.id,
                                onOpenChange = { open ->
                                    openRowId = if (open) entry.id else null
                                },
                                onClick = {
                                    // 有菜单滑开时，点击任何行都只负责收起（微信 / SwipeDelMenuLayout 习惯）。
                                    if (openRowId != null) openRowId = null
                                    else onNavigateToJournalEditor(entry.id, entry.date)
                                },
                                onToggleFavorite = { viewModel.setFavorite(entry.id, !entry.isFavorite) },
                                onDelete = { pendingDelete = entry }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        JournalDeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteJournal(target.id)
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
internal fun filterJournals(entries: List<JournalEntry>, query: String): List<JournalEntry> {
    val q = query.trim()
    if (q.isEmpty()) return entries

    val lower = q.lowercase()
    // 抽出查询里所有数字：既支持「2026-09-21」全写，也支持「9月21日」「09-21」「9/21」。
    val digits = q.filter { it.isDigit() }

    return entries.filter { entry ->
        val inText = entry.title.lowercase().contains(lower) ||
            entry.content.lowercase().contains(lower) ||
            entry.tags.any { it.lowercase().contains(lower) }

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
    }
}

/** 搜索框：大圆角、浅底、放大镜前缀。 */
@Composable
private fun JournalSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YanjiRadius.Pill),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = YanjiColors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    // 占位词居中显示
                    Text(
                        text = "搜索关键词或日期",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiColors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(JournalScreenTags.SearchInput)
                )
            }
            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清除",
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

/** 无搜索结果时的提示。 */
@Composable
private fun JournalNoResultState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Search,
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
@Composable
private fun JournalDayHeader(
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
            text = journalDayHeaderLabel(date),
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
internal fun journalDayHeaderLabel(isoDate: String): String {
    val parsed = YanjiTime.parseIsoDate(isoDate) ?: return isoDate
    return "${parsed.monthValue}月${parsed.dayOfMonth}日"
}

@Composable
private fun JournalEmptyState(
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
                    imageVector = Icons.Default.EditNote,
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
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun JournalCard(
    entry: JournalEntry,
    onClick: () -> Unit,
    onStudyDurationClick: () -> Unit,
    studyDurationSeconds: Long
) {
    // 单一事实来源：该日期真实的学习时长聚合（FocusSession + ExamSession），
    // 由 ViewModel 计算后以纯值传入，本组件不再持有仓库依赖。
    val durationSecs = studyDurationSeconds

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(YanjiRadius.GroupedCardRadius),
        border = BorderStroke(0.8.dp, YanjiColors.separator),
        colors = CardDefaults.cardColors(containerColor = YanjiColors.elevatedSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Clickable study duration chip linking to DailyStudyDetail
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(YanjiRadius.Small))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onStudyDurationClick() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = DurationFormatter.formatHoursMinutes(durationSecs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Star rating
                Row {
                    repeat(entry.moodScore) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = YanjiColors.warning,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = entry.title.ifEmpty { "学习随记与复盘" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Content snippet
            // 摘要走 stripJournalMarkup：正文里 `**` / `_` 是样式标记，不该在卡片上露出来。
            Text(
                text = stripJournalMarkup(entry.content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )

            if (entry.tomorrowPlan.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(YanjiRadius.Small))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "明日计划: ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = entry.tomorrowPlan,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
