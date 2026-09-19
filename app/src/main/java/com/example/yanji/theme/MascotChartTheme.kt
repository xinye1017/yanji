package com.example.yanji.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 伙伴主题专属的图表与进度条配色规范。
 *
 * 为每个吉祥物伙伴（卷卷、绵绵、冰冰、豆豆、芽芽）定制在浅色与深色模式下的：
 * 1. 考研核心学科语义色彩（数学、408/专业课、英语、政治、其他）；
 * 2. 丰富协调的多学科循环调色序列（用于子类统计或多于5科时的环形图与进度条分段）。
 */
@Immutable
data class MascotChartPalette(
    // --- 浅色模式（Light）核心学科 ---
    val lightMath: Color,
    val lightMajor: Color,
    val lightEnglish: Color,
    val lightPolitics: Color,
    val lightOther: Color,
    val lightSeries: List<Color>,

    // --- 深色模式（Dark）核心学科 ---
    val darkMath: Color,
    val darkMajor: Color,
    val darkEnglish: Color,
    val darkPolitics: Color,
    val darkOther: Color,
    val darkSeries: List<Color>
) {
    /** 获取当前模式下的数学类颜色 */
    fun math(isDark: Boolean): Color = if (isDark) darkMath else lightMath

    /** 获取当前模式下的专业课/408类颜色 */
    fun major(isDark: Boolean): Color = if (isDark) darkMajor else lightMajor

    /** 获取当前模式下的英语类颜色 */
    fun english(isDark: Boolean): Color = if (isDark) darkEnglish else lightEnglish

    /** 获取当前模式下的政治类颜色 */
    fun politics(isDark: Boolean): Color = if (isDark) darkPolitics else lightPolitics

    /** 获取当前模式下的其他/综合类颜色 */
    fun other(isDark: Boolean): Color = if (isDark) darkOther else lightOther

    /** 获取当前模式下的多学科系列循环色谱 */
    fun series(isDark: Boolean): List<Color> = if (isDark) darkSeries else lightSeries
}

// ---------------------------------------------------------------------------
// 1. CLOUD（卷卷 · 沉静蓝 - Serene Azure）
// 调性：理性沉着、深度专注、克莱因蓝与星夜薰衣草的学术心流调
// ---------------------------------------------------------------------------
val CloudChartPalette = MascotChartPalette(
    lightMath = Color(0xFF356AE6),       // 经典克莱因主蓝
    lightMajor = Color(0xFF5356E2),      // 理性靛蓝
    lightEnglish = Color(0xFF7C3AED),    // 优雅薰衣草深紫
    lightPolitics = Color(0xFF0284C7),   // 晴空蔚蓝
    lightOther = Color(0xFF64748B),      // 板岩冷灰
    lightSeries = listOf(
        Color(0xFF356AE6), Color(0xFF5356E2), Color(0xFF7C3AED), Color(0xFF0284C7),
        Color(0xFF2563EB), Color(0xFF9333EA), Color(0xFF0891B2), Color(0xFF64748B)
    ),
    darkMath = Color(0xFF4F7DF3),        // 升调心流蓝
    darkMajor = Color(0xFF818CF8),       // 柔亮紫蓝
    darkEnglish = Color(0xFFA78BFA),     // 星夜轻灵紫
    darkPolitics = Color(0xFF38BDF8),    // 极光天蓝
    darkOther = Color(0xFF94A3B8),       // 雾蓝冷灰
    darkSeries = listOf(
        Color(0xFF4F7DF3), Color(0xFF818CF8), Color(0xFFA78BFA), Color(0xFF38BDF8),
        Color(0xFF60A5FA), Color(0xFFC084FC), Color(0xFF22D3EE), Color(0xFF94A3B8)
    )
)

// ---------------------------------------------------------------------------
// 2. BUNNY（绵绵 · 柔樱粉 - Sakura Blossom）
// 调性：温润治愈、甜而不腻、覆盆子红、香芋紫与珊瑚桃粉的治愈暖调
// ---------------------------------------------------------------------------
val BunnyChartPalette = MascotChartPalette(
    lightMath = Color(0xFFB8426B),       // 绵绵主色，浓郁覆盆子山茶红
    lightMajor = Color(0xFF8B2CBF),      // 暖香芋深紫
    lightEnglish = Color(0xFFC2256E),    // 柔樱甜粉红
    lightPolitics = Color(0xFFC24F29),   // 暖杏珊瑚蜜桃
    lightOther = Color(0xFF885E6E),      // 烟灰豆沙粉紫
    lightSeries = listOf(
        Color(0xFFB8426B), Color(0xFF8B2CBF), Color(0xFFC2256E), Color(0xFFC24F29),
        Color(0xFFA21CAF), Color(0xFFD946EF), Color(0xFFEA580C), Color(0xFF885E6E)
    ),
    darkMath = Color(0xFFE06B96),        // 晶透夜樱粉
    darkMajor = Color(0xFFC77DFF),       // 柔光香芋紫
    darkEnglish = Color(0xFFF472B6),     // 浅亮樱花粉
    darkPolitics = Color(0xFFFB923C),    // 暖阳蜜桃橙
    darkOther = Color(0xFFBA98A5),       // 暮色柔灰粉
    darkSeries = listOf(
        Color(0xFFE06B96), Color(0xFFC77DFF), Color(0xFFF472B6), Color(0xFFFB923C),
        Color(0xFFF493C1), Color(0xFFE879F9), Color(0xFFFDBA74), Color(0xFFBA98A5)
    )
)

// ---------------------------------------------------------------------------
// 3. PENGUIN（冰冰 · 冰川蓝 - Glacial Cyan）
// 调性：极地冰川、浮冰碧海、晶莹剔透的极地冰晶与湖青蓝调
// ---------------------------------------------------------------------------
val PenguinChartPalette = MascotChartPalette(
    lightMath = Color(0xFF2965A8),       // 冰冰主色，深海极地湛蓝
    lightMajor = Color(0xFF0E7490),      // 冰川碧湖青
    lightEnglish = Color(0xFF0F766E),    // 极地松石青
    lightPolitics = Color(0xFF0369A1),   // 苍穹冰蓝
    lightOther = Color(0xFF64748B),      // 寒原冰岩灰
    lightSeries = listOf(
        Color(0xFF2965A8), Color(0xFF0E7490), Color(0xFF0F766E), Color(0xFF0369A1),
        Color(0xFF1D4ED8), Color(0xFF0891B2), Color(0xFF0D9488), Color(0xFF64748B)
    ),
    darkMath = Color(0xFF66A3D2),        // 冰川浮冰蓝
    darkMajor = Color(0xFF22D3EE),       // 荧光冰青
    darkEnglish = Color(0xFF2DD4BF),     // 极光薄荷青
    darkPolitics = Color(0xFF38BDF8),    // 霜天冰青
    darkOther = Color(0xFF94A8B3),       // 极光雾蓝灰
    darkSeries = listOf(
        Color(0xFF66A3D2), Color(0xFF22D3EE), Color(0xFF2DD4BF), Color(0xFF38BDF8),
        Color(0xFF60A5FA), Color(0xFF67E8F9), Color(0xFF5EEAD4), Color(0xFF94A8B3)
    )
)

// ---------------------------------------------------------------------------
// 4. SHIBA（豆豆 · 暖栗橙 - Amber Chestnut）
// 调性：金秋麦穗、暖栗焦糖、柴犬温润治愈的大地与琥珀暖阳调
// ---------------------------------------------------------------------------
val ShibaChartPalette = MascotChartPalette(
    lightMath = Color(0xFFA95822),       // 豆豆主色，焦糖暖栗棕
    lightMajor = Color(0xFF92400E),      // 琥珀枫糖褐
    lightEnglish = Color(0xFFB45309),    // 麦浪秋金黄
    lightPolitics = Color(0xFFC2410C),   // 深秋赤柿红
    lightOther = Color(0xFF765D50),      // 温润陶土暖灰
    lightSeries = listOf(
        Color(0xFFA95822), Color(0xFF92400E), Color(0xFFB45309), Color(0xFFC2410C),
        Color(0xFF78350F), Color(0xFFD97706), Color(0xFFEA580C), Color(0xFF765D50)
    ),
    darkMath = Color(0xFFDE8A49),        // 暖烤金栗
    darkMajor = Color(0xFFD7A154),       // 香槟琥珀金
    darkEnglish = Color(0xFFFBBF24),     // 明亮麦穗金
    darkPolitics = Color(0xFFFB923C),    // 晚霞暖橙
    darkOther = Color(0xFFB0988A),       // 晚秋暖岩灰
    darkSeries = listOf(
        Color(0xFFDE8A49), Color(0xFFD7A154), Color(0xFFFBBF24), Color(0xFFFB923C),
        Color(0xFFF59E0B), Color(0xFFF87171), Color(0xFFFDE047), Color(0xFFB0988A)
    )
)

// ---------------------------------------------------------------------------
// 5. FROG（芽芽 · 新芽绿 - Fresh Sprout）
// 调性：春雨初霁、新竹抽芽、生机盎然与天然护眼的森林草木调
// ---------------------------------------------------------------------------
val FrogChartPalette = MascotChartPalette(
    lightMath = Color(0xFF2F7F55),       // 芽芽主色，苍翠冷杉绿
    lightMajor = Color(0xFF047857),      // 深林青碧
    lightEnglish = Color(0xFF4D7C0F),    // 青草新绿
    lightPolitics = Color(0xFF0F766E),   // 雨林薄荷冷翠
    lightOther = Color(0xFF55685B),      // 苍苔灰绿
    lightSeries = listOf(
        Color(0xFF2F7F55), Color(0xFF047857), Color(0xFF4D7C0F), Color(0xFF0F766E),
        Color(0xFF166534), Color(0xFF15803D), Color(0xFF65A30D), Color(0xFF55685B)
    ),
    darkMath = Color(0xFF5EB37B),        // 春萌翡翠绿
    darkMajor = Color(0xFF34D399),       // 夜光碧玉绿
    darkEnglish = Color(0xFF82BE8B),     // 柔和青草绿
    darkPolitics = Color(0xFF2DD4BF),    // 晨露薄荷青
    darkOther = Color(0xFF95A89B),       // 云雾竹灰
    darkSeries = listOf(
        Color(0xFF5EB37B), Color(0xFF34D399), Color(0xFF82BE8B), Color(0xFF2DD4BF),
        Color(0xFF4ADE80), Color(0xFFA3E635), Color(0xFF6EE7B7), Color(0xFF95A89B)
    )
)

/** 根据伙伴主题获取专属图表调色盘 */
fun mascotChartPaletteOf(id: MascotThemeId): MascotChartPalette = when (id) {
    MascotThemeId.CLOUD -> CloudChartPalette
    MascotThemeId.BUNNY -> BunnyChartPalette
    MascotThemeId.PENGUIN -> PenguinChartPalette
    MascotThemeId.SHIBA -> ShibaChartPalette
    MascotThemeId.FROG -> FrogChartPalette
}

/** 扩展属性：直接从 [MascotThemeSpec] 获取图表调色板 */
val MascotThemeSpec.chartPalette: MascotChartPalette
    get() = mascotChartPaletteOf(id)

/**
 * 智能解析科目列表中每一项在当前主题下的显示色彩。
 *
 * 核心设计规则：
 * 1. 【大类统计】（`isSubcategory == false`）：
 *    优先按考研大类（数学、408/专业课、英语、政治、其他）进行语义色彩映射。
 *    若存在自定义科目或冲突，则用当前主题的循环色谱 [series] 保证每一项颜色清晰可辨、互不重复。
 * 2. 【子类统计】（`isSubcategory == true`）：
 *    由于子类常常包含多个同一学科项（例如高等数学、线性代数、概率论），若全用同一种颜色会导致饼图扇环
 *    连成一片无法区分。因此采用精心编排的系列色谱 [series] 进行分阶分配，使环形图与进度条丰富悦目。
 * 3. 【单科展示】：
 *    若是单科，直接赋予该科目在该主题下的专属语义色（或主色），让 100% 进度条充满沉浸感。
 */
fun resolveSubjectChartColors(
    items: List<Pair<String, String>>, // list of Pair(subjectId, subjectName)
    palette: MascotChartPalette,
    isDark: Boolean,
    isSubcategory: Boolean
): Map<String, Color> {
    if (items.isEmpty()) return emptyMap()

    val series = palette.series(isDark)

    if (items.size == 1) {
        val (id, name) = items.first()
        val color = matchSemanticColor(id, name, palette, isDark) ?: series.first()
        return mapOf(name to color)
    }

    val result = mutableMapOf<String, Color>()

    if (isSubcategory || items.size > 5) {
        // 子类或多于5个科目：直接按系列循环色分配，确保相邻扇区不撞色且与主题风格一致
        items.forEachIndexed { index, (_, name) ->
            result[name] = series[index % series.size]
        }
    } else {
        // 2~5 个大类科目：语义优先，重复/未匹配项从 series 顺序补充未被使用的颜色
        val usedColors = mutableSetOf<Color>()

        // 第一轮：语义匹配
        val unassigned = mutableListOf<Pair<Int, String>>()
        items.forEachIndexed { index, (id, name) ->
            val semanticColor = matchSemanticColor(id, name, palette, isDark)
            if (semanticColor != null && semanticColor !in usedColors) {
                result[name] = semanticColor
                usedColors.add(semanticColor)
            } else {
                unassigned.add(index to name)
            }
        }

        // 第二轮：为未匹配或冲突项分配未使用的 series 色
        var seriesIndex = 0
        for ((_, name) in unassigned) {
            var colorCandidate = series[seriesIndex % series.size]
            while (colorCandidate in usedColors && seriesIndex < series.size * 2) {
                seriesIndex++
                colorCandidate = series[seriesIndex % series.size]
            }
            result[name] = colorCandidate
            usedColors.add(colorCandidate)
            seriesIndex++
        }
    }

    return result
}

/** 按考研核心大类匹配语义颜色 */
private fun matchSemanticColor(
    subjectId: String,
    subjectName: String,
    palette: MascotChartPalette,
    isDark: Boolean
): Color? = when {
    subjectId.startsWith("math") ||
    subjectName.contains("数学") ||
    subjectName.contains("线性代数") ||
    subjectName.contains("概率论") -> palette.math(isDark)

    subjectId.startsWith("major") ||
    subjectName.contains("408") ||
    subjectName.contains("专业课") ||
    subjectName.contains("数据结构") ||
    subjectName.contains("计算机组成") ||
    subjectName.contains("计算机网络") ||
    subjectName.contains("操作系统") -> palette.major(isDark)

    subjectId.startsWith("english") ||
    subjectName.contains("英语") -> palette.english(isDark)

    subjectId.startsWith("politics") ||
    subjectName.contains("政治") -> palette.politics(isDark)

    subjectId.startsWith("other") ||
    subjectName.contains("其他") -> palette.other(isDark)

    else -> null
}

/**
 * 在 Compose 环境中便捷获取单个学科在当前主题下的显示色。
 */
@Composable
@ReadOnlyComposable
fun subjectChartColor(
    name: String,
    id: String = ""
): Color {
    val mascot = LocalMascotTheme.current
    val isDark = LocalYanjiDarkTheme.current
    val palette = mascot.chartPalette
    return matchSemanticColor(id, name, palette, isDark)
        ?: palette.series(isDark).first()
}
