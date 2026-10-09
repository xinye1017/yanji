package com.example.yanji.layout

import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.MainActivity
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.service.FocusTimerService
import com.facebook.react.views.view.ReactViewGroup
import com.facebook.react.uimanager.PointerEvents
import kotlinx.coroutines.runBlocking
import java.io.File
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the shipped offline RN bundle through Fabric/Yoga, with the real bridge.
 * No synthetic layout events or mock business data. Personal-device execution
 * requires explicit authorization via the allowPersonalDevice runner argument.
 * Only a timer created by this test may be discarded during cleanup.
 */
@RunWith(AndroidJUnit4::class)
class TabLayoutInstrumentedTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val tabs = listOf("today", "focus", "review")
    private var ownedSessionId: String? = null

    @Before
    fun launchWithDeviceAuthorization() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish") ||
            InstrumentationRegistry.getArguments().getString("allowPersonalDevice") == "true") {
            "Personal-device UI tests require explicit authorization."
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await("three native RN scroll viewports mounted") {
            root?.let { views(it).filterIsInstance<ScrollView>().count { scroll -> scroll.height > 0 } == 3 } == true
        }
    }

    @Test
    fun nativeScrollViewCoordinatesCoincide() {
        // Uses native ScrollViews instead of testIDs, so it also detects the
        // original shipped defect before installing a corrected app bundle.
        onMain {
            val viewports = views(root!!).filterIsInstance<ScrollView>().map { bounds(it) }.toList()
            assertEquals(3, viewports.size)
            Log.i("YanjiLayoutTest", "Native scroll viewports: $viewports")
            viewports.drop(1).forEach {
                assertEquals("All native RN ScrollViews must share position and size", viewports.first(), it)
            }
        }
    }

    @After
    fun close() {
        if (::scenario.isInitialized) {
            scenario.onActivity { activity ->
                if (ownedSessionId != null && ActiveSessionCoordinator.active.value?.sessionId == ownedSessionId) {
                    FocusTimerService.discardTimer(activity)
                }
            }
            scenario.close()
        }
    }

    @Test
    fun allPanesFillTheSameSafeAreaWithoutTabTranslation() {
        val content = onMain { bounds(required("tab-content")) }
        val mounted = onMain { tabs.associateWith { required("pane-$it") } }
        onMain {
            val shell = required("app-shell")
            val nav = required("bottom-tab-bar")
            val insets = ViewCompat.getRootWindowInsets(shell)!!
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            assertTrue("Content must start below the status bar/cutout", content.top >= insets.top)
            assertEquals("Content ends where the in-flow navigation starts", bounds(nav).top, content.bottom)
            assertTrue("A full content viewport must remain", content.height() > bounds(nav).height())
            val controls = tabs.map { bounds(required("tab-$it")) }
            assertTrue("Navigation controls stay above the bottom system inset",
                controls.maxOf { it.bottom } <= bounds(shell).bottom - insets.bottom)
        }
        for (active in tabs + tabs.reversed()) {
            select(active)
            onMain {
                assertEquals(content, bounds(required("tab-content")))
                for (tab in tabs) {
                    val pane = required("pane-$tab")
                    assertSame("Switching must not remount $tab", mounted[tab], pane)
                    assertEquals("$tab must share the entire viewport, even when inactive", content, bounds(pane))
                    assertEquals(if (tab == active) 1f else 0f, pane.alpha, 0f)
                    if (tab == active) {
                        // Android may resolve RN's AUTO to YES when attaching
                        // its accessibility delegate to a view with testID.
                        assertTrue(pane.importantForAccessibility in listOf(
                            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, View.IMPORTANT_FOR_ACCESSIBILITY_YES))
                    } else {
                        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, pane.importantForAccessibility)
                    }
                    assertEquals(if (tab == active) PointerEvents.AUTO else PointerEvents.NONE,
                        (pane as ReactViewGroup).pointerEvents)
                    assertEquals("$tab ScrollView must fill its pane", content, bounds(required("scroll-$tab")))
                    Log.i("YanjiLayoutTest", "$active: $tab pane=${bounds(pane)} scroll=${bounds(required("scroll-$tab"))}")
                }
            }
            if (InstrumentationRegistry.getArguments().getString("screenshots") == "true") {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val directory = File(instrumentation.targetContext.cacheDir, "layout-regression").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(directory, "$active.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun realScrollGesturesReachTheLastElementAndOffsetsSurviveSwitching() {
        for (tab in tabs) {
            select(tab)
            val scroll = onMain { required("scroll-$tab") as ScrollView }
            var atEnd = false
            for (attempt in 0 until 20) {
                if (onMain { !scroll.canScrollVertically(1) }) {
                    atEnd = true
                    break
                }
                onView(testId("scroll-$tab")).perform(swipeUp())
                SystemClock.sleep(250) // Let native fling settle before measuring.
            }
            assertTrue("$tab must reach the bottom through native gestures", atEnd)
            val offset = onMain {
                val viewport = bounds(scroll)
                val content = scroll.getChildAt(0) as ViewGroup
                val last = content.getChildAt(content.childCount - 1)
                assertTrue("$tab last element must be above navigation", bounds(last).bottom <= viewport.bottom)
                assertTrue("$tab last element must remain visible", bounds(last).bottom > viewport.top)
                Log.i("YanjiLayoutTest", "$tab scrollY=${scroll.scrollY} viewport=$viewport last=${bounds(last)}")
                scroll.scrollY
            }
            select(tabs.first { it != tab })
            select(tab)
            onMain {
                assertSame(scroll, required("scroll-$tab"))
                assertEquals("$tab offset must survive tab switching", offset, scroll.scrollY)
            }
        }
    }

    @Test
    fun localConfigurationDraftAndNativeTimerSurviveTabSwitches() {
        // Do not replace, pause, discard or complete a user's existing session.
        check(ActiveSessionCoordinator.active.value == null) { "Existing focus session: configuration test refused safely." }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = YanjiDatabase.getDatabase(instrumentation.targetContext)
        val countsBefore = runBlocking {
            listOf(db.focusSessionDao().count(), db.examSessionDao().count(), db.noteEntryDao().count())
        }
        select("focus")
        onView(withText("90 分钟")).perform(click())
        await("duration selected") { selectedText("90 分钟") }
        select("review")
        val initialDate = onMain { (required("review-date") as TextView).text.toString() }
        onView(withContentDescription("前一天")).perform(click())
        await("date changed") { (required("review-date") as TextView).text.toString() != initialDate }
        val chosenDate = onMain { (required("review-date") as TextView).text.toString() }
        select("today")
        onMain {
            val scroll = required("scroll-today") as ScrollView
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        await("Today scrolled to footer") { !(required("scroll-today") as ScrollView).canScrollVertically(1) }
        onView(withContentDescription("写心得")).perform(click())
        val draft = "unsaved layout regression draft"
        onView(withContentDescription("记录内容")).inRoot(isDialog()).perform(replaceText(draft), closeSoftKeyboard())
        pressBack() // Exercise the modal's onRequestClose without a moving sheet target.
        select("focus")
        onMain { assertTrue("Focus duration was preserved", selectedText("90 分钟")) }
        select("review")
        onMain { assertEquals(chosenDate, (required("review-date") as TextView).text.toString()) }
        select("today")
        onView(withContentDescription("写心得")).perform(click())
        onView(withContentDescription("记录内容")).inRoot(isDialog()).check(matches(withText(draft)))
        pressBack()

        select("focus")
        onMain {
            val scroll = required("scroll-focus") as ScrollView
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        onView(allOf(withContentDescription("开始专注"), isDescendantOfA(testId("scroll-focus")))).perform(click())
        await("real native timer started") { ActiveSessionCoordinator.active.value != null }
        val sessionId = ActiveSessionCoordinator.active.value!!.sessionId
        ownedSessionId = sessionId
        val elapsed = FocusTimerService.elapsedSecondsForUi.value
        select("today")
        select("review")
        await("native timer progresses while its page is hidden") {
            FocusTimerService.elapsedSecondsForUi.value >= elapsed + 2
        }
        select("focus")
        assertEquals("Same native session after switching", sessionId, ActiveSessionCoordinator.active.value?.sessionId)
        assertEquals("Preserved duration reaches the native timer", 90 * 60L,
            ActiveSessionCoordinator.active.value?.targetDurationSeconds)
        Log.i("YanjiLayoutTest", "Native timer continued across tabs: elapsed=$elapsed -> ${FocusTimerService.elapsedSecondsForUi.value}")
        onMain { assertEquals(bounds(required("tab-content")), bounds(required("pane-focus"))) }
        onView(withContentDescription("放弃")).perform(click())
        await("test session discarded without saving a study record") { ActiveSessionCoordinator.active.value == null }
        val countsAfter = runBlocking {
            listOf(db.focusSessionDao().count(), db.examSessionDao().count(), db.noteEntryDao().count())
        }
        assertEquals("Existing study records must remain unchanged", countsBefore, countsAfter)
        Log.i("YanjiLayoutTest", "Study record counts (focus/exam/notes) unchanged: $countsBefore -> $countsAfter")
    }

    private fun select(tab: String) {
        // RN Modal dismissal crosses the JS/native window boundary; Espresso
        // otherwise tries to find the tab in the still-focused dialog root.
        await("activity window focused") { root?.hasWindowFocus() == true }
        onView(testId("tab-$tab")).perform(click())
        await("$tab activated") { required("pane-$tab").alpha == 1f }
    }

    private fun testId(id: String) = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) { description.appendText("RN testID=$id") }
        override fun matchesSafely(view: View): Boolean = view.getTag(com.facebook.react.R.id.react_test_id) == id
    }

    private fun find(id: String): View? = root?.let { views(it).firstOrNull { view -> testId(id).matches(view) } }
    private fun required(id: String): View = checkNotNull(find(id)) { "Missing RN view: $id" }
    private var root: View? = null

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }

    private fun bounds(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun selectedText(text: String): Boolean {
        // Fabric can flatten native parents while ReactScrollView clips its
        // children. Locate the native text from the window, as Espresso does.
        var view = views(root!!).firstOrNull { it is TextView && it.text.toString() == text }
            ?: return false
        while (view !== root) {
            if (view.createAccessibilityNodeInfo().isSelected) return true
            view = view.parent as View
        }
        return false
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        scenario.onActivity {
            root = it.window.decorView
            result = block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for $message")
    }
}
