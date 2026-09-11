package com.example.yanji.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.ExamSession
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar

@Composable
fun ExamHistoryScreen(
    onBack: () -> Unit,
    onNavigateToExamDetail: (examId: String) -> Unit,
    onStartNewExam: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = viewModel { ExamViewModel(YanjiRepository.getInstance()) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val examSessions = state.examSessions

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "模拟考试记录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary
                )
                Text(
                    text = "已完成 ${examSessions.size} 次全真模拟",
                    fontSize = 12.sp,
                    color = YanjiTextSecondary
                )
            }
            Button(
                onClick = onStartNewExam,
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("新模考", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (examSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JuanjuanAvatar(size = 56.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无模拟考试记录",
                        fontSize = 14.sp,
                        color = YanjiTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartNewExam,
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("发起模拟考试")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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

                if (exam.score != null) {
                    Text(
                        text = "${exam.score.toInt()} 分",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiSuccess
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
                    fontSize = 12.sp,
                    color = YanjiTextSecondary
                )
            }

            if (!exam.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(YanjiSurfaceSoft)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = exam.note!!,
                        fontSize = 12.sp,
                        color = YanjiTextSecondary,
                        maxLines = 2,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
