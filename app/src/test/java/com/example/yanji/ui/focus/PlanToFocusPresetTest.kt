package com.example.yanji.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 首页「今日计划」行右侧「开始」→ 专注准备页 这条链路的守卫。
 *
 * 用户诉求：点「开始」应**直接**落到能开计时的那一屏，并把计划标题填成本次专注的备注。
 * 早先这条链路只切 tab、把学科/时长/备注写进 FocusScreen 的状态源，准备流程仍从
 * 「选大类」第一步开始，用户还得自己再走两三步，且一进 MODULE 步骤时
 * `onCategoryClick` 会用默认 45 分钟覆盖掉预设时长。
 *
 * 这里守住四个不变量：
 *  1. 计划标题确实被当作专注备注传过去（用户明确点名的部分）；
 *  2. 预设由准备流程**唯一**消费，父页不再就地应用一遍；
 *  3. 准备页落地即最后一步，且滚轮上限与计划时长上限对齐（否则 120 分钟的计划
 *     会显示 100 分钟，而真正计的又是 120 分钟）；
 *  4. 计划行内「整行改计划、Play 才开专注」的分工，以及末步屏底栏滑出。
 */
class PlanToFocusPresetTest {

    private fun source(relativePath: String): String {
        val root = findProjectRoot()
        val file = File(root, relativePath)
        assertTrue("$relativePath must exist", file.exists())
        return file.readText()
    }

    @Test
    fun navigationCarriesThePlanTitleAsTheFocusNote() {
        val nav = source("app/src/main/java/com/example/yanji/Navigation.kt")
        assertTrue(
            "点击计划行必须构造 FocusPresetRequest，否则专注页拿不到任何预设",
            nav.contains("onStartTask = { task ->") &&
                nav.contains("com.example.yanji.data.FocusPresetRequest(")
        )
        assertTrue(
            "计划的标题必须作为专注备注传过去（note = task.title）",
            nav.contains("note = task.title")
        )
        assertTrue(
            "计划预计时长必须一并传过去，否则准备页无从按计划设定倒计时",
            nav.contains("plannedMinutes = task.plannedMinutes")
        )
        assertTrue(
            "点击后必须切到专注 tab",
            nav.contains("currentTab = YanjiTab.FOCUS")
        )
    }

    @Test
    fun focusScreenOnlyRelaysTheRequestAndNeverAppliesItItself() {
        val screen = source("app/src/main/java/com/example/yanji/ui/focus/FocusScreen.kt")
        assertTrue(
            "请求必须转交给准备流程消费",
            screen.contains("presetRequest = presetRequest") &&
                screen.contains("onPresetApplied = { onClearPresetRequest?.invoke() }")
        )
        assertTrue(
            "FocusScreen 不得再就地写 noteText —— 父子两处各消费一半会留下半应用状态",
            !screen.contains("noteText = req.note")
        )
        assertTrue(
            "已有计时在跑时准备流程不参与组合，预设必须被丢弃而不是留着事后误用",
            screen.contains("if (activeSession != null) onClearPresetRequest?.invoke()")
        )
    }

    @Test
    fun setupFlowLandsOnTheLastStepWithEveryFieldPreset() {
        val setup = source("app/src/main/java/com/example/yanji/ui/focus/QuietFocusSetupContent.kt")
        assertTrue(
            "准备流程必须接收预设请求",
            setup.contains("presetRequest: FocusPresetRequest? = null")
        )
        assertTrue(
            "落点必须是最后一步（RHYTHM），跳过选大类 / 选细分",
            setup.contains("currentStep = QuietFocusStep.RHYTHM")
        )
        assertTrue(
            "科目必须经 onSelectSubject 写回上层，不在本页另存一份事实",
            setup.contains("onSelectSubject(resolved)")
        )
        assertTrue(
            "时长必须经 onSelectMode 写回上层",
            setup.contains("onSelectMode(FocusModes.forPlannedMinutes(minutes))")
        )
        assertTrue(
            "备注必须经 onNoteChange 写回上层",
            setup.contains("onNoteChange(req.note)")
        )
        assertTrue(
            "计划选「不设定时间」时必须落到正向计时，与计划的意图一致",
            setup.contains("onSelectMode(FocusModes.COUNT_UP)")
        )
        assertTrue(
            "滚轮上限必须与「添加今日计划」的时长上限（180 分钟）对齐，" +
                "否则 120 分钟的计划会停在 100 分钟的滚轮上",
            setup.contains("private const val QuietDurationMaxMinutes = 180")
        )
    }

    @Test
    fun planDurationAndFocusWheelShareTheSameUpperBound() {
        val plan = source("app/src/main/java/com/example/yanji/ui/home/AddPlanSheet.kt")
        val setup = source("app/src/main/java/com/example/yanji/ui/focus/QuietFocusSetupContent.kt")
        // 两个滚轮各自声明的上限必须一致；一旦单侧调整，就会重新出现
        // 「计划写 120 分钟、准备页只能表达到 100 分钟」的偏差。
        val planMax = Regex("""\((\d+)\.\.(\d+) step 5\)""").find(plan)?.groupValues?.get(2)?.toInt()
        val setupMax = Regex("""QuietDurationMaxMinutes = (\d+)""")
            .find(setup)?.groupValues?.get(1)?.toInt()
        assertEquals("计划的时长滚轮上限应可从源码读出", 180, planMax)
        assertEquals("准备页的滚轮上限应可从源码读出", 180, setupMax)
        assertEquals("两侧时长上限必须一致", planMax, setupMax)
    }

    /**
     * 计划行内的点击分工：整行改计划，只有右侧 Play 才开专注。
     *
     * 曾经整行点击即开始专注，导致想改一条计划的时长或备注时无处下手。
     * 两个入口必须在**同一行**里按区域分开，且各自带读屏标签。
     */
    @Test
    fun rowClickEditsAndOnlyThePlayButtonStartsFocus() {
        val row = source("app/src/main/java/com/example/yanji/ui/home/TodayPlanCard.kt")
        assertTrue(
            "整行点击必须是编辑计划，而不是开始专注",
            Regex("""onClick\s*=\s*onEdit""").containsMatchIn(row)
        )
        assertTrue(
            "Play 必须是自带触控框与点击处理的真按钮，而非装饰性图标",
            row.contains("clickable(role = Role.Button, onClick = onStart)")
        )
        assertTrue(
            "Play 必须带读屏标签，否则视障用户听不出这是「开始」",
            row.contains("contentDescription = \"开始专注\"")
        )
    }

    /**
     * 末步沉浸式：底部浮岛滑出屏幕，且离开专注 tab 时必须归还。
     *
     * 直接 `if (!immersive) { GlassBottomBar(...) }` 会变成闪现消失，
     * 收不到「滑出屏幕」的方向感；而只在沉浸式置位时隐藏、不复位，
     * 切到其他 tab 会一直缺一条底栏。
     */
    @Test
    fun bottomBarSlidesOutOnTheImmersiveLastStep() {
        val nav = source("app/src/main/java/com/example/yanji/Navigation.kt")
        val setup = source("app/src/main/java/com/example/yanji/ui/focus/QuietFocusSetupContent.kt")

        assertTrue(
            "底栏显隐必须走 AnimatedVisibility 才能做退场动画",
            nav.contains("AnimatedVisibility(")
        )
        assertTrue(
            "退场必须是向下移出屏幕（位移量取自身高度）",
            nav.contains("slideOutVertically(") &&
                Regex("""\)\s*\{\s*fullHeight\s*->\s*fullHeight\s*\}""").containsMatchIn(nav)
        )
        assertTrue(
            "沉浸式必须进入底栏的可见条件",
            nav.contains("!focusSetupImmersive")
        )
        assertTrue(
            "准备流程必须把沉浸式开关上报给宿主",
            nav.contains("onImmersiveChange = { focusSetupImmersive = it }")
        )

        assertTrue(
            "准备流程必须接收沉浸式上报通道",
            setup.contains("onImmersiveChange: ((Boolean) -> Unit)? = null")
        )
        assertTrue(
            "沉浸式 = 走到最后一步",
            setup.contains("val isImmersive = currentStep == QuietFocusStep.RHYTHM")
        )
        assertTrue(
            "置位与复位都要上报 —— 复位靠 DisposableEffect，LaunchedEffect 不会在离开组合时回调",
            setup.contains("onImmersiveChange?.invoke(isImmersive)") &&
                setup.contains("DisposableEffect(Unit)") &&
                setup.contains("onDispose { onImmersiveChange?.invoke(false) }")
        )
        assertTrue(
            "末步 immersive 为真时内容只避系统手势区；仍按 112dp 预留会在底栏消失后留一片空白",
            setup.contains("immersive = isImmersive") &&
                setup.contains("Modifier.navigationBarsPadding()")
        )
    }

    private fun findProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        return File(".").canonicalFile
    }
}
