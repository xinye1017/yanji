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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun JournalScreen(
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToJournalEditor: (journalId: String?, date: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    repo: YanjiRepository = YanjiRepository.getInstance(),
    statsRepo: StudyStatisticsRepository = StudyStatisticsRepository.getInstance()
) {
    val journals by repo.journalEntries.collectAsStateWithLifecycle()

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "考研日记",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "记录真实思考与状态 · 自动关联每日学时",
                            style = MaterialTheme.typography.bodyMedium,
                            color = YanjiTextSecondary
                        )
                    }

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
            }

            items(journals) { entry ->
                JournalCard(
                    entry = entry,
                    onClick = { onNavigateToJournalEditor(entry.id, entry.date) },
                    onStudyDurationClick = { onNavigateToDailyDetail(entry.date) },
                    statsRepo = statsRepo
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
    statsRepo: StudyStatisticsRepository
) {
    // 单一事实来源：该日期真实的学习时长聚合（FocusSession + ExamSession）。
    // 不再回退到日记自身记录的时长副本。
    val dailySummary = statsRepo.getDailyStudySummary(entry.date)
    val durationSecs = dailySummary.totalDurationSeconds

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
                        fontSize = 14.sp,
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
                            fontSize = 12.sp,
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Content snippet
            Text(
                text = entry.content,
                fontSize = 13.sp,
                color = YanjiTextSecondary,
                maxLines = 3,
                lineHeight = 20.sp
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiLavender
                        )
                        Text(
                            text = entry.tomorrowPlan,
                            fontSize = 12.sp,
                            color = YanjiTextPrimary,
                            maxLines = 2,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
