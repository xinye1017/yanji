package com.example.yanji.theme

import androidx.compose.ui.graphics.Color
import com.example.yanji.data.Subject
import com.example.yanji.data.SubjectCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SubjectColorsTest {

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
