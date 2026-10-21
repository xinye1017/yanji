package com.example.yanji.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.note.NoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * 人工纠正随笔分类：跑真实生成的 DAO 与 SQLite，覆盖「纠正分类」「重复写同一分类」
 * 「id 不存在」三条路径。
 *
 * 之所以盯住这三条：AI 的建议只是建议，用户纠正必须真的落库；而重放同一条语句不能把
 * 用户已经勾掉的"已解决"再抹掉一次（见 [NoteEntryDao.setCategory] 的 CASE WHEN 规则）。
 */
class NoteCategoryQueriesTest {

    private fun open(path: String): YanjiDatabase =
        Room.databaseBuilder<YanjiDatabase>(path, factory = { YanjiDatabase_Impl() })
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private fun isolatedDatabase(block: suspend (YanjiDatabase) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("yanji-note-category").toFile()
        val db = open(directory.resolve("isolated.db").absolutePath)
        try {
            block(db)
        } finally {
            db.close()
            directory.deleteRecursively()
        }
    }

    private fun note(
        id: String,
        category: String,
        isResolved: Int,
        resolvedAt: Long?
    ) = NoteEntryEntity(
        id = id,
        date = "2026-10-18",
        title = "",
        content = "一条已保存的记录",
        moodScore = 5,
        energyScore = 4,
        studySatisfaction = 5,
        tomorrowPlan = "",
        blockers = "",
        tags = "",
        createdAt = 1000L,
        updatedAt = 1000L,
        sessionId = "session-1",
        category = category,
        isResolved = isResolved,
        resolvedAt = resolvedAt
    )

    @Test
    fun correctingTheCategoryClearsTheNowMeaninglessResolvedState() = isolatedDatabase { db ->
        val dao = db.noteEntryDao()
        dao.insert(note("note-1", "DOUBT", 1, 900L))

        assertEquals(1, dao.setCategory("note-1", "INSPIRATION", 2000L))

        val after = dao.getById("note-1")!!
        assertEquals("INSPIRATION", after.category)
        assertEquals(0, after.isResolved)
        assertNull(after.resolvedAt)
        assertEquals(2000L, after.updatedAt)
        // 纠正分类不是重写记录：其余事实一律不动。
        assertEquals("一条已保存的记录", after.content)
        assertEquals(1000L, after.createdAt)
        assertEquals("2026-10-18", after.date)
        assertEquals("session-1", after.sessionId)
    }

    @Test
    fun rewritingTheSameCategoryKeepsResolvedStateAndTimestamp() = isolatedDatabase { db ->
        val dao = db.noteEntryDao()
        dao.insert(note("note-2", "DOUBT", 1, 900L))

        assertEquals(1, dao.setCategory("note-2", "DOUBT", 2000L))

        val after = dao.getById("note-2")!!
        assertEquals("DOUBT", after.category)
        assertEquals(1, after.isResolved)
        assertEquals(900L, after.resolvedAt)
        assertEquals(1000L, after.updatedAt)
    }

    @Test
    fun aMissingIdDoesNotReportASuccessfulWrite() = isolatedDatabase { db ->
        assertEquals(0, db.noteEntryDao().setCategory("missing", "DOUBT", 2000L))
    }

    @Test
    fun theAwaitableStorePathReportsOnlyRealWrites() = isolatedDatabase { db ->
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertTrue(NoteStore(scope, { db }).setCategoryAndAwait("note-3", "DOUBT").not())
            db.noteEntryDao().insert(note("note-3", "INSPIRATION", 0, null))
            assertTrue(NoteStore(scope, { db }).setCategoryAndAwait("note-3", "DOUBT"))
            assertEquals("DOUBT", db.noteEntryDao().getById("note-3")!!.category)
            // 数据库尚未就绪时不能谎报成功。
            assertFalse(NoteStore(scope, { null }).setCategoryAndAwait("note-3", "INSPIRATION"))
            assertFalse(NoteStore(scope, { db }).setCategoryAndAwait("missing", "DOUBT"))
        } finally {
            scope.cancel()
        }
    }
}
