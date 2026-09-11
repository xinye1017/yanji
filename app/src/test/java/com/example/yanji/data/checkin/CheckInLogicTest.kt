package com.example.yanji.data.checkin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * 打卡纯逻辑测试。连续天数是成就系统的输入，这里的边界一旦算错，
 * 用户会"莫名丢连续记录"——比多算一天更伤积极性。
 */
class CheckInLogicTest {

    private fun todayMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    private fun daysAgoStr(days: Int): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayMs(); add(Calendar.DAY_OF_YEAR, -days)
        }
        return CheckInLogic.dateStr(cal.timeInMillis)
    }

    private fun checkIn(date: String, streak: Int = 1) =
        com.example.yanji.data.CheckIn(date = date, checkInTime = 0L, streak = streak)

    @Test
    fun `empty history has zero streak`() {
        assertEquals(0, CheckInLogic.currentStreak(emptySet(), todayMs()))
    }

    @Test
    fun `today only gives streak of one`() {
        assertEquals(1, CheckInLogic.currentStreak(setOf(daysAgoStr(0)), todayMs()))
    }

    @Test
    fun `consecutive days including today count fully`() {
        val dates = setOf(daysAgoStr(0), daysAgoStr(1), daysAgoStr(2))
        assertEquals(3, CheckInLogic.currentStreak(dates, todayMs()))
    }

    @Test
    fun `missing today still counts yesterday backwards`() {
        // 昨天和前天都打了，今天还没打：连续记录不能被清零
        val dates = setOf(daysAgoStr(1), daysAgoStr(2))
        assertEquals(2, CheckInLogic.currentStreak(dates, todayMs()))
    }

    @Test
    fun `a gap stops the count`() {
        val dates = setOf(daysAgoStr(0), daysAgoStr(1), daysAgoStr(3), daysAgoStr(4))
        assertEquals(2, CheckInLogic.currentStreak(dates, todayMs()))
    }

    @Test
    fun `next streak value continues from yesterday`() {
        val existing = listOf(checkIn(daysAgoStr(1), streak = 4))
        assertEquals(5, CheckInLogic.nextStreakValue(existing, todayMs()))
    }

    @Test
    fun `next streak value starts at one without history`() {
        assertEquals(1, CheckInLogic.nextStreakValue(emptyList(), todayMs()))
    }

    @Test
    fun `next streak value is idempotent when today already exists`() {
        val existing = listOf(checkIn(daysAgoStr(0), streak = 3))
        assertEquals(3, CheckInLogic.nextStreakValue(existing, todayMs()))
    }

    @Test
    fun `past7Days returns seven entries with today last`() {
        val result = CheckInLogic.past7Days(emptySet(), todayMs())
        assertEquals(7, result.size)
        assertEquals(true, result.last().isToday)
        assertEquals("今天", result.last().dayLabel)
        assertEquals(false, result.first().isToday)
    }

    @Test
    fun `past7Days marks checked in dates`() {
        val today = daysAgoStr(0)
        val twoDaysAgo = daysAgoStr(2)
        val result = CheckInLogic.past7Days(setOf(today, twoDaysAgo), todayMs())
        assertEquals(true, result.last().isCheckedIn)
        assertEquals(true, result[4].isCheckedIn)
        assertEquals(false, result[5].isCheckedIn)
    }
}
