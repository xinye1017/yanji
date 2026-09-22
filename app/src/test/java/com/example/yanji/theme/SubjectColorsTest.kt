package com.example.yanji.theme

import androidx.compose.ui.graphics.Color
import com.example.yanji.data.Subject
import com.example.yanji.data.SubjectCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SubjectColorsTest {

    @Test
    fun subjectColorSlotsAreDeterministicAndFollowCategoryHierarchy() {
        // 核心学科槽位严格绑定
        assertEquals(0, subjectColorSlot("math"))
        assertEquals(0, subjectColorSlot("math_advanced"))
        assertEquals(0, subjectColorSlot("math_linear"))
        assertEquals(0, subjectColorSlot("math_probability"))

        assertEquals(1, subjectColorSlot("major"))
        assertEquals(1, subjectColorSlot("major_organization"))
        assertEquals(1, subjectColorSlot("major_data_structure"))

        assertEquals(2, subjectColorSlot("english"))
        assertEquals(3, subjectColorSlot("politics"))
        assertEquals(4, subjectColorSlot("other"))

        // 带未分类后缀的 ID 也应归类到正确槽位
        assertEquals(0, subjectColorSlot("math${SubjectCatalog.UNCLASSIFIED_SUFFIX}"))
        assertEquals(2, subjectColorSlot("english${SubjectCatalog.UNCLASSIFIED_SUFFIX}"))
    }

    @Test
    fun subjectSlotSurvivesCatalogReorderAddAndRemove() {
        val original = SubjectCatalog.all

        try {
            // 重排学科列表不会使固定槽位发生偏移
            SubjectCatalog.replaceAll(
                original.map { subject ->
                    if (subject.id == "math") subject.copy(sortOrder = 999) else subject
                }
            )
            assertEquals(0, subjectColorSlot("math"))
            assertEquals(1, subjectColorSlot("major"))
            assertEquals(2, subjectColorSlot("english"))

            val custom = Subject(
                id = "custom_test",
                name = "测试学科",
                colorHex = "#B8426B",
                sortOrder = 1
            )
            SubjectCatalog.replaceAll(SubjectCatalog.all + custom)
            assertEquals(0, subjectColorSlot("math"))

            SubjectCatalog.replaceAll(SubjectCatalog.all.filterNot { it.id == custom.id })
            assertEquals(0, subjectColorSlot("math"))
        } finally {
            SubjectCatalog.replaceAll(original)
        }
    }

    @Test
    fun allFiveMascotPalettesHaveDistinctStepsAndPassContrastRequirements() {
        val darkCard = YanjiDarkSurface
        val lightCard = Color.White

        for (id in MascotThemeId.entries) {
            val palette = mascotSubjectPaletteOf(id)
            val light = palette.lightSteps
            val dark = palette.darkSteps

            assertEquals("$id must define 5 light steps", 5, light.size)
            assertEquals("$id must define 5 dark steps", 5, dark.size)
            assertEquals("$id light steps must all be distinct", 5, light.toSet().size)
            assertEquals("$id dark steps must all be distinct", 5, dark.toSet().size)

            // 浅色对比度 >= 3.0
            light.forEachIndexed { idx, c ->
                val cr = contrast(c, lightCard)
                assertTrue("$id light step #$idx contrast on white ($cr) must >= 3.0", cr >= 3.0)
            }

            // 深色对比度 >= 2.4
            dark.forEachIndexed { idx, c ->
                val cr = contrast(c, darkCard)
                assertTrue("$id dark step #$idx contrast on dark card ($cr) must >= 2.4", cr >= 2.4)
            }
        }
    }

    @Test
    fun yanjiSubjectColorFollowsMascotThemeSmoothly() {
        val mathCloudLight = yanjiSubjectColor("math", MascotThemes.CLOUD, isDark = false)
        val mathBunnyLight = yanjiSubjectColor("math", MascotThemes.BUNNY, isDark = false)
        val mathFrogLight = yanjiSubjectColor("math", MascotThemes.FROG, isDark = false)

        // 切换不同伙伴时，学科取色平滑切换为该伙伴的高质感主色相
        assertEquals(CloudSubjectPalette.lightSteps[0], mathCloudLight)
        assertEquals(BunnySubjectPalette.lightSteps[0], mathBunnyLight)
        assertEquals(FrogSubjectPalette.lightSteps[0], mathFrogLight)

        // 不同伙伴主题下的学科颜色各不相同
        assertNotEquals(mathCloudLight, mathBunnyLight)
        assertNotEquals(mathCloudLight, mathFrogLight)
    }

    @Test
    fun subjectColorIdentitySurvivesCatalogReorderAddAndRemove() {
        val original = SubjectCatalog.all
        val originalMathColor = SubjectCatalog.find("math")!!.colorHex

        try {
            SubjectCatalog.replaceAll(
                original.map { subject ->
                    if (subject.id == "math") subject.copy(sortOrder = 999) else subject
                }
            )
            assertEquals(originalMathColor, SubjectCatalog.find("math")!!.colorHex)

            val custom = Subject(
                id = "custom_color_test",
                name = "测试学科",
                colorHex = "#B8426B",
                sortOrder = 1
            )
            SubjectCatalog.replaceAll(SubjectCatalog.all + custom)
            assertEquals(originalMathColor, SubjectCatalog.find("math")!!.colorHex)

            SubjectCatalog.replaceAll(SubjectCatalog.all.filterNot { it.id == custom.id })
            assertEquals(originalMathColor, SubjectCatalog.find("math")!!.colorHex)
        } finally {
            SubjectCatalog.replaceAll(original)
        }
    }

    @Test
    fun defaultAndGeneratedPaletteColorsStayReadableInLightAndDark() {
        val colors = SubjectCatalog.defaults.map { it.colorHex } + listOf(
            "#356AE6", "#8B7CF6", "#2F9E6D", "#E67E22",
            "#B8426B", "#3B78B8", "#A95822", "#2F7F55", "#667085"
        )

        colors.distinct().forEach { hex ->
            val light = resolveSubjectColor(hex, isDark = false)
            val dark = resolveSubjectColor(hex, isDark = true)
            assertTrue("$hex must be readable on white", contrast(light, Color.White) >= 3.0)
            assertTrue("$hex must be readable on dark surface", contrast(dark, YanjiDarkSurface) >= 3.0)
        }
    }

    @Test
    fun repeatedResolutionKeepsTheSameSubjectIdentity() {
        val storedHex = SubjectCatalog.find("math")!!.colorHex
        val first = resolveSubjectColor(storedHex, isDark = false)
        val second = resolveSubjectColor(storedHex, isDark = false)

        assertEquals(first, second)
        assertEquals(storedHex, SubjectCatalog.find("math")!!.colorHex)
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linearize(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }
}
