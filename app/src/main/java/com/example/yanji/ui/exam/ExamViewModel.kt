package com.example.yanji.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.AiAnalysis
import com.example.yanji.data.ExamSession
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 模考域不可变 UiState：模考列表、AI 分析与最近完成事件。 */
data class ExamUiState(
    val examSessions: List<ExamSession>,
    val aiAnalyses: List<AiAnalysis>,
    val lastCompletedExam: ExamSession?
)

/**
 * 模考 Feature ViewModel：被模考发起页、成绩历史与模考详情三个界面共享
 * （导航为手写 screenStack，无 per-destination ViewModelStore，Activity scope 天然共享）。
 */
class ExamViewModel(
    private val repo: YanjiRepository
) : ViewModel() {

    val uiState: StateFlow<ExamUiState> = combine(
        repo.examSessions,
        repo.aiAnalyses,
        repo.lastCompletedExam
    ) { exams, analyses, lastCompleted ->
        ExamUiState(exams, analyses, lastCompleted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExamUiState(
            examSessions = repo.examSessions.value,
            aiAnalyses = repo.aiAnalyses.value,
            lastCompletedExam = repo.lastCompletedExam.value
        )
    )

    // ---- 动作 ----

    /** 登记一场模考；返回 null 表示已有计时在跑（专注与模考互斥），调用方负责提示。 */
    suspend fun startExamSession(
        subjectId: String,
        subjectName: String,
        plannedDurationSeconds: Long
    ): ExamSession? = repo.startExamSession(subjectId, subjectName, plannedDurationSeconds)

    fun saveExamResult(session: ExamSession) = repo.addExamSession(session)

    fun abandonExam() = repo.abandonExam()

    fun acknowledgeCompletedExam() = repo.acknowledgeCompletedExam()

    fun deleteExam(id: String) = repo.deleteExamSession(id)

    suspend fun generateAnalysis(periodDays: Int = 7): AiAnalysis = repo.generateAiAnalysis(periodDays)
}
