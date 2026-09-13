package com.example.yanji

import android.app.Application
import com.example.yanji.data.YanjiRepository
import com.example.yanji.di.AppContainer
import com.example.yanji.di.DefaultAppContainer

class YanjiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        YanjiRepository.init(this)
        container = DefaultAppContainer(this)
    }
}
