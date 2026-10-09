package com.example.yanji

import android.app.Application
import com.example.yanji.data.YanjiRepository
import com.example.yanji.di.AppContainer
import com.example.yanji.di.DefaultAppContainer
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

/**
 * 研迹 Application —— React Native 新架构（Bridgeless）宿主。
 */
class YanjiApplication : Application(), com.facebook.react.ReactApplication {
    lateinit var container: AppContainer
        private set

    override val reactNativeHost: ReactNativeHost by lazy {
        object : DefaultReactNativeHost(this@YanjiApplication) {
            override fun getPackages(): List<ReactPackage> = listOf(
                MainReactPackage(),
                YanjiPackage()
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
        container = DefaultAppContainer(this)
    }
}
