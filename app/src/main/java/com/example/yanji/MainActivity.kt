package com.example.yanji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.YanjiTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 1. Initialize Room Database via Repository
    YanjiRepository.init(applicationContext)

    enableEdgeToEdge()
    val container = (application as YanjiApplication).container
    setContent {
      androidx.compose.runtime.CompositionLocalProvider(
        com.example.yanji.di.LocalAppContainer provides container
      ) {
        YanjiTheme {
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
