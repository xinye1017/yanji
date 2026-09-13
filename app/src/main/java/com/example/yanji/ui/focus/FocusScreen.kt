package com.example.yanji.ui.focus

import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.Subject
import com.example.yanji.data.timer.ActiveFocusState
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.service.FocusTimerService
import kotlinx.coroutines.launch

@Composable
fun FocusScreen(
    onNavigateToExam: () -> Unit,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToFocusDetail: (sessionId: String) -> Unit = {},
    quickStartPreset: QuickStartPreset? = null,
    onQuickStartConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FocusViewModel = yanjiViewModel { container ->
        FocusViewModel(container.repository, container.statisticsRepository)
    }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subjects = state.subjects
    val activeSession = state.activeSession
    val liveState by FocusTimerService.liveState.collectAsStateWithLifecycle()
    val tickingElapsed = FocusTimerService.elapsedSecondsForUi.collectAsStateWithLifecycle()
    val restoredElapsed = remember(activeSession?.id, activeSession?.durationSeconds) {
        mutableStateOf(activeSession?.durationSeconds ?: 0L)
    }
    val elapsedSeconds: State<Long> =
        if (liveState is ActiveFocusState) tickingElapsed else restoredElapsed

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
    val screenScope = rememberCoroutineScope()

    fun launchFocus(subject: Subject, mode: String, note: String) {
        screenScope.launch {
            val session = viewModel.startFocus(
                subjectId = subject.id,
                subjectName = subject.name,
                note = note,
                mode = mode
            )
            if (session == null) {
                Toast.makeText(
                    context,
                    "无法安全启动：已有计时或本机存储暂不可用",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            FocusTimerService.startFocus(context, session.id, subject.name, FocusModes.targetSeconds(mode))
        }
    }

    fun dismissSummary() {
        showSummaryDialog = false
        viewModel.acknowledgeCompletedFocus()
    }

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

    val completedFocus = state.lastCompletedFocus
    LaunchedEffect(completedFocus) {
        if (completedFocus != null) {
            lastFinishedSession = completedFocus
            showSummaryDialog = true
        }
    }

    if (activeSession != null) {
        ActiveFocusContent(
            session = activeSession,
            elapsedSeconds = elapsedSeconds,
            onPause = {
                FocusTimerService.pauseTimer(context)
                viewModel.pauseFocus(elapsedSeconds.value)
            },
            onResume = {
                FocusTimerService.resumeTimer(context)
                viewModel.resumeFocus()
            },
            onFinish = {
                if (elapsedSeconds.value < 60L) {
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
        QuietFocusSetupContent(
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

    if (showSummaryDialog && lastFinishedSession != null) {
        FocusSummaryDialog(
            session = lastFinishedSession!!,
            todayFocusSeconds = state.todayFocusSeconds,
            onDismiss = { dismissSummary() }
        )
    }
}
