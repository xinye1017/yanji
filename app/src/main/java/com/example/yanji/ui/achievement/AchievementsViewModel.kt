package com.example.yanji.ui.achievement

import androidx.lifecycle.ViewModel
import com.example.yanji.data.AchievementRepository

/** Thin UI boundary around the existing achievement repository. */
class AchievementsViewModel(
    repository: AchievementRepository
) : ViewModel() {
    val achievements = repository.achievements
    val unlockedCount = repository.unlockedCount
    val totalCount: Int = repository.totalCount
}
