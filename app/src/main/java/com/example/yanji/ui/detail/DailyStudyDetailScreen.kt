package com.example.yanji.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.*
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar

@Composable
fun DailyStudyDetailScreen(
    date: String,
    onBack: () -> Unit,
    onNavigateToSubjectDetail: (subjectId: String) -> Unit,
    onNavigateToFocusDetail: (sessionId: String) -> Unit,
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateToStartFocus: () -> Unit,
    modifier: Modifier = Modifier,
    statsRepo: StudyStatisticsRepository = StudyStatisticsRepository.getInstance()
) {
    val summary by statsRepo.getDailyStudySummaryFlow(date).collectAsStateWithLifecycle(
        initialValue = statsRepo.getDailyStudySummary(date)
    )

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = YanjiTextPrimary
                )
            }
            Text(
                text = DurationFormatter.formatDateWithWeekday(date),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "学习总览",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = YanjiTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = DurationFormatter.formatHoursMinutes(summary.totalDurationSeconds),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "共 ${summary.focusCount} 次专注" + if (summary.examCount > 0) " · ${summary.examCount} 次模考" else "",
                                fontSize = 13.sp,
                                color = YanjiTextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (summary.subjectDistribution.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "科目投入分布",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = YanjiTextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                summary.subjectDistribution.forEach { (subName, secs) ->
                                    val chipColor = when {
                                        subName.contains("数学") || subName.contains("线性代数") || subName.contains("概率论") -> SubjectMath
                                        subName.contains("408") || subName.contains("专业课") ||
                                            subName.contains("数据结构") || subName.contains("计算机组成") ||
                                            subName.contains("计算机网络") || subName.contains("操作系统") -> SubjectMajor
                                        subName.contains("英语") -> SubjectEnglish
                                        subName.contains("政治") -> SubjectPolitics
                                        else -> SubjectOther
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(chipColor.copy(alpha = 0.08f))
                                            .clickable { onNavigateToSubjectDetail(subName) }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(chipColor)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = subName,
                                            fontSize = 12.sp,
                                            color = YanjiTextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = DurationFormatter.formatHoursMinutes(secs),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = YanjiTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Session List Header
            item {
                Text(
                    text = "学习轨迹明细",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary
                )
            }

            if (summary.sessions.isEmpty()) {
                // Empty state
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            JuanjuanAvatar(size = 56.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "今天还没有留下学习轨迹。",
                                fontSize = 14.sp,
                                color = YanjiTextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToStartFocus,
                                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("开始专注", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(summary.sessions) { item ->
                    DailySessionRowCard(
                        item = item,
                        onClick = {
                            if (item.isExam) {
                                onNavigateToExamDetail(item.id)
                            } else {
                                onNavigateToFocusDetail(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DailySessionRowCard(
    item: DailySessionItem,
    onClick: () -> Unit
) {
    val tagColor = Color(android.graphics.Color.parseColor(item.subjectColor))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tagColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DurationFormatter.formatTimeRange(item.startTime, item.endTime),
                        fontSize = 12.sp,
                        color = YanjiTextTertiary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(tagColor.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.isExam) "模拟考试" else item.subjectName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tagColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YanjiTextPrimary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DurationFormatter.formatHoursMinutes(item.durationSeconds),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiPrimary
                )
                if (item.isExam && item.score != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.score.toInt()} 分",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiSuccess
                    )
                } else if (item.pauseCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "暂停 ${item.pauseCount} 次",
                        fontSize = 12.sp,
                        color = YanjiTextTertiary
                    )
                }
            }
        }
    }
}
