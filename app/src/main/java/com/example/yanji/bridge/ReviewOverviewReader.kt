package com.example.yanji.bridge

import com.example.yanji.data.EpochRange
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import com.example.yanji.data.db.ExamStatsRow
import com.example.yanji.data.db.FocusStatsRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 回顾页窗口读取器：把「窗口定义 → 区间扫描 → 多窗口聚合」合成一次读事务。
 *
 * 一次切换日期时页面需要三个窗口：当前窗口、环比窗口、所选日期所在的自然周。
 * 旧实现为每个窗口各发四条 Room 观测查询（合计十二条），而且聚合跑在
 * `Dispatchers.Main.immediate` 上。这里改为：
 *
 *  1. 所有窗口先合并成一个**区间并集**，每张表只扫一次（两条投影查询）；
 *  2. 在同一次 Room 读事务里取完，窗口之间看到的是同一份数据快照；
 *  3. 聚合只在内存里按窗口过滤，不再重复查询；
 *  4. 调用方负责把 [read] 放在后台调度器上（见 `YanjiDataModule`）。
 *
 * 科目合计不需要单独的 `GROUP BY` 查询：每个窗口已经逐条累计了展示桶秒数，
 * 把它们整体交给 [BridgeMappers.reviewOverviewFields] 求和，与 SQL 聚合同口径。
 */
internal object ReviewOverviewReader {

    /** 一次概览请求。`periodsBack` 只对自然周 / 自然月有意义。 */
    data class Request(
        val scope: String,
        val periodsBack: Long,
        val anchorDate: String?
    ) {
        val effectivePeriodsBack: Long
            get() = if (scope == BridgeMappers.SCOPE_TODAY) 0L else periodsBack.coerceAtLeast(0L)
    }

    /** 窗口 = 查询区间 + 窗口内本地日期键（升序）。 */
    data class Window(
        val range: EpochRange,
        val dates: List<String>
    )

    /**
     * 批量读取多个窗口。返回顺序与 [requests] 一致；空请求返回空列表且不碰数据库。
     */
    suspend fun read(
        repository: YanjiRepository,
        requests: List<Request>
    ): List<BridgeMappers.ReviewOverviewFields> {
        if (requests.isEmpty()) return emptyList()

        val today = YanjiTime.today()
        val todayIso = today.format(YanjiTime.isoDateFormatter)
        val windows = requests.map { windowOf(it, today) }

        // 并集区间：一次扫描覆盖全部窗口，窗口之间共享同一份行列表。
        val startInclusive = windows.minOf { it.range.startInclusive }
        val endExclusive = windows.maxOf { it.range.endExclusive }
        val rows = repository.readCompletedStatsRows(startInclusive, endExclusive)

        return buildAll(requests, windows, rows.focus, rows.exams, todayIso)
    }

    /** Equivalent windows share the row traversal and mapper result; only request metadata is copied. */
    fun buildAll(
        requests: List<Request>,
        windows: List<Window>,
        focus: List<FocusStatsRow>,
        exams: List<ExamStatsRow>,
        todayIso: String
    ): List<BridgeMappers.ReviewOverviewFields> {
        val built = HashMap<Window, BridgeMappers.ReviewOverviewFields>()
        return requests.mapIndexed { index, request ->
            val overview = built.getOrPut(windows[index]) {
                build(
                    scope = request.scope,
                    periodsBack = request.effectivePeriodsBack.toInt(),
                    window = windows[index],
                    focus = focus,
                    exams = exams,
                    todayIso = todayIso
                )
            }
            overview.copy(scope = request.scope, periodsBack = request.effectivePeriodsBack.toInt())
        }
    }

    /**
     * scope → 窗口。日期键与 DAO 查询区间来自同一套日历推进，二者不会错位。
     * 自然周必须落到周一（`previousOrSame(MONDAY)`），拿 7 天滚动凑会让「上周」
     * 和本周的数据整块重叠。
     */
    fun windowOf(
        request: Request,
        today: LocalDate = YanjiTime.today(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Window {
        val anchor = request.anchorDate?.let { dateStr ->
            try {
                LocalDate.parse(dateStr, YanjiTime.isoDateFormatter)
            } catch (_: Exception) {
                null
            }
        } ?: today
        val periodsBack = request.effectivePeriodsBack

        return when (request.scope) {
            BridgeMappers.SCOPE_TODAY -> {
                val range = EpochRange(
                    anchor.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    anchor.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
                Window(range, BridgeMappers.rollingWindowDates(anchor, 1))
            }
            BridgeMappers.SCOPE_CALENDAR_WEEK -> {
                val monday = anchor.minusWeeks(periodsBack)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6L)
                val range = EpochRange(
                    monday.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    sunday.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
                Window(range, BridgeMappers.rollingWindowDates(monday, 7))
            }
            BridgeMappers.SCOPE_CALENDAR_MONTH -> {
                val first = anchor.minusMonths(periodsBack).withDayOfMonth(1)
                val last = first.withDayOfMonth(first.lengthOfMonth())
                val range = EpochRange(
                    first.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    last.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
                )
                Window(range, BridgeMappers.rollingWindowDates(first, first.lengthOfMonth()))
            }
            else -> throw IllegalArgumentException("Unsupported review scope: ${request.scope}")
        }
    }

    /**
     * 从共享的区间行里汇总出一个窗口的概览。
     *
     * 只保留窗口日期内的行；科目秒数整份交给 mapper，由它完成唯一的「秒 → 分钟」截断。
     */
    fun build(
        scope: String,
        periodsBack: Int,
        window: Window,
        focus: List<FocusStatsRow>,
        exams: List<ExamStatsRow>,
        todayIso: String
    ): BridgeMappers.ReviewOverviewFields {
        val windowKeys = window.dates.toSet()
        val daySecondsByDate = LinkedHashMap<String, Long>(window.dates.size)
        window.dates.forEach { daySecondsByDate[it] = 0L }
        val subjectSecondsByDate =
            LinkedHashMap<String, MutableList<BridgeMappers.SubjectSecondsSlice>>()
        val allSlices = ArrayList<BridgeMappers.SubjectSecondsSlice>()
        var examCount = 0

        /** 归属窗口时返回 true，供模考计数复用同一次过滤。 */
        fun accumulate(startTime: Long, subjectId: String, subjectName: String, seconds: Long): Boolean {
            val date = YanjiTime.localDate(startTime).format(YanjiTime.isoDateFormatter)
            if (date !in windowKeys) return false
            daySecondsByDate[date] = (daySecondsByDate[date] ?: 0L) + seconds
            val (bucketId, displayName) = BridgeMappers.subjectBucket(subjectId, subjectName)
            val slice = BridgeMappers.SubjectSecondsSlice(bucketId, displayName, seconds)
            subjectSecondsByDate.getOrPut(date) { mutableListOf() }.add(slice)
            allSlices.add(slice)
            return true
        }

        focus.forEach {
            accumulate(it.startTime, it.subjectId, it.subjectName, it.durationSeconds)
        }
        exams.forEach {
            if (accumulate(it.startTime, it.subjectId, it.subjectName, it.actualDurationSeconds)) {
                examCount++
            }
        }

        return BridgeMappers.reviewOverviewFields(
            scope = scope,
            periodsBack = periodsBack,
            windowDates = window.dates,
            todayIso = todayIso,
            daySecondsByDate = daySecondsByDate,
            subjectSecondsByDate = subjectSecondsByDate,
            subjectTotals = allSlices,
            examCount = examCount
        )
    }
}
