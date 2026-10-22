package com.example.yanji.data.db

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.yanji.data.StudyTaskNotCarryableException
import com.example.yanji.data.carryStudyTaskToTodayInTransaction
import com.example.yanji.data.deleteStudyTaskInTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

/**
 * Real generated Room DAO + SQLite coverage for the cross-day task workflow.
 *
 * These tests use isolated temporary databases. They never open or write the
 * app's production database on a developer or user device.
 */
class StudyTaskCarryOverQueriesTest {

    private val today = LocalDate.of(2026, 10, 22)

    private fun isolatedDatabase(block: suspend (YanjiDatabase) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("yanji-task-carry").toFile()
        val db = Room.databaseBuilder<YanjiDatabase>(
            directory.resolve("isolated.db").absolutePath,
            factory = { YanjiDatabase_Impl() }
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            block(db)
        } finally {
            db.close()
            directory.deleteRecursively()
        }
    }

    private fun task(
        id: String,
        date: String,
        title: String = id,
        completed: Boolean = false,
        carriedOverToId: String? = null,
        createdAt: Long = 1_000L
    ) = StudyTaskEntity(
        id = id,
        date = date,
        subjectId = "operating_system",
        subjectName = "操作系统",
        title = title,
        plannedMinutes = 75,
        isCompleted = completed,
        carriedOverToId = carriedOverToId,
        createdAt = createdAt,
        updatedAt = createdAt + 100
    )

    private fun focus(
        id: String,
        taskId: String?,
        durationSeconds: Long,
        status: String = "COMPLETED"
    ) = FocusSessionEntity(
        id = id,
        subjectId = "operating_system",
        subjectName = "操作系统",
        startTime = 2_000L,
        endTime = 2_000L + durationSeconds * 1_000,
        durationSeconds = durationSeconds,
        pausedDurationSeconds = 0,
        pauseCount = 0,
        mode = "正向计时",
        note = "",
        taskId = taskId,
        status = status,
        createdAt = 2_000L
    )

    private suspend fun allTasks(db: YanjiDatabase): List<StudyTaskEntity> =
        db.studyTaskDao().getAllOnce()

    private suspend inline fun <reified T : Throwable> assertFailsWith(
        message: String = "Expected ${T::class.simpleName}",
        block: suspend () -> Unit
    ): T = try {
        block()
        throw AssertionError(message)
    } catch (error: Throwable) {
        if (error is T) error else throw error
    }

    @Test
    fun pendingQueryUsesAnInclusiveSevenDayWindowAndTruthfulOrdering() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        dao.insert(task("boundary", "2026-10-15", title = "窗口内", createdAt = 20))
        dao.insert(task("too-old", "2026-10-14", title = "窗口外"))
        dao.insert(task("done", "2026-10-21", completed = true))
        dao.insert(task("linked", "2026-10-20", carriedOverToId = "newer"))
        dao.insert(task("future", "2026-10-23"))
        dao.insert(task("same-day-late", "2026-10-21", createdAt = 30))
        dao.insert(task("same-day-early", "2026-10-21", createdAt = 10))

        val rows = dao.getCarryOverWithActualSeconds(
            earliestDate = "2026-10-15",
            today = "2026-10-22"
        )

        assertEquals(
            listOf("same-day-early", "same-day-late", "boundary"),
            rows.map { it.task.id }
        )
    }

    @Test
    fun pendingQueryAggregatesOnlyCompletedFocusAcrossTheWholeTaskHistory() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        dao.insert(task("source", "2026-10-20"))
        db.focusSessionDao().insert(focus("done-focus", "source", 600))
        db.focusSessionDao().insert(focus("cancelled", "source", 3600, status = "CANCELLED"))
        db.focusSessionDao().insert(focus("unbound", null, 1800))

        val row = dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22").single()

        assertEquals(600L, row.actualSeconds)
    }

    @Test
    fun carryingCreatesATodayCopyAndLeavesTheSourceHistoryUntouched() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        val source = task("source", "2026-10-20", title = "文件管理复习")
        dao.insert(source)
        db.focusSessionDao().insert(focus("focus", source.id, 900))

        val result = carryStudyTaskToTodayInTransaction(
            db = db,
            taskId = source.id,
            today = today,
            timestamp = 9_000L,
            destinationId = "today-copy"
        )

        val persistedSource = dao.getById(source.id)!!
        val destination = dao.getById("today-copy")!!
        assertEquals("today-copy", persistedSource.carriedOverToId)
        assertEquals(9_000L, persistedSource.updatedAt)
        assertEquals(source.date, persistedSource.date)
        assertEquals(source.title, persistedSource.title)
        assertEquals(source.plannedMinutes, persistedSource.plannedMinutes)
        assertEquals(source.isCompleted, persistedSource.isCompleted)

        assertEquals("2026-10-22", destination.date)
        assertEquals(source.id, persistedSource.id)
        assertEquals(source.subjectId, destination.subjectId)
        assertEquals(source.subjectName, destination.subjectName)
        assertEquals(source.title, destination.title)
        assertEquals(source.plannedMinutes, destination.plannedMinutes)
        assertFalse(destination.isCompleted)
        assertNull(destination.carriedOverToId)
        assertEquals(9_000L, destination.createdAt)
        assertEquals(9_000L, destination.updatedAt)
        assertEquals("today-copy", result.task.id)
        assertEquals(0L, result.actualSeconds)
        assertEquals(
            0,
            dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22").size
        )
    }

    @Test
    fun completedLinkedFutureOutOfWindowAndMissingSourcesAreRejected() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        dao.insert(task("done", "2026-10-21", completed = true))
        dao.insert(task("linked", "2026-10-21", carriedOverToId = "copy"))
        dao.insert(task("future", "2026-10-23"))
        dao.insert(task("too-old", "2026-10-14"))
        dao.insert(task("today", "2026-10-22"))

        for ((index, id) in listOf("done", "linked", "future", "too-old", "today", "missing").withIndex()) {
            assertFailsWith<StudyTaskNotCarryableException>("$id must be refused") {
                carryStudyTaskToTodayInTransaction(
                    db = db,
                    taskId = id,
                    today = today,
                    timestamp = 10_000L + index,
                    destinationId = "copy-$index"
                )
            }
        }

        assertEquals(5, allTasks(db).size)
        assertEquals(
            listOf("linked" to "copy"),
            allTasks(db)
                .filter { it.carriedOverToId != null }
                .map { it.id to it.carriedOverToId }
        )
        assertEquals(0, allTasks(db).count { it.id.startsWith("copy-") })
    }

    @Test
    fun repeatedAndConcurrentCarriesCreateAtMostOneCopy() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        dao.insert(task("source", "2026-10-21"))

        val outcomes = coroutineScope {
            List(2) { index ->
                async(Dispatchers.IO) {
                    runCatching {
                        carryStudyTaskToTodayInTransaction(
                            db = db,
                            taskId = "source",
                            today = today,
                            timestamp = 20_000L + index,
                            destinationId = "copy-$index"
                        )
                    }
                }
            }.awaitAll()
        }

        assertEquals(1, outcomes.count(Result<*>::isSuccess))
        assertEquals(1, outcomes.count { it.exceptionOrNull() is StudyTaskNotCarryableException })
        val source = dao.getById("source")!!
        val copies = allTasks(db).filter { it.date == "2026-10-22" }
        assertEquals(1, copies.size)
        assertEquals(copies.single().id, source.carriedOverToId)

        assertFailsWith<StudyTaskNotCarryableException> {
            carryStudyTaskToTodayInTransaction(
                db = db,
                taskId = "source",
                today = today,
                timestamp = 30_000L,
                destinationId = "copy-later"
            )
        }
        assertNull(dao.getById("copy-later"))
    }

    @Test
    fun aDestinationInsertConflictRollsBackTheSourceLink() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        val sourceBefore = task("source", "2026-10-20")
        dao.insert(sourceBefore)
        dao.insert(task("occupied", "2026-10-22", title = "existing"))

        assertFailsWith<Exception> {
            carryStudyTaskToTodayInTransaction(
                db = db,
                taskId = "source",
                today = today,
                timestamp = 40_000L,
                destinationId = "occupied"
            )
        }

        val source = dao.getById("source")!!
        assertNull("insert failure must roll back the claim", source.carriedOverToId)
        assertEquals(sourceBefore.updatedAt, source.updatedAt)
        assertEquals("existing", dao.getById("occupied")!!.title)
    }

    @Test
    fun completingASourceRemovesItFromPendingWithoutDeletingHistory() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        dao.insert(task("source", "2026-10-20"))
        assertEquals(1, dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22").size)

        dao.setCompleted("source", true, 50_000L)

        assertEquals(0, dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22").size)
        val source = dao.getById("source")
        assertNotNull(source)
        assertTrue(source!!.isCompleted)
        assertEquals("2026-10-20", source.date)
        assertEquals(50_000L, source.updatedAt)
    }

    @Test
    fun deletingACarriedCopyRestoresTheSourceToPending() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        dao.insert(task("source", "2026-10-20", title = "文件管理复习"))
        carryStudyTaskToTodayInTransaction(
            db = db,
            taskId = "source",
            today = today,
            timestamp = 60_000L,
            destinationId = "copy"
        )
        assertEquals(0, dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22").size)

        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                assertTrue(deleteStudyTaskInTransaction(db, "copy", 70_000L))
            }
        }

        val source = dao.getById("source")!!
        assertNull(source.carriedOverToId)
        assertEquals("2026-10-20", source.date)
        assertEquals(70_000L, source.updatedAt)
        assertNull(dao.getById("copy"))
        assertEquals(
            listOf("source"),
            dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22")
                .map { it.task.id }
        )
    }

    @Test
    fun deletingAnOrdinaryTaskLeavesOtherLinksUntouchedAndReportsMissingIds() =
        isolatedDatabase { db ->
            val dao = db.studyTaskDao()
            dao.insert(task("source", "2026-10-20"))
            carryStudyTaskToTodayInTransaction(
                db = db,
                taskId = "source",
                today = today,
                timestamp = 60_000L,
                destinationId = "copy"
            )
            dao.insert(task("ordinary", "2026-10-22"))

            assertTrue(deleteStudyTaskInTransaction(db, "ordinary", timestamp = 70_000L))
            assertFalse(deleteStudyTaskInTransaction(db, "missing", timestamp = 80_000L))

            assertEquals("copy", dao.getById("source")!!.carriedOverToId)
            assertNull(dao.getById("ordinary"))
            assertEquals("copy", dao.getById("copy")!!.id)
        }

    @Test
    fun aDeleteFailureRollsBackTheSourceRelease() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        dao.insert(task("source", "2026-10-20"))
        val sourceBeforeRelease = runBlocking {
            carryStudyTaskToTodayInTransaction(
                db = db,
                taskId = "source",
                today = today,
                timestamp = 60_000L,
                destinationId = "copy"
            )
            dao.getById("source")!!
        }

        assertFailsWith<Exception>("the injected delete failure must abort the transaction") {
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    deleteStudyTaskInTransaction(db, "copy", timestamp = 70_000L) {
                        throw IllegalStateException("delete failed")
                    }
                }
            }
        }

        val source = dao.getById("source")!!
        assertEquals(sourceBeforeRelease.carriedOverToId, source.carriedOverToId)
        assertEquals(sourceBeforeRelease.updatedAt, source.updatedAt)
        assertEquals("copy", dao.getById("copy")!!.id)
    }

    @Test
    fun danglingCarryOverLinksAreReleasedWithoutChangingTaskHistory() = isolatedDatabase { db ->
        val dao = db.studyTaskDao()
        val dangling = task(
            "dangling",
            "2026-10-20",
            title = "学完图论",
            carriedOverToId = "missing-copy",
            createdAt = 1_000L
        )
        val validSource = task("valid-source", "2026-10-18")
        dao.insert(dangling)
        dao.insert(validSource)
        carryStudyTaskToTodayInTransaction(
            db = db,
            taskId = validSource.id,
            today = today,
            timestamp = 2_000L,
            destinationId = "valid-copy"
        )
        assertEquals(0, dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22").size)

        assertEquals(1, dao.releaseDanglingCarryOverLinks(updatedAt = 3_000L))

        val released = dao.getById("dangling")!!
        assertNull(released.carriedOverToId)
        assertEquals("2026-10-20", released.date)
        assertEquals(dangling.isCompleted, released.isCompleted)
        assertEquals(dangling.createdAt, released.createdAt)
        assertEquals(3_000L, released.updatedAt)
        assertEquals("valid-copy", dao.getById("valid-source")!!.carriedOverToId)
        assertEquals(
            listOf("dangling"),
            dao.getCarryOverWithActualSeconds("2026-10-15", "2026-10-22")
                .map { it.task.id }
        )
        assertEquals(
            "the integrity repair must be idempotent",
            0,
            dao.releaseDanglingCarryOverLinks(updatedAt = 4_000L)
        )
        assertEquals(3_000L, dao.getById("dangling")!!.updatedAt)
    }
}
