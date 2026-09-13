package com.example.yanji.data.checkin

import com.example.yanji.data.CheckIn
import com.example.yanji.data.YanjiTime
import java.time.format.DateTimeFormatter
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

    fun dateStr(epochMs: Long): String =
        YanjiTime.localDate(epochMs).format(YanjiTime.isoDateFormatter)

    /**
     * 连续打卡天数。
     * 今天已打卡则从今天往前数；今天没打卡则从昨天往前数——这样凌晨打开 App
     * 不会把昨天的连续成果清零（行为与抽取前一致）。
     */
    fun currentStreak(dates: Set<String>, now: Long): Int {
        if (dates.isEmpty()) return 0
        var day = YanjiTime.localDate(now)
        var streak = 0
        if (dates.contains(dateStr(now))) {
            streak = 1
        } else {
            day = day.minusDays(1)
            if (!dates.contains(day.format(YanjiTime.isoDateFormatter))) return 0
            streak = 1
        }
        while (true) {
            day = day.minusDays(1)
            if (dates.contains(day.format(YanjiTime.isoDateFormatter))) streak++ else break
        }
        return streak
    }

    /** 今日打卡时应写入的 streak 值（date 为主键，已打过则沿用原值）。 */
    fun nextStreakValue(existing: List<CheckIn>, now: Long): Int {
        val todayStr = dateStr(now)
        existing.find { it.date == todayStr }?.let { return it.streak }
        val yesterday = YanjiTime.localDate(now).minusDays(1).format(YanjiTime.isoDateFormatter)
        existing.find { it.date == yesterday }?.let { return it.streak + 1 }
        return 1
    }

    /** 近 7 天（含今天）的打卡状态，用于首页打卡条。 */
    fun past7Days(checkInDates: Set<String>, now: Long): List<DayCheckInStatus> {
        val weekdayFormatter = DateTimeFormatter.ofPattern("E", Locale.CHINESE)
        val result = mutableListOf<DayCheckInStatus>()
        val today = YanjiTime.localDate(now)
        for (i in 6 downTo 0) {
            val day = today.minusDays(i.toLong())
            val dStr = day.format(YanjiTime.isoDateFormatter)
            result.add(
                DayCheckInStatus(
                    date = dStr,
                    dayLabel = if (i == 0) "今天" else day.format(weekdayFormatter),
                    isToday = i == 0,
                    isCheckedIn = dStr in checkInDates
                )
            )
        }
        return result
    }
}
