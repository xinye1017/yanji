package com.example.yanji.theme

import androidx.compose.ui.graphics.Color

// Yanji Rounded Blue Color Palette
val YanjiPrimary = Color(0xFF356AE6)
val YanjiPrimaryStrong = Color(0xFF2453BF)
val YanjiPrimarySoft = Color(0xFFEAF1FF)
val YanjiOnPrimary = Color(0xFFFFFFFF)

/** App 页面背景（浅色）。刻意不是纯白，使白色卡片无需阴影/描边即可分辨。 */
val YanjiBackground = Color(0xFFF2F4F7)
val YanjiSurface = Color(0xFFFFFFFF)
val YanjiSurfaceSoft = Color(0xFFF1F5FB)
val YanjiSurfaceBlue = Color(0xFFF4F7FF)

/**
 * 亮色文字灰阶：四级里前三级必须全部承载信息（WCAG 1.4.3 的 4.5:1）。
 *
 * 约束（实测，取最坏配对「白卡 / 页面底 / surfaceSoft / surfaceBlue / primarySoft」的最小值）：
 *  - [YanjiTextSecondary] #5A6473 → 5.99 / 5.44 / 5.48 / 5.59 / 5.28
 *  - [YanjiTextTertiary]  #606A7C → 5.45 / 4.95 / 4.99 / 5.09 / 4.81
 *  - [YanjiQuaternaryLabel] 是旧 tertiary 值，**仅供禁用态与纯装饰**（规范豁免），不得用于任何可读信息。
 *
 * 为什么三级要压到 #606A7C 这么深：亮色底（#F2F4F7）之上，比 #667085 更浅的中性灰
 * 不存在仍 ≥4.5:1 的取值，即「比二级更淡、却仍达标」的第三级在数学上不可能存在。
 * 因此本系统把层级差转移到字号与字重，而不是继续用明度表达层级。
 * 同理：**不得在调用点用 `copy(alpha = )` 稀释前三级文字** —— 0.7 alpha 会把 5.99 打到 3.13。
 */
val YanjiTextPrimary = Color(0xFF172033)
val YanjiTextSecondary = Color(0xFF5A6473)
val YanjiTextTertiary = Color(0xFF606A7C)
val YanjiQuaternaryLabel = Color(0xFF98A2B3)

val YanjiElevatedSurface = Color(0xFFFFFFFF)

val YanjiBorder = Color(0xFFE4EAF2)
val YanjiDivider = Color(0xFFEDF1F6)

/**
 * 列表行分割线：非卡片式列表的行边界（随笔历史页的跨日期分组线等）。
 *
 * 为什么不能直接用 [YanjiDivider]（1.03:1）或 [YanjiBorder]（1.10:1）：
 * 卡片列表有卡片自身的边界兜底，分割线只是装饰；但**非卡片列表没有这层兜底**——
 * 分割线本身就是唯一的分组线索，1.0:1 级等于没有线，跨日期分组会糊成一片。
 * 随笔历史页正是这种结构：同日多篇之间不画线，只有跨日期才画（见 NoteScreen 的分组循环）。
 *
 * 取 #C6CCD5：连调两档后按真机反馈继续回调。1.92 → 1.66 → 1.47:1，
 * 最终与改动前的原始观感（quaternary@50% 的 1.48:1）基本齐平。
 * 教训记在这里：这个页面的分割线承担的是「肌理」而不是「信息」，
 * 超过原始观感一档就会在长屏上变成一道道横杠。该值不承载任何信息，
 * 不受 WCAG 1.4.11 的 3:1 约束（那条只管控件边界，见 [YanjiFieldBorder]）。
 */
val YanjiListSeparator = Color(0xFFC6CCD5)

/**
 * 输入控件填充色：搜索框等**无描边**输入控件的底色。
 *
 * 为什么不复用 [YanjiSurfaceSoft]（#F1F5FB）或 [YanjiSurfaceBlue]（#F4F7FF）：
 * 它们都比页面底 [YanjiBackground]（#F2F4F7）更浅，铺上去等于没铺。
 * 搜索框取消描边后，识别度只能由「形状 + 填充」承担，填充必须真的比页面底暗一档。
 *
 * 实测：#DEE4EC 在页面底 #F2F4F7 上 1.16:1。填充是大面积色块，
 * 1.16:1 的可辨识度远高于同等数值的 0.8dp 发丝线。
 */
val YanjiInputFill = Color(0xFFDEE4EC)

/** 暗色输入填充：与暗色次级卡 #1D2536 同值，在 #0D111A 上 1.21:1。 */
val YanjiDarkInputFill = Color(0xFF1D2536)

/**
 * 表单 / 控件边界的专职色（WCAG 1.4.11 要求「可辨识组件边界」≥3:1）。
 *
 * 与 [YanjiBorder] 分开定义的原因：后者承担卡片描边、分隔线等**装饰性**边界，
 * 1.10:1 是刻意的轻；用它同时做输入框描边会让输入框要么过重、要么不达标。
 * 实测 #7E8DA1：白 3.38 / 页面底 3.07 / surfaceSoft 3.09 / surfaceBlue 3.15。
 */
val YanjiFieldBorder = Color(0xFF7E8DA1)

val YanjiLavender = Color(0xFF8B7CF6)
val YanjiLavenderSoft = Color(0xFFF0EDFF)

/**
 * 卷卷品牌色强化色阶。
 *
 * 比 [YanjiLavender] 更深、饱和度更高，专用于卷卷伙伴主题的经典辅色强化色阶（[MascotTheme.kt] 的 lightSecondaryStrong）：
 * 浅紫容器 `YanjiLavenderSoft` 需要与之配对以达到正文对比度要求，
 * 而 `YanjiLavender` 铺在浅紫上对比不足。
 *
 * 不参与主 CTA，也不得替换 [YanjiPrimary]。
 */
val YanjiLavenderDeep = Color(0xFF5C4BC3)

/**
 * 语义色「文字档」：这三个值会被直接当作文字与图标前景使用，因此按 ≥4.5:1 定值。
 *
 * 实测（白卡 / 页面底 / surfaceSoft / 各自 Soft 容器）：
 *  - [YanjiSuccess] #17784F → 5.47 / 4.97 / 5.00 / 4.95（旧 #2F9E6D 只有 3.37 / 3.06 / 3.08 / 3.05）
 *  - [YanjiWarning] #9A6100 → 5.14 / 4.67 / 4.70 / 4.76（旧 #D99024 只有 2.64 / 2.39 / 2.41 / 2.44）
 *  - [YanjiDanger]  #BE3232 → 5.69 / 5.16 / 5.20 / 4.98，白字压此底亦 5.69（旧 #D94B4B 为 4.15）
 *
 * 三个 `...Soft` 容器值**刻意不变**：它们只做底色、不做文字。
 * 「文字档加深 + Soft 容器不变」让整体调性几乎不动，同时把 6 个失败配对一次修完。
 */
val YanjiSuccess = Color(0xFF17784F)
val YanjiSuccessSoft = Color(0xFFE8F7F0)

val YanjiWarning = Color(0xFF9A6100)
val YanjiWarningSoft = Color(0xFFFFF5E3)

val YanjiDanger = Color(0xFFBE3232)
val YanjiDangerSoft = Color(0xFFFDECEC)

// 学科色不属于 App Theme：它是 Subject.colorHex 的持久化内容数据。
// 统一解析与亮/暗对比度处理见 SubjectColors.kt；伙伴主题只负责 UI Accent。

// ===========================================================================
// Yanji Dark Palette —— 严格对齐「Midnight Blue」规范（AGENTS.md §三.6）
//
// 三条不可违背的约束（AGENTS.md §三.6 UI 设计红线）：
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

/**
 * 暗色三级文字 #8797AC：从 #64748B 提亮一档，使四种落点全部 ≥4.5:1
 * （暗卡 5.78 / 页面底 6.34 / 次级卡 5.15 / 浮层 4.69；旧值为 3.62 / 3.97 / 3.22 / 2.94）。
 * 暗色底有明度空间，所以暗色能保住四级灰阶，亮色不能（见上方亮色灰阶说明）。
 */
val YanjiDarkTextTertiary = Color(0xFF8797AC)

/** 四级：与亮色同义 —— 只用于禁用态与装饰，不承载信息。 */
val YanjiDarkQuaternaryLabel = Color(0xFF475569)

/** 暗色控件边界专职色，与 [YanjiFieldBorder] 同职（实测：暗卡 3.95 / 浮层 3.21）。 */
val YanjiDarkFieldBorder = Color(0xFF6B7A91)

// --- 描边与分割线 ---
//
// 分割线只有**两档**语义，两个主题一一对应，不再各页面自行 copy(alpha) 凑值：
//
//   档位          Token                 亮色              暗色              用途
//   ------------------------------------------------------------------
//   内部分行      rowDivider            #EDF1F6  1.13:1   白@3%  1.06:1   设置行、详情页行间、弹窗
//   结构分组线    listSeparator         #C6CCD5  1.47:1   白@9%  1.24:1   非卡片列表的跨分组线
//
// 暗色为什么比亮色低一截：亮暗底的感知不对称 —— 同样的亮度比，浅底上的深线
// 远不如深底上的浅线显眼。暗色若照搬亮色的 1.47:1（白@14%），会直接变成一道道硬杠。
// 这也是此前「深色下分割线不统一」的成因：14%（结构线）/ 5%（满强度 outlineVariant）/
// 2.5%（separator@0.5）三个值同时存在，彼此没有语义关系。

/** rgba(255,255,255,0.08)：精细高质感 1px 描边，替代阴影定义卡片边缘。 */
val YanjiDarkBorder = Color(0x14FFFFFF)

/**
 * 通用描边/分隔基准（白@5%），M3 `outlineVariant` 的落点。
 *
 * 职责已收窄：只服务**非分割线**的装饰性描边（卡片边缘、输入框弱边界等）。
 * 画分割线请改用 [YanjiDarkRowDivider]（内部分行）或 [YanjiDarkListSeparator]（结构分组），
 * 不要再直接引用本值 —— 那正是此前深色下出现三档强度的来源。
 */
val YanjiDarkDivider = Color(0x0DFFFFFF)

/** rgba(255,255,255,0.03)：内部分行线，合成后 #141821 在 #0D111A 上 1.06:1。 */
val YanjiDarkRowDivider = Color(0x08FFFFFF)

/** rgba(255,255,255,0.09)：结构分组线，合成后 #1E222A 在 #0D111A 上 1.24:1。 */
val YanjiDarkListSeparator = Color(0x17FFFFFF)

/**
 * 亮色内部分行线。与 [YanjiDivider] 同值（#EDF1F6），单列一份只为让
 * 「内部分行 / 结构分组」两档在两个主题下语义对称，暗色有独立取值时不必再靠 alpha 硬凑。
 */
val YanjiRowDivider = Color(0xFFEDF1F6)

// --- 主色（升调）---
/** 高透心流蓝：暗底上的主 CTA、进度环、高亮标记。 */
val YanjiDarkPrimary = Color(0xFF4F7DF3)

/**
 * 暗色主色上的前景（主按钮文字、FAB 图标、选中药丸文字）。
 *
 * 不能沿用亮色的白字：暗色 `primary` 刻意升调成较亮的蓝/粉/橙/绿以保证在深底上可读，
 * 白字压上去反而只有 2.56–3.78:1（五套吉祥物主题实测），主 CTA 直接违反 1.4.3。
 * 用背景级深墨后：5.00 / 6.04 / 6.95 / 7.05 / 7.38 —— 与 M3 暗色「亮主色 + 暗前景」的惯例一致。
 */
val YanjiDarkOnPrimary = Color(0xFF0D111A)

/** 柔亮天蓝：暗底上的选中文字与高对比强调标签。 */
val YanjiDarkPrimaryStrong = Color(0xFF7197F7)

/** rgba(79,125,243,0.16)：半透明柔和容器 —— 选中胶囊 / Tab 高亮 / 图标浅底。 */
val YanjiDarkPrimarySoft = Color(0x294F7DF3)

// --- 品牌紫（卷卷 / AI 复盘）---
val YanjiDarkLavender = Color(0xFFA78BFA)

/** AI 深度复盘正文色：比 accent 更亮，保证暗底长文可读。 */
val YanjiDarkLavenderDeep = Color(0xFFC4B5FD)

/** rgba(167,139,250,0.16)：卷卷 AI 洞察气泡 / 反思建议的轻盈底。 */
val YanjiDarkLavenderSoft = Color(0x29A78BFA)

// --- 语义色 ---
val YanjiDarkSuccess = Color(0xFF34D399)
val YanjiDarkSuccessSoft = Color(0x2634D399)
val YanjiDarkWarning = Color(0xFFFBBF24)
val YanjiDarkWarningSoft = Color(0x26FBBF24)
val YanjiDarkDanger = Color(0xFFF87171)
val YanjiDarkDangerSoft = Color(0x26F87171)


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
 * 分段导航栏「贴页面底色」变体（OnPage）的轨道与药丸色。
 *
 * 单列的原因：该变体直接压在页面背景上，需要比 `YanjiSegmentTrack` 更重的凹槽对比，
 * 又不能复用 `surfaceVariant` —— 亮色下它会和页面浅灰底糊在一起，暗色下又压不出层次。
 * UI 层只能引用这三个 token，不得内联字面量。
 *
 * 亮色由 #E2E7EE 加深到 #D8DFE9：分段控件取消描边后（见 YanjiSegmentedControl），
 * 轨道填充要独自承担控件边界。#E2E7EE 在页面底上只有 1.13:1，等于没有边界；
 * #D8DFE9 提到 1.22:1，同时选中药丸（纯白）压在其上仍有 1.34:1，两头都不吃亏。
 *
 * 暗色**不加深**：暗轨 #1A2333 压深到 #212B3D 会让药丸 #2A374F 的对比从 1.32:1
 * 掉到 1.19:1，药丸直接糊进轨道里。暗色靠药丸自身的亮度拉开层次，轨道维持原值。
 */
val YanjiSegmentTrackOnPage = Color(0xFFD8DFE9)
val YanjiDarkSegmentTrackOnPage = Color(0xFF1A2333)

/** OnPage 变体暗色的选中药丸：浮于暗轨之上，需与主蓝文字拉开对比。 */
val YanjiDarkSegmentPillOnPage = Color(0xFF2A374F)

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

