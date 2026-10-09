package com.example.yanji

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.example.yanji.data.YanjiRepository

/**
 * 研迹的唯一 Activity —— React Native 新架构宿主。
 */
class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "YanjiApp"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        YanjiRepository.init(applicationContext)
    }
}
