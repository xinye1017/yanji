package com.example.yanji.data.backup

import com.example.yanji.data.CheckIn
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSession
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.ScheduleBlockCompletion
import com.example.yanji.data.ScheduleDayAssignment
import com.example.yanji.data.ScheduleTemplate
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.data.UserSettings
import kotlinx.serialization.SerialName
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
    val aiProtocol: String = "OPENAI_CHAT",
    /**
     * 外观偏好。**必须带默认值**：v1 及更早导出的备份里没有这个字段，
     * 缺省值让它们仍能正常反序列化（导入后回落到跟随系统）。
     */
    val themeMode: String = "SYSTEM",
    /** 学习伙伴主题；旧备份缺失时默认恢复为卷卷。 */
    val mascotTheme: String = "CLOUD",
    /** 决策模型配置；v3 新字段，旧备份缺失时使用当前产品默认值，并在预览中提醒。 */
    val decisionProvider: String = "openrouter",
    val decisionModel: String = "respan/span-01-lite:free",
    val decisionEndpoint: String = "https://openrouter.ai/api/alpha/decisions"
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
        aiProtocol = aiProtocol,
        themeMode = themeMode,
        mascotTheme = mascotTheme,
        decisionProvider = decisionProvider,
        decisionModel = decisionModel,
        decisionEndpoint = decisionEndpoint
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
            aiProtocol = settings.aiProtocol,
            themeMode = settings.themeMode,
            mascotTheme = settings.mascotTheme,
            decisionProvider = settings.decisionProvider,
            decisionModel = settings.decisionModel,
            decisionEndpoint = settings.decisionEndpoint
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
 * 集合默认值只服务 v1 的字段补全；导入前仍由 [BackupCodec] 校验完整 envelope。
 */
@Serializable
data class YanjiBackup(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val appVersionName: String = "",
    val settings: UserSettingsBackup? = null,
    val focusSessions: List<FocusSession> = emptyList(),
    val examSessions: List<ExamSession> = emptyList(),
    /**
     * 日记（随笔）条目。
     *
     * JSON 键**刻意保持 `journalEntries`**：这是已导出备份文件的线上格式，
     * 且该字段带默认值——改名会让用户手里的旧备份在导入时静默变成空列表。
     * Kotlin 侧属性名随模块更名为 note，线上格式由 @SerialName 显式钉住。
     */
    @SerialName("journalEntries")
    val noteEntries: List<NoteEntry> = emptyList(),
    val chatSessions: List<ChatSession> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val quickStartPresets: List<QuickStartPreset> = emptyList(),
    val studyTasks: List<StudyTask> = emptyList(),
    /**
     * 用户自定义后的完整学科列表。
     *
     * 带默认值：v1 备份没有这个字段，导入时**不覆盖**本机学科。
     * 空列表同样不覆盖本机学科，与原生预览和 UI 的提示保持一致。
     */
    val subjects: List<Subject> = emptyList(),
    /**
     * 每日时间表（v4 新增）。
     *
     * 三个列表带默认值：v3 及更早的备份没有这些字段。恢复旧备份时**保留本机时间表**，
     * 不把「旧文件没有这个字段」误读成「用户删光了模板」。v4 文件里的空列表才是显式清空。
     */
    val scheduleTemplates: List<ScheduleTemplate> = emptyList(),
    val scheduleDayAssignments: List<ScheduleDayAssignment> = emptyList(),
    val scheduleBlockCompletions: List<ScheduleBlockCompletion> = emptyList()
) {
    /** 备份里是否一条业务数据都没有（合法的「清空后导出」状态）。 */
    val isEmpty: Boolean
        get() = focusSessions.isEmpty() &&
            examSessions.isEmpty() &&
            noteEntries.isEmpty() &&
            chatSessions.isEmpty() &&
            chatMessages.isEmpty() &&
            checkIns.isEmpty() &&
            quickStartPresets.isEmpty() &&
            studyTasks.isEmpty() &&
            subjects.isEmpty() &&
            scheduleTemplates.isEmpty() &&
            scheduleDayAssignments.isEmpty() &&
            scheduleBlockCompletions.isEmpty() &&
            settings == null

    companion object {
        /** 备份格式版本。改动 [YanjiBackup] 的字段语义时递增。 */
        const val CURRENT_SCHEMA_VERSION = 4
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
