package com.example.yanji.data.checkin

import com.example.yanji.data.CheckIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 打卡条目在某一天的展示状态。 */
data class DayCheckInStatus(
    val date: String,
    val dayLabel: String,
    val isToday: Boolean,
    val isCheckedIn: Boolean
)

/**
 * 打卡的纯逻辑：连续天数计算、近 7 天状态。
 *
 * 连续天数是成就系统的输入（`streak_7` 之类），而且规则里有"今天没打卡就从昨天数"这种
 * 容易写错的分支。抽成纯函数后可以用 JVM 单测把边界钉死，不必靠真机手点。
 */
internal object CheckInLogic {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun dateStr(epochMs: Long): String = dateFormat.format(Date(epochMs))

    /**
     * 连续打卡天数。
     * 今天已打卡则从今天往前数；今天没打卡则从昨天往前数——这样凌晨打开 App
     * 不会把昨天的连续成果清零（行为与抽取前一致）。
     */
    fun currentStreak(dates: Set<String>, now: Long): Int {
        if (dates.isEmpty()) return 0
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        var streak = 0
        if (dates.contains(dateStr(now))) {
            streak = 1
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (!dates.contains(dateStr(cal.timeInMillis))) return 0
            streak = 1
        }
        while (true) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (dates.contains(dateStr(cal.timeInMillis))) streak++ else break
        }
        return streak
    }

    /** 今日打卡时应写入的 streak 值（date 为主键，已打过则沿用原值）。 */
    fun nextStreakValue(existing: List<CheckIn>, now: Long): Int {
        val todayStr = dateStr(now)
        existing.find { it.date == todayStr }?.let { return it.streak }
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }
        existing.find { it.date == dateStr(cal.timeInMillis) }?.let { return it.streak + 1 }
        return 1
    }

    /** 近 7 天（含今天）的打卡状态，用于首页打卡条。 */
    fun past7Days(checkInDates: Set<String>, now: Long): List<DayCheckInStatus> {
        val dayNameSdf = SimpleDateFormat("E", Locale.CHINESE)
        val result = mutableListOf<DayCheckInStatus>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -i) }
            val dStr = dateStr(cal.timeInMillis)
            result.add(
                DayCheckInStatus(
                    date = dStr,
                    dayLabel = if (i == 0) "今天" else dayNameSdf.format(cal.time),
                    isToday = i == 0,
                    isCheckedIn = dStr in checkInDates
                )
            )
        }
        return result
    }
}
