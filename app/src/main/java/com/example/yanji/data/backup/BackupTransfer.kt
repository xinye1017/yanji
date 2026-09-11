package com.example.yanji.data.backup

import androidx.room.withTransaction
import com.example.yanji.data.db.*
import kotlinx.coroutines.flow.first

/**
 * 备份的「采集」与「整表替换」两个纯数据操作。
 *
 * 之所以从 `YanjiRepository` 里抽出来：这两段逻辑是备份功能的核心，也是唯一会**破坏性**
 * 改写用户数据的地方，必须能被插桩测试针对一个**一次性数据库**完整验证——
 * 直接在 Repository 上测会打到用户的真实数据库，测试本身就是事故。
 *
 * 这里的两个方法都以 [YanjiDatabase] 作为参数，因此测试可以传入自己的临时库。
 */
internal object BackupTransfer {

    /** 采集全库快照。只读，不修改任何数据。 */
    suspend fun collect(
        db: YanjiDatabase,
        appVersionName: String,
        now: Long = System.currentTimeMillis()
    ): YanjiBackup = YanjiBackup(
        exportedAt = now,
        appVersionName = appVersionName,
        settings = db.userSettingsDao().getSettings().first()
            ?.let { UserSettingsBackup.fromDomain(it.toDomainModel()) },
        focusSessions = db.focusSessionDao().getAll().first().map { it.toDomainModel() },
        examSessions = db.examSessionDao().getAll().first().map { it.toDomainModel() },
        journalEntries = db.journalEntryDao().getAll().first().map { it.toDomainModel() },
        chatSessions = db.chatSessionDao().getAll().first().map { it.toDomainModel() },
        chatMessages = db.chatMessageDao().getAll().first().map { it.toDomainModel() },
        checkIns = db.checkInDao().getAllFlow().first().map { it.toDomainModel() },
        unlockedAchievements = db.achievementDao().getAllFlow().first().associate { it.id to it.unlockedAt },
        quickStartPresets = db.quickStartPresetDao().getAllFlow().first().map { it.toDomainModel() }
    )

    /**
     * 整表替换。**必须在事务内调用**；调用方负责 `db.withTransaction { }`。
     *
     * 语义：备份里有几条就写几条——包括"一条都没有"，那意味着清空本机记录。
     * 这是「删空后导出」这种合法状态的自然结果。
     */
    suspend fun apply(db: YanjiDatabase, backup: YanjiBackup) {
        db.focusSessionDao().deleteAll()
        if (backup.focusSessions.isNotEmpty()) {
            db.focusSessionDao().insertAll(backup.focusSessions.map { FocusSessionEntity.fromDomainModel(it) })
        }

        db.examSessionDao().deleteAll()
        if (backup.examSessions.isNotEmpty()) {
            db.examSessionDao().insertAll(backup.examSessions.map { ExamSessionEntity.fromDomainModel(it) })
        }

        db.journalEntryDao().deleteAll()
        if (backup.journalEntries.isNotEmpty()) {
            db.journalEntryDao().insertAll(backup.journalEntries.map { JournalEntryEntity.fromDomainModel(it) })
        }

        db.chatMessageDao().clearAll()
        db.chatSessionDao().clearAll()
        if (backup.chatSessions.isNotEmpty()) {
            db.chatSessionDao().insertAll(backup.chatSessions.map { ChatSessionEntity.fromDomainModel(it) })
        }
        if (backup.chatMessages.isNotEmpty()) {
            db.chatMessageDao().insertAll(backup.chatMessages.map { ChatMessageEntity.fromDomainModel(it) })
        }

        db.checkInDao().deleteAll()
        if (backup.checkIns.isNotEmpty()) {
            db.checkInDao().insertAll(backup.checkIns.map { CheckInEntity.fromDomainModel(it) })
        }

        db.achievementDao().deleteAll()
        if (backup.unlockedAchievements.isNotEmpty()) {
            db.achievementDao().unlockAll(
                backup.unlockedAchievements.map { (id, unlockedAt) ->
                    UnlockedAchievementEntity(id = id, unlockedAt = unlockedAt)
                }
            )
        }

        db.quickStartPresetDao().deleteAll()
        if (backup.quickStartPresets.isNotEmpty()) {
            db.quickStartPresetDao().insertAll(
                backup.quickStartPresets.map { QuickStartPresetEntity.fromDomainModel(it) }
            )
        }

        backup.settings?.let {
            db.userSettingsDao().saveSettings(UserSettingsEntity.fromDomainModel(it.toDomain()))
        }
    }

    /** 在单个事务里执行 [apply]。失败会整体回滚，不会留下"一半新一半旧"的库。 */
    suspend fun applyInTransaction(db: YanjiDatabase, backup: YanjiBackup) {
        db.withTransaction { apply(db, backup) }
    }
}
