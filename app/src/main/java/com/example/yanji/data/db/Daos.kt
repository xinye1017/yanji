package com.example.yanji.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<FocusSessionEntity?>

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun count(): Int

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

    @Query("SELECT COUNT(*) FROM exam_sessions")
    suspend fun count(): Int

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
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun getAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<JournalEntryEntity?>

    @Query("SELECT * FROM journal_entries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE date = :date LIMIT 1")
    fun getByDateFlow(date: String): Flow<JournalEntryEntity?>

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<JournalEntryEntity>)

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

    @Query("SELECT * FROM check_ins WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): CheckInEntity?

    @Query("SELECT * FROM check_ins ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): CheckInEntity?

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(item: UnlockedAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlockAll(items: List<UnlockedAchievementEntity>)

    /** 仅用于备份恢复：清空后按备份内容整表重建。 */
    @Query("DELETE FROM unlocked_achievements")
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

