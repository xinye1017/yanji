package com.example.yanji.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Room projection used by statistics screens; aggregation stays in SQLite. */
data class StudySubjectAggregateRow(
    val subjectId: String,
    val subjectName: String,
    val durationSeconds: Long,
    val sessionCount: Int,
    val longestSessionSeconds: Long
)

/**
 * 回顾窗口聚合所需的最小专注投影。
 *
 * 只取四列而不是整行：区间可能覆盖整月甚至跨月，整行 `SELECT *` 会把 `note`、
 * `mode` 等与统计无关的字符串一起搬过 CursorWindow，纯属浪费。
 */
data class FocusStatsRow(
    val startTime: Long,
    val subjectId: String,
    val subjectName: String,
    val durationSeconds: Long
)

/** 回顾窗口聚合所需的最小模考投影；口径与 [FocusStatsRow] 相同。 */
data class ExamStatsRow(
    val startTime: Long,
    val subjectId: String,
    val subjectName: String,
    val actualDurationSeconds: Long
)

/**
 * 计划 id → 已投入秒数。聚合留在 SQLite，UI 只拿结果做「实际 / 计划」对照。
 *
 * 不按日期过滤：关联本身就是精确的，一条计划只属于某一天，它的时段全算它的。
 */
data class TaskActualSecondsRow(
    val taskId: String,
    val seconds: Long
)

/** A dated task together with its completed focus time across every session date. */
data class StudyTaskActualSecondsRow(
    @Embedded(prefix = "task_")
    val task: StudyTaskEntity,
    val actualSeconds: Long
)

/** A carry-over candidate together with completed focus accumulated across all dates. */
data class CarryOverTaskRow(
    @Embedded(prefix = "task_")
    val task: StudyTaskEntity,
    val actualSeconds: Long
)

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<FocusSessionEntity?>

    @Query(
        "SELECT startTime, subjectId, subjectName, durationSeconds FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive"
    )
    suspend fun getCompletedStatsRows(
        startInclusive: Long,
        endExclusive: Long
    ): List<FocusStatsRow>

    /**
     * 与 [startInclusive, endExclusive) 有交集的已完成专注。
     *
     * 与「按开始时间归属」的统计查询不同，这里按时间区间相交：
     * 一条 23:40→00:10 的专注会同时出现在前一天和后一天的查询里。每日快照只用
     * 它计算「参与次数」；时间轴与总时长仍按开始日期归属，因此同一条历史记录
     * 只显示一次、只累计一次。
     *
     * `endTime` 缺失的历史行回退为 `startTime + durationSeconds * 1000`，与 JS
     * 侧绘制节奏图时的回退口径一致。
     */
    @Query(
        "SELECT * FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND startTime < :endExclusive " +
            "AND (CASE WHEN endTime > startTime THEN endTime ELSE startTime + durationSeconds * 1000 END) > :startInclusive " +
            "ORDER BY startTime DESC"
    )
    suspend fun getCompletedOverlapping(startInclusive: Long, endExclusive: Long): List<FocusSessionEntity>

    @Query(
        "SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive"
    )
    fun observeTotalSeconds(startInclusive: Long, endExclusive: Long): Flow<Long>

    @Query(
        "SELECT taskId, COALESCE(SUM(durationSeconds), 0) AS seconds " +
            "FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND taskId IS NOT NULL " +
            "GROUP BY taskId"
    )
    fun observeTaskActualSeconds(): Flow<List<TaskActualSecondsRow>>

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun count(): Int

    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    suspend fun getAllOnce(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :sinceEpochMs ORDER BY startTime DESC")
    suspend fun getSessionsSince(sinceEpochMs: Long): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<FocusSessionEntity>)

    @Query("UPDATE focus_sessions SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String): Int

    @Query("UPDATE focus_sessions SET subjectName = :newName WHERE subjectId = :subjectId")
    suspend fun updateSubjectName(subjectId: String, newName: String)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}

@Dao
interface ExamSessionDao {
    @Query("SELECT * FROM exam_sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<ExamSessionEntity>>

    @Query("SELECT * FROM exam_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExamSessionEntity?

    @Query("SELECT * FROM exam_sessions WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<ExamSessionEntity?>

    /** 每日快照按开始时间归属读取窗口内的完整模考行。 */
    @Query(
        "SELECT * FROM exam_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive " +
            "ORDER BY startTime DESC"
    )
    suspend fun getCompletedInRange(startInclusive: Long, endExclusive: Long): List<ExamSessionEntity>

    @Query(
        "SELECT startTime, subjectId, subjectName, actualDurationSeconds FROM exam_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive"
    )
    suspend fun getCompletedStatsRows(
        startInclusive: Long,
        endExclusive: Long
    ): List<ExamStatsRow>

    @Query(
        "SELECT COALESCE(SUM(actualDurationSeconds), 0) FROM exam_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive"
    )
    fun observeTotalSeconds(startInclusive: Long, endExclusive: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM exam_sessions")
    suspend fun count(): Int

    @Query("SELECT * FROM exam_sessions ORDER BY startTime DESC")
    suspend fun getAllOnce(): List<ExamSessionEntity>

    @Query("SELECT * FROM exam_sessions WHERE startTime >= :sinceEpochMs ORDER BY startTime DESC")
    suspend fun getSessionsSince(sinceEpochMs: Long): List<ExamSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ExamSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<ExamSessionEntity>)

    @Query("UPDATE exam_sessions SET subjectName = :newName WHERE subjectId = :subjectId")
    suspend fun updateSubjectName(subjectId: String, newName: String)

    @Query("DELETE FROM exam_sessions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM exam_sessions")
    suspend fun deleteAll()
}

@Dao
interface NoteEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY date DESC, createdAt DESC")
    fun getAll(): Flow<List<NoteEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<NoteEntryEntity?>

    @Query("SELECT * FROM journal_entries WHERE date = :date ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByDate(date: String): NoteEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE date = :date ORDER BY createdAt ASC")
    suspend fun getAllByDate(date: String): List<NoteEntryEntity>

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM journal_entries ORDER BY date DESC, createdAt DESC")
    suspend fun getAllOnce(): List<NoteEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: NoteEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NoteEntryEntity>)

    /**
     * 在数据库内原子切换收藏，避免使用尚未回灌的内存状态计算目标值。
     * 只改 isFavorite 与 updatedAt；返回受影响行数，0 表示记录不存在。
     */
    @Query("UPDATE journal_entries SET isFavorite = 1 - isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleFavorite(id: String, updatedAt: Long): Int

    /**
     * 更新正文。只改 content 与 updatedAt，不触碰 createdAt、收藏状态与归属日期。
     * 返回受影响行数：0 表示 id 不存在。
     */
    @Query("UPDATE journal_entries SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, updatedAt: Long): Int

    /**
     * 更新疑点解决状态。只改 isResolved、resolvedAt 与 updatedAt。
     * 返回受影响行数：0 表示 id 不存在。
     */
    @Query("UPDATE journal_entries SET isResolved = :isResolved, resolvedAt = :resolvedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setResolved(id: String, isResolved: Int, resolvedAt: Long?, updatedAt: Long): Int

    /**
     * 人工纠正分类（灵感 ⇄ 疑点）。
     *
     * 解决状态只对疑点有意义，所以**分类真的发生变化时**一并清空 `isResolved` /
     * `resolvedAt`：刚被纠正成灵感的记录不该留着"已解决"的旧结论，刚被纠正成疑点的
     * 记录也不可能已经解决。规则写进同一条语句，避免出现"灵感却标记为已解决"的矛盾行。
     *
     * 写成 `CASE WHEN category = :category` 是有意的：分类未变时保持解决状态原样，
     * 于是这条语句可以被无条件重放（前端保存时不必先比较旧值，重试也不会把用户
     * 已经勾掉的"已解决"再抹掉一次）。SQLite 的 SET 右侧一律按**旧行**求值，
     * 所以三处赋值互不影响。
     *
     * 返回受影响行数：0 表示 id 不存在。
     */
    @Query(
        "UPDATE journal_entries SET " +
            "isResolved = CASE WHEN category = :category THEN isResolved ELSE 0 END, " +
            "resolvedAt = CASE WHEN category = :category THEN resolvedAt ELSE NULL END, " +
            "category = :category, " +
            "updatedAt = CASE WHEN category = :category THEN updatedAt ELSE :updatedAt END " +
            "WHERE id = :id"
    )
    suspend fun setCategory(id: String, category: String, updatedAt: Long): Int

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT COUNT(*) FROM user_settings")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettingsEntity)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: String): Flow<List<ChatMessageEntity>>

    /** First screen and load-more query. DESC lets SQLite stop at LIMIT using the composite index. */
    @Query(
        "SELECT * FROM chat_messages WHERE sessionId = :sessionId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit"
    )
    fun observeRecentBySessionId(sessionId: String, limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    fun observeCountBySessionId(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND sender = 'USER'")
    suspend fun countUserMessages(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatSessionEntity?

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<ChatSessionEntity>)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET model = :model, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateModel(id: String, model: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAll()
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY date DESC")
    fun getAllFlow(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins ORDER BY date DESC")
    suspend fun getAllOnce(): List<CheckInEntity>

    @Query("SELECT COUNT(*) FROM check_ins")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: CheckInEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checkIns: List<CheckInEntity>)

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM unlocked_achievements")
    fun getAllFlow(): Flow<List<UnlockedAchievementEntity>>

    @Query("SELECT id FROM unlocked_achievements")
    suspend fun getUnlockedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(item: UnlockedAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlockAll(items: List<UnlockedAchievementEntity>)

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM unlocked_achievements")
    suspend fun deleteAll()
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, name ASC")
    fun getAllFlow(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<SubjectEntity>)

    @Query("UPDATE subjects SET name = :newName WHERE id = :id")
    suspend fun updateName(id: String, newName: String)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 删除某类目下的全部子学科。 */
    @Query("DELETE FROM subjects WHERE parentId = :parentId")
    suspend fun deleteChildrenOf(parentId: String)

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}

@Dao
interface QuickStartPresetDao {
    @Query("SELECT * FROM quick_start_presets ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllFlow(): Flow<List<QuickStartPresetEntity>>

    @Query("SELECT COUNT(*) FROM quick_start_presets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: QuickStartPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<QuickStartPresetEntity>)

    @Query("DELETE FROM quick_start_presets WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM quick_start_presets")
    suspend fun deleteAll()
}

@Dao
interface StudyTaskDao {
    @Query(
        "SELECT * FROM study_tasks WHERE date = :date " +
            "ORDER BY isCompleted ASC, createdAt ASC"
    )
    fun observeByDate(date: String): Flow<List<StudyTaskEntity>>

    @Query(
        "SELECT " +
            "t.id AS task_id, t.date AS task_date, t.subjectId AS task_subjectId, " +
            "t.subjectName AS task_subjectName, t.title AS task_title, " +
            "t.plannedMinutes AS task_plannedMinutes, t.isCompleted AS task_isCompleted, " +
            "t.carriedOverToId AS task_carriedOverToId, " +
            "t.createdAt AS task_createdAt, t.updatedAt AS task_updatedAt, " +
            "COALESCE(SUM(f.durationSeconds), 0) AS actualSeconds " +
            "FROM study_tasks AS t " +
            "LEFT JOIN focus_sessions AS f " +
            "ON f.taskId = t.id AND f.status = 'COMPLETED' " +
            "WHERE t.date = :date " +
            "GROUP BY t.id, t.date, t.subjectId, t.subjectName, t.title, " +
            "t.plannedMinutes, t.isCompleted, t.carriedOverToId, t.createdAt, t.updatedAt " +
            "ORDER BY t.isCompleted ASC, t.createdAt ASC"
    )
    suspend fun getByDateWithActualSeconds(date: String): List<StudyTaskActualSecondsRow>

    /**
     * 最近 7 天内仍有希望继续的计划：不含今天，包含今天减去 7 天。
     *
     * 完成、已承接与更早的任务都不再进入待处理区；实际投入仍按任务 id 汇总，
     * 不按日期截断跨日专注。
     */
    @Query(
        "SELECT " +
            "t.id AS task_id, t.date AS task_date, t.subjectId AS task_subjectId, " +
            "t.subjectName AS task_subjectName, t.title AS task_title, " +
            "t.plannedMinutes AS task_plannedMinutes, t.isCompleted AS task_isCompleted, " +
            "t.carriedOverToId AS task_carriedOverToId, " +
            "t.createdAt AS task_createdAt, t.updatedAt AS task_updatedAt, " +
            "COALESCE(SUM(f.durationSeconds), 0) AS actualSeconds " +
            "FROM study_tasks AS t " +
            "LEFT JOIN focus_sessions AS f " +
            "ON f.taskId = t.id AND f.status = 'COMPLETED' " +
            "WHERE t.date >= :earliestDate AND t.date < :today " +
            "AND t.isCompleted = 0 AND t.carriedOverToId IS NULL " +
            "GROUP BY t.id, t.date, t.subjectId, t.subjectName, t.title, " +
            "t.plannedMinutes, t.isCompleted, t.carriedOverToId, t.createdAt, t.updatedAt " +
            "ORDER BY t.date DESC, t.createdAt ASC"
    )
    suspend fun getCarryOverWithActualSeconds(
        earliestDate: String,
        today: String
    ): List<CarryOverTaskRow>

    @Query("SELECT * FROM study_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StudyTaskEntity?

    @Query("SELECT * FROM study_tasks ORDER BY date DESC, createdAt ASC")
    suspend fun getAllOnce(): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks ORDER BY date DESC, createdAt ASC")
    fun getAllFlow(): Flow<List<StudyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: StudyTaskEntity)

    /**
     * 承接事务使用 ABORT：失败必须抛出并回滚同事务内的来源链接，
     * 不能被 REPLACE 静默覆盖。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCarryOver(task: StudyTaskEntity)

    /** 原位更新保留任务 ID 和历史专注关联，不采用删除再创建。 */
    @Query("UPDATE study_tasks SET title = :title, plannedMinutes = :plannedMinutes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDetails(id: String, title: String, plannedMinutes: Int, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<StudyTaskEntity>)

    @Query("UPDATE study_tasks SET isCompleted = :completed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: String, completed: Boolean, updatedAt: Long): Int

    /**
     * 以条件更新占用来源任务，返回 0 表示已完成、已承接、超出窗口或不存在。
     * 并发承接时 SQLite 只允许其中一次把 NULL 改成目标 id。
     */
    @Query(
        "UPDATE study_tasks SET carriedOverToId = :destinationId, updatedAt = :updatedAt " +
            "WHERE id = :sourceId AND date >= :earliestDate AND date < :today " +
            "AND isCompleted = 0 AND carriedOverToId IS NULL"
    )
    suspend fun linkCarryOverSource(
        sourceId: String,
        destinationId: String,
        earliestDate: String,
        today: String,
        updatedAt: Long
    ): Int

    @Query("UPDATE study_tasks SET subjectName = :newName WHERE subjectId = :subjectId")
    suspend fun updateSubjectName(subjectId: String, newName: String)

    /** 删除承接副本前先解除来源链接，避免来源任务永久退出待处理区。 */
    @Query(
        "UPDATE study_tasks SET carriedOverToId = NULL, updatedAt = :updatedAt " +
            "WHERE carriedOverToId = :destinationId"
    )
    suspend fun releaseCarryOverSources(destinationId: String, updatedAt: Long): Int

    /**
     * 修复已经存在的悬空承接链接：只解除指向不存在任务的来源。
     *
     * 这是幂等的数据完整性清理，不改动来源日期、完成状态或专注关联。
     */
    @Query(
        "UPDATE study_tasks SET carriedOverToId = NULL, updatedAt = :updatedAt " +
            "WHERE carriedOverToId IS NOT NULL " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM study_tasks AS destination " +
            "WHERE destination.id = study_tasks.carriedOverToId" +
            ")"
    )
    suspend fun releaseDanglingCarryOverLinks(updatedAt: Long): Int

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** 仅用于备份恢复 / 全新备考旅程。 */
    @Query("DELETE FROM study_tasks")
    suspend fun deleteAll()
}


@Dao
interface AiAnalysisDao {
    @Query("SELECT * FROM ai_analyses ORDER BY createdAt DESC")
    fun getAll(): Flow<List<AiAnalysisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: AiAnalysisEntity)

    /** 随「全新备考旅程」重置一并清除：诊断是基于学习记录派生的，不单独保留。 */
    @Query("DELETE FROM ai_analyses")
    suspend fun deleteAll()
}
