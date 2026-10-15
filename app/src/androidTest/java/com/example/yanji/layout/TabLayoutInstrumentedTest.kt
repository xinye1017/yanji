package com.example.yanji.layout

import android.graphics.Rect
import android.graphics.Bitmap
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Press
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.MainActivity
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.service.FocusTimerService
import com.facebook.react.views.view.ReactViewGroup
import com.facebook.react.uimanager.PointerEvents
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File
import android.os.ParcelFileDescriptor
import java.util.UUID
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
 * Cleanup removes ONLY the note/task rows this test created through the UI
 * (unique per-run `snapshot-*` / `task-*` names) and discards ONLY a native
 * session this test itself started. A pre-existing user session or row is never
 * touched. Cleanup runs on failure too: the owned task id is recovered from the
 * unique per-run title when a UI wait fails after the row is already durable.
 */
@RunWith(AndroidJUnit4::class)
class TabLayoutInstrumentedTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val tabs = listOf("today", "focus", "review")
    private var ownedSessionId: String? = null
    private var ownedTaskId: String? = null
    private val ownedNoteText = "snapshot-${UUID.randomUUID().toString().take(8)}"
    private val ownedTaskTitle = "task-${UUID.randomUUID().toString().take(8)}"

    @Before
    fun launchWithDeviceAuthorization() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish") ||
            InstrumentationRegistry.getArguments().getString("allowPersonalDevice") == "true") {
            "Personal-device UI tests require explicit authorization."
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Wake for the explicitly authorized test; never change the device's sleep settings.
        val wakeResult = instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP")
        ParcelFileDescriptor.AutoCloseInputStream(wakeResult).use { it.readBytes() }
        check(!(instrumentation.targetContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
            "Unlock the device before running personal-device UI tests."
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Recover the owned task row *before* correlating anything. The UI wait
        // that normally records its id can fail after the row is durable; a
        // leaked row (or a session this test can no longer recognise) is exactly
        // the failure case, so teardown must not depend on the happy path.
        // The per-run title is a fresh UUID, so only this instance's row can
        // ever match — a user's row is never touched.
        val ownedTaskRowId: String? = ownedTaskId ?: runBlocking {
            YanjiDatabase.getDatabase(context).studyTaskDao().getAllFlow().first()
                .firstOrNull { it.title == ownedTaskTitle }?.id
        }
        if (::scenario.isInitialized) {
            scenario.onActivity { activity ->
                val active = ActiveSessionCoordinator.active.value
                // Discard ONLY a session this test started: the one it recorded
                // itself, or one bound to the task the preset test created (the
                // no-auto-start assertion can fail while a session is live).
                val owned = active != null &&
                    (active.sessionId == ownedSessionId ||
                        (ownedTaskRowId != null && active.taskId == ownedTaskRowId))
                if (owned) {
                    FocusTimerService.discardTimer(activity)
                }
            }
            // Close the Activity first: FocusScreen holds the task preset in
            // local state, so the owned row must not be deleted from under it.
            scenario.close()
        }
        // Only remove records created through this test's UI flow.
        runBlocking {
            val noteDao = YanjiDatabase.getDatabase(context).noteEntryDao()
            noteDao.getAllOnce().filter { it.content == ownedNoteText || it.content == "$ownedNoteText edited" }
                .forEach { noteDao.deleteById(it.id) }
            val taskDao = YanjiDatabase.getDatabase(context).studyTaskDao()
            taskDao.getAllFlow().first()
                .filter { it.title == ownedTaskTitle && (ownedTaskRowId == null || it.id == ownedTaskRowId) }
                .forEach { taskDao.deleteById(it.id) }
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
            assertEquals("Content fills the viewport behind the floating dock", bounds(shell).bottom, content.bottom)
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
                onView(testId("scroll-$tab")).perform(GeneralSwipeAction(
                    Swipe.FAST,
                    { view ->
                        val viewport = bounds(view)
                        val dock = bounds(required("bottom-tab-bar"))
                        floatArrayOf(viewport.exactCenterX(), (dock.top - 24).toFloat())
                    },
                    { view ->
                        val viewport = bounds(view)
                        floatArrayOf(viewport.exactCenterX(), (viewport.top + viewport.height() / 4).toFloat())
                    },
                    Press.FINGER
                ))
                SystemClock.sleep(250) // Let native fling settle before measuring.
            }
            assertTrue("$tab must reach the bottom through native gestures", atEnd)
            val offset = onMain {
                val viewport = bounds(scroll)
                val content = scroll.getChildAt(0) as ViewGroup
                val last = content.getChildAt(content.childCount - 1)
                assertTrue("$tab last element must clear the floating dock", bounds(last).bottom <= bounds(required("bottom-tab-bar")).top)
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
        onView(withContentDescription("预设 90 分钟")).perform(click())
        await("duration selected") { selectedPreset(90) }
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
        onView(withContentDescription("新建今日记录")).perform(click())
        val draft = "unsaved layout regression draft"
        onView(withContentDescription("记录内容")).inRoot(isDialog()).perform(replaceText(draft), closeSoftKeyboard())
        pressBack() // Exercise the modal's onRequestClose without a moving sheet target.
        select("focus")
        onMain { assertTrue("Focus duration was preserved", selectedPreset(90)) }
        select("review")
        onMain { assertEquals(chosenDate, (required("review-date") as TextView).text.toString()) }
        select("today")
        onView(withContentDescription("新建今日记录")).perform(click())
        onView(withContentDescription("记录内容")).inRoot(isDialog()).check(matches(withText(draft)))
        pressBack()

        select("focus")
        onMain {
            val scroll = required("scroll-focus") as ScrollView
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        onView(allOf(withContentDescription("开始专注"), isDescendantOfA(testId("scroll-focus")))).perform(click())
        await("real native timer started") {
            ActiveSessionCoordinator.active.value?.also { ownedSessionId = it.sessionId } != null
        }
        val sessionId = checkNotNull(ownedSessionId)
        val elapsed = FocusTimerService.elapsedSecondsForUi.value
        onView(withContentDescription("返回今天，专注继续在后台运行")).perform(click())
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
        onView(withContentDescription("暂停专注")).perform(click())
        await("test session paused") { ActiveSessionCoordinator.active.value?.paused == true }
        onView(withContentDescription("放弃本轮")).perform(click())
        onView(withText("确认放弃")).inRoot(isDialog()).perform(click())
        await("test session discarded without saving a study record") { ActiveSessionCoordinator.active.value == null }
        val countsAfter = runBlocking {
            listOf(db.focusSessionDao().count(), db.examSessionDao().count(), db.noteEntryDao().count())
        }
        assertEquals("Existing study records must remain unchanged", countsBefore, countsAfter)
        Log.i("YanjiLayoutTest", "Study record counts (focus/exam/notes) unchanged: $countsBefore -> $countsAfter")
    }

    @Test
    fun editedRecordRefreshesTodayAndPreviouslyHiddenReview() {
        val db = YanjiDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        val countsBefore = runBlocking {
            listOf(db.focusSessionDao().count(), db.examSessionDao().count(), db.noteEntryDao().count())
        }
        select("review") // Prime a snapshot before the later write while hidden.
        select("today")
        onMain {
            val scroll = required("scroll-today") as ScrollView
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        onView(withContentDescription("新建今日记录")).perform(click())
        onView(withContentDescription("记录内容")).inRoot(isDialog())
            .perform(replaceText(ownedNoteText), closeSoftKeyboard())
        onView(withContentDescription("保存")).inRoot(isDialog()).perform(click())
        awaitRoom("new note is persisted") { db.noteEntryDao().getAllOnce().any { it.content == ownedNoteText } }
        await("note editor closed after saving") { root?.hasWindowFocus() == true }
        await("new note appears after Room invalidation") {
            findText(ownedNoteText, "today") != null
        }
        onView(withContentDescription("编辑记录：$ownedNoteText")).perform(click())
        onView(withContentDescription("记录内容")).inRoot(isDialog())
            .perform(replaceText("$ownedNoteText edited"), closeSoftKeyboard())
        onView(withContentDescription("保存")).inRoot(isDialog()).perform(click())
        awaitRoom("edited note is persisted") { db.noteEntryDao().getAllOnce().any { it.content == "$ownedNoteText edited" } }
        await("edited note replaces its previous text") { findText("$ownedNoteText edited", "today") != null }
        select("review")
        await("hidden Review refreshes when activated") { findText("$ownedNoteText edited", "review") != null }
        runBlocking {
            val dao = db.noteEntryDao()
            val owned = dao.getAllOnce().filter { it.content == "$ownedNoteText edited" }
            assertEquals("Editing preserves one record", 1, owned.size)
            owned.forEach { dao.deleteById(it.id) }
            assertEquals(countsBefore, listOf(db.focusSessionDao().count(), db.examSessionDao().count(), dao.count()))
        }
        select("today")
        await("deleted test note is absent on reactivation") { findText("$ownedNoteText edited", "today") == null }
        Log.i("YanjiLayoutTest", "UI note create/edit + hidden-page refresh passed; counts unchanged: $countsBefore")
    }

    @Test
    fun taskPresetKeepsItsBindingAcrossTabs() {
        val db = YanjiDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        // Preparing a preset must never start, pause or discard a user's session.
        check(ActiveSessionCoordinator.active.value == null) { "Existing focus session: task preset test refused safely." }
        select("today")
        onMain {
            val scroll = required("scroll-today") as ScrollView
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        // Deterministic owned fixture: create the task through the UI with a
        // per-run title. No fallback to a pre-existing user row, and no
        // three-pending-task threshold that silently degrades the assertions.
        onView(withContentDescription("添加任务")).perform(click())
        onView(withContentDescription("任务名称")).inRoot(isDialog())
            .perform(replaceText(ownedTaskTitle), closeSoftKeyboard())
        val category = YanjiRepository.getInstance().subjects.value.first { it.enabled && it.parentId == null }
        onView(withText(category.name)).inRoot(isDialog()).perform(click())
        onView(withContentDescription("保存任务")).inRoot(isDialog()).perform(click())
        awaitRoom("new task is persisted") { db.studyTaskDao().getAllFlow().first().any { it.title == ownedTaskTitle } }
        // Bind the exact row to this test instance *before* any UI wait. A wait
        // can fail after the row is already durable, and teardown must still be
        // able to clean it up — and to recognise a session bound to it.
        val task = runBlocking { db.studyTaskDao().getAllFlow().first().first { it.title == ownedTaskTitle } }
        ownedTaskId = task.id
        await("task editor closed after saving") { root?.hasWindowFocus() == true }
        await("new task is visible after native table invalidation") { findText(ownedTaskTitle, "today") != null }
        onView(withContentDescription("为${task.title}设置专注")).perform(click())
        await("Focus consumes the task preset") { required("pane-focus").alpha == 1f }
        await("Task binding is shown") { findText("关联任务 · ${task.title}", "focus") != null }
        onMain { assertTrue("Preset adopts the task's planned duration", selectedPreset(task.plannedMinutes)) }
        select("review")
        select("focus")
        onMain {
            assertNotNull("Task binding survives tab switching", findText("关联任务 · ${task.title}", "focus"))
            assertTrue("Task duration survives tab switching", selectedPreset(task.plannedMinutes))
        }
        // Clear the local binding before the owned row is deleted, so a later
        // submit can never carry a task id this test removed.
        onView(withContentDescription("取消关联任务")).perform(click())
        await("task binding cleared") { findText("关联任务 · ${task.title}", "focus") == null }
        assertNull("Preparing a preset does not start a native session", ActiveSessionCoordinator.active.value)
        Log.i("YanjiLayoutTest", "Task preset survives tab switching without starting or changing a study session")
    }

    // Fabric can flatten the pane wrapper. The native ScrollView remains the
    // actual parent of that page's text and keeps hidden-page matches separate.
    private fun findText(text: String, tab: String): TextView? = find("scroll-$tab")?.let {
        views(it).filterIsInstance<TextView>().firstOrNull { view -> view.text.toString() == text }
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

    /**
     * The duration presets expose their selected state through accessibility,
     * not through a combined "90 分钟" text node — the app renders the number
     * alone plus a separate "分钟" label, so an exact-text match can never hit.
     */
    private fun selectedPreset(minutes: Int): Boolean {
        // Fabric can flatten native parents while the chip clips its children.
        // Locate the view from the window, as Espresso does.
        var view = views(root!!).firstOrNull { it.contentDescription == "预设 $minutes 分钟" }
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

    /**
     * Capture the on-device evidence for any failed wait: a full-screen shot
     * plus the pane/focus state. Shared by [await] and [awaitRoom] so a Room
     * timeout is no longer image-less, and so the failure exit status is kept.
     */
    private fun captureFailure(message: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "layout-regression").apply { mkdirs() }
        // Clear only this test's own screenshot name, so earlier evidence from a
        // previous run cannot be mistaken for the current failure.
        File(directory, "failure.png").delete()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        onMain {
            Log.i("YanjiLayoutTest", "Failure $message: todayAlpha=${find("pane-today")?.alpha}, focus=${root?.hasWindowFocus()}")
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(100)
        }
        captureFailure(message)
        fail("Timed out waiting for $message")
    }

    private fun awaitRoom(message: String, condition: suspend () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (runBlocking { condition() }) return
            SystemClock.sleep(100)
        }
        captureFailure(message)
        fail("Timed out waiting for $message")
    }
}
