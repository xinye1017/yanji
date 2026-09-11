package com.example.yanji.data.preset

import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.db.QuickStartPresetEntity
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页快捷操作的状态与动作。
 *
 * 写入顺序刻意保持"先内存、后落库"：拖动排序需要每一帧都拿到新顺序，
 * 等 DB Flow 回灌会有可感知的延迟；DB 仍是事实来源，Flow 回灌后两者一致。
 */
internal class QuickStartPresetStore(
    private val scope: CoroutineScope,
    private val dbProvider: () -> YanjiDatabase?
) {

    private val _quickStartPresets = MutableStateFlow<List<QuickStartPreset>>(emptyList())
    val quickStartPresets: StateFlow<List<QuickStartPreset>> = _quickStartPresets.asStateFlow()

    fun bind(db: YanjiDatabase) {
        scope.launch {
            db.quickStartPresetDao().getAllFlow().collect { entities ->
                _quickStartPresets.value = PresetOrderLogic.normalized(entities.map { it.toDomainModel() })
            }
        }
    }

    /** 追加多项（内置模板播种用）。 */
    fun insertAll(presets: List<QuickStartPreset>) {
        _quickStartPresets.value = (_quickStartPresets.value + presets)
            .sortedBy { it.sortOrder }
            .mapIndexed { i, p -> p.copy(sortOrder = i) }
        scope.launch {
            val db = dbProvider() ?: return@launch
            presets.forEach { db.quickStartPresetDao().insert(QuickStartPresetEntity.fromDomainModel(it)) }
        }
    }

    fun add(preset: QuickStartPreset): QuickStartPreset {
        val stored = PresetOrderLogic.withNextOrder(_quickStartPresets.value, preset)
        _quickStartPresets.value = _quickStartPresets.value + stored
        scope.launch {
            dbProvider()?.quickStartPresetDao()?.insert(QuickStartPresetEntity.fromDomainModel(stored))
        }
        return stored
    }

    fun update(preset: QuickStartPreset) {
        _quickStartPresets.value = _quickStartPresets.value.map { if (it.id == preset.id) preset else it }
        scope.launch {
            dbProvider()?.quickStartPresetDao()?.insert(QuickStartPresetEntity.fromDomainModel(preset))
        }
    }

    fun delete(id: String) {
        _quickStartPresets.value = _quickStartPresets.value.filter { it.id != id }
        scope.launch {
            dbProvider()?.quickStartPresetDao()?.deleteById(id)
        }
    }

    /** 拖动中：内存即时生效，落库延迟到 [commitOrder]。 */
    fun move(id: String, toIndex: Int) {
        _quickStartPresets.value = PresetOrderLogic.moved(_quickStartPresets.value, id, toIndex)
    }

    /** 拖动结束：把当前内存顺序持久化。 */
    fun commitOrder() {
        val ordered = _quickStartPresets.value
        scope.launch {
            val db = dbProvider() ?: return@launch
            ordered.forEachIndexed { i, p ->
                db.quickStartPresetDao().insert(QuickStartPresetEntity.fromDomainModel(p.copy(sortOrder = i)))
            }
        }
    }

    fun count(): Int = _quickStartPresets.value.size
}
