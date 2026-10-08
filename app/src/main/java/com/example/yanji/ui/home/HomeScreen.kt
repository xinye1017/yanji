package com.example.yanji.ui.home

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import com.example.yanji.data.CheckIn
import com.example.yanji.data.DurationFormatter
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.CheckInCard
import com.example.yanji.ui.components.CheckInCelebrationDialog
import com.example.yanji.ui.components.AiEncouragementBanner
import com.example.yanji.ui.components.YanjiProgressBar

@Composable
fun HomeScreen(
    onNavigateToFocus: () -> Unit,
    onNavigateToExam: () -> Unit,
    onNavigateToNote: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToSubjectDetail: (subjectId: String) -> Unit = {},
    onNavigateToExamHistory: () -> Unit = {},
    onNavigateToNoteEditor: (date: String) -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onStartTask: ((com.example.yanji.data.StudyTask) -> Unit)? = null,
    viewModel: HomeViewModel = yanjiViewModel { container ->
        HomeViewModel(container.repository, container.statisticsRepository)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val examSessions = state.examSessions

    var celebratingCheckIn by remember { mutableStateOf<CheckIn?>(null) }

    val todayIso = state.todayIso

    val todaySecs = state.todaySummary.totalDurationSeconds
    val todayHours = todaySecs / 3600
    val todayMins = (todaySecs % 3600) / 60
    val goalHours = settings.dailyGoalHours
    val goalSecs = (goalHours * 3600).toLong()
    val progress = if (goalSecs > 0) (todaySecs.toFloat() / goalSecs).coerceIn(0f, 1f) else 0f

    val daysRemaining = state.daysRemaining

    // Exam statistics（派生值由 HomeViewModel 随数据变化重算）
    val avgScore = state.avgExamScore

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = YanjiSpacing.PageHorizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

            // 倒计时与今日结果共享一个核心视觉区，专注时长为主要视觉焦点。
            YanjiCard(
                modifier = Modifier.fillMaxWidth().testTag("home_study_hero"),
                variant = YanjiCardVariant.Hero
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().testTag("home_countdown_card"),
                        horizontalArrangement = Arrangement.spacedBy(YanjiSpacing.ItemGap),
                        verticalArrangement = Arrangement.spacedBy(YanjiSpacing.TightGap)
                    ) {
                        Text(
                            text = if (settings.targetExamDate.length >= 4) "${settings.targetExamDate.take(4)} 考研倒计时" else "考研倒计时",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Text(
                            text = daysRemaining?.let { "$it 天" } ?: settings.targetExamDate.ifBlank { "未设置" },
                            style = YanjiTypography.title2,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${settings.targetSchool.ifBlank { "目标院校未设置" }} · ${settings.targetMajor.ifBlank { "专业未设置" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

                    // 详情入口仅包住统计区，科目入口保留独立的触控语义。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_today_study_card")
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "查看今日学习详情",
                                onClick = { onNavigateToDailyDetail(todayIso) }
                            )
                    ) {
                        Text(
                            text = "今日专注学习",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))
                        Text(
                            text = if (todayHours > 0) "${todayHours}h ${todayMins}m" else "${todayMins}m",
                            style = YanjiTypography.metricL,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))
                        val goalColor = if (goalSecs > 0 && progress >= 1f) YanjiColors.success else MaterialTheme.colorScheme.primary
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(YanjiSpacing.ItemGap),
                            verticalArrangement = Arrangement.spacedBy(YanjiSpacing.TightGap)
                        ) {
                            Text(
                                text = if (goalSecs > 0) "达成 ${(progress * 100).toInt()}%" else "日目标未设置",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = goalColor
                            )
                            if (goalSecs > 0) {
                                val goalLabel = if (goalHours % 1f == 0f) goalHours.toInt().toString() else goalHours.toString()
                                Text(
                                    text = "日目标 $goalLabel 小时",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (goalSecs > 0) {
                            Spacer(modifier = Modifier.height(YanjiSpacing.ItemGap))
                            YanjiProgressBar(progress = progress, color = goalColor)
                        }
                    }

                    if (state.todaySummary.subjectDistribution.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(YanjiSpacing.ItemGapSmall),
                            verticalArrangement = Arrangement.spacedBy(YanjiSpacing.TightGap)
                        ) {
                            state.todaySummary.subjectDistribution.forEach { (name, secs) ->
                                SubjectTimeChip(
                                    name = name,
                                    time = DurationFormatter.formatHoursMinutes(secs),
                                    color = yanjiSubjectColorOf(name),
                                    modifier = Modifier.clickable { onNavigateToSubjectDetail(name) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // 3. 今日计划 —— 计划是还没发生的事，放在「实际做了多久」的后面：
            //    先看清今天的结果，再安排下一步。
            TodayPlanCard(
                tasks = state.todayTasks,
                subjects = state.subjects,
                dailyGoalMinutes = (state.settings.dailyGoalHours * 60).toInt(),
                onAdd = { subject, title, minutes, onCreated ->
                    viewModel.addStudyTask(subject, title, minutes, onCreated)
                },
                onEdit = { task, subject, title, minutes -> viewModel.updateStudyTask(task, subject, title, minutes) },
                onToggle = { task -> viewModel.setStudyTaskCompleted(task.id, !task.isCompleted) },
                onDelete = { id -> viewModel.deleteStudyTask(id) },
                actualSecondsByTaskId = state.actualSecondsByTaskId,
                runningTaskId = state.runningTaskId,
                onStart = { task -> onStartTask?.invoke(task) ?: onNavigateToFocus() }
            )

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // 4. 每日打卡 —— 签完就折叠成一行「已累计签到 N 天」，不占主卡位，
            //    紧跟计划之后，模考看板与 AI 鼓励语这类低频内容排在它下面。
            CheckInCard(
                onCheckInSuccess = { checkIn -> celebratingCheckIn = checkIn },
                onTodayCheckInClick = { checkIn -> celebratingCheckIn = checkIn }
            )

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // 低频模考只保留记录行，不再占一个独立卡片。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_exam_card")
                    .clickable(role = Role.Button, onClickLabel = "查看模考记录", onClick = onNavigateToExamHistory)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = YanjiSpacing.CardPadding, vertical = YanjiSpacing.ItemGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YanjiSpacing.ItemGap)
            ) {
                Icon(
                    imageVector = RemixIcons.LineChartLine,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "模考看板",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            examSessions.isEmpty() -> "尚无模考记录"
                            avgScore > 0 -> "${examSessions.size} 次模拟 · 均分 ${String.format(Locale.US, "%.1f", avgScore)}"
                            else -> "${examSessions.size} 次模拟"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = RemixIcons.ArrowRightSLine,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

        // 6. AI 鼓励语 —— 收尾一句，只读已有统计，不引入新的数据源。
        // 注意：这里不能对连续天数做 `maxOf(1, ...)` —— 没有任何连续学习记录时
        // 显示"已达成 1 天"属于伪造统计。真实的 0 天就如实呈现，只是换成引导文案。
        val streakDays = state.streakDays
        AiEncouragementBanner(
            message = if (todaySecs > 0) {
                "今天的每一段专注，都在让你靠近目标。"
            } else {
                "从今天的第一项计划开始，每一步都算数。"
            },
            subMessage = if (streakDays > 0) {
                "连续有效学习已达成 $streakDays 天"
            } else {
                null
            },
            onClick = onNavigateToStats
        )

        // Unified Bottom Inset Padding
        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    celebratingCheckIn?.let { checkIn ->
        CheckInCelebrationDialog(
            checkIn = checkIn,
            onDismiss = { celebratingCheckIn = null },
            onNavigateToFocus = {
                celebratingCheckIn = null
                onNavigateToFocus()
            }
        )
    }

    } // Box
}

@Composable
fun SubjectTimeChip(
    name: String,
    time: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    // 不画背景：学科胶囊原本是「圆角块 + 12% 学科色底」，在「今日专注学习」这张已经
    // 有大数字、进度条和目标文案的卡里，N 个色块会把标题和数字的层级压掉。
    // 去掉底色后学科色交给左侧 6dp 圆点承载，仍然一眼能分辨，但整行安静下来。
    // 48dp 的最小触控高度保留（点击进科目详情），只是不再有可见的点击态外观。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            // 横向内边距由 10dp 收到 6dp：底色没了，原本用来「填」的呼吸感会让
            // 相邻两个科目之间空出 28dp（10 + FlowRow 的 8 + 10），收到 6dp 后是 20dp。
            .padding(horizontal = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}
