package com.example.yanji.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.data.DayBarData
import com.example.yanji.theme.YanjiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsTrendChartInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val days = listOf(
        DayBarData(
            date = "2026-09-21",
            dayLabel = "周一",
            durationSeconds = 3_600L,
            isToday = false,
            subjectDistribution = emptyMap()
        ),
        DayBarData(
            date = "2026-09-22",
            dayLabel = "周二",
            durationSeconds = 7_200L,
            isToday = true,
            subjectDistribution = emptyMap()
        )
    )

    @Test
    fun lineRevealSettlesAndModeSwitchKeepsDayDataAccessible() {
        var selectedMode = TrendMode.LINE
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            YanjiTheme {
                var mode by remember { mutableStateOf(TrendMode.LINE) }
                StatsTrendChart(
                    selectedTimeTab = 0,
                    days = days,
                    trendChartMode = mode,
                    onSelectTrendMode = {
                        mode = it
                        selectedMode = it
                    },
                    onSelectDay = {}
                )
            }
        }

        // Advance through an intermediate draw frame, then beyond the line reveal duration.
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithTag(StatsTrendChartTags.LinePlot).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(StatsTrendChartTags.LinePlot).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("周一，学习时长 1h").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("周二，学习时长 2h，今日").assertIsDisplayed()

        composeRule.onNodeWithText("柱状").performClick()
        composeRule.mainClock.advanceTimeBy(700L)
        composeRule.waitForIdle()
        assertEquals(TrendMode.BAR, selectedMode)
        composeRule.onNodeWithContentDescription("周一，学习时长 1h").assertIsDisplayed()

        composeRule.onNodeWithText("折线").performClick()
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(StatsTrendChartTags.LinePlot).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("周二，学习时长 2h，今日").assertIsDisplayed()
    }
}
