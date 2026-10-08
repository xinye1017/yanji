package com.example.yanji.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.YanjiThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 「研迹」React Native ↔ Kotlin 主题桥接模块。
 *
 * 红线（AGENTS.md §二.6）：本模块**只读/只写应用内偏好** `user_settings.themeMode`，
 * 绝不触碰系统级深浅色（`UiModeManager` / `settings put ui_night_mode` 一律禁止）。
 * `isDark` 由系统 uiMode 与应用偏好共同解析，与 Compose 侧 `YanjiThemeMode.resolveDarkTheme` 同源。
 */
class YanjiThemeModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            YanjiRepository.getInstance().settings.collectLatest {
                emitThemeChanged()
            }
        }
    }

    override fun getName(): String = "YanjiThemeModule"

    @ReactMethod
    fun getThemePreference(promise: Promise) {
        val mode = YanjiThemeMode.fromStorage(YanjiRepository.getInstance().settings.value.themeMode)
        promise.resolve(Arguments.createMap().apply {
            putString("mode", mode.name)
            putBoolean("isDark", resolveDark(mode))
        })
    }

    @ReactMethod
    fun setThemePreference(mode: String, promise: Promise) {
        val parsed = YanjiThemeMode.entries.firstOrNull { it.name.equals(mode.trim(), ignoreCase = true) }
        if (parsed == null) {
            promise.reject("E_INVALID_THEME", "Invalid theme mode: $mode")
            return
        }
        val repository = YanjiRepository.getInstance()
        val current = repository.settings.value
        val result = repository.updateSettings(current.copy(themeMode = parsed.name))
        if (result !is com.example.yanji.data.SettingsUpdateResult.Saved) {
            promise.reject("E_SETTINGS_SAVE_FAILED", "Failed to persist theme preference")
            return
        }
        emitThemeChanged()
        promise.resolve(true)
    }

    private fun resolveDark(mode: YanjiThemeMode): Boolean = when (mode) {
        YanjiThemeMode.SYSTEM -> isSystemDark()
        YanjiThemeMode.LIGHT -> false
        YanjiThemeMode.DARK -> true
    }

    private fun isSystemDark(): Boolean =
        reactContext.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun emitThemeChanged() {
        if (!reactContext.hasActiveReactInstance()) return
        val mode = YanjiThemeMode.fromStorage(YanjiRepository.getInstance().settings.value.themeMode)
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(EVENT_THEME_CHANGED, Arguments.createMap().apply {
                putString("mode", mode.name)
                putBoolean("isDark", resolveDark(mode))
            })
    }

    companion object {
        const val EVENT_THEME_CHANGED = "onThemeChanged"
    }
}
