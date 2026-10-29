package com.example.yanji.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.MainActivity
import com.example.yanji.data.timer.ActiveSession
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionKind
import com.example.yanji.data.timer.TimerCompletionFeedback
import com.example.yanji.liveactivity.FocusNotificationSpecs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs real short timers on the emulator; sessions are below the recording threshold. */
@RunWith(AndroidJUnit4::class)
class TimerNotificationInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private var ownedSessionId: String? = null

    @Before fun prepare() {
        assertFalse("An existing timer must not be overwritten", ActiveSessionCoordinator.isBusy)
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        manager.cancel(FocusNotificationSpecs.COMPLETION_NOTIFICATION_ID)
    }

    @After fun cleanUp() {
        val id = ownedSessionId
        if (id != null && ActiveSessionCoordinator.active.value?.sessionId == id) {
            FocusTimerService.discardTimer(context)
            await { !ActiveSessionCoordinator.isBusy }
        }
        if (id != null) TimerCompletionFeedback.state.acknowledge(id)
        manager.cancel(FocusNotificationSpecs.COMPLETION_NOTIFICATION_ID)
    }

    private fun start(scenario: ActivityScenario<MainActivity>, seconds: Long) {
        val id = "notification-test-${System.nanoTime()}"
        ownedSessionId = id
        assertTrue(runBlocking { ActiveSessionCoordinator.begin(ActiveSession(
            sessionId = id, kind = ActiveSessionKind.FOCUS, subjectId = "math_advanced",
            subjectName = "高等数学", targetDurationSeconds = seconds
        )) })
        scenario.onActivity { FocusTimerService.startFocus(it, id, "高等数学", seconds) }
    }

    @Test fun foregroundCountdownShowsResultCardAfterSuccessfulCompletion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start(scenario, 3)
            await { TimerCompletionFeedback.state.pending.value?.sessionId == ownedSessionId }
            val result = TimerCompletionFeedback.state.pending.value!!
            assertEquals("高等数学", result.subjectName)
            assertEquals(3L, result.elapsedSeconds)
            await { manager.activeNotifications.none { it.id == FocusNotificationSpecs.ONGOING_NOTIFICATION_ID } }
            assertFalse(manager.activeNotifications.any { it.id == FocusNotificationSpecs.COMPLETION_NOTIFICATION_ID })
            await { instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText("已计时完成")?.any { it.isVisibleToUser } == true }
            val root = instrumentation.uiAutomation.rootInActiveWindow
            assertTrue(root.findAccessibilityNodeInfosByText("3 秒").any { it.isVisibleToUser })
            assertTrue(root.findAccessibilityNodeInfosByText("高等数学").any { it.isVisibleToUser })
            Thread.sleep(500) // Capture after the native Modal fade has completed.
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.externalCacheDir, "timer-completion.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            var button = root.findAccessibilityNodeInfosByText("完成")
                .first { it.text?.toString() == "完成" || it.contentDescription?.toString() == "完成" }
            while (!button.isClickable && button.parent != null) button = button.parent
            assertTrue(button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            await { TimerCompletionFeedback.state.pending.value == null }
        }
    }

    @Test fun backgroundTimerKeepsChronometerAndPostsSeparateCompletionNotification() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start(scenario, 0)
            await { manager.activeNotifications.any { it.id == FocusNotificationSpecs.ONGOING_NOTIFICATION_ID } }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertFalse(TimerCompletionFeedback.state.appVisible)
            val ongoing = manager.activeNotifications.single { it.id == FocusNotificationSpecs.ONGOING_NOTIFICATION_ID }.notification
            assertTrue(ongoing.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            FocusTimerService.completeTimer(context)
            await { manager.activeNotifications.any { it.id == FocusNotificationSpecs.COMPLETION_NOTIFICATION_ID } }
            val complete = manager.activeNotifications.single { it.id == FocusNotificationSpecs.COMPLETION_NOTIFICATION_ID }.notification
            assertEquals(FocusNotificationSpecs.COMPLETION_CHANNEL_ID, complete.channelId)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(complete.channelId).importance)
            assertEquals(0, complete.flags and Notification.FLAG_ONGOING_EVENT)
            assertNull(TimerCompletionFeedback.state.pending.value)
            assertFalse(manager.activeNotifications.any { it.id == FocusNotificationSpecs.ONGOING_NOTIFICATION_ID })
        }
    }

    @Test fun backgroundCountdownNotifiesAtZeroWithoutReplayingForegroundCard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start(scenario, 5)
            await { manager.activeNotifications.any { it.id == FocusNotificationSpecs.ONGOING_NOTIFICATION_ID } }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            await { manager.activeNotifications.any { it.id == FocusNotificationSpecs.COMPLETION_NOTIFICATION_ID } }
            assertFalse(manager.activeNotifications.any { it.id == FocusNotificationSpecs.ONGOING_NOTIFICATION_ID })
            assertNull(TimerCompletionFeedback.state.pending.value)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertNull(TimerCompletionFeedback.state.pending.value)
        }
    }

    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < until) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("Timed out waiting for timer feedback", condition())
    }
}
