package com.example.yanji.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.yanji.ui.components.YanjiPageHeader
import com.example.yanji.data.YanjiTime

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
    val journals = state.journals

    val todayStr = remember {
        YanjiTime.todayIso()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiColors.groupedBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = YanjiSpacing.PageTopGap, bottom = AppContentInsets.BottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(YanjiSpacing.CardGap)
        ) {
            item {
                YanjiPageHeader(
                    title = "考研日记",
                    subtitle = "记录真实思考与状态 · 自动关联每日学时",
                    trailing = if (journals.isNotEmpty()) {
                        {
                            Button(
                                onClick = { onNavigateToJournalEditor(null, todayStr) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(YanjiRadius.Pill),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("记今天", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else null
                )
            }

            if (journals.isEmpty()) {
                item {
                    JournalEmptyState(
                        onStartRecording = { onNavigateToJournalEditor(null, todayStr) }
                    )
                }
            } else {
                items(journals) { entry ->
                    JournalCard(
                        entry = entry,
                        onClick = { onNavigateToJournalEditor(entry.id, entry.date) },
                        onStudyDurationClick = { onNavigateToDailyDetail(entry.date) },
                        studyDurationSeconds = viewModel.dailySummaryFor(entry.date).totalDurationSeconds
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalEmptyState(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
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
                text = "当前还没有",
                style = YanjiTypography.title2,
                fontWeight = FontWeight.Bold,
                color = YanjiColors.primaryLabel,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "记录真实备考心境、反思与知识薄弱点\n每一篇日记都是通往上岸的坚实脚印",
                style = YanjiTypography.subheadline,
                color = YanjiColors.secondaryLabel,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartRecording,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(YanjiRadius.Pill),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "开始记录",
                    style = YanjiTypography.headline,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
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
            Text(
                text = entry.content,
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
