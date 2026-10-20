package com.example.yanji.bridge

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.content.res.Resources
import java.io.File
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

    /**
     * 本机能否渲染真正的**中文衬线**字面。
     *
     * Android 的 `serif` 家族是拉丁衬线（Noto Serif / Droid Serif），汉字会
     * 回落到无衬线中文字体，于是 `fontFamily:'serif'` 在中英混排里一半有衬线、
     * 一半没有。ColorOS（Find X8 及当前全部 OPPO / 一加）只内置 OPPO Sans 一套
     * 中文无衬线，没有任何衬线中文字体，所以这里必须探测而不是假设。
     *
     * 探测口径：一个候选家族只有在 `hasGlyph('永')` 为真 **且** 它排出来确实
     * 不是无衬线默认字体时才算数。第二个条件靠比较 Typeface 的 native 句柄：
     * 平台对不存在的家族会静默回落到默认无衬线，句柄相同即“没有这个字体”。
     *
     * 只读，不改任何系统设置。
     */
    @ReactMethod
    fun getFontAvailability(promise: Promise) {
        val result = Arguments.createMap()
        result.putString("serifHanFamily", detectSerifHanFamily())
        promise.resolve(result)
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

        /** 系统字体目录。厂商把可用字面以 ttc/otf 落在这里，名字即事实。 */
        private const val SYSTEM_FONT_DIR = "/system/fonts"

        /**
         * 具备**汉字衬线**字面的系统字体文件名特征。
         *
         * 只认带 CJK 字段名（CJK / Hans / SC / Serif SC）的思源宋体族：Android
         * 自带的 `NotoSerif-*.ttf` 是纯拉丁衬线，汉字会回落到无衬线，把它算作
         * 命中正是要避免的那种“一半有衬线”；`NotoSerifCJK-Regular.ttc`（AOSP
         * 与多数国产 ROM 之外的平台）才是真正带汉字衬线的那一个。
         */
        private val SERIF_HAN_FONT_FILES = listOf(
            "NotoSerifCJK-Regular.ttc",
            "NotoSerifCJKsc-Regular.otf",
            "NotoSerifSC-Regular.otf",
            "SourceHanSerifSC-Regular.otf",
            "SourceHanSerifCN-Regular.otf",
        )

        /**
         * 本机是否真的装有中文衬线字体；有则返回家族名，没有返回 null。
         *
         * 判定只依赖 `/system/fonts` 的实际文件，而不是 `Typeface.create` 的结果：
         * 平台对不存在的家族不报错，只静默回落到默认无衬线，所以「能创建出
         * Typeface」不能证明字体存在。字体文件是唯一可靠的事实来源。
         *
         * ColorOS（Find X8 及当前全部 OPPO / 一加）只内置 OPPO Sans 一套中文
         * 无衬线，目录里没有任何宋体文件，因此这里必然返回 null，衬线角色回落
         * 到系统无衬线，而不是一半衬线一半无衬线。
         */
        internal fun detectSerifHanFamily(): String? {
            val installed = runCatching {
                File(SYSTEM_FONT_DIR).list()?.toSet() ?: emptySet()
            }.getOrNull() ?: return null
            return SERIF_HAN_FONT_FILES.firstOrNull { installed.contains(it) }?.let { fileName ->
                SERIF_HAN_FAMILY_BY_FILE[fileName]
            }
        }

        /** 文件 → 平台家族名。文件在、名字对得上，才认为该家族可渲染。 */
        private val SERIF_HAN_FAMILY_BY_FILE = mapOf(
            "NotoSerifCJK-Regular.ttc" to "serif",
            "NotoSerifCJKsc-Regular.otf" to "serif",
            "NotoSerifSC-Regular.otf" to "Noto Serif SC",
            "SourceHanSerifSC-Regular.otf" to "Source Han Serif SC",
            "SourceHanSerifCN-Regular.otf" to "Source Han Serif SC",
        )
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
