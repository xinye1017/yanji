package com.example.yanji.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.yanji.R

/**
 * 研迹的“学习伙伴主题”。
 *
 * 它与 [YanjiThemeMode] 正交：前者决定品牌/吉祥物，后者决定浅色/深色。
 * 持久化始终保存 [name]，未知值安全回落到 [CLOUD]。
 */
enum class MascotThemeId {
    CLOUD,
    BUNNY,
    PENGUIN,
    SHIBA,
    FROG;

    companion object {
        val DEFAULT = CLOUD

        fun fromStorage(raw: String?): MascotThemeId =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

@Immutable
data class MascotPalette(
    val lightPrimary: Color,
    val lightPrimaryStrong: Color,
    val lightPrimarySoft: Color,
    val lightSecondary: Color,
    val lightSecondaryStrong: Color,
    val lightSecondarySoft: Color,
    val lightSurfaceBlue: Color,
    val darkPrimary: Color,
    val darkPrimaryStrong: Color,
    val darkPrimarySoft: Color,
    val darkSecondary: Color,
    val darkSecondaryStrong: Color,
    val darkSecondarySoft: Color,
    val darkSurfaceBlue: Color
)

@Immutable
data class MascotThemeSpec(
    val id: MascotThemeId,
    val name: String,
    val themeName: String,
    @DrawableRes val drawableRes: Int,
    val palette: MascotPalette
)

object MascotThemes {
    val CLOUD = MascotThemeSpec(
        id = MascotThemeId.CLOUD,
        name = "卷卷",
        themeName = "沉静蓝",
        drawableRes = R.drawable.juanjuan,
        palette = MascotPalette(
            lightPrimary = YanjiPrimary,
            lightPrimaryStrong = YanjiPrimaryStrong,
            lightPrimarySoft = YanjiPrimarySoft,
            lightSecondary = YanjiLavender,
            lightSecondaryStrong = YanjiLavenderDeep,
            lightSecondarySoft = YanjiLavenderSoft,
            lightSurfaceBlue = YanjiSurfaceBlue,
            darkPrimary = YanjiDarkPrimary,
            darkPrimaryStrong = YanjiDarkPrimaryStrong,
            darkPrimarySoft = YanjiDarkPrimarySoft,
            darkSecondary = YanjiDarkLavender,
            darkSecondaryStrong = YanjiDarkLavenderDeep,
            darkSecondarySoft = YanjiDarkLavenderSoft,
            darkSurfaceBlue = YanjiDarkSurfaceBlue
        )
    )

    val BUNNY = MascotThemeSpec(
        id = MascotThemeId.BUNNY,
        name = "绵绵",
        themeName = "柔樱粉",
        drawableRes = R.drawable.mianmian,
        palette = MascotPalette(
            lightPrimary = Color(0xFFB8426B),
            lightPrimaryStrong = Color(0xFF8E3150),
            lightPrimarySoft = Color(0xFFFFE8F0),
            lightSecondary = Color(0xFFC46FA8),
            lightSecondaryStrong = Color(0xFF8C4D78),
            lightSecondarySoft = Color(0xFFFFEDF7),
            lightSurfaceBlue = Color(0xFFFFF1F6),
            darkPrimary = Color(0xFFE06B96),
            darkPrimaryStrong = Color(0xFFF39AB9),
            darkPrimarySoft = Color(0x29E06B96),
            darkSecondary = Color(0xFFD995C1),
            darkSecondaryStrong = Color(0xFFF0B7D8),
            darkSecondarySoft = Color(0x29D995C1),
            darkSurfaceBlue = Color(0xFF251821)
        )
    )

    val PENGUIN = MascotThemeSpec(
        id = MascotThemeId.PENGUIN,
        name = "冰冰",
        themeName = "冰川蓝",
        drawableRes = R.drawable.bingbing,
        palette = MascotPalette(
            lightPrimary = Color(0xFF3B78B8),
            lightPrimaryStrong = Color(0xFF285A8D),
            lightPrimarySoft = Color(0xFFE6F2FB),
            lightSecondary = Color(0xFF4E9FBF),
            lightSecondaryStrong = Color(0xFF31728C),
            lightSecondarySoft = Color(0xFFE8F7FB),
            lightSurfaceBlue = Color(0xFFEDF7FC),
            darkPrimary = Color(0xFF66A3D2),
            darkPrimaryStrong = Color(0xFF8EC3E6),
            darkPrimarySoft = Color(0x2966A3D2),
            darkSecondary = Color(0xFF68B5CC),
            darkSecondaryStrong = Color(0xFF96D3E2),
            darkSecondarySoft = Color(0x2968B5CC),
            darkSurfaceBlue = Color(0xFF15232C)
        )
    )

    val SHIBA = MascotThemeSpec(
        id = MascotThemeId.SHIBA,
        name = "豆豆",
        themeName = "暖栗橙",
        drawableRes = R.drawable.doudou,
        palette = MascotPalette(
            lightPrimary = Color(0xFFA95822),
            lightPrimaryStrong = Color(0xFF7E3E16),
            lightPrimarySoft = Color(0xFFFFEBD9),
            lightSecondary = Color(0xFFC58A3B),
            lightSecondaryStrong = Color(0xFF8F5E1E),
            lightSecondarySoft = Color(0xFFFFF0DC),
            lightSurfaceBlue = Color(0xFFFFF2E6),
            darkPrimary = Color(0xFFDE8A49),
            darkPrimaryStrong = Color(0xFFF2AE72),
            darkPrimarySoft = Color(0x29DE8A49),
            darkSecondary = Color(0xFFD7A154),
            darkSecondaryStrong = Color(0xFFEEC17C),
            darkSecondarySoft = Color(0x29D7A154),
            darkSurfaceBlue = Color(0xFF261B13)
        )
    )

    val FROG = MascotThemeSpec(
        id = MascotThemeId.FROG,
        name = "芽芽",
        themeName = "新芽绿",
        drawableRes = R.drawable.yaya,
        palette = MascotPalette(
            lightPrimary = Color(0xFF2F7F55),
            lightPrimaryStrong = Color(0xFF225E3F),
            lightPrimarySoft = Color(0xFFE5F5EB),
            lightSecondary = Color(0xFF65A36F),
            lightSecondaryStrong = Color(0xFF477A50),
            lightSecondarySoft = Color(0xFFECF8EE),
            lightSurfaceBlue = Color(0xFFEDF8F0),
            darkPrimary = Color(0xFF5EB37B),
            darkPrimaryStrong = Color(0xFF86D29E),
            darkPrimarySoft = Color(0x295EB37B),
            darkSecondary = Color(0xFF82BE8B),
            darkSecondaryStrong = Color(0xFFA8D6AE),
            darkSecondarySoft = Color(0x2982BE8B),
            darkSurfaceBlue = Color(0xFF17251B)
        )
    )

    val all: List<MascotThemeSpec> = listOf(CLOUD, BUNNY, PENGUIN, SHIBA, FROG)

    fun spec(id: MascotThemeId): MascotThemeSpec = when (id) {
        MascotThemeId.CLOUD -> CLOUD
        MascotThemeId.BUNNY -> BUNNY
        MascotThemeId.PENGUIN -> PENGUIN
        MascotThemeId.SHIBA -> SHIBA
        MascotThemeId.FROG -> FROG
    }

    fun fromStorage(raw: String?): MascotThemeSpec = spec(MascotThemeId.fromStorage(raw))
}

val LocalMascotTheme = staticCompositionLocalOf { MascotThemes.CLOUD }

@ReadOnlyComposable
@androidx.compose.runtime.Composable
fun currentMascotTheme(): MascotThemeSpec = LocalMascotTheme.current

