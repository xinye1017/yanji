package com.example.yanji.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release/R8 UI journeys. Run each method against the baseline and optimized
 * benchmark APK using the same physical device and compilation mode.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = STARTUP_ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun navigationAndGlassBottomBar() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = FRAME_ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
    ) {
        startActivityAndWait()
        val tabs = listOf("专注", "随笔", "统计", "我的", "首页")
        repeat(2) {
            tabs.forEach { title ->
                val tab = device.findObject(By.desc(title))
                check(tab != null) { "Bottom navigation tab '$title' was not found" }
                tab.click()
            }
        }
    }

    @Test
    fun notesSearchAndListScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = FRAME_ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        setupBlock = {
            startActivityAndWait()
            device.findObject(By.desc("随笔")).click()
            check(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)) {
                "Notes search field did not appear"
            }
            val search = device.findObject(By.clazz("android.widget.EditText"))
            search.click()
            search.setText("")
            device.pressBack()
        },
    ) {
        val search = device.findObject(By.clazz("android.widget.EditText"))
        search.click()
        search.setText(SEARCH_QUERY)
        device.pressBack()
        val list = device.findObject(By.scrollable(true))
        check(list != null) { "Notes list was not exposed as a scrollable UI element" }
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
    }

    @Test
    fun editorTyping() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = FRAME_ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        setupBlock = {
            startActivityAndWait()
            device.findObject(By.desc("随笔")).click()
            check(device.wait(Until.hasObject(By.desc("记今天")), UI_TIMEOUT_MS)) {
                "New-note action did not appear"
            }
            device.findObject(By.desc("记今天")).click()
            check(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)) {
                "Note editor input did not appear"
            }
            device.findObject(By.clazz("android.widget.EditText")).click()
        },
    ) {
        // Model repeated typing updates while using only an unsaved, in-memory blank draft.
        // Force-stop drops the transient editor state and cannot create a Room note or draft.
        val input = device.findObject(By.clazz("android.widget.EditText"))
        repeat(EDITOR_TYPING_UPDATES) { index ->
            input.setText("p".repeat(index + 1))
        }
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.yanji"
        const val STARTUP_ITERATIONS = 10
        const val FRAME_ITERATIONS = 5
        const val UI_TIMEOUT_MS = 5_000L
        const val SEARCH_QUERY = "macrobenchmark_no_match"
        const val EDITOR_TYPING_UPDATES = 40
    }
}
