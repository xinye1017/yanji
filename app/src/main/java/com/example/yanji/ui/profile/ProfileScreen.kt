package com.example.yanji.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.YanjiTime
import com.example.yanji.data.backup.BackupCodec
import com.example.yanji.data.backup.BackupDecodeResult
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiThemeMode
import com.example.yanji.ui.components.AchievementSummaryBanner
import com.example.yanji.ui.components.AiConfigDialog
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.YanjiPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateToAchievements: () -> Unit = {},
    viewModel: ProfileViewModel = yanjiViewModel { container ->
        ProfileViewModel(container.repository)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val mascot = currentMascotTheme()

    val daysRemaining = remember(settings.targetExamDate) {
        YanjiTime.parseIsoDate(settings.targetExamDate)?.let { target ->
            ChronoUnit.DAYS.between(YanjiTime.today(), target).toInt()
        }
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showExamTargetDialog by remember { mutableStateOf(false) }
    var showAiConfigDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showMascotPicker by remember { mutableStateOf(false) }

    // Backup export / import state
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
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
                    Toast.makeText(context, "已导出备份（不含 AI Key）", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        context,
                        "导出失败：${written.exceptionOrNull()?.localizedMessage ?: "未知错误"}",
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
                    } ?: error("无法读取所选文件")
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
                importError = "读取文件失败：${it.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun startExport() {
        val stamp = Instant.now().atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US))
        exportLauncher.launch("yanji-backup-$stamp.json")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YanjiColors.groupedBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(YanjiSpacing.PageTopGap))

        YanjiPageHeader(title = "我的")

        Spacer(modifier = Modifier.height(20.dp))

        ProfileIdentityCard(settings = settings)

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSectionHeader(
            title = "备考概览",
            actionLabel = "编辑",
            onAction = { showExamTargetDialog = true }
        )
        Spacer(modifier = Modifier.height(8.dp))
        PreparationOverviewCard(
            settings = settings,
            daysRemaining = daysRemaining,
            todayStudySeconds = state.todayStudySeconds
        )

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSectionHeader(title = "研途成就")
        Spacer(modifier = Modifier.height(8.dp))
        AchievementSummaryBanner(onClick = onNavigateToAchievements)

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSectionHeader(title = "AI 与智能")
        Spacer(modifier = Modifier.height(8.dp))
        ProfileSettingsGroup {
            ProfileSettingsItem(
                icon = Icons.Outlined.Psychology,
                title = "AI API 配置",
                subtitle = "兼容 OpenAI API",
                onClick = { showAiConfigDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSectionHeader(title = "偏好")
        Spacer(modifier = Modifier.height(8.dp))
        ProfileSettingsGroup {
            ProfileSettingsItem(
                icon = Icons.Outlined.Pets,
                title = "学习伙伴",
                subtitle = "当前：${mascot.name}",
                onClick = { showMascotPicker = true }
            )
            HorizontalDivider(
                color = YanjiColors.separator,
                thickness = 0.8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            ProfileThemeSelector(
                selected = YanjiThemeMode.fromStorage(settings.themeMode),
                onSelect = { mode -> viewModel.updateSettings(settings.copy(themeMode = mode.name)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSectionHeader(
            title = "数据与隐私",
            trailingContent = { LocalDataStatus() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProfileSettingsGroup {
            ProfileSettingsItem(
                icon = Icons.Outlined.CloudDownload,
                title = "导出备份",
                subtitle = if (isExporting) {
                    "正在导出…"
                } else {
                    "专注、模考、日记与设置，不包含 AI Key"
                },
                enabled = !isExporting,
                onClick = { startExport() }
            )
            HorizontalDivider(
                color = YanjiColors.separator,
                thickness = 0.8.dp,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp)
            )
            ProfileSettingsItem(
                icon = Icons.Outlined.CloudUpload,
                title = "导入恢复",
                subtitle = if (isImporting) {
                    "正在导入…"
                } else {
                    "恢复 JSON 备份，操作前自动留存快照"
                },
                enabled = !isImporting,
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ProfileSectionHeader(title = "更多")
        Spacer(modifier = Modifier.height(8.dp))
        ProfileSettingsGroup {
            ProfileSettingsItem(
                icon = Icons.Outlined.Info,
                title = "关于研迹与${mascot.name}",
                subtitle = "v1.0 · 记录、专注、积累、复盘",
                onClick = { showAboutDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(AppContentInsets.BottomBarPadding))
    }

    if (showMascotPicker) {
        MascotThemeBottomSheet(
            selected = MascotThemeId.fromStorage(settings.mascotTheme),
            onSelect = { theme ->
                viewModel.updateSettings(settings.copy(mascotTheme = theme.name))
            },
            onDismiss = { showMascotPicker = false }
        )
    }

    // Exam Target Dialog
    if (showExamTargetDialog) {
        ExamTargetDialog(
            settings = settings,
            onDismiss = { showExamTargetDialog = false },
            onSave = {
                viewModel.updateSettings(it)
                showExamTargetDialog = false
            }
        )
    }

    // AI Config Dialog
    if (showAiConfigDialog) {
        AiConfigDialog(
            onDismissRequest = { showAiConfigDialog = false }
        )
    }

    // Import confirmation dialog
    pendingImport?.let { (rawJson, decoded) ->
        ImportConfirmDialog(
            decoded = decoded,
            onDismiss = { pendingImport = null },
            onConfirm = {
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
                                "已恢复：${decoded.backup.countsSummary()}$tail",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is com.example.yanji.data.backup.BackupImportResult.Failure ->
                            importError = result.message
                    }
                }
            }
        )
    }

    // Import error dialog
    importError?.let { message ->
        ImportErrorDialog(
            message = message,
            onDismiss = { importError = null }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutYanjiDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}
