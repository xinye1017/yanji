package com.example.yanji

import android.app.Application
import com.example.yanji.data.YanjiRepository
import com.example.yanji.di.AppContainer
import com.example.yanji.di.DefaultAppContainer
import com.example.yanji.bridge.YanjiPackage
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.shell.MainReactPackage
import com.facebook.soloader.SoLoader

class YanjiApplication : Application(), ReactApplication {
    lateinit var container: AppContainer
        private set

    private val mReactNativeHost = object : ReactNativeHost(this@YanjiApplication) {
        override fun getUseDeveloperSupport(): Boolean = false

        override fun getPackages(): List<ReactPackage> = listOf(
            MainReactPackage(),
            YanjiPackage()
        )

        override fun getJSMainModuleName(): String = "index"

        override fun getBundleAssetName(): String = "index.android.bundle"
    }

    override val reactNativeHost: ReactNativeHost
        get() = mReactNativeHost

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, false)
        YanjiRepository.init(this)
        container = DefaultAppContainer(this)
    }
}
