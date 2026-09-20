package com.example.yanji.data.journal

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 随笔编辑页顶部信息栏的展示配置。
 *
 * 满足需求：
 * 1. 今日状态打分：固定显示（[showScore] 永远为 true）。
 * 2. 当前记录随笔时间：[showTime]，默认开启。
 * 3. 用户自定义内容：天气 [showWeather]，默认关闭，可在「我的」页面中自定义。
 */
data class JournalHeaderConfig(
    val showScore: Boolean = true,
    val showTime: Boolean = true,
    val showWeather: Boolean = false
)

/**
 * 随笔顶部信息栏偏好管理仓库。
 * 单一事实源基于轻量 SharedPreferences，并提供响应式 StateFlow。
 */
class JournalHeaderPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(readFromPrefs())
    val configFlow: StateFlow<JournalHeaderConfig> = _configFlow.asStateFlow()

    private fun readFromPrefs(): JournalHeaderConfig {
        return JournalHeaderConfig(
            showScore = true,
            showTime = prefs.getBoolean(KEY_SHOW_TIME, true),
            showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, false)
        )
    }

    fun updateConfig(config: JournalHeaderConfig) {
        prefs.edit()
            .putBoolean(KEY_SHOW_TIME, config.showTime)
            .putBoolean(KEY_SHOW_WEATHER, config.showWeather)
            .apply()
        _configFlow.value = config.copy(showScore = true)
    }

    fun setShowTime(enabled: Boolean) {
        updateConfig(_configFlow.value.copy(showTime = enabled))
    }

    fun setShowWeather(enabled: Boolean) {
        updateConfig(_configFlow.value.copy(showWeather = enabled))
    }

    companion object {
        private const val PREFS_NAME = "yanji_journal_header_prefs"
        private const val KEY_SHOW_TIME = "show_time"
        private const val KEY_SHOW_WEATHER = "show_weather"

        @Volatile
        private var instance: JournalHeaderPreferences? = null

        fun getInstance(context: Context): JournalHeaderPreferences {
            return instance ?: synchronized(this) {
                instance ?: JournalHeaderPreferences(context).also { instance = it }
            }
        }
    }
}
