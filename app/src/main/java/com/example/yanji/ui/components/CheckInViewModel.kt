package com.example.yanji.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yanji.data.CheckIn
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.checkin.DayCheckInStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 打卡卡片不可变 UiState：全部由 checkIns 流派生，随打卡记录变化刷新。 */
data class CheckInUiState(
    val isCheckedInToday: Boolean,
    val currentStreak: Int,
    val past7Days: List<DayCheckInStatus>,
    val todayCheckIn: CheckIn?
)

/**
 * 每日打卡 Feature ViewModel。
 * CheckInCard 自包含使用：宿主（HomeScreen）不再需要持有仓库引用。
 */
class CheckInViewModel(
    private val repo: YanjiRepository
) : ViewModel() {

    val uiState: StateFlow<CheckInUiState> = repo.checkIns
        .map {
            CheckInUiState(
                isCheckedInToday = repo.isCheckedInToday(),
                currentStreak = repo.getCurrentStreak(),
                past7Days = repo.getPast7DaysCheckInStatus(),
                todayCheckIn = repo.getTodayCheckIn()
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CheckInUiState(
                isCheckedInToday = repo.isCheckedInToday(),
                currentStreak = repo.getCurrentStreak(),
                past7Days = repo.getPast7DaysCheckInStatus(),
                todayCheckIn = repo.getTodayCheckIn()
            )
        )

    fun checkInToday(note: String = "", mood: String = ""): CheckIn = repo.checkInToday(note, mood)
}
