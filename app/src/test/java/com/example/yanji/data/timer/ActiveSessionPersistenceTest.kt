package com.example.yanji.data.timer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class TestMonotonicClock(
    private var now: Long = 10_000L,
    var bootId: String = "boot-test-1"
) : MonotonicClock {
    override fun nowMs(): Long = now
    override fun currentBootId(): String = bootId

    fun advanceBy(deltaMs: Long) {
        now += deltaMs
    }

    fun setNow(nowMs: Long) {
        now = nowMs
    }
}

class MockSessionPersistence(
    val filePersistence: FileTimerSessionPersistence
) : TimerSessionPersistence {
    var failCompleteWith: Throwable? = null
    var completeFocusCalls = 0
    var completeExamCalls = 0
    var lastCompletedSession: ActiveSession? = null

    override suspend fun completeFocus(
        session: ActiveSession,
        actualSeconds: Long,
        pausedSeconds: Long,
        pauseCount: Int,
        endEpochMs: Long
    ) {
        failCompleteWith?.let { throw it }
        completeFocusCalls++
        lastCompletedSession = session
    }

    override suspend fun completeExam(
        session: ActiveSession,
        actualSeconds: Long,
        endEpochMs: Long
    ) {
        failCompleteWith?.let { throw it }
        completeExamCalls++
        lastCompletedSession = session
    }

    override suspend fun saveActiveSession(record: ActiveSessionRecord) {
        filePersistence.saveActiveSession(record)
    }

    override suspend fun saveActiveSession(session: ActiveSession) {
        filePersistence.saveActiveSession(session)
    }

    override suspend fun clearActiveSession() {
        filePersistence.clearActiveSession()
    }

    override suspend fun loadActiveSession(): ActiveSession? {
        return filePersistence.loadActiveSession()
    }

    override suspend fun loadActiveSessionRecord(): ActiveSessionRecord? {
        return filePersistence.loadActiveSessionRecord()
    }
}

class ActiveSessionPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createFocusSession(
        id: String = "focus-1",
        targetDuration: Long = 0L,
        paused: Boolean = false,
        accumulatedMs: Long = 0L
    ) = ActiveSession(
        sessionId = id,
        kind = ActiveSessionKind.FOCUS,
        subjectId = "math",
        subjectName = "高等数学",
        mode = if (targetDuration > 0) "倒计时" else "正向计时",
        targetDurationSeconds = targetDuration,
        startedAtEpochMs = 1_700_000_000_000L,
        accumulatedActiveMs = accumulatedMs,
        paused = paused
    )

    private fun createExamSession(id: String = "exam-1", plannedDuration: Long = 10800L) = ActiveSession(
        sessionId = id,
        kind = ActiveSessionKind.EXAM,
        subjectId = "math_exam",
        subjectName = "数学全真模拟",
        targetDurationSeconds = plannedDuration,
        plannedDurationSeconds = plannedDuration,
        startedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun test1_beginCreatesSnapshotOnDisk() = runTest {
        val clock = TestMonotonicClock(now = 5_000L, bootId = "boot-abc")
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        val session = createFocusSession("session-1")
        val accepted = coordinator.begin(session)
        assertTrue(accepted)
        coordinator.awaitPersistence()

        val file = filePersistence.sessionFile
        assertTrue("快照文件必须存在", file.exists())

        val record = filePersistence.loadActiveSessionRecord()
        assertNotNull(record)
        assertEquals(1, record!!.formatVersion)
        assertEquals("session-1", record.activeSession.sessionId)
        assertEquals("高等数学", record.activeSession.subjectName)
        assertEquals(TimerPhase.RUNNING, record.timerSnapshot.phase)
        assertEquals(5_000L, record.timerSnapshot.resumedAtMonotonicMs)
        assertEquals("boot-abc", record.bootId)
        assertTrue(record.lastPersistedWallClockMs > 0)
    }

    @Test
    fun test2_coordinatorRestoresAfterReconstruction() = runTest {
        val clock = TestMonotonicClock(now = 10_000L, bootId = "boot-1")
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator1 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        coordinator1.begin(createFocusSession("session-restore"))
        coordinator1.awaitPersistence()

        // 模拟进程重建：创建全新的 coordinator2 实例
        val coordinator2 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)
        assertEquals(CoordinatorState.IDLE, coordinator2.coordinatorState.value)
        assertNull(coordinator2.active.value)

        val restored = coordinator2.restorePersistedNow()
        assertNotNull(restored)
        assertEquals("session-restore", restored!!.sessionId)
        assertEquals(CoordinatorState.ACTIVE, coordinator2.coordinatorState.value)
        assertEquals("session-restore", coordinator2.active.value?.sessionId)
        assertTrue(coordinator2.isBusy)
    }

    @Test
    fun test3_pauseRestoresStillPaused() = runTest {
        val clock = TestMonotonicClock(now = 10_000L, bootId = "boot-1")
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator1 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        coordinator1.begin(createFocusSession("session-paused"))
        coordinator1.awaitPersistence()

        clock.advanceBy(25_000L) // 运行了 25 秒
        coordinator1.update { it.copy(paused = true, accumulatedActiveMs = 25_000L, pauseCount = 1) }
        coordinator1.awaitPersistence()

        // 暂停期间时钟继续走 100 秒
        clock.advanceBy(100_000L)

        // 重建 coordinator2 并恢复
        val coordinator2 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)
        val restored = coordinator2.restorePersistedNow()
        assertNotNull(restored)
        assertTrue("恢复后必须仍处于暂停状态", restored!!.paused)
        assertEquals("暂停期间不应累计计时", 25_000L, restored.accumulatedActiveMs)
        assertEquals(1, restored.pauseCount)
        assertEquals(TimerPhase.PAUSED, coordinator2.currentTimerSnapshot?.phase)
        assertNull(coordinator2.currentTimerSnapshot?.resumedAtMonotonicMs)
    }

    @Test
    fun test4_resumeUpdatesPersistence() = runTest {
        val clock = TestMonotonicClock(now = 10_000L, bootId = "boot-1")
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        coordinator.begin(createFocusSession("session-resume"))
        coordinator.awaitPersistence()

        coordinator.update { it.copy(paused = true, accumulatedActiveMs = 15_000L, pauseCount = 1) }
        coordinator.awaitPersistence()

        var record = filePersistence.loadActiveSessionRecord()
        assertTrue(record!!.activeSession.paused)
        assertEquals(TimerPhase.PAUSED, record.timerSnapshot.phase)

        clock.advanceBy(30_000L)
        // 恢复计时
        coordinator.update { it.copy(paused = false) }
        coordinator.awaitPersistence()

        record = filePersistence.loadActiveSessionRecord()
        assertNotNull(record)
        assertFalse("恢复后持久化文件必须更新为非暂停", record!!.activeSession.paused)
        assertEquals(TimerPhase.RUNNING, record.timerSnapshot.phase)
        assertEquals(clock.nowMs(), record.timerSnapshot.resumedAtMonotonicMs)
    }

    @Test
    fun test5_cancelClearsState() = runTest {
        val clock = TestMonotonicClock()
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        coordinator.begin(createFocusSession("session-cancel"))
        coordinator.awaitPersistence()
        assertTrue(filePersistence.sessionFile.exists())

        coordinator.cancel()
        coordinator.awaitPersistence()

        assertFalse("cancel 后持久化快照文件必须已被删除", filePersistence.sessionFile.exists())
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
        assertNull(coordinator.active.value)
        assertFalse(coordinator.isBusy)
    }

    @Test
    fun test6_completeWritesDbAndClearsState() = runTest {
        val clock = TestMonotonicClock()
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        coordinator.begin(createFocusSession("session-complete"))
        coordinator.awaitPersistence()

        val success = coordinator.complete(actualSeconds = 120L, pausedSeconds = 10L, pauseCount = 1)
        assertTrue(success)

        assertEquals(1, mockPersistence.completeFocusCalls)
        assertEquals("session-complete", mockPersistence.lastCompletedSession?.sessionId)
        assertFalse("complete 成功后持久化快照必须被清除", filePersistence.sessionFile.exists())
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
        assertNull(coordinator.active.value)
    }

    @Test
    fun test7_dbFailureKeepsActiveRecoverable() = runTest {
        val clock = TestMonotonicClock()
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        coordinator.begin(createFocusSession("session-db-fail"))
        coordinator.awaitPersistence()

        // 模拟 DB 崩溃（例如磁盘满或 SQLite 异常）
        mockPersistence.failCompleteWith = IOException("Disk is full")

        val result = runCatching { coordinator.complete(actualSeconds = 200L) }
        assertTrue("complete 遇到 DB 异常必须抛出", result.isFailure)

        // 核心不变量：DB 失败时不能先丢失 active state，快照文件也不能被清空
        assertEquals(CoordinatorState.ACTIVE, coordinator.coordinatorState.value)
        assertEquals("session-db-fail", coordinator.active.value?.sessionId)
        assertTrue("快照必须仍然保留供重试恢复", filePersistence.sessionFile.exists())

        // 模拟进程在 DB 失败后崩溃，重启后再次恢复
        val coordinator2 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)
        val restored = coordinator2.restorePersistedNow()
        assertNotNull(restored)
        assertEquals("session-db-fail", restored!!.sessionId)

        // 数据库恢复正常，重试 complete 成功
        mockPersistence.failCompleteWith = null
        val retrySuccess = coordinator2.complete(actualSeconds = 200L)
        assertTrue(retrySuccess)
        assertEquals(1, mockPersistence.completeFocusCalls)
        assertFalse(filePersistence.sessionFile.exists())
    }

    @Test
    fun test8_corruptedFileDoesNotCrashAndIsolates() = runTest {
        val clock = TestMonotonicClock()
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        // 写入损坏的非法 JSON
        filePersistence.sessionFile.writeText("{ corrupted json content :: !@#$ %^&* }")

        val restored = coordinator.restorePersistedNow()
        assertNull("损坏文件不能返回恢复对象", restored)
        assertEquals(CoordinatorState.IDLE, coordinator.coordinatorState.value)
        assertNull(coordinator.active.value)
        assertEquals("绝不能制造虚假的完成记录", 0, mockPersistence.completeFocusCalls)

        // 验证文件被隔离或删除
        assertFalse("损坏的源快照文件必须被清理", filePersistence.sessionFile.exists())
        assertTrue("损坏文件应被隔离命名为 .corrupted", filePersistence.corruptedFile.exists())
    }

    @Test
    fun test9_twoConcurrentBeginsOnlyOneSucceeds() = runTest {
        val clock = TestMonotonicClock()
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        // 模拟多线程同时调用 begin
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                coordinator.begin(createFocusSession("concurrent-session-$i"))
            }
        }.awaitAll()

        val successCount = results.count { it }
        val failCount = results.count { !it }

        assertEquals("并发 begin 必须且仅有一个成功", 1, successCount)
        assertEquals(19, failCount)
        assertEquals(CoordinatorState.ACTIVE, coordinator.coordinatorState.value)
    }

    @Test
    fun test10_focusAndExamMutuallyExclusive() = runTest {
        val clock = TestMonotonicClock()
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        val focus = createFocusSession("focus-1")
        val exam = createExamSession("exam-1")

        assertTrue(coordinator.begin(focus))
        assertFalse("已在专注计时，开始模考必须被拒绝", coordinator.begin(exam))
        assertEquals(ActiveSessionKind.FOCUS, coordinator.activeKind())

        coordinator.cancel()
        coordinator.awaitPersistence()

        assertTrue(coordinator.begin(exam))
        assertFalse("已在模考计时，开始专注必须被拒绝", coordinator.begin(focus))
        assertEquals(ActiveSessionKind.EXAM, coordinator.activeKind())
    }

    @Test
    fun test11_countdownRecoveryBoundary() = runTest {
        val clock = TestMonotonicClock(now = 10_000L, bootId = "boot-1")
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator1 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        // 30 分钟倒计时 = 1800 秒
        val session = createFocusSession("countdown-1", targetDuration = 1800L)
        coordinator1.begin(session)
        coordinator1.awaitPersistence()

        // 进程死掉后过了 2000 秒（超过了目标 1800 秒）
        clock.advanceBy(2000 * 1000L)

        // 同一 boot 恢复
        val coordinator2 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)
        val restored = coordinator2.restorePersistedNow()
        assertNotNull(restored)

        // 恢复边界：有效计时夹紧到目标上限，剩余时长不能为负数
        val effectiveMs = restored!!.accumulatedActiveMs
        assertEquals(1800 * 1000L, effectiveMs)
        val remaining = (restored.targetDurationSeconds - effectiveMs / 1000L).coerceAtLeast(0L)
        assertEquals(0L, remaining)

        // 完成落库
        val success = coordinator2.complete(actualSeconds = 1800L)
        assertTrue(success)
        assertEquals(1, mockPersistence.completeFocusCalls)
    }

    @Test
    fun test12_rebootSemanticsRestoreAsPausedWithoutFakingElapsed() = runTest {
        val clock = TestMonotonicClock(now = 100_000L, bootId = "boot-before-reboot")
        val filePersistence = FileTimerSessionPersistence(tempFolder.root, clock, Dispatchers.Unconfined)
        val mockPersistence = MockSessionPersistence(filePersistence)
        val coordinator1 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)

        coordinator1.begin(createFocusSession("session-reboot"))
        coordinator1.awaitPersistence()

        // 运行了 60 秒后持久化
        clock.advanceBy(60_000L)
        coordinator1.update { it.copy(accumulatedActiveMs = 60_000L) }
        coordinator1.awaitPersistence()

        // 模拟设备重启：
        // 1. bootId 变更；
        // 2. 单调时钟复位为极小值（开机 5 秒）
        clock.bootId = "boot-after-reboot"
        clock.setNow(5_000L)

        // 进程在重启后启动
        val coordinator2 = ActiveSessionCoordinatorCore(mockPersistence, clock, this)
        val restored = coordinator2.restorePersistedNow()
        coordinator2.awaitPersistence()

        assertNotNull(restored)
        assertTrue("Reboot 后单调时钟基准已失效，必须恢复为暂停状态待用户确认", restored!!.paused)
        assertEquals("严禁伪造流逝时间，保持上一次安全落盘的累积时间", 60_000L, restored.accumulatedActiveMs)
        assertEquals(TimerPhase.PAUSED, coordinator2.currentTimerSnapshot?.phase)
        assertNull("单调时钟基准必须置空", coordinator2.currentTimerSnapshot?.resumedAtMonotonicMs)

        // 检查快照文件已被更新为 PAUSED 状态并记录了新的 bootId
        val recordOnDisk = filePersistence.loadActiveSessionRecord()
        assertNotNull(recordOnDisk)
        assertTrue(recordOnDisk!!.activeSession.paused)
        assertEquals("boot-after-reboot", recordOnDisk.bootId)
    }
}
