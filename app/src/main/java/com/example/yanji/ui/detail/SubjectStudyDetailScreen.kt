package com.example.yanji.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.StudyTimeRange
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.yanjiSeriesColor
import com.example.yanji.ui.components.AiAvatar
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiDetailTopBar
import com.example.yanji.ui.components.YanjiPrimaryButton

@Composable
fun SubjectStudyDetailScreen(
    subjectId: String,
    onBack: () -> Unit,
    onNavigateToFocusDetail: (sessionId: String) -> Unit,
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateToStartFocus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubjectStudyDetailViewModel = yanjiViewModel(
        key = subjectId
    ) { container -> SubjectStudyDetailViewModel(container.statisticsRepository, subjectId) }
) {
    var selectedRange by rememberSaveable(subjectId) { mutableStateOf(StudyTimeRange.TODAY) }

    val summary by viewModel.summary.collectAsStateWithLifecycle()

    val selectRange: (StudyTimeRange) -> Unit = { range ->
        selectedRange = range
    }

    LaunchedEffect(selectedRange) {
        viewModel.selectRange(selectedRange)
    }

    val seriesFallback = MaterialTheme.colorScheme.primary
    val rawSubjectColor = remember(summary.subjectColor, seriesFallback) {
        runCatching { Color(summary.subjectColor.toColorInt()) }.getOrDefault(seriesFallback)
    }
    val subjectColor = yanjiSeriesColor(summary.subjectColor, rawSubjectColor)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Unified Detail TopBar with Subject Color Dot
        YanjiDetailTopBar(
            title = summary.subjectName,
            onBack = onBack,
            titleLeading = {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(subjectColor)
                )
            }
        )

        // Time Range Filter Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedRange.ordinal,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YanjiSpacing.PageHorizontalPadding)
                .clip(RoundedCornerShape(YanjiRadius.Small)),
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
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(YanjiRadius.Small))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = YanjiSpacing.PageHorizontalPadding),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(YanjiSpacing.CardGap)
        ) {
            // Summary Card
            item {
                YanjiCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = YanjiCardVariant.Standard
                ) {
                    Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                        Text(
                            text = "${selectedRange.title}专注投入",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = DurationFormatter.formatHoursMinutes(summary.totalDurationSeconds),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "共 ${summary.sessionCount} 次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (summary.longestSessionSeconds > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "单次最长：",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = DurationFormatter.formatHoursMinutes(summary.longestSessionSeconds),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (summary.sessions.isEmpty()) {
                item {
                    YanjiCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = YanjiCardVariant.Standard
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AiAvatar(size = 56.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "这个科目在${selectedRange.title}还没有专注记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            YanjiPrimaryButton(
                                text = "开始专注",
                                onClick = onNavigateToStartFocus
                            )
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
