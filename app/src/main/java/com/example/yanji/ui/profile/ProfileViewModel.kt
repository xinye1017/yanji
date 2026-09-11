package com.example.yanji.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.backup.BackupImportResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 我的页不可变 UiState。 */
data class ProfileUiState(
    val settings: UserSettings
)

/**
 * 我的页 Feature ViewModel：目标设置读写 + JSON 备份导出/导入。
 * 备份内容与快照规则见 [YanjiRepository.exportBackupJson]/[importBackupJson]。
 */
class ProfileViewModel(
    private val repo: YanjiRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = repo.settings
        .map { ProfileUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(repo.settings.value)
        )

    fun updateSettings(newSettings: UserSettings) = repo.updateSettings(newSettings)

    suspend fun exportBackupJson(): String = repo.exportBackupJson()

    suspend fun importBackupJson(rawJson: String): BackupImportResult = repo.importBackupJson(rawJson)
}
