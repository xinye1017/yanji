package com.example.yanji.data

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 仓储「按日期 / 按 id」查询的消费者契约。
 *
 * 每条用例都使用自己的一次性 Room 库：绝不打开 App 单例，也不碰真机数据。
 * 成就系统摘除后，这里原先借用的测试库随之删除，因此本文件自带最小夹具。
 */
class ScopedSessionQueriesConsumerTest {

    private companion object {
        /** The app's own tables; Room's internal bookkeeping tables are excluded. */
        val BUSINESS_TABLES = listOf("focus_sessions", "exam_sessions", "journal_entries", "check_ins")
    }

    /** 一条被记录下来的业务读取，用来证明查询没有退化成整表搬运。 */
    private class Read(val sql: String)

    private class Fixture : AutoCloseable {
        private val directory = Files.createTempDirectory("yanji-scoped-queries").toFile()
        val reads = CopyOnWriteArrayList<Read>()
        private val driver = object : androidx.sqlite.SQLiteDriver {
            private val delegate = BundledSQLiteDriver()
            override fun open(fileName: String): androidx.sqlite.SQLiteConnection {
                val connection = delegate.open(fileName)
                return object : androidx.sqlite.SQLiteConnection by connection {
                    override fun prepare(sql: String): androidx.sqlite.SQLiteStatement {
                        val statement = connection.prepare(sql)
                        if (sql.trimStart().uppercase().startsWith("SELECT")) reads.add(Read(sql))
                        return statement
                    }
                }
            }
        }
        val db: YanjiDatabase = Room.databaseBuilder<YanjiDatabase>(
            directory.resolve("scoped.db").absolutePath,
            factory = { YanjiDatabase_Impl() }
        ).setDriver(driver).setQueryCoroutineContext(Dispatchers.IO).build()

        suspend fun seed(focus: List<FocusSession> = emptyList(), exams: List<ExamSession> = emptyList()) {
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    db.focusSessionDao().insertAll(focus.map(FocusSessionEntity::fromDomainModel))
                    db.examSessionDao().insertAll(exams.map(ExamSessionEntity::fromDomainModel))
                }
            }
        }

        override fun close() {
            db.close()
            directory.deleteRecursively()
        }
    }

    private fun repository(fixture: Fixture): YanjiRepository {
        val repo = YanjiRepository::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        YanjiRepository::class.java.getDeclaredField("database").apply { isAccessible = true }.set(repo, fixture.db)
        return repo
    }

    @Test fun focusByIdReadsOnlyTheMatchingPersistedRow() = runBlocking {
        Fixture().use { fixture ->
            val target = FocusSession("target", "math", "数学", 1, 3600001, 3600)
            fixture.seed(focus = listOf(target, target.copy(id = "other")))
            val repo = repository(fixture)
            assertEquals(target, repo.getFocusSessionByIdFlow("target").first())
            assertNull(repo.getFocusSessionByIdFlow("missing").first())
        }
    }

    @Test fun examByIdReadsOnlyTheMatchingPersistedRow() = runBlocking {
        Fixture().use { fixture ->
            val target = ExamSession("target", "math", "数学", startTime = 1, endTime = 2, createdAt = 3)
            fixture.seed(exams = listOf(target, target.copy(id = "other")))
            val repo = repository(fixture)
            assertEquals(target, repo.getExamSessionByIdFlow("target").first())
            assertNull(repo.getExamSessionByIdFlow("missing").first())
        }
    }

    @Test fun todayDurationComesFromRoomAggregates() = runBlocking {
        Fixture().use { fixture ->
            val range = YanjiTime.dayRange(YanjiTime.today())
            val start = range.startInclusive + 1000
            val focus = FocusSession("focus", "math", "数学", start, start + 3600000, 3600)
            fixture.seed(
                focus = listOf(
                    focus,
                    focus.copy(id = "old", startTime = range.startInclusive - 1),
                    focus.copy(id = "cancelled", status = SessionStatus.CANCELLED)
                ),
                exams = listOf(ExamSession("exam", "math", "数学", actualDurationSeconds = 1800, startTime = start))
            )
            fixture.reads.clear()
            assertEquals(5400L, repository(fixture).observeTodayStudyDurationSeconds().first())
            // Room's own invalidation tracking (`room_table_modification_log`) is
            // infrastructure, not a business projection, so only the app's tables are
            // checked for whole-row reads.
            val offenders = fixture.reads.map { it.sql.uppercase() }
                .filter { sql -> "SELECT *" in sql && BUSINESS_TABLES.any { table -> table in sql } }
            assertFalse("the daily aggregate must not ship whole rows: $offenders", offenders.isNotEmpty())
        }
    }

    @Test fun aiDiagnosticSnapshotBuildsWithScopedSessions() {
        val now = System.currentTimeMillis()
        val snapshot = StudyDiagnosticSnapshot.from(
            periodDays = 7, settings = UserSettings(),
            focusSessions = listOf(FocusSession("f", "sub_math", "数学一", now - 3600000, now, 3600)),
            examSessions = emptyList(), noteEntries = emptyList(), now = now
        )
        assertTrue(snapshot.toPromptData().contains("数学一"))
    }
}
