package com.example.yanji.data.checkin

import com.example.yanji.data.CheckIn
import com.example.yanji.data.db.CheckInEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 打卡的状态与动作归属者。
 *
 * 抽出来的原因：连续天数（streak）是成就系统的输入，规则分支多且容易被写错，
 * 需要能对 [CheckInLogic] 做纯函数级别的测试；同时打卡状态不该继续堆在
 * `YanjiRepository` 这个 God Object 里。
 */
internal class CheckInStore(
    private val scope: CoroutineScope,
    private val dbProvider: () -> YanjiDatabase?
) {

    private val _checkIns = MutableStateFlow<List<CheckIn>>(emptyList())
    val checkIns: StateFlow<List<CheckIn>> = _checkIns.asStateFlow()

    fun bind(db: YanjiDatabase) {
        scope.launch {
            db.checkInDao().getAllFlow().collect { entities ->
                _checkIns.value = entities.map { it.toDomainModel() }
            }
        }
    }

    fun isCheckedInToday(now: Long = System.currentTimeMillis()): Boolean {
        val today = CheckInLogic.dateStr(now)
        return _checkIns.value.any { it.date == today }
    }

    fun getTodayCheckIn(now: Long = System.currentTimeMillis()): CheckIn? {
        val today = CheckInLogic.dateStr(now)
        return _checkIns.value.find { it.date == today }
    }

    fun getCurrentStreak(now: Long = System.currentTimeMillis()): Int =
        CheckInLogic.currentStreak(_checkIns.value.map { it.date }.toSet(), now)

    fun getPast7DaysCheckInStatus(now: Long = System.currentTimeMillis()): List<DayCheckInStatus> =
        CheckInLogic.past7Days(_checkIns.value.map { it.date }.toSet(), now)

    /** 今日打卡。幂等：已打过则直接返回已有记录（date 是主键）。 */
    fun checkInToday(note: String = "", mood: String = "", now: Long = System.currentTimeMillis()): CheckIn {
        val todayStr = CheckInLogic.dateStr(now)
        _checkIns.value.find { it.date == todayStr }?.let { return it }

        val checkIn = CheckIn(
            date = todayStr,
            checkInTime = now,
            streak = CheckInLogic.nextStreakValue(_checkIns.value, now),
            note = note.ifBlank { "今日打卡，稳扎稳打向前进！" },
            mood = mood
        )
        _checkIns.value = listOf(checkIn) + _checkIns.value.filter { it.date != todayStr }
        scope.launch {
            dbProvider()?.checkInDao()?.insert(CheckInEntity.fromDomainModel(checkIn))
        }
        return checkIn
    }
}
