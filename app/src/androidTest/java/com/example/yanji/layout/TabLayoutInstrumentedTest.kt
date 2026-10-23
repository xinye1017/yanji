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
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Press
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yanji.MainActivity
import com.example.yanji.YanjiApplication
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.data.NoteEntry
import com.example.yanji.service.FocusTimerService
import com.facebook.react.views.view.ReactViewGroup
import com.facebook.react.uimanager.PointerEvents
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.ParcelFileDescriptor
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.hamcrest.Description
import org.hamcrest.Matcher
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
        // App.tsx mounts a tab screen on its first visit, so only the launch tab
        // owns a native ScrollView here. Waiting for all three would assert the
        // eager startup rendering the startup perf work deliberately removed;
        // tests that compare every pane mount the rest via [mountAllTabs].
        await("launch tab native RN scroll viewport mounted") {
            root?.let { views(it).filterIsInstance<ScrollView>().any { scroll -> scroll.height > 0 } } == true
        }
        await("launched activity window focused") { root?.hasWindowFocus() == true }
    }

    @Test
    fun nativeScrollViewCoordinatesCoincide() {
        // Uses native ScrollViews instead of testIDs, so it also detects the
        // original shipped defect before installing a corrected app bundle.
        mountAllTabs()
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
            // ReactActivity stops its Fabric surface asynchronously. Finish JS
            // teardown before the next test mounts another app in the same host.
            val stopped = (context.applicationContext as YanjiApplication).reactHost
                .destroy("Layout test teardown", null)
            check(stopped.waitForCompletion(15, TimeUnit.SECONDS) && !stopped.isFaulted() && !stopped.isCancelled()) {
                "React host teardown failed: ${stopped.getError()}"
            }
        }
        // Only remove records created through this test's UI flow.
        runBlocking {
            val noteDao = YanjiDatabase.getDatabase(context).noteEntryDao()
            // Every note this test writes starts with the per-run `ownedNoteText`
            // prefix (the plain record, its edit, and the scroll seed rows), so
            // the prefix identifies this run's rows and nothing the user owns.
            noteDao.getAllOnce().filter { it.content.startsWith(ownedNoteText) }
                .forEach { noteDao.deleteById(it.id) }
            val taskDao = YanjiDatabase.getDatabase(context).studyTaskDao()
            taskDao.getAllFlow().first()
                .filter { it.title == ownedTaskTitle && (ownedTaskRowId == null || it.id == ownedTaskRowId) }
                .forEach { taskDao.deleteById(it.id) }
        }
    }

    @Test
    fun allPanesFillTheSameSafeAreaWithoutTabTranslation() {
        mountAllTabs()
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
        seedScrollableToday()
        for (tab in tabs) {
            select(tab)
            if (tab == "review") await("Review daily snapshot loaded") {
                findText("正在读取这一天的记录…", "review") == null
            }
            val scroll = onMain { required("scroll-$tab") as ScrollView }
            awaitScrollSettled(scroll)
            for (attempt in 0 until 20) {
                if (onMain { !scroll.canScrollVertically(1) }) break
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
                awaitScrollSettled(scroll)
            }
            onMain { assertFalse("$tab must reach the bottom through native gestures", scroll.canScrollVertically(1)) }
            val offset = onMain {
                val viewport = bounds(scroll)
                val content = scroll.getChildAt(0) as ViewGroup
                val last = content.getChildAt(content.childCount - 1)
                assertTrue("$tab last element must clear the floating dock", bounds(last).bottom <= bounds(required("bottom-tab-bar")).top)
                assertTrue("$tab last element must remain visible", bounds(last).bottom > viewport.top)
                Log.i("YanjiLayoutTest", "$tab scrollY=${scroll.scrollY} viewport=$viewport last=${bounds(last)}")
                scroll.scrollY
            }
            val contentHeight = onMain { scroll.getChildAt(0).height }
            select(tabs.first { it != tab })
            awaitScrollSettled(scroll)
            onMain {
                assertEquals("$tab hidden content height must remain stable", contentHeight, scroll.getChildAt(0).height)
                assertEquals("$tab hidden offset must remain stable", offset, scroll.scrollY)
            }
            select(tab)
            awaitScrollSettled(scroll)
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
        // 90 minutes is a real user reachable value: the ruler steps by five
        // from 5 to 180. Drive it through the same accessibility action a
        // screen reader exposes, not through a coordinate tap on the scale.
        adjustDurationTo(90)
        await("duration selected") { selectedPreset(90) }
        select("review")
        onView(allOf(withText("趋势统计"), isDescendantOfA(testId("scroll-review")))).perform(scrollTo(), click())
        await("Review period selector mounted") { find("review-date") is TextView }
        val initialDate = onMain { (required("review-date") as TextView).text.toString() }
        onView(withContentDescription("上一周期")).perform(scrollTo(), click())
        await("date changed") { (required("review-date") as TextView).text.toString() != initialDate }
        val chosenDate = onMain { (required("review-date") as TextView).text.toString() }
        select("today")
        onMain {
            val scroll = required("scroll-today") as ScrollView
            scroll.scrollTo(0, scroll.getChildAt(0).height)
        }
        await("Today scrolled to footer") { !(required("scroll-today") as ScrollView).canScrollVertically(1) }
        onView(withContentDescription("新建今日记录")).perform(scrollTo(), click())
        await("draft note editor opened") { root?.hasWindowFocus() == false }
        awaitDialogChild(withContentDescription("记录内容"))
        val draft = "unsaved layout regression draft"
        onView(withContentDescription("记录内容")).inRoot(isDialog()).perform(replaceText(draft), closeSoftKeyboard())
        pressBack() // Exercise the modal's onRequestClose without a moving sheet target.
        await("draft note editor closed") { root?.hasWindowFocus() == true }
        select("focus")
        onMain { assertTrue("Focus duration was preserved", selectedPreset(90)) }
        select("review")
        onMain { assertEquals(chosenDate, (required("review-date") as TextView).text.toString()) }
        select("today")
        onView(withContentDescription("新建今日记录")).perform(scrollTo(), click())
        await("draft note editor reopened") { root?.hasWindowFocus() == false }
        awaitDialogChild(withContentDescription("记录内容"))
        onView(withContentDescription("记录内容")).inRoot(isDialog()).check(matches(withText(draft)))
        pressBack()
        await("draft note editor closed again") { root?.hasWindowFocus() == true }

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
        await("immersive focus mounted") { find("focus-running") != null }
        pressBack()
        await("system back returns to Today without stopping the timer") { required("pane-today").alpha == 1f }
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
        await("running focus controls mounted") { find("focus-running") != null }
        clickImmersive("暂停专注")
        await("test session paused") { ActiveSessionCoordinator.active.value?.paused == true && find("focus-paused") != null }
        clickImmersive("继续专注")
        await("test session resumed") { ActiveSessionCoordinator.active.value?.paused == false && find("focus-running") != null }
        clickImmersive("暂停专注")
        await("test session paused again") { ActiveSessionCoordinator.active.value?.paused == true && find("focus-paused") != null }
        clickImmersive("放弃本轮")
        await("discard confirmation dialog opened") { root?.hasWindowFocus() == false }
        awaitDialogChild(withText("确认放弃"))
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
        onView(withContentDescription("新建今日记录")).perform(scrollTo(), click())
        await("note editor opened") { root?.hasWindowFocus() == false }
        awaitDialogChild(withContentDescription("记录内容"))
        onView(withContentDescription("记录内容")).inRoot(isDialog())
            .perform(replaceText(ownedNoteText), closeSoftKeyboard())
        onView(withContentDescription("保存")).inRoot(isDialog()).perform(click())
        awaitRoom("new note is persisted") { db.noteEntryDao().getAllOnce().any { it.content == ownedNoteText } }
        await("note editor closed after saving") { root?.hasWindowFocus() == true }
        await("new note appears after Room invalidation") {
            findText(ownedNoteText, "today") != null
        }
        // A compact row has no standalone edit button: the row itself opens the
        // record, and the anchored 「···」 menu holds the management actions. Tap
        // the row the way a reader does, on the label the row actually publishes.
        onView(withContentDescription("查看完整记录：$ownedNoteText")).perform(scrollTo(), click())
        await("note editor opened for edit") { root?.hasWindowFocus() == false }
        awaitDialogChild(withContentDescription("记录内容"))
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
        onView(withContentDescription("添加任务")).perform(scrollTo(), click())
        await("task editor opened") { root?.hasWindowFocus() == false }
        awaitDialogChild(withContentDescription("任务名称"))
        onView(withContentDescription("任务名称")).inRoot(isDialog())
            .perform(replaceText(ownedTaskTitle), closeSoftKeyboard())
        val category = YanjiRepository.getInstance().subjects.value.first { it.enabled && it.parentId == null }
        onView(withText(category.name)).inRoot(isDialog()).perform(scrollTo(), click())
        onView(withContentDescription("保存任务")).inRoot(isDialog()).perform(scrollTo(), click())
        awaitRoom("new task is persisted") { db.studyTaskDao().getAllFlow().first().any { it.title == ownedTaskTitle } }
        // Bind the exact row to this test instance *before* any UI wait. A wait
        // can fail after the row is already durable, and teardown must still be
        // able to clean it up — and to recognise a session bound to it.
        val task = runBlocking { db.studyTaskDao().getAllFlow().first().first { it.title == ownedTaskTitle } }
        ownedTaskId = task.id
        await("task editor closed after saving") { root?.hasWindowFocus() == true }
        await("new task is visible after native table invalidation") { findText(ownedTaskTitle, "today") != null }
        onView(withContentDescription("开始专注：${task.title}")).perform(scrollTo(), click())
        await("Focus consumes the task preset") { required("pane-focus").alpha == 1f }
        await("Task binding is shown") { bindingRow(task.title) != null }
        onMain { assertTrue("Preset adopts the task's planned duration", selectedPreset(task.plannedMinutes)) }
        select("review")
        select("focus")
        onMain {
            assertNotNull("Task binding survives tab switching", bindingRow(task.title))
            assertTrue("Task duration survives tab switching", selectedPreset(task.plannedMinutes))
        }
        // Clear the local binding before the owned row is deleted, so a later
        // submit can never carry a task id this test removed.
        onView(withContentDescription("取消关联任务")).perform(scrollTo(), click())
        await("task binding cleared") { bindingRow(task.title) == null }
        assertNull("Preparing a preset does not start a native session", ActiveSessionCoordinator.active.value)
        Log.i("YanjiLayoutTest", "Task preset survives tab switching without starting or changing a study session")
    }

    /** Seeds two owned notes through the repository so Today can scroll even on a tall phone. */
    private fun seedScrollableToday() {
        select("today")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        repeat(2) { index ->
            val content = "$ownedNoteText seed $index\nScroll preservation\nHidden page content\nReturned page content"
            runBlocking {
                YanjiRepository.getInstance().addOrUpdateNoteAndAwait(
                    NoteEntry(
                        id = UUID.randomUUID().toString(),
                        date = todayStr,
                        content = content
                    )
                )
            }
            await("seeded note is rendered") { findText(content, "today") != null }
        }
        val today = onMain { required("scroll-today") as ScrollView }
        awaitScrollSettled(today)
        onMain {
            assertTrue("Today has enough real content to exercise a nonzero scroll offset",
                today.getChildAt(0).height > today.height)
        }
    }

    private fun awaitScrollSettled(scroll: ScrollView) {
        var previous: Pair<Int, Int>? = null
        var stableSamples = 0
        await("native scroll and content layout settled") {
            val current = scroll.scrollY to scroll.getChildAt(0).height
            stableSamples = if (current == previous && !scroll.isLayoutRequested) stableSamples + 1 else 0
            previous = current
            stableSamples >= 4
        }
    }

    // Fabric can flatten the pane wrapper; scope matches to the native page.
    // A compact timeline row shows the stored text as ONE display line: the row
    // collapses every whitespace run (including the newlines a seeded note
    // carries) into single spaces before rendering. Compare against that same
    // normalization, otherwise a multi-line seed can never match the TextView
    // even though the note is on screen.
    private fun findText(text: String, tab: String): TextView? = find("scroll-$tab")?.let {
        val expected = previewOf(text)
        views(it).filterIsInstance<TextView>().firstOrNull { previewOf(it.text.toString()) == expected }
    }

    /**
     * Mirrors TimelineCompactRow's one-line preview exactly — whitespace runs
     * collapse to one space, and an empty note renders the row's placeholder —
     * so a test asserts the text the user actually reads.
     */
    private fun previewOf(content: String): String =
        content.replace(Regex("\\s*\\n+\\s*"), " ").trim().ifEmpty { "无标题记录" }

    /**
     * Waits until a child of the open modal dialog is actually mounted.
     *
     * A window losing focus is not the same event as the sheet's contents being
     * mounted: an RN Modal attaches an empty dialog window first and fills it on
     * a later frame, so an Espresso lookup fired straight after the focus wait
     * can land in the gap and dump a hierarchy that already contains the view it
     * says is missing. Waiting on a child of `isDialog()` closes that gap for
     * every sheet interaction instead of relying on a fixed sleep.
     */
    private fun awaitDialogChild(matcher: Matcher<View>) {
        // Deliberately not routed through [await]: that helper evaluates its
        // condition inside scenario.onActivity, and Espresso may only be driven
        // from the instrumentation thread. Poll here instead.
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                onView(matcher).inRoot(isDialog()).check(matches(isDisplayed()))
                return
            } catch (_: NoMatchingViewException) {
                // Not mounted yet.
            } catch (_: AssertionError) {
                // Matched, but not laid out/displayed yet.
            }
            SystemClock.sleep(100)
        }
        captureFailure("modal dialog content mounted")
        fail("Timed out waiting for the modal dialog content to mount")
    }

    private fun select(tab: String) {
        // RN Modal dismissal crosses the JS/native window boundary; Espresso
        // otherwise tries to find the tab in the still-focused dialog root.
        await("activity window focused") { root?.hasWindowFocus() == true }
        onView(testId("tab-$tab")).perform(click())
        await("$tab activated") { required("pane-$tab").alpha == 1f }
        // The shell mounts a tab screen on its first visit (App.tsx), so the
        // pane wrapper becomes active one frame before its page content exists.
        // Wait for the page's own root: Today and Review own a ScrollView, and
        // Focus swaps its ScrollView for the immersion field while a session
        // runs, so each tab is probed with the roots it can actually show.
        await("$tab content mounted") { pageRoot(tab) != null }
    }

    /** The root view each tab's page renders once the shell has mounted it. */
    private fun pageRoot(tab: String): View? = when (tab) {
        "focus" -> find("focus-ready") ?: find("focus-running") ?: find("focus-paused")
        else -> find("scroll-$tab")
    }

    /** Pane state at the moment a wait failed, so the CI report explains itself. */
    private fun paneDiagnostics(): String = tabs.joinToString("; ") { tab ->
        val pane = find("pane-$tab") as? ViewGroup
        val tags = pane?.let { group ->
            (0 until group.childCount).joinToString(",") {
                group.getChildAt(it).getTag(com.facebook.react.R.id.react_test_id)?.toString() ?: "-"
            }
        }
        "$tab pane=${pane?.javaClass?.simpleName}:children=${pane?.childCount}:alpha=${pane?.alpha}" +
            ":root=${pageRoot(tab) != null}:tags=$tags"
    }

    /**
     * The Focus page row that shows the preset task. Matched on the task
     * title alone: the surrounding copy (and the separator between it and the
     * title) has changed twice during the design pass, while this test only
     * cares that the binding itself survived the tab switch.
     */
    private fun bindingRow(title: String): TextView? = find("scroll-focus")?.let {
        views(it).filterIsInstance<TextView>().firstOrNull {
            previewOf(it.text.toString()).contains(title)
        }
    }

    /** Visits every tab once so each lazily-mounted page has mounted. */
    private fun mountAllTabs() {
        tabs.forEach { select(it) }
        select(tabs.first())
    }

    /**
     * Clicks an immersion-field control. The running/paused field is a fixed
     * full-height column with no ScrollView (its actions are always on screen),
     * so Espresso's [scrollTo] precondition would reject the view outright.
     */
    private fun clickImmersive(label: String) {
        await("immersion control 「$label」 mounted") {
            root?.let { views(it).any { view -> view.contentDescription?.toString() == label } } == true
        }
        onView(withContentDescription(label)).perform(click())
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
     * The countdown duration is a vernier ruler, not a row of preset chips: the
     * old `预设 N 分钟` content description no longer exists. The ruler sets
     * `accessibilityValue={{min, now, max, text}}`, and RN folds the `text` into
     * the node's content description (`调整倒计时长度, 45 分钟`) while also
     * publishing the numbers as `RangeInfo` — so match the label by prefix and
     * read the value from the accessibility tree, as a screen reader would.
     */
    private fun dialView() = views(root!!)
        .firstOrNull { it.contentDescription?.startsWith("调整倒计时长度") == true }

    /** Read the value exposed to accessibility services. */
    private fun dialRange(): Int? = dialView()?.createAccessibilityNodeInfo()?.rangeInfo?.current?.toInt()

    private fun selectedPreset(minutes: Int): Boolean = dialRange() == minutes

    /**
     * Steps the ruler to `target` through its accessibility actions. This is
     * the shipped assistive path (ReactAccessibilityDelegate maps `increment` /
     * `decrement` to ACTION_SCROLL_FORWARD / BACKWARD), so the test exercises
     * the control the way a screen-reader user reaches it instead of tapping
     * ruler coordinates that depend on the device width.
     */
    private fun adjustDurationTo(target: Int) {
        await("duration ruler mounted") { dialRange() != null }
        repeat(60) {
            val current = onMain { checkNotNull(dialRange()) }
            if (current == target) return
            onMain {
                val action = if (current < target) {
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                checkNotNull(dialView()).performAccessibilityAction(action, null)
            }
            await("duration advances from $current") { dialRange()?.let { it != current } == true }
        }
        fail("Duration ruler did not reach $target minutes")
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
            val scrolls = root?.let { views(it).filterIsInstance<ScrollView>().count { scroll -> scroll.height > 0 } }
            Log.i("YanjiLayoutTest", "Failure $message: focus=${root?.hasWindowFocus()} scrolls=$scrolls ${paneDiagnostics()}")
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
