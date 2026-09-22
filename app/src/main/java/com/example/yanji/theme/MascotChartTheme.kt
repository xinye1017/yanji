package com.example.yanji.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 伙伴主题专属的图表与进度条配色规范。
 *
 * ## 设计原则（重要）
 *
 * 学科大类与子类都可由用户**自由增删改名**，因此「颜色 ↔ 学科」之间**不能有任何语义绑定**：
 * 不存在「数学色」「英语色」这种东西——用户新增一个「量子力学」也必须拿到一个清晰可辨的颜色。
 *
 * 所以每套主题只预设 **5 个同色系、彼此高区分度的色阶**，渲染时**按学科在当前列表中的顺序
 * 依次分配**（第 i 个学科取 `steps[i]`）。这让颜色与学科完全解耦：
 * 无论用户如何自定义学科，前 5 个一定拿到 5 个互不相同的颜色。
 *
 * ## 色阶是怎么来的
 *
 * 每套主题的 5 色由 OKLCH 空间求解，并满足以下硬约束：
 * 1. **同色系**：5 色色相都落在该主题的色相弧内（如芽芽的绿→薄荷青），整体读起来是一套；
 * 2. **相邻可分**：按顺序相邻两色的 CIEDE2000 ΔE ≥ 18（实测 22~45），环形图相邻扇区、
 *    进度条并列时一眼能分辨；
 * 3. **两两可分**：任意两色 ΔE ≥ 12（实测 12.3~16.8），保证图例中不出现「看起来一样」的两项；
 * 4. **可读**：浅色模式 5 色对白色卡片对比度 ≥ 3.0，深色模式对深色卡片 ≥ 2.4。
 *
 * 色阶刻意做成**明度交错**（如 中-亮-暗-亮-暗）而非单调明暗序列：单调序列的相邻明度差很小，
 * 而交错排列能让相邻索引在明度与色相两个维度同时错开，ΔE 显著更大。
 */
@Immutable
data class MascotChartPalette(
    /** 浅色模式下的 5 个有序色阶，按学科顺序分配 */
    val lightSteps: List<Color>,
    /** 深色模式下的 5 个有序色阶，按学科顺序分配 */
    val darkSteps: List<Color>
) {
    /** 获取当前模式下的有序色阶 */
    fun steps(isDark: Boolean): List<Color> = if (isDark) darkSteps else lightSteps

    /** 按学科顺序索引取色（索引超出 5 时循环，保证任何自定义科目数量都有色可用） */
    fun colorAt(index: Int, isDark: Boolean): Color {
        val s = steps(isDark)
        return s[((index % s.size) + s.size) % s.size]
    }
}

// ---------------------------------------------------------------------------
// 1. CLOUD（卷卷 · 沉静蓝 - Serene Azure）
// 调性：理性沉着、深度专注的学术心流。色相弧 244°~286°（克莱因蓝 → 星夜薰衣草）
// ---------------------------------------------------------------------------
val CloudChartPalette = MascotChartPalette(
    lightSteps = listOf(
        Color(0xFF7756F7), // 星夜薰衣草紫
        Color(0xFF0A4980), // 深海克莱因蓝
        Color(0xFF5F82F8), // 晴空亮蓝
        Color(0xFF4D1BE2), // 幽夜紫电
        Color(0xFF1265C9)  // 湖水湛蓝
    ),
    darkSteps = listOf(
        Color(0xFF46ACFA), // 极光天蓝
        Color(0xFF846FF8), // 柔光紫罗兰
        Color(0xFFCCD1FD), // 月光雾蓝白
        Color(0xFF2E86F8), // 升调心流蓝
        Color(0xFFB1DAFD)  // 霜天淡蓝
    )
)

// ---------------------------------------------------------------------------
// 2. BUNNY（绵绵 · 柔樱粉 - Sakura Blossom）
// 调性：温润治愈、甜而不腻。色相弧 315°~360°（香芋紫 → 覆盆子红 → 樱粉）
// ---------------------------------------------------------------------------
val BunnyChartPalette = MascotChartPalette(
    lightSteps = listOf(
        Color(0xFFC41A6A), // 浓郁覆盆子红
        Color(0xFFC532F8), // 亮香芋紫
        Color(0xFF880F5B), // 深玫红
        Color(0xFFF925AB), // 柔樱甜粉红
        Color(0xFF7E13A2)  // 深紫罗兰
    ),
    darkSteps = listOf(
        Color(0xFFEB2180), // 夜樱玫红
        Color(0xFFE4B0FC), // 浅亮香芋紫
        Color(0xFFDD21BB), // 荧光品红
        Color(0xFFFDCBDD), // 初雪樱粉
        Color(0xFFE062FA)  // 浅亮紫罗兰
    )
)

// ---------------------------------------------------------------------------
// 3. PENGUIN（冰冰 · 冰川蓝 - Glacial Cyan）
// 调性：极地冰川、晶莹剔透。色相弧 199°~252°（极地松石青 → 深海湛蓝）
// ---------------------------------------------------------------------------
val PenguinChartPalette = MascotChartPalette(
    lightSteps = listOf(
        Color(0xFF1264B0), // 深海极地湛蓝
        Color(0xFF1C8E93), // 冰川碧湖青
        Color(0xFF094768), // 极夜深蓝
        Color(0xFF1D8FCC), // 冰川浮冰蓝
        Color(0xFF0D5558)  // 幽深冰湖青
    ),
    darkSteps = listOf(
        Color(0xFF26B3BA), // 荧光冰青
        Color(0xFF1D8EF5), // 霜天湛蓝
        Color(0xFF5FE2FC), // 极光冰青
        Color(0xFF1B88A2), // 幽谷冰湖
        Color(0xFFA6D4FC)  // 浮冰淡蓝
    )
)

// ---------------------------------------------------------------------------
// 4. SHIBA（豆豆 · 暖栗橙 - Amber Chestnut）
// 调性：金秋麦穗、暖栗焦糖。色相弧 28°~65°（深秋赤柿红 → 琥珀暖橙）
// ---------------------------------------------------------------------------
val ShibaChartPalette = MascotChartPalette(
    lightSteps = listOf(
        Color(0xFFA55713), // 焦糖暖栗棕
        Color(0xFFFA4B40), // 赤柿正红
        Color(0xFF79380A), // 深焙琥珀褐
        Color(0xFFE06D1D), // 暖阳麦橙
        Color(0xFFCC1B1B)  // 枫叶深红
    ),
    darkSteps = listOf(
        Color(0xFFFCB587), // 暖阳浅杏
        Color(0xFFDF5019), // 晚霞赤橙
        Color(0xFFFDCEC7), // 初雪暖粉
        Color(0xFFBB731A), // 琥珀麦金
        Color(0xFFFA7062)  // 暖珊瑚
    )
)

// ---------------------------------------------------------------------------
// 5. FROG（芽芽 · 新芽绿 - Fresh Sprout）
// 调性：春雨初霁、生机盎然。色相弧 132°~178°（森林苍翠绿 → 薄荷青）
// ---------------------------------------------------------------------------
val FrogChartPalette = MascotChartPalette(
    lightSteps = listOf(
        Color(0xFF4BA41C), // 青草新绿
        Color(0xFF0E5E46), // 深林青碧
        Color(0xFF1E9B5C), // 苍翠冷杉绿
        Color(0xFF0C5711), // 深林墨绿
        Color(0xFF1B8D7A)  // 晨露薄荷青
    ),
    darkSteps = listOf(
        Color(0xFF25B589), // 夜光薄荷
        Color(0xFF75F830), // 荧光嫩芽绿
        Color(0xFF1D9947), // 幽谷翠绿
        Color(0xFF36F7BC), // 晨光薄荷青
        Color(0xFF76BB23)  // 明亮青草绿
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

