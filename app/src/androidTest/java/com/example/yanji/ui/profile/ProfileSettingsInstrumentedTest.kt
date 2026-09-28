package com.example.yanji.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.YanjiApplication
import com.example.yanji.data.UserSettings
import com.example.yanji.di.LocalAppContainer
import com.example.yanji.theme.YanjiTheme
import com.example.yanji.ui.components.AiConfigDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSettingsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun keylessLocalModelShowsConfiguredState() {
        composeRule.setContent {
            YanjiTheme {
                ProfileAiCard(
                    settings = UserSettings(
                        aiProvider = "本地 Ollama",
                        aiBaseUrl = "http://127.0.0.1:11434/v1",
                        aiModel = "qwen2.5:latest"
                    ),
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText("AI API 配置").assertIsDisplayed()
        composeRule.onNodeWithText("已配置").assertIsDisplayed()
        composeRule.onNodeWithText("本地 Ollama").assertDoesNotExist()
        composeRule.onNodeWithText("qwen2.5:latest").assertDoesNotExist()
        composeRule.onNodeWithText("使用服务默认模型").assertDoesNotExist()
    }

    @Test
    fun emptyExamGoalUsesOneSetupPrompt() {
        composeRule.setContent {
            YanjiTheme {
                ProfileIdentityCard(
                    settings = UserSettings(),
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText("备考目标").assertIsDisplayed()
        composeRule.onNodeWithText("设置院校、专业与初试日期").assertIsDisplayed()
        composeRule.onNodeWithText("点击设置专业").assertDoesNotExist()
        composeRule.onNodeWithText("点击设置初试日期").assertDoesNotExist()
        composeRule.onNodeWithText("考研").assertDoesNotExist()
    }

    @Test
    fun unconfiguredAiCardShowsNoSubtitle() {
        composeRule.setContent {
            YanjiTheme {
                ProfileAiCard(settings = UserSettings(), onClick = {})
            }
        }

        composeRule.onNodeWithText("未配置").assertIsDisplayed()
        composeRule.onNodeWithText("配置服务地址和模型").assertDoesNotExist()
    }

    @Test
    fun examTargetDialogUsesSeparateLabelsAndGoalStepper() {
        composeRule.setContent {
            YanjiTheme {
                ExamTargetDialog(
                    settings = UserSettings(),
                    onDismiss = {},
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithText("目标院校").assertIsDisplayed()
        composeRule.onNodeWithText("填写目标院校").assertIsDisplayed()
        composeRule.onNodeWithText("目标专业").assertIsDisplayed()
        composeRule.onNodeWithText("填写目标专业").assertIsDisplayed()
        composeRule.onNodeWithText("每日学习目标").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("增加每日学习目标").performClick()
        composeRule.onNodeWithText("1h").assertIsDisplayed()
    }

    @Test
    fun openAiPresetLeavesModelForUserToEnter() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as YanjiApplication
        composeRule.setContent {
            CompositionLocalProvider(LocalAppContainer provides application.container) {
                YanjiTheme {
                    AiConfigDialog(onDismissRequest = {})
                }
            }
        }

        composeRule.onNodeWithContentDescription("选择模型提供商").performClick()
        listOf("DeepSeek", "OpenAI", "Kimi", "GLM", "自定义").forEach { provider ->
            composeRule.onNodeWithTag("ai-provider-option-$provider").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("ai-provider-option-OpenAI").performClick()
        composeRule.onNodeWithText("gpt-4o-mini").assertDoesNotExist()
        composeRule.onNodeWithText("选择模型或直接输入模型ID")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun switchingProvidersClearsAndRestoresApiKeyState() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as YanjiApplication
        composeRule.setContent {
            CompositionLocalProvider(LocalAppContainer provides application.container) {
                YanjiTheme {
                    AiConfigDialog(onDismissRequest = {})
                }
            }
        }

        val initialProvider = composeRule.onNodeWithTag("ai-selected-provider")
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        val apiKeyPlaceholder = "sk-... (局域网本地模型可留空)"
        val initiallyHasApiKey = composeRule.onAllNodesWithText(apiKeyPlaceholder)
            .fetchSemanticsNodes()
            .isEmpty()
        val otherProvider = listOf("DeepSeek", "OpenAI", "Kimi", "GLM")
            .first { it != initialProvider }

        fun selectProvider(provider: String) {
            composeRule.onNodeWithContentDescription("选择模型提供商").performClick()
            composeRule.onNodeWithTag("ai-provider-option-$provider").performClick()
        }

        selectProvider(otherProvider)
        composeRule.onNodeWithText(apiKeyPlaceholder).assertIsDisplayed()
        selectProvider(initialProvider)

        val placeholderVisibleAfterReturn = composeRule.onAllNodesWithText(apiKeyPlaceholder)
            .fetchSemanticsNodes()
            .isNotEmpty()
        assertEquals(
            "切回原提供商后应恢复其 API Key 是否已填写的状态",
            initiallyHasApiKey,
            !placeholderVisibleAfterReturn
        )
    }

    @Test
    fun settingsSwitchHasOneAccessibleToggleAction() {
        val checked = mutableStateOf(false)
        var callbackCount = 0
        composeRule.setContent {
            YanjiTheme {
                ProfileSettingsSwitchItem(
                    icon = com.example.yanji.ui.icons.RemixIcons.SunLine,
                    title = "自动沉浸省电",
                    subtitle = "自动切换专注显示",
                    checked = checked.value,
                    onCheckedChange = {
                        callbackCount++
                        checked.value = it
                    }
                )
            }
        }

        val switch = composeRule.onNodeWithText("自动沉浸省电")
        switch.assertHasClickAction()
        assertEquals(Role.Switch, switch.fetchSemanticsNode().config[SemanticsProperties.Role])
        switch.performClick()
        composeRule.runOnIdle {
            assertEquals("整行/开关点击只应切换一次", 1, callbackCount)
            assertTrue("切换后应进入开启态", checked.value)
        }
    }

    @Test
    fun profileSectionsRemainReachableInCompactLightAndDarkLayouts() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as YanjiApplication
        val container = application.container
        val viewModel = ProfileViewModel(container.repository)
        val darkTheme = mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                YanjiTheme(darkTheme = darkTheme.value) {
                    Box(modifier = Modifier.size(width = 320.dp, height = 640.dp)) {
                        ProfileScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        listOf(false, true).forEach { useDarkTheme ->
            composeRule.runOnIdle { darkTheme.value = useDarkTheme }

            composeRule.onNodeWithText("我的").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("偏好与功能").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("数据与隐私").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("关于与支持").performScrollTo().assertIsDisplayed()
        }
    }
}
