package com.example.yanji.data.note

import com.example.yanji.data.NoteEntry
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.YanjiDatabase
import com.example.yanji.data.achievement.AchievementEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 日记的状态与动作归属者。
 *
 * 写入路径刻意只走 Room：内存列表由 DAO Flow 回灌，**不做双写**。
 * 这样「同一天只允许一条日记」与 `updatedAt` 的一致性都由
 * [addOrUpdate] 里的日期归一化保证，不会出现内存与库各说各话。
 */
internal class NoteStore(
    private val scope: CoroutineScope,
    private val dbProvider: () -> YanjiDatabase?
) {

    var onAchievementEvent: (suspend (AchievementEvent) -> Unit)? = null

    private val _noteEntries = MutableStateFlow<List<NoteEntry>>(emptyList())
    val noteEntries: StateFlow<List<NoteEntry>> = _noteEntries.asStateFlow()

    fun bind(db: YanjiDatabase) {
        scope.launch {
            db.noteEntryDao().getAll().collect { entities ->
                _noteEntries.value = entities.map { it.toDomainModel() }
            }
        }
    }

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
            val dao = dbProvider()?.noteEntryDao() ?: return@launch
            val existing = dao.getById(entry.id)
            val normalized = entry.copy(
                createdAt = existing?.createdAt ?: entry.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            dao.insert(NoteEntryEntity.fromDomainModel(normalized))
            onAchievementEvent?.invoke(AchievementEvent.NoteCreated(normalized))
        }
    }

    /**
     * [addOrUpdate] 的**可等待**版本：返回时 Room 写入已经落库。
     *
     * 存在的理由：`addOrUpdate` 自己 `launch`，桥接层 `saveQuickNote` 会在行还不存在时就
     * resolve，JS 侧紧接着的读取会漏掉这条记录。这里把同一条归一化规则原样搬过来
     * （按 id 保留首次 `createdAt`、刷新 `updatedAt`），只是把执行权交还调用方。
     *
     * 仍然**只写 Room**：内存列表由 `bind()` 的 DAO Flow 回灌，不会出现双写。
     * [onAchievementEvent] 是 NoteStore 的私有回调，因此可等待写路径也必须放在这里。
     */
    suspend fun addOrUpdateAndAwait(entry: NoteEntry) {
        val dao = dbProvider()?.noteEntryDao() ?: return
        val existing = dao.getById(entry.id)
        val normalized = entry.copy(
            createdAt = existing?.createdAt ?: entry.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        dao.insert(NoteEntryEntity.fromDomainModel(normalized))
        onAchievementEvent?.invoke(AchievementEvent.NoteCreated(normalized))
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
     * 只写 Room，内存列表由 `bind()` 的 DAO Flow 回灌。**不触发**
     * [AchievementEvent.NoteCreated]：这是编辑既有随笔，不是新建。
     */
    suspend fun updateContentAndAwait(id: String, content: String): Boolean {
        val dao = dbProvider()?.noteEntryDao() ?: return false
        return dao.updateContent(id, content, System.currentTimeMillis()) > 0
    }

    /**
     * 更新疑点解决状态的**可等待版本**：返回 false 表示 id 不存在（或数据库未就绪）。
     */
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

    fun delete(id: String) {
        scope.launch {
            deleteAndAwait(id)
        }
    }
}

