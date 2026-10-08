package com.example.yanji.di

import android.content.Context
import com.example.yanji.data.AchievementRepository
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.FocusPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 轻量级依赖注入容器。
 *
 * ## 为什么不引入 Hilt：
 * 1. 研迹是轻量单 module 本地优先应用，没有大型跨 module 依赖图；
 * 2. Hilt 引入额外的 APT/KSP 开销、编译时字节码修改与 ClassLoader 代理，拉长增量构建 30~50%；
 * 3. 手写纯 Kotlin 容器零反射、零代码生成，在单元测试中只需覆写单例或传入 Fake。
 *
 * 纯 Kotlin：不再 import Compose 运行时。原 [LocalAppContainer] /
 * `yanjiViewModel()` 是给 Compose 屏幕层用的，随整套 Compose UI 移除而删除；
 * RN 侧通过 `YanjiPackage` 注册的桥接模块直接走
 * `YanjiRepository.getInstance()`，不经过本容器。
 */
interface AppContainer {
    val repository: YanjiRepository
    val statisticsRepository: StudyStatisticsRepository
    val achievementRepository: AchievementRepository
    val activeSessionCoordinator: ActiveSessionCoordinator

    /**
     * 专注/省电偏好。由组合根持有，页面与 ViewModel 都从这里取，不再各自
     * `FocusPreferences.getInstance(context)`。
     *
     * 刻意**复用既有单例**而不是新建实例：[FocusPreferences] 是 private 构造 + 内部
     * `MutableStateFlow` 镜像 SharedPreferences 的单例，若容器另建一个，写入方与读取方
     * 会持有两份互不同步的内存镜像。
     */
    val focusPreferences: FocusPreferences
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    /**
     * 应用级受控协程作用域：由组合根持有，随 AppContainer 生命周期存活。
     *
     * 供需要后台并发的仓储（如 AchievementRepository）注入，避免各仓储自建
     * `CoroutineScope(Dispatchers.Default + SupervisorJob())` 造成生命周期不受管辖。
     * 用 SupervisorJob：单个子任务失败不牵连其他后台任务。
     */
    private val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val repository: YanjiRepository by lazy {
        YanjiRepository.init(context.applicationContext)
        YanjiRepository.getInstance()
    }
    override val statisticsRepository: StudyStatisticsRepository by lazy {
        StudyStatisticsRepository(repository)
    }
    override val achievementRepository: AchievementRepository by lazy {
        AchievementRepository(repository, applicationScope)
    }
    override val activeSessionCoordinator: ActiveSessionCoordinator get() = ActiveSessionCoordinator

    override val focusPreferences: FocusPreferences by lazy {
        FocusPreferences.getInstance(context.applicationContext)
    }
}
