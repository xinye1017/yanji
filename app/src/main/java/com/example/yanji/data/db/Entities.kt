package com.example.yanji.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.yanji.data.*

@Entity(
    tableName = "focus_sessions",
    indices = [Index("startTime")]
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val subjectName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val pausedDurationSeconds: Long = 0,
    val pauseCount: Int = 0,
    val mode: String = "正向计时",
    val note: String = "",
    val status: String = "COMPLETED",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): FocusSession {
        return FocusSession(
            id = id,
            subjectId = subjectId,
            subjectName = subjectName,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            pausedDurationSeconds = pausedDurationSeconds,
            pauseCount = pauseCount,
            mode = mode,
            note = note,
            status = try { SessionStatus.valueOf(status) } catch (e: Exception) { SessionStatus.COMPLETED },
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomainModel(model: FocusSession): FocusSessionEntity {
            return FocusSessionEntity(
                id = model.id,
                subjectId = model.subjectId,
                subjectName = model.subjectName,
                startTime = model.startTime,
                endTime = model.endTime,
                durationSeconds = model.durationSeconds,
                pausedDurationSeconds = model.pausedDurationSeconds,
                pauseCount = model.pauseCount,
                mode = model.mode,
                note = model.note,
                status = model.status.name,
                createdAt = model.createdAt
            )
        }
    }
}

@Entity(
    tableName = "exam_sessions",
    indices = [Index("startTime")]
)
data class ExamSessionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val subjectName: String,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long,
    val startTime: Long,
    val endTime: Long,
    val score: Double?,
    val maxScore: Double,
    val note: String,
    val status: String,
    val createdAt: Long
) {
    fun toDomainModel(): ExamSession {
        return ExamSession(
            id = id,
            subjectId = subjectId,
            subjectName = subjectName,
            plannedDurationSeconds = plannedDurationSeconds,
            actualDurationSeconds = actualDurationSeconds,
            startTime = startTime,
            endTime = endTime,
            score = score,
            maxScore = maxScore,
            note = note,
            status = try { SessionStatus.valueOf(status) } catch (e: Exception) { SessionStatus.COMPLETED },
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomainModel(model: ExamSession): ExamSessionEntity {
            return ExamSessionEntity(
                id = model.id,
                subjectId = model.subjectId,
                subjectName = model.subjectName,
                plannedDurationSeconds = model.plannedDurationSeconds,
                actualDurationSeconds = model.actualDurationSeconds,
                startTime = model.startTime,
                endTime = model.endTime,
                score = model.score,
                maxScore = model.maxScore,
                note = model.note,
                status = model.status.name,
                createdAt = model.createdAt
            )
        }
    }
}

@Entity(
    tableName = "journal_entries",
    // v15 起 date 不再是唯一键：一天允许多篇随笔。保留普通索引供按日期分组/排序使用。
    indices = [Index(value = ["date"], unique = false)]
)
data class NoteEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val title: String,
    val content: String,
    val moodScore: Int,
    val energyScore: Int,
    val studySatisfaction: Int,
    val tomorrowPlan: String,
    val blockers: String, // 遇到的困难 / 卡点（v10 新增）
    val tags: String, // Comma separated
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Int = 0, // v15 新增：0/1，随笔收藏标记
    val isDraft: Int = 0 // v16 新增：0/1，随笔草稿标记（0=已保存，1=草稿）
) {
    fun toDomainModel(): NoteEntry {
        return NoteEntry(
            id = id,
            date = date,
            title = title,
            content = content,
            moodScore = moodScore,
            energyScore = energyScore,
            studySatisfaction = studySatisfaction,
            tomorrowPlan = tomorrowPlan,
            blockers = blockers,
            tags = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() },
            createdAt = createdAt,
            updatedAt = updatedAt,
            isFavorite = isFavorite != 0,
            isDraft = isDraft != 0
        )
    }

    companion object {
        fun fromDomainModel(model: NoteEntry): NoteEntryEntity {
            return NoteEntryEntity(
                id = model.id,
                date = model.date,
                title = model.title,
                content = model.content,
                moodScore = model.moodScore,
                energyScore = model.energyScore,
                studySatisfaction = model.studySatisfaction,
                tomorrowPlan = model.tomorrowPlan,
                blockers = model.blockers,
                tags = model.tags.joinToString(","),
                createdAt = model.createdAt,
                updatedAt = model.updatedAt,
                isFavorite = if (model.isFavorite) 1 else 0,
                isDraft = if (model.isDraft) 1 else 0
            )
        }
    }
}

/**
 * 用户设置。**不含 AI API Key**：
 * 凭据由 [com.example.yanji.data.security.SecretStore] 单独保存（Keystore 加密 + no-backup 目录），
 * 避免与可云备份的学习数据共享备份生命周期。
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
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
    val themeMode: String,
    val mascotTheme: String
) {
    fun toDomainModel(): UserSettings {
        return UserSettings(
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
    }

    companion object {
        fun fromDomainModel(model: UserSettings): UserSettingsEntity {
            return UserSettingsEntity(
                id = 1,
                targetExamDate = model.targetExamDate,
                targetSchool = model.targetSchool,
                targetMajor = model.targetMajor,
                dailyGoalHours = model.dailyGoalHours,
                validStudyThresholdMinutes = model.validStudyThresholdMinutes,
                defaultSubjectId = model.defaultSubjectId,
                soundEnabled = model.soundEnabled,
                vibrationEnabled = model.vibrationEnabled,
                aiProvider = model.aiProvider,
                aiBaseUrl = model.aiBaseUrl,
                aiModel = model.aiModel,
                themeMode = model.themeMode,
                mascotTheme = model.mascotTheme
            )
        }
    }
}

@Entity(
    tableName = "chat_messages",
    indices = [Index("sessionId"), Index("timestamp"), Index(value = ["sessionId", "timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String = "session-initial",
    val sender: String, // "USER" or "JUANJUAN"
    val content: String,
    val timestamp: Long
) {
    fun toDomainModel(): ChatMessage {
        return ChatMessage(
            id = id,
            sessionId = sessionId,
            sender = if (sender == "USER") ChatSender.USER else ChatSender.JUANJUAN,
            content = content,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromDomainModel(model: ChatMessage): ChatMessageEntity {
            return ChatMessageEntity(
                id = model.id,
                sessionId = model.sessionId,
                sender = model.sender.name,
                content = model.content,
                timestamp = model.timestamp
            )
        }
    }
}

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String
) {
    fun toDomainModel(): ChatSession {
        return ChatSession(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            model = model
        )
    }

    companion object {
        fun fromDomainModel(model: ChatSession): ChatSessionEntity {
            return ChatSessionEntity(
                id = model.id,
                title = model.title,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt,
                model = model.model
            )
        }
    }
}

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val date: String,
    val checkInTime: Long,
    val streak: Int,
    val note: String = "",
    val mood: String = ""
) {
    fun toDomainModel(): CheckIn {
        return CheckIn(
            date = date,
            checkInTime = checkInTime,
            streak = streak,
            note = note,
            mood = mood
        )
    }

    companion object {
        fun fromDomainModel(model: CheckIn): CheckInEntity {
            return CheckInEntity(
                date = model.date,
                checkInTime = model.checkInTime,
                streak = model.streak,
                note = model.note,
                mood = model.mood
            )
        }
    }
}

@Entity(tableName = "unlocked_achievements")
data class UnlockedAchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long
)

@Entity(
    tableName = "subjects",
    indices = [Index("parentId"), Index("sortOrder")]
)
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val parentId: String? = null
) {
    fun toDomainModel(): Subject {
        return Subject(
            id = id,
            name = name,
            colorHex = colorHex,
            sortOrder = sortOrder,
            enabled = enabled,
            parentId = parentId
        )
    }

    companion object {
        fun fromDomainModel(model: Subject): SubjectEntity {
            return SubjectEntity(
                id = model.id,
                name = model.name,
                colorHex = model.colorHex,
                sortOrder = model.sortOrder,
                enabled = model.enabled,
                parentId = model.parentId
            )
        }
    }
}

@Entity(
    tableName = "quick_start_presets",
    indices = [Index("sortOrder")]
)
data class QuickStartPresetEntity(
    @PrimaryKey val id: String,
    val type: String = QuickStartPreset.TYPE_CUSTOM,
    val label: String,
    val subLabel: String = "",
    val subjectId: String = "math_advanced",
    val subjectName: String = "高等数学",
    val mode: String = FocusModes.COUNT_UP,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
) {
    fun toDomainModel(): QuickStartPreset {
        return QuickStartPreset(
            id = id,
            type = type,
            label = label,
            subLabel = subLabel,
            subjectId = subjectId,
            subjectName = subjectName,
            mode = mode,
            note = note,
            createdAt = createdAt,
            sortOrder = sortOrder
        )
    }

    companion object {
        fun fromDomainModel(model: QuickStartPreset): QuickStartPresetEntity {
            return QuickStartPresetEntity(
                id = model.id,
                type = model.type,
                label = model.label,
                subLabel = model.subLabel,
                subjectId = model.subjectId,
                subjectName = model.subjectName,
                mode = model.mode,
                note = model.note,
                createdAt = model.createdAt,
                sortOrder = model.sortOrder
            )
        }
    }
}
