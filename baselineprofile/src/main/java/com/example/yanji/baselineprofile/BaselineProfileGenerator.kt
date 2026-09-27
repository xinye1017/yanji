package com.example.yanji.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupNotesScrollAndUnsavedEditor() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
    ) {
        startActivityAndWait()

        val notesTab = device.findObject(By.desc("随笔"))
        check(notesTab != null) { "Notes tab was not found" }
        notesTab.click()

        check(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)) {
            "Notes search field did not appear"
        }
        val search = device.findObject(By.clazz("android.widget.EditText"))
        search.click()
        search.setText(SEARCH_QUERY)
        device.pressBack()

        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
        }

        val newNote = device.findObject(By.desc("记今天"))
        check(newNote != null) { "New-note action was not found" }
        newNote.click()
        check(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)) {
            "Note editor input did not appear"
        }

        val editor = device.findObject(By.clazz("android.widget.EditText"))
        editor.click()
        repeat(EDITOR_TYPING_UPDATES) { index ->
            editor.setText("p".repeat(index + 1))
        }

        // Keep this journey transient: the app never reaches its save action.
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.yanji"
        const val UI_TIMEOUT_MS = 5_000L
        const val SEARCH_QUERY = "baseline_profile_no_match"
        const val EDITOR_TYPING_UPDATES = 10
    }
}
