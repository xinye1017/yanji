package com.example.yanji.ui.focus

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.theme.YanjiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the active focus UI in isolation, without starting a timer or writing records. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ActiveFocusContentInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun pauseResumeAndFinishDispatchTheirOwnCallbacks() {
        val session = mutableStateOf(focusSession())
        val elapsed = mutableStateOf(125L)
        var pauses = 0
        var resumes = 0
        var finishes = 0
        var cancels = 0
        composeRule.setContent {
            YanjiTheme {
                ActiveFocusContent(
                    session = session.value,
                    elapsedSeconds = elapsed,
                    onPause = {
                        pauses++
                        session.value = session.value.copy(status = SessionStatus.PAUSED)
                    },
                    onResume = {
                        resumes++
                        session.value = session.value.copy(status = SessionStatus.RUNNING)
                    },
                    onFinish = { finishes++ },
                    onCancel = { cancels++ }
                )
            }
        }

        capture("focus-flow.png")
        // 暂停是可见的 primary 控件（不再藏在「点击圆环」里）。
        composeRule.onNodeWithText("暂停").performScrollTo().performClick()
        composeRule.onNodeWithText("继续专注").assertExists()
        capture("focus-paused.png")
        composeRule.onNodeWithText("继续专注").performScrollTo().performClick()
        composeRule.onNodeWithText("暂停").assertExists()
        composeRule.onNodeWithText("完成").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, pauses)
            assertEquals(1, resumes)
            assertEquals(1, finishes)
            assertEquals(0, cancels)
        }
    }

    @Test
    fun finishIsDisabledUntilOneMinuteSoItNeverSilentlyDiscards() {
        // 不足 1 分钟：完成必须被禁用并给出原因，绝不能点下去才静默丢弃。
        val elapsed = mutableStateOf(30L)
        var finishes = 0
        composeRule.setContent {
            YanjiTheme {
                ActiveFocusContent(
                    session = focusSession(),
                    elapsedSeconds = elapsed,
                    onPause = {},
                    onResume = {},
                    onFinish = { finishes++ },
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("已专注不足 1 分钟，完成后不会生成记录").assertExists()
        composeRule.onNodeWithText("完成").performScrollTo().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, finishes) }

        composeRule.runOnIdle { elapsed.value = 60L }
        composeRule.onNodeWithText("完成").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, finishes) }
    }

    @Test
    fun abandoningRequiresConfirmationAndCanBeDismissed() {
        var cancels = 0
        val elapsed = mutableStateOf(125L)
        composeRule.setContent {
            YanjiTheme {
                ActiveFocusContent(
                    session = focusSession(),
                    elapsedSeconds = elapsed,
                    onPause = {},
                    onResume = {},
                    onFinish = {},
                    onCancel = { cancels++ }
                )
            }
        }

        composeRule.onNodeWithText("放弃").performScrollTo().performClick()
        composeRule.onNodeWithText("放弃本次专注？").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, cancels) }
        composeRule.onNodeWithText("保留记录").performClick()
        composeRule.onNodeWithText("放弃本次专注？").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, cancels) }

        composeRule.onNodeWithText("放弃").performScrollTo().performClick()
        composeRule.onNodeWithText("确认放弃").performClick()
        composeRule.runOnIdle { assertEquals(1, cancels) }
    }

    @Test
    fun countUpShowsMinutesThenHoursAtOneHourBoundary() {
        val elapsedSeconds = mutableStateOf(3_599L)
        composeRule.setContent {
            YanjiTheme {
                ActiveFocusContent(
                    session = focusSession(),
                    elapsedSeconds = elapsedSeconds,
                    onPause = {},
                    onResume = {},
                    onFinish = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("59:59").assertExists()
        composeRule.runOnIdle { elapsedSeconds.value = 3_600L }
        composeRule.onNodeWithText("1:00:00").assertExists()
        composeRule.runOnIdle { elapsedSeconds.value = 3_661L }
        composeRule.onNodeWithText("1:01:01").assertExists()
    }

    @Test
    fun countdownReachesZeroWithoutShowingNegativeTime() {
        val elapsedSeconds = mutableStateOf(1_499L)
        composeRule.setContent {
            YanjiTheme {
                ActiveFocusContent(
                    session = focusSession(mode = FocusModes.POMODORO_25),
                    elapsedSeconds = elapsedSeconds,
                    onPause = {},
                    onResume = {},
                    onFinish = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("倒计时 · 25 分钟").assertExists()
        composeRule.onNodeWithText("剩余时间").assertExists()
        composeRule.onNodeWithText("00:01").assertExists()
        capture("focus-countdown.png")
        // 进度不再用重复的「已完成 N%」文字表达：大数字 + 环已经是同一件事。
        composeRule.runOnIdle { elapsedSeconds.value = 1_500L }
        composeRule.onNodeWithText("00:00").assertExists()
        composeRule.runOnIdle { elapsedSeconds.value = 1_507L }
        composeRule.onNodeWithText("00:00").assertExists()
    }

    @Test
    fun compactScreenWithLargeFontKeepsEveryActionReachable() {
        val session = mutableStateOf(focusSession())
        val elapsed = mutableStateOf(3_661L)
        var finishes = 0
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale = 2f)) {
                YanjiTheme {
                    Box(Modifier.size(width = 320.dp, height = 480.dp)) {
                        ActiveFocusContent(
                            session = session.value,
                            elapsedSeconds = elapsed,
                            onPause = { session.value = session.value.copy(status = SessionStatus.PAUSED) },
                            onResume = { session.value = session.value.copy(status = SessionStatus.RUNNING) },
                            onFinish = { finishes++ },
                            onCancel = {}
                        )
                    }
                }
            }
        }

        capture("focus-large-font.png")
        composeRule.onNodeWithText("暂停").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("继续专注").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("完成").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, finishes) }
        composeRule.onNodeWithText("放弃").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("保留记录").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("放弃本次专注？").assertDoesNotExist()
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir!!
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun focusSession(mode: String = FocusModes.COUNT_UP) = FocusSession(
        id = "isolated-focus-ui-test",
        subjectId = "math_advanced",
        subjectName = "高等数学",
        startTime = 0L,
        endTime = 0L,
        durationSeconds = 0L,
        mode = mode,
        status = SessionStatus.RUNNING,
        createdAt = 0L
    )
}
