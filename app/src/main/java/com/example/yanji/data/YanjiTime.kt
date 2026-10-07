package com.example.yanji.data

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class EpochRange(val startInclusive: Long, val endExclusive: Long)

/** Thread-safe date/time policy shared by repositories and view models. */
object YanjiTime {
    val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val chineseDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINESE)
    private val shortDateWithWeekdayFormatter = DateTimeFormatter.ofPattern("M 月 d 日 · E", Locale.CHINESE)
    private val fullDateWithWeekdayFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 EEEE", Locale.CHINESE)
    private val backupStampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    fun today(clock: Clock = Clock.systemDefaultZone()): LocalDate = LocalDate.now(clock)

    fun todayIso(clock: Clock = Clock.systemDefaultZone()): String = today(clock).format(isoDateFormatter)

    fun localDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()

    fun parseIsoDate(value: String): LocalDate? = try {
        LocalDate.parse(value, isoDateFormatter)
    } catch (_: DateTimeParseException) {
        null
    }

    fun dayRange(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): EpochRange =
        EpochRange(
            date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )

    /**
     * 自然周区间：周一 00:00 起 7 天。[weeksBack] 是往回翻几个整周，0 = 本周。
     *
     * 统计页回看「上周」走这里取窗口：翻期只平移起点，周一至周日的自然周口径不变。
     */
    fun weekRange(
        weeksBack: Long = 0,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): EpochRange {
        val monday = today(clock).minusWeeks(weeksBack)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return EpochRange(
            monday.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            monday.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    /**
     * 自然月区间：1 日 00:00 起一个自然月。[monthsBack] 是往回翻几个整月，0 = 本月。
     *
     * 翻期与取月初的先后顺序无关紧要：先减月再取 1 日，落到的一定是那个月的第一天。
     */
    fun monthRange(
        monthsBack: Long = 0,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): EpochRange {
        val first = today(clock).minusMonths(monthsBack).withDayOfMonth(1)
        return EpochRange(
            first.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            first.plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    /** Calendar year semantics: Jan 1 00:00 through the next Jan 1 00:00. */
    fun currentYearRange(
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): EpochRange {
        val first = today(clock).withDayOfYear(1)
        return EpochRange(
            first.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            first.plusYears(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    /** Rolling N-day semantics, including today and excluding tomorrow. */
    fun lastDaysRange(
        days: Long,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): EpochRange {
        require(days > 0)
        val today = today(clock)
        return EpochRange(
            today.minusDays(days - 1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    fun rangeFor(range: StudyTimeRange, clock: Clock = Clock.systemDefaultZone()): EpochRange = when (range) {
        StudyTimeRange.TODAY -> dayRange(today(clock), clock.zone)
        StudyTimeRange.WEEK -> weekRange(clock = clock, zoneId = clock.zone)
        StudyTimeRange.MONTH -> monthRange(clock = clock, zoneId = clock.zone)
        StudyTimeRange.ALL -> EpochRange(0L, Long.MAX_VALUE)
        StudyTimeRange.YEAR -> currentYearRange(clock, clock.zone)
    }

    fun formatTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(timeFormatter)

    fun formatDateTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(dateTimeFormatter)

    fun formatChineseDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(chineseDateFormatter)

    fun formatShortDateWithWeekday(date: LocalDate): String = date.format(shortDateWithWeekdayFormatter)

    fun formatFullDateWithWeekday(date: LocalDate): String = date.format(fullDateWithWeekdayFormatter)

    fun backupStamp(instant: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): String =
        instant.atZone(zoneId).format(backupStampFormatter)
}
