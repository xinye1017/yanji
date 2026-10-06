package com.example.yanji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.YanjiTab
import com.example.yanji.theme.YanjiTheme
import com.example.yanji.ui.components.GlassSurface
import com.example.yanji.ui.navigation.GlassBottomBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 玻璃拟态（haze）在**真实 HazeState** 下的端到端渲染冒烟测试：
 * `hazeSource` → [GlassSurface] → [GlassBottomBar] 三层串起来，确认玻璃面板与 Dock
 * 真的拿到了模糊后的背景，且 Dock 的标签仍然可点。
 *
 * **为什么这个类被单独拆出来、并且单独一个 CI job 跑**：CI 的模拟器走 SwiftShader
 * （软件 GL），撑不住全屏 haze 渐进模糊，跑这一个测试时 emulator 进程会被直接打没。
 * 它挂在同一个 Gradle 任务里时，adb 随即报 `device not found`，Gradle 当场中止，
 * 同批剩下的 37 个测试连开始的机会都没有 —— 单个测试的崩溃被放大成整套插桩门禁失守。
 *
 * 已测量并排除的调优项（2026-10-06，证据见 `.github/workflows/android-quality.yml`）：
 *  - `-gpu-memory 4096` → 模拟器根本起不来（boot 超时）；
 *  - `-memory 4096` → 起得来，但这个测试照样把模拟器打死，所以不是宿主内存压力。
 *
 * 因此 CI 把它隔离成带 `continue-on-error` 的独立 job：其余 54 个测试重新成为有效门槛，
 * 而这个测试的失败仍会在 Checks 里留痕、只是不再挡合并。
 *
 * 真正该做的修法是给 CI 换上 GPU 加速的模拟器，或把 haze 的成本压到软件渲染扛得住的量级；
 * 在那之前，**不要**把这个类并回主任务，也不要删掉它的断言。
 */
@RunWith(AndroidJUnit4::class)
class HazeGlassSurfaceInstrumentedTest {

    // 这里刻意不用 DesignSystemVisualMatrixInstrumentedTest 那个 scaleFactor = 0 的
    // effectContext：本测试不驱动任何动画，用默认时钟更接近真机上的真实渲染路径。
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hazeBackedDockAndSurfaceRenderWithRealHazeState() {
        composeRule.setContent {
            YanjiTheme {
                val hazeState = remember { HazeState() }
                Box(modifier = Modifier.size(390.dp, 844.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .hazeSource(state = hazeState),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("Haze source content")
                    }

                    GlassSurface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
                        hazeState = hazeState
                    ) {
                        Text("Haze glass surface")
                    }

                    GlassBottomBar(
                        currentTab = YanjiTab.HOME,
                        onTabSelected = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                        hazeState = hazeState
                    )
                }
            }
        }

        composeRule.onNodeWithText("Haze source content").assertIsDisplayed()
        composeRule.onNodeWithText("Haze glass surface").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed().assertHasClickAction()
    }
}
