package com.example.yanji.ui.focus

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.R
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.Subject
import com.example.yanji.data.timer.ActiveFocusState
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.service.FocusTimerService
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.ui.components.YanjiPrimaryButton
import kotlinx.coroutines.launch

/**
 * `Manifest.permission.POST_NOTIFICATIONS` 是**编译期内联**的 String 常量：在 minSdk 24 上
 * 引用它不会触发任何运行时 API 调用，只有 API 33+ 才会真正弹出系统权限框。
 * Lint 的 `InlinedApi` 只针对"常量所需的 API 高于 minSdk"这一点，故在此按需抑制；
 * 收敛成私有常量而不是在 Composable 上做函数级抑制，是为了把抑制范围限制到这一处引用。
 */
@SuppressLint("InlinedApi")
private const val POST_NOTIFICATIONS_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

@Composable
fun FocusScreen(
    onNavigateToExam: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToFocusDetail: (sessionId: String) -> Unit = {},
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
        mutableLongStateOf(activeSession?.durationSeconds ?: 0L)
    }
    val elapsedSeconds: State<Long> =
        if (liveState is ActiveFocusState) tickingElapsed else restoredElapsed

    var selectedSubjectId by rememberSaveable { mutableStateOf("math_advanced") }
    // 学科现在可被用户删除，所以不能写死回落值：优先用之前的选择，其次优先默认学科，
    // 再退到第一个可选学科。全都没有时（用户删光了学科）才给一个占位，交由界面引导去创建。
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
        ?: subjects.firstOrNull { it.id == "math_advanced" }
        ?: subjects.firstOrNull { it.parentId != null }
        ?: subjects.firstOrNull()
        ?: Subject("math_advanced", "高等数学", "#356AE6", parentId = "math")
    var selectedMode by rememberSaveable { mutableStateOf(FocusModes.POMODORO_25) }
    var noteText by rememberSaveable { mutableStateOf("") }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showManualLogDialog by rememberSaveable { mutableStateOf(false) }
    var lastFinishedSession by remember { mutableStateOf<FocusSession?>(null) }
    val screenScope = rememberCoroutineScope()

    var pendingFocusSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFocusSubjectName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFocusMode by rememberSaveable { mutableStateOf(FocusModes.COUNT_UP) }
    var pendingFocusNote by rememberSaveable { mutableStateOf("") }
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }

    fun doLaunchFocus(subject: Subject, mode: String, note: String) {
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

    fun clearPendingFocusLaunch() {
        pendingFocusSubjectId = null
        pendingFocusSubjectName = null
        pendingFocusMode = FocusModes.COUNT_UP
        pendingFocusNote = ""
    }

    fun runPendingFocusLaunch() {
        val subjectId = pendingFocusSubjectId ?: return
        val subject = subjects.firstOrNull { it.id == subjectId }
            ?: Subject(
                id = subjectId,
                name = pendingFocusSubjectName.orEmpty().ifBlank { subjectId },
                colorHex = "#356AE6"
            )
        doLaunchFocus(subject, pendingFocusMode, pendingFocusNote)
        clearPendingFocusLaunch()
    }

    val permissionDeniedToast = stringResource(R.string.notification_permission_denied_toast)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, permissionDeniedToast, Toast.LENGTH_SHORT).show()
        }
        runPendingFocusLaunch()
    }

    fun launchFocus(subject: Subject, mode: String, note: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFocusSubjectId = subject.id
            pendingFocusSubjectName = subject.name
            pendingFocusMode = mode
            pendingFocusNote = note
            showNotificationRationale = true
        } else {
            doLaunchFocus(subject, mode, note)
        }
    }

    fun dismissSummary() {
        showSummaryDialog = false
        viewModel.acknowledgeCompletedFocus()
    }

    val completedFocus = state.lastCompletedFocus
    LaunchedEffect(completedFocus) {
        if (completedFocus != null) {
            lastFinishedSession = completedFocus
            showSummaryDialog = true
        }
    }

    if (activeSession != null) {
        val isTimerPaused = if (liveState is ActiveFocusState) liveState.isPaused else (activeSession.status == SessionStatus.PAUSED)
        ActiveFocusContent(
            session = activeSession,
            elapsedSeconds = elapsedSeconds,
            isPaused = isTimerPaused,
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
            onSelectSubject = { selectedSubjectId = it.id },
            selectedMode = selectedMode,
            onSelectMode = { selectedMode = it },
            todayTotalSeconds = state.todayTotalSeconds,
            onStart = { launchFocus(selectedSubject, selectedMode, "") },
            onNavigateToExam = onNavigateToExam,
            onNavigateToDailyDetail = onNavigateToDailyDetail,
            onManualLogClick = { showManualLogDialog = true }
        )
    }

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = {
                showNotificationRationale = false
                runPendingFocusLaunch()
            },
            shape = RoundedCornerShape(YanjiRadius.DialogRadius),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.notification_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.notification_permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                YanjiPrimaryButton(
                    text = stringResource(R.string.notification_permission_grant),
                    onClick = {
                        showNotificationRationale = false
                        notificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)
                    }
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        Toast.makeText(
                            context,
                            permissionDeniedToast,
                            Toast.LENGTH_SHORT
                        ).show()
                        runPendingFocusLaunch()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.notification_permission_dismiss),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (showSummaryDialog && lastFinishedSession != null) {
        FocusSummaryDialog(
            session = lastFinishedSession!!,
            todayFocusSeconds = state.todayFocusSeconds,
            onDismiss = { dismissSummary() }
        )
    }

    if (showManualLogDialog) {
        ManualFocusLogDialog(
            subjects = subjects,
            initialSubjectId = selectedSubjectId,
            onDismissRequest = { showManualLogDialog = false },
            onConfirm = { subId, subName, startTime, endTime, note ->
                screenScope.launch {
                    val result = viewModel.addManualFocusSession(
                        subjectId = subId,
                        subjectName = subName,
                        startTime = startTime,
                        endTime = endTime,
                        note = note
                    )
                    if (result.isSuccess) {
                        val session = result.getOrNull()
                        val durationText = com.example.yanji.data.DurationFormatter.formatHoursMinutes(session?.durationSeconds ?: 0L)
                        Toast.makeText(context, "已成功补记 ${subName} ${durationText} 专注记录", Toast.LENGTH_SHORT).show()
                        showManualLogDialog = false
                    } else {
                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
