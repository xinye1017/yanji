package com.example.yanji.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusSession
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.StudyTask
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.Subject
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 专注页不可变 UiState：科目目录、活跃会话、完成事件与今日累计。 */
data class FocusUiState(
    val subjects: List<Subject>,
    val activeSession: FocusSession?,
    val lastCompletedFocus: FocusSession?,
    val quickStartPresets: List<QuickStartPreset>,
    val todayTasks: List<StudyTask>,
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

    private val planningFlow = combine(
        repo.observeQuickStartPresets(),
        repo.observeStudyTasks(todayIso)
    ) { presets, tasks ->
        presets
            .filter { it.type == QuickStartPreset.TYPE_CUSTOM }
            .sortedByDescending { it.createdAt } to tasks
    }

    val uiState: StateFlow<FocusUiState> = combine(
        repo.subjects,
        repo.activeFocus,
        repo.lastCompletedFocus,
        statsRepo.getDailyStudySummaryFlow(todayIso),
        planningFlow
    ) { subjects, active, lastCompleted, todaySummary, planning ->
        FocusUiState(
            subjects = subjects,
            activeSession = active,
            lastCompletedFocus = lastCompleted,
            quickStartPresets = planning.first,
            todayTasks = planning.second,
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
            quickStartPresets = emptyList(),
            todayTasks = emptyList(),
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

    /** UI 侧的暂停意图；真实计时事实由前台 Service 维护（落库字段也由 Service 写）。 */
    fun pauseFocus() = repo.pauseFocus()

    fun resumeFocus() = repo.resumeFocus()

    fun cancelFocus() = repo.cancelFocus()

    fun acknowledgeCompletedFocus() = repo.acknowledgeCompletedFocus()

    suspend fun saveQuickStartPreset(subject: Subject, mode: String, note: String) {
        repo.saveQuickStartPreset(
            QuickStartPreset(
                type = QuickStartPreset.TYPE_CUSTOM,
                label = subject.name,
                subLabel = mode,
                subjectId = subject.id,
                subjectName = subject.name,
                mode = mode,
                note = note.trim()
            )
        )
    }

    fun deleteQuickStartPreset(id: String) = viewModelScope.launch {
        repo.deleteQuickStartPreset(id)
    }

    fun addStudyTask(subject: Subject, title: String, plannedMinutes: Int) = viewModelScope.launch {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return@launch
        repo.saveStudyTask(
            StudyTask(
                date = todayIso,
                subjectId = subject.id,
                subjectName = subject.name,
                title = cleanTitle,
                plannedMinutes = plannedMinutes.coerceIn(5, 360)
            )
        )
    }

    fun setStudyTaskCompleted(id: String, completed: Boolean) = viewModelScope.launch {
        repo.setStudyTaskCompleted(id, completed)
    }

    fun deleteStudyTask(id: String) = viewModelScope.launch {
        repo.deleteStudyTask(id)
    }

    fun modeForTask(task: StudyTask): String {
        val minutes = task.plannedMinutes.coerceAtLeast(1)
        return when (minutes) {
            25 -> FocusModes.POMODORO_25
            45 -> FocusModes.POMODORO_45
            60 -> FocusModes.DEEP_60
            90 -> FocusModes.BIG_90
            else -> "${minutes}分钟专注"
        }
    }

    companion object {
        /**
         * 纯函数：校验手动补记起止时间与时长合法性。
         *
         * @return 错误提示文案，若校验通过则返回 null。
         */
        fun validateManualFocusSession(
            startTime: Long,
            endTime: Long,
            now: Long = System.currentTimeMillis()
        ): String? {
            if (startTime >= endTime) {
                return "开始时间必须早于结束时间"
            }
            val durationSeconds = (endTime - startTime) / 1000L
            if (durationSeconds < 60L) {
                return "专注时长不足 1 分钟，不予保存"
            }
            if (durationSeconds > 16L * 3600L) {
                return "单次专注时长不能超过 16 小时"
            }
            if (endTime > now + 60_000L) {
                return "结束时间不能在未来"
            }
            return null
        }
    }

    /**
     * 手动补记专注记录。
     *
     * 业务校验规则：
     * 1. 最小时长 1 分钟（60 秒）；
     * 2. 最大时长 16 小时；
     * 3. 结束时间不能在未来（允许 60 秒时钟误差）；
     * 4. 开始时间必须早于结束时间。
     */
    suspend fun addManualFocusSession(
        subjectId: String,
        subjectName: String,
        startTime: Long,
        endTime: Long,
        note: String = "",
        mode: String = "补记专注"
    ): Result<FocusSession> {
        val now = System.currentTimeMillis()
        val error = validateManualFocusSession(startTime, endTime, now)
        if (error != null) {
            return Result.failure(IllegalArgumentException(error))
        }
        val durationSeconds = (endTime - startTime) / 1000L
        val session = FocusSession(
            id = java.util.UUID.randomUUID().toString(),
            subjectId = subjectId,
            subjectName = subjectName,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            pausedDurationSeconds = 0L,
            pauseCount = 0,
            mode = mode,
            note = note,
            status = com.example.yanji.data.SessionStatus.COMPLETED,
            createdAt = now
        )

        repo.addFocusSession(session)
        return Result.success(session)
    }
}
