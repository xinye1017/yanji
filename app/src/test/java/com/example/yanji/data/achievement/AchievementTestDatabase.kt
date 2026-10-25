package com.example.yanji.data.achievement

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.db.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** Each test owns a real generated Room database, separate from the app and other tests. */
internal class AchievementTestDatabase : AutoCloseable {
    class Read(val sql: String) {
        @Volatile var rows = 0L
        @Volatile var textBytes = 0L
    }
    val reads = CopyOnWriteArrayList<Read>()
    private val directory = Files.createTempDirectory("yanji-achievement-facts").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val driver = object : SQLiteDriver {
        private val delegate = BundledSQLiteDriver()
        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    val businessRead = sql.trimStart().startsWith("SELECT", ignoreCase = true) &&
                        Regex("\\b(focus_sessions|exam_sessions|journal_entries|check_ins|unlocked_achievements)\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql)
                    val read = if (businessRead) Read(sql).also(reads::add) else null
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean = statement.step().also { if (it && read != null) read.rows++ }
                        override fun getText(index: Int): String = statement.getText(index).also {
                            if (read != null) read.textBytes += it.toByteArray(Charsets.UTF_8).size
                        }
                    }
                }
            }
        }
    }
    val db: YanjiDatabase = Room.databaseBuilder<YanjiDatabase>(
        directory.resolve("isolated.db").absolutePath, factory = { YanjiDatabase_Impl() }
    ).setDriver(driver).setQueryCoroutineContext(Dispatchers.IO).build()

    suspend fun seed(
        focus: List<FocusSession> = emptyList(),
        exams: List<ExamSession> = emptyList(),
        notes: List<NoteEntry> = emptyList(),
        checkIns: List<CheckIn> = emptyList()
    ) {
        // JVM tests resolve room-runtime-jvm, which has no `withTransaction`
        // extension; the writer connection + immediateTransaction pair is the
        // same transaction boundary the production paths use.
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                db.focusSessionDao().insertAll(focus.map(FocusSessionEntity::fromDomainModel))
                db.examSessionDao().insertAll(exams.map(ExamSessionEntity::fromDomainModel))
                db.noteEntryDao().insertAll(notes.map(NoteEntryEntity::fromDomainModel))
                db.checkInDao().insertAll(checkIns.map(CheckInEntity::fromDomainModel))
            }
        }
    }

    suspend fun facts(
        event: AchievementEvent = AchievementEvent.ReconcileAll,
        unlockedIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis()
    ): AchievementFacts = run {
        var facts: AchievementFacts? = null
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                facts = AchievementFactsReader.read(
                    db,
                    AchievementEvaluator().pendingDefinitions(event, unlockedIds),
                    nowMs
                )
            }
        }
        checkNotNull(facts) { "facts were not produced inside the transaction" }
    }

    suspend fun stopScope() {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    override fun close() {
        scope.cancel()
        db.close()
        directory.deleteRecursively()
    }
}
