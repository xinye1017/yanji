package com.example.yanji.ui.journal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun JournalEditorScreen(
    journalId: String?,
    date: String,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel {
        JournalViewModel(YanjiRepository.getInstance(), StudyStatisticsRepository.getInstance())
    }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val journals = state.journals
    val existingEntry = remember(journalId, date, journals) {
        if (!journalId.isNullOrBlank()) {
            journals.find { it.id == journalId }
        } else {
            journals.find { it.date == date }
        }
    }

    // Single source of truth for daily study duration
    val studyDuration = viewModel.dailySummaryFor(date).totalDurationSeconds

    var title by remember(existingEntry) { mutableStateOf(existingEntry?.title ?: "") }
    var content by remember(existingEntry) { mutableStateOf(existingEntry?.content ?: "") }
    var moodScore by remember(existingEntry) { mutableIntStateOf(existingEntry?.moodScore ?: 5) }
    var energyScore by remember(existingEntry) { mutableIntStateOf(existingEntry?.energyScore ?: 4) }
    var satisfactionScore by remember(existingEntry) { mutableIntStateOf(existingEntry?.studySatisfaction ?: 5) }
    var tomorrowPlan by remember(existingEntry) { mutableStateOf(existingEntry?.tomorrowPlan ?: "") }

    val formattedHeaderDate = remember(date) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val d = parser.parse(date) ?: Date()
            SimpleDateFormat("M 月 d 日", Locale.CHINESE).format(d)
        } catch (e: Exception) {
            date
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiBackground)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = YanjiTextPrimary
                    )
                }
                Text(
                    text = "${formattedHeaderDate}日记",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary
                )
            }

            Button(
                onClick = {
                    if (content.isBlank() && title.isBlank()) {
                        Toast.makeText(context, "请写点今日内容吧", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val finalEntry = JournalEntry(
                        id = existingEntry?.id ?: UUID.randomUUID().toString(),
                        date = date,
                        title = title.trim(),
                        content = content.trim(),
                        moodScore = moodScore,
                        energyScore = energyScore,
                        studySatisfaction = satisfactionScore,
                        tomorrowPlan = tomorrowPlan.trim(),
                        tags = existingEntry?.tags ?: emptyList()
                    )
                    viewModel.saveJournal(finalEntry)
                    Toast.makeText(context, "日记已保存", Toast.LENGTH_SHORT).show()
                    onSaveSuccess()
                },
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("保存", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(YanjiSpacing.CardGap)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Study Duration Banner (Read-only single source of truth)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日累计有效学习",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = DurationFormatter.formatHoursMinutes(studyDuration),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiPrimary
                    )
                }
            }

            // Status Ratings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "今日状态打分",
                        style = MaterialTheme.typography.titleMedium,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))

                    RatingBarRow(
                        label = "整体心态",
                        score = moodScore,
                        onScoreChanged = { moodScore = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    RatingBarRow(
                        label = "精力充沛度",
                        score = energyScore,
                        onScoreChanged = { energyScore = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    RatingBarRow(
                        label = "复习满意度",
                        score = satisfactionScore,
                        onScoreChanged = { satisfactionScore = it }
                    )
                }
            }

            // Title Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "今日标题",
                        style = MaterialTheme.typography.titleMedium,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("例如：渐入佳境：攻克多元微分与树算法", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Content Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "今日状态与复盘总结",
                        style = MaterialTheme.typography.titleMedium,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = {
                            Text(
                                "记录今天各科目的复习感受、卡点攻克过程、心态变化...",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 19.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 6
                    )
                }
            }

            // Tomorrow Plan Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "明日核心目标 (可选)",
                        style = MaterialTheme.typography.titleMedium,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))
                    OutlinedTextField(
                        value = tomorrowPlan,
                        onValueChange = { tomorrowPlan = it },
                        placeholder = {
                            Text(
                                "1. 上午完成线性代数二次型标准形复习\n2. 下午攻克408图的最短路径算法",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))
        }
    }
}

@Composable
private fun RatingBarRow(
    label: String,
    score: Int,
    onScoreChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = YanjiTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..5) {
                Icon(
                    imageVector = if (i <= score) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "$i 星",
                    tint = if (i <= score) YanjiWarning else YanjiTextTertiary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onScoreChanged(i) }
                )
            }
        }
    }
}
