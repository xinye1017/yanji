package com.example.yanji.bridge

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.content.res.Resources
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 「研迹」React Native ↔ Kotlin 主题桥接模块。
 *
 * 红线（AGENTS.md §二.6）：本模块**只读/只写应用内偏好** `user_settings.themeMode`，
 * 绝不触碰系统级深浅色（`UiModeManager` / `settings put ui_night_mode` 一律禁止）。
 *
 * 「跟随系统」的口径是**系统全局**深浅色，见 [systemIsDark]。
 */
class YanjiThemeModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Application 级配置回调；[invalidate] 时注销，避免长期持有已销毁的桥接实例。 */
    private var applicationCallback: ComponentCallbacks? = null

    init {
        scope.launch {
            YanjiRepository.getInstance().settings.collectLatest {
                emitThemeChanged()
            }
        }
        // 「跟随系统」必须随系统全局深浅色实时变化。系统深浅色切换只会触发
        // Application 级回调（Activity 已声明 configChanges=uiMode，自己收不到），
        // 所以这里注册在 Application 上，而不是 Activity。
        applicationCallback = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                emitThemeChanged()
            }

            override fun onLowMemory() = Unit
        }
        reactContext.applicationContext.registerComponentCallbacks(applicationCallback)
    }

    override fun getName(): String = "YanjiThemeModule"

    override fun invalidate() {
        applicationCallback?.let {
            reactContext.applicationContext.unregisterComponentCallbacks(it)
        }
        applicationCallback = null
        scope.cancel()
        super.invalidate()
    }

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
        YanjiThemeMode.SYSTEM -> systemIsDark()
        YanjiThemeMode.LIGHT -> false
        YanjiThemeMode.DARK -> true
    }

    /**
     * 系统**全局**是否为深色。
     *
     * 读 [Resources.getSystem] 而非 `reactContext.resources`：后者是应用级配置，
     * 会被系统 / OEM 的「单应用强制深色」覆盖（ColorOS 的
     * `ui_night_mode_override_on` 就是这个机制）。用户选「跟随系统」时指的是
     * 手机整体的深浅色，被单应用覆盖后的值不是他要跟随的东西。
     *
     * 系统每次深浅色切换都会更新这份全局配置，这里直接读取即是最新值，
     * 不需要缓存副本。
     *
     * 只读，不写；不触碰任何系统设置（AGENTS.md §二.6）。
     */
    private fun systemIsDark(): Boolean = isNightMode(Resources.getSystem().configuration)

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

/**
 * 配置是否处于夜间模式。
 *
 * 抽成顶层函数是为了能在 JVM 单测里直接断言：读 `Resources.getSystem()` 需要
 * Android runtime，而这一层判断（UI_MODE_NIGHT_MASK 的取值口径）本身没有依赖。
 *
 * 只判断夜间位，不判断 UI_MODE_TYPE（手机 / 车机 / 电视）：主题跟随的是深浅色，
 * 不是设备形态。
 */
internal fun isNightMode(config: Configuration): Boolean =
    config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
