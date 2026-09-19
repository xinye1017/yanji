package com.example.yanji.data.backup

import com.example.yanji.data.CheckIn
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSession
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.UserSettings
import kotlinx.serialization.Serializable

/**
 * 用户设置的备份形态。
 *
 * **刻意不包含 `aiApiKey`**：凭据不在 Room 里、不进备份、也不应该出现在用户可自由
 * 复制/转发的 JSON 文件里。用独立 DTO 而不是「拷一份 UserSettings 再把 key 清空」，
 * 是为了让「导出文件里不可能有凭据」成为**结构上的保证**，而不是依赖调用方记得清空。
 */
@Serializable
data class UserSettingsBackup(
    val targetExamDate: String,
    val targetSchool: String,
    val targetMajor: String,
    val dailyGoalHours: Float,
    val validStudyThresholdMinutes: Int,
    val defaultSubjectId: String,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val aiProvider: String,
    val aiBaseUrl: String,
    val aiModel: String,
    /**
     * 外观偏好。**必须带默认值**：v1 及更早导出的备份里没有这个字段，
     * 缺省值让它们仍能正常反序列化（导入后回落到跟随系统）。
     */
    val themeMode: String = "SYSTEM",
    /** 学习伙伴主题；旧备份缺失时默认恢复为卷卷。 */
    val mascotTheme: String = "CLOUD"
) {
    fun toDomain(): UserSettings = UserSettings(
        targetExamDate = targetExamDate,
        targetSchool = targetSchool,
        targetMajor = targetMajor,
        dailyGoalHours = dailyGoalHours,
        validStudyThresholdMinutes = validStudyThresholdMinutes,
        defaultSubjectId = defaultSubjectId,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        aiProvider = aiProvider,
        aiBaseUrl = aiBaseUrl,
        aiApiKey = "",
        aiModel = aiModel,
        themeMode = themeMode,
        mascotTheme = mascotTheme
    )

    companion object {
        fun fromDomain(settings: UserSettings): UserSettingsBackup = UserSettingsBackup(
            targetExamDate = settings.targetExamDate,
            targetSchool = settings.targetSchool,
            targetMajor = settings.targetMajor,
            dailyGoalHours = settings.dailyGoalHours,
            validStudyThresholdMinutes = settings.validStudyThresholdMinutes,
            defaultSubjectId = settings.defaultSubjectId,
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            aiProvider = settings.aiProvider,
            aiBaseUrl = settings.aiBaseUrl,
            aiModel = settings.aiModel,
            themeMode = settings.themeMode,
            mascotTheme = settings.mascotTheme
        )
    }
}

/**
 * 完整备份文件的结构。
 *
 * [schemaVersion] 是**备份格式自己的版本号**，与 Room 数据库版本解耦：
 * 数据库迁移服务的是「已安装应用内的数据升级」，备份格式服务的是「文件在设备之间流转」，
 * 两者的演进节奏不同，绑在一起会让任意一方的小改动都强迫另一方做兼容处理。
 *
 * 所有集合都给了默认值：读取**旧版本**备份时缺失的字段会自动补默认值，
 * 因此 `ignoreUnknownKeys = true` + 默认值组合可以让新旧文件互相可用。
 */
@Serializable
data class YanjiBackup(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val appVersionName: String = "",
    val settings: UserSettingsBackup? = null,
    val focusSessions: List<FocusSession> = emptyList(),
    val examSessions: List<ExamSession> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val chatSessions: List<ChatSession> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val unlockedAchievements: Map<String, Long> = emptyMap(),
    val quickStartPresets: List<QuickStartPreset> = emptyList()
) {
    /** 备份里是否一条业务数据都没有（合法的「清空后导出」状态）。 */
    val isEmpty: Boolean
        get() = focusSessions.isEmpty() &&
            examSessions.isEmpty() &&
            journalEntries.isEmpty() &&
            chatSessions.isEmpty() &&
            chatMessages.isEmpty() &&
            checkIns.isEmpty() &&
            unlockedAchievements.isEmpty() &&
            quickStartPresets.isEmpty() &&
            settings == null

    fun countsSummary(): String = buildString {
        append("专注 ${focusSessions.size} · 模考 ${examSessions.size} · 日记 ${journalEntries.size}")
        append(" · 对话 ${chatSessions.size}/${chatMessages.size}")
        append(" · 打卡 ${checkIns.size} · 成就 ${unlockedAchievements.size}")
        append(" · 快捷 ${quickStartPresets.size}")
    }

    companion object {
        /** 备份格式版本。改动 [YanjiBackup] 的字段语义时递增。 */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** 解码结果。失败时给出**面向用户可读**的原因，而不是异常栈。 */
sealed interface BackupDecodeResult {
    data class Success(
        val backup: YanjiBackup,
        val warnings: List<String> = emptyList()
    ) : BackupDecodeResult

    data class Failure(val message: String) : BackupDecodeResult
}

/** 导入结果。 */
sealed interface BackupImportResult {
    data class Success(
        val restored: YanjiBackup,
        val snapshotPath: String?,
        val warnings: List<String> = emptyList()
    ) : BackupImportResult

    data class Failure(val message: String) : BackupImportResult
}
