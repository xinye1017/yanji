package com.example.yanji.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.YanjiTab
import com.example.yanji.theme.YanjiTheme
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.components.YanjiDetailTopBar
import com.example.yanji.ui.navigation.GlassBottomBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 跨分辨率与字体缩放的 Design System 视觉矩阵验证测试：
 * 1. 360x800 dp (1.0x density / 1.0x font scale) — 紧凑小屏设备
 * 2. 390x844 dp (1.0x density / 1.0x font scale) — 主流常规机型
 * 3. 412x915 dp (1.0x density / 1.0x font scale) — 大屏 / 旗舰机型
 * 4. 390x844 dp (1.0x density / 1.3x font scale) — 无障碍大字号场景
 *
 * 覆盖：
 * - GlassBottomBar 响应式 Dock、标签与可点击区域
 * - YanjiDetailTopBar 统一返回按钮、标题 Heading、动作区域
 * - YanjiCard 变体在深色/浅色模式下的渲染
 */
@RunWith(AndroidJUnit4::class)
class DesignSystemVisualMatrixInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    private fun testDesignSystemMatrix(
        width: Dp,
        height: Dp,
        fontScale: Float = 1.0f,
        darkTheme: Boolean = false
    ) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity, fontScale = fontScale)
            ) {
                YanjiTheme(darkTheme = darkTheme) {
                    var currentTab by remember { mutableStateOf(YanjiTab.HOME) }

                    Box(
                        modifier = Modifier.size(width = width, height = height)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 1. YanjiDetailTopBar
                            YanjiDetailTopBar(
                                title = "学习明细",
                                subtitle = "2026年9月14日",
                                onBack = {},
                                actions = {
                                    Button(onClick = {}) {
                                        Text("操作")
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2. YanjiCard Hero & Standard
                            YanjiCard(variant = YanjiCardVariant.Hero) {
                                Text("Hero Card 内容测试")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            YanjiCard(variant = YanjiCardVariant.Standard) {
                                Text("Standard Card 内容测试")
                            }
                        }

                        // 3. GlassBottomBar Dock
                        GlassBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        // 验证 DetailTopBar 元素及无障碍
        composeRule.onNodeWithTag("detail_top_bar_back").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("学习明细").assertIsDisplayed()
        composeRule.onNodeWithText("2026年9月14日").assertIsDisplayed()
        composeRule.onNodeWithText("操作").assertIsDisplayed()

        // 验证 Cards
        composeRule.onNodeWithText("Hero Card 内容测试").assertIsDisplayed()
        composeRule.onNodeWithText("Standard Card 内容测试").assertIsDisplayed()

        // 验证 GlassBottomBar 所有的 5 个 Tab 节点均正常呈现并具备点击语义
        composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("nav_tab_focus").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("nav_tab_note").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("nav_tab_stats").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("nav_tab_profile").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun viewport360x800_LightTheme() {
        testDesignSystemMatrix(width = 360.dp, height = 800.dp, fontScale = 1.0f, darkTheme = false)
    }

    @Test
    fun viewport390x844_LightTheme() {
        testDesignSystemMatrix(width = 390.dp, height = 844.dp, fontScale = 1.0f, darkTheme = false)
    }

    @Test
    fun viewport412x915_LightTheme() {
        testDesignSystemMatrix(width = 412.dp, height = 915.dp, fontScale = 1.0f, darkTheme = false)
    }

    @Test
    fun viewport390x844_DarkTheme() {
        testDesignSystemMatrix(width = 390.dp, height = 844.dp, fontScale = 1.0f, darkTheme = true)
    }

    @Test
    fun viewport390x844_LargeFontAccessibility() {
        testDesignSystemMatrix(width = 390.dp, height = 844.dp, fontScale = 1.3f, darkTheme = false)
    }
}
