package com.example.yanji.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.YanjiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun searchControlFitsNarrowViewportAndCanSearchAndClear() {
        setScreen(listOf(note()))

        composeRule.onNodeWithTag(NoteScreenTags.SearchControl)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertHasClickAction()
        composeRule.onNodeWithTag(NoteScreenTags.SearchControl).performClick()
        composeRule.onNodeWithTag(NoteScreenTags.SearchInput).performTextInput("关键字")
        composeRule.onNodeWithTag(NoteScreenTags.SearchInput).assertTextEquals("关键字")

        composeRule.onNodeWithContentDescription("清除").performClick()
        composeRule.onNodeWithTag(NoteScreenTags.SearchInput).assertTextEquals("")
    }

    @Test
    fun dailyDurationButtonHasFortyEightDpTargetAndNavigatesToItsDate() {
        var navigatedDate: String? = null
        val date = "1970-01-01"
        setScreen(listOf(note(date)), onNavigateToDailyDetail = { navigatedDate = it })

        val durationButton = composeRule.onNodeWithTag(NoteScreenTags.DailyDurationButton)
        durationButton.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction()
        durationButton.performClick()

        assertEquals(date, navigatedDate)
    }

    private fun setScreen(
        notes: List<NoteEntry>,
        onNavigateToDailyDetail: (String) -> Unit = {}
    ) {
        val repository = YanjiRepository.getInstance()
        val viewModel = ReadOnlyNoteScreenViewModel(
            repository,
            StudyStatisticsRepository(repository),
            notes
        )
        composeRule.setContent {
            YanjiTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 640.dp)) {
                    NoteScreen(
                        onNavigateToDailyDetail = onNavigateToDailyDetail,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun note(date: String = "1970-01-01") = NoteEntry(
        id = "instrumented-note",
        date = date,
        title = "测试随笔",
        content = "可搜索的测试内容"
    )
}

/** UI fixture only; mutation entry points are sealed off from the singleton user repository. */
private class ReadOnlyNoteScreenViewModel(
    repository: YanjiRepository,
    statsRepository: StudyStatisticsRepository,
    notes: List<NoteEntry>
) : NoteViewModel(repository, statsRepository) {
    override val uiState: StateFlow<NoteUiState> = MutableStateFlow(NoteViewModel.groupsOf(notes))

    override fun saveNote(entry: NoteEntry) = Unit
    override fun setFavorite(id: String, favorite: Boolean) = Unit
    override fun deleteNote(id: String) = Unit
}
