package com.example.yanji.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.DailyStudySummary
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 同一天的随笔分组。[date] 为 ISO 日期，[entries] 组内按 `createdAt` 倒序（最新在上）。
 */
data class NoteDayGroup(
    val date: String,
    val entries: List<NoteEntry>
)

/** 日记域不可变 UiState。 */
data class NoteUiState(
    val notes: List<NoteEntry>,
    /** 按日期分组后的视图模型，供历史页渲染分组头 + 组内条目。 */
    val groups: List<NoteDayGroup> = emptyList()
)

/**
 * 日记 Feature ViewModel：日记列表 + 保存动作 + 当日学时摘要查询。
 * NoteScreen 与 NoteEditorScreen 共享同一实例（Activity scope）。
 */
open class NoteViewModel(
    private val repo: YanjiRepository,
    private val statsRepo: StudyStatisticsRepository
) : ViewModel() {

    /** 声明为 `open` 便于插桩测试注入固定列表（如「当天已有随笔」的只读场景），避免打真实库。 */
    open val uiState: StateFlow<NoteUiState> = repo.noteEntries
        .map { groupsOf(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = groupsOf(repo.noteEntries.value)
        )

    /**
     * 落库入口。声明为 `open` 是为了让插桩测试注入一个「不落库」的子类：
     * 插桩测试必须使用一次性数据库，默认实现会写入 App 全局单例所指向的**用户真实库**，
     * 因此在 UI 测试里必须被覆盖掉，否则会污染用户数据。
     */
    open fun saveNote(entry: NoteEntry) = repo.addOrUpdateNote(entry)

    /** 切换收藏（历史页向右滑 / 编辑页收藏按钮）。 */
    open fun setFavorite(id: String, favorite: Boolean) = repo.setNoteFavorite(id, favorite)

    /** 删除一篇随笔（历史页向左滑 + 二次确认后调用）。 */
    open fun deleteNote(id: String) = repo.deleteNote(id)

    /** 用户设置（同步读缓存），编辑页用于计算初试倒计时。 */
    val settings: StateFlow<UserSettings> get() = repo.settings

    /** 该日期真实学习时长（单一事实来源：FocusSession + ExamSession 聚合）。 */
    fun dailySummaryFor(date: String): DailyStudySummary = statsRepo.getDailyStudySummary(date)

    companion object {
        /**
         * 按日期分组。入参已由 DAO 按 `date DESC, createdAt DESC` 排序；
         * 这里用 `groupBy` 的插入序保证「日期倒序」稳定，组内再按 `createdAt` 倒序。
         * 纯函数，便于单测。
         */
        fun groupsOf(entries: List<NoteEntry>): NoteUiState {
            val groups = entries
                .groupBy { it.date }
                .map { (date, dayEntries) ->
                    NoteDayGroup(
                        date = date,
                        entries = dayEntries.sortedByDescending { it.createdAt }
                    )
                }
                .sortedByDescending { it.date }
            return NoteUiState(notes = entries, groups = groups)
        }
    }
}
