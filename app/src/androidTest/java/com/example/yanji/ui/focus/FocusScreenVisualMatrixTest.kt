package com.example.yanji.ui.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.SessionStatus
import com.example.yanji.theme.YanjiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual matrix verification across standard Android viewports:
 * 1. 360x800 dp (1.0x density / 1.0x font scale) — Compact budget devices
 * 2. 390x844 dp (1.0x density / 1.0x font scale) — Modern mainstream devices
 * 3. 412x915 dp (1.0x density / 1.0x font scale) — Large display / flagship devices
 * 4. 390x844 dp (1.0x density / 1.3x font scale) — Accessibility enlarged font
 */
@RunWith(AndroidJUnit4::class)
class FocusScreenVisualMatrixTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    private val session = FocusSession(
        id = "matrix-test-session",
        subjectId = "math_advanced",
        subjectName = "高等数学",
        startTime = 0L,
        endTime = 0L,
        durationSeconds = 1800L,
        mode = FocusModes.COUNT_UP,
        status = SessionStatus.RUNNING,
        createdAt = 0L
    )

    private fun testViewport(width: Dp, height: Dp, fontScale: Float = 1.0f) {
        val elapsedSeconds = mutableStateOf(1800L)
        composeRule.setContent {
            val currentDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity, fontScale = fontScale)
            ) {
                YanjiTheme {
                    Box(modifier = Modifier.size(width = width, height = height)) {
                        ActiveFocusContent(
                            session = session,
                            elapsedSeconds = elapsedSeconds,
                            onPause = {},
                            onResume = {},
                            onFinish = {},
                            onCancel = {}
                        )
                    }
                }
            }
        }

        // Verify primary info and buttons are displayed and reachable
        composeRule.onNodeWithText("高等数学").assertIsDisplayed()
        composeRule.onNodeWithText("持续专注中").assertIsDisplayed()
        composeRule.onNodeWithText("30:00").assertIsDisplayed()
        composeRule.onNodeWithText("暂停").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("放弃").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("完成").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun viewport360x800_StandardCompact() {
        testViewport(width = 360.dp, height = 800.dp, fontScale = 1.0f)
    }

    @Test
    fun viewport390x844_StandardMainstream() {
        testViewport(width = 390.dp, height = 844.dp, fontScale = 1.0f)
    }

    @Test
    fun viewport412x915_StandardFlagship() {
        testViewport(width = 412.dp, height = 915.dp, fontScale = 1.0f)
    }

    @Test
    fun viewport390x844_LargeFontAccessibility() {
        testViewport(width = 390.dp, height = 844.dp, fontScale = 1.3f)
    }
}
