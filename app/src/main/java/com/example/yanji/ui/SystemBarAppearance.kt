package com.example.yanji.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 把系统栏（状态栏 / 导航栏）的图标明暗绑定到**应用主题**，而不是系统 uiMode。
 *
 * 为什么必须显式绑定：`ComponentActivity.enableEdgeToEdge()` 无参调用时用的是
 * `SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)`，其 `detectDarkMode`
 * 默认实现为
 * ```
 * { resources -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
 *     Configuration.UI_MODE_NIGHT_YES }
 * ```
 * 即**只看系统深色模式**。而本 App 的主题由 `user_settings.themeMode` 决定，用户可以在
 * 设置页「偏好 → 外观」显式覆盖系统（LIGHT / DARK）。两者一旦不一致，状态栏图标就会与
 * 其背后的底色同色——亮色底配白色图标，或暗色底配深色图标——从而完全看不见。
 *
 * 实现细节：用 key 含 [darkTheme] 的 [DisposableEffect]，只在主题真正变化时写一次窗口属性，
 * 而不是每帧重组都去碰 `WindowInsetsController`。这同时保证专注页
 * （`ActiveFocusContent.FocusSystemBarAppearance`，刻意使用亮色画布 + 深色图标）的临时覆盖
 * 不会在其它页面的重组中被本函数打断。
 *
 * @param darkTheme 当前应用是否处于暗色主题，由 `YanjiThemeMode.resolveDarkTheme()` 解析得到。
 */
@Composable
fun SystemBarAppearance(darkTheme: Boolean) {
    val activity = LocalActivity.current
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(activity, view, darkTheme) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        // 亮色主题 → 深色图标（light bars = true）；暗色主题 → 浅色图标。
        controller?.isAppearanceLightStatusBars = !darkTheme
        controller?.isAppearanceLightNavigationBars = !darkTheme
        onDispose {
            // 全局常驻外观，不需要回滚：本函数在任何时刻都代表当前主题的期望值。
        }
    }
}
