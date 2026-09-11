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
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.StudyTimeRange
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar

@Composable
fun SubjectStudyDetailScreen(
    subjectId: String,
    onBack: () -> Unit,
    onNavigateToFocusDetail: (sessionId: String) -> Unit,
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateToStartFocus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubjectStudyDetailViewModel = viewModel(
        key = subjectId
    ) { SubjectStudyDetailViewModel(StudyStatisticsRepository.getInstance(), subjectId) }
) {
    var selectedRange by remember { mutableStateOf(StudyTimeRange.TODAY) }

    val summary by viewModel.summary.collectAsStateWithLifecycle()

    val selectRange: (StudyTimeRange) -> Unit = { range ->
        selectedRange = range
        viewModel.selectRange(range)
    }

    val subjectColor = remember(summary.subjectColor) {
        try {
            Color(android.graphics.Color.parseColor(summary.subjectColor))
        } catch (e: Exception) {
            YanjiPrimary
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = YanjiTextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(subjectColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = summary.subjectName,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )
        }

        // Time Range Filter Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedRange.ordinal,
            containerColor = YanjiSurfaceSoft,
            contentColor = YanjiPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp)),
            indicator = {}
        ) {
            StudyTimeRange.entries.forEach { range ->
                val isSelected = selectedRange == range
                Tab(
                    selected = isSelected,
                    onClick = { selectRange(range) },
                    text = {
                        Text(
                            text = range.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) YanjiPrimary else YanjiTextSecondary
                        )
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) YanjiSurface else Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                            text = "${selectedRange.title}专注投入",
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
                                text = "共 ${summary.sessionCount} 次",
                                fontSize = 13.sp,
                                color = YanjiTextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (summary.longestSessionSeconds > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "单次最长：",
                                    fontSize = 12.sp,
                                    color = YanjiTextSecondary
                                )
                                Text(
                                    text = DurationFormatter.formatHoursMinutes(summary.longestSessionSeconds),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = YanjiPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Session List Header
            item {
                Text(
                    text = "专注明细记录",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary
                )
            }

            if (summary.sessions.isEmpty()) {
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
                                text = "这个科目在${selectedRange.title}还没有专注记录。",
                                fontSize = 14.sp,
                                color = YanjiTextSecondary
                            )
        Spacer(modifier = Modifier.height(32.dp))
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
