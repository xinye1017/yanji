package com.example.yanji.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证各吉祥物主题的图表与进度条专属调色板，确保：
 * 1. 5 个主题（卷卷、绵绵、冰冰、豆豆、芽芽）均有完整且独立的调色板配置；
 * 2. 浅色模式和深色模式针对夜间发光与白底清晰度具有独立配色；
 * 3. 核心考研学科能够精准匹配对应语义色彩；
 * 4. 大类与子类统计在环形饼图与进度条上色彩分明不冲突。
 */
class MascotChartThemeTest {

    @Test
    fun verifyAllFiveMascotThemesHaveDistinctChartPalettes() {
        val palettes = MascotThemeId.entries.map { mascotChartPaletteOf(it) }
        assertEquals("Must support exactly 5 mascot theme chart palettes", 5, palettes.size)

        // 验证 5 个主题的主色（数学/核心色）各不相同
        val lightMathColors = palettes.map { it.lightMath }.toSet()
        assertEquals("Each mascot theme must have a distinct light primary chart color", 5, lightMathColors.size)

        val darkMathColors = palettes.map { it.darkMath }.toSet()
        assertEquals("Each mascot theme must have a distinct dark primary chart color", 5, darkMathColors.size)
    }

    @Test
    fun verifyLightAndDarkPaletteSeparation() {
        for (id in MascotThemeId.entries) {
            val palette = mascotChartPaletteOf(id)
            assertNotEquals("Dark math color must differ from light math color for $id", palette.lightMath, palette.darkMath)
            assertNotEquals("Dark major color must differ from light major color for $id", palette.lightMajor, palette.darkMajor)
            assertNotEquals("Dark english color must differ from light english color for $id", palette.lightEnglish, palette.darkEnglish)
            assertNotEquals("Dark politics color must differ from light politics color for $id", palette.lightPolitics, palette.darkPolitics)
            assertTrue("Series palette must contain at least 6 distinct steps for $id", palette.lightSeries.size >= 6)
            assertTrue("Dark series palette must contain at least 6 distinct steps for $id", palette.darkSeries.size >= 6)
        }
    }

    @Test
    fun verifyCategoryLevelSubjectSemanticResolution() {
        val palette = BunnyChartPalette // 绵绵柔樱粉
        val items = listOf(
            "math" to "数学一",
            "major" to "408专业课",
            "english" to "英语一",
            "politics" to "政治",
            "other" to "其他"
        )

        val colorsLight = resolveSubjectChartColors(items, palette, isDark = false, isSubcategory = false)
        assertEquals(5, colorsLight.size)
        assertEquals(palette.lightMath, colorsLight["数学一"])
        assertEquals(palette.lightMajor, colorsLight["408专业课"])
        assertEquals(palette.lightEnglish, colorsLight["英语一"])
        assertEquals(palette.lightPolitics, colorsLight["政治"])
        assertEquals(palette.lightOther, colorsLight["其他"])

        val colorsDark = resolveSubjectChartColors(items, palette, isDark = true, isSubcategory = false)
        assertEquals(palette.darkMath, colorsDark["数学一"])
        assertEquals(palette.darkMajor, colorsDark["408专业课"])
        assertEquals(palette.darkEnglish, colorsDark["英语一"])
        assertEquals(palette.darkPolitics, colorsDark["政治"])
        assertEquals(palette.darkOther, colorsDark["其他"])
    }

    @Test
    fun verifySubcategoryLevelPreventsColorCollisions() {
        val palette = ShibaChartPalette // 豆豆暖栗橙
        val subcategoryItems = listOf(
            "math_advanced" to "高等数学",
            "math_linear" to "线性代数",
            "math_prob" to "概率论",
            "major_ds" to "数据结构",
            "major_net" to "计算机网络",
            "english" to "英语一"
        )

        // 在子类模式下，同大类的科目不应全都混成同一个颜色，而应由 series 调色板赋予区分度
        val colors = resolveSubjectChartColors(subcategoryItems, palette, isDark = false, isSubcategory = true)
        assertEquals(6, colors.size)

        // 高等数学与线性代数应具有不同色阶
        assertNotEquals("Subcategories within math should not have identical colors in donut chart",
            colors["高等数学"], colors["线性代数"])
        assertNotEquals(colors["线性代数"], colors["概率论"])
    }

    @Test
    fun verifySingleSubjectResolution() {
        val palette = FrogChartPalette // 芽芽新芽绿
        val singleItem = listOf("math" to "数学一")
        val result = resolveSubjectChartColors(singleItem, palette, isDark = false, isSubcategory = false)

        assertEquals(1, result.size)
        assertEquals(palette.lightMath, result["数学一"])
    }
}
