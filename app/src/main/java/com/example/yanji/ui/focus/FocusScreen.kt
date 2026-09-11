package com.example.yanji.ui.focus

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.yanji.ui.components.YanjiCard as Card
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.*
import com.adamglin.phosphoricons.regular.*
import com.example.yanji.data.FocusSession
import com.example.yanji.data.FocusModes
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.Subject
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.DurationFormatter
import com.example.yanji.service.FocusTimerService
import com.example.yanji.service.TimerServiceMode
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.JuanjuanEncouragementBanner
import com.example.yanji.ui.detail.DailySessionRowCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    onNavigateToExam: () -> Unit,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    onNavigateToFocusDetail: (sessionId: String) -> Unit = {},
    quickStartPreset: QuickStartPreset? = null,
    onQuickStartConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    repo: YanjiRepository = YanjiRepository.getInstance()
) {
    val context = LocalContext.current
    val subjects by repo.subjects.collectAsStateWithLifecycle()
    val activeSession by repo.activeFocus.collectAsStateWithLifecycle()
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

    // 统一的启动入口：设置页手动开始与首页快捷启动共用同一段逻辑
    fun launchFocus(subject: Subject, mode: String, note: String) {
        // 先把会话登记到业务层，再把 sessionId 交给前台 Service：
        // 这样倒计时结束时的落库不再依赖本页面是否还在组合。
        val session = repo.startFocus(
            subjectId = subject.id,
            subjectName = subject.name,
            note = note,
            mode = mode
        )
        if (session == null) {
            // 专注与模考互斥：已有计时在跑时拒绝再次启动
            Toast.makeText(context, "已有计时在进行中，请先结束当前的专注或模考", Toast.LENGTH_SHORT).show()
            return
        }
        FocusTimerService.startFocus(context, session.id, subject.name, FocusModes.targetSeconds(mode))
    }

    fun dismissSummary() {
        showSummaryDialog = false
        repo.acknowledgeCompletedFocus()
    }

    // 首页快捷操作：进页面即按预设组合开始计时，随后立刻清空 pending 值避免重复触发
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

    // Derive live elapsed seconds strictly from Foreground Service timer or activeSession fallback.
    // This prevents any time-stealing when paused or resetting to 0 when switching tabs.
    val currentElapsedSeconds = if (timerServiceState.isRunning && timerServiceState.mode == TimerServiceMode.FOCUS) {
        timerServiceState.elapsedSeconds
    } else {
        activeSession?.durationSeconds ?: 0L
    }

    // 完成事件由业务层广播（前台 Service → ActiveSessionCoordinator → Repository）。
    // 页面只做展示：即便本页面当时没有组合，记录也已经写入数据库。
    val completedFocus by repo.lastCompletedFocus.collectAsStateWithLifecycle()
    LaunchedEffect(completedFocus) {
        if (completedFocus != null) {
            lastFinishedSession = completedFocus
            showSummaryDialog = true
        }
    }

    if (activeSession != null) {
        // Active Focusing Screen
        ActiveFocusContent(
            session = activeSession!!,
            elapsedSeconds = currentElapsedSeconds,
            onPause = {
                FocusTimerService.pauseTimer(context)
                repo.pauseFocus(currentElapsedSeconds)
            },
            onResume = {
                FocusTimerService.resumeTimer(context)
                repo.resumeFocus()
            },
            onFinish = {
                // 正向计时时间小于1分钟默认不予保存
                if (currentElapsedSeconds < 60L) {
                    Toast.makeText(context, "专注时间不足 1 分钟，本次记录不予保存", Toast.LENGTH_SHORT).show()
                    FocusTimerService.discardTimer(context)
                    repo.cancelFocus()
                } else {
                    // 结束语义：主动结束 → 按实际时长保存（由业务层执行）
                    FocusTimerService.completeTimer(context)
                }
            },
            onCancel = {
                FocusTimerService.discardTimer(context)
                repo.cancelFocus()
            }
        )
    } else {
        // Setup Screen
        FocusSetupContent(
            subjects = subjects,
            selectedSubject = selectedSubject,
            onSelectSubject = { selectedSubject = it },
            selectedMode = selectedMode,
            onSelectMode = { selectedMode = it },
            noteText = noteText,
            onNoteChange = { noteText = it },
            todayFocusSeconds = repo.getTodayFocusDurationSeconds(),
            onStart = { launchFocus(selectedSubject, selectedMode, noteText) },
            onSaveAsQuickAction = { label ->
                repo.addQuickStartPreset(
                    QuickStartPreset(
                        label = label.ifBlank { "开始${selectedSubject.name}${selectedMode}" },
                        subjectId = selectedSubject.id,
                        subjectName = selectedSubject.name,
                        mode = selectedMode,
                        note = noteText
                    )
                )
                Toast.makeText(context, "已添加到首页快捷操作", Toast.LENGTH_SHORT).show()
            },
            onNavigateToExam = onNavigateToExam,
            onNavigateToDailyDetail = onNavigateToDailyDetail,
            onNavigateToFocusDetail = onNavigateToFocusDetail
        )
    }

    // Summary Dialog when session completes
    if (showSummaryDialog && lastFinishedSession != null) {
        val totalSecs = repo.getTodayFocusDurationSeconds()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusSetupContent(
    subjects: List<Subject>,
    selectedSubject: Subject,
    onSelectSubject: (Subject) -> Unit,
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    todayFocusSeconds: Long = 0L,
    onStart: () -> Unit,
    onSaveAsQuickAction: (String) -> Unit = {},
    onNavigateToExam: () -> Unit,
    onNavigateToDailyDetail: (String) -> Unit = {},
    onNavigateToFocusDetail: (String) -> Unit = {}
) {
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val statsRepo = remember { StudyStatisticsRepository.getInstance() }
    val dailySummary by statsRepo.getDailyStudySummaryFlow(todayIso).collectAsStateWithLifecycle(
        initialValue = statsRepo.getDailyStudySummary(todayIso)
    )

    var showSaveQuickDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

        // Unified Focus Setup Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, YanjiBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Card Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "专注计时",
                        style = MaterialTheme.typography.headlineMedium,
                        color = YanjiTextPrimary
                    )

                    if (dailySummary.totalDurationSeconds > 0L) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(YanjiPrimarySoft)
                                .clickable { onNavigateToDailyDetail(todayIso) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "今日已专注 ${DurationFormatter.formatHoursMinutes(dailySummary.totalDurationSeconds)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = YanjiPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. 大类与子类选择
                Text(
                    text = "选择大类",
                    style = MaterialTheme.typography.labelLarge,
                    color = YanjiTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = subjects.filter { it.parentId == null && it.enabled }.sortedBy { it.sortOrder }
                    val selectedCategoryId = selectedSubject.parentId ?: selectedSubject.id
                    categories.forEach { category ->
                        val isSelected = category.id == selectedCategoryId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) YanjiPrimary else YanjiSurfaceSoft
                                )
                                .clickable {
                                    val children = subjects.filter { it.parentId == category.id && it.enabled }
                                        .sortedBy { it.sortOrder }
                                    onSelectSubject(children.firstOrNull() ?: category)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) YanjiOnPrimary else YanjiTextSecondary
                            )
                        }
                    }
                }

                val selectedCategoryId = selectedSubject.parentId ?: selectedSubject.id
                val subcategories = subjects.filter { it.parentId == selectedCategoryId && it.enabled }
                    .sortedBy { it.sortOrder }
                if (subcategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "选择子类",
                        style = MaterialTheme.typography.labelLarge,
                        color = YanjiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subcategories.forEach { subject ->
                            val isSelected = subject.id == selectedSubject.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) YanjiPrimarySoft else YanjiSurfaceSoft)
                                    .clickable { onSelectSubject(subject) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) YanjiPrimaryStrong else YanjiTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 计时模式 (FlowRow)
                Text(
                    text = "计时模式",
                    style = MaterialTheme.typography.labelLarge,
                    color = YanjiTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val modes = FocusModes.ALL
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { mode ->
                        val isSelected = mode == selectedMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) YanjiPrimarySoft else YanjiSurfaceSoft
                                )
                                .clickable { onSelectMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) YanjiPrimary else YanjiTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 学习备注
                Text(
                    text = "学习备注 (可选)",
                    style = MaterialTheme.typography.labelLarge,
                    color = YanjiTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = onNoteChange,
                    placeholder = { Text("例如：二重积分刷题、数据结构真题代码...", style = MaterialTheme.typography.bodyMedium, color = YanjiTextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YanjiPrimary,
                        unfocusedBorderColor = YanjiBorder,
                        focusedContainerColor = YanjiSurfaceSoft,
                        unfocusedContainerColor = YanjiSurfaceSoft
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

                // 4. 开始专注按钮
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Fill.Play,
                        contentDescription = null,
                        tint = YanjiOnPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "开始专注 (${selectedSubject.name})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = YanjiOnPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. 保存为首页快捷操作
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showSaveQuickDialog = true }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = YanjiTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "把当前组合保存为首页快捷 →",
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiTextSecondary
                    )
                }

                // 6. 模拟考试入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigateToExam() }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.Timer,
                        contentDescription = null,
                        tint = YanjiPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "需要 180 分钟模考？切换到模拟考试 →",
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    // 保存当前组合为首页快捷操作
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
                            PresetSummaryRow("备注", noteText.ifBlank { "（无）" })
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("快捷名称") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YanjiPrimary,
                            unfocusedBorderColor = YanjiBorder,
                            focusedContainerColor = YanjiSurfaceSoft,
                            unfocusedContainerColor = YanjiSurfaceSoft
                        ),
                        singleLine = true
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
        )
    }
}

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
    val hours = elapsedSeconds / 3600
    val mins = (elapsedSeconds % 3600) / 60
    val secs = elapsedSeconds % 60
    val timeFormatted = String.format("%02d:%02d:%02d", hours, mins, secs)

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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isPaused) YanjiWarningSoft else YanjiPrimarySoft)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isPaused) "已暂停" else "专注进行中",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused) YanjiWarning else YanjiPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = session.subjectName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = YanjiTextPrimary
            )

            if (session.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = session.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiTextSecondary
                )
            }
        }

        // Central Breathing Clock Dial (Clickable to Pause/Resume)
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .clickable {
                    if (isPaused) {
                        onResume()
                    } else {
                        onPause()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Soft outer halo
            Box(
                modifier = Modifier
                    .size(240.dp * (if (isPaused) 1.0f else pulseScale))
                    .clip(CircleShape)
                    .background(if (isPaused) YanjiSurfaceSoft else YanjiPrimarySoft.copy(alpha = 0.6f))
            )

            // Surface dial
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(YanjiSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeFormatted,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) YanjiTextSecondary else YanjiPrimary,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPaused) "点击恢复计时" else "保持心流 · 沉静积累",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isPaused) YanjiPrimary else YanjiTextTertiary,
                        fontWeight = if (isPaused) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = YanjiTextPrimary)
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

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { showCancelConfirmDialog = true }
            ) {
                Text("放弃本次记录", style = MaterialTheme.typography.bodyMedium, color = YanjiTextTertiary)
            }
        }
    }

    // Cancel Focus Confirmation Dialog with Red Danger Button
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
