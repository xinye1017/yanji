package com.example.yanji.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.Subject
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.backup.BackupCodec
import com.example.yanji.data.backup.BackupDecodeResult
import com.example.yanji.data.backup.BackupImportResult
import com.example.yanji.data.timer.FocusPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 我的页不可变 UiState。 */
data class ProfileUiState(
    val settings: UserSettings,
    val subjects: List<Subject> = emptyList(),
    /** 自动沉浸省电开关。 */
    val autoPowerSavingEnabled: Boolean = FocusPreferences.DEFAULT_AUTO_POWER_SAVING,
    /** 静置进入沉浸模式的等待时长（秒）。 */
    val powerSavingTimeoutSeconds: Int = FocusPreferences.DEFAULT_TIMEOUT_SECONDS
)

/**
 * 我的页 Feature ViewModel：目标设置读写 + JSON 备份导出/导入。
 * 备份内容与快照规则见 [YanjiRepository.exportBackupJson]/[importBackupJson]。
 */
class ProfileViewModel(
    private val repo: YanjiRepository,
    /** 由 AppContainer 注入的既有单例，避免页面各自 getInstance 造成两份内存镜像。 */
    private val focusPrefs: FocusPreferences
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        repo.settings,
        repo.subjects,
        focusPrefs.autoPowerSavingEnabled,
        focusPrefs.timeoutSeconds
    ) { settings, subjects, autoPowerSaving, timeoutSeconds ->
        ProfileUiState(
            settings = settings,
            subjects = subjects,
            autoPowerSavingEnabled = autoPowerSaving,
            powerSavingTimeoutSeconds = timeoutSeconds
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(
                settings = repo.settings.value,
                subjects = repo.subjects.value,
                autoPowerSavingEnabled = focusPrefs.autoPowerSavingEnabled.value,
                powerSavingTimeoutSeconds = focusPrefs.timeoutSeconds.value
            )
        )

    fun setAutoPowerSavingEnabled(enabled: Boolean) =
        focusPrefs.setAutoPowerSavingEnabled(enabled)

    fun setPowerSavingTimeoutSeconds(seconds: Int) =
        focusPrefs.setTimeoutSeconds(seconds)

    fun updateSettings(newSettings: UserSettings) = repo.updateSettings(newSettings)

    fun addSubjectCategory(name: String) = viewModelScope.launch {
        repo.addSubjectCategory(name)
    }

    fun addSubSubject(parentId: String, name: String) = viewModelScope.launch {
        repo.addSubSubject(parentId, name)
    }

    fun renameSubject(subjectId: String, newName: String) = viewModelScope.launch {
        repo.renameSubject(subjectId, newName)
    }

    fun deleteSubject(subjectId: String) = viewModelScope.launch {
        repo.deleteSubject(subjectId)
    }

    fun restoreDefaultSubjects() = viewModelScope.launch {
        repo.restoreDefaultSubjects()
    }

    suspend fun exportBackupJson(): String = repo.exportBackupJson()

    suspend fun importBackupJson(rawJson: String): BackupImportResult = repo.importBackupJson(rawJson)

    /**
     * 解析备份文件内容，供导入前的确认弹窗预览使用。
     *
     * 为什么不放在 Composable 里直接调 [BackupCodec.decode]：decode 会对整份 JSON
     * （防御上限 64 MB，见 BackupCodec.MAX_IMPORT_CHARS）做 kotlinx.serialization 解析与
     * schema 校验，是实打实的重 CPU 工作。页面层的 withContext(Dispatchers.IO) 只包住了
     * openInputStream().readBytes()，解析本身仍落在主线程上——大文件足以触发掉帧甚至 ANR。
     *
     * 这里把「读文件」与「解析文件」两段 IO 都收进 ViewModel：调用方只需把读到的
     * 字符串交进来，剩余的调度与结果判定都不再泄漏到 UI 层。
     */
    suspend fun decodeBackupPreview(rawJson: String): BackupDecodeResult =
        withContext(Dispatchers.IO) { BackupCodec.decode(rawJson) }
}
