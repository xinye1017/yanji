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

    /** Calendar week semantics: Monday 00:00 through the following Monday 00:00. */
    fun currentWeekRange(
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): EpochRange {
        val monday = today(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return EpochRange(
            monday.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            monday.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    fun previousWeekRange(
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): EpochRange {
        val current = currentWeekRange(clock, zoneId)
        val monday = Instant.ofEpochMilli(current.startInclusive).atZone(zoneId).toLocalDate()
        return EpochRange(
            monday.minusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            current.startInclusive
        )
    }

    fun currentMonthRange(
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone
    ): EpochRange {
        val first = today(clock).withDayOfMonth(1)
        return EpochRange(
            first.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            first.plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
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
        StudyTimeRange.WEEK -> currentWeekRange(clock, clock.zone)
        StudyTimeRange.MONTH -> currentMonthRange(clock, clock.zone)
        StudyTimeRange.ALL -> EpochRange(0L, Long.MAX_VALUE)
    }

    fun formatTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(timeFormatter)

    fun formatChineseDate(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(chineseDateFormatter)

    fun formatShortDateWithWeekday(date: LocalDate): String = date.format(shortDateWithWeekdayFormatter)

    fun formatFullDateWithWeekday(date: LocalDate): String = date.format(fullDateWithWeekdayFormatter)

    fun backupStamp(instant: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): String =
        instant.atZone(zoneId).format(backupStampFormatter)
}
