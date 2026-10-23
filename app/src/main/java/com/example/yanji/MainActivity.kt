package com.example.yanji

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.yanji.bridge.LaunchPreheat
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate

/**
 * 研迹的唯一 Activity —— React Native 新架构宿主。
 */
class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "YanjiApp"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        YanjiActivityDelegate(this, mainComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // 宿主在 Application.onCreate 就开始预热了；这里只做有界等待，让首帧直接拿到
        // 真实数据。等待期间系统启动窗口仍在显示，用户看到的是「启动中」而不是空页面。
        val waitStartedAt = SystemClock.elapsedRealtime()
        LaunchPreheat.awaitSettled()
        Log.i(
            "YanjiStartup",
            "preheat wait ${SystemClock.elapsedRealtime() - waitStartedAt}ms, " +
                "settled=${LaunchPreheat.hasSettled}, props=${LaunchPreheat.launchProperties != null}"
        )
        super.onCreate(savedInstanceState)
        // 万一等待超时（异常慢的盘上），启动画面继续持有到预热真正结束，
        // 避免首页先闪一屏「正在读取」。
        splashScreen.setKeepOnScreenCondition { !LaunchPreheat.hasSettled }
    }
}

/**
 * 冷启动预热好的今天数据，直接当 initialProperties 交给首帧。
 *
 * 没有它，首页第一帧只能渲染「正在读取今日记录…」，等 Room 读过桥回来再跳一次。
 * 预热失败时为 null，JS 侧退回原有的桥接读取路径，行为与改造前一致。
 */
private class YanjiActivityDelegate(
    activity: ReactActivity,
    mainComponentName: String
) : DefaultReactActivityDelegate(activity, mainComponentName) {
    override fun getLaunchOptions(): Bundle? = LaunchPreheat.launchProperties
}
