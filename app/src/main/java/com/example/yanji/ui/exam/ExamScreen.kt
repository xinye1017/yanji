package com.example.yanji.ui.exam

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.yanji.ui.components.YanjiDetailTopBar
import com.example.yanji.ui.components.YanjiPageHeader
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
    var currentSubTab by rememberSaveable { mutableIntStateOf(0) } // 0: 备考发起, 1: 走势与记录, 2: AI诊断

    // 模考业务状态只来自可持久化的 ActiveSessionCoordinator，页面不再维护第二套计时真相。
    val businessSession by ActiveSessionCoordinator.active.collectAsStateWithLifecycle()
    val activeExam = businessSession?.takeIf { it.kind == ActiveSessionKind.EXAM }
    val isExamRunning = activeExam != null
    val activeExamName = activeExam?.subjectName
    val activeExamDurationSecs = activeExam?.targetDurationSeconds ?: 10_800L
    val activeExamStartTime = activeExam?.startedAtEpochMs ?: 0L
    val isExamPaused = activeExam?.paused == true

    var latestAiAnalysis by remember { mutableStateOf(state.aiAnalyses.firstOrNull()) }
    // The request lives in the composition scope. Never restore a stale "loading" flag after the
    // request was cancelled by Activity/process recreation.
    var isAnalyzingAi by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state.aiAnalyses) {
        if (latestAiAnalysis == null) latestAiAnalysis = state.aiAnalyses.firstOrNull()
    }
    // Dialog for score entry
    var showScoreDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExamSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingExamSession = pendingExamSessionId?.let { id -> examSessions.find { it.id == id } }

    val effectiveRemaining = if (timerServiceState.isRunning && timerServiceState.mode == TimerServiceMode.EXAM) {
        timerServiceState.remainingSeconds
    } else {
        (activeExamDurationSecs - (activeExam?.accumulatedActiveMs ?: 0L) / 1000L).coerceAtLeast(0L)
    }

    val completedExam = state.lastCompletedExam
    LaunchedEffect(completedExam) {
        val finished = completedExam ?: return@LaunchedEffect
        pendingExamSessionId = finished.id
        showScoreDialog = true
    }

    fun dismissScoreDialog() {
        showScoreDialog = false
        pendingExamSessionId = null
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
                } else {
                    FocusTimerService.pauseTimer(context)
                }
            },
            onEarlyFinish = {
                FocusTimerService.completeTimer(context)
            },
            onQuit = {
                FocusTimerService.discardTimer(context)
                viewModel.abandonExam()
            }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = YanjiSpacing.PageHorizontalPadding, vertical = 16.dp)
        ) {
            if (onBack != null) {
                YanjiDetailTopBar(
                    title = "模拟考试",
                    subtitle = "全真严格计时 · 考后复盘归档与AI诊断",
                    onBack = onBack
                )
            } else {
                YanjiPageHeader(
                    title = "模拟考试",
                    subtitle = "全真严格计时 · 考后复盘归档与AI诊断"
                )
            }

            Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))

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
            session = pendingExamSession,
            onDismiss = { dismissScoreDialog() },
            onConfirm = { scoreVal, noteInput ->
                viewModel.saveExamResult(pendingExamSession.copy(score = scoreVal, note = noteInput))
                dismissScoreDialog()
            }
        )
    }
}
