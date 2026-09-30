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
import kotlinx.coroutines.flow.asStateFlow
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

    private val _selectedRange = MutableStateFlow(StudyTimeRange.TODAY)

    /**
     * 时间范围选择的唯一事实来源。只读暴露：页面只 collect + 回调，不再自己存一份。
     *
     * 此前页面用 rememberSaveable 存了一份再经 LaunchedEffect 异步推给 VM，导致
     * 「选中的范围」与「实际查询用的范围」之间存在一帧滞后，且 VM 沦为宿主的下游。
     */
    val selectedRange: StateFlow<StudyTimeRange> = _selectedRange.asStateFlow()

    val summary: StateFlow<SubjectStudySummary> = _selectedRange
        .flatMapLatest { range -> statsRepo.getSubjectStudySummaryFlow(subjectId, range) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = statsRepo.getSubjectStudySummary(subjectId, _selectedRange.value)
        )

    fun selectRange(range: StudyTimeRange) {
        _selectedRange.value = range
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
