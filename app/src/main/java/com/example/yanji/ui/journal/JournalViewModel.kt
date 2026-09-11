package com.example.yanji.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.DailyStudySummary
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 日记域不可变 UiState。 */
data class JournalUiState(
    val journals: List<JournalEntry>
)

/**
 * 日记 Feature ViewModel：日记列表 + 保存动作 + 当日学时摘要查询。
 * JournalScreen 与 JournalEditorScreen 共享同一实例（Activity scope）。
 */
class JournalViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    val uiState: StateFlow<JournalUiState> = repo.journalEntries
        .map { JournalUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = JournalUiState(repo.journalEntries.value)
        )

    fun saveJournal(entry: JournalEntry) = repo.addOrUpdateJournal(entry)

    /** 该日期真实学习时长（单一事实来源：FocusSession + ExamSession 聚合）。 */
    fun dailySummaryFor(date: String): DailyStudySummary = statsRepo.getDailyStudySummary(date)
}
