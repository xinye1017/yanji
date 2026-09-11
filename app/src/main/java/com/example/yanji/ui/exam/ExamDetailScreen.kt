package com.example.yanji.ui.exam

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*
import com.example.yanji.ui.detail.DetailRowItem

@Composable
fun ExamDetailScreen(
    examId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = viewModel { ExamViewModel(YanjiRepository.getInstance()) }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exam = state.examSessions.find { it.id == examId }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (exam == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(YanjiBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("模考记录已删除或不存在", color = YanjiTextSecondary)
        }
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    val subjectColor = when {
        exam.subjectName.contains("数学") || exam.subjectId.startsWith("math") -> SubjectMath
        exam.subjectName.contains("408") || exam.subjectId.startsWith("major") -> SubjectMajor
        exam.subjectName.contains("英语") || exam.subjectId == "english" -> SubjectEnglish
        exam.subjectName.contains("政治") || exam.subjectId == "politics" -> SubjectPolitics
        else -> SubjectOther
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
                text = "模考复盘详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Main Overview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(subjectColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = exam.subjectName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSuccessSoft)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "全真模拟",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = YanjiSuccess
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        if (exam.score != null) {
                            Text(
                                text = "${exam.score.toInt()}",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiSuccess
                            )
                            Text(
                                text = " / ${exam.maxScore?.toInt() ?: 150} 分",
                                fontSize = 16.sp,
                                color = YanjiTextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        } else {
                            Text(
                                text = "未录入分数",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${DurationFormatter.formatDateChinese(exam.startTime)} · 用时 ${DurationFormatter.formatHoursMinutes(exam.actualDurationSeconds)}",
                        fontSize = 13.sp,
                        color = YanjiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Details Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DetailRowItem(label = "考试日期", value = DurationFormatter.formatDateChinese(exam.startTime))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = YanjiBorderSoft)
                    DetailRowItem(
                        label = "考试起止",
                        value = DurationFormatter.formatTimeRange(exam.startTime, exam.endTime)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = YanjiBorderSoft)
                    DetailRowItem(
                        label = "实际用时",
                        value = DurationFormatter.formatHoursMinutes(exam.actualDurationSeconds)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = YanjiBorderSoft)
                    DetailRowItem(
                        label = "计划时长",
                        value = DurationFormatter.formatHoursMinutes(exam.plannedDurationSeconds)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = YanjiBorderSoft)
                    DetailRowItem(label = "记录 ID", value = exam.id.take(8))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Notes Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = YanjiSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "考后复盘与失分分析",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (!exam.note.isNullOrBlank()) exam.note!! else "未录入考后复盘笔记",
                        fontSize = 13.sp,
                        color = if (!exam.note.isNullOrBlank()) YanjiTextPrimary else YanjiTextTertiary,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Delete Button
            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = YanjiError),
                border = androidx.compose.foundation.BorderStroke(1.dp, YanjiError.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("删除此条模考记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Secondary confirmation for deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除模考记录？", fontWeight = FontWeight.Bold) },
            text = {
                Text("删除后模考成绩和用时将从统计记录中移除，且无法撤销。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExam(exam.id)
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "模考记录已删除", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("确认删除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", color = YanjiTextSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = YanjiSurface
        )
    }
}
