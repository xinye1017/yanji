package com.example.yanji.ui.exam

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.AiAnalysis
import com.example.yanji.data.ExamSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.service.FocusTimerService
import com.example.yanji.service.TimerServiceMode
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.JuanjuanEncouragementBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExamScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = viewModel { ExamViewModel(YanjiRepository.getInstance()) }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val examSessions = state.examSessions
    val timerServiceState by FocusTimerService.timerState.collectAsStateWithLifecycle()
    var currentSubTab by remember { mutableStateOf(0) } // 0: 备考发起, 1: 走势与记录, 2: AI诊断

    // Active exam state
    var activeExamSubjectId by remember { mutableStateOf("other") }
    var activeExamName by remember { mutableStateOf<String?>(null) }
    var activeExamDurationSecs by remember { mutableLongStateOf(10800L) } // 3 hours
    var activeExamStartTime by remember { mutableLongStateOf(0L) }
    var isExamRunning by remember { mutableStateOf(false) }
    var isExamPaused by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableLongStateOf(10800L) }

    // 模考的业务状态由 ActiveSessionCoordinator 持有（进程级），不依赖本页面的 remember：
    // 切换 Tab 导致页面离开组合、Activity 重建后，都能从业务层恢复"正在考哪一场"。
    val businessSession by ActiveSessionCoordinator.active.collectAsStateWithLifecycle()

    // 模考页的 AI 诊断：展示真实的 AiAnalysis，没有就显示空状态（不编造内容）
    var latestAiAnalysis by remember { mutableStateOf(state.aiAnalyses.firstOrNull()) }
    var isAnalyzingAi by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state.aiAnalyses) {
        if (latestAiAnalysis == null) latestAiAnalysis = state.aiAnalyses.firstOrNull()
    }
    LaunchedEffect(businessSession) {
        val session = businessSession
        if (session != null && session.kind == ActiveSessionKind.EXAM && activeExamName == null) {
            activeExamSubjectId = session.subjectId
            activeExamName = session.subjectName
            activeExamDurationSecs = session.targetDurationSeconds
            activeExamStartTime = session.startedAtEpochMs
            remainingSeconds = (session.targetDurationSeconds - session.accumulatedActiveMs / 1000L)
                .coerceAtLeast(0L)
            isExamRunning = true
            isExamPaused = session.paused
        }
    }

    // Dialog for score entry
    var showScoreDialog by remember { mutableStateOf(false) }
    var pendingExamSession by remember { mutableStateOf<ExamSession?>(null) }

    val effectiveRemaining = if (timerServiceState.isRunning && timerServiceState.mode == TimerServiceMode.EXAM) {
        timerServiceState.remainingSeconds
    } else {
        remainingSeconds
    }

    // 模考完成事件同样由业务层广播：记录在 Service 结束计时的瞬间就已落库，
    // 这里只负责弹出成绩录入对话框。
    val completedExam = state.lastCompletedExam
    LaunchedEffect(completedExam) {
        val finished = completedExam ?: return@LaunchedEffect
        isExamRunning = false
        isExamPaused = false
        activeExamName = null
        pendingExamSession = finished
        showScoreDialog = true
    }

    fun dismissScoreDialog() {
        showScoreDialog = false
        pendingExamSession = null
        viewModel.acknowledgeCompletedExam()
    }

    if (isExamRunning && activeExamName != null) {
        // Immersive Exam Countdown Screen
        ImmersiveExamTimer(
            examName = activeExamName!!,
            remainingSeconds = effectiveRemaining,
            totalSeconds = activeExamDurationSecs,
            startTime = activeExamStartTime,
            isPaused = isExamPaused,
            onPauseResume = {
                if (isExamPaused) {
                    FocusTimerService.resumeTimer(context)
                    isExamPaused = false
                } else {
                    FocusTimerService.pauseTimer(context)
                    isExamPaused = true
                }
            },
            onEarlyFinish = {
                // 主动交卷 → 按实际用时完成并落库（业务层执行，不依赖本页面存活）
                FocusTimerService.completeTimer(context)
            },
            onQuit = {
                // 放弃本场模考：不产生记录
                FocusTimerService.discardTimer(context)
                viewModel.abandonExam()
                isExamRunning = false
                activeExamName = null
            }
        )
    } else {
        // Normal Exam Management Screen
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(YanjiBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = YanjiTextPrimary
                        )
                    }
                }
                Column {
                    Text(
                        text = "模拟考试",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                    Text(
                        text = "全真严格计时 · 考后复盘归档与AI诊断",
                        fontSize = 13.sp,
                        color = YanjiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Sub-tabs
            PrimaryTabRow(
                selectedTabIndex = currentSubTab,
                containerColor = YanjiSurfaceSoft,
                contentColor = YanjiPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                indicator = {}
            ) {
                listOf("发起模考", "成绩走势", "AI深度诊断").forEachIndexed { index, title ->
                    val isSelected = currentSubTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { currentSubTab = index },
                        text = {
                            Text(
                                text = title,
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

            Spacer(modifier = Modifier.height(32.dp))

            when (currentSubTab) {
                0 -> {
                    // 1. Preset exams
                    ExamPresetsList(
                        onStartExam = { subjectId, name, durationSecs ->
                            activeExamSubjectId = subjectId
                            activeExamName = name
                            activeExamDurationSecs = durationSecs
                            remainingSeconds = durationSecs
                            activeExamStartTime = System.currentTimeMillis()
                            isExamRunning = true
                            isExamPaused = false
                            // 先登记业务会话，再启动前台计时：完成时的落库由业务层负责。
                            // 专注与模考互斥，已有计时在跑时拒绝启动并回滚本地 UI 状态。
                            val session = viewModel.startExamSession(subjectId, name, durationSecs)
                            if (session == null) {
                                isExamRunning = false
                                activeExamName = null
                                Toast.makeText(
                                    context,
                                    "已有计时在进行中，请先结束当前的专注或模考",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@ExamPresetsList
                            }
                            FocusTimerService.startExam(context, session.id, name, durationSecs)
                        }
                    )
                }
                1 -> {
                    // 2. Score trend & history
                    ExamScoreTrendSection(examSessions = examSessions)
                }
                2 -> {
                    // 3. AI Exam Diagnosis —— 只渲染真实产出的诊断报告
                    ExamAiDiagnosisSection(
                        analysis = latestAiAnalysis,
                        isAnalyzing = isAnalyzingAi,
                        onGenerate = {
                            coroutineScope.launch {
                                isAnalyzingAi = true
                                try {
                                    latestAiAnalysis = viewModel.generateAnalysis(7)
                                } finally {
                                    isAnalyzingAi = false
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Score Entry Dialog
    if (showScoreDialog && pendingExamSession != null) {
        var scoreInput by remember { mutableStateOf("") }
        var noteInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { dismissScoreDialog() },
            confirmButton = {
                Button(
                    onClick = {
                        val scoreVal = scoreInput.toDoubleOrNull()
                        // 模考记录在计时结束的那一刻就已经落库，这里只是补写成绩与复盘。
                        viewModel.saveExamResult(pendingExamSession!!.copy(score = scoreVal, note = noteInput))
                        dismissScoreDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("保存成绩与复盘", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissScoreDialog() }) {
                    Text("暂不录入分数", color = YanjiTextSecondary)
                }
            },
            title = {
                Text("模拟考试结束 · 成绩录入", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        text = "考试科目：${pendingExamSession?.subjectName}",
                        fontSize = 14.sp,
                        color = YanjiTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "实际用时：${(pendingExamSession?.actualDurationSeconds ?: 0) / 60} 分钟",
                        fontSize = 13.sp,
                        color = YanjiTextTertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = scoreInput,
                        onValueChange = { scoreInput = it },
                        label = { Text("卷面得分 (满分 150/100)") },
                        placeholder = { Text("例如 128") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("答题复盘与失分点") },
                        placeholder = { Text("例如：选择题全对，证明题构造辅助函数耗时过长...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
        )
    }
}

@Composable
fun ExamPresetsList(
    onStartExam: (String, String, Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ExamCard(
            title = "数学一 全真模拟套卷",
            durationMins = 180,
            fullScore = 150,
            recommendedTime = "08:30 - 11:30",
            accentColor = SubjectMath,
            onStart = { onStartExam("math", "数学一 全真模拟", 10800L) }
        )

        ExamCard(
            title = "408 计算机学科专业基础",
            durationMins = 180,
            fullScore = 150,
            recommendedTime = "14:00 - 17:00",
            accentColor = SubjectMajor,
            onStart = { onStartExam("major", "408专业课 全真模拟", 10800L) }
        )

        ExamCard(
            title = "英语一 全真模考",
            durationMins = 180,
            fullScore = 100,
            recommendedTime = "14:00 - 17:00",
            accentColor = SubjectEnglish,
            onStart = { onStartExam("english", "英语一 模拟考试", 10800L) }
        )

        ExamCard(
            title = "思想政治理论 模考",
            durationMins = 180,
            fullScore = 100,
            recommendedTime = "08:30 - 11:30",
            accentColor = SubjectPolitics,
            onStart = { onStartExam("politics", "思想政治理论 模考", 10800L) }
        )

        ExamCard(
            title = "自定义专项模拟 (60分钟)",
            durationMins = 60,
            fullScore = 50,
            recommendedTime = "限时小题突破",
            accentColor = YanjiLavender,
            onStart = { onStartExam("other", "小题限时训练", 3600L) }
        )
    }
}

@Composable
fun ExamCard(
    title: String,
    durationMins: Int,
    fullScore: Int,
    recommendedTime: String,
    accentColor: Color,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = YanjiSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = YanjiTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$durationMins 分钟 · 满分 $fullScore",
                        fontSize = 12.sp,
                        color = YanjiTextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = recommendedTime,
                        fontSize = 12.sp,
                        color = YanjiTextTertiary
                    )
                }
            }

            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("开始考试", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ImmersiveExamTimer(
    examName: String,
    remainingSeconds: Long,
    totalSeconds: Long,
    startTime: Long,
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onEarlyFinish: () -> Unit,
    onQuit: () -> Unit
) {
    val hours = remainingSeconds / 3600
    val mins = (remainingSeconds % 3600) / 60
    val secs = remainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d:%02d", hours, mins, secs)

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startStr = remember { timeFormat.format(Date(startTime)) }
    val endStr = remember { timeFormat.format(Date(startTime + totalSeconds * 1000L)) }

    val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1L)).coerceIn(0f, 1f)

    // DESIGN.md: 倒计时禁红（红仅用于破坏性操作）；临界（剩余<15分钟）最多 subtle amber，且不闪烁
    val isCritical = remainingSeconds < 900

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isPaused) YanjiWarningSoft else YanjiLavenderSoft)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isPaused) "考试已暂停" else "全真模拟进行中",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused) YanjiWarning else YanjiLavender
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = examName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "考试时间：$startStr - $endStr · 请专心答卷",
                fontSize = 13.sp,
                color = YanjiTextSecondary
            )
        }

        // Center Countdown
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "剩余考试时间",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = YanjiTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = timeFormatted,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCritical) YanjiWarning else YanjiPrimary,
                    letterSpacing = (-1).sp
                )

                if (isCritical) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiWarningSoft)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "剩余不足 15 分钟 · 请安排收尾检查",
                            fontSize = 12.sp,
                            color = YanjiWarning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = YanjiPrimary,
                    trackColor = YanjiPrimarySoft
                )
            }
        }

        // Bottom Operations
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPaused) "继续考试" else "暂停", fontSize = 15.sp)
                }

                Button(
                    onClick = onEarlyFinish,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("提前交卷", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onQuit) {
                Text("退出考试", fontSize = 13.sp, color = YanjiTextTertiary)
            }
        }
    }
}

@Composable
fun ExamScoreTrendSection(examSessions: List<ExamSession>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Trend Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "模考均分走势",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YanjiTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "近 4 次模拟全真成绩统计",
                    fontSize = 12.sp,
                    color = YanjiTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 真实数据源：examSessions（按开始时间倒序），取最近 4 次已录入成绩的模考
                val scoredSessions = examSessions.filter { it.score != null }
                val recentScored = scoredSessions.take(4).reversed()
                val numberingOffset = scoredSessions.size - recentScored.size

                if (recentScored.isEmpty()) {
                    // 空状态：暂无已录入成绩的模考
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSurfaceSoft)
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无模考成绩，完成模考并录入分数后展示走势",
                            fontSize = 12.sp,
                            color = YanjiTextTertiary
                        )
                    }
                } else {
                    // Score trend canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        // 按各自满分归一化到 150 分制，保证跨科目可比
                        val scores = recentScored.map { s ->
                            ((s.score ?: 0.0) / s.maxScore.coerceAtLeast(1.0) * 150.0).toFloat()
                        }
                        val points = scores.mapIndexed { index, sc ->
                            val x = if (scores.size == 1) width / 2f
                            else width * (index.toFloat() / (scores.size - 1))
                            val y = height - (sc / 150f * height * 0.8f + 10f)
                            Offset(x, y)
                        }

                        // Draw connecting lines
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = YanjiPrimary,
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Draw points
                        points.forEach { pt ->
                            drawCircle(color = YanjiPrimary, radius = 5.dp.toPx(), center = pt)
                            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = pt)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        recentScored.forEachIndexed { index, s ->
                            val isLatest = index == recentScored.lastIndex
                            val scoreValue = s.score ?: 0.0
                            val scoreText = if (scoreValue % 1.0 == 0.0) {
                                "${scoreValue.toInt()}"
                            } else {
                                "$scoreValue"
                            }
                            Text(
                                text = "第${numberingOffset + index + 1}次: ${scoreText}分",
                                fontSize = 12.sp,
                                fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLatest) YanjiPrimary else YanjiTextTertiary
                            )
                        }
                    }
                }
            }
        }

        // Exam History List
        Text(
            text = "模考历史归档",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = YanjiTextPrimary
        )

        examSessions.forEach { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text(
                            text = session.subjectName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )

                        if (session.score != null) {
                            Text(
                                text = "${session.score.toInt()} 分",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = YanjiPrimary
                            )
                        } else {
                            Text("未录入成绩", fontSize = 12.sp, color = YanjiTextTertiary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "实际用时：${session.actualDurationSeconds / 60} 分钟 · 满分 ${session.maxScore.toInt()}",
                        fontSize = 12.sp,
                        color = YanjiTextSecondary
                    )

                    if (session.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(YanjiSurfaceSoft)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = session.note,
                                fontSize = 12.sp,
                                color = YanjiTextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模考页的「AI 深度诊断」。
 *
 * 此前这里渲染的是**写死的示例报告**（"数学一由 112 分上升至 126 分"、"关键提分攻坚建议"
 * 三条固定文案），却以"基于近 4 次全真模考答卷与失分点生成"的措辞呈现给用户——
 * 属于把假数据当真实结论展示，与 `优化方案.md`「不要显示假数据」直接冲突。
 *
 * 现在只渲染 [AiAnalysis]（由 `YanjiRepository.generateAiAnalysis` 基于真实的
 * 专注 / 模考 / 日记数据产出，数据不足时会明确说明不足），没有报告时给空状态。
 */
@Composable
fun ExamAiDiagnosisSection(
    analysis: AiAnalysis?,
    isAnalyzing: Boolean,
    onGenerate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JuanjuanAvatar(size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI 模考深度诊断报告",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextPrimary
                        )
                        Text(
                            text = if (analysis != null) {
                                "${analysis.provider} · ${analysis.model} · ${analysis.periodStart} ~ ${analysis.periodEnd}"
                            } else {
                                "基于你真实的专注、模考与日记记录生成"
                            },
                            fontSize = 12.sp,
                            color = YanjiTextSecondary
                        )
                    }
                    Button(
                        onClick = onGenerate,
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("分析中...", fontSize = 12.sp)
                        } else {
                            Text(
                                if (analysis == null) "生成诊断" else "重新生成",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (analysis == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSurfaceSoft)
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAnalyzing) {
                                "正在生成诊断报告…"
                            } else {
                                "尚未生成诊断报告。\n点击右上角「生成诊断」，卷卷会只依据你已有的真实记录给出结论；记录不足时会直接说明数据缺口，不会编造分数或趋势。"
                            },
                            fontSize = 12.sp,
                            color = YanjiTextTertiary,
                            lineHeight = 18.sp
                        )
                    }
                    return@Column
                }

                if (analysis.overview.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiSurfaceBlue)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "【综合概览】${analysis.overview}",
                            fontSize = 13.sp,
                            color = YanjiTextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }

                if (analysis.strengths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("已确认的优势", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    analysis.strengths.forEach { item ->
                        BulletLine(text = item, color = YanjiSuccess)
                    }
                }

                if (analysis.weaknesses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("待改进 / 数据缺口", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    analysis.weaknesses.forEach { item ->
                        BulletLine(text = item, color = YanjiWarning)
                    }
                }

                if (analysis.trendAnalysis.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("趋势判断", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(analysis.trendAnalysis, fontSize = 12.sp, color = YanjiTextSecondary, lineHeight = 18.sp)
                }

                if (analysis.suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("未来 3 天计划", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = YanjiTextPrimary)
                    analysis.suggestions.forEachIndexed { index, item ->
                        DiagnosisItem(
                            tag = "第 ${index + 1} 天",
                            title = "行动建议",
                            desc = item,
                            color = YanjiPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletLine(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontSize = 12.sp, color = YanjiTextSecondary, lineHeight = 18.sp)
    }
}

@Composable
fun DiagnosisItem(
    tag: String,
    title: String,
    desc: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(text = tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = YanjiTextPrimary)
            Text(text = desc, fontSize = 12.sp, color = YanjiTextSecondary, lineHeight = 18.sp)
        }
    }
}
