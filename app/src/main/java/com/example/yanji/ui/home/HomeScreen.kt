package com.example.yanji.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.ChartLineUp
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.yanji.data.CheckIn
import com.example.yanji.data.DurationFormatter
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.CheckInCard
import com.example.yanji.ui.components.CheckInCelebrationDialog
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.JuanjuanEncouragementBanner
import java.util.*

@Composable
fun HomeScreen(
    onNavigateToFocus: () -> Unit,
    onNavigateToExam: () -> Unit,
    onNavigateToJournal: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToJuanjuanChat: () -> Unit = {},
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToSubjectDetail: (subjectId: String) -> Unit = {},
    onNavigateToExamHistory: () -> Unit = {},
    onNavigateToJournalEditor: (date: String) -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = YanjiSpacing.PageHorizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

            // 1. Countdown Card
            YanjiCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_countdown_card"),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(YanjiSpacing.ItemGap))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (daysRemaining != null) {
                            Text(
                                text = "$daysRemaining",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 44.sp
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

            // Check-In Card
            CheckInCard(
                onCheckInSuccess = { checkIn ->
                    celebratingCheckIn = checkIn
                }
            )

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // 2. Today's Study Card (Clickable to DailyStudyDetail)
            YanjiCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_today_study_card")
                    .clickable { onNavigateToDailyDetail(todayIso) },
                variant = YanjiCardVariant.Standard
            ) {
                Column(modifier = Modifier.padding(YanjiSpacing.CardPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "今日专注学习",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = PhosphorIcons.Regular.CaretRight,
                                contentDescription = "查看明细",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (settings.dailyGoalHours > 0f) "目标 ${settings.dailyGoalHours.toInt()}h" else "目标 未设置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${todayHours}h ${todayMins}m",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (settings.dailyGoalHours > 0f) "达成 ${(progress * 100).toInt()}%" else "达成 -",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (progress >= 1f && settings.dailyGoalHours > 0f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(YanjiRadius.ButtonRadius)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Subject breakdown (Dynamic Single Source of Truth, Clickable, FlowRow responsive)
                    Text(
                        text = "今日科目时长分布 (点击查看科目明细)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))

                    if (state.todaySummary.subjectDistribution.isEmpty()) {
                        Text(
                            text = "今日尚未记录学习时长，点击下方按钮开始专注",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.todaySummary.subjectDistribution.forEach { (subName, secs) ->
                                val color = when {
                                    subName.contains("数学") || subName.contains("线性代数") || subName.contains("概率论") -> SubjectMath
                                    subName.contains("408") || subName.contains("专业课") ||
                                        subName.contains("数据结构") || subName.contains("计算机组成") ||
                                        subName.contains("计算机网络") || subName.contains("操作系统") -> SubjectMajor
                                    subName.contains("英语") -> SubjectEnglish
                                    subName.contains("政治") -> SubjectPolitics
                                    else -> SubjectOther
                                }
                                SubjectTimeChip(
                                    name = subName,
                                    time = DurationFormatter.formatHoursMinutes(secs),
                                    color = color,
                                    modifier = Modifier.clickable { onNavigateToSubjectDetail(subName) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

            // 5. Exam Panorama & Linked Card (Clickable to ExamHistory)
            YanjiCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_exam_card")
                    .clickable { onNavigateToExamHistory() },
                variant = YanjiCardVariant.Compact
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(YanjiSpacing.CardPaddingCompact),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(YanjiRadius.ButtonRadius))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.ChartLineUp,
                            contentDescription = "模考看板",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(YanjiSpacing.ItemGap))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "模考看板 · 近期成绩",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (avgScore > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "均分 ${String.format(Locale.US, "%.1f", avgScore)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (examSessions.isNotEmpty()) {
                                val recentStr = examSessions.take(2).joinToString("，") { "${it.subjectName.take(4)} ${it.score?.toInt() ?: 0}分" }
                                "已完成 ${examSessions.size} 次模拟 · $recentStr"
                            } else {
                                "尚未进行模拟考试，点击发起模考"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = PhosphorIcons.Regular.CaretRight,
                        contentDescription = "查看详情",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 6. Juanjuan Encouragement
        // 注意：这里不能对连续天数做 `maxOf(1, ...)` —— 没有任何连续学习记录时
        // 显示"已达成 1 天"属于伪造统计。真实的 0 天就如实呈现，只是换成引导文案。
        val streakDays = state.streakDays
        JuanjuanEncouragementBanner(
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
            onClick = onNavigateToJuanjuanChat
        )

        // Unified Bottom Inset Padding
        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    // 2. Floating Draggable Juanjuan Ball (吸附在左右两侧，支持自由拖动与点击唤起伴学)
    FloatingJuanjuanBall(
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        onClick = onNavigateToJuanjuanChat
    )

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

    } // BoxWithConstraints
}

@Composable
fun SubjectTimeChip(
    name: String,
    time: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(YanjiRadius.Small))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
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

/**
 * 卷卷可拖动悬浮球：
 * 1. 默认停靠在屏幕右侧，初次加载在靠上位置；
 * 2. 支持用户手指自由拖动至全屏安全区域任意位置；
 * 3. 拖动松手后，根据横向中线自动以 Spring 弹簧动画吸附至最近的左侧或右侧边缘；
 * 4. 纵向位置保留用户拖动的高度，但自动限制在屏幕上下安全区域内（避开顶部与底部导航栏）；
 * 5. 点击（轻触位移小于 touchSlop）触发伴学对话。
 */
@Composable
fun FloatingJuanjuanBall(
    screenWidthPx: Float,
    screenHeightPx: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val ballSizeDp = 54.dp
    val ballSizePx = with(density) { ballSizeDp.toPx() }
    val edgeMarginPx = with(density) { 12.dp.toPx() }
    val minYPx = with(density) { 16.dp.toPx() }
    val maxYPx = (screenHeightPx - ballSizePx - with(density) { 96.dp.toPx() }).coerceAtLeast(minYPx)

    val leftDockX = edgeMarginPx
    val rightDockX = (screenWidthPx - ballSizePx - edgeMarginPx).coerceAtLeast(leftDockX)

    var isDockedOnLeft by rememberSaveable { mutableStateOf(false) }
    var dockedY by rememberSaveable { mutableStateOf<Float?>(null) }

    val defaultY = with(density) { 120.dp.toPx() }.coerceIn(minYPx, maxYPx)
    val currentTargetY = dockedY?.coerceIn(minYPx, maxYPx) ?: defaultY
    val currentTargetX = if (isDockedOnLeft) leftDockX else rightDockX

    val animX = remember { Animatable(currentTargetX) }
    val animY = remember { Animatable(currentTargetY) }
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    // 当屏幕尺寸或停靠方向变化时更新位置
    LaunchedEffect(screenWidthPx, screenHeightPx, isDockedOnLeft) {
        if (screenWidthPx > 0f && screenHeightPx > 0f) {
            val targetX = if (isDockedOnLeft) leftDockX else rightDockX
            val targetY = dockedY?.coerceIn(minYPx, maxYPx) ?: defaultY
            if (!animX.isRunning && !isDragging) {
                animX.snapTo(targetX)
            }
            if (!animY.isRunning && !isDragging) {
                animY.snapTo(targetY)
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "floatingBallScale"
    )

    Surface(
        modifier = modifier
            .offset {
                IntOffset(
                    animX.value.roundToInt(),
                    animY.value.roundToInt()
                )
            }
            .size(ballSizeDp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(screenWidthPx, screenHeightPx, leftDockX, rightDockX, minYPx, maxYPx) {
                val touchSlop = viewConfiguration.touchSlop
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        coroutineScope.launch {
                            animX.stop()
                            animY.stop()
                        }

                        var dragTotal = Offset.Zero
                        var hasDragged = false
                        var currentX = animX.value
                        var currentY = animY.value

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }

                            if (change == null || !change.pressed) {
                                change?.consume()
                                if (hasDragged) {
                                    val snapToLeft = (currentX + ballSizePx / 2f) < (screenWidthPx / 2f)
                                    val endX = if (snapToLeft) leftDockX else rightDockX
                                    val endY = currentY.coerceIn(minYPx, maxYPx)

                                    isDockedOnLeft = snapToLeft
                                    dockedY = endY
                                    isDragging = false

                                    coroutineScope.launch {
                                        launch {
                                            animX.animateTo(
                                                endX,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                        launch {
                                            animY.animateTo(
                                                endY,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    isDragging = false
                                    onClick()
                                }
                                break
                            }

                            val dragDelta = change.positionChange()
                            dragTotal += dragDelta

                            if (!hasDragged) {
                                if (dragTotal.getDistance() > touchSlop) {
                                    hasDragged = true
                                    isDragging = true
                                    change.consume()
                                    currentX = (currentX + dragDelta.x).coerceIn(0f, (screenWidthPx - ballSizePx).coerceAtLeast(0f))
                                    currentY = (currentY + dragDelta.y).coerceIn(minYPx, maxYPx)
                                    coroutineScope.launch {
                                        animX.snapTo(currentX)
                                        animY.snapTo(currentY)
                                    }
                                }
                            } else {
                                if (dragDelta != Offset.Zero) {
                                    change.consume()
                                    currentX = (currentX + dragDelta.x).coerceIn(0f, (screenWidthPx - ballSizePx).coerceAtLeast(0f))
                                    currentY = (currentY + dragDelta.y).coerceIn(minYPx, maxYPx)
                                    coroutineScope.launch {
                                        animX.snapTo(currentX)
                                        animY.snapTo(currentY)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    if (isDragging) {
                        isDragging = false
                        val currentX = animX.value
                        val snapToLeft = (currentX + ballSizePx / 2f) < (screenWidthPx / 2f)
                        val endX = if (snapToLeft) leftDockX else rightDockX
                        val endY = animY.value.coerceIn(minYPx, maxYPx)
                        coroutineScope.launch {
                            launch { animX.animateTo(endX, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                            launch { animY.animateTo(endY, spring(dampingRatio = Spring.DampingRatioNoBouncy)) }
                        }
                    }
                    throw e
                }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isDragging) 8.dp else 4.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            JuanjuanAvatar(size = 44.dp)
        }
    }
}
