package com.example.yanji.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「实际 / 计划」口径的守卫。
 *
 * 用户诉求：今日计划分配的时间要和实际时间做对比，超了要显示出来。
 * 这些判定收敛在 [planTimeLabels] 一个纯函数里，页面只负责渲染，
 * 因此这里把边界钉住：
 *  - 一分钟的进位口径（避免「0/45 分钟」这种没信息的开头）；
 *  - 不限时的计划只报实际、不报超额；
 *  - 超额不足一分钟不提示（刚从倒计时归零跑到 59 秒不该刷告警）。
 */
class PlanTimeLabelsTest {

    @Test
    fun noWorkDoneYetShowsNothingSoEveryRowDoesNotStartWithZero() {
        assertEquals(PlanTimeLabels(compare = null, overrun = null), planTimeLabels(plannedMinutes = 45, actualSeconds = 0))
        assertEquals(PlanTimeLabels(compare = null, overrun = null), planTimeLabels(plannedMinutes = 0, actualSeconds = 0))
        assertEquals(PlanTimeLabels(compare = null, overrun = null), planTimeLabels(plannedMinutes = 180, actualSeconds = 59))
    }

    @Test
    fun actualMinutesAreTruncatedNotRounded() {
        assertEquals("29/45 分钟", planTimeLabels(45, actualSeconds = 29 * 60L + 59).compare)
        assertEquals("30/45 分钟", planTimeLabels(45, actualSeconds = 30 * 60L).compare)
    }

    @Test
    fun hittingThePlanExactlyIsNotAnOverrun() {
        val labels = planTimeLabels(plannedMinutes = 45, actualSeconds = 45 * 60L)
        assertEquals("45/45 分钟", labels.compare)
        assertEquals(null, labels.overrun)
    }

    @Test
    fun overrunNeedsAFullMinuteBeforeItIsAnnounced() {
        // 归零后又跑了 59 秒：数字已经越过计划，但告警刷出来只是噪音。
        assertEquals(null, planTimeLabels(45, actualSeconds = 45 * 60L + 59).overrun)
        // 正好一分钟：可以说了。
        assertEquals("已超 1 分钟", planTimeLabels(45, actualSeconds = 45 * 60L + 60).overrun)
        assertEquals("已超 75 分钟", planTimeLabels(45, actualSeconds = 120 * 60L).overrun)
    }

    @Test
    fun untimedPlanOnlyReportsActualAndCanNeverOverrun() {
        val labels = planTimeLabels(plannedMinutes = 0, actualSeconds = 30 * 60L)
        assertEquals("30 分钟", labels.compare)
        assertEquals(null, labels.overrun)
    }

    @Test
    fun longPlansRenderFullMinutes() {
        assertEquals("120/180 分钟", planTimeLabels(180, actualSeconds = 120 * 60L).compare)
    }
}
