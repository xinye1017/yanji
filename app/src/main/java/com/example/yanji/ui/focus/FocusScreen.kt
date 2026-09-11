package com.example.yanji.ui.focus

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.Subject
import com.example.yanji.data.YanjiRepository
import com.example.yanji.service.FocusTimerService
import com.example.yanji.service.TimerServiceMode
import com.example.yanji.theme.*
import java.text.SimpleDateFormat
import java.util.*

private data class DurationOption(
    val minutes: Int,
    val title: String,
    val subtitle: String,
    val fullLabel: String,
    val mode: String
)

private val QUICK_DURATIONS = listOf(
    DurationOption(25, "25", "番茄", "25 分钟番茄钟", FocusModes.POMODORO_25),
    DurationOption(45, "45", "深度", "45 分钟深度学习", FocusModes.POMODORO_45),
    DurationOption(60, "60", "小测", "60 分钟专项模拟", FocusModes.DEEP_60),
    DurationOption(90, "90", "专题", "90 分钟真题整块", FocusModes.BIG_90)
)


private fun getSubjectIcon(subjectId: String, name: String): ImageVector {
    val lower = (subjectId + name).lowercase()
    return when {
        lower.contains("math") || lower.contains("数") -> Icons.Default.Calculate
        lower.contains("408") || lower.contains("major") || lower.contains("计") || lower.contains("code") -> Icons.Default.Terminal
        lower.contains("english") || lower.contains("英") || lower.contains("语") -> Icons.Default.Translate
        lower.contains("politic") || lower.contains("政") || lower.contains("思") -> Icons.Default.AutoStories
        else -> Icons.Default.School
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    onNavigateToExam: () -> Unit,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToFocusDetail: (sessionId: String) -> Unit = {},
    quickStartPreset: QuickStartPreset? = null,
    onQuickStartConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FocusViewModel = viewModel {
        FocusViewModel(
            YanjiRepository.getInstance(),
            StudyStatisticsRepository.getInstance()
        )
    }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subjects = state.subjects
    val activeSession = state.activeSession
    val timerServiceState by FocusTimerService.timerState.collectAsStateWithLifecycle()

    var selectedSubject by remember {
        mutableStateOf(
            subjects.firstOrNull { it.id == "math_advanced" }
                ?: Subject("math_advanced", "高等数学", "#356AE6", parentId = "math")
        )
    }
    var selectedMode by remember { mutableStateOf(FocusModes.COUNT_UP) }
    var noteText by remember { mutableStateOf("") }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var lastFinishedSession by remember { mutableStateOf<FocusSession?>(null) }

    // 启动专注
    fun launchFocus(subject: Subject, mode: String, note: String) {
        val session = viewModel.startFocus(
            subjectId = subject.id,
            subjectName = subject.name,
            note = note,
            mode = mode
        )
        if (session == null) {
            Toast.makeText(context, "已有计时在进行中，请先结束当前的专注或模考", Toast.LENGTH_SHORT).show()
            return
        }
        FocusTimerService.startFocus(context, session.id, subject.name, FocusModes.targetSeconds(mode))
    }

    fun dismissSummary() {
        showSummaryDialog = false
        viewModel.acknowledgeCompletedFocus()
    }

    // 首页快捷操作响应
    LaunchedEffect(quickStartPreset) {
        val preset = quickStartPreset ?: return@LaunchedEffect
        onQuickStartConsumed()
        if (activeSession != null) {
            Toast.makeText(context, "已有专注在进行中，请先结束本次计时", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        val subject = subjects.find { it.id == preset.subjectId }
            ?: Subject(preset.subjectId, preset.subjectName, "#356AE6")
        selectedSubject = subject
        selectedMode = preset.mode
        noteText = preset.note
        launchFocus(subject, preset.mode, preset.note)
    }

    val currentElapsedSeconds = if (timerServiceState.isRunning && timerServiceState.mode == TimerServiceMode.FOCUS) {
        timerServiceState.elapsedSeconds
    } else {
        activeSession?.durationSeconds ?: 0L
    }

    val completedFocus = state.lastCompletedFocus
    LaunchedEffect(completedFocus) {
        if (completedFocus != null) {
            lastFinishedSession = completedFocus
            showSummaryDialog = true
        }
    }

    if (activeSession != null) {
        // Active Focusing Screen (区分正向计时和倒计时)
        ActiveFocusContent(
            session = activeSession,
            elapsedSeconds = currentElapsedSeconds,
            onPause = {
                FocusTimerService.pauseTimer(context)
                viewModel.pauseFocus(currentElapsedSeconds)
            },
            onResume = {
                FocusTimerService.resumeTimer(context)
                viewModel.resumeFocus()
            },
            onFinish = {
                if (currentElapsedSeconds < 60L) {
                    Toast.makeText(context, "专注时间不足 1 分钟，本次记录不予保存", Toast.LENGTH_SHORT).show()
                    FocusTimerService.discardTimer(context)
                    viewModel.cancelFocus()
                } else {
                    FocusTimerService.completeTimer(context)
                }
            },
            onCancel = {
                FocusTimerService.discardTimer(context)
                viewModel.cancelFocus()
            }
        )
    } else {
        // Setup / Entry Screen (Stitch 设计)
        FocusSetupContent(
            subjects = subjects,
            selectedSubject = selectedSubject,
            onSelectSubject = { selectedSubject = it },
            selectedMode = selectedMode,
            onSelectMode = { selectedMode = it },
            todayTotalSeconds = state.todayTotalSeconds,
            onStart = { launchFocus(selectedSubject, selectedMode, "") },
            onSaveAsQuickAction = { label ->
                viewModel.saveQuickStartPreset(
                    QuickStartPreset(
                        label = label.ifBlank { "开始${selectedSubject.name}${selectedMode}" },
                        subjectId = selectedSubject.id,
                        subjectName = selectedSubject.name,
                        mode = selectedMode,
                        note = ""
                    )
                )
                Toast.makeText(context, "已添加到首页快捷操作", Toast.LENGTH_SHORT).show()
            },
            onNavigateToExam = onNavigateToExam,
            onNavigateToDailyDetail = onNavigateToDailyDetail
        )
    }

    // 完成结算弹窗
    if (showSummaryDialog && lastFinishedSession != null) {
        val totalSecs = state.todayFocusSeconds
        val totalH = totalSecs / 3600
        val totalM = (totalSecs % 3600) / 60
        val sessionH = lastFinishedSession!!.durationSeconds / 3600
        val sessionM = (lastFinishedSession!!.durationSeconds % 3600) / 60
        val sessionS = lastFinishedSession!!.durationSeconds % 60

        AlertDialog(
            onDismissRequest = { dismissSummary() },
            confirmButton = {
                Button(
                    onClick = { dismissSummary() },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("太棒了，收下轨迹", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = YanjiSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("专注完成！", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "本次投入 ${lastFinishedSession!!.subjectName} 学习：",
                        fontSize = 14.sp,
                        color = YanjiTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (sessionH > 0) "${sessionH}小时 ${sessionM}分钟 ${sessionS}秒" else "${sessionM}分钟 ${sessionS}秒",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "今天已累计专注 ${totalH}h ${totalM}m。每一份真实的专注，都是上岸的阶梯。",
                        fontSize = 13.sp,
                        color = YanjiTextPrimary,
                        lineHeight = 18.sp
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
        )
    }
}

/**
 * 专注入场页（基于 Stitch 设计重构）
 */
@Composable
fun FocusSetupContent(
    subjects: List<Subject>,
    selectedSubject: Subject,
    onSelectSubject: (Subject) -> Unit,
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    todayTotalSeconds: Long = 0L,
    onStart: () -> Unit,
    onSaveAsQuickAction: (String) -> Unit = {},
    onNavigateToExam: () -> Unit,
    onNavigateToDailyDetail: (String) -> Unit = {}
) {
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var isCountdownMode by remember(selectedMode) {
        mutableStateOf(selectedMode != FocusModes.COUNT_UP)
    }
    var selectedDurationMinutes by remember(selectedMode) {
        val mins = (FocusModes.targetSeconds(selectedMode) / 60L).toInt()
        mutableStateOf(if (mins > 0) mins else 45)
    }

    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var showSaveQuickDialog by remember { mutableStateOf(false) }

    val topCategories = remember(subjects) {
        subjects.filter {
            it.parentId == null && it.enabled && it.id != "other" && it.name != "其他" && !it.name.contains("其他")
        }.sortedBy { it.sortOrder }
    }
    val currentCategoryId = selectedSubject.parentId ?: selectedSubject.id
    val subcategories = remember(subjects, currentCategoryId) {
        subjects.filter { it.parentId == currentCategoryId && it.enabled }.sortedBy { it.sortOrder }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- 1. Subtle Ambient Header Section ----
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "专注准备",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = YanjiTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(YanjiSuccess, CircleShape)
                    )
                }

                // Today's cumulative stat badge
                Surface(
                    shape = CircleShape,
                    color = YanjiPrimarySoft,
                    modifier = Modifier.clickable { onNavigateToDailyDetail(todayIso) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = YanjiPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "今日累计 ${DurationFormatter.formatHoursMinutes(todayTotalSeconds)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = YanjiPrimary
                        )
                    }
                }
            }
            Text(
                text = "设定复习目标，保持心流节律",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = YanjiTextSecondary,
                    fontSize = 14.sp
                )
            )
        }

        // ---- 2. Module 1: 复习科目选择 (2-Column Grid + Subcategories) ----
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "选择复习科目",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                color = YanjiTextPrimary
            )

            // 2-Column Grid for Top-level Subject Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                topCategories.chunked(2).forEach { rowSubjects ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowSubjects.forEach { category ->
                            val isSelected = currentCategoryId == category.id
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = if (isSelected) YanjiPrimarySoft else YanjiSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) YanjiPrimary.copy(alpha = 0.5f) else YanjiBorder
                                ),
                                shadowElevation = if (isSelected) 1.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable {
                                        val children = subjects.filter { it.parentId == category.id && it.enabled }
                                            .sortedBy { it.sortOrder }
                                        onSelectSubject(children.firstOrNull() ?: category)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) YanjiSurface else YanjiSurfaceSoft,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    imageVector = getSubjectIcon(category.id, category.name),
                                                    contentDescription = null,
                                                    tint = if (isSelected) YanjiPrimary else YanjiTextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "已选择",
                                                tint = YanjiPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        ),
                                        color = if (isSelected) YanjiPrimary else YanjiTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // If odd number in row, fill spacer
                        if (rowSubjects.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Sub-category Micro Chips
            if (subcategories.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = YanjiSurface,
                    border = BorderStroke(1.dp, YanjiBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = YanjiPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "细分小类",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    color = YanjiTextSecondary
                                )
                            }
                            Text(
                                text = "单选细分方向",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = YanjiTextTertiary
                                )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subcategories.forEach { child ->
                                val isChildSelected = selectedSubject.id == child.id
                                Surface(
                                    shape = CircleShape,
                                    color = if (isChildSelected) YanjiPrimarySoft else YanjiSurfaceSoft,
                                    modifier = Modifier.clickable { onSelectSubject(child) }
                                ) {
                                    Text(
                                        text = child.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = if (isChildSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isChildSelected) YanjiPrimary else YanjiTextSecondary
                                        ),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 3. Module 2: 计时模式 (Segmented Capsule + Panels) ----
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "计时模式",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                color = YanjiTextPrimary
            )

            // Segmented Control Capsule (正向计时 vs 倒计时模式)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = YanjiSurfaceSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Button 1: 正向计时
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (!isCountdownMode) YanjiSurface else Color.Transparent,
                        shadowElevation = if (!isCountdownMode) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                isCountdownMode = false
                                onSelectMode(FocusModes.COUNT_UP)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = if (!isCountdownMode) YanjiPrimary else YanjiTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "正向计时",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = if (!isCountdownMode) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (!isCountdownMode) YanjiPrimary else YanjiTextSecondary
                            )
                        }
                    }

                    // Button 2: 倒计时模式
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isCountdownMode) YanjiSurface else Color.Transparent,
                        shadowElevation = if (isCountdownMode) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                isCountdownMode = true
                                val matched = QUICK_DURATIONS.find { it.minutes == selectedDurationMinutes }
                                onSelectMode(matched?.mode ?: "${selectedDurationMinutes}分钟专注")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = if (isCountdownMode) YanjiPrimary else YanjiTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "倒计时模式",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = if (isCountdownMode) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isCountdownMode) YanjiPrimary else YanjiTextSecondary
                            )
                        }
                    }
                }
            }

            // Mode Details Panel
            if (isCountdownMode) {
                // Countdown Duration Picker Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = YanjiSurface,
                    border = BorderStroke(1.dp, YanjiBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "快捷时长选项",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    color = YanjiTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = "${selectedDurationMinutes} 分钟深度学习",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    color = YanjiPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // 4-Column Quick Duration Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QUICK_DURATIONS.forEach { opt ->
                                val isOptSelected = selectedDurationMinutes == opt.minutes
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isOptSelected) YanjiPrimarySoft else YanjiSurfaceSoft,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isOptSelected) YanjiPrimary.copy(alpha = 0.4f) else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedDurationMinutes = opt.minutes
                                            onSelectMode(opt.mode)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = opt.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            ),
                                            color = if (isOptSelected) YanjiPrimary else YanjiTextPrimary
                                        )
                                        Text(
                                            text = opt.subtitle,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (isOptSelected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = if (isOptSelected) YanjiPrimary else YanjiTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = YanjiTextTertiary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "达到设定时间后将温和柔声提醒",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        color = YanjiTextTertiary
                                    )
                                )
                            }

                            TextButton(
                                onClick = { showCustomDurationDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "自定义时长",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = YanjiPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Count-up Explainer Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = YanjiSurface,
                    border = BorderStroke(1.dp, YanjiBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = YanjiLavenderSoft,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AllInclusive,
                                    contentDescription = null,
                                    tint = YanjiLavender,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "无限制心流模式",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                ),
                                color = YanjiTextPrimary
                            )
                            Text(
                                text = "不预设终止闹钟，适合难题探索与深度文献研读。",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    color = YanjiTextSecondary
                                )
                            )
                        }
                    }
                }
            }
        }



        // ---- 5. CTA Module (Start Button & Footer) ----
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "进入专注心流 · ${selectedSubject.name}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Color.White
                )
            }

            Text(
                text = "准备好纸笔与书本，放空杂念，只对当下负责。",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = YanjiTextTertiary
                ),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showSaveQuickDialog = true }) {
                    Text(
                        text = "存为首页快捷 →",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = YanjiTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                TextButton(onClick = onNavigateToExam) {
                    Text(
                        text = "切换到模拟考试 →",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = YanjiPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }



    // 自定义时长 Dialog
    if (showCustomDurationDialog) {
        var customMinsText by remember { mutableStateOf("$selectedDurationMinutes") }
        val commonMins = listOf(15, 30, 45, 60, 75, 90, 120, 150, 180)

        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            title = { Text("自定义倒计时时长", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择或直接输入专注时长（分钟）：", style = MaterialTheme.typography.bodyMedium, color = YanjiTextSecondary)

                    OutlinedTextField(
                        value = customMinsText,
                        onValueChange = { customMinsText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("分钟数") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        commonMins.forEach { m ->
                            Surface(
                                shape = CircleShape,
                                color = if (customMinsText == "$m") YanjiPrimarySoft else YanjiSurfaceSoft,
                                modifier = Modifier.clickable { customMinsText = "$m" }
                            ) {
                                Text(
                                    text = "${m}m",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (customMinsText == "$m") FontWeight.Bold else FontWeight.Normal,
                                        color = if (customMinsText == "$m") YanjiPrimary else YanjiTextSecondary
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = customMinsText.toIntOrNull() ?: 45
                        val finalMins = parsed.coerceIn(1, 360)
                        selectedDurationMinutes = finalMins
                        onSelectMode("${finalMins}分钟专注")
                        showCustomDurationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("确定", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = YanjiSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 保存为首页快捷 Dialog
    if (showSaveQuickDialog) {
        var label by remember(showSaveQuickDialog) {
            mutableStateOf("开始${selectedSubject.name}${selectedMode}")
        }

        AlertDialog(
            onDismissRequest = { showSaveQuickDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAsQuickAction(label.trim())
                        showSaveQuickDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("添加到首页", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveQuickDialog = false }) {
                    Text("取消", color = YanjiTextSecondary)
                }
            },
            title = { Text("保存为首页快捷", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "这一组合会出现在首页【快捷操作】里，下次点击即按该配置直接开始计时。",
                        fontSize = 12.sp,
                        color = YanjiTextSecondary,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YanjiSurfaceSoft
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            PresetSummaryRow("科目", selectedSubject.name)
                            PresetSummaryRow("模式", selectedMode)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("快捷名称") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
        )
    }
}

/**
 * 活跃专注计时界面（核心区别：倒计时带环状进度，正向计时不带环状进度）
 */
@Composable
fun ActiveFocusContent(
    session: FocusSession,
    elapsedSeconds: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val isPaused = session.status == SessionStatus.PAUSED
    val targetSeconds = FocusModes.targetSeconds(session.mode)
    val isCountdown = targetSeconds > 0L

    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    // Breathing scale animation for gentle visual pacing
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isPaused -> YanjiWarningSoft
                    isCountdown -> YanjiPrimarySoft
                    else -> YanjiLavenderSoft
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                when {
                                    isPaused -> YanjiWarning
                                    isCountdown -> YanjiPrimary
                                    else -> YanjiLavender
                                },
                                CircleShape
                            )
                    )
                    Text(
                        text = when {
                            isPaused -> "已暂停"
                            isCountdown -> "倒计时进行中 · 目标 ${targetSeconds / 60}m"
                            else -> "无限制心流中 · 自由探索"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = when {
                            isPaused -> YanjiWarning
                            isCountdown -> YanjiPrimary
                            else -> YanjiLavender
                        }
                    )
                }
            }

            Text(
                text = session.subjectName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )

            if (session.note.isNotBlank()) {
                Text(
                    text = session.note,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = YanjiTextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Central Timer Dial
        if (isCountdown) {
            // ==========================================
            // 1. 倒计时模式：拥有完整的环状进度条
            // ==========================================
            val remainingSeconds = maxOf(0L, targetSeconds - elapsedSeconds)
            val progress = (elapsedSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)

            val timeDisplay = if (targetSeconds >= 3600) {
                val h = remainingSeconds / 3600
                val m = (remainingSeconds % 3600) / 60
                val s = remainingSeconds % 60
                String.format("%02d:%02d:%02d", h, m, s)
            } else {
                val m = remainingSeconds / 60
                val s = remainingSeconds % 60
                String.format("%02d:%02d", m, s)
            }

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .clickable { if (isPaused) onResume() else onPause() },
                contentAlignment = Alignment.Center
            ) {
                // Circular Progress Ring via Canvas
                Canvas(modifier = Modifier.size(240.dp)) {
                    val strokePx = 10.dp.toPx()
                    // Track circle
                    drawArc(
                        color = YanjiSurfaceSoft,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    // Progress arc (clockwise from top center -90 deg)
                    if (progress > 0f) {
                        drawArc(
                            color = YanjiPrimary,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner Surface Dial
                Surface(
                    shape = CircleShape,
                    color = YanjiSurface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, YanjiBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.size(190.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = timeDisplay,
                            fontSize = if (timeDisplay.length > 5) 32.sp else 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) YanjiTextSecondary else YanjiPrimary,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPaused) "点击恢复计时" else "剩余时间 · 已完成 ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isPaused) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isPaused) YanjiWarning else YanjiTextTertiary
                            )
                        )
                    }
                }
            }
        } else {
            // ==========================================
            // 2. 正向计时模式：不需要环状进度
            // ==========================================
            val hours = elapsedSeconds / 3600
            val mins = (elapsedSeconds % 3600) / 60
            val secs = elapsedSeconds % 60
            val timeDisplay = String.format("%02d:%02d:%02d", hours, mins, secs)

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .clickable { if (isPaused) onResume() else onPause() },
                contentAlignment = Alignment.Center
            ) {
                // Soft breathing halo (no progress ring)
                Box(
                    modifier = Modifier
                        .size(230.dp * (if (isPaused) 1.0f else pulseScale))
                        .clip(CircleShape)
                        .background(if (isPaused) YanjiSurfaceSoft else YanjiPrimarySoft.copy(alpha = 0.5f))
                )

                // Surface dial
                Surface(
                    shape = CircleShape,
                    color = YanjiSurface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, YanjiBorder),
                    modifier = Modifier.size(200.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = timeDisplay,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) YanjiTextSecondary else YanjiTextPrimary,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPaused) "点击恢复计时" else "无限制心流 · 持续积累中",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isPaused) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isPaused) YanjiWarning else YanjiTextTertiary
                            )
                        )
                    }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isPaused) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("继续专注", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = YanjiTextPrimary),
                        border = BorderStroke(1.dp, YanjiBorder)
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("暂停", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiLavender)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("结束并保存", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = { showCancelConfirmDialog = true }
            ) {
                Text("放弃本次记录", style = MaterialTheme.typography.bodyMedium, color = YanjiTextTertiary)
            }
        }
    }

    // Cancel Focus Confirmation Dialog
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = {
                Text(
                    text = "放弃本次专注？",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = YanjiTextPrimary
                )
            },
            text = {
                Text(
                    text = "放弃后本次计时时长将不予保存，是否确认放弃？",
                    fontSize = 14.sp,
                    color = YanjiTextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        onCancel()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiDanger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("确认放弃", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelConfirmDialog = false }
                ) {
                    Text("继续专注", color = YanjiTextSecondary, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
        )
    }
}

@Composable
private fun PresetSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = YanjiTextSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = YanjiTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
