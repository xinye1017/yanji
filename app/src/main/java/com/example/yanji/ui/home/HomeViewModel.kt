package com.example.yanji.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.DailyStudySummary
import com.example.yanji.data.ExamSession
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

/**
 * 首页不可变 UiState：一张卡所需的所有展示数据都在这里，页面只渲染不计算。
 *
 * 派生字段（倒计时天数、模考均分等）在 [HomeViewModel] 的 combine 链中随数据变化重算，
 * 页面里不再出现 SimpleDateFormat / Calendar 等展示层派生逻辑。
 */
data class HomeUiState(
    val todayIso: String,
    val settings: UserSettings,
    val examSessions: List<ExamSession>,
    val todaySummary: DailyStudySummary,
    val streakDays: Int,
    /** 目标日期解析失败时为 null（页面回退显示原始日期字符串）。 */
    val daysRemaining: Int?,
    /** 已评分模考的平均分；没有任何评分时为 0.0。 */
    val avgExamScore: Double,
    /** 今日计划（意图侧）。与 todaySummary（结果侧）成对构成首页的「今天」叙事。 */
    val todayTasks: List<StudyTask>,
    /** 计划 id → 已投入秒数。只含真正落到某个计划上的专注时段。 */
    val actualSecondsByTaskId: Map<String, Long>,
    /** 正在计时且属于某条计划的会话所属计划 id；没有进行中的计划专注时为 null。 */
    val runningTaskId: String?,
    /** 可选科目：添加计划时需要按目录过滤可选项。 */
    val subjects: List<Subject>
)

/** 今日语义的聚合载荷：把「结果」与「意图」合成一个值，供外层 combine 消费。 */
private data class TodayFacts(
    val summary: DailyStudySummary,
    val streakDays: Int,
    val tasks: List<StudyTask>,
    val actualSecondsByTaskId: Map<String, Long>,
    val runningTaskId: String?,
    val subjects: List<Subject>
)

/** 计划侧执行进度：已投入时长，以及正在计时的那条计划。 */
private data class PlanProgress(
    val actualSecondsByTaskId: Map<String, Long>,
    val runningTaskId: String?
)

/**
 * 首页 Feature ViewModel。
 *
 * 依赖通过构造函数注入（无默认单例取值），宿主用 `viewModel { HomeViewModel(...) }` 创建；
 * Store 层（Timer/Chat/Note/CheckIn）仍是进程级单例，这里只是状态编排层。
 */
class HomeViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    private val todayIso: String = YanjiTime.todayIso()

    private val todaySummaryFlow = statsRepo.getDailyStudySummaryFlow(todayIso)
    private val weeklySummaryFlow = statsRepo.getWeeklyStudySummaryFlow()

    /** 同步读当前值作为初值，避免首帧空白（与原 collectAsStateWithLifecycle(initialValue=...) 行为一致）。 */
    private val initial = HomeUiState(
        todayIso = todayIso,
        settings = repo.settings.value,
        examSessions = repo.examSessions.value,
        todaySummary = statsRepo.getDailyStudySummary(todayIso),
        streakDays = statsRepo.getWeeklyStudySummary().streakDays,
        daysRemaining = daysRemainingFor(repo.settings.value.targetExamDate),
        avgExamScore = averageScore(repo.examSessions.value),
        todayTasks = emptyList(),
        actualSecondsByTaskId = emptyMap(),
        runningTaskId = null,
        subjects = repo.subjects.value
    )

    // 计划侧执行进度：已落库的实际用时 + 正在计时的那条计划。
    // 先两路合成一个值，是为了不突破 typed combine 的 5 路上限（见 todayFactsFlow）。
    private val planProgressFlow = combine(
        repo.observeTaskActualSeconds(),
        repo.activeFocus
    ) { actualSecondsByTaskId, activeFocus ->
        PlanProgress(actualSecondsByTaskId, activeFocus?.taskId)
    }

    // 今天的两组数据：结果（统计）与意图（计划 + 科目目录 + 计划执行进度）。
    // 先合成一个 todayFacts 再与设置类数据 combine —— typed combine 最多 5 路，
    // 直接堆 6 路会退化到 vararg 版本而丢失类型。
    private val todayFactsFlow = combine(
        todaySummaryFlow,
        weeklySummaryFlow,
        repo.observeStudyTasks(todayIso),
        repo.subjects,
        planProgressFlow
    ) { todaySummary, weekly, tasks, subjects, progress ->
        TodayFacts(
            summary = todaySummary,
            streakDays = weekly.streakDays,
            tasks = tasks,
            actualSecondsByTaskId = progress.actualSecondsByTaskId,
            runningTaskId = progress.runningTaskId,
            subjects = subjects
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repo.settings,
        repo.examSessions,
        todayFactsFlow
    ) { settings, exams, facts ->
        HomeUiState(
            todayIso = todayIso,
            settings = settings,
            examSessions = exams,
            todaySummary = facts.summary,
            streakDays = facts.streakDays,
            daysRemaining = daysRemainingFor(settings.targetExamDate),
            avgExamScore = averageScore(exams),
            todayTasks = facts.tasks,
            actualSecondsByTaskId = facts.actualSecondsByTaskId,
            runningTaskId = facts.runningTaskId,
            subjects = facts.subjects
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initial
    )

    // ---- 动作 ----

    /**
     * 写入今日计划。标题空串与超范围时长在这里挡住，不让脏数据落库。0 表示不限时。
     *
     * @param onCreated 落库后回传新建的计划。「添加并开始专注」要用**真实 id** 建
     *   计划 ↔ 专注关联，不能用调用方临时构造的那条（id 由本层生成，两者对不上）。
     */
    fun addStudyTask(
        subject: Subject,
        title: String,
        plannedMinutes: Int,
        onCreated: (StudyTask) -> Unit = {}
    ) = viewModelScope.launch {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return@launch
        val task = StudyTask(
            date = todayIso,
            subjectId = subject.id,
            subjectName = subject.name,
            title = cleanTitle,
            plannedMinutes = if (plannedMinutes <= 0) 0 else plannedMinutes.coerceIn(5, 720)
        )
        repo.saveStudyTask(task)
        onCreated(task)
    }

    /** 更新已有今日计划。保留原有 id、date、createdAt 与完成状态。 */
    fun updateStudyTask(task: StudyTask, subject: Subject, title: String, plannedMinutes: Int) = viewModelScope.launch {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return@launch
        repo.saveStudyTask(
            task.copy(
                subjectId = subject.id,
                subjectName = subject.name,
                title = cleanTitle,
                plannedMinutes = if (plannedMinutes <= 0) 0 else plannedMinutes.coerceIn(5, 720),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun setStudyTaskCompleted(id: String, completed: Boolean) = viewModelScope.launch {
        repo.setStudyTaskCompleted(id, completed)
    }

    fun deleteStudyTask(id: String) = viewModelScope.launch {
        repo.deleteStudyTask(id)
    }

    // ---- 派生计算 ----

    private fun averageScore(exams: List<ExamSession>): Double {
        if (exams.isEmpty()) return 0.0
        val scored = exams.mapNotNull { it.score }
        return if (scored.isNotEmpty()) scored.average() else 0.0
    }

    /** 单一数据源：settings.targetExamDate；解析失败返回 null（页面回退显示原字符串）。 */
    private fun daysRemainingFor(targetExamDate: String): Int? {
        val target = YanjiTime.parseIsoDate(targetExamDate) ?: return null
        return ChronoUnit.DAYS.between(YanjiTime.today(), target).toInt()
    }
}
