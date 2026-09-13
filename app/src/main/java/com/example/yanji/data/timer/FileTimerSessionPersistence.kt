package com.example.yanji.data.timer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * 基于 context.noBackupFilesDir 的活动计时会话持久化实现。
 *
 * 保证：
 * 1. 位于 noBackupFilesDir，不进入 Android Cloud Backup / Device Transfer，新设备不会携带正在进行的 Timer；
 * 2. 写入走 tmp file -> write/close/flush -> atomic/controlled rename，防止半写损坏；
 * 3. 读取损坏文件时不 crash、不泄露敏感日志、不制造 Completed Session，自动隔离/清除坏文件；
 * 4. 纯值持久化，格式包含 formatVersion, activeSession, timerSnapshot, lastPersistedWallClockMs, bootId。
 */
class FileTimerSessionPersistence(
    private val directory: File,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TimerSessionPersistence {

    constructor(
        context: Context,
        clock: MonotonicClock = SystemMonotonicClock
    ) : this(context.applicationContext.noBackupFilesDir, clock, Dispatchers.IO)

    companion object {
        const val FILE_NAME = "active_timer_session.json"
        const val TMP_FILE_NAME = "active_timer_session.json.tmp"
        const val CORRUPTED_FILE_NAME = "active_timer_session.json.corrupted"
        private const val TAG = "FileTimerPersistence"

        private val json = Json {
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    val sessionFile: File get() = File(directory, FILE_NAME)
    val tmpFile: File get() = File(directory, TMP_FILE_NAME)
    val corruptedFile: File get() = File(directory, CORRUPTED_FILE_NAME)

    override suspend fun completeFocus(
        session: ActiveSession,
        actualSeconds: Long,
        pausedSeconds: Long,
        pauseCount: Int,
        endEpochMs: Long
    ) {
        // 完成落库由外层 TimerStore Room DAO 负责
    }

    override suspend fun completeExam(
        session: ActiveSession,
        actualSeconds: Long,
        endEpochMs: Long
    ) {
        // 完成落库由外层 TimerStore Room DAO 负责
    }

    override suspend fun saveActiveSession(record: ActiveSessionRecord): Unit = withContext(ioDispatcher) {
        if (!directory.exists() && !directory.mkdirs()) {
            error("Unable to create timer persistence directory")
        }
        val payload = json.encodeToString(ActiveSessionRecord.serializer(), record)
        writeAtomically(payload)
    }

    override suspend fun saveActiveSession(session: ActiveSession): Unit = withContext(ioDispatcher) {
        val snapshot = TimerSnapshot(
            phase = if (session.paused) TimerPhase.PAUSED else TimerPhase.RUNNING,
            startedAtEpochMs = session.startedAtEpochMs,
            targetDurationSeconds = session.targetDurationSeconds,
            accumulatedActiveMs = session.accumulatedActiveMs,
            resumedAtMonotonicMs = if (session.paused) null else clock.nowMs(),
            pauseCount = session.pauseCount
        )
        val record = ActiveSessionRecord(
            formatVersion = ActiveSessionRecord.CURRENT_FORMAT_VERSION,
            activeSession = session,
            timerSnapshot = snapshot,
            lastPersistedWallClockMs = System.currentTimeMillis(),
            bootId = clock.currentBootId()
        )
        saveActiveSession(record)
    }

    override suspend fun clearActiveSession(): Unit = withContext(ioDispatcher) {
        deleteIfPresent(sessionFile)
        deleteIfPresent(tmpFile)
    }

    override suspend fun loadActiveSessionRecord(): ActiveSessionRecord? = withContext(ioDispatcher) {
        val target = sessionFile
        if (!target.exists() || !target.isFile) return@withContext null
        return@withContext try {
            val raw = target.readText(Charsets.UTF_8)
            if (raw.isBlank()) {
                isolateCorruptedFile()
                return@withContext null
            }
            // 兼容首字母大写与小写字段命名
            val normalized = raw
                .replace("\"ActiveSession\":", "\"activeSession\":")
                .replace("\"TimerSnapshot\":", "\"timerSnapshot\":")
            val record = json.decodeFromString(ActiveSessionRecord.serializer(), normalized)
            if (record.formatVersion <= 0 || record.activeSession.sessionId.isBlank()) {
                isolateCorruptedFile()
                return@withContext null
            }
            record
        } catch (e: Exception) {
            // 损坏文件处理：不 crash、不制造 Completed Session、删除或隔离损坏文件、不泄露敏感日志
            isolateCorruptedFile()
            safeLogW("Active session snapshot corrupted; isolating file")
            null
        }
    }

    override suspend fun loadActiveSession(): ActiveSession? =
        loadActiveSessionRecord()?.activeSession

    private fun writeAtomically(content: String) {
        val tmp = tmpFile
        val target = sessionFile
        val backup = File(directory, "$FILE_NAME.bak")
        FileOutputStream(tmp).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }

        deleteIfPresent(backup)
        if (target.exists() && !target.renameTo(backup)) {
            deleteIfPresent(tmp)
            error("Unable to stage previous timer snapshot")
        }
        if (!tmp.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            deleteIfPresent(tmp)
            error("Unable to atomically replace timer snapshot")
        }
        if (backup.exists() && !backup.delete()) {
            safeLogW("Timer snapshot updated but old backup cleanup failed")
        }
    }

    private fun deleteIfPresent(file: File) {
        if (file.exists() && !file.delete()) error("Unable to delete ${file.name}")
    }

    private fun isolateCorruptedFile() {
        runCatching {
            val target = sessionFile
            if (target.exists()) {
                val dest = corruptedFile
                if (dest.exists()) {
                    dest.delete()
                }
                val renamed = target.renameTo(dest)
                if (!renamed) {
                    target.copyTo(dest, overwrite = true)
                    target.delete()
                }
            }
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    private fun safeLogW(message: String) {
        runCatching {
            Log.w(TAG, message)
        }
    }
}
