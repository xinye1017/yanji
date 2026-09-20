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
     * 切换收藏。只更新 `isFavorite` 与 `updatedAt`，不改正文与 `createdAt`。
     */
    fun setFavorite(id: String, favorite: Boolean) {
        scope.launch {
            val dao = dbProvider()?.noteEntryDao() ?: return@launch
            dao.setFavorite(id, if (favorite) 1 else 0, System.currentTimeMillis())
        }
    }

    fun delete(id: String) {
        scope.launch {
            val db = dbProvider() ?: return@launch
            db.noteEntryDao().deleteById(id)
        }
    }
}
