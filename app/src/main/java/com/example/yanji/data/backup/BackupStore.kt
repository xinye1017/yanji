package com.example.yanji.data.backup

import android.content.Context
import com.example.yanji.data.Subject
import com.example.yanji.data.YanjiTime
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.data.timer.ActiveSessionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
 * 事务语义、JSON 结构、表替换顺序、校验逻辑与失败回滚**逐字沿用**抽取前的实现，未做任何变更。
 */
class BackupStore(
    /** 数据库延迟提供者：与 `YanjiRepository` 一样，进程可能先于 `init` 构造本类。 */
    private val dbProvider: () -> YanjiDatabase?,
    /** 用于解析 `filesDir` 与包版本号；null 表示尚未 attach（导出仍可用，快照会被跳过）。 */
    private val contextProvider: () -> Context?,
    /** 导入事务成功后的镜像校正回调。由组合根（Repository）注入，避免本类反持其内部状态。 */
    private val onActiveFocusCleared: () -> Unit,
    private val onSubjectsReplaced: (List<Subject>) -> Unit
) {

    /** 采集当前全部数据，生成可序列化的备份负载。实现见 [BackupTransfer.collect]。 */
    private suspend fun buildPayload(): YanjiBackup {
        val db = dbProvider() ?: return YanjiBackup(exportedAt = System.currentTimeMillis())
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
        if (ActiveSessionCoordinator.isBusy) {
            return@withContext BackupImportResult.Failure("正在计时中，请先结束当前的专注或模考再导入")
        }

        val decoded = BackupCodec.decode(rawJson)
        if (decoded is BackupDecodeResult.Failure) {
            return@withContext BackupImportResult.Failure(decoded.message)
        }
        val backup = (decoded as BackupDecodeResult.Success).backup

        val db = dbProvider()
            ?: return@withContext BackupImportResult.Failure("数据库尚未初始化，请重启研迹后重试")

        // 先留一份「导入前」快照。即使导入事务回滚，这份快照也不受影响。
        val snapshotPath = runCatching { writePreImportSnapshot() }.getOrNull()

        val applied = runCatching {
            BackupTransfer.applyInTransaction(db, backup)
        }
        if (applied.isFailure) {
            val reason = applied.exceptionOrNull()?.localizedMessage ?: "未知错误"
            return@withContext BackupImportResult.Failure("导入失败，已回滚，本机数据未改变（$reason）")
        }

        onActiveFocusCleared()

        // 学科镜像也要立即刷新：导入是整表替换，若不在这里同步，紧接着的同步查询
        // （统计分桶、AI 提示词）会读到导入前的旧学科列表。
        val restoredSubjects = db.subjectDao().getAll().map { it.toDomainModel() }
        if (restoredSubjects.isNotEmpty()) {
            onSubjectsReplaced(restoredSubjects)
        }

        BackupImportResult.Success(backup, snapshotPath, decoded.warnings)
    }

    private suspend fun writePreImportSnapshot(): String? {
        val ctx = contextProvider() ?: return null
        val dir = File(ctx.filesDir, "pre_import_snapshots").apply { mkdirs() }
        val stamp = YanjiTime.backupStamp(Instant.now())
        val file = File(dir, "yanji-pre-import-$stamp.json")
        file.writeText(BackupCodec.encode(buildPayload()))
        return file.absolutePath
    }
}
