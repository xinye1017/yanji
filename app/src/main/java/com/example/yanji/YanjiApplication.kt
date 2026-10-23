package com.example.yanji

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.example.yanji.bridge.LaunchPreheat
import com.example.yanji.data.YanjiRepository
import com.example.yanji.bridge.YanjiPackage
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.defaults.DefaultReactHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.runtime.hermes.HermesInstance
import com.facebook.react.shell.MainReactPackage
import com.facebook.soloader.SoLoader
import com.horcrux.svg.SvgPackage
import com.swmansion.reanimated.ReanimatedPackage
import com.swmansion.worklets.WorkletsPackage
import com.th3rdwave.safeareacontext.SafeAreaContextPackage
import com.bluroverly.SajjadBlurOverlayPackage

/**
 * 研迹 Application —— React Native 新架构（Bridgeless）宿主。
 */
class YanjiApplication : Application(), com.facebook.react.ReactApplication {
    override val reactNativeHost: ReactNativeHost by lazy {
        object : DefaultReactNativeHost(this@YanjiApplication) {
            override fun getPackages(): List<ReactPackage> = listOf(
                MainReactPackage(),
                YanjiPackage(),
                // 前端 UI 组件库（reanimated 依赖 worklets，两者必须同时注册）
                ReanimatedPackage(),
                WorkletsPackage(),
                SvgPackage(),
                SafeAreaContextPackage(),
                SajjadBlurOverlayPackage()
            )

            override fun getJSMainModuleName(): String = "index"

            override fun getBundleAssetName(): String = "index.android.bundle"

            override fun getUseDeveloperSupport(): Boolean = false
        }
    }

    override val reactHost: ReactHost by lazy {
        DefaultReactHost.getDefaultReactHost(applicationContext, reactNativeHost, HermesInstance())
    }

    override fun onCreate() {
        super.onCreate()
        // SoLoader 必须拿到 react-android 的 OpenSourceMergedSoMapping：
        // RN 0.87 把 react_featureflagsjni 等一大批 JNI 库以 target_merge_so()
        // 合并进了 libreactnative.so，并不再产出独立 .so。SoLoader 自带的
        // MergedSoMapping 是空实现（mapLibName 恒返回 null），于是会按原始
        // soname 去找 libreact_featureflagsjni.so，dlopen 必然失败并在
        // ReactActivityDelegate.onCreate 里抛 UnsatisfiedLinkError 崩溃。
        // OpenSourceMergedSoMapping 才把这些 soname 映射回 libreactnative.so
        // 并触发各自的 JNI_OnLoad。
        // 这个调用必须在任何 React Native 类被加载之前完成。
        SoLoader.init(this, YanjiMergedSoMapping())
        DefaultNewArchitectureEntryPoint.load()
        YanjiRepository.init(this)
        // 预热必须在 Repository 就绪之后立刻开始：它读的正是 Repository 背后的同一份 Room
        // 数据，Activity 的首帧会等它（见 MainActivity.onCreate）。
        LaunchPreheat.start()
        // 同时把 JS 引擎也提前拉起来。ReactHost.start() 按 RN 官方语义就是
        // 「在首个 surface 需要渲染前，先在后台创建 ReactInstance 并执行 bundle」：
        // 它不挂载任何视图，真正的 initialProperties 仍在 createSurface 时交给根组件。
        //
        // 这一步把原本串行的两段时间并行掉：以前 Activity 会先阻塞等待 Room 预热
        // （最多 1s），之后才开始加载 Hermes 与 bundle；现在 JS 启动与 Room 预热同时进行，
        // 启动画面停留时间由「预热 + RN 启动」缩短为两者中较慢的那个。
        val startedAt = SystemClock.elapsedRealtime()
        try {
            reactHost.start()
            Log.i(TAG, "ReactHost preload requested at +${SystemClock.elapsedRealtime() - startedAt}ms")
        } catch (error: Exception) {
            // 预加载失败不改变语义：Activity 创建 surface 时仍会按正常路径重试并暴露真实错误。
            Log.w(TAG, "ReactHost preload failed; the activity will start it normally", error)
        }
    }

    private companion object {
        const val TAG = "YanjiStartup"
    }
}
