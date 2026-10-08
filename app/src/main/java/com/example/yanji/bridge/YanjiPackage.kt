package com.example.yanji.bridge

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * 「研迹」原生模块包：把 Kotlin 业务层桥接模块注册进 React Native。
 *
 * 与 `MainReactPackage` 并存，不覆盖任何官方模块。
 */
class YanjiPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        listOf(
            YanjiTimerModule(reactContext),
            YanjiDataModule(reactContext),
            YanjiThemeModule(reactContext)
        )

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
