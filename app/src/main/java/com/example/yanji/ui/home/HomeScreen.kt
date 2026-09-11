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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.example.yanji.data.CheckIn
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.CheckInCard
import com.example.yanji.ui.components.CheckInCelebrationDialog
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.JuanjuanEncouragementBanner
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onNavigateToFocus: () -> Unit,
    onNavigateToExam: () -> Unit,
    onNavigateToJournal: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToJuanjuanChat: () -> Unit = {},
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToSubjectDetail: (subjectId: String) -> Unit = {},
    onNavigateToExamHistory: () -> Unit = {},
    onNavigateToJournalEditor: (date: String) -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onQuickStart: (preset: QuickStartPreset) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel {
        HomeViewModel(YanjiRepository.getInstance(), StudyStatisticsRepository.getInstance())
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val examSessions = state.examSessions
    val quickPresets = state.quickPresets

    var presetToDelete by remember { mutableStateOf<QuickStartPreset?>(null) }

    // 长按进入删除编辑态：被长按的块右上角显示红叉
    var editingQuickId by remember { mutableStateOf<String?>(null) }

    // 长按拖动排序的状态（坐标统一使用 window 坐标系）
    val itemTopLefts = remember { mutableStateMapOf<Int, Offset>() }
    val itemSizes = remember { mutableStateMapOf<Int, IntSize>() }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    var celebratingCheckIn by remember { mutableStateOf<CheckIn?>(null) }

    val todayIso = state.todayIso

    val todaySecs = state.todaySummary.totalDurationSeconds
    val todayHours = todaySecs / 3600
    val todayMins = (todaySecs % 3600) / 60
    val goalHours = settings.dailyGoalHours
    val goalSecs = (goalHours * 3600).toLong()
    val progress = (todaySecs.toFloat() / goalSecs.coerceAtLeast(1L)).coerceIn(0f, 1f)

    val daysRemaining = state.daysRemaining

    // Exam statistics（派生值由 HomeViewModel 随数据变化重算）
    val avgScore = state.avgExamScore

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiBackground)
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 点击快捷操作区以外的空白处时，退出红叉删除编辑态
                .pointerInput(Unit) {
                    detectTapGestures { editingQuickId = null }
                }
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

            // 1. Countdown Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
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
                                .background(YanjiPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2027 考研倒计时",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = YanjiTextSecondary
                        )
                    }
                    Text(
                        text = settings.targetExamDate,
                        fontSize = 12.sp,
                        color = YanjiTextTertiary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (daysRemaining != null) {
                        Text(
                            text = "$daysRemaining",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiPrimary,
                            lineHeight = 44.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "天",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else {
                        // 解析失败回退：直接显示原始日期字符串
                        Text(
                            text = settings.targetExamDate,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiPrimary,
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
                            text = settings.targetSchool,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )
                        Text(
                            text = settings.targetMajor,
                            fontSize = 12.sp,
                            color = YanjiTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Check-In Card（打卡状态由 CheckInViewModel 自持，宿主不再传 repo）
        CheckInCard(
            onCheckInSuccess = { checkIn ->
                celebratingCheckIn = checkIn
            }
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 2. Today's Study Card (Clickable to DailyStudyDetail)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onNavigateToDailyDetail(todayIso) },
            shape = RoundedCornerShape(20.dp),
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
                        Text(
                            text = "今日专注学习",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "查看明细",
                            tint = YanjiTextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "目标 ${settings.dailyGoalHours.toInt()}h",
                        fontSize = 13.sp,
                        color = YanjiTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${todayHours}h ${todayMins}m",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "达成 ${(progress * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (progress >= 1f) YanjiSuccess else YanjiPrimary,
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
                        .clip(RoundedCornerShape(12.dp)),
                    color = YanjiPrimary,
                    trackColor = YanjiPrimarySoft,
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Subject breakdown (Dynamic Single Source of Truth, Clickable)
                Text(
                    text = "今日科目时长分布 (点击查看科目明细)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = YanjiTextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.todaySummary.subjectDistribution.isEmpty()) {
                    Text(
                        text = "今日尚未记录学习时长，点击下方按钮开始专注",
                        fontSize = 12.sp,
                        color = YanjiTextTertiary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigateToSubjectDetail(subName) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 4. Quick Actions (Visual Hierarchy: Primary vs Secondary)
        Text(
            text = "快捷操作",
            style = MaterialTheme.typography.titleMedium,
            color = YanjiTextPrimary
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))

        // 4. Quick Actions：内置模板（开始专注 / 模拟考试 / 写今日日记）与自定义组合统一展示。
        // 长按任意块进入删除编辑态（右上角红叉），再点红叉弹出确认。
        if (quickPresets.isEmpty()) {
            // 空状态占位：没有快捷操作时引导用户去专注页创建
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(YanjiSurfaceSoft)
                    .clickable { onNavigateToFocus() }
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = YanjiPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "暂无快捷操作",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "去「专注计时」页选好科目与模式，点「把当前组合保存为首页快捷」即可添加",
                            fontSize = 12.sp,
                            color = YanjiTextSecondary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        } else {
        quickPresets.chunked(3).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { preset ->
                    val index = quickPresets.indexOfFirst { it.id == preset.id }
                    val isDragging = dragIndex == index

                    QuickActionButton(
                        preset = preset,
                        modifier = Modifier
                            .weight(1f)
                            .zIndex(if (isDragging) 1f else 0f)
                            // pointerInput / onGloballyPositioned 必须在 graphicsLayer 外侧：
                            // 保证手势坐标与布局坐标都不随拖动平移变化
                            .pointerInput(preset.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { _ ->
                                        dragIndex = index
                                        dragOffset = Offset.Zero
                                        editingQuickId = preset.id
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val current = dragIndex
                                        if (current < 0) return@detectDragGesturesAfterLongPress
                                        dragOffset += amount

                                        val myTop = itemTopLefts[current]
                                            ?: return@detectDragGesturesAfterLongPress
                                        val mySize = itemSizes[current]
                                            ?: return@detectDragGesturesAfterLongPress
                                        if (mySize == IntSize.Zero) {
                                            return@detectDragGesturesAfterLongPress
                                        }

                                        // 被拖块中心（window 坐标）
                                        val draggingCenter = myTop + dragOffset +
                                            Offset(mySize.width / 2f, mySize.height / 2f)

                                        // 找中心最近的其它块
                                        var targetIndex = -1
                                        var bestDist = Float.MAX_VALUE
                                        itemTopLefts.forEach { (i, topLeft) ->
                                            if (i == current) return@forEach
                                            val size = itemSizes[i] ?: return@forEach
                                            val center = topLeft + Offset(size.width / 2f, size.height / 2f)
                                            val dist = (center - draggingCenter).getDistance()
                                            if (dist < bestDist) {
                                                bestDist = dist
                                                targetIndex = i
                                            }
                                        }

                                        val hitRadius = maxOf(mySize.width, mySize.height) * 0.62f
                                        if (targetIndex >= 0 && bestDist < hitRadius) {
                                            val draggedId = quickPresets.getOrNull(current)?.id
                                                ?: return@detectDragGesturesAfterLongPress
                                            val oldTop = myTop
                                            val newTop = itemTopLefts[targetIndex] ?: oldTop
                                            viewModel.moveQuickStartPreset(draggedId, targetIndex)
                                            // 补偿：交换后保持手指相对被拖块的抓取位置不变
                                            dragOffset += oldTop - newTop
                                            dragIndex = targetIndex
                                        }
                                    },
                                    onDragEnd = {
                                        viewModel.commitQuickStartPresetOrder()
                                        dragIndex = -1
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        viewModel.commitQuickStartPresetOrder()
                                        dragIndex = -1
                                        dragOffset = Offset.Zero
                                    }
                                )
                            }
                            .onGloballyPositioned { coords ->
                                if (index >= 0) {
                                    itemTopLefts[index] = coords.boundsInWindow().topLeft
                                    itemSizes[index] = coords.size
                                }
                            }
                            .graphicsLayer {
                                if (isDragging) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    shadowElevation = 24f
                                    shape = RoundedCornerShape(20.dp)
                                    alpha = 0.94f
                                }
                            },
                        showDeleteBadge = editingQuickId == preset.id,
                        onClick = {
                            if (editingQuickId != null) {
                                editingQuickId = null
                            } else {
                                when (preset.type) {
                                    QuickStartPreset.TYPE_START_FOCUS -> onNavigateToFocus()
                                    QuickStartPreset.TYPE_EXAM -> onNavigateToExam()
                                    QuickStartPreset.TYPE_JOURNAL -> onNavigateToJournalEditor(todayIso)
                                    else -> onQuickStart(preset)
                                }
                            }
                        },
                        onDeleteClick = { presetToDelete = preset }
                    )
                }
            }
        }
        } // end else: quickPresets.isNotEmpty()

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // 5. Exam Panorama & Linked Card (Clickable to ExamHistory)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onNavigateToExamHistory() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(YanjiLavenderSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Assessment,
                        contentDescription = "模考看板",
                        tint = YanjiLavender,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "模考看板 · 近期成绩",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        )
                        if (avgScore > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "均分 ${String.format("%.1f", avgScore)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiSuccess
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
                        fontSize = 12.sp,
                        color = YanjiTextSecondary,
                        maxLines = 1
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "查看详情",
                    tint = YanjiTextTertiary
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

    // 移除自定义快捷操作
    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = {
                Text("移除快捷操作", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "确定从首页快捷操作中移除「${preset.label}」吗？",
                    fontSize = 14.sp,
                    color = YanjiTextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuickStartPreset(preset.id)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiDanger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("移除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("取消", color = YanjiTextSecondary, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
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
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 6.dp)
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
            fontSize = 12.sp,
            color = YanjiTextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = time,
            fontSize = 12.sp,
            color = YanjiTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickActionButton(
    preset: QuickStartPreset,
    modifier: Modifier = Modifier,
    showDeleteBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    // 内置模板沿用原三键配色；自定义组合用主色淡底区分
    val (containerColor, contentColor, icon) = when (preset.type) {
        QuickStartPreset.TYPE_START_FOCUS -> Triple(YanjiPrimary, YanjiOnPrimary, Icons.Default.PlayArrow)
        QuickStartPreset.TYPE_EXAM -> Triple(YanjiLavenderSoft, YanjiLavender, Icons.Outlined.Timer)
        QuickStartPreset.TYPE_JOURNAL -> Triple(YanjiSurfaceBlue, YanjiPrimaryStrong, Icons.Outlined.EditNote)
        else -> Triple(YanjiPrimarySoft, YanjiPrimary, Icons.Default.PlayArrow)
    }
    val subLabel = if (preset.isBuiltin) preset.subLabel else "${preset.subjectName} · ${preset.mode}"

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = preset.label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = preset.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subLabel,
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 删除编辑态：右上角红色叉，点击弹出确认
        if (showDeleteBadge) {
            Surface(
                shape = CircleShape,
                color = YanjiDanger,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clickable { onDeleteClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除该快捷操作",
                    tint = Color.White,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
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
        color = YanjiSurface,
        shadowElevation = if (isDragging) 8.dp else 4.dp,
        border = BorderStroke(1.5.dp, YanjiPrimarySoft)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            JuanjuanAvatar(size = 44.dp)
        }
    }
}
