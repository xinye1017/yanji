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
 * 根据学科 ID 或其分类推导稳定的色阶槽位（0..4）。
 *
 * 核心原则：
 * - 标准 5 大类严格对应固定槽位（数学 -> 0, 专业课 -> 1, 英语 -> 2, 政治 -> 3, 其他 -> 4）；
 * - 子学科（如高等数学、数据结构）直接继承所属大类的槽位；
 * - 自定义科目按其分类在目录中的顺序循环分配（catIndex % 5）；
 * - 未知或临时科目按 ID 哈希稳定映射。
 *
 * 无论列表如何重排、按时长降序或过滤，同一学科计算出的槽位永不改变。
 */
fun subjectColorSlot(subjectId: String): Int {
    val cleanId = subjectId.removeSuffix(SubjectCatalog.UNCLASSIFIED_SUFFIX)
    val categoryId = SubjectCatalog.categoryIdOf(cleanId)

    val standardSlot = when (categoryId) {
        "math" -> 0
        "major" -> 1
        "english" -> 2
        "politics" -> 3
        "other" -> 4
        else -> null
    }
    if (standardSlot != null) return standardSlot

    val categories = SubjectCatalog.categories
    val catIndex = categories.indexOfFirst { it.id == categoryId }
    if (catIndex >= 0) {
        return catIndex % 5
    }

    return (cleanId.hashCode() and 0x7FFFFFFF) % 5
}

/**
 * 非 Composable 取色方法：按学科槽位从对应伙伴主题中获取专属色阶。
 */
fun yanjiSubjectColor(
    subjectId: String,
    mascotTheme: MascotThemeSpec,
    isDark: Boolean
): Color {
    val slot = subjectColorSlot(subjectId)
    return mascotTheme.subjectPalette.colorAt(slot, isDark)
}

/**
 * Composable 语法糖：读取当前 [currentMascotTheme] 与 [LocalYanjiDarkTheme] 取色。
 */
@Composable
@ReadOnlyComposable
fun yanjiSubjectColor(subjectId: String): Color {
    return yanjiSubjectColor(
        subjectId = subjectId,
        mascotTheme = currentMascotTheme(),
        isDark = LocalYanjiDarkTheme.current
    )
}

/**
 * 按学科展示名称取色。
 */
@Composable
@ReadOnlyComposable
fun yanjiSubjectColorOf(subjectName: String): Color {
    val subjectId = SubjectCatalog.idForDisplayName(subjectName)
    return if (subjectId != null) {
        yanjiSubjectColor(subjectId)
    } else {
        val mascotTheme = currentMascotTheme()
        val isDark = LocalYanjiDarkTheme.current
        val slot = (subjectName.hashCode() and 0x7FFFFFFF) % 5
        mascotTheme.subjectPalette.colorAt(slot, isDark)
    }
}

/**
 * 纯颜色十六进制解析与对比度保底校正（无破坏性 RGB 混色）。
 * 用于自定义十六进制输入或测试场景。
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
