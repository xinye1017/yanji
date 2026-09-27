package com.example.yanji.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 色彩对比度守卫（WCAG 2.1 相对亮度公式，用 Compose 自带的 `Color.luminance()`）。
 *
 * 存在的理由：本项目的视觉层级几乎全靠明度差表达，而明度差是**手改一个 hex 就会静默失效**的东西。
 * 之前它只由 `build/orca-reports/contrast_audit.py`（不入库、靠人记得跑）覆盖过一次，
 * 结果 49 个配对里 30 个不达标长期无人察觉。这里把**必须达标**的那些配对变成 CI 断言。
 *
 * 只断言「规范强制」的两类，刻意**不**断言装饰性配对，避免将来有人拿 1.4.11 去要求分隔线：
 *  - 1.4.3（≥4.5:1）：承载信息的文字、语义色当作文字/图标前景使用时；
 *  - 1.4.11（≥3.0:1）：表单与控件的边界、控件组的轨道、「未选中」这类必须可辨的状态。
 *
 * 明确豁免、因此不在断言范围内的（改动前请回看本段）：
 *  - 禁用态前景（`YanjiButtons` 的 `disabledContentColor`、`*Label` 里的 quaternary 档）——
 *    WCAG 1.4.3 正文豁免 disabled；[YanjiQuaternaryLabel] / [YanjiDarkQuaternaryLabel]
 *    按定义只服务禁用与装饰，**不得承载信息**；
 *  - 卡片描边 [YanjiBorder]、分隔线 [YanjiDivider] 及暗色 8% / 5% 白版本——纯装饰性分隔，
 *    层级由「表面明度递进」表达（AGENTS.md §三.6），不受 1.4.11 约束；
 *  - 学科序列色的相邻同色系色阶（`SubjectColors.kt`，暗色下限实测 2.2:1）——
 *    它的可读性由**文字标签与图例**承担（见 StatsTrendChart / SubjectDistributionCard 的
 *    per-segment `contentDescription`），因此这里不锁它的阈值，锁了只会逼人把图表改丑。
 */
class YanjiContrastGuardTest {

    // --- 1.4.3：亮色文字三档 × 三种中性落点 ---

    @Test
    fun lightTextTiersPassOnAllNeutralSurfaces() {
        val surfaces = listOf(
            "白卡" to YanjiSurface,
            "页面底" to YanjiBackground,
            "surfaceSoft" to YanjiSurfaceSoft
        )
        val tiers = listOf(
            "primary" to YanjiTextPrimary,
            "secondary" to YanjiTextSecondary,
            "tertiary" to YanjiTextTertiary
        )
        forSurfaceTierPair(surfaces, tiers, floor = 4.5, label = "亮色")
    }

    @Test
    fun darkTextTiersPassOnAllDarkSurfaces() {
        val surfaces = listOf(
            "暗卡" to YanjiDarkSurface,
            "页面底" to YanjiDarkBackground,
            "次级卡" to YanjiDarkSurfaceSoft,
            "浮层" to YanjiDarkSurfaceFloating
        )
        val tiers = listOf(
            "primary" to YanjiDarkTextPrimary,
            "secondary" to YanjiDarkTextSecondary,
            "tertiary" to YanjiDarkTextTertiary
        )
        forSurfaceTierPair(surfaces, tiers, floor = 4.5, label = "暗色")
    }

    @Test
    fun quaternaryTierStaysVisiblyLighterThanTertiary() {
        // 灰阶必须单调：四级若比三级还深，说明有人把「装饰档」误当成了信息档。
        assertTrue(
            "quaternary 必须比 tertiary 更淡（亮色）",
            YanjiQuaternaryLabel.luminance() > YanjiTextTertiary.luminance()
        )
        assertTrue(
            "quaternary 必须比 tertiary 更暗（暗色）",
            YanjiDarkQuaternaryLabel.luminance() < YanjiDarkTextTertiary.luminance()
        )
    }

    // --- 1.4.3：语义色「文字档」，含落在各自 Soft 容器上的真实用法 ---

    @Test
    fun semanticColorsPassAsTextOnNeutralAndOwnSoftSurfaces() {
        val surfaces = listOf(
            "白卡" to YanjiSurface,
            "页面底" to YanjiBackground,
            "surfaceSoft" to YanjiSurfaceSoft
        )
        forSurfaceTierPair(
            surfaces,
            listOf("success" to YanjiSuccess, "warning" to YanjiWarning, "danger" to YanjiDanger),
            floor = 4.5,
            label = "亮色语义色"
        )
        assertTrue(
            "success 文字压 successSoft = ${contrast(YanjiSuccess, YanjiSuccessSoft)}",
            contrast(YanjiSuccess, YanjiSuccessSoft) >= 4.5
        )
        assertTrue(
            "warning 文字压 warningSoft = ${contrast(YanjiWarning, YanjiWarningSoft)}",
            contrast(YanjiWarning, YanjiWarningSoft) >= 4.5
        )
        assertTrue(
            "danger 文字压 dangerSoft = ${contrast(YanjiDanger, YanjiDangerSoft)}",
            contrast(YanjiDanger, YanjiDangerSoft) >= 4.5
        )
    }

    // --- 1.4.11：控件边界 ---

    @Test
    fun controlBoundariesPassNonTextContrast() {
        forSurfaceTierPair(
            listOf("白卡" to YanjiSurface, "页面底" to YanjiBackground, "surfaceSoft" to YanjiSurfaceSoft),
            listOf("fieldBorder" to YanjiFieldBorder),
            floor = 3.0,
            label = "亮色控件边界"
        )
        forSurfaceTierPair(
            listOf("暗卡" to YanjiDarkSurface, "浮层" to YanjiDarkSurfaceFloating),
            listOf("fieldBorder" to YanjiDarkFieldBorder),
            floor = 3.0,
            label = "暗色控件边界"
        )
    }

    @Test
    fun extraColorPaletteMappingsFollowTheTokensTheyClaim() {
        // 调色板是 token 与调用点之间唯一的一层映射；它接错线时，改 token 值不会生效。
        assertTrue(contrast(LightExtraColors.textTertiary, YanjiSurface) >= 4.5)
        assertTrue(contrast(DarkExtraColors.textTertiary, YanjiDarkSurface) >= 4.5)
        assertTrue(contrast(LightExtraColors.success, YanjiSurface) >= 4.5)
        assertTrue(contrast(LightExtraColors.warning, YanjiSurface) >= 4.5)
    }

    // --- 五套吉祥物主题 × 亮暗：主 CTA 的前景 ---

    @Test
    fun primaryButtonForegroundPassesInEveryMascotThemeAndMode() {
        for (spec in MascotThemes.all) {
            val id = spec.id.name
            val light = contrast(YanjiOnPrimary, spec.palette.lightPrimary)
            assertTrue("$id 亮色主按钮白字压主色 ($light) 必须 ≥ 4.5", light >= 4.5)
            val dark = contrast(YanjiDarkOnPrimary, spec.palette.darkPrimary)
            assertTrue("$id 暗色主按钮深墨压主色 ($dark) 必须 ≥ 4.5", dark >= 4.5)
        }
    }

    @Test
    fun primaryAsTextPassesOnCardsInEveryMascotTheme() {
        // 主色当文字用（选中药丸、计时数字）只允许压在**卡片**上。
        // 已知未达标场景：亮色主色压页面底为 4.18–4.82（PENGUIN/SHIBA/CLOUD/FROG 里有三个 <4.5），
        // 页面底上的主色文字应改用 primaryStrong；把这条也纳入断言之前，先不让它静默回退。
        for (spec in MascotThemes.all) {
            val id = spec.id.name
            val onCard = contrast(spec.palette.lightPrimary, YanjiSurface)
            assertTrue("$id 亮色 primary 作文字压白卡 ($onCard) 必须 ≥ 4.5", onCard >= 4.5)
            val strongOnPage = contrast(spec.palette.lightPrimaryStrong, YanjiBackground)
            assertTrue("$id 亮色 primaryStrong 作文字压页面底 ($strongOnPage) 必须 ≥ 4.5", strongOnPage >= 4.5)
            val darkOnCard = contrast(spec.palette.darkPrimary, YanjiDarkSurface)
            assertTrue("$id 暗色 primary 作文字压暗卡 ($darkOnCard) 必须 ≥ 4.5", darkOnCard >= 4.5)
        }
    }

    @Test
    fun bottomBarUnselectedIconStateStaysIdentifiable() {
        // 底栏未选中态 = onSurfaceVariant 以 0.72 alpha 压在页面底上（GlassBottomBar）。
        // alpha 叠底是「设计稿看着行、真机不达标」的头号来源，因此按合成后的实际呈现值断言。
        val composited = blend(YanjiTextSecondary, 0.72f, YanjiBackground)
        assertTrue("亮色底栏未选中图标 (${contrast(composited, YanjiBackground)}) 必须 ≥ 3.0",
            contrast(composited, YanjiBackground) >= 3.0)
        val darkComposited = blend(YanjiDarkTextSecondary, 0.72f, YanjiDarkSurfaceFloating)
        assertTrue("暗色底栏未选中图标 (${contrast(darkComposited, YanjiDarkSurfaceFloating)}) 必须 ≥ 3.0",
            contrast(darkComposited, YanjiDarkSurfaceFloating) >= 3.0)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun forSurfaceTierPair(
        surfaces: List<Pair<String, Color>>,
        tiers: List<Pair<String, Color>>,
        floor: Double,
        label: String
    ) {
        for ((surfaceName, surface) in surfaces) {
            for ((tierName, tier) in tiers) {
                val ratio = contrast(tier, surface)
                assertTrue(
                    "$label $tierName 压 $surfaceName = $ratio，低于门槛 $floor",
                    ratio >= floor
                )
            }
        }
    }

    /** WCAG 2.1 对比度：(L1 + 0.05) / (L2 + 0.05)，亮度取 Compose 的实现。 */
    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** 把带 alpha 的前景色合成到不透明底色上，得到真正呈现的那个颜色。 */
    private fun blend(fg: Color, alpha: Float, bg: Color): Color = Color(
        red = fg.red * alpha + bg.red * (1 - alpha),
        green = fg.green * alpha + bg.green * (1 - alpha),
        blue = fg.blue * alpha + bg.blue * (1 - alpha)
    )
}
