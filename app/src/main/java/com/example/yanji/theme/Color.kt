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
val YanjiQuaternaryLabel = Color(0xFFC4CBD6)

val YanjiGroupedBackground = Color(0xFFF2F4F7)
val YanjiElevatedSurface = Color(0xFFFFFFFF)

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

// ===========================================================================
// Yanji Dark Palette —— 严格对齐 design_dark.md「Midnight Blue」
//
// 三条不可违背的约束（design_dark.md §1.2）：
//  1. **禁止纯黑 #000000**：底色是富含冷蓝因子的深邃灰蓝，避免卤化效应与刺眼光感；
//  2. **用「表面明度递进」而非阴影表达层级**：背景 → 卡片 → 控件 → 浮层 逐级提亮；
//  3. **主色升调**：亮色主蓝 #356AE6 在暗底反差不足，暗色升为 #4F7DF3，强调文字 #7197F7。
//
// 半透明容器一律用 ARGB 常量（如 0x294F7DF3 = rgba(79,125,243,0.16)），
// 让「微发光容器」与深底自然融合，而不是硬编码一层不透明的浅色块。
// ===========================================================================

// --- 表面层级（Elevation by Surface Lightness）---
/** 最底层：App 背景。深邃冷夜蓝，刻意避开纯黑。 */
val YanjiDarkBackground = Color(0xFF0D111A)

/** 沉浸式分组底色：比背景更深一档，用于 Grouped Table / List 背景。 */
val YanjiDarkGroupedBackground = Color(0xFF080C14)

/** 第一层：内容卡片。仅比背景亮约 5%，形成温和可见的层次。 */
val YanjiDarkSurface = Color(0xFF151B28)

/** 第二层：输入框 / 次级卡片 / 未选中标签。 */
val YanjiDarkSurfaceSoft = Color(0xFF1D2536)

/** 特征卡：学习目标、伴学胶囊等夜幕蓝卡片。 */
val YanjiDarkSurfaceBlue = Color(0xFF162035)

/** 第三层：浮层 / 弹窗 / 底部弹层。 */
val YanjiDarkSurfaceFloating = Color(0xFF222C40)

// --- 文本 ---
/** 柔白冷灰：杜绝纯白 #FFF 的刺眼与晕光，但字形依然饱满。 */
val YanjiDarkTextPrimary = Color(0xFFF0F4FC)
val YanjiDarkTextSecondary = Color(0xFF94A3B8)
val YanjiDarkTextTertiary = Color(0xFF64748B)
val YanjiDarkQuaternaryLabel = Color(0xFF475569)

// --- 描边与分割线（暗色用 8% / 5% 白替代阴影定义边界）---
/** rgba(255,255,255,0.08)：精细高质感 1px 描边，替代阴影定义卡片边缘。 */
val YanjiDarkBorder = Color(0x14FFFFFF)

/** rgba(255,255,255,0.05)：极弱分割线，存在而不喧宾夺主。 */
val YanjiDarkDivider = Color(0x0DFFFFFF)

/** rgba(255,255,255,0.06)：比 divider 略强一点点的次级边框。 */
val YanjiDarkBorderSoft = Color(0x0FFFFFFF)

// --- 主色（升调）---
/** 高透心流蓝：暗底上的主 CTA、进度环、高亮标记。 */
val YanjiDarkPrimary = Color(0xFF4F7DF3)

/** 柔亮天蓝：暗底上的选中文字与高对比强调标签。 */
val YanjiDarkPrimaryStrong = Color(0xFF7197F7)

/** rgba(79,125,243,0.16)：半透明柔和容器 —— 选中胶囊 / Tab 高亮 / 图标浅底。 */
val YanjiDarkPrimarySoft = Color(0x294F7DF3)

/** rgba(79,125,243,0.40)：选中态细腻微发光边缘。 */
val YanjiDarkPrimaryEdge = Color(0x664F7DF3)

/** rgba(79,125,243,0.35)：专注圆环的夜间自发光呼吸微感。 */
val YanjiDarkPrimaryGlow = Color(0x594F7DF3)

/** rgba(255,255,255,0.06)：专注进度底轨。 */
val YanjiDarkTrack = Color(0x0FFFFFFF)

// --- 品牌紫（卷卷 / AI 复盘）---
val YanjiDarkLavender = Color(0xFFA78BFA)

/** AI 深度复盘正文色：比 accent 更亮，保证暗底长文可读。 */
val YanjiDarkLavenderDeep = Color(0xFFC4B5FD)

/** rgba(167,139,250,0.16)：卷卷 AI 洞察气泡 / 反思建议的轻盈底。 */
val YanjiDarkLavenderSoft = Color(0x29A78BFA)

/** rgba(167,139,250,0.10)：AI 深度复盘卡片的极弱紫雾底。 */
val YanjiDarkLavenderSoftDeep = Color(0x1AA78BFA)

// --- 语义色 ---
val YanjiDarkSuccess = Color(0xFF34D399)
val YanjiDarkSuccessSoft = Color(0x2634D399)
val YanjiDarkWarning = Color(0xFFFBBF24)
val YanjiDarkWarningSoft = Color(0x26FBBF24)
val YanjiDarkDanger = Color(0xFFF87171)
val YanjiDarkDangerSoft = Color(0x26F87171)
val YanjiDarkError = YanjiDarkDanger

// --- 浮动液态玻璃底栏（design_dark.md §3.2）---
/** rgba(21,27,40,0.78)：半透明玻璃面板，让底栏与滚动内容自然叠合。 */
val YanjiDarkDockPanel = Color(0xC7151B28)

/** rgba(79,125,243,0.22)：激活态胶囊底色。 */
val YanjiDarkDockPill = Color(0x384F7DF3)

/** 亮色底栏面板：保持不透明纯白（不改动亮色既有视觉）。 */
val YanjiDockPanel = YanjiSurface

// --- 学科序列色（design_dark.md §3.4；亮色序列色见文件上方 Subject*）---
val SubjectMathDark = Color(0xFF4F7DF3)
val SubjectMajorDark = Color(0xFF818CF8)
val SubjectEnglishDark = Color(0xFFA78BFA)
val SubjectPoliticsDark = Color(0xFF38BDF8)

/** 「其他」：规范未给暗色值，取与 text-secondary 同值的雾蓝灰，保证与主蓝区分度。 */
val SubjectOtherDark = Color(0xFF94A3B8)

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

/**
 * 分段导航栏（YanjiSegmentedControl）未选中轨道的亮色实色背景。
 *
 * 单列的原因：该控件要求「纯实色、无半透明透出」，与其他容器语义不同；
 * 暗色分支复用 `MaterialTheme.colorScheme.surfaceVariant`，故这里只定义亮色值。
 * UI 层只能引用本 token，不得内联 `Color(0xFFECEFF5)`。
 */
val YanjiSegmentTrack = Color(0xFFECEFF5)

// ---------------------------------------------------------------------------
// Power Saving Mode tokens（专注计时省电模式）
//
// 用于在专注计时中静置无触碰时自动进入的省电/极黑时钟模式：
// 1. 背景为 OLED 灭屏级纯黑 #000000（像素级熄灭，极度省电）；
// 2. 文字为柔和冷白，夜间不刺眼且低功耗；
// 3. 轨道为极弱透明白，进度条使用心流主蓝。
// ---------------------------------------------------------------------------
val YanjiPowerSavingBackground = Color(0xFF000000)
val YanjiPowerSavingText = Color(0xFFE2E8F0)
val YanjiPowerSavingTrack = Color(0x26FFFFFF)
val YanjiPowerSavingProgress = Color(0xFF4F7DF3)

