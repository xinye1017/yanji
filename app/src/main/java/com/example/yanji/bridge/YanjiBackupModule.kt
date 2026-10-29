package com.example.yanji.bridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import com.example.yanji.data.backup.*
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** SAF owns file access; the backup engine remains the only owner of database replacement. */
class YanjiBackupModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = YanjiRepository.getInstance()
    private var picker: CancellableContinuation<Uri?>? = null
    private val workflow = BackupDocumentWorkflow(
        exportJson = { repository.exportBackupJson() },
        importJson = { repository.importBackupJson(it) },
        beforeImport = {
            try {
                ActiveSessionCoordinator.withBackupRestore { Unit }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw BackupOperationException("活动计时状态未能确认，请先检查并结束专注或模考（包括暂停的会话）后再恢复")
            }
        }
    )

    private val activityListener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode != PICK_BACKUP_DOCUMENT) return
            val waiting = picker ?: return
            picker = null
            if (!waiting.isActive) return
            when {
                resultCode == Activity.RESULT_CANCELED -> waiting.resume(null)
                resultCode == Activity.RESULT_OK && data?.data != null -> waiting.resume(data.data)
                else -> waiting.resumeWithException(BackupOperationException("未取得文件访问权限，请重新选择备份文件"))
            }
        }
    }

    private val lifecycleListener = object : LifecycleEventListener {
        override fun onHostResume() = Unit
        override fun onHostPause() = Unit
        override fun onHostDestroy() {
            val waiting = picker
            picker = null
            if (waiting?.isActive == true) {
                waiting.resumeWithException(BackupOperationException("文件选择已中断，请重试"))
            }
        }
    }

    init {
        reactContext.addActivityEventListener(activityListener)
        reactContext.addLifecycleEventListener(lifecycleListener)
    }

    override fun getName() = "YanjiBackupModule"

    override fun invalidate() {
        reactContext.removeActivityEventListener(activityListener)
        reactContext.removeLifecycleEventListener(lifecycleListener)
        scope.cancel()
        picker = null
        workflow.close()
        super.invalidate()
    }

    @ReactMethod
    fun exportBackup(promise: Promise) = launchOperation(promise, "E_BACKUP_EXPORT", "导出失败，备份文件可能未完整保存，请重新导出") {
        val name = workflow.export {
            val defaultName = "yanji-${YanjiTime.backupStamp()}.json"
            val uri = pickDocument(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, defaultName)
            }) ?: return@export null
            BackupOutput(documentName(uri, defaultName)) {
                checkNotNull(reactContext.contentResolver.openOutputStream(uri, "wt")) { "No output stream" }
            }
        }
        if (name == null) null else Arguments.createMap().apply { putString("fileName", name) }
    }

    @ReactMethod
    fun prepareBackupImport(promise: Promise) = launchOperation(promise, "E_BACKUP_READ", "无法读取备份文件，请检查文件访问权限后重试") {
        val preview = workflow.prepareImport {
            // Historical .yanji-backup files may be advertised as octet-stream by providers.
            // The codec validates content; an extension or provider MIME type is not trusted.
            val uri = pickDocument(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }) ?: return@prepareImport null
            BackupInput(documentName(uri, "研迹备份")) {
                checkNotNull(reactContext.contentResolver.openInputStream(uri)) { "No input stream" }
            }
        }
        preview?.let(::previewMap)
    }

    @ReactMethod
    fun restoreBackupImport(token: String, promise: Promise) = launchOperation(promise, "E_BACKUP_RESTORE", "恢复未完成，请重试") {
        Arguments.createMap().apply {
            putArray("warnings", Arguments.fromList(workflow.restore(token)))
        }
    }

    @ReactMethod
    fun discardBackupImport(token: String, promise: Promise) = launchOperation(promise, "E_BACKUP_DISCARD", "取消恢复失败，请重试") {
        workflow.discard(token)
    }

    private fun launchOperation(promise: Promise, code: String, failureMessage: String, operation: suspend () -> Any?) {
        scope.launch {
            try {
                promise.resolve(operation())
            } catch (cancelled: CancellationException) {
                promise.reject("E_BACKUP_INTERRUPTED", "操作已中断，未能确认结果。请先检查本机数据或备份文件，再重新操作。")
                throw cancelled
            } catch (error: Exception) {
                val unknownRestore = error is BackupRestoreOutcomeException
                val message = if (error is BackupOperationException || unknownRestore) error.message else failureMessage
                promise.reject(if (unknownRestore) "E_BACKUP_RESTORE_UNKNOWN" else code, message, error)
            }
        }
    }

    @Suppress("DEPRECATION") // ReactActivity forwards this result to ActivityEventListener.
    private suspend fun pickDocument(intent: Intent): Uri? = suspendCancellableCoroutine { waiting ->
        val activity = reactContext.currentActivity
        if (activity == null) {
            waiting.resumeWithException(BackupOperationException("当前页面不可用，请返回研迹后重试"))
        } else {
            picker = waiting
            waiting.invokeOnCancellation { if (picker === waiting) picker = null }
            try {
                activity.startActivityForResult(intent, PICK_BACKUP_DOCUMENT)
            } catch (error: Exception) {
                picker = null
                waiting.resumeWithException(BackupOperationException("无法打开系统文件选择器，请检查设备是否有可用的文件管理器"))
            }
        }
    }

    private suspend fun documentName(uri: Uri, fallback: String): String = withContext(Dispatchers.IO) {
        runCatching {
            reactContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
            }
        }.getOrNull() ?: fallback
    }

    private fun previewMap(preview: BackupImportPreview): WritableMap = Arguments.createMap().apply {
        val backup = preview.backup
        putString("token", preview.token)
        putString("fileName", preview.fileName)
        putInt("schemaVersion", backup.schemaVersion)
        putDouble("exportedAt", backup.exportedAt.toDouble())
        putString("appVersionName", backup.appVersionName)
        putBoolean("replacesSettings", backup.settings != null)
        putBoolean("replacesSubjects", backup.subjects.isNotEmpty())
        putArray("warnings", Arguments.fromList(preview.warnings))
        putMap("counts", Arguments.createMap().apply {
            putInt("focusSessions", backup.focusSessions.size)
            putInt("examSessions", backup.examSessions.size)
            putInt("noteEntries", backup.noteEntries.size)
            putInt("chatSessions", backup.chatSessions.size)
            putInt("chatMessages", backup.chatMessages.size)
            putInt("checkIns", backup.checkIns.size)
            putInt("quickStartPresets", backup.quickStartPresets.size)
            putInt("studyTasks", backup.studyTasks.size)
            putInt("subjects", backup.subjects.size)
            putInt("scheduleTemplates", backup.scheduleTemplates.size)
        })
    }

    private companion object { const val PICK_BACKUP_DOCUMENT = 7314 }
}
