package com.example.yanji.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CharacterCodingException
import java.util.UUID

internal class BackupOperationException(message: String) : IllegalStateException(message)
internal class BackupRestoreOutcomeException(cause: Exception) : IllegalStateException(
    "未能确认恢复结果，请先检查本机数据，再重新选择备份。", cause
)
internal data class BackupInput(val fileName: String, val open: () -> InputStream)
internal data class BackupOutput(val fileName: String, val open: () -> OutputStream)
internal data class BackupImportPreview(
    val token: String,
    val fileName: String,
    val backup: YanjiBackup,
    val warnings: List<String>
)

/**
 * Owns the selected document until confirmation. No JSON or file URI crosses the RN bridge.
 * A token refers to the exact bytes that were previewed, so external file edits cannot alter
 * the restore between preview and confirmation. Only one picker/read/write may run at a time.
 */
internal class BackupDocumentWorkflow(
    private val exportJson: suspend () -> String,
    private val importJson: suspend (String) -> BackupImportResult,
    private val beforeImport: suspend () -> Unit
) {
    private data class StagedImport(val token: String, val raw: String)
    private val operation = Mutex()
    private var staged: StagedImport? = null

    private suspend fun <T> exclusive(block: suspend () -> T): T {
        if (!operation.tryLock()) throw BackupOperationException("备份操作正在进行，请稍候")
        try { return block() } finally { operation.unlock() }
    }

    suspend fun export(select: suspend () -> BackupOutput?): String? = exclusive {
        val document = select() ?: return@exclusive null
        withContext(Dispatchers.IO) {
            // Collect after the user chooses a destination, not before a potentially long picker.
            val raw = exportJson()
            BackupDocumentIO.write(raw, document.open)
        }
        document.fileName
    }

    suspend fun prepareImport(select: suspend () -> BackupInput?): BackupImportPreview? = exclusive {
        withContext(Dispatchers.IO) { beforeImport() }
        staged = null
        val document = select() ?: return@exclusive null
        val decoded = withContext(Dispatchers.IO) {
            val raw = try {
                BackupDocumentIO.read(document.open())
            } catch (_: CharacterCodingException) {
                throw BackupOperationException("文件不是 UTF-8 编码的研迹备份，请选择研迹导出的 JSON 文件")
            }
            when (val result = BackupCodec.decode(raw)) {
                is BackupDecodeResult.Failure -> throw BackupOperationException(result.message)
                is BackupDecodeResult.Success -> raw to result
            }
        }
        val token = UUID.randomUUID().toString()
        staged = StagedImport(token, decoded.first)
        BackupImportPreview(token, document.fileName, decoded.second.backup, decoded.second.warnings)
    }

    suspend fun restore(token: String): List<String> = exclusive {
        val prepared = staged?.takeIf { it.token == token }
            ?: throw BackupOperationException("备份预览已失效，请重新选择文件并确认")
        withContext(Dispatchers.IO) { beforeImport() }
        val result = try {
            withContext(Dispatchers.IO) { importJson(prepared.raw) }
        } catch (cancelled: CancellationException) {
            staged = null
            throw cancelled
        } catch (error: Exception) {
            // Only an explicit engine Failure guarantees no writes landed. Never replay an
            // indeterminate restore with the old confirmation token.
            staged = null
            throw BackupRestoreOutcomeException(error)
        }
        when (result) {
            is BackupImportResult.Failure -> throw BackupOperationException(result.message)
            is BackupImportResult.Success -> {
                staged = null
                result.warnings
            }
        }
    }

    suspend fun discard(token: String): Boolean {
        // Access is confined to the module's Main dispatcher, including continuations after IO.
        // Discard releases the stored reference even while a restore owns its own local copy.
        if (staged?.token != token) return false
        staged = null
        return true
    }

    fun close() { staged = null }
}
