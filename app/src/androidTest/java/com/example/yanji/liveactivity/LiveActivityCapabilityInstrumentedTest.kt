package com.example.yanji.liveactivity

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.data.timer.CountdownPaused
import com.example.yanji.data.timer.CountdownRunning
import com.example.yanji.data.timer.CountUpRunning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 真机能力诊断 + 通知契约回归测试。
 *
 * 为什么必须在真机上跑：promoted ongoing 的可用性由**系统**决定（系统版本、ROM 是否提供该
 * framework 方法、权限、渠道重要性），模拟器与 JVM 单测都给不出答案。
 *
 * 本测试做两件事：
 *  1. 把 [LiveActivityCapability] 的探测结果落盘（`externalCacheDir/live-activity-capability.txt`），
 *     便于 `adb pull` 取证，不必在真机上翻 logcat；
 *  2. 守住一条关键不变量——**只要系统报告 promoted 能力可用，我们构造出来的常驻通知就必须
 *     通过 `hasPromotableCharacteristics()`**。如果哪天有人改坏了通知结构（比如加了自定义
 *     RemoteViews、把渠道降成 IMPORTANCE_MIN、少了标题），系统会静默拒绝提升，而这条断言会先失败。
 *
 * 不写数据库、不改用户数据。
 */
@RunWith(AndroidJUnit4::class)
class LiveActivityCapabilityInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun detectsCapabilityAndKeepsTheOngoingNotificationPromotable() {
        val capability = LiveActivityCapabilityDetector.detect(context)
        val now = System.currentTimeMillis()
        val controller = FocusLiveActivityController(context)
        // 创建渠道（幂等）+ 探测能力 + 打日志。
        controller.initialize()

        val countdown = CountdownRunning(
            sessionId = "instrumented-capability-probe",
            subject = "高等数学",
            startedAtEpochMs = now,
            elapsedSeconds = 125L,
            targetSeconds = 2700L
        )
        val countUp = CountUpRunning(
            sessionId = "instrumented-capability-probe",
            subject = "概率论",
            startedAtEpochMs = now,
            elapsedSeconds = 1122L
        )
        val paused = CountdownPaused(
            sessionId = "instrumented-capability-probe",
            subject = "高等数学",
            startedAtEpochMs = now,
            elapsedSeconds = 1482L,
            targetSeconds = 2700L
        )

        val countdownNotification = controller.buildOngoing(countdown, FocusKind.FOCUS)
        val countUpNotification = controller.buildOngoing(countUp, FocusKind.FOCUS)
        val pausedNotification = controller.buildOngoing(paused, FocusKind.FOCUS)

        assertNotNull("倒计时必须产出常驻通知", countdownNotification)
        assertNotNull("正向计时必须产出常驻通知", countUpNotification)
        assertNotNull("暂停态必须产出常驻通知", pausedNotification)

        val countdownPromotable = NotificationCompat.hasPromotableCharacteristics(countdownNotification!!)
        val countUpPromotable = NotificationCompat.hasPromotableCharacteristics(countUpNotification!!)
        val pausedPromotable = NotificationCompat.hasPromotableCharacteristics(pausedNotification!!)

        // 系统的能力声明与我们的构造结果必须一致：声明可用 ⇒ 产物必须够格。
        if (capability.canPostPromotedOngoing) {
            assertTrue(
                "系统报告 promoted 可用，但我们构造的倒计时通知不合格，系统会静默拒绝提升",
                countdownPromotable
            )
            assertTrue(
                "系统报告 promoted 可用，但我们构造的正向计时通知不合格",
                countUpPromotable
            )
        } else {
            assertFalse(
                "系统报告 promoted 不可用时，不应意外判定为可提升",
                countdownPromotable
            )
        }

        // 暂停态必须把「冻结的时间」交给静态文本承载。若这里还依赖 Chronometer，
        // 系统会继续走数字，用户看到的时长会比真实有效专注时长多出整个暂停区间。
        val pausedText = pausedNotification.extras.getString(Notification.EXTRA_TEXT).orEmpty()
        assertTrue("暂停通知必须显示冻结的剩余时间，实际为：$pausedText", pausedText.contains("20:18"))

        // 运行态则相反：正文里不允许出现静态时间，否则会出现一个不走的假数字。
        val runningText = countdownNotification.extras.getString(Notification.EXTRA_TEXT).orEmpty()
        assertFalse("运行中的通知不得下发静态时间，实际为：$runningText", runningText.contains(":"))

        val report = buildString {
            appendLine("=== 研迹 Live Activity 真机能力报告 ===")
            appendLine("时间: " + java.time.LocalDateTime.now())
            appendLine("设备: " + capability.manufacturer + " / " + capability.brand)
            appendLine("Android SDK_INT: " + capability.sdkInt)
            appendLine("生效层级 tier: " + capability.tier)
            appendLine("canPostPromotedOngoing: " + capability.canPostPromotedOngoing)
            appendLine("POST_PROMOTED_NOTIFICATIONS 已授予: " + capability.promotedPermissionGranted)
            appendLine("应用通知总开关: " + capability.notificationsEnabled)
            appendLine("ColorOS 系 ROM: " + capability.isColorOsFamily)
            appendLine("--- 通知结构自检 ---")
            appendLine("倒计时 hasPromotableCharacteristics: " + countdownPromotable)
            appendLine("正向计时 hasPromotableCharacteristics: " + countUpPromotable)
            appendLine("暂停态 hasPromotableCharacteristics: " + pausedPromotable)
        }

        val out = File(context.externalCacheDir, "live-activity-capability.txt")
        out.writeText(report)
        Log.i(TAG, report)
    }

    @Test
    fun capabilityDetectionNeverClaimsPromotionBelowApi36() {
        val capability = LiveActivityCapabilityDetector.detect(context)
        // 低版本系统上必须一律降级，不允许凭厂商名硬开。
        if (capability.sdkInt < 36) {
            assertFalse(capability.canPostPromotedOngoing)
            assertEquals(LiveActivityCapability.Tier.STANDARD_ONGOING, capability.tier)
        }
    }

    companion object {
        private const val TAG = "YanjiCapabilityTest"
    }
}
