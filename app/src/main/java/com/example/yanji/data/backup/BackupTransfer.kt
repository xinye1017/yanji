package com.example.yanji.data.backup

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
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
    ): YanjiBackup = db.useWriterConnection { transactor ->
        transactor.deferredTransaction {
            YanjiBackup(
                exportedAt = now,
                appVersionName = appVersionName,
                settings = db.userSettingsDao().getSettings().first()
                    ?.let { UserSettingsBackup.fromDomain(it.toDomainModel()) },
                focusSessions = db.focusSessionDao().getAll().first().map { it.toDomainModel() },
                examSessions = db.examSessionDao().getAll().first().map { it.toDomainModel() },
                noteEntries = db.noteEntryDao().getAll().first().map { it.toDomainModel() },
                chatSessions = db.chatSessionDao().getAll().first().map { it.toDomainModel() },
                chatMessages = db.chatMessageDao().getAll().first().map { it.toDomainModel() },
                checkIns = db.checkInDao().getAllFlow().first().map { it.toDomainModel() },
                quickStartPresets = db.quickStartPresetDao().getAllFlow().first().map { it.toDomainModel() },
                studyTasks = db.studyTaskDao().getAllFlow().first().map { it.toDomainModel() },
                subjects = db.subjectDao().getAll().map { it.toDomainModel() },
                scheduleTemplates = run {
                    val blocks = db.scheduleDao().getBlocks().groupBy { it.templateId }
                    db.scheduleDao().getTemplates().map { entity ->
                        entity.toDomainModel(blocks[entity.id].orEmpty())
                    }
                },
                scheduleDayAssignments = db.scheduleDao().getAllAssignments().map { it.toDomainModel() },
                scheduleBlockCompletions = db.scheduleDao().getAllCompletions().map { it.toDomainModel() }
            )
        }
    }

    /**
     * 整表替换。**必须在事务内调用**；调用方负责提供事务边界。
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

        db.noteEntryDao().deleteAll()
        if (backup.noteEntries.isNotEmpty()) {
            db.noteEntryDao().insertAll(backup.noteEntries.map { NoteEntryEntity.fromDomainModel(it) })
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

        db.quickStartPresetDao().deleteAll()
        if (backup.quickStartPresets.isNotEmpty()) {
            db.quickStartPresetDao().insertAll(
                backup.quickStartPresets.map { QuickStartPresetEntity.fromDomainModel(it) }
            )
        }

        db.studyTaskDao().deleteAll()
        if (backup.studyTasks.isNotEmpty()) {
            db.studyTaskDao().insertAll(
                backup.studyTasks.map { StudyTaskEntity.fromDomainModel(it) }
            )
        }

        // 旧版备份没有 subjects 字段或列表为空：保持本机学科不动，
        // 与原生预览/UI 的 replacesSubjects 语义一致。
        if (backup.subjects.isNotEmpty()) {
            db.subjectDao().deleteAll()
            db.subjectDao().insertAll(backup.subjects.map { SubjectEntity.fromDomainModel(it) })
        }

        // 时间表是 v4 新增的数据。v3 及更早的备份没有这个字段，恢复时保留本机时间表；
        // 只有 v4 文件才按「备份里有几条就写几条」的整表替换语义处理（含显式清空）。
        if (backup.schemaVersion >= 4) {
            db.scheduleDao().deleteAllCompletions()
            db.scheduleDao().deleteAllAssignments()
            db.scheduleDao().deleteAllBlocks()
            db.scheduleDao().deleteAllTemplates()
            if (backup.scheduleTemplates.isNotEmpty()) {
                db.scheduleDao().insertTemplates(
                    backup.scheduleTemplates.map { ScheduleTemplateEntity.fromDomainModel(it) }
                )
                db.scheduleDao().insertAllBlocks(
                    backup.scheduleTemplates.flatMap { template ->
                        template.blocks.map { ScheduleTemplateBlockEntity.fromDomainModel(it) }
                    }
                )
            }
            if (backup.scheduleDayAssignments.isNotEmpty()) {
                db.scheduleDao().insertAllAssignments(
                    backup.scheduleDayAssignments.map { ScheduleDayAssignmentEntity.fromDomainModel(it) }
                )
            }
            if (backup.scheduleBlockCompletions.isNotEmpty()) {
                db.scheduleDao().insertAllCompletions(
                    backup.scheduleBlockCompletions.map { ScheduleBlockCompletionEntity.fromDomainModel(it) }
                )
            }
        }

        backup.settings?.let {
            db.userSettingsDao().saveSettings(UserSettingsEntity.fromDomainModel(it.toDomain()))
        }
    }

    /** 在单个事务里执行 [apply]。失败会整体回滚，不会留下"一半新一半旧"的库。 */
    suspend fun applyInTransaction(db: YanjiDatabase, backup: YanjiBackup) {
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction { apply(db, backup) }
        }
    }
}
