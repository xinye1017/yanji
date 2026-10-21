package com.example.yanji.data.backup

import android.content.Context
import com.example.yanji.data.Subject
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiTime
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.data.timer.ActiveSessionCoordinator
import com.example.yanji.data.timer.ActiveSessionCoordinatorCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * 备份导出 / 导入的**唯一归属者**。
 *
 * 从 `YanjiRepository` 抽出的理由：这是全仓库唯一会**破坏性整表替换**用户数据的路径，
 * 把它独立出来可以让「事务边界、校验顺序、失败回滚」的审查与后续插桩测试只针对这一处，
 * 不再混在 1000 行的 god-object 里。
 *
 * 职责边界（刻意收窄）：
 *  - 采集负载（[BackupTransfer.collect]）并编码为 JSON（[BackupCodec.encode]）；
 *  - 解码 + 校验（[BackupCodec.decode]），失败时给出面向用户可读的原因；
 *  - 导入前落一份 `filesDir/pre_import_snapshots/` 快照；
 *  - 在**单个事务**内整表替换（[BackupTransfer.applyInTransaction]），任一步失败整体回滚；
 *  - 事务成功后的内存镜像校正改为**回调**上交调用方（学科镜像、会话指针、活动计时），
 *    本类不直接持有 `YanjiRepository` 的内部状态。
 *
 * **不负责**：UI/SAF 文件读写、备份格式演进（属 [BackupCodec]/[YanjiBackup]）。
 *
 * 导入顺序固定为：严格解码 → 预留活动会话所有权并复核持久快照 →
 * 同步落盘唯一的安全快照 → 单事务整表替换 → 提交后的镜像校正。
 * 快照或事务失败都在提交前中止；镜像回调失败只作为成功结果中的告警返回。
 */
class BackupStore(
    /** 数据库延迟提供者：与 `YanjiRepository` 一样，进程可能先于 `init` 构造本类。 */
    private val dbProvider: () -> YanjiDatabase?,
    /** 用于解析 `filesDir` 与包版本号；null 时导出仍可用，但导入因无法创建快照而拒绝。 */
    private val contextProvider: () -> Context?,
    /** 导入事务成功后的镜像校正回调。由组合根（Repository）注入，避免本类反持其内部状态。 */
    private val onActiveFocusCleared: () -> Unit,
    private val onSubjectsReplaced: (List<Subject>) -> Unit,
    private val onSettingsReplaced: (UserSettings) -> Unit = {},
    private val snapshotWriter: (suspend (YanjiBackup) -> String)? = null,
    private val coordinator: ActiveSessionCoordinatorCore = ActiveSessionCoordinator
) {

    /** 采集当前全部数据，生成可序列化的备份负载。实现见 [BackupTransfer.collect]。 */
    private suspend fun buildPayload(): YanjiBackup {
        val db = dbProvider() ?: throw IllegalStateException("数据库尚未初始化，请重启研迹后重试")
        return BackupTransfer.collect(db, appVersionName())
    }

    private fun appVersionName(): String = runCatching {
        val ctx = contextProvider() ?: return@runCatching ""
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 导出为 JSON 字符串。写文件（SAF）由 UI 层负责，这里只产出内容。 */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        BackupCodec.encode(buildPayload())
    }

    /**
     * 从 JSON 导入并整表替换本机数据。
     *
     * @return 成功时带回落盘快照路径与来自 [BackupCodec] 的提醒；失败时 message 可直接展示给用户。
     */
    suspend fun importJson(rawJson: String): BackupImportResult = withContext(Dispatchers.IO) {
        val decoded = BackupCodec.decode(rawJson)
        if (decoded is BackupDecodeResult.Failure) {
            return@withContext BackupImportResult.Failure(decoded.message)
        }
        val backup = (decoded as BackupDecodeResult.Success).backup

        if (dbProvider() == null) {
            return@withContext BackupImportResult.Failure("数据库尚未初始化，请重启研迹后重试")
        }
        // The import guard inspects persisted state without invoking cold-start recovery,
        // which may quarantine a corrupt snapshot and hide it from a subsequent check.
        if (coordinator.isBusy) {
            return@withContext BackupImportResult.Failure("正在计时中，请先结束当前的专注或模考再导入")
        }

        var committed = false
        var snapshotPath: String? = null
        try {
            coordinator.withBackupRestore {
                val db = dbProvider()
                    ?: throw IllegalStateException("数据库尚未初始化，请重启研迹后重试")
                // Snapshot creation is fail-closed; replacement remains one atomic transaction.
                snapshotPath = writePreImportSnapshot()
                BackupTransfer.applyInTransaction(db, backup)
                committed = true

                val warnings = decoded.warnings.toMutableList()
                try {
                    backup.settings?.let { onSettingsReplaced(it.toDomain()) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    warnings += "数据已恢复，但设置界面刷新失败，请重启研迹"
                }
                try {
                    onActiveFocusCleared()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    warnings += "数据已恢复，但活动计时界面刷新失败，请重启研迹"
                }
                try {
                    val restoredSubjects = db.subjectDao().getAll().map { it.toDomainModel() }
                    if (restoredSubjects.isNotEmpty()) onSubjectsReplaced(restoredSubjects)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    warnings += "数据已恢复，但学科界面刷新失败，请重启研迹"
                }
                BackupImportResult.Success(backup, snapshotPath, warnings)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (committed) {
                BackupImportResult.Success(
                    backup,
                    snapshotPath,
                    decoded.warnings + "数据已恢复，但界面状态刷新失败，请重启研迹"
                )
            } else {
                BackupImportResult.Failure(
                    "导入未完成，本机数据未改变（${error.localizedMessage ?: "未知错误"}）"
                )
            }
        }
    }

    private suspend fun writePreImportSnapshot(): String {
        val payload = buildPayload()
        // A safety snapshot must satisfy the same byte limit as a user-selected backup.
        // Validate before creating the file and write exactly these validated UTF-8 bytes.
        val bytes = BackupDocumentIO.encodeUtf8(BackupCodec.encode(payload))
        snapshotWriter?.let { return it(payload) }
        val ctx = contextProvider()
            ?: throw IllegalStateException("应用上下文尚未初始化，无法创建导入前快照")
        val dir = File(ctx.filesDir, "pre_import_snapshots")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("无法创建导入前快照目录")
        }
        val stamp = YanjiTime.backupStamp(Instant.now())
        val file = File.createTempFile("yanji-pre-import-$stamp-", ".json", dir)
        return try {
            FileOutputStream(file).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            file.absolutePath
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
