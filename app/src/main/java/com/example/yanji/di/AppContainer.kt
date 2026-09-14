package com.example.yanji.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.AchievementRepository
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.timer.ActiveSessionCoordinator

/**
 * 轻量级依赖注入容器。
 *
 * ## 为什么不引入 Hilt：
 * 1. 研迹是轻量单 module 纯 Compose 本地优先应用，没有大型跨 module 依赖图；
 * 2. Hilt 引入额外的 APT/KSP 开销、编译时字节码修改与 ClassLoader 代理，拉长增量构建 30~50%；
 * 3. 手写纯 Kotlin 容器不仅零反射、零代码生成，而且在单元测试、UI 测试与 Compose Preview
 *    中只需覆写单例或传入 Fake，透明度与可控性显著高于 Hilt 的 `@UninstallModules`。
 */
interface AppContainer {
    val repository: YanjiRepository
    val statisticsRepository: StudyStatisticsRepository
    val achievementRepository: AchievementRepository
    val activeSessionCoordinator: ActiveSessionCoordinator
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val repository: YanjiRepository by lazy {
        YanjiRepository.init(context.applicationContext)
        YanjiRepository.getInstance()
    }
    override val statisticsRepository: StudyStatisticsRepository by lazy {
        StudyStatisticsRepository(repository)
    }
    override val achievementRepository: AchievementRepository by lazy {
        AchievementRepository(repository)
    }
    override val activeSessionCoordinator: ActiveSessionCoordinator get() = ActiveSessionCoordinator
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided. Wrap app and tests in LocalAppContainer.")
}

/**
 * 屏幕层 ViewModel 工厂辅助函数，消除各 Screen 内部对全局 getInstance() 的直接硬编码依赖。
 */
@Composable
inline fun <reified VM : ViewModel> yanjiViewModel(
    key: String? = null,
    crossinline creator: (AppContainer) -> VM
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return creator(container) as T
            }
        }
    )
}
