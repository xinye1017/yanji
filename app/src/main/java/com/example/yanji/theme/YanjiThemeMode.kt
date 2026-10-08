package com.example.yanji.theme

/**
 * 主题模式：跟随系统 / 强制浅色 / 强制深色。
 *
 * 持久化在 `user_settings.themeMode`（存 [name] 字符串，与 `aiProvider` 等同为 String 字段，
 * 便于 Room 迁移与旧备份兼容）。未知值一律回退 [SYSTEM]，保证前/后向兼容都不崩。
 *
 * 纯 Kotlin：不 import 任何 Compose 运行时。RN 侧通过 `YanjiThemeModule` 读写的正是这个
 * 枚举，而 JS 层自己有一套 `YanjiThemeMode` 类型——两侧以 [name] 字符串对齐。
 */
enum class YanjiThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT = SYSTEM

        /** 宽松解析：null / 空串 / 历史遗留值 / 未来新增值 全部安全回退到 [SYSTEM]。 */
        fun fromStorage(raw: String?): YanjiThemeMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
