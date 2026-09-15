package com.example.yanji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.YanjiTheme
import com.example.yanji.theme.YanjiThemeMode
import com.example.yanji.theme.resolveDarkTheme
import com.example.yanji.ui.SystemBarAppearance

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 1. Initialize Room Database via Repository
    YanjiRepository.init(applicationContext)

    enableEdgeToEdge()
    val container = (application as YanjiApplication).container
    setContent {
      // 主题模式持久化在 Room 的 user_settings 里；由它驱动整棵树：
      // SYSTEM 跟随系统，LIGHT / DARK 是用户的显式选择。
      val settings by container.repository.settings.collectAsStateWithLifecycle()
      val darkTheme = YanjiThemeMode.fromStorage(settings.themeMode).resolveDarkTheme()
      androidx.compose.runtime.CompositionLocalProvider(
        com.example.yanji.di.LocalAppContainer provides container
      ) {
        YanjiTheme(darkTheme = darkTheme) {
          // 系统栏图标明暗必须跟随「应用主题」：enableEdgeToEdge() 的默认判定只看系统
          // uiMode，用户显式选择的 LIGHT / DARK 与系统不一致时状态栏会与底色同色而看不见。
          SystemBarAppearance(darkTheme = darkTheme)
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            MainNavigation()
          }
        }
      }
    }
  }
}
