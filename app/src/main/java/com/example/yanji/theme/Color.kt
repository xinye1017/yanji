package com.example.yanji.theme

import androidx.compose.ui.graphics.Color

// Yanji Rounded Blue Color Palette
val YanjiPrimary = Color(0xFF356AE6)
val YanjiPrimaryStrong = Color(0xFF2453BF)
val YanjiPrimarySoft = Color(0xFFEAF1FF)
val YanjiOnPrimary = Color(0xFFFFFFFF)

val YanjiBackground = Color(0xFFF7F9FC)
val YanjiSurface = Color(0xFFFFFFFF)
val YanjiSurfaceSoft = Color(0xFFF1F5FB)
val YanjiSurfaceBlue = Color(0xFFF4F7FF)

val YanjiTextPrimary = Color(0xFF172033)
val YanjiTextSecondary = Color(0xFF667085)
val YanjiTextTertiary = Color(0xFF98A2B3)

val YanjiBorder = Color(0xFFE4EAF2)
val YanjiDivider = Color(0xFFEDF1F6)

val YanjiLavender = Color(0xFF8B7CF6)
val YanjiLavenderSoft = Color(0xFFF0EDFF)

/**
 * 卷卷 AI「深度解析」Brand Tone。
 *
 * 比 [YanjiLavender] 更深、饱和度更高，专用于 AI 深度解析场景的**文字与图标前景**：
 * 浅紫容器 `YanjiLavenderSoft` 需要与之配对以达到正文对比度要求，
 * 而 `YanjiLavender` 铺在浅紫上对比不足。
 *
 * 该色只在 Chat 的品牌化区域（深度解析徽章、思考链提示、行动按钮）出现，
 * 不参与主 CTA，也不得替换 [YanjiPrimary]。
 */
val YanjiLavenderDeep = Color(0xFF5C4BC3)

/**
 * 薰衣草系柔和容器阶 —— 与 [YanjiLavenderSoft] 同色相、更浅的一档。
 *
 * 用于「需要浅紫底但不能再吃满 lavender-soft」的场景：
 * 成就徽章的径向渐变收尾色，以及英语分类的图标底色。
 */
val YanjiLavenderSoftDeep = Color(0xFFF2F0FF)

/**
 * 主蓝系柔和容器阶 —— [YanjiPrimarySoft] 之外更浅的一档，
 * 仅作渐变收尾色使用，避免在 Screen 内联硬编码渐变端点。
 */
val YanjiPrimaryGradientSoft = Color(0xFFE8EEFF)

val YanjiSuccess = Color(0xFF2F9E6D)
val YanjiSuccessSoft = Color(0xFFE8F7F0)

val YanjiWarning = Color(0xFFD99024)
val YanjiWarningSoft = Color(0xFFFFF5E3)

val YanjiDanger = Color(0xFFD94B4B)
val YanjiDangerSoft = Color(0xFFFDECEC)
val YanjiError = YanjiDanger
val YanjiBorderSoft = Color(0xFFF2F4F7)

// Subject Colors (DESIGN.md chart series)
val SubjectMath = Color(0xFF356AE6)       // Mathematics 数学 - 主蓝
val SubjectMajor = Color(0xFF6F91EA)      // Major/408 专业课 - 亮蓝
val SubjectEnglish = Color(0xFF8B7CF6)    // English 英语 - 薰衣草紫
val SubjectPolitics = Color(0xFF7CB6D9)   // Politics 政治 - 天蓝
val SubjectOther = Color(0xFFB8C6DF)      // Other 其他 - 雾蓝

// Subject Soft —— 与上列分类色配对的极浅容器，用于图标底 / 分类 chip 背景。
// 与分类色一一对应，不要在 Screen 内联推导。
val SubjectMathSoft = YanjiPrimarySoft    // #EAF1FF
val SubjectMajorSoft = Color(0xFFEEF3FF)
val SubjectEnglishSoft = YanjiLavenderSoftDeep
val SubjectPoliticsSoft = Color(0xFFEDF6FA)
val SubjectOtherSoft = YanjiSurfaceSoft   // #F1F5FB

// --- Yanji Dark Slate Color Palette (Calm & Soft, not pure black #000000) ---
val YanjiDarkBackground = Color(0xFF0F141C)
val YanjiDarkSurface = Color(0xFF192231)
val YanjiDarkSurfaceSoft = Color(0xFF222C3D)
val YanjiDarkSurfaceBlue = Color(0xFF1C273C)

val YanjiDarkTextPrimary = Color(0xFFF0F4F8)
val YanjiDarkTextSecondary = Color(0xFFA0ABC0)
val YanjiDarkTextTertiary = Color(0xFF687890)

val YanjiDarkBorder = Color(0xFF273448)
val YanjiDarkDivider = Color(0xFF1E2838)
val YanjiDarkBorderSoft = Color(0xFF202B3B)

val YanjiDarkPrimary = Color(0xFF5386F5)
val YanjiDarkPrimaryStrong = Color(0xFF7CA6F8)
val YanjiDarkPrimarySoft = Color(0xFF1D2C4A)

val YanjiDarkLavender = Color(0xFFA79BFA)
val YanjiDarkLavenderSoft = Color(0xFF2B2544)
val YanjiDarkLavenderDeep = Color(0xFF8B7CF6)
val YanjiDarkLavenderSoftDeep = Color(0xFF26203D)

val YanjiDarkSuccess = Color(0xFF38B27A)
val YanjiDarkSuccessSoft = Color(0xFF163324)

val YanjiDarkWarning = Color(0xFFE5A43B)
val YanjiDarkWarningSoft = Color(0xFF3B2C15)

val YanjiDarkDanger = Color(0xFFE56363)
val YanjiDarkDangerSoft = Color(0xFF3B1B1B)
val YanjiDarkError = YanjiDarkDanger



// ---------------------------------------------------------------------------
// Achievement rarity tokens（成就稀有度）
//
// 单列一组的原因：稀有度是成就系统独有的六阶语义色（前景色 / 极浅容器 / 渐变收尾色），
// 与主蓝、Lavender、Success/Warning/Danger 均不重合，复用既有 token 会破坏语义。
// UI 层（AchievementsScreen）只允许引用这些 token，不得内联 Color(0x...)。
// ---------------------------------------------------------------------------

val AchievementCommon = Color(0xFF64748B)      // Slate
val AchievementUncommon = Color(0xFF10B981)    // Emerald
val AchievementRare = Color(0xFF2563EB)        // Royal Blue
val AchievementEpic = Color(0xFF8B5CF6)        // Purple
val AchievementLegendary = Color(0xFFF59E0B)   // Amber Gold
val AchievementMythic = Color(0xFFEF4444)      // Crimson Red

val AchievementCommonContainer = Color(0xFFF1F5F9)
val AchievementUncommonContainer = Color(0xFFECFDF5)
val AchievementRareContainer = Color(0xFFEFF6FF)
val AchievementEpicContainer = Color(0xFFF5F3FF)
val AchievementLegendaryContainer = Color(0xFFFFFBEB)
val AchievementMythicContainer = Color(0xFFFEF2F2)

/** 徽章线性渐变的主色之后的收尾色（比主色浅一档）。MYTHIC 为三段渐变，无收尾色。 */
val AchievementUncommonGradientEnd = Color(0xFFA7F3D0)
val AchievementRareGradientEnd = Color(0xFF93C5FD)
val AchievementEpicGradientEnd = Color(0xFFC4B5FD)
val AchievementLegendaryGradientEnd = Color(0xFFFCD34D)
