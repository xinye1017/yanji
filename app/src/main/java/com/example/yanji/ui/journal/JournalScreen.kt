package com.example.yanji.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.YanjiPageHeader
import com.example.yanji.data.YanjiTime

@Composable
fun JournalScreen(
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToJournalEditor: (journalId: String?, date: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel {
        JournalViewModel(YanjiRepository.getInstance(), StudyStatisticsRepository.getInstance())
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
            .background(YanjiBackground)
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
                    trailing = {
                        Button(
                            onClick = { onNavigateToJournalEditor(null, todayStr) },
                            colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("记今天", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
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
                        color = YanjiPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Clickable study duration chip linking to DailyStudyDetail
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiPrimarySoft)
                            .clickable { onStudyDurationClick() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = DurationFormatter.formatHoursMinutes(durationSecs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiPrimaryStrong
                        )
                    }
                }

                // Star rating
                Row {
                    repeat(entry.moodScore) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = YanjiWarning,
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
                color = YanjiTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Content snippet
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                color = YanjiTextSecondary,
                maxLines = 3
            )

            if (entry.tomorrowPlan.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(YanjiSurfaceSoft)
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "明日计划: ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = YanjiLavender
                        )
                        Text(
                            text = entry.tomorrowPlan,
                            style = MaterialTheme.typography.labelMedium,
                            color = YanjiTextPrimary,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
