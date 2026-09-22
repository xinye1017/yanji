package com.example.yanji.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.example.yanji.data.SubjectCatalog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val DEFAULT_SUBJECT_COLOR_HEX = "#667085"
private const val MIN_SUBJECT_CONTRAST = 3.0

private val SubjectLightBackground = Color(0xFFFFFFFF)
private val SubjectDarkBackground = YanjiDarkSurface

/**
 * 学科颜色只来源于 Subject.colorHex，与伙伴主题、列表位置和学科名称完全解耦。
 *
 * Light/Dark 模式保持同一 hue identity；只有当原色在当前背景上的对比度不足时，
 * 才向黑/白轻微调整明度，避免浅色卡片或深色卡片上的低对比度。
 */
fun resolveSubjectColor(colorHex: String, isDark: Boolean): Color {
    val base = parseSubjectColor(colorHex)
    val background = if (isDark) SubjectDarkBackground else SubjectLightBackground
    if (contrastRatio(base, background) >= MIN_SUBJECT_CONTRAST) return base

    val target = if (isDark) Color.White else Color.Black
    for (step in 1..6) {
        val candidate = mix(base, target, step * 0.08f)
        if (contrastRatio(candidate, background) >= MIN_SUBJECT_CONTRAST) return candidate
    }
    return mix(base, target, 0.48f)
}

@Composable
@ReadOnlyComposable
fun yanjiSubjectColor(subjectId: String): Color {
    val cleanId = subjectId.removeSuffix(SubjectCatalog.UNCLASSIFIED_SUFFIX)
    val colorHex = SubjectCatalog.find(cleanId)?.colorHex
        ?: SubjectCatalog.categoryOf(subjectId)?.colorHex
        ?: DEFAULT_SUBJECT_COLOR_HEX
    return resolveSubjectColor(colorHex, LocalYanjiDarkTheme.current)
}

@Composable
@ReadOnlyComposable
fun yanjiSubjectColorOf(subjectName: String): Color {
    val subjectId = SubjectCatalog.idForDisplayName(subjectName)
    return if (subjectId != null) {
        yanjiSubjectColor(subjectId)
    } else {
        resolveSubjectColor(DEFAULT_SUBJECT_COLOR_HEX, LocalYanjiDarkTheme.current)
    }
}

private fun parseSubjectColor(colorHex: String): Color {
    val raw = colorHex.trim().removePrefix("#")
    val value = raw.toLongOrNull(16) ?: return Color(0xFF667085)
    return when (raw.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> Color(0xFF667085)
    }
}

private fun mix(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha
    )
}

private fun contrastRatio(a: Color, b: Color): Double {
    val l1 = relativeLuminance(a)
    val l2 = relativeLuminance(b)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun linearize(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * linearize(color.red) +
        0.7152 * linearize(color.green) +
        0.0722 * linearize(color.blue)
}
