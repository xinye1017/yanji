package com.example.yanji.liveactivity

import com.example.yanji.data.timer.CountdownPaused
import com.example.yanji.data.timer.CountdownRunning
import com.example.yanji.data.timer.CountUpPaused
import com.example.yanji.data.timer.CountUpRunning
import com.example.yanji.data.timer.Discarded
import com.example.yanji.data.timer.Finished
import com.example.yanji.data.timer.Idle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知描述的纯 JVM 测试。
 *
 * 这里锁住的是规范里最容易被破坏的两条不变量：
 *  1. Chronometer 基准时间的算法（倒计时取未来、正向取过去）；
 *  2. 暂停时**必须**关掉 Chronometer，否则系统会继续走数字，
 *     用户看到的时长会比真实有效专注时长多出整个暂停区间。
 */
class FocusNotificationSpecsTest {

    private val now = 1_700_000_000_000L

    @Test
    fun runningCountdownPointsChronometerAtAFutureWallClock() {
        val state = CountdownRunning("sid", "高等数学", now, elapsedSeconds = 125L, targetSeconds = 2700L)
        val spec = FocusNotificationSpecs.ongoing(state, now)!!

        assertTrue(spec.usesChronometer)
        assertTrue(spec.chronometerCountDown)
        assertTrue(spec.showWhen)
        // 基准 = 现在 + 剩余 2575s
        assertEquals(now + 2575L * 1000L, spec.referenceWallClockMs)
        assertEquals("高等数学", spec.title)
        // 运行中不得下发静态时间：那会显示一个不走的假数字。
        assertNull(spec.shortCriticalText)
        // 进度条在 Android 通知中无法随物理时钟平滑自增，为避免在 ColorOS 流体云中卡死，统一关闭静态进度条
        assertFalse(spec.showProgress)
        assertEquals(0, spec.progressPercent)
        assertEquals(FocusNotificationAction.PAUSE, spec.primaryAction)
        assertEquals(FocusNotificationAction.COMPLETE, spec.secondaryAction)
        assertTrue(spec.requestPromoted)
    }

    @Test
    fun runningCountUpPointsChronometerAtAPastWallClockAndHidesProgress() {
        val state = CountUpRunning("sid", "概率论", now, elapsedSeconds = 1122L)
        val spec = FocusNotificationSpecs.ongoing(state, now)!!

        assertTrue(spec.usesChronometer)
        assertFalse(spec.chronometerCountDown)
        // 基准 = 现在 - 已专注 1122s
        assertEquals(now - 1122L * 1000L, spec.referenceWallClockMs)
        assertFalse(spec.showProgress)
        assertFalse(spec.isProgressIndeterminate)
        assertEquals(0, spec.progressPercent)
        assertEquals("概率论", spec.title)
    }

    @Test
    fun pausedFreezesTheDigitsAndSwapsThePrimaryActionToResume() {
        val state = CountdownPaused("sid", "高等数学", now, elapsedSeconds = 1482L, targetSeconds = 2700L)
        val spec = FocusNotificationSpecs.ongoing(state, now)!!

        // 关键不变量：暂停必须关掉 Chronometer，改由静态文本承载冻结的数字。
        assertFalse(spec.usesChronometer)
        assertFalse(spec.showWhen)
        assertEquals("高等数学  20:18", spec.title)
        assertEquals("已暂停", spec.contentText)
        assertNull(spec.subText)
        // 冻结的数字是真实的，可以安全下发给状态栏胶囊。
        assertEquals("20:18", spec.shortCriticalText)
        assertEquals(FocusNotificationAction.RESUME, spec.primaryAction)
        assertEquals(FocusNotificationAction.COMPLETE, spec.secondaryAction)
    }

    @Test
    fun pausedCountUpShowsElapsedInsteadOfRemaining() {
        val state = CountUpPaused("sid", "概率论", now, elapsedSeconds = 1938L)
        val spec = FocusNotificationSpecs.ongoing(state, now)!!
        assertEquals("概率论  32:18", spec.title)
        assertEquals("已暂停", spec.contentText)
        assertNull(spec.subText)
        assertFalse(spec.showProgress)
    }

    @Test
    fun terminalStatesProduceNoOngoingNotificationAtAll() {
        assertNull(FocusNotificationSpecs.ongoing(Idle, now))
        assertNull(
            FocusNotificationSpecs.ongoing(
                Finished("sid", "高等数学", now, elapsedSeconds = 2700L, targetSeconds = 2700L),
                now
            )
        )
        assertNull(FocusNotificationSpecs.ongoing(Discarded("sid", "高等数学", now, 30L), now))
    }

    @Test
    fun completionNotificationReportsMinutesWhenTheTargetWasReached() {
        val state = Finished(
            "sid", "高等数学", now,
            elapsedSeconds = 2700L, targetSeconds = 2700L,
            endEpochMs = now, reachedTarget = true
        )
        val spec = FocusNotificationSpecs.completion(state)!!

        assertEquals("高等数学 专注完成", spec.title)
        assertEquals("本次专注 45 分钟", spec.contentText)
        assertFalse(spec.ongoing)
        assertTrue(spec.autoCancel)
        // 完成通知不应该继续申请 promoted：活动已经结束，胶囊必须收掉。
        assertFalse(spec.requestPromoted)
        assertEquals(FocusNotificationSpecs.COMPLETION_NOTIFICATION_ID, spec.notificationId)
    }

    @Test
    fun completionNotificationReportsRealDurationWhenEndedEarly() {
        val state = Finished(
            "sid", "概率论", now,
            elapsedSeconds = 1122L, targetSeconds = 0L,
            endEpochMs = now, reachedTarget = false
        )
        val spec = FocusNotificationSpecs.completion(state)!!
        assertEquals("概率论 专注完成", spec.title)
        assertEquals("本次专注 18:42", spec.contentText)
    }

    @Test
    fun examKindUsesItsOwnWording() {
        val running = CountdownRunning("sid", "全真模考", now, elapsedSeconds = 60L, targetSeconds = 10800L)
        assertEquals("剩余时间 · 保持专注", FocusNotificationSpecs.ongoing(running, now, FocusKind.EXAM)!!.contentText)

        val finished = Finished(
            "sid", "全真模考", now,
            elapsedSeconds = 600L, targetSeconds = 10800L,
            endEpochMs = now, reachedTarget = false
        )
        val spec = FocusNotificationSpecs.completion(finished, FocusKind.EXAM)!!
        assertEquals("全真模考 已交卷", spec.title)
        assertEquals("本次作答 10:00", spec.contentText)
    }

    @Test
    fun ongoingNotificationsDoNotExposeStaticProgressBars() {
        val overrun = CountdownRunning("sid", "数学", now, elapsedSeconds = 3000L, targetSeconds = 2700L)
        assertFalse(FocusNotificationSpecs.ongoing(overrun, now)!!.showProgress)

        val atStart = CountdownRunning("sid", "数学", now, elapsedSeconds = 0L, targetSeconds = 2700L)
        assertFalse(FocusNotificationSpecs.ongoing(atStart, now)!!.showProgress)
    }
}
