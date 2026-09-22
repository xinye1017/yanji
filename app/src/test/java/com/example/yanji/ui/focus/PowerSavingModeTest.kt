package com.example.yanji.ui.focus

import com.example.yanji.data.timer.formatFocusClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSavingModeTest {

    @Test
    fun verifyInactivityTimeoutSpecification() {
        assertEquals(
            "Inactivity timeout for power saving mode should be 30 seconds",
            30_000L,
            INACTIVITY_TIMEOUT_MS
        )
    }

    @Test
    fun verifyCountdownDisplayCalculation() {
        val targetSeconds = 1500L // 25 minutes Pomodoro
        val elapsed = 300L // 5 minutes elapsed

        val remaining = (targetSeconds - elapsed).coerceAtLeast(0L)
        assertEquals(1200L, remaining)
        assertEquals("20:00", formatFocusClock(remaining))

        // When elapsed exceeds target
        val overtimeElapsed = 1600L
        val overtimeRemaining = (targetSeconds - overtimeElapsed).coerceAtLeast(0L)
        assertEquals(0L, overtimeRemaining)
        assertEquals("00:00", formatFocusClock(overtimeRemaining))
    }

    @Test
    fun verifyProgressBarRatioCalculation() {
        val targetSeconds = 1800L // 30 minutes

        // Start
        val initialProgress = (0L.toFloat() / targetSeconds).coerceIn(0f, 1f)
        assertEquals(0f, initialProgress, 0.001f)

        // Mid-way
        val midProgress = (900L.toFloat() / targetSeconds).coerceIn(0f, 1f)
        assertEquals(0.5f, midProgress, 0.001f)

        // Complete
        val fullProgress = (1800L.toFloat() / targetSeconds).coerceIn(0f, 1f)
        assertEquals(1.0f, fullProgress, 0.001f)

        // Overtime cap
        val overProgress = (2000L.toFloat() / targetSeconds).coerceIn(0f, 1f)
        assertEquals(1.0f, overProgress, 0.001f)
    }

    @Test
    fun verifyFlowTimerDisplayCalculation() {
        val targetSeconds = 0L // Positive timing
        val elapsed = 3665L // 1 hour 1 minute 5 seconds

        val display = if (targetSeconds > 0L) {
            (targetSeconds - elapsed).coerceAtLeast(0L)
        } else {
            elapsed
        }
        assertEquals(3665L, display)
        assertEquals("1:01:05", formatFocusClock(display))
    }

    @Test
    fun verifyFocusPreferencesDefaultsAndOptions() {
        assertEquals(true, com.example.yanji.data.timer.FocusPreferences.DEFAULT_AUTO_POWER_SAVING)
        assertEquals(30, com.example.yanji.data.timer.FocusPreferences.DEFAULT_TIMEOUT_SECONDS)
        assertTrue(com.example.yanji.data.timer.FocusPreferences.TIMEOUT_OPTIONS.contains(30))
        assertTrue(com.example.yanji.data.timer.FocusPreferences.TIMEOUT_OPTIONS.contains(15))
        assertTrue(com.example.yanji.data.timer.FocusPreferences.TIMEOUT_OPTIONS.contains(60))
    }

    @Test
    fun verifyStatusTextSpecification() {
        fun resolveStatusText(isPaused: Boolean, targetSeconds: Long): String {
            return when {
                isPaused -> "已暂停"
                targetSeconds > 0L -> "倒计时"
                else -> "计时中"
            }
        }
        assertEquals("已暂停", resolveStatusText(isPaused = true, targetSeconds = 1500L))
        assertEquals("已暂停", resolveStatusText(isPaused = true, targetSeconds = 0L))
        assertEquals("倒计时", resolveStatusText(isPaused = false, targetSeconds = 1500L))
        assertEquals("计时中", resolveStatusText(isPaused = false, targetSeconds = 0L))
    }

    @Test
    fun verifyMathematicalDeviceCenteringFormula() {
        val deviceHeight = 2760
        val statusBarHeight = 114
        val screenHeightInContainer = deviceHeight - statusBarHeight // 2646
        val bodyHeight = 900 // 300dp * 3.0

        val idealBodyTop = (screenHeightInContainer - bodyHeight - statusBarHeight) / 2
        val physicalBodyTop = statusBarHeight + idealBodyTop
        val physicalBodyBottom = physicalBodyTop + bodyHeight
        val physicalCenter = physicalBodyTop + bodyHeight / 2

        // Verify that the body is centered at exactly deviceHeight / 2
        assertEquals(deviceHeight / 2, physicalCenter)

        // Verify distance from top of device equals distance to bottom of device
        val distanceToTopBezel = physicalBodyTop
        val distanceToBottomBezel = deviceHeight - physicalBodyBottom
        assertEquals(distanceToTopBezel, distanceToBottomBezel)
    }
}

