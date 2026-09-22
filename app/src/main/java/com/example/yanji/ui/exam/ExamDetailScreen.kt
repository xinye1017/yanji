package com.example.yanji.ui.exam

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Trash
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.SubjectCatalog
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiDangerButton
import com.example.yanji.ui.components.YanjiDetailTopBar
import com.example.yanji.ui.detail.DetailRowItem

@Composable
fun ExamDetailScreen(
    examId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = yanjiViewModel { container -> ExamViewModel(container.repository) }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val examState = remember(examId) {
        viewModel.getExamSessionFlow(examId)
    }.collectAsStateWithLifecycle(initialValue = state.examSessions.find { it.id == examId })
    val exam = examState.value

    var showDeleteConfirmDialog by rememberSaveable(examId) { mutableStateOf(false) }

    if (exam == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("模考记录已删除或不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    // 颜色按学科在目录中的顺序取主题色阶，不做学科名判断（学科可自定义）。
    val subjectColor = yanjiSeriesColorAt(SubjectCatalog.colorIndexOf(exam.subjectId))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Unified Detail TopBar
        YanjiDetailTopBar(
            title = "模考复盘详情",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = YanjiSpacing.PageHorizontalPadding, vertical = 8.dp)
        ) {
            // Main Overview Card
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Standard
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(YanjiRadius.Small))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "全真模拟",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        if (exam.score != null) {
                            Text(
                                text = "${exam.score.toInt()}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = " / ${exam.maxScore?.toInt() ?: 150} 分",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        } else {
                            Text(
                                text = "未录入分数",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${DurationFormatter.formatDateChinese(exam.startTime)} · 用时 ${DurationFormatter.formatHoursMinutes(exam.actualDurationSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // Details Info Card
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Compact
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    DetailRowItem(label = "考试日期", value = DurationFormatter.formatDateChinese(exam.startTime))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRowItem(
                        label = "考试起止",
                        value = DurationFormatter.formatTimeRange(exam.startTime, exam.endTime)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRowItem(
                        label = "实际用时",
                        value = DurationFormatter.formatHoursMinutes(exam.actualDurationSeconds)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRowItem(
                        label = "计划时长",
                        value = DurationFormatter.formatHoursMinutes(exam.plannedDurationSeconds)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRowItem(label = "记录 ID", value = exam.id.take(8))
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // Notes Card
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Compact
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    Text(
                        text = "考后复盘与失分分析",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (!exam.note.isNullOrBlank()) exam.note!! else "未录入考后复盘笔记",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!exam.note.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // Delete Button
            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.Trash,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "删除此条模考记录",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Secondary confirmation for deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    "确认删除模考记录？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "删除后模考成绩和用时将从统计记录中移除，且无法撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                YanjiDangerButton(
                    text = "确认删除",
                    onClick = {
                        viewModel.deleteExam(exam.id)
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "模考记录已删除", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(YanjiRadius.DialogRadius),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
