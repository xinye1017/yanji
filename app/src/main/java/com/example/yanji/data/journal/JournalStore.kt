package com.example.yanji.data.journal

import com.example.yanji.data.JournalEntry
import com.example.yanji.data.db.JournalEntryEntity
import com.example.yanji.data.db.YanjiDatabase
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
internal class JournalStore(
    private val scope: CoroutineScope,
    private val dbProvider: () -> YanjiDatabase?
) {

    private val _journalEntries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journalEntries: StateFlow<List<JournalEntry>> = _journalEntries.asStateFlow()

    fun bind(db: YanjiDatabase) {
        scope.launch {
            db.journalEntryDao().getAll().collect { entities ->
                _journalEntries.value = entities.map { it.toDomainModel() }
            }
        }
    }

    /**
     * 保存日记。**以 Room 为唯一事实来源**。
     *
     * 修复过的问题：
     * 1. 旧代码把 `updatedAt` 只写进内存副本，却把原始 entry 写库，两边不一致；
     * 2. 旧代码按 `date || id` 匹配内存行、却按 `id` 覆盖写库，会产生两条同日期日记。
     *    现在统一按日期归一化 id / createdAt。
     */
    fun addOrUpdate(entry: JournalEntry) {
        scope.launch {
            val dao = dbProvider()?.journalEntryDao() ?: return@launch
            val existing = dao.getByDate(entry.date)
            val normalized = entry.copy(
                id = existing?.id ?: entry.id,
                createdAt = existing?.createdAt ?: entry.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            dao.insert(JournalEntryEntity.fromDomainModel(normalized))
        }
    }

    fun delete(id: String) {
        scope.launch {
            val db = dbProvider() ?: return@launch
            db.journalEntryDao().deleteById(id)
        }
    }
}
