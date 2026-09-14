package com.example.yanji.theme

import androidx.compose.ui.unit.dp

/**
 * 圆角 token（DESIGN.md `rounded` block 的工程化映射）。
 *
 * 设计目标：消除 card-in-card。任何容器在嵌套时都应复用这套 token，
 * 由「角色」决定取值，而不是看到什么数抄什么数。
 *
 *   - [PageRadius]        页面级别的容器（Sheet、Hero Card）
 *   - [StandardCardRadius] 标准 YanjiCard 默认圆角（DESIGN.md: standard card = 24dp）
 *   - [MessageRadius]     单条消息外层气泡
 *   - [ContentBlockRadius]消息内的内容子块（诊断、证据、步骤、引用）
 *   - [ButtonRadius]      行动按钮 / 主操作
 *   - [ChipRadius]        pill 形 chip / tag
 */
object YanjiRadius {
    val PageRadius = 24.dp

    /**
     * Hero card radius — DESIGN.md「Shapes」: large hero card = 24–28px。
     */
    val HeroCardRadius = 28.dp

    /**
     * Standard card radius — DESIGN.md「Shapes」规定 standard card = 24px。
     *
     * [com.example.yanji.ui.components.YanjiCard] 以此作为默认 shape，
     * 不再回退到 Material 的 `CardDefaults.shape`（medium = 16dp），
     * 那样会让标准卡片偏离设计稿。
     */
    val StandardCardRadius = 24.dp

    /**
     * Compact card radius — 子卡片、列表条目卡片、嵌套卡片使用 16dp。
     */
    val CompactCardRadius = 16.dp

    val MessageRadius = 20.dp
    val ContentBlockRadius = 16.dp

    /**
     * 小尺度容器的统一圆角（12dp）：标签、徽章、图标底、行内高亮块、小分段控件等。
     *
     * 这些元素**语义各异但视觉角色一致**——都是附属于主体的紧凑块，因此共用同一档，
     * 不为每个语义单独造一个同值的 token：那只是把 magic number 换个名字。
     */
    val Small = 12.dp

    /**
     * 行动按钮圆角 — 12dp，保持与 [com.example.yanji.ui.components.YanjiButtons] 一致。
     */
    val ButtonRadius = 12.dp

    /**
     * 输入框圆角 — 16dp，保持与 [com.example.yanji.ui.components.YanjiTextField] 一致。
     */
    val InputRadius = 16.dp

    val DialogRadius = 24.dp
    val SheetRadius = 28.dp
    val ChipRadius = 999.dp
}

