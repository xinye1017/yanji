package com.example.yanji.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** AI 配置对话框不可变 UiState。 */
data class AiConfigUiState(
    val settings: UserSettings
)

/**
 * AI 后端配置 Feature ViewModel：设置读写 + 模型列表探测。
 * AiConfigDialog 自包含使用，被「我的」页与对话页两处宿主复用。
 * 注意：API Key 的加密存储由 [YanjiRepository.updateSettings] 内部处理（Keystore），
 * 这里不触碰任何密钥细节。
 */
class AiConfigViewModel(
    private val repo: YanjiRepository
) : ViewModel() {

    val uiState: StateFlow<AiConfigUiState> = repo.settings
        .map { AiConfigUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AiConfigUiState(repo.settings.value)
        )

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): Result<List<String>> =
        repo.fetchAvailableModels(baseUrl, apiKey)

    fun updateSettings(newSettings: UserSettings) = repo.updateSettings(newSettings)
}
