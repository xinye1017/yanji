package com.example.yanji.data.backup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BackupDocumentWorkflowTest {
    private val json = BackupCodec.encode(YanjiBackup(exportedAt = 1))
    private fun document(raw: String = json) = BackupInput("学习记录.json") { ByteArrayInputStream(raw.toByteArray()) }

    @Test fun cancellingPickerDoesNotExportOrRestoreOrReportSuccess() = runBlocking {
        var calls = 0
        val workflow = BackupDocumentWorkflow({ calls++; json }, { error("must not restore") }, {})
        assertNull(workflow.export { null })
        assertNull(workflow.prepareImport { null })
        assertEquals(0, calls)
    }

    @Test fun previewOnlyValidatesAndConfirmedRestoreUsesTheSameContentOnce() = runBlocking {
        var imports = 0
        var fail = true
        val workflow = BackupDocumentWorkflow({ json }, { raw ->
            assertEquals(json, raw)
            imports++
            if (fail) BackupImportResult.Failure("注入写入失败，本机数据未改变")
            else BackupImportResult.Success(YanjiBackup(exportedAt = 1), null)
        }, {})
        val preview = workflow.prepareImport { document() }!!
        assertEquals(0, imports)
        assertEquals("学习记录.json", preview.fileName)
        assertTrue(runCatching { workflow.restore(preview.token) }.isFailure)
        fail = false
        workflow.restore(preview.token)
        assertEquals(2, imports)
        assertTrue(runCatching { workflow.restore(preview.token) }.isFailure)
        assertEquals("A successful confirmation token must not replay the destructive restore", 2, imports)
    }

    @Test fun cancelledOrReplacedPreviewCannotBeRestored() = runBlocking {
        var imports = 0
        val workflow = BackupDocumentWorkflow({ json }, { imports++; error("must not restore") }, {})
        val first = workflow.prepareImport { document() }!!
        assertTrue(workflow.discard(first.token))
        assertTrue(runCatching { workflow.restore(first.token) }.isFailure)
        val second = workflow.prepareImport { document() }!!
        assertNull(workflow.prepareImport { null })
        assertTrue(runCatching { workflow.restore(second.token) }.isFailure)
        assertEquals(0, imports)
    }

    @Test fun failedValidationAndActiveSessionNeverReachImport() = runBlocking {
        var busy = false
        var imports = 0
        var pickerCalls = 0
        val workflow = BackupDocumentWorkflow({ json }, { imports++; error("must not restore") }, {
            if (busy) throw BackupOperationException("请先结束专注或模考")
        })
        assertTrue(runCatching { workflow.prepareImport { document("{}") } }.isFailure)
        val preview = workflow.prepareImport { document() }!!
        busy = true
        assertTrue(runCatching { workflow.prepareImport { pickerCalls++; document() } }.isFailure)
        assertEquals(0, pickerCalls)
        assertTrue(runCatching { workflow.restore(preview.token) }.isFailure)
        assertEquals(0, imports)
    }

    @Test fun duplicateOperationsAreRefusedUntilFileCloseCompletesAndFailureAllowsRetry() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val workflow = BackupDocumentWorkflow({ calls++; entered.complete(Unit); release.await(); json }, { error("unused") }, {})
        val write = async { workflow.export { BackupOutput("backup.json") { ByteArrayOutputStream() } } }
        entered.await()
        assertFalse(write.isCompleted)
        assertTrue(runCatching { workflow.export { error("duplicate picker") } }.isFailure)
        assertTrue(runCatching { workflow.prepareImport { error("duplicate picker") } }.isFailure)
        release.complete(Unit)
        assertEquals("backup.json", write.await())
        assertEquals(1, calls)
        assertTrue(runCatching {
            workflow.export { BackupOutput("broken.json") { object : ByteArrayOutputStream() {
                override fun close() { throw IOException("close failed") }
            } } }
        }.isFailure)
        assertEquals("retry.json", workflow.export { BackupOutput("retry.json") { ByteArrayOutputStream() } })
    }

    @Test fun unexpectedRestoreExceptionConsumesTokenInsteadOfReplayingAnUnknownCommit() = runBlocking {
        var imports = 0
        val workflow = BackupDocumentWorkflow({ json }, {
            imports++
            throw IOException("injected ambiguous post-commit failure")
        }, {})
        val preview = workflow.prepareImport { document() }!!
        assertTrue(runCatching { workflow.restore(preview.token) }.exceptionOrNull() is BackupRestoreOutcomeException)
        assertTrue(runCatching { workflow.restore(preview.token) }.isFailure)
        assertEquals(1, imports)
    }

    @Test fun discardDuringRestoreReleasesStagedContentEvenWhenImportLaterFails() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val workflow = BackupDocumentWorkflow({ json }, {
            entered.complete(Unit)
            release.await()
            BackupImportResult.Failure("已回滚")
        }, {})
        val preview = workflow.prepareImport { document() }!!
        val restore = async { runCatching { workflow.restore(preview.token) } }
        entered.await()
        assertTrue(workflow.discard(preview.token))
        release.complete(Unit)
        assertTrue(restore.await().isFailure)
        assertFalse(workflow.discard(preview.token))
    }
}
