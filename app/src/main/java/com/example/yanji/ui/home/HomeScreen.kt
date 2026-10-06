package com.example.yanji.ui.home

import com.example.yanji.ui.icons.RemixIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
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
import com.example.yanji.ui.components.RollingNumber
import com.example.yanji.ui.components.YanjiGroupedCard
import com.example.yanji.ui.components.YanjiSettingsRow
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

            // 1. Countdown Hero Card
            YanjiCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_countdown_card"),
                variant = YanjiCardVariant.Hero
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
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val countdownTitle = if (settings.targetExamDate.length >= 4) {
                                "${settings.targetExamDate.take(4)} 考研倒计时"
                            } else {
                                "考研倒计时"
                            }
                            Text(
                                text = countdownTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = settings.targetExamDate.ifBlank { "未设置" },
                            style = MaterialTheme.typography.bodySmall,
                            color = YanjiColors.textTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(YanjiSpacing.ItemGap))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (daysRemaining != null) {
                            RollingNumber(
                                text = "$daysRemaining",
                                style = com.example.yanji.theme.YanjiTypography.metricL,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "天",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        } else {
                            Text(
                                text = settings.targetExamDate.ifBlank { "未设置" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.targetExamDate.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                lineHeight = 44.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = settings.targetSchool.ifBlank { "未设置院校" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.targetSchool.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = settings.targetMajor.ifBlank { "未设置专业" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // 2. 今日累计专注时长 —— 首页第二张主卡：今天已经沉下去多少时间是最有
            //    说服力的一个数字，放在最高频的视线上；进度与科目分布也由它承载。
            YanjiGroupedCard(
                modifier = Modifier.fillMaxWidth(),
                cardModifier = Modifier.testTag("home_today_study_card"),
                onClick = { onNavigateToDailyDetail(todayIso) }
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    Text(
                        text = "今日专注学习",
                        style = MaterialTheme.typography.titleMedium,
                        // 与「今日计划」「每日打卡」同为 16sp / Bold：
                        // 三条主卡标题同一字号同一字重，扫首页时是同一层级的三件事。
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            val durationText = if (todayHours > 0) "${todayHours}h ${todayMins}m" else "${todayMins}m"
                            RollingNumber(
                                text = durationText,
                                style = com.example.yanji.theme.YanjiTypography.title1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (settings.dailyGoalHours > 0f) "达成 ${(progress * 100).toInt()}%" else "达成 -",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (progress >= 1f && settings.dailyGoalHours > 0f) YanjiColors.success else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = if (settings.dailyGoalHours > 0f) {
                                val h = if (settings.dailyGoalHours % 1f == 0f) settings.dailyGoalHours.toInt().toString() else settings.dailyGoalHours.toString()
                                "日目标 $h 小时"
                            } else {
                                "日目标 未设置"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 与学科时长分布共用同一胶囊进度条。
                    val goalProgressColor = if (progress >= 1f && settings.dailyGoalHours > 0f) {
                        YanjiColors.success
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    YanjiProgressBar(progress = progress, color = goalProgressColor)

                    if (state.todaySummary.subjectDistribution.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))

                        // Subject breakdown (Dynamic Single Source of Truth, Clickable, FlowRow responsive)
                        Text(
                            text = "今日科目时长分布",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.todaySummary.subjectDistribution.forEach { (subName, secs) ->
                                // 学科显示色由当前伙伴主题专属色阶体系与稳定槽位解析
                                SubjectTimeChip(
                                    name = subName,
                                    time = DurationFormatter.formatHoursMinutes(secs),
                                    color = com.example.yanji.theme.yanjiSubjectColorOf(subName),
                                    modifier = Modifier.clickable { onNavigateToSubjectDetail(subName) }
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

            // 5. 模考看板 —— 长期事件，排在每日任务之后；倒计时在首屏即可承接它。
            YanjiGroupedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_exam_card")
            ) {
                YanjiSettingsRow(
                    title = "模考看板 · 近期成绩",
                    subtitle = if (examSessions.isNotEmpty()) {
                        val recentStr = examSessions.take(2).joinToString("，") { "${it.subjectName.take(4)} ${it.score?.toInt() ?: 0}分" }
                        "已完成 ${examSessions.size} 次模拟 · $recentStr"
                    } else {
                        "尚未进行模拟考试，点击发起模考"
                    },
                    icon = RemixIcons.LineChartLine,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    value = if (avgScore > 0) "均分 ${String.format(Locale.US, "%.1f", avgScore)}" else null,
                    showChevron = true,
                    onClick = onNavigateToExamHistory
                )
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 6. AI 鼓励语 —— 收尾一句，只读已有统计，不引入新的数据源。
        // 注意：这里不能对连续天数做 `maxOf(1, ...)` —— 没有任何连续学习记录时
        // 显示"已达成 1 天"属于伪造统计。真实的 0 天就如实呈现，只是换成引导文案。
        val streakDays = state.streakDays
        AiEncouragementBanner(
            message = if (todaySecs > 0) {
                "今天已经积累 ${todayHours} 小时 ${todayMins} 分钟。专注的轨迹正在清晰留下，不急不躁，按部就班。"
            } else {
                "研迹已经为你准备好，开始你的第一段专注吧，每一步都算数。"
            },
            subMessage = if (streakDays > 0) {
                "连续有效学习已达成 $streakDays 天"
            } else {
                "还没有连续学习记录 · 今天开始第一段专注吧"
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
