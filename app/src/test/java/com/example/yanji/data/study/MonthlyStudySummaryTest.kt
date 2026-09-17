package com.example.yanji.data.study

import com.example.yanji.data.DayBarData
import com.example.yanji.data.MonthlyStudySummary
import com.example.yanji.data.YanjiTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class MonthlyStudySummaryTest {

    @Test
    fun `monthly summary contains all days of current month`() {
        val today = YanjiTime.today()
        val daysInMonth = today.lengthOfMonth()
        val days = (1..daysInMonth).map { dayNum ->
            DayBarData(
                date = today.withDayOfMonth(dayNum).format(YanjiTime.isoDateFormatter),
                dayLabel = "${dayNum}日",
                durationSeconds = if (dayNum == 1) 3600L else 0L,
                isToday = (dayNum == today.dayOfMonth),
                subjectDistribution = emptyMap()
            )
        }

        val summary = MonthlyStudySummary(
            totalDurationSeconds = 3600L,
            dailyAverageSeconds = 3600L / daysInMonth,
            activeDays = 1,
            longestSession = null,
            examCount = 0,
            streakDays = if (today.dayOfMonth == 1) 1 else 0,
            year = today.year,
            month = today.monthValue,
            firstDayOfWeek = today.withDayOfMonth(1).dayOfWeek,
            days = days
        )

        assertEquals(daysInMonth, summary.days.size)
        assertEquals("1日", summary.days.first().dayLabel)
        assertEquals("${daysInMonth}日", summary.days.last().dayLabel)
        assertEquals(1, summary.activeDays)
        assertTrue(summary.firstDayOfWeek in DayOfWeek.values())
    }
}
