package com.example.yanji.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 吉祥物伙伴主题专属的学科与图表配色方案。
 *
 * ## 设计原则
 * 1. **同色系与品牌统一**：每个伙伴主题拥有由 OKLCH 空间计算、高视觉协调感的 5 个色阶；
 * 2. **两两与相邻高区分度**：同模式下相邻两色 CIEDE2000 ΔE >= 18，任意两色 ΔE >= 12；
 * 3. **文字与卡片高可读性**：浅色模式下对浅色底对比度 >= 3.0，深色模式下对深色底对比度 >= 2.4；
 * 4. **学科槽位固定映射**：通过 `subjectColorSlot` 将学科 ID 映射到固定的色阶槽位，
 *    避免因列表重排、时长过滤导致同一学科在同一主题下跳变颜色。
 */
@Immutable
data class MascotSubjectPalette(
    /** 浅色模式下的 5 个有序色阶（Slot 0..4） */
    val lightSteps: List<Color>,
    /** 深色模式下的 5 个有序色阶（Slot 0..4） */
    val darkSteps: List<Color>
) {
    fun steps(isDark: Boolean): List<Color> = if (isDark) darkSteps else lightSteps

    /**
     * 按槽位索引安全取色（支持循环兜底与负数安全）
     */
    fun colorAt(slot: Int, isDark: Boolean): Color {
        val s = steps(isDark)
        val index = ((slot % s.size) + s.size) % s.size
        return s[index]
    }
}

// ---------------------------------------------------------------------------
// 1. CLOUD（卷卷 · 沉静蓝 - Serene Azure）
// 调性：理性沉着、深度专注的学术心流。
// Slot 0: 数学 (深海克莱因蓝)
// Slot 1: 408 (星夜薰衣草紫)
// Slot 2: 英语 (湖水湛蓝)
// Slot 3: 政治 (幽夜紫电)
// Slot 4: 其他 (晴空亮蓝)
// ---------------------------------------------------------------------------
val CloudSubjectPalette = MascotSubjectPalette(
    lightSteps = listOf(
        Color(0xFF0A4980), // 深海克莱因蓝 (Slot 0 - Math)
        Color(0xFF7756F7), // 星夜薰衣草紫 (Slot 1 - Major)
        Color(0xFF1265C9), // 湖水湛蓝 (Slot 2 - English)
        Color(0xFF4D1BE2), // 幽夜紫电 (Slot 3 - Politics)
        Color(0xFF5F82F8)  // 晴空亮蓝 (Slot 4 - Other)
    ),
    darkSteps = listOf(
        Color(0xFF2E86F8), // 升调心流蓝 (Slot 0 - Math)
        Color(0xFF846FF8), // 柔光紫罗兰 (Slot 1 - Major)
        Color(0xFF46ACFA), // 极光天蓝 (Slot 2 - English)
        Color(0xFFCCD1FD), // 月光雾蓝白 (Slot 3 - Politics)
        Color(0xFFB1DAFD)  // 霜天淡蓝 (Slot 4 - Other)
    )
)

// ---------------------------------------------------------------------------
// 2. BUNNY（绵绵 · 柔樱粉 - Sakura Blossom）
// 调性：温润治愈、甜而不腻的轻盈花瓣。
// Slot 0: 数学 (浓郁覆盆子红)
// Slot 1: 408 (深紫罗兰)
// Slot 2: 英语 (柔樱甜粉红)
// Slot 3: 政治 (深玫红)
// Slot 4: 其他 (亮香芋紫)
// ---------------------------------------------------------------------------
val BunnySubjectPalette = MascotSubjectPalette(
    lightSteps = listOf(
        Color(0xFFC41A6A), // 浓郁覆盆子红 (Slot 0 - Math)
        Color(0xFF7E13A2), // 深紫罗兰 (Slot 1 - Major)
        Color(0xFFF925AB), // 柔樱甜粉红 (Slot 2 - English)
        Color(0xFF880F5B), // 深玫红 (Slot 3 - Politics)
        Color(0xFFC532F8)  // 亮香芋紫 (Slot 4 - Other)
    ),
    darkSteps = listOf(
        Color(0xFFEB2180), // 夜樱玫红 (Slot 0 - Math)
        Color(0xFFE062FA), // 浅亮紫罗兰 (Slot 1 - Major)
        Color(0xFFFDCBDD), // 初雪樱粉 (Slot 2 - English)
        Color(0xFFDD21BB), // 荧光品红 (Slot 3 - Politics)
        Color(0xFFE4B0FC)  // 浅亮香芋紫 (Slot 4 - Other)
    )
)

// ---------------------------------------------------------------------------
// 3. PENGUIN（冰冰 · 冰川蓝 - Glacial Cyan）
// 调性：极地冰川、晶莹剔透。
// Slot 0: 数学 (深海极地湛蓝)
// Slot 1: 408 (幽深冰湖青)
// Slot 2: 英语 (冰川浮冰蓝)
// Slot 3: 政治 (冰川碧湖青)
// Slot 4: 其他 (极夜深蓝)
// ---------------------------------------------------------------------------
val PenguinSubjectPalette = MascotSubjectPalette(
    lightSteps = listOf(
        Color(0xFF1264B0), // 深海极地湛蓝 (Slot 0 - Math)
        Color(0xFF0D5558), // 幽深冰湖青 (Slot 1 - Major)
        Color(0xFF1D8FCC), // 冰川浮冰蓝 (Slot 2 - English)
        Color(0xFF1C8E93), // 冰川碧湖青 (Slot 3 - Politics)
        Color(0xFF094768)  // 极夜深蓝 (Slot 4 - Other)
    ),
    darkSteps = listOf(
        Color(0xFF1D8EF5), // 霜天湛蓝 (Slot 0 - Math)
        Color(0xFF26B3BA), // 荧光冰青 (Slot 1 - Major)
        Color(0xFFA6D4FC), // 浮冰淡蓝 (Slot 2 - English)
        Color(0xFF5FE2FC), // 极光冰青 (Slot 3 - Politics)
        Color(0xFF1B88A2)  // 幽谷冰湖 (Slot 4 - Other)
    )
)

// ---------------------------------------------------------------------------
// 4. SHIBA（豆豆 · 暖栗橙 - Amber Chestnut）
// 调性：金秋麦穗、暖栗焦糖。
// Slot 0: 数学 (焦糖暖栗棕)
// Slot 1: 408 (深焙琥珀褐)
// Slot 2: 英语 (暖阳麦橙)
// Slot 3: 政治 (赤柿正红)
// Slot 4: 其他 (枫叶深红)
// ---------------------------------------------------------------------------
val ShibaSubjectPalette = MascotSubjectPalette(
    lightSteps = listOf(
        Color(0xFFA55713), // 焦糖暖栗棕 (Slot 0 - Math)
        Color(0xFF79380A), // 深焙琥珀褐 (Slot 1 - Major)
        Color(0xFFE06D1D), // 暖阳麦橙 (Slot 2 - English)
        Color(0xFFFA4B40), // 赤柿正红 (Slot 3 - Politics)
        Color(0xFFCC1B1B)  // 枫叶深红 (Slot 4 - Other)
    ),
    darkSteps = listOf(
        Color(0xFFDF5019), // 晚霞赤橙 (Slot 0 - Math)
        Color(0xFFBB731A), // 琥珀麦金 (Slot 1 - Major)
        Color(0xFFFCB587), // 暖阳浅杏 (Slot 2 - English)
        Color(0xFFFA7062), // 暖珊瑚 (Slot 3 - Politics)
        Color(0xFFFDCEC7)  // 初雪暖粉 (Slot 4 - Other)
    )
)

// ---------------------------------------------------------------------------
// 5. FROG（芽芽 · 新芽绿 - Fresh Sprout）
// 调性：春雨初霁、生机盎然。
// Slot 0: 数学 (苍翠冷杉绿)
// Slot 1: 408 (深林青碧)
// Slot 2: 英语 (青草新绿)
// Slot 3: 政治 (晨露薄荷青)
// Slot 4: 其他 (深林墨绿)
// ---------------------------------------------------------------------------
val FrogSubjectPalette = MascotSubjectPalette(
    lightSteps = listOf(
        Color(0xFF1E9B5C), // 苍翠冷杉绿 (Slot 0 - Math)
        Color(0xFF0E5E46), // 深林青碧 (Slot 1 - Major)
        Color(0xFF4BA41C), // 青草新绿 (Slot 2 - English)
        Color(0xFF1B8D7A), // 晨露薄荷青 (Slot 3 - Politics)
        Color(0xFF0C5711)  // 深林墨绿 (Slot 4 - Other)
    ),
    darkSteps = listOf(
        Color(0xFF25B589), // 夜光薄荷 (Slot 0 - Math)
        Color(0xFF1D9947), // 幽谷翠绿 (Slot 1 - Major)
        Color(0xFF76BB23), // 明亮青草绿 (Slot 2 - English)
        Color(0xFF36F7BC), // 晨光薄荷青 (Slot 3 - Politics)
        Color(0xFF75F830)  // 荧光嫩芽绿 (Slot 4 - Other)
    )
)

/** 根据伙伴主题获取专属学科调色板 */
fun mascotSubjectPaletteOf(id: MascotThemeId): MascotSubjectPalette = when (id) {
    MascotThemeId.CLOUD -> CloudSubjectPalette
    MascotThemeId.BUNNY -> BunnySubjectPalette
    MascotThemeId.PENGUIN -> PenguinSubjectPalette
    MascotThemeId.SHIBA -> ShibaSubjectPalette
    MascotThemeId.FROG -> FrogSubjectPalette
}

/** 扩展属性：从 [MascotThemeSpec] 便捷读取学科调色板 */
val MascotThemeSpec.subjectPalette: MascotSubjectPalette
    get() = mascotSubjectPaletteOf(id)
