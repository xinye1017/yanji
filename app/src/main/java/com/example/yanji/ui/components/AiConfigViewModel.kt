package com.example.yanji.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.SettingsUpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** AI 配置对话框不可变 UiState。 */
data class AiConfigUiState(
    val settings: UserSettings,
    val securityError: String? = null
)

/**
 * AI 后端配置 Feature ViewModel：设置读写 + 模型列表探测。
 * AiConfigDialog 自包含使用，被「我的」、统计与模考页面复用。
 * 注意：API Key 的加密存储由 [YanjiRepository.updateSettings] 内部处理（Keystore），
 * 这里不触碰任何密钥细节。
 */
class AiConfigViewModel(
    private val repo: YanjiRepository
) : ViewModel() {

    private val securityError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AiConfigUiState> = combine(repo.settings, securityError) { settings, error ->
        AiConfigUiState(settings, error)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AiConfigUiState(repo.settings.value)
        )

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): Result<List<String>> {
        val result = repo.fetchAvailableModels(baseUrl, apiKey)
        result.onSuccess { models ->
            repo.setAvailableAiModels(models)
        }
        return result
    }

    /** Returns false when Android Keystore could not durably save the key. */
    fun updateSettings(newSettings: UserSettings): Boolean {
        if (newSettings.aiModel.isNotBlank() && repo.availableAiModels.value.isEmpty()) {
            repo.setAvailableAiModels(listOf(newSettings.aiModel))
        }
        return when (repo.updateSettings(newSettings)) {
            SettingsUpdateResult.Saved -> {
                securityError.value = null
                true
            }
            SettingsUpdateResult.SecretUnavailable -> {
                securityError.value = SECURITY_STORAGE_MESSAGE
                false
            }
        }
    }

    fun clearSecurityError() {
        securityError.value = null
    }

    companion object {
        const val SECURITY_STORAGE_MESSAGE = "安全存储不可用，Key 未保存"
    }
}
