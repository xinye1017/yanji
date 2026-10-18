package com.example.yanji.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Execute the actual DAO query with SQLite, including missing rows and repeated toggles. */
class NoteFavoriteQueriesTest {
    private fun favoriteQuery(): String {
        val source = File("src/main/java/com/example/yanji/data/db/Daos.kt").readText()
        val match = Regex("""@Query\("([^"]+)"\)\s*suspend fun toggleFavorite\(""").find(source)
        assertTrue("DAO must expose one atomic favorite toggle", match != null)
        return match!!.groupValues[1]
    }

    private fun toggle(db: SQLiteConnection, query: String, id: String, now: Long): Long {
        db.prepare(query).use {
            it.bindLong(1, now)
            it.bindText(2, id)
            it.step()
        }
        return db.prepare("SELECT changes()").use { it.step(); it.getLong(0) }
    }

    @Test
    fun togglePersistsBothDirectionsWithoutChangingTheNoteFacts() {
        val query = favoriteQuery()
        BundledSQLiteDriver().open(":memory:").use { db ->
            db.prepare("CREATE TABLE journal_entries (id TEXT PRIMARY KEY, isFavorite INTEGER NOT NULL, updatedAt INTEGER, date TEXT, content TEXT, createdAt INTEGER, sessionId TEXT)").use { it.step() }
            db.prepare("INSERT INTO journal_entries VALUES ('note-1', 0, 10, '2026-10-17', '原始正文', 5, 'session-1')").use { it.step() }
            for (attempt in 1L..20L) {
                assertEquals(1L, toggle(db, query, "note-1", 100L + attempt))
                db.prepare("SELECT isFavorite, updatedAt, date, content, createdAt, sessionId FROM journal_entries WHERE id = 'note-1'").use {
                    assertTrue(it.step())
                    assertEquals(attempt % 2, it.getLong(0))
                    assertEquals(100L + attempt, it.getLong(1))
                    assertEquals("2026-10-17", it.getText(2))
                    assertEquals("原始正文", it.getText(3))
                    assertEquals(5L, it.getLong(4))
                    assertEquals("session-1", it.getText(5))
                }
            }
        }
    }

    @Test
    fun missingNoteDoesNotReportASuccessfulWrite() {
        val query = favoriteQuery()
        BundledSQLiteDriver().open(":memory:").use { db ->
            db.prepare("CREATE TABLE journal_entries (id TEXT PRIMARY KEY, isFavorite INTEGER NOT NULL, updatedAt INTEGER)").use { it.step() }
            assertEquals(0L, toggle(db, query, "missing", 100L))
        }
    }
}
