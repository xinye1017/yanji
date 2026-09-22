package com.example.yanji.data.timer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 专注与省电沉浸模式的本地配置管理器。
 *
 * 职责：
 * 1. 管理是否自动进入沉浸省电模式（默认开启）；
 * 2. 管理静置等待时长（默认 30 秒，支持 15s / 30s / 45s / 60s / 90s / 120s）。
 */
class FocusPreferences private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "yanji_focus_preferences",
        Context.MODE_PRIVATE
    )

    private val _autoPowerSavingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_POWER_SAVING, DEFAULT_AUTO_POWER_SAVING)
    )
    val autoPowerSavingEnabled: StateFlow<Boolean> = _autoPowerSavingEnabled.asStateFlow()

    private val _timeoutSeconds = MutableStateFlow(
        prefs.getInt(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
    )
    val timeoutSeconds: StateFlow<Int> = _timeoutSeconds.asStateFlow()

    fun setAutoPowerSavingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_POWER_SAVING, enabled).apply()
        _autoPowerSavingEnabled.value = enabled
    }

    fun setTimeoutSeconds(seconds: Int) {
        val valid = seconds.coerceIn(5, 600)
        prefs.edit().putInt(KEY_TIMEOUT_SECONDS, valid).apply()
        _timeoutSeconds.value = valid
    }

    companion object {
        const val KEY_AUTO_POWER_SAVING = "key_auto_power_saving"
        const val KEY_TIMEOUT_SECONDS = "key_timeout_seconds"

        const val DEFAULT_AUTO_POWER_SAVING = true
        const val DEFAULT_TIMEOUT_SECONDS = 30

        val TIMEOUT_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

        @Volatile
        private var instance: FocusPreferences? = null

        fun getInstance(context: Context): FocusPreferences {
            return instance ?: synchronized(this) {
                instance ?: FocusPreferences(context).also { instance = it }
            }
        }
    }
}
