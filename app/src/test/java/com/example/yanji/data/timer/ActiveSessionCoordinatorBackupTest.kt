package com.example.yanji.data.timer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActiveSessionCoordinatorBackupTest {

    private class FixedClock : MonotonicClock {
        override fun nowMs(): Long = 10_000L
        override fun currentBootId(): String = "boot-backup-test"
    }

    private class BackupPersistence(
        var record: ActiveSessionRecord? = null
    ) : TimerSessionPersistence {
        var failLoad = false
        var clearCalls = 0
        var failComplete = false
        var beforeLoadRecord: (suspend () -> Unit)? = null
        var afterLoadRecord: (suspend () -> Unit)? = null

        override suspend fun completeFocus(
            session: ActiveSession,
            actualSeconds: Long,
            pausedSeconds: Long,
            pauseCount: Int,
            endEpochMs: Long
        ) {
            if (failComplete) error("injected database failure")
        }

        override suspend fun completeExam(
            session: ActiveSession,
            actualSeconds: Long,
            endEpochMs: Long
        ) {
            if (failComplete) error("injected database failure")
        }

        override suspend fun saveActiveSession(record: ActiveSessionRecord) {
            this.record = record
        }

        override suspend fun saveActiveSession(session: ActiveSession) {
            record = ActiveSessionRecord(
                activeSession = session,
                timerSnapshot = TimerSnapshot(
                    phase = if (session.paused) TimerPhase.PAUSED else TimerPhase.RUNNING,
                    startedAtEpochMs = session.startedAtEpochMs,
                    targetDurationSeconds = session.targetDurationSeconds,
                    accumulatedActiveMs = session.accumulatedActiveMs,
                    resumedAtMonotonicMs = if (session.paused) null else 10_000L,
                    pauseCount = session.pauseCount
                ),
                bootId = "boot-backup-test"
            )
        }

        override suspend fun clearActiveSession() {
            clearCalls++
            record = null
        }

        override suspend fun loadActiveSessionRecord(): ActiveSessionRecord? {
            beforeLoadRecord?.invoke()
            if (failLoad) error("injected persisted-session read failure")
            afterLoadRecord?.invoke()
            return record
        }

        override suspend fun loadActiveSession(): ActiveSession? {
            if (failLoad) error("injected persisted-session read failure")
            return record?.activeSession
        }
    }

    private fun session(id: String = "focus-backup") = ActiveSession(
        sessionId = id,
        kind = ActiveSessionKind.FOCUS,
        subjectId = "math",
        subjectName = "数学",
        startedAtEpochMs = 1_757_000_000_000L
    )

    private fun persisted(session: ActiveSession = session()) = ActiveSessionRecord(
        activeSession = session,
        timerSnapshot = TimerSnapshot(
            phase = TimerPhase.RUNNING,
            startedAtEpochMs = session.startedAtEpochMs,
            targetDurationSeconds = session.targetDurationSeconds,
            accumulatedActiveMs = session.accumulatedActiveMs,
            resumedAtMonotonicMs = 10_000L,
            pauseCount = session.pauseCount
        ),
        bootId = "boot-backup-test"
    )

    @Test
    fun `backup restore rejects a corrupt production snapshot without isolating it`() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("yanji-backup-corrupt-session").toFile()
        try {
            val persistence = FileTimerSessionPersistence(directory, FixedClock())
            persistence.sessionFile.writeText("{ incomplete timer snapshot")
            val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)
            var entered = false

            assertTrue(runCatching { coordinator.withBackupRestore { entered = true } }.isFailure)
            assertFalse(entered)
            assertEquals("{ incomplete timer snapshot", persistence.sessionFile.readText())
            assertFalse("Import must not invoke cold-start quarantine", persistence.corruptedFile.exists())
            assertFalse(coordinator.isBusy)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `backup restore rejects a pending production snapshot write`() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("yanji-backup-pending-session").toFile()
        try {
            val persistence = FileTimerSessionPersistence(directory, FixedClock())
            persistence.tmpFile.writeText("pending session bytes")
            val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)
            var entered = false

            assertTrue(runCatching { coordinator.withBackupRestore { entered = true } }.isFailure)
            assertFalse(entered)
            assertEquals("pending session bytes", persistence.tmpFile.readText())
            assertFalse(coordinator.isBusy)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `restore ownership blocks begin and persisted restore until success releases it`() = runTest {
        val persistence = BackupPersistence()
        val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)

        val result = coordinator.withBackupRestore {
            assertTrue("Backup restore must be visible as busy", coordinator.isBusy)
            assertFalse("Timer cannot start while restore owns the coordinator", coordinator.begin(session("racing-start")))
            assertNull("Persisted restore cannot race a backup restore", coordinator.restorePersistedNow())
            "restored"
        }

        assertEquals("restored", result)
        assertFalse(coordinator.isBusy)
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
        assertTrue("Timer must become available after restore ownership releases", coordinator.begin(session("after")))
    }

    @Test
    fun `duplicate backup restores are excluded atomically`() = runTest {
        val persistence = BackupPersistence()
        val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            coordinator.withBackupRestore {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()

        val duplicate = runCatching {
            coordinator.withBackupRestore { error("duplicate block must never run") }
        }
        assertTrue(duplicate.isFailure)
        assertFalse(coordinator.begin(session("racing-start")))

        release.complete(Unit)
        first.await()
        assertFalse(coordinator.isBusy)
    }

    @Test
    fun `timer start cannot enter while strict persisted probe owns restore`() = runTest {
        val persistence = BackupPersistence()
        val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)
        val probeEntered = CompletableDeferred<Unit>()
        val releaseProbe = CompletableDeferred<Unit>()
        persistence.afterLoadRecord = {
            probeEntered.complete(Unit)
            releaseProbe.await()
        }

        val restore = async {
            runCatching {
                coordinator.withBackupRestore { "restored" }
            }
        }
        probeEntered.await()
        val start = async { coordinator.begin(session("must-not-win")) }
        runCurrent()
        assertTrue("Begin must resolve immediately while restore owns the coordinator", start.isCompleted)
        assertFalse("Begin must not win while the strict persisted read owns restore", start.await())
        assertTrue("Backup restore must remain visibly busy during its probe", coordinator.isBusy)
        assertNull("Rejected begin must not persist a timer snapshot", persistence.record)

        releaseProbe.complete(Unit)
        persistence.afterLoadRecord = null
        assertEquals("restored", restore.await().getOrThrow())
        assertTrue("Timer start must become available after restore releases ownership", coordinator.begin(session("after-restore")))
        assertEquals("after-restore", persistence.record?.activeSession?.sessionId)
    }

    @Test
    fun `ownership releases on exception and cancellation`() = runTest {
        val persistence = BackupPersistence()
        val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)

        assertTrue(
            runCatching {
                coordinator.withBackupRestore<Unit> { error("injected import failure") }
            }.isFailure
        )
        assertFalse(coordinator.isBusy)

        val cancelled = launch {
            coordinator.withBackupRestore<Unit> { awaitCancellation() }
        }
        while (!coordinator.isBusy) {
            kotlinx.coroutines.yield()
        }
        cancelled.cancel(CancellationException("injected cancellation"))
        cancelled.join()

        assertFalse("Cancellation must release restore ownership", coordinator.isBusy)
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
    }

    @Test
    fun `persisted read failure blocks restore and never clears the snapshot`() = runTest {
        val existing = persisted()
        val persistence = BackupPersistence(existing).apply { failLoad = true }
        val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)
        var blockRan = false

        val result = runCatching {
            coordinator.withBackupRestore {
                blockRan = true
            }
        }

        assertTrue("An unreadable active snapshot must fail closed", result.isFailure)
        assertFalse(blockRan)
        assertEquals(0, persistence.clearCalls)
        assertEquals(existing, persistence.record)
        assertFalse(coordinator.isBusy)
    }

    @Test
    fun `existing persisted session blocks restore without clearing its pointer`() = runTest {
        val existing = persisted()
        val persistence = BackupPersistence(existing)
        val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)

        assertTrue(
            runCatching {
                coordinator.withBackupRestore { error("block must not run") }
            }.isFailure
        )

        assertEquals(0, persistence.clearCalls)
        assertEquals(existing, persistence.record)
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
    }

    @Test
    fun `completion failure remains recoverable and retryable`() = runTest {
        val persistence = BackupPersistence()
        val coordinator = ActiveSessionCoordinatorCore(persistence, FixedClock(), this)
        assertTrue(coordinator.begin(session("retryable")))

        persistence.failComplete = true
        assertTrue(runCatching { coordinator.complete(actualSeconds = 120L) }.isFailure)
        assertEquals(CoordinatorState.ACTIVE, coordinator.coordinatorState.value)
        assertNotNull(coordinator.active.value)
        assertNotNull("The active snapshot must survive completion failure", persistence.record)

        persistence.failComplete = false
        assertTrue(coordinator.complete(actualSeconds = 120L))
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
        assertNull(coordinator.active.value)
        assertNull(persistence.record)
    }
}
