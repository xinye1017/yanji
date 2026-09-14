package com.example.yanji.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.FocusSession
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.Subject
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 专注页不可变 UiState：科目目录、活跃会话、完成事件与今日累计。 */
data class FocusUiState(
    val subjects: List<Subject>,
    val activeSession: FocusSession?,
    val lastCompletedFocus: FocusSession?,
    /** 今日专注时长（不含模考）。 */
    val todayFocusSeconds: Long,
    /** 今日总学时（专注 + 模考，与统计页"今日"口径一致）。 */
    val todayTotalSeconds: Long
)

/**
 * 专注页 Feature ViewModel。
 *
 * 计时事实（开始/暂停/恢复/取消）依旧走「业务层登记 → 前台 Service 计时」两步：
 * VM 只封装业务层那半步，Service 的启动/暂停/结束仍由页面持有 Context 调用，
 * 与 ActiveSessionCoordinator 的分工保持不变。
 */
class FocusViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    private val todayIso: String = YanjiTime.todayIso()

    val uiState: StateFlow<FocusUiState> = combine(
        repo.subjects,
        repo.activeFocus,
        repo.lastCompletedFocus,
        statsRepo.getDailyStudySummaryFlow(todayIso)
    ) { subjects, active, lastCompleted, todaySummary ->
        FocusUiState(
            subjects = subjects,
            activeSession = active,
            lastCompletedFocus = lastCompleted,
            todayFocusSeconds = repo.getTodayFocusDurationSeconds(),
            todayTotalSeconds = todaySummary.totalDurationSeconds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusUiState(
            subjects = repo.subjects.value,
            activeSession = repo.activeFocus.value,
            lastCompletedFocus = repo.lastCompletedFocus.value,
            todayFocusSeconds = repo.getTodayFocusDurationSeconds(),
            todayTotalSeconds = statsRepo.getDailyStudySummary(todayIso).totalDurationSeconds
        )
    )

    // ---- 动作 ----

    /** 登记专注会话到业务层；返回 null 表示已有计时在跑，调用方负责提示。 */
    suspend fun startFocus(
        subjectId: String,
        subjectName: String,
        note: String,
        mode: String
    ): FocusSession? = repo.startFocus(subjectId, subjectName, note, mode)

    /** UI 侧的暂停镜像；真实计时事实由前台 Service 维护。 */
    fun pauseFocus(elapsedSeconds: Long) = repo.pauseFocus(elapsedSeconds)

    fun resumeFocus() = repo.resumeFocus()

    fun cancelFocus() = repo.cancelFocus()

    fun acknowledgeCompletedFocus() = repo.acknowledgeCompletedFocus()

    /** 新增自定义科目 */
    fun addCustomSubject(name: String): Subject = repo.addCustomSubject(name)
}
