package com.example.yanji.theme

/**
 * 研迹的"学习伙伴主题"标识。
 *
 * 它与 [YanjiThemeMode] 正交：前者决定品牌/吉祥物，后者决定浅色/深色。
 * 持久化始终保存 [name]，未知值安全回落到 [CLOUD]。
 *
 * 纯 Kotlin：不 import 任何 Compose 运行时。吉祥物的**调色板与 drawable 资源**属于
 * 已删除的 Compose 设计系统（`MascotPalette` / `MascotThemeSpec`），活代码只需要
 * 标识本身与它的展示名，因此这里只保留 [MascotThemes.displayName]。
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

/**
 * 吉祥物展示名。仅供诊断快照等非 UI 文本使用。
 *
 * 名字与已删除的 Compose 主题一一对应，改这里等于改用户在自己数据里看到的称呼，
 * 因此按 id 显式列出，不做模糊匹配。
 */
object MascotThemes {

    private val displayNames: Map<MascotThemeId, String> = mapOf(
        MascotThemeId.CLOUD to "卷卷",
        MascotThemeId.BUNNY to "绵绵",
        MascotThemeId.PENGUIN to "冰冰",
        MascotThemeId.SHIBA to "豆豆",
        MascotThemeId.FROG to "芽芽",
    )

    fun displayName(id: MascotThemeId): String = displayNames[id] ?: displayNames.getValue(MascotThemeId.DEFAULT)

    /** 与 [YanjiThemeMode.fromStorage] 同型的宽松解析。 */
    fun fromStorage(raw: String?): MascotThemeId = MascotThemeId.fromStorage(raw)

    /** 便捷入口：直接拿展示名。 */
    fun displayNameForStorage(raw: String?): String = displayName(fromStorage(raw))
}
