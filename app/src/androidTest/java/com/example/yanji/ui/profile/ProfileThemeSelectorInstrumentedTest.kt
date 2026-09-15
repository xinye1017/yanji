package com.example.yanji.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.theme.YanjiTheme
import com.example.yanji.theme.YanjiThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「外观」主题切换的 UI 契约测试。
 *
 * 定位一律走 [ProfileThemeTags]（testTag 常量），不依赖中文文案；
 * 选中态用 `assertIsSelected()` 断言 —— 这依赖 [ProfileThemeSelector] 用的是
 * `Modifier.selectable` 而不是 `clickable`（后者不带 Selected 语义）。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ProfileThemeSelectorInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun rendersAllThreeModesAndMarksTheCurrentSelection() {
        composeRule.setContent {
            YanjiTheme {
                ProfileThemeSelector(
                    selected = YanjiThemeMode.SYSTEM,
                    onSelect = {}
                )
            }
        }

        YanjiThemeMode.entries.forEach { mode ->
            composeRule.onNodeWithTag(ProfileThemeTags.option(mode)).assertExists()
        }
        composeRule.onNodeWithTag(ProfileThemeTags.option(YanjiThemeMode.SYSTEM)).assertIsSelected()
        composeRule.onNodeWithTag(ProfileThemeTags.option(YanjiThemeMode.DARK)).assertIsNotSelected()
    }

    @Test
    fun clickingAModeReportsItAndMovesTheSelection() {
        var reported: YanjiThemeMode? = null

        composeRule.setContent {
            var selected by remember { mutableStateOf(YanjiThemeMode.SYSTEM) }
            YanjiTheme {
                ProfileThemeSelector(
                    selected = selected,
                    onSelect = { mode ->
                        reported = mode
                        selected = mode
                    }
                )
            }
        }

        composeRule.onNodeWithTag(ProfileThemeTags.option(YanjiThemeMode.DARK)).performClick()

        assertNotNull("点击必须回调所选模式", reported)
        assertEquals(YanjiThemeMode.DARK, reported)
        composeRule.onNodeWithTag(ProfileThemeTags.option(YanjiThemeMode.DARK)).assertIsSelected()
        composeRule.onNodeWithTag(ProfileThemeTags.option(YanjiThemeMode.SYSTEM)).assertIsNotSelected()
    }

    /** 选择器在暗色主题下同样要能渲染并保持选中态（暗色是最容易漏测的一档）。 */
    @Test
    fun rendersAndSelectsCorrectlyInDarkTheme() {
        composeRule.setContent {
            YanjiTheme(darkTheme = true) {
                ProfileThemeSelector(
                    selected = YanjiThemeMode.DARK,
                    onSelect = {}
                )
            }
        }

        composeRule.onNodeWithTag(ProfileThemeTags.option(YanjiThemeMode.DARK)).assertIsSelected()
        composeRule.onNodeWithTag(ProfileThemeTags.option(YanjiThemeMode.LIGHT)).assertIsNotSelected()
    }

    /** 宽松解析：未知/空值必须回落到跟随系统，不能让设置页崩掉。 */
    @Test
    fun themeModeStorageParserFallsBackToSystem() {
        assertEquals(YanjiThemeMode.SYSTEM, YanjiThemeMode.fromStorage(null))
        assertEquals(YanjiThemeMode.SYSTEM, YanjiThemeMode.fromStorage(""))
        assertEquals(YanjiThemeMode.SYSTEM, YanjiThemeMode.fromStorage("SOMETHING_NEW"))
        assertEquals(YanjiThemeMode.DARK, YanjiThemeMode.fromStorage("dark"))
        assertEquals(YanjiThemeMode.LIGHT, YanjiThemeMode.fromStorage(" LIGHT "))
    }
}
