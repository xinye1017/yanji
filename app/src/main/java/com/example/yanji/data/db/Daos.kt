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

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<FocusSessionEntity?>

    @Query(
        "SELECT * FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive " +
            "ORDER BY startTime DESC"
    )
    fun observeCompletedInRange(startInclusive: Long, endExclusive: Long): Flow<List<FocusSessionEntity>>

    @Query(
        "SELECT subjectId, subjectName, " +
            "COALESCE(SUM(durationSeconds), 0) AS durationSeconds, " +
            "COUNT(*) AS sessionCount, COALESCE(MAX(durationSeconds), 0) AS longestSessionSeconds " +
            "FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive " +
            "GROUP BY subjectId, subjectName"
    )
    fun observeSubjectTotals(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<StudySubjectAggregateRow>>

    @Query(
        "SELECT COALESCE(SUM(durationSeconds), 0) FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive"
    )
    fun observeTotalSeconds(startInclusive: Long, endExclusive: Long): Flow<Long>

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
    suspend fun updateNote(id: String, note: String)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

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

    @Query(
        "SELECT * FROM exam_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive " +
            "ORDER BY startTime DESC"
    )
    fun observeCompletedInRange(startInclusive: Long, endExclusive: Long): Flow<List<ExamSessionEntity>>

    @Query(
        "SELECT subjectId, subjectName, " +
            "COALESCE(SUM(actualDurationSeconds), 0) AS durationSeconds, " +
            "COUNT(*) AS sessionCount, COALESCE(MAX(actualDurationSeconds), 0) AS longestSessionSeconds " +
            "FROM exam_sessions " +
            "WHERE status = 'COMPLETED' AND startTime >= :startInclusive AND startTime < :endExclusive " +
            "GROUP BY subjectId, subjectName"
    )
    fun observeSubjectTotals(
        startInclusive: Long,
        endExclusive: Long
    ): Flow<List<StudySubjectAggregateRow>>

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

    @Query("DELETE FROM exam_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

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

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM journal_entries ORDER BY date DESC, createdAt DESC")
    suspend fun getAllOnce(): List<NoteEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: NoteEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NoteEntryEntity>)

    /**
     * 切换收藏。只改 isFavorite 与 updatedAt，不触碰正文与 createdAt，
     * 保证「初次编辑完毕时间」不因收藏动作而漂移。
     */
    @Query("UPDATE journal_entries SET isFavorite = :value, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, value: Int, updatedAt: Long)

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
