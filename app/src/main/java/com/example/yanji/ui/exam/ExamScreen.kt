package com.example.yanji.ui.exam

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.AiAnalysis
import com.example.yanji.data.ExamSession
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.service.FocusTimerService
import com.example.yanji.service.TimerServiceMode
import com.example.yanji.theme.*
import kotlinx.coroutines.launch

@Composable
fun ExamScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ExamViewModel = yanjiViewModel { container ->
        ExamViewModel(container.repository)
    }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val examSessions = state.examSessions
    val timerServiceState by FocusTimerService.timerState.collectAsStateWithLifecycle()
    var currentSubTab by remember { mutableIntStateOf(0) } // 0: 备考发起, 1: 走势与记录, 2: AI诊断

    // Active exam state
    var activeExamSubjectId by remember { mutableStateOf("other") }
    var activeExamName by remember { mutableStateOf<String?>(null) }
    var activeExamDurationSecs by remember { mutableLongStateOf(10800L) } // 3 hours
    var activeExamStartTime by remember { mutableLongStateOf(0L) }
    var isExamRunning by remember { mutableStateOf(false) }
    var isExamPaused by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableLongStateOf(10800L) }

    // 模考的业务状态由 ActiveSessionCoordinator 持有（进程级），不依赖本页面的 remember：
    val businessSession by ActiveSessionCoordinator.active.collectAsStateWithLifecycle()

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
                FocusTimerService.completeTimer(context)
            },
            onQuit = {
                FocusTimerService.discardTimer(context)
                viewModel.abandonExam()
                isExamRunning = false
                activeExamName = null
            }
        )
    } else {
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
                    ExamPresetsList(
                        onStartExam = { subjectId, name, durationSecs ->
                            coroutineScope.launch {
                                val session = viewModel.startExamSession(subjectId, name, durationSecs)
                                if (session == null) {
                                    Toast.makeText(
                                        context,
                                        "无法安全启动：已有计时或本机存储暂不可用",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                activeExamSubjectId = subjectId
                                activeExamName = name
                                activeExamDurationSecs = durationSecs
                                remainingSeconds = durationSecs
                                activeExamStartTime = session.startTime
                                isExamRunning = true
                                isExamPaused = false
                                FocusTimerService.startExam(context, session.id, name, durationSecs)
                            }
                        }
                    )
                }
                1 -> {
                    ExamScoreTrendSection(examSessions = examSessions)
                }
                2 -> {
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
        ExamScoreDialog(
            session = pendingExamSession!!,
            onDismiss = { dismissScoreDialog() },
            onConfirm = { scoreVal, noteInput ->
                viewModel.saveExamResult(pendingExamSession!!.copy(score = scoreVal, note = noteInput))
                dismissScoreDialog()
            }
        )
    }
}
