package com.example.yanji.data.note

import com.example.yanji.data.NoteEntry
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.CoroutineScope
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.launch

/**
 * 随笔动作归属者。Room 是唯一事实来源；不保留全历史内存镜像。
 * v15 起按 id 归一化，一天允许多篇随笔；createdAt 仅在首次落库时生成。
 */
internal class NoteStore(
    private val scope: CoroutineScope,
    private val dbProvider: () -> YanjiDatabase?
) {

    /**
     * 保存随笔。**以 Room 为唯一事实来源**。
     *
     * 修复过的问题：
     * 1. 旧代码把 `updatedAt` 只写进内存副本，却把原始 entry 写库，两边不一致；
     * 2. 旧代码按 `date || id` 匹配内存行、却按 `id` 覆盖写库，会产生两条同日期随笔。
     *
     * v15 起改为**按 id 归一化**（不再按 date）：一天允许多篇随笔，
     * 每篇的归属只由自己的 id 决定。`createdAt` 仍只在首次落库时生成，
     * 因此「初次编辑完毕时间」不会被后续编辑刷新。
     */
    fun addOrUpdate(entry: NoteEntry) {
        scope.launch {
            addOrUpdateAndAwait(entry)
        }
    }

    /**
     * [addOrUpdate] 的**可等待**版本：返回时 Room 写入已经落库。
     *
     * 存在的理由：`addOrUpdate` 自己 `launch`，桥接层 `saveQuickNote` 会在行还不存在时就
     * resolve，JS 侧紧接着的读取会漏掉这条记录。这里把同一条归一化规则原样搬过来
     * （按 id 保留首次 `createdAt`、刷新 `updatedAt`），只是把执行权交还调用方。
     *
     * 仍然只写 Room。
     */
    suspend fun addOrUpdateAndAwait(entry: NoteEntry) {
        val dao = dbProvider()?.noteEntryDao() ?: return
        val existing = dao.getById(entry.id)
        val normalized = entry.copy(
            createdAt = existing?.createdAt ?: entry.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        dao.insert(NoteEntryEntity.fromDomainModel(normalized))
    }

    /**
     * 等待 Room 原子切换收藏完成；只更新 `isFavorite` 与 `updatedAt`。
     */
    suspend fun toggleFavoriteAndAwait(id: String): Boolean {
        val dao = dbProvider()?.noteEntryDao() ?: return false
        return dao.toggleFavorite(id, System.currentTimeMillis()) > 0
    }

    /**
     * 更新正文的**可等待版本**：返回 false 表示 id 不存在（或数据库未就绪）。
     *
     * 只写 Room：这是编辑既有随笔，不是新建。
     */
    suspend fun updateContentAndAwait(id: String, content: String): Boolean {
        val dao = dbProvider()?.noteEntryDao() ?: return false
        return dao.updateContent(id, content, System.currentTimeMillis()) > 0
    }

    /**
     * 原子切换疑点解决状态：返回 false 表示 id 不存在（或数据库未就绪）。
     *
     * 只按 id 读这一行的 `isResolved`，不依赖全量随笔镜像；读取与写回在同一个
     * 事务里完成，两次并发切换不会都读到同一个旧值。
     */
    suspend fun toggleResolvedAndAwait(id: String): Boolean {
        val db = dbProvider() ?: return false
        // Writer-connection transaction rather than `withTransaction`: the latter
        // only exists in the Android room-ktx artifact, and the JVM tests run
        // against room-runtime-jvm where it is a NoSuchMethodError at runtime.
        var toggled = false
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val current = db.noteEntryDao().getResolvedById(id) ?: return@immediateTransaction
                toggled = setResolvedAndAwait(id, !current)
            }
        }
        return toggled
    }

    /** 显式写入疑点解决状态；返回 false 表示 id 不存在（或数据库未就绪）。 */
    suspend fun setResolvedAndAwait(id: String, isResolved: Boolean): Boolean {
        val dao = dbProvider()?.noteEntryDao() ?: return false
        val now = System.currentTimeMillis()
        val count = dao.setResolved(
            id = id,
            isResolved = if (isResolved) 1 else 0,
            resolvedAt = if (isResolved) now else null,
            updatedAt = now
        )
        return count > 0
    }

    /**
     * 人工纠正分类的**可等待版本**：返回 false 表示 id 不存在（或数据库未就绪）。
     *
     * AI 只给建议，最终分类由用户定夺——决策模型失败时会静默回落灵感，
     * 这条路径就是那次回落的纠正入口，所以必须真正落库。
     * 同一条语句顺带清空 `isResolved` / `resolvedAt`（见 DAO 注释）。
     */
    suspend fun setCategoryAndAwait(id: String, category: String): Boolean {
        val dao = dbProvider()?.noteEntryDao() ?: return false
        val count = dao.setCategory(id, category, System.currentTimeMillis())
        return count > 0
    }

    suspend fun deleteAndAwait(id: String): Boolean {
        val db = dbProvider() ?: return false
        db.noteEntryDao().deleteById(id)
        return true
    }

    suspend fun getAllNotes(): List<NoteEntry> {
        val db = dbProvider() ?: return emptyList()
        return db.noteEntryDao().getAllOnce().map { it.toDomainModel() }
    }

    suspend fun getRandomNotes(limit: Int): List<NoteEntry> {
        val db = dbProvider() ?: return emptyList()
        return db.noteEntryDao().getRandomNotes(limit).map { it.toDomainModel() }
    }

    fun delete(id: String) {
        scope.launch {
            deleteAndAwait(id)
        }
    }
}

