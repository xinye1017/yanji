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
     * Standard card radius — DESIGN.md「Shapes」规定 standard card = 24px。
     *
     * [com.example.yanji.ui.components.YanjiCard] 以此作为默认 shape，
     * 不再回退到 Material 的 `CardDefaults.shape`（medium = 16dp），
     * 那样会让标准卡片偏离设计稿。
     */
    val StandardCardRadius = 24.dp

    val MessageRadius = 20.dp
    val ContentBlockRadius = 16.dp
    val ButtonRadius = 12.dp
    val ChipRadius = 999.dp
}
