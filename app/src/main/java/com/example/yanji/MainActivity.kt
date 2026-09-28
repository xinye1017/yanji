package com.example.yanji

import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.YanjiMotion
import com.example.yanji.theme.YanjiTheme
import com.example.yanji.theme.YanjiThemeMode
import com.example.yanji.theme.MascotThemes
import com.example.yanji.theme.resolveDarkTheme
import com.example.yanji.ui.SystemBarAppearance
import com.example.yanji.ui.achievement.AchievementCelebrationOverlay

class MainActivity : ComponentActivity() {
  private var exitingSplashScreen: SplashScreenViewProvider? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)
    splashScreen.setOnExitAnimationListener { provider ->
      exitingSplashScreen = provider
      if (savedInstanceState != null || YanjiMotion.isReduceMotionEnabled(this)) {
        removeSplashScreen()
      } else {
        val easing = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        val duration = YanjiMotion.DurationStandard.toLong()
        provider.iconView.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(duration)
            .setInterpolator(easing)
            .start()
        provider.view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(easing)
            .withEndAction { removeSplashScreen() }
            .start()
      }
    }

    // 1. Initialize Room Database via Repository
    YanjiRepository.init(applicationContext)

    enableEdgeToEdge()
    val container = (application as YanjiApplication).container
    setContent {
      // 主题模式持久化在 Room 的 user_settings 里；由它驱动整棵树：
      // SYSTEM 跟随系统，LIGHT / DARK 是用户的显式选择。
      val settings by container.repository.settings.collectAsStateWithLifecycle()
      val darkTheme = YanjiThemeMode.fromStorage(settings.themeMode).resolveDarkTheme()
      val mascotTheme = MascotThemes.fromStorage(settings.mascotTheme)
      androidx.compose.runtime.CompositionLocalProvider(
        com.example.yanji.di.LocalAppContainer provides container
      ) {
        YanjiTheme(
            darkTheme = darkTheme,
            // 系统级「降低透明度」直通到主题档位：不传的话 ReducedGlassTokens 永远不会被取用。
            reduceTransparency = YanjiMotion.isReduceTransparencyEnabled(),
            mascotTheme = mascotTheme
        ) {
          // 系统栏图标明暗必须跟随「应用主题」：enableEdgeToEdge() 的默认判定只看系统
          // uiMode，用户显式选择的 LIGHT / DARK 与系统不一致时状态栏会与底色同色而看不见。
          SystemBarAppearance(darkTheme = darkTheme)
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              MainNavigation()
              AchievementCelebrationOverlay()
            }
          }
        }
      }
    }
    handleIntent(intent)
  }

  private fun removeSplashScreen() {
    val provider = exitingSplashScreen ?: return
    exitingSplashScreen = null
    provider.iconView.animate().cancel()
    provider.view.animate().cancel()
    provider.remove()
  }

  override fun onDestroy() {
    removeSplashScreen()
    super.onDestroy()
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: android.content.Intent?) {
    val testId = intent?.getStringExtra("test_achievement")
    if (!testId.isNullOrBlank()) {
      android.util.Log.d("AchievementCelebration", "handleIntent received test_achievement: $testId")
      val container = (application as YanjiApplication).container
      com.example.yanji.data.achievement.AchievementCatalog.find(testId)?.let { def ->
        android.util.Log.d("AchievementCelebration", "Emitting celebration: ${def.title}")
        container.repository.emitCelebration(def)
      }
      intent.removeExtra("test_achievement")
    }
  }
}
