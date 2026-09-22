package com.example.yanji.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.DailyStudySummary
import com.example.yanji.data.FocusSession
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.StudyTimeRange
import com.example.yanji.data.SubjectStudySummary
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * 日/科目明细页的 ViewModel 集：查询参数（日期 / 科目）由导航参数决定，
 * 宿主用 `viewModel(key = date)` 创建按参数隔离的实例。
 */

/** 单日明细。 */
class DailyStudyDetailViewModel(
    private val statsRepo: StudyStatisticsRepository,
    date: String
) : ViewModel() {

    val summary: StateFlow<DailyStudySummary> = statsRepo.getDailyStudySummaryFlow(date)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = statsRepo.getDailyStudySummary(date)
        )
}

/** 科目明细：内含时间范围选择态。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SubjectStudyDetailViewModel(
    private val statsRepo: StudyStatisticsRepository,
    private val subjectId: String
) : ViewModel() {

    private val selectedRange = MutableStateFlow(StudyTimeRange.TODAY)

    val summary: StateFlow<SubjectStudySummary> = selectedRange
        .flatMapLatest { range -> statsRepo.getSubjectStudySummaryFlow(subjectId, range) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = statsRepo.getSubjectStudySummary(subjectId, selectedRange.value)
        )

    fun selectRange(range: StudyTimeRange) {
        selectedRange.value = range
    }
}

/** 专注单条记录详情：备注编辑 + 删除。 */
class FocusSessionDetailViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    val focusSessions get() = repo.focusSessions

    fun getSessionFlow(sessionId: String): Flow<FocusSession?> =
        repo.getFocusSessionByIdFlow(sessionId)

    fun updateSessionNote(sessionId: String, note: String) =
        statsRepo.updateSessionNote(sessionId, isExam = false, note = note)

    fun deleteSession(sessionId: String) = repo.deleteFocusSession(sessionId)
}
