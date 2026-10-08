package com.example.yanji

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.facebook.react.ReactActivity
import com.example.yanji.data.YanjiRepository

class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "YanjiApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        YanjiRepository.init(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val testId = intent?.getStringExtra("test_achievement")
        if (!testId.isNullOrBlank()) {
            val container = (application as YanjiApplication).container
            com.example.yanji.data.achievement.AchievementCatalog.find(testId)?.let { def ->
                container.repository.emitCelebration(def)
            }
            intent.removeExtra("test_achievement")
        }
    }
}
