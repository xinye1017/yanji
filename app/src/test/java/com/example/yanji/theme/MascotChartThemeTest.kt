package com.example.yanji.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证各吉祥物主题的图表色板满足「按顺序分配、同色系、高区分度、可读」的设计约束。
 *
 * 关键背景：学科大类与子类都可由用户**自由增删改名**，所以颜色**不能与学科语义绑定**。
 * 每套主题只预设 5 个有序色阶，渲染时按学科顺序依次分配——本测试固化的正是这些色阶的
 * 客观质量指标（`MascotChartTheme.kt` 文件头注释里的 4 条硬约束）。
 */
class MascotChartThemeTest {

    // --- 色彩空间换算（测试内自持，避免为测试改动生产代码可见性）---

    private fun srgbToLinear(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * srgbToLinear(color.red) +
            0.7152 * srgbToLinear(color.green) +
            0.0722 * srgbToLinear(color.blue)

    /** WCAG 相对对比度 */
    private fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun toLab(color: Color): DoubleArray {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)
        val x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041
        fun f(t: Double): Double =
            if (t > 216.0 / 24389.0) Math.cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
        val fx = f(x / 0.95047)
        val fy = f(y / 1.0)
        val fz = f(z / 1.08883)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /** CIEDE2000 色差 */
    private fun deltaE(c1: Color, c2: Color): Double {
        val l1 = toLab(c1)
        val l2 = toLab(c2)
        val L1 = l1[0]; val a1 = l1[1]; val b1 = l1[2]
        val L2 = l2[0]; val a2 = l2[1]; val b2 = l2[2]

        val C1 = Math.hypot(a1, b1)
        val C2 = Math.hypot(a2, b2)
        val cBar = (C1 + C2) / 2
        val g = 0.5 * (1 - Math.sqrt(Math.pow(cBar, 7.0) / (Math.pow(cBar, 7.0) + Math.pow(25.0, 7.0))))
        val a1p = (1 + g) * a1
        val a2p = (1 + g) * a2
        val C1p = Math.hypot(a1p, b1)
        val C2p = Math.hypot(a2p, b2)
        val h1p = if (a1p == 0.0 && b1 == 0.0) 0.0 else (Math.toDegrees(Math.atan2(b1, a1p)) + 360) % 360
        val h2p = if (a2p == 0.0 && b2 == 0.0) 0.0 else (Math.toDegrees(Math.atan2(b2, a2p)) + 360) % 360

        val dLp = L2 - L1
        val dCp = C2p - C1p
        val dhp = when {
            C1p * C2p == 0.0 -> 0.0
            Math.abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p - h1p > 180 -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val dHp = 2 * Math.sqrt(C1p * C2p) * Math.sin(Math.toRadians(dhp) / 2)

        val Lbp = (L1 + L2) / 2
        val Cbp = (C1p + C2p) / 2
        val hbp = when {
            C1p * C2p == 0.0 -> h1p + h2p
            Math.abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 -
            0.17 * Math.cos(Math.toRadians(hbp - 30)) +
            0.24 * Math.cos(Math.toRadians(2 * hbp)) +
            0.32 * Math.cos(Math.toRadians(3 * hbp + 6)) -
            0.20 * Math.cos(Math.toRadians(4 * hbp - 63))
        val dTheta = 30 * Math.exp(-Math.pow((hbp - 275) / 25, 2.0))
        val rc = 2 * Math.sqrt(Math.pow(Cbp, 7.0) / (Math.pow(Cbp, 7.0) + Math.pow(25.0, 7.0)))
        val sl = 1 + (0.015 * Math.pow(Lbp - 50, 2.0)) / Math.sqrt(20 + Math.pow(Lbp - 50, 2.0))
        val sc = 1 + 0.045 * Cbp
        val sh = 1 + 0.015 * Cbp * t
        val rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc

        return Math.sqrt(
            Math.pow(dLp / sl, 2.0) +
                Math.pow(dCp / sc, 2.0) +
                Math.pow(dHp / sh, 2.0) +
                rt * (dCp / sc) * (dHp / sh)
        )
    }

    private val white = Color(0xFFFFFFFF)

    /** 浅色卡片底色（YanjiSurface 近似白） */
    private val lightCard = Color(0xFFFFFFFF)

    /** 深色卡片底色（YanjiDarkSurfaceSoft #1D2536） */
    private val darkCard = Color(0xFF1D2536)

    // -----------------------------------------------------------------------
    // 1. 结构完整性
    // -----------------------------------------------------------------------

    @Test
    fun verifyAllFiveMascotThemesHaveExactlyFiveStepPalettes() {
        val palettes = MascotThemeId.entries.map { mascotChartPaletteOf(it) }
        assertEquals("Must support exactly 5 mascot theme chart palettes", 5, palettes.size)

        for (id in MascotThemeId.entries) {
            val p = mascotChartPaletteOf(id)
            assertEquals("$id must define exactly 5 light steps", 5, p.lightSteps.size)
            assertEquals("$id must define exactly 5 dark steps", 5, p.darkSteps.size)
            assertEquals("$id light steps must all be distinct", 5, p.lightSteps.toSet().size)
            assertEquals("$id dark steps must all be distinct", 5, p.darkSteps.toSet().size)
        }

        // 5 个主题的首色互不相同，避免主题之间「看起来是同一套图」
        assertEquals(
            "Each mascot theme must have a distinct first light step",
            5,
            palettes.map { it.lightSteps.first() }.toSet().size
        )
        assertEquals(
            "Each mascot theme must have a distinct first dark step",
            5,
            palettes.map { it.darkSteps.first() }.toSet().size
        )
    }

    @Test
    fun verifyLightAndDarkStepsDiffer() {
        for (id in MascotThemeId.entries) {
            val p = mascotChartPaletteOf(id)
            assertNotEquals("$id light and dark steps must not be identical", p.lightSteps, p.darkSteps)
        }
    }

    // -----------------------------------------------------------------------
    // 2. 相邻/两两区分度（核心指标）
    // -----------------------------------------------------------------------

    @Test
    fun verifyAdjacentStepsAreClearlyDistinguishable() {
        for (id in MascotThemeId.entries) {
            val p = mascotChartPaletteOf(id)
            for ((mode, steps) in listOf("light" to p.lightSteps, "dark" to p.darkSteps)) {
                for (i in 0 until steps.size - 1) {
                    val de = deltaE(steps[i], steps[i + 1])
                    assertTrue(
                        "$id/$mode adjacent steps #$i and #${i + 1} must have ΔE2000 >= 18 " +
                            "(was ${"%.1f".format(de)})；相邻扇区/并列进度条必须一眼可分",
                        de >= 18.0
                    )
                }
            }
        }
    }

    @Test
    fun verifyAllStepPairsRemainDistinct() {
        for (id in MascotThemeId.entries) {
            val p = mascotChartPaletteOf(id)
            for ((mode, steps) in listOf("light" to p.lightSteps, "dark" to p.darkSteps)) {
                for (i in steps.indices) {
                    for (j in i + 1 until steps.size) {
                        val de = deltaE(steps[i], steps[j])
                        assertTrue(
                            "$id/$mode steps #$i and #$j must have ΔE2000 >= 12 " +
                                "(was ${"%.1f".format(de)})；图例中不能出现「看起来一样」的两项",
                            de >= 12.0
                        )
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // 3. 可读性（对比度）
    // -----------------------------------------------------------------------

    @Test
    fun verifyLightStepsAreReadableOnLightCard() {
        for (id in MascotThemeId.entries) {
            mascotChartPaletteOf(id).lightSteps.forEachIndexed { i, c ->
                val ratio = contrastRatio(c, lightCard)
                assertTrue(
                    "$id light step #$i must have contrast >= 3.0 on white (was ${"%.2f".format(ratio)})",
                    ratio >= 3.0
                )
            }
        }
    }

    @Test
    fun verifyDarkStepsAreReadableOnDarkCard() {
        for (id in MascotThemeId.entries) {
            mascotChartPaletteOf(id).darkSteps.forEachIndexed { i, c ->
                val ratio = contrastRatio(c, darkCard)
                assertTrue(
                    "$id dark step #$i must have contrast >= 2.4 on #1D2536 (was ${"%.2f".format(ratio)})",
                    ratio >= 2.4
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 4. 按顺序分配语义（与学科名完全解耦）
    // -----------------------------------------------------------------------

    @Test
    fun verifyColorsAreAssignedByOrderIndexOnly() {
        val palette = FrogChartPalette
        val light = palette.steps(isDark = false)

        // 顺序分配：第 i 项取第 i 个色阶（纯位置映射，与学科名无关）
        assertEquals(5, light.size)
        assertNotEquals(light[0], light[1])
        assertNotEquals("any two distinct indices must differ", light[0], light[4])

        // 超出 5 项时按色阶循环，保证任何自定义科目数量都有色可用
        val dark = palette.steps(isDark = true)
        assertEquals(dark[5 % dark.size], palette.colorAt(5, isDark = true))
        assertEquals(dark[6 % dark.size], palette.colorAt(6, isDark = true))
    }

    @Test
    fun verifyColorAtCyclesForArbitrarySubjectCounts() {
        for (id in MascotThemeId.entries) {
            val p = mascotChartPaletteOf(id)
            // 负索引与超大索引都应安全取色（用户可能自定义任意数量的学科）
            assertEquals(p.lightSteps[0], p.colorAt(0, isDark = false))
            assertEquals(p.lightSteps[2], p.colorAt(2, isDark = false))
            assertEquals(p.lightSteps[0], p.colorAt(5, isDark = false))
            assertEquals(p.darkSteps[4], p.colorAt(-1, isDark = true))
            assertEquals(p.darkSteps[1], p.colorAt(6, isDark = true))
        }
    }

    @Test
    fun verifySingleSubjectGetsFirstStep() {
        assertEquals(
            CloudChartPalette.lightSteps.first(),
            CloudChartPalette.colorAt(0, isDark = false)
        )
    }
}
