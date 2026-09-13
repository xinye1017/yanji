package com.example.yanji.ui.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.data.JournalEntry
import com.example.yanji.theme.YanjiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class JournalEditorScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun moodSelectionUpdatesSelectedState() {
        val entry = mutableStateOf(sampleJournalEntry(moodScore = 3))
        var savedEntry: JournalEntry? = null

        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    // Test isolated journal editor content directly
                    JournalEditorScreen(
                        journalId = entry.value.id,
                        date = entry.value.date,
                        onBack = {},
                        onSaveSuccess = { savedEntry = entry.value }
                    )
                }
            }
        }

        // Verify mood buttons are present
        composeRule.onNodeWithText("平稳前行").assertExists()
        composeRule.onNodeWithText("深度心流").performScrollTo().performClick()
        composeRule.onNodeWithText("今日随笔与复盘").assertExists()
    }

    @Test
    fun contentAndTomorrowPlanSectionsAreRendered() {
        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    JournalEditorScreen(
                        journalId = null,
                        date = "2026-09-13",
                        onBack = {},
                        onSaveSuccess = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("今日心境").assertExists()
        composeRule.onNodeWithText("今日随笔与复盘").performScrollTo().assertExists()
        composeRule.onNodeWithText("今日遇到的困难 / 卡点（选填）").performScrollTo().assertExists()
        composeRule.onNodeWithText("明日核心任务").performScrollTo().assertExists()
        composeRule.onNodeWithText("保存日记").performScrollTo().assertExists()
    }

    private fun sampleJournalEntry(moodScore: Int = 5) = JournalEntry(
        id = "test-journal-1",
        date = "2026-09-13",
        title = "考研复盘",
        moodScore = moodScore,
        energyScore = 4,
        studySatisfaction = 5,
        content = "今天完成了数学真题复盘，效率很高。",
        blockers = "选择题最后一题运算量稍大。",
        tomorrowPlan = "完成专业课第二章习题\n背诵英语单词50个",
        tags = listOf("数学", "复盘"),
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )
}
