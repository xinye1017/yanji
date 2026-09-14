package com.example.yanji.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.*
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiDetailTopBar
import com.example.yanji.ui.components.YanjiPrimaryButton

@Composable
fun DailyStudyDetailScreen(
    date: String,
    onBack: () -> Unit,
    onNavigateToSubjectDetail: (subjectId: String) -> Unit,
    onNavigateToFocusDetail: (sessionId: String) -> Unit,
    onNavigateToExamDetail: (examId: String) -> Unit,
    onNavigateToStartFocus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyStudyDetailViewModel = yanjiViewModel(
        key = date
    ) { container -> DailyStudyDetailViewModel(container.statisticsRepository, date) }
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Unified Detail TopBar
        YanjiDetailTopBar(
            title = DurationFormatter.formatDateWithWeekday(date),
            onBack = onBack
        )

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
                            text = "学习总览",
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
                                text = "共 ${summary.focusCount} 次专注" + if (summary.examCount > 0) " · ${summary.examCount} 次模考" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (summary.subjectDistribution.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "科目投入分布",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            .clip(RoundedCornerShape(YanjiRadius.Small))
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
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = DurationFormatter.formatHoursMinutes(secs),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (summary.sessions.isEmpty()) {
                // Empty state
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
                            JuanjuanAvatar(size = 56.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "今天还没有留下学习轨迹。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
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

@Composable
fun DailySessionRowCard(
    item: DailySessionItem,
    onClick: () -> Unit
) {
    val tagColor = remember(item.subjectColor) {
        try {
            Color(item.subjectColor.toColorInt())
        } catch (e: Exception) {
            YanjiPrimary
        }
    }

    YanjiCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = YanjiCardVariant.Compact
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(YanjiSpacing.CardPaddingCompact),
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(YanjiRadius.Small))
                            .background(tagColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.isExam) "模拟考试" else item.subjectName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tagColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DurationFormatter.formatHoursMinutes(item.durationSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (item.isExam && item.score != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.score.toInt()} 分",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (item.pauseCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "暂停 ${item.pauseCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
