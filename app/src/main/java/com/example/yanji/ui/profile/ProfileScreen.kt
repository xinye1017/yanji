package com.example.yanji.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.backup.BackupCodec
import com.example.yanji.data.backup.BackupDecodeResult
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.ui.components.AchievementSummaryBanner
import com.example.yanji.ui.components.AiConfigDialog
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.JuanjuanAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ProfileScreen(
    onNavigateToAchievements: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel { ProfileViewModel(YanjiRepository.getInstance()) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings

    // Countdown days: compute from settings.targetExamDate (single source of truth, mirror HomeScreen)
    val daysRemaining = remember(settings.targetExamDate) {
        runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.isLenient = false
            val target = Calendar.getInstance()
            target.time = fmt.parse(settings.targetExamDate)!!
            target.set(Calendar.HOUR_OF_DAY, 0)
            target.set(Calendar.MINUTE, 0)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            ((target.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
        }.getOrNull()
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var showExamTargetDialog by remember { mutableStateOf(false) }
    var showAiConfigDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // ---------- 备份导出 / 导入 ----------
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    // 待确认的导入：先解析出摘要给用户看清�?"要覆盖什�?"，再真正落库
    var pendingImport by remember { mutableStateOf<Pair<String, BackupDecodeResult.Success>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isExporting = true
            try {
                val json = viewModel.exportBackupJson()
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                            out.flush()
                        } ?: error("无法打开目标文件")
                    }
                }
                if (written.isSuccess) {
                    Toast.makeText(context, "已导出备份（不含 AI Key�?", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        "导出失败�?${written.exceptionOrNull()?.localizedMessage ?: "未知错误"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isExporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取所选文�?")
                }
            }
            text.onSuccess { raw ->
                when (val decoded = BackupCodec.decode(raw)) {
                    is BackupDecodeResult.Failure -> {
                        importError = decoded.message
                    }
                    is BackupDecodeResult.Success -> {
                        pendingImport = raw to decoded
                    }
                }
            }.onFailure {
                importError = "读取文件失败�?${it.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun startExport() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        exportLauncher.launch("yanji-backup-$stamp.json")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

        // Profile Avatar & Goal Overview
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JuanjuanAvatar(size = 56.dp)
                Spacer(modifier = Modifier.width(YanjiSpacing.InlineGap))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "2027 考研备战�?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = YanjiTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YanjiPrimarySoft)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Local First",
                                style = MaterialTheme.typography.labelMedium,
                                color = YanjiPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(YanjiSpacing.TightGap))
                    Text(
                        text = "${settings.targetSchool} · ${settings.targetMajor}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = YanjiTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Check-in & Achievement Summary
        AchievementSummaryBanner(
            onClick = onNavigateToAchievements
        )

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        // Settings Groups
        SettingsGroupTitle("考研与备考规�?")
        Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Outlined.Flag,
                    title = "考研目标与初试日�?",
                    subtitle = if (daysRemaining != null) {
                        "${settings.targetExamDate} (倒计�? ${if (daysRemaining >= 0) daysRemaining else 0} �?)"
                    } else {
                        settings.targetExamDate
                    },
                    onClick = { showExamTargetDialog = true }
                )
                HorizontalDivider(color = YanjiDivider, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Outlined.Timer,
                    title = "每日学习目标时长",
                    subtitle = "${settings.dailyGoalHours.toInt()} 小时 / �? (最低有效阈�? 30 分钟)",
                    onClick = { showExamTargetDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        SettingsGroupTitle("AI 诊断与模型接�?")
        Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Outlined.Psychology,
                    title = "AI API 配置",
                    subtitle = "兼容 OpenAI 格式",
                    onClick = { showAiConfigDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        SettingsGroupTitle("数据存储与安�?")
        Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Outlined.CloudDownload,
                    title = "导出本地数据备份 (JSON)",
                    subtitle = if (isExporting) {
                        "正在导出�?"
                    } else {
                        "专注记录、模拟考试、日记、打卡与设置（不�? AI Key�?"
                    },
                    onClick = { if (!isExporting) startExport() }
                )
                HorizontalDivider(color = YanjiDivider, thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Outlined.CloudUpload,
                    title = "导入数据恢复",
                    subtitle = if (isImporting) {
                        "正在导入�?"
                    } else {
                        "�? JSON 备份整表恢复（会先自动留一份本机快照）"
                    },
                    onClick = { if (!isImporting) importLauncher.launch(arrayOf("application/json")) }
                )
            }
        }

        Spacer(modifier = Modifier.height(YanjiSpacing.CardGap))

        SettingsGroupTitle("关于")
        Spacer(modifier = Modifier.height(YanjiSpacing.SectionGap))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = YanjiSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "关于研迹 (Yanji) 与卷�?",
                    subtitle = "v1.0 · 安静、稳定、耐心的考研备考伴�?",
                    onClick = { showAboutDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    // Exam Target Dialog
    if (showExamTargetDialog) {
        var school by remember { mutableStateOf(settings.targetSchool) }
        var major by remember { mutableStateOf(settings.targetMajor) }
        var date by remember { mutableStateOf(settings.targetExamDate) }
        var goalH by remember { mutableFloatStateOf(settings.dailyGoalHours) }
        var showExamDatePicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showExamTargetDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSettings(
                            settings.copy(
                                targetSchool = school,
                                targetMajor = major,
                                targetExamDate = date,
                                dailyGoalHours = goalH
                            )
                        )
                        showExamTargetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("保存设置", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExamTargetDialog = false }) {
                    Text("取消", color = YanjiTextSecondary)
                }
            },
            title = { Text("考研目标设置", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = school,
                        onValueChange = { school = it },
                        label = { Text("目标院校") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = major,
                        onValueChange = { major = it },
                        label = { Text("目标专业") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedCard(
                        onClick = { showExamDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "考研初试日期",
                                    fontSize = 12.sp,
                                    color = YanjiTextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = date,
                                    fontSize = 16.sp,
                                    color = YanjiTextPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = "选择考研初试日期",
                                tint = YanjiPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("每日专注学习目标�?${goalH.toInt()} 小时", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = goalH,
                        onValueChange = { goalH = it },
                        valueRange = 4f..16f,
                        steps = 11
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
        )

        if (showExamDatePicker) {
            YanjiExamDatePickerDialog(
                initialDateMillis = parseExamDateToUtcMillis(date),
                onDismissRequest = { showExamDatePicker = false },
                onDateSelected = {
                    date = formatExamDateFromUtcMillis(it)
                    showExamDatePicker = false
                }
            )
        }
    }

    // AI Config Dialog
    if (showAiConfigDialog) {
        AiConfigDialog(
            onDismissRequest = { showAiConfigDialog = false }
        )
    }

    // ---------- 导入确认 ----------
    // 导入是「整表替捀��，属于破坏性操作：必须先把要覆盖的内容讲清楚再执行�?
    pendingImport?.let { (rawJson, decoded) ->
        val backup = decoded.backup
        val warnings = decoded.warnings
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImport = null
                        coroutineScope.launch {
                            isImporting = true
                            val result = try {
                                viewModel.importBackupJson(rawJson)
                            } catch (e: Exception) {
                                com.example.yanji.data.backup.BackupImportResult.Failure(
                                    e.localizedMessage ?: "未知错误"
                                )
                            } finally {
                                isImporting = false
                            }
                            when (result) {
                                is com.example.yanji.data.backup.BackupImportResult.Success -> {
                                    val tail = result.snapshotPath?.let { "\n导入前快照：$it" }.orEmpty()
                                    Toast.makeText(
                                        context,
                                        "已恢复：${backup.countsSummary()}$tail",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.example.yanji.data.backup.BackupImportResult.Failure ->
                                    importError = result.message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiDanger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("确认覆盖本机数据", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("取消", color = YanjiTextSecondary)
                }
            },
            title = { Text("确认从备份恢复？", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(
                        "本机现有的专注、模考、日记、对话、打卡与成就将被**整体替换**为备份内容�?",
                        fontSize = 13.sp,
                        color = YanjiTextSecondary,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(YanjiSurfaceSoft)
                            .padding(12.dp)
                    ) {
                        Text(
                            "备份内容�?${backup.countsSummary()}",
                            fontSize = 12.sp,
                            color = YanjiTextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                    if (warnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        warnings.forEach { warning ->
                            Text("· $warning", fontSize = 12.sp, color = YanjiWarning, lineHeight = 18.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "导入前会自动在本机留一份快照，可随时找回�?",
                        fontSize = 12.sp,
                        color = YanjiTextTertiary
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = YanjiSurface
        )
    }

    // ---------- 导入/导出错误 ----------
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            confirmButton = {
                Button(
                    onClick = { importError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("知道�?", fontWeight = FontWeight.Bold)
                }
            },
            title = { Text("导入未执�?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(message, fontSize = 13.sp, color = YanjiTextPrimary, lineHeight = 19.sp) },
            shape = RoundedCornerShape(20.dp),
            containerColor = YanjiSurface
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("了解", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JuanjuanAvatar(size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("关于研迹 (Yanji)", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "研迹（Yanji）是一款仅供个人使用的 Android 考研日记与学习管理应用。\n\n核心价值：记录、专注、积累、复盘。\n\n设计原则：\n�? Local First：数据全部存储在用户本地设备\n�? 真实记录：专注计时基于真实时间戳\n�? 低干扰：不做复杂社交、排行榜和过度鸡血\n�? 卷卷陪伴：拟人化圆角笔记本伙伴，安静陪伴你的考研全程�?",
                        fontSize = 13.sp,
                        color = YanjiTextPrimary,
                        lineHeight = 20.sp
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = YanjiSurface
        )
    }
}

@Composable
private fun YanjiExamDatePickerDialog(
    initialDateMillis: Long?,
    onDismissRequest: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val initialSelection = initialDateMillis ?: currentLocalDateAsUtcMillis()
    val initialCalendar = remember(initialSelection) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = initialSelection
        }
    }
    var visibleYear by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var visibleMonth by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH)) }
    var selectedDateMillis by remember { mutableLongStateOf(initialSelection) }

    val monthCalendar = remember(visibleYear, visibleMonth) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(visibleYear, visibleMonth, 1)
        }
    }
    val leadingEmptyCells = remember(visibleYear, visibleMonth) {
        (monthCalendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    }
    val daysInMonth = remember(visibleYear, visibleMonth) {
        monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val weekRowCount = remember(leadingEmptyCells, daysInMonth) {
        ((leadingEmptyCells + daysInMonth + 6) / 7).coerceIn(5, 6)
    }
    val todayMillis = remember { currentLocalDateAsUtcMillis() }
    val weekdays = remember { listOf("一", "�?", "�?", "�?", "�?", "�?", "�?") }

    fun moveMonth(delta: Int) {
        val target = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(visibleYear, visibleMonth + delta, 1)
        }
        visibleYear = target.get(Calendar.YEAR)
        visibleMonth = target.get(Calendar.MONTH)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = YanjiSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, YanjiBorder),
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择考研初试日期",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YanjiTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatExamDateDisplayFromUtcMillis(selectedDateMillis),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = YanjiPrimary
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${visibleYear} �? ${visibleMonth + 1} �?",
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = YanjiTextPrimary
                    )
                    IconButton(
                        onClick = { moveMonth(-1) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(YanjiSurfaceSoft)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "上个�?",
                            tint = YanjiTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { moveMonth(1) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(YanjiSurfaceSoft)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "下个�?",
                            tint = YanjiTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = YanjiDivider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { weekday ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = weekday,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = YanjiTextTertiary
                            )
                        }
                    }
                }

                repeat(weekRowCount) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val cellIndex = row * 7 + column
                            val day = cellIndex - leadingEmptyCells + 1

                            if (day in 1..daysInMonth) {
                                val dayMillis = utcDateMillis(visibleYear, visibleMonth, day)
                                val isSelected = dayMillis == selectedDateMillis
                                val isToday = dayMillis == todayMillis

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(43.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) YanjiPrimary else Color.Transparent)
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(1.dp, YanjiPrimary, CircleShape)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .semantics {
                                                contentDescription = "${visibleYear}�?${visibleMonth + 1}�?${day}�?"
                                                selected = isSelected
                                            }
                                            .clickable { selectedDateMillis = dayMillis },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isToday) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> YanjiPrimary
                                                else -> YanjiTextPrimary
                                            }
                                        )
                                    }
                                }
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(43.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("取消", color = YanjiTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onDateSelected(selectedDateMillis) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary)
                    ) {
                        Text("确定", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun parseExamDateToUtcMillis(value: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)?.time
}.getOrNull()

private fun formatExamDateFromUtcMillis(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(value))

private fun formatExamDateDisplayFromUtcMillis(value: Long): String =
    SimpleDateFormat("yyyy年M月d�?", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(value))

private fun currentLocalDateAsUtcMillis(): Long {
    val local = Calendar.getInstance()
    return utcDateMillis(
        year = local.get(Calendar.YEAR),
        month = local.get(Calendar.MONTH),
        day = local.get(Calendar.DAY_OF_MONTH)
    )
}

private fun utcDateMillis(year: Int, month: Int, day: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month, day)
    }.timeInMillis

@Composable
fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = YanjiTextSecondary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(YanjiPrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = YanjiPrimary, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = YanjiTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = YanjiTextSecondary
            )
        }

        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = YanjiTextTertiary)
    }
}
