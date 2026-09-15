package com.example.yanji.ui

import androidx.compose.ui.unit.dp
import com.example.yanji.theme.DarkColorScheme
import com.example.yanji.theme.LightColorScheme
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
 * 4. 破坏 GlassBottomBar 浮动 Dock 宽度响应式范围约束（260dp..360dp）。
 */
class UiDesignSystemContractTest {

    @Test
    fun verifyRadiusTokensMatchDesignSpecification() {
        assertEquals("StandardCardRadius must be 24.dp per DESIGN.md", 24.dp, YanjiRadius.StandardCardRadius)
        assertEquals("HeroCardRadius must be 28.dp per DESIGN.md", 28.dp, YanjiRadius.HeroCardRadius)
        assertEquals("CompactCardRadius must be 16.dp", 16.dp, YanjiRadius.CompactCardRadius)
        assertEquals("ButtonRadius must be 12.dp per YanjiButtons", 12.dp, YanjiRadius.ButtonRadius)
        assertEquals("InputRadius must be 16.dp per YanjiTextField", 16.dp, YanjiRadius.InputRadius)
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
        assertNotEquals("Dark background must not be pure #000000", pureBlack, DarkColorScheme.background)
        assertNotEquals("Dark surface must not be pure #000000", pureBlack, DarkColorScheme.surface)
        assertNotEquals("Dark surfaceVariant must not be pure #000000", pureBlack, DarkColorScheme.surfaceVariant)

        // Light color scheme sanity
        assertEquals("Light background", com.example.yanji.theme.YanjiBackground, LightColorScheme.background)
        assertEquals("Light surface", com.example.yanji.theme.YanjiSurface, LightColorScheme.surface)
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

        val segmentedFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/components/GlassSegmentedControl.kt")
        assertTrue("GlassSegmentedControl.kt must exist", segmentedFile.exists())
        val segmentedContent = segmentedFile.readText()
        assertTrue("GlassSegmentedControl must use Role.Tab for a11y", segmentedContent.contains("Role.Tab"))

        val rollingFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/components/RollingNumber.kt")
        assertTrue("RollingNumber.kt must exist", rollingFile.exists())

        val sectionFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/components/YanjiSection.kt")
        assertTrue("YanjiSection.kt must exist", sectionFile.exists())
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
