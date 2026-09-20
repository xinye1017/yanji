package com.example.yanji.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.ExamSession
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.ui.components.AiAvatar
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiDetailTopBar
import com.example.yanji.ui.components.YanjiPrimaryButton

@Composable
fun ExamHistoryScreen(
    onBack: () -> Unit,
    onNavigateToExamDetail: (examId: String) -> Unit,
    onStartNewExam: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = yanjiViewModel { container -> ExamViewModel(container.repository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val examSessions = state.examSessions

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Unified Detail TopBar with Action Button
        YanjiDetailTopBar(
            title = "模拟考试记录",
            subtitle = "已完成 ${examSessions.size} 次全真模拟",
            onBack = onBack,
            actions = {
                YanjiPrimaryButton(
                    text = "新模考",
                    onClick = onStartNewExam,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        )

        if (examSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AiAvatar(size = 56.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无模拟考试记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    YanjiPrimaryButton(
                        text = "发起模拟考试",
                        onClick = onStartNewExam
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = YanjiSpacing.PageHorizontalPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(YanjiSpacing.CardGap)
            ) {
                items(examSessions) { exam ->
                    ExamHistoryCard(
                        exam = exam,
                        onClick = { onNavigateToExamDetail(exam.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExamHistoryCard(
    exam: ExamSession,
    onClick: () -> Unit
) {
    val subjectColor = when {
        exam.subjectName.contains("数学") || exam.subjectId.startsWith("math") -> SubjectMath
        exam.subjectName.contains("408") || exam.subjectId.startsWith("major") -> SubjectMajor
        exam.subjectName.contains("英语") || exam.subjectId == "english" -> SubjectEnglish
        exam.subjectName.contains("政治") || exam.subjectId == "politics" -> SubjectPolitics
        else -> SubjectOther
    }

    YanjiCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Compact
    ) {
        Column(modifier = Modifier.padding(YanjiSpacing.CardPaddingCompact)) {
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

                if (exam.score != null) {
                    Text(
                        text = "${exam.score.toInt()} 分",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${DurationFormatter.formatDateChinese(exam.startTime)} · 用时 ${DurationFormatter.formatHoursMinutes(exam.actualDurationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "计划 ${exam.plannedDurationSeconds / 60}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!exam.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = exam.note!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}
