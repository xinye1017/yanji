package com.example.yanji.data.backup

import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Validates destructive imports before any database write. Empty, complete exports are valid. */
object BackupCodec {
    /** Shared UTF-8 document limit. File IO enforces bytes before allocating the JSON model. */
    const val MAX_IMPORT_BYTES: Int = 64 * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(backup: YanjiBackup): String = json.encodeToString(backup)

    fun decode(raw: String, currentSchemaVersion: Int = YanjiBackup.CURRENT_SCHEMA_VERSION): BackupDecodeResult {
        if (raw.isBlank()) return BackupDecodeResult.Failure("文件内容为空，请确认选择的是研迹导出的 JSON 备份")
        if (raw.length > MAX_IMPORT_BYTES) return BackupDecodeResult.Failure("备份文件过大（超过 64 MB），无法安全恢复")

        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return BackupDecodeResult.Failure("无法解析该文件：它不是有效的研迹备份（JSON 格式错误）")
        val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: return BackupDecodeResult.Failure("备份文件缺少有效的格式版本号（schemaVersion），无法确认其结构")
        if (version <= 0) return BackupDecodeResult.Failure("备份文件缺少有效的格式版本号（schemaVersion），无法确认其结构")
        if (version > currentSchemaVersion) return BackupDecodeResult.Failure(
            "该备份由更新版本的研迹生成（格式 v$version，当前支持 v$currentSchemaVersion），请先升级研迹后再试"
        )
        validateEnvelope(root, version)?.let { return BackupDecodeResult.Failure(it) }
        val backup = runCatching { json.decodeFromString<YanjiBackup>(raw) }.getOrElse {
            return BackupDecodeResult.Failure("无法解析该文件：它不是有效的研迹备份（记录结构或数值无效）")
        }
        validateRecords(backup)?.let { return BackupDecodeResult.Failure(it) }
        val warnings = buildList {
            if (version < currentSchemaVersion) add("备份由旧版本生成（格式 v$version），缺失字段将使用默认值")
            if (version < 3 && backup.settings != null) {
                add("旧版备份未包含完整的决策模型设置，恢复设置时缺失项将使用当前产品默认值")
            }
            if (backup.isEmpty) add("备份中没有任何学习数据，导入后将清空本机现有记录")
        }
        return BackupDecodeResult.Success(backup, warnings)
    }

    private val recordArrays = listOf(
        "focusSessions", "examSessions", "journalEntries", "chatSessions", "chatMessages", "quickStartPresets"
    )

    private fun validateEnvelope(root: JsonObject, version: Int): String? {
        // All previous exports encoded defaults. Requiring their envelope prevents {} or a
        // partial unrelated object from silently becoming an instruction to clear every table.
        // `unlockedAchievements` is deliberately NOT required any more: the achievement system
        // is gone, so old files that still carry it import normally (the field is ignored) and
        // files exported from now on simply omit it.
        val required = recordArrays + listOf("exportedAt", "appVersionName", "checkIns") +
            if (version >= 2) listOf("studyTasks", "subjects") else emptyList()
        required.firstOrNull { it !in root }?.let { return "这不是完整的研迹备份：缺少 $it，无法安全恢复" }
        if ((root["exportedAt"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull == null ||
            (root["appVersionName"] as? JsonPrimitive)?.isString != true
        ) return "备份文件的导出时间或应用版本无效，无法安全恢复"

        for (key in recordArrays + listOf("studyTasks", "subjects", "checkIns")) {
            if (key !in root) continue // v1 did not contain every newer table.
            val entries = root[key] as? JsonArray ?: return "备份文件中的 $key 不是列表，无法安全恢复"
            for (entry in entries) {
                val record = entry as? JsonObject ?: return "备份文件中的 $key 记录结构无效"
                if (key != "checkIns") {
                    val id = (record["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (id.isNullOrBlank()) return "备份文件中的 $key 缺少有效 id"
                }
            }
        }
        // No achievement validation: old files may still carry the key, and its shape no
        // longer affects whether the rest of the backup is safe to restore.
        if (version >= 3 && root["settings"] is JsonObject) {
            val settings = root.getValue("settings") as JsonObject
            if (listOf("decisionProvider", "decisionModel", "decisionEndpoint").any { it !in settings }) {
                return "这不是完整的 v3 备份：缺少决策模型设置，无法安全恢复"
            }
        }
        return null
    }

    private fun validateRecords(backup: YanjiBackup): String? {
        val identities = listOf(
            backup.focusSessions.map { it.id }, backup.examSessions.map { it.id },
            backup.noteEntries.map { it.id }, backup.chatSessions.map { it.id },
            backup.chatMessages.map { it.id }, backup.quickStartPresets.map { it.id },
            backup.studyTasks.map { it.id }, backup.subjects.map { it.id }, backup.checkIns.map { it.date }
        )
        if (identities.any { it.size != it.toSet().size }) return "备份文件中存在重复 id 或打卡日期，无法安全恢复"
        val chatIds = backup.chatSessions.map { it.id }.toSet()
        // The original chat store used session-initial without a corresponding chat_sessions row.
        if (backup.chatMessages.any { it.sessionId !in chatIds && it.sessionId != "session-initial" }) {
            return "备份中的聊天消息缺少对应会话，无法安全恢复"
        }
        val dates = backup.noteEntries.map { it.date } + backup.checkIns.map { it.date } +
            backup.studyTasks.map { it.date } + listOfNotNull(backup.settings?.targetExamDate?.takeIf { it.isNotBlank() })
        if (dates.any { !validDate(it) }) return "备份文件中存在无效日期，无法安全恢复"
        if (backup.focusSessions.any { it.durationSeconds < 0 || it.pausedDurationSeconds < 0 || it.pauseCount < 0 } ||
            backup.examSessions.any { it.plannedDurationSeconds < 0 || it.actualDurationSeconds < 0 } ||
            backup.studyTasks.any { it.plannedMinutes < 0 } || backup.checkIns.any { it.streak < 0 }
        ) return "备份文件中的时长或计数不能为负数"
        if (backup.examSessions.any { !it.maxScore.isFinite() || it.score?.isFinite() == false } ||
            backup.settings?.dailyGoalHours?.isFinite() == false
        ) return "备份文件中存在无效数值，无法安全恢复"
        if (backup.settings?.let { it.dailyGoalHours < 0 || it.validStudyThresholdMinutes < 0 } == true) {
            return "备份文件中的学习目标不能为负数"
        }
        // Wall-clock ordering is deliberately not constrained: timers measure actual duration
        // with a monotonic clock, and changing the system time can reverse stored timestamps.
        return null
    }

    private fun validDate(value: String): Boolean = try {
        LocalDate.parse(value)
        true
    } catch (_: DateTimeParseException) { false }
}
