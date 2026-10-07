package com.example.yanji.ui

import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiDarkBackground
import com.example.yanji.theme.YanjiDarkSurface
import com.example.yanji.theme.YanjiDarkSurfaceSoft
import com.example.yanji.theme.YanjiBackground
import com.example.yanji.theme.YanjiSurface
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 静态架构守卫测试：Compose Design System、响应式与 UI 一致性契约检查。
 *
 * 防止后续迭代中重新引入：
 * 1. 散落随意手写的 Detail TopBar 与返回按键（破坏统一体验）；
 * 2. 随意硬编码 Card 圆角（例如 RoundedCornerShape(20.dp) 违背 DESIGN.md）；
 * 3. 破坏 Radius 与 Spacing 关键 token 契约；
 * 4. 破坏 GlassBottomBar 浮动 Dock 的响应式宽度与全宽渐变模糊契约。
 */
class UiDesignSystemContractTest {

    @Test
    fun verifyRadiusTokensMatchDesignSpecification() {
        assertEquals("StandardCardRadius must be 24.dp per DESIGN.md", 24.dp, YanjiRadius.StandardCardRadius)
        assertEquals("HeroCardRadius must be 28.dp per DESIGN.md", 28.dp, YanjiRadius.HeroCardRadius)
        assertEquals("CompactCardRadius must be 16.dp", 16.dp, YanjiRadius.CompactCardRadius)
        assertEquals("ButtonRadius must be 12.dp per YanjiButtons", 12.dp, YanjiRadius.ButtonRadius)
        assertEquals("InputRadius must be 16.dp (OutlinedTextField 容器圆角)", 16.dp, YanjiRadius.InputRadius)
        assertEquals("Small radius must be 12.dp", 12.dp, YanjiRadius.Small)
    }

    @Test
    fun verifySpacingTokensMatchDesignSpecification() {
        assertEquals("PageHorizontalPadding must be 20.dp", 20.dp, YanjiSpacing.PageHorizontalPadding)
        assertEquals("CardPadding must be 20.dp", 20.dp, YanjiSpacing.CardPadding)
        assertEquals("CardPaddingCompact must be 16.dp", 16.dp, YanjiSpacing.CardPaddingCompact)
        assertEquals("ItemGap must be 12.dp", 12.dp, YanjiSpacing.ItemGap)
        assertEquals("ItemGapSmall must be 8.dp", 8.dp, YanjiSpacing.ItemGapSmall)
        assertEquals("TopBarHeight must be 56.dp", 56.dp, YanjiSpacing.TopBarHeight)
    }

    @Test
    fun verifyDarkThemeColorSchemeIsCalmSlateNavy() {
        // Dark theme should NOT be harsh pure black #000000
        val pureBlack = androidx.compose.ui.graphics.Color(0xFF000000)
        assertNotEquals("Dark background must not be pure #000000", pureBlack, YanjiDarkBackground)
        assertNotEquals("Dark surface must not be pure #000000", pureBlack, YanjiDarkSurface)
        assertNotEquals("Dark surfaceVariant must not be pure #000000", pureBlack, YanjiDarkSurfaceSoft)

        // Light color scheme sanity
        assertNotEquals("Light background differs from dark", YanjiDarkBackground, YanjiBackground)
        assertNotEquals("Light surface differs from dark", YanjiDarkSurface, YanjiSurface)
    }

    @Test
    fun verifyDetailScreensAdoptYanjiDetailTopBar() {
        val projectRoot = findProjectRoot()
        val detailScreens = listOf(
            "app/src/main/java/com/example/yanji/ui/detail/DailyStudyDetailScreen.kt",
            "app/src/main/java/com/example/yanji/ui/detail/SubjectStudyDetailScreen.kt",
            "app/src/main/java/com/example/yanji/ui/detail/FocusSessionDetailScreen.kt",
            "app/src/main/java/com/example/yanji/ui/exam/ExamDetailScreen.kt",
            "app/src/main/java/com/example/yanji/ui/exam/ExamHistoryScreen.kt",
            "app/src/main/java/com/example/yanji/ui/achievement/AchievementsScreen.kt"
        )

        for (relativePath in detailScreens) {
            val file = File(projectRoot, relativePath)
            assertTrue("File must exist: $relativePath", file.exists())
            val content = file.readText()

            assertTrue(
                "$relativePath must use YanjiDetailTopBar for header consistency",
                content.contains("YanjiDetailTopBar(")
            )
            assertTrue(
                "$relativePath must not use raw Icons.Default.ArrowBack or Icons.AutoMirrored.Filled.ArrowBack",
                !content.contains("Icons.Default.ArrowBack") && !content.contains("Icons.AutoMirrored.Filled.ArrowBack")
            )
        }
    }

    @Test
    fun verifyGlassBottomBarIsResponsiveDock() {
        val projectRoot = findProjectRoot()
        val navFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/navigation/GlassBottomBar.kt")
        assertTrue(navFile.exists())
        val content = navFile.readText()

        assertTrue(
            "GlassBottomBar must use BoxWithConstraints for responsive dock sizing",
            content.contains("BoxWithConstraints")
        )
        assertTrue(
            "GlassBottomBar must define MinDockWidth = 260.dp and MaxDockWidth = 360.dp",
            content.contains("MinDockWidth = 260.dp") && content.contains("MaxDockWidth = 360.dp")
        )
        assertTrue(
            "GlassBottomBar must clamp dock width between MinDockWidth and MaxDockWidth",
            content.contains("coerceIn(MinDockWidth, MaxDockWidth)") || content.contains("coerceIn(260.dp, 360.dp)")
        )
        assertTrue(
            "GlassBottomBar must not have fixed 272.dp dock width",
            !content.contains("val dockWidth = 272.dp")
        )
        assertTrue(
            "GlassBottomBar must apply a full-width progressive backdrop below the dock top edge",
            content.contains("HazeProgressive.verticalGradient") &&
                content.contains(".then(gradientBackdrop)")
        )
        assertTrue(
            "Progressive backdrop must fade into full blur",
            (content.contains("startIntensity = 0.35f") || content.contains("startIntensity = 0f")) && content.contains("endIntensity = 1f")
        )
        assertTrue(
            "Dock and backdrop must share the same liquid-glass blur token",
            content.contains("blurRadius = glassTokens.blurRadius") &&
                content.contains("hazeState = hazeState")
        )
        assertTrue(
            "Every hazeEffect must provide an explicit background color to prevent device crashes",
            content.contains("backgroundColor = Color.Transparent")
        )
    }

    @Test
    fun verifyHomeScreenAdoptsFlowRowAndDesignSystemTokens() {
        val projectRoot = findProjectRoot()
        val homeFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/home/HomeScreen.kt")
        assertTrue(homeFile.exists())
        val content = homeFile.readText()

        assertTrue("HomeScreen must use FlowRow for subject / quick actions", content.contains("FlowRow"))
        assertTrue("HomeScreen must use YanjiCard", content.contains("YanjiCard("))
        assertTrue("HomeScreen must not hardcode RoundedCornerShape(20.dp) for standard cards", !content.contains("shape = RoundedCornerShape(20.dp)"))
    }

    @Test
    fun verifyLiquidGlassPrimitivesAndComponentsContract() {
        val projectRoot = findProjectRoot()
        val glassSurfaceFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/components/GlassSurface.kt")
        assertTrue("GlassSurface.kt must exist", glassSurfaceFile.exists())
        val glassSurfaceContent = glassSurfaceFile.readText()
        assertTrue("GlassSurface must support HazeState", glassSurfaceContent.contains("hazeState: HazeState?"))
        assertTrue("GlassSurface must support fallback without blur", glassSurfaceContent.contains("fallbackColor"))

        val yanjiSegmentedFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/components/YanjiSegmentedControl.kt")
        assertTrue("YanjiSegmentedControl.kt must exist", yanjiSegmentedFile.exists())
        // 语义契约必须匹配**代码**而不是 KDoc：此前它匹配到的是 GlassSegmentedControl 注释里的
        // 一句「完整适配 Role.Tab」，函数体其实只是逐参数转发的别名，断言在注释上就变绿了。
        val yanjiSegmentedContent = codeOf(yanjiSegmentedFile)
        assertTrue("YanjiSegmentedControl must use selectable(role = Role.Tab)",
            yanjiSegmentedContent.contains("selectable(") && yanjiSegmentedContent.contains("Role.Tab"))

        val rollingContent = codeOf(File(projectRoot, "app/src/main/java/com/example/yanji/ui/components/RollingNumber.kt"))
        assertTrue("RollingNumber must merge its per-digit Text nodes into one semantic unit",
            rollingContent.contains("mergeDescendants = true"))

        val rollingFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/components/RollingNumber.kt")
        assertTrue("RollingNumber.kt must exist", rollingFile.exists())

    }

    @Test
    fun verifyTodayPlanLivesOnHomeNotOnFocusPrepPage() {
        val projectRoot = findProjectRoot()

        // 今日计划是「今天要做什么」，属于首页开屏就该回答的问题；
        // 放在专注准备页会让用户在选学科的路径里才发现自己的计划。
        val homeFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/home/TodayPlanCard.kt")
        assertTrue("TodayPlanCard.kt must exist on the home feature", homeFile.exists())
        val homeCode = codeOf(homeFile)
        assertTrue(
            "TodayPlanCard must render the 今日计划 title",
            homeCode.contains("今日计划")
        )
        // 完成态/未完成态由同一个圆圈承担，行内不应再出现独立的 Checkbox 方块：
        // 方形 Checkbox 在 56dp 高的行里过重，且其主色填充会与学科色点抢注意力。
        assertTrue(
            "TodayPlanCard must not reintroduce the M3 Checkbox",
            !homeCode.contains("Checkbox(")
        )

        val homeScreenCode = codeOf(
            File(projectRoot, "app/src/main/java/com/example/yanji/ui/home/HomeScreen.kt")
        )
        assertTrue(
            "HomeScreen must render TodayPlanCard",
            homeScreenCode.contains("TodayPlanCard(")
        )

        // 专注准备页不应再出现今日计划：那里只保留与当前科目/模式绑定的快捷专注。
        val quietSetupCode = codeOf(
            File(projectRoot, "app/src/main/java/com/example/yanji/ui/focus/QuietFocusSetupContent.kt")
        )
        assertTrue(
            "QuietFocusSetupContent must not re-add 今日计划",
            !quietSetupCode.contains("今日计划")
        )
    }

    @Test
    fun verify80_20RuleContentCardsDoNotAbuseHazeEffect() {
        val projectRoot = findProjectRoot()
        val contentFiles = listOf(
            "app/src/main/java/com/example/yanji/ui/home/HomeScreen.kt",
            "app/src/main/java/com/example/yanji/ui/components/CheckInCard.kt",
            "app/src/main/java/com/example/yanji/ui/stats/SubjectDistributionCard.kt",
            "app/src/main/java/com/example/yanji/ui/stats/StatsTrendChart.kt"
        )
        for (relPath in contentFiles) {
            val file = File(projectRoot, relPath)
            assertTrue("File must exist: $relPath", file.exists())
            val content = file.readText()
            assertTrue(
                "$relPath must NOT directly invoke hazeEffect; 80% content layer must remain quiet per 80/20 rule",
                !content.contains(".hazeEffect(")
            )
        }
    }

    /** 读源码并剥掉 KDoc 与行注释，供「必须使用某 API」这类断言匹配真实代码。 */
    /**
     * 读源码并剥掉 KDoc 与行注释。
     *
     * 语义类契约（Role / selectable / mergeDescendants）必须断言**代码**：
     * 直接 readText() 会让断言命中 KDoc 里的一句话，在注释上就变绿了。
     */
    private fun codeOf(file: File): String {
        val out = StringBuilder()
        var inBlockComment = false
        for (rawLine in file.readText().lineSequence()) {
            var line = rawLine
            if (inBlockComment) {
                val end = line.indexOf("*/")
                if (end < 0) continue
                inBlockComment = false
                line = line.substring(end + 2)
            }
            val blockStart = line.indexOf("/*")
            if (blockStart >= 0) {
                inBlockComment = true
                line = line.substring(0, blockStart)
            }
            val lineComment = line.indexOf("//")
            if (lineComment >= 0) line = line.substring(0, lineComment)
            out.append(line).append('\n')
        }
        return out.toString()
    }

    private fun findProjectRoot(): File {
        var current: File? = File(System.getProperty("user.dir") ?: ".")
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists() || File(current, "settings.gradle").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File(".")
    }
}
