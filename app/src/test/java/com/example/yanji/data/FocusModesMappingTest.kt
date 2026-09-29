package com.example.yanji.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 今日计划迁到首页后，「用户写下一个分钟数 → 该用哪个计时模式」这条规则
 * 从两处实现（专注页的 `modeForTask` 与专注准备页的 `quietModeForMinutes`）
 * 收敛到 [FocusModes.forPlannedMinutes] 单一实现。
 *
 * 这里守住它与 [FocusModes.targetSeconds] 的**双向自洽**：
 * 凡是落在一个标准档位上的分钟数，取回的模式必须解析回同一个秒数。
 * 两份实现曾经各自为政，任何一侧单独调整档位（例如新增 30 分钟档），
 * 都不会有编译错误提示，只会让「计划写 30 分钟、实际计时 25 分钟」这类偏差静静发生。
 */
class FocusModesMappingTest {

    @Test
    fun standardDurationsMapToTheirNamedMode() {
        assertEquals(FocusModes.POMODORO_25, FocusModes.forPlannedMinutes(25))
        assertEquals(FocusModes.POMODORO_45, FocusModes.forPlannedMinutes(45))
        assertEquals(FocusModes.DEEP_60, FocusModes.forPlannedMinutes(60))
        assertEquals(FocusModes.BIG_90, FocusModes.forPlannedMinutes(90))
    }

    @Test
    fun offGridDurationsGetAnExplicitMinuteMode() {
        assertEquals("30分钟专注", FocusModes.forPlannedMinutes(30))
        assertEquals("50分钟专注", FocusModes.forPlannedMinutes(50))
    }

    @Test
    fun everyStandardDurationRoundTripsBackToItsOwnLength() {
        val standard = listOf(25, 45, 60, 90)
        for (minutes in standard) {
            val mode = FocusModes.forPlannedMinutes(minutes)
            assertEquals(
                "「$minutes 分钟」应解析回 $minutes 分钟，但模式 $mode 解析成了别处",
                minutes * 60L,
                FocusModes.targetSeconds(mode)
            )
        }
    }

    @Test
    fun offGridDurationsAlsoRoundTrip() {
        for (minutes in listOf(5, 30, 50, 120)) {
            val mode = FocusModes.forPlannedMinutes(minutes)
            assertEquals(
                "「$minutes 分钟」的回环时长不符（模式 $mode）",
                minutes * 60L,
                FocusModes.targetSeconds(mode)
            )
        }
    }
}
