package com.example.yanji

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.facebook.react.ReactActivity
import com.example.yanji.data.YanjiRepository

/**
 * 研迹的唯一 Activity —— 纯 React Native 宿主。
 *
 * 曾经这里是 Compose `setContent` 的挂载点，并带一个 `test_achievement`
 * intent 分支用来在真机上触发成就庆祝动画调试。整套 Compose UI 已随 RN
 * 重构移除，那个分支全仓库零调用方，随之删除。
 */
class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "YanjiApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        YanjiRepository.init(applicationContext)
    }
}
