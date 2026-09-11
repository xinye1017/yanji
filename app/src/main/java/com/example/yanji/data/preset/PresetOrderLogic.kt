package com.example.yanji.data.preset

import com.example.yanji.data.QuickStartPreset

/**
 * 快捷操作的纯逻辑：新排序值的分配与拖动排序。
 *
 * 拖动排序是自研实现（无第三方库），"把 id 移到 toIndex、其余项顺移、重新编号"
 * 这段逻辑值得用单测钉住——首页的排序一旦错乱，用户重排就会"跳位"。
 */
internal object PresetOrderLogic {

    /** 追加一项：sortOrder 取当前最大值 + 1。 */
    fun withNextOrder(existing: List<QuickStartPreset>, preset: QuickStartPreset): QuickStartPreset =
        preset.copy(sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1)

    /** 把 id 项移动到 toIndex（0 基），其余项顺移，并按新顺序重新编号。越界/找不到时原样返回。 */
    fun moved(existing: List<QuickStartPreset>, id: String, toIndex: Int): List<QuickStartPreset> {
        val list = existing.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0 || toIndex !in list.indices || from == toIndex) return existing
        val item = list.removeAt(from)
        list.add(toIndex, item)
        return list.mapIndexed { i, p -> p.copy(sortOrder = i) }
    }

    /** 按 sortOrder 排序并重新编号（从 DB 读回后保证顺序稳定）。 */
    fun normalized(existing: List<QuickStartPreset>): List<QuickStartPreset> =
        existing.sortedBy { it.sortOrder }.mapIndexed { i, p -> p.copy(sortOrder = i) }
}
