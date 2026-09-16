package com.example.yanji.data.backup

import kotlinx.serialization.json.Json

/**
 * 备份文件的编解码与校验。**纯 Kotlin，不依赖 Android**，因此可以被 JVM 单元测试完整覆盖。
 *
 * 校验策略（宁严勿宽）：
 *  - JSON 解析失败 → 拒绝，并明确告知"不是有效的研迹备份"；
 *  - `schemaVersion` 比当前更高 → 拒绝。旧 App 读不了新格式，硬读会**丢字段**，
 *    这比直接报错更危险；
 *  - `schemaVersion` 更低 → 接受，并提示缺失字段会用默认值补齐；
 *  - 备份为空 → 接受但提示"导入后将清空本机记录"（"删空后导出"是合法状态）。
 */
object BackupCodec {

    /**
     * 导入文件的字符数上限（防御性上限，正常备份远达不到）。
     *
     * UTF-8 编码下字节数 ≥ 字符数恒成立，因此用字符数做保守代理即可，避免为量尺寸
     * 先复制整份字符串。用途：在 JSON 解析**之前**拒绝超大输入，防止恶意或损坏文件
     * 在 decode / 整表替换事务里造成内存峰值。深度防御依赖 kotlinx.serialization
     * 对未知键的结构性跳过 + Room 事务，见审计报告"备份导入防御"一节。
     */
    private const val MAX_IMPORT_CHARS = 64L * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // 前向兼容：更新版本的备份里多出来的字段不应该让旧版本直接失败
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(backup: YanjiBackup): String = json.encodeToString(backup)

    fun decode(
        raw: String,
        currentSchemaVersion: Int = YanjiBackup.CURRENT_SCHEMA_VERSION
    ): BackupDecodeResult {
        if (raw.isBlank()) {
            return BackupDecodeResult.Failure("文件内容为空，请确认选择的是研迹导出的 JSON 备份")
        }

        if (raw.length > MAX_IMPORT_CHARS) {
            return BackupDecodeResult.Failure(
                "备份文件过大（超过 64 MB），请确认选择的是研迹导出的 JSON 备份"
            )
        }

        val backup = runCatching { json.decodeFromString<YanjiBackup>(raw) }.getOrElse {
            return BackupDecodeResult.Failure("无法解析该文件：它不是有效的研迹备份（JSON 格式错误）")
        }

        if (backup.schemaVersion <= 0) {
            return BackupDecodeResult.Failure("备份文件缺少有效的格式版本号（schemaVersion），无法确认其结构")
        }

        if (backup.schemaVersion > currentSchemaVersion) {
            return BackupDecodeResult.Failure(
                "该备份由更新版本的研迹生成（格式 v${backup.schemaVersion}，当前支持 v$currentSchemaVersion）。" +
                    "直接导入会丢失新字段，请先升级研迹后再试。"
            )
        }

        val warnings = buildList {
            if (backup.schemaVersion < currentSchemaVersion) {
                add("备份由旧版本生成（格式 v${backup.schemaVersion}），缺失字段将使用默认值")
            }
            if (backup.isEmpty) {
                add("备份中没有任何学习数据，导入后将清空本机现有记录")
            }
        }

        return BackupDecodeResult.Success(backup, warnings)
    }
}
