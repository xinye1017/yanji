package com.example.yanji.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.example.yanji.data.db.ScheduleBlockCompletionEntity
import com.example.yanji.data.db.ScheduleDao
import com.example.yanji.data.db.ScheduleDayAssignmentEntity
import com.example.yanji.data.db.ScheduleTemplateBlockEntity
import com.example.yanji.data.db.ScheduleTemplateEntity
import com.example.yanji.data.db.YanjiDatabase
import java.util.UUID

/**
 * 每日时间表的领域读写。
 *
 * 设计边界：
 *  - 模板与日期分离。模板只描述「理想的一天」，日期通过 `schedule_day_assignments`
 *    显式引用模板；没有指派时回落到排序第一的模板，不写任何隐式行。
 *  - 完成状态按 (日期, 时间段 id) 记录。切换某一天的模板只改当天指派，原模板的
 *    完成状态保留在库里，切回来仍然可见；专注记录永远不被时间表改写。
 *  - 所有跨表写操作在一个 Room 事务里完成，失败整体回滚。
 */
internal class ScheduleStore(
    private val dbProvider: () -> YanjiDatabase?
) {

    private fun db(): YanjiDatabase =
        checkNotNull(dbProvider()) { "YanjiRepository.init(context) must run before schedule queries" }

    suspend fun templates(): List<ScheduleTemplate> = read { dao -> readTemplates(dao) }

    suspend fun day(date: String): ScheduleDay = read { dao ->
        val templates = readTemplates(dao)
        val assignment = dao.getAssignment(date)
        val assignedTemplate = assignment?.let { row -> templates.firstOrNull { it.id == row.templateId } }
        val template = assignedTemplate ?: templates.firstOrNull()
        val completed = if (template == null) {
            emptyList()
        } else {
            val blockIds = template.blocks.mapTo(mutableSetOf()) { it.id }
            dao.getCompletions(date)
                .map { it.blockId }
                .filter { it in blockIds }
                .sorted()
        }
        ScheduleDay(
            date = date,
            template = template,
            assigned = assignedTemplate != null,
            completedBlockIds = completed
        )
    }

    suspend fun create(name: String, blocks: List<ScheduleBlockInput>): ScheduleTemplate {
        val cleanName = ScheduleValidation.cleanName(name)
        val cleanBlocks = ScheduleValidation.cleanBlocks(blocks)
        return write { dao ->
            val now = System.currentTimeMillis()
            val template = ScheduleTemplateEntity(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                sortOrder = (dao.maxSortOrder() ?: -1) + 1,
                createdAt = now,
                updatedAt = now
            )
            dao.upsertTemplate(template)
            val entities = cleanBlocks.map { input ->
                input.toEntity(template.id, UUID.randomUUID().toString())
            }
            if (entities.isNotEmpty()) dao.upsertBlocks(entities)
            template.toDomainModel(entities)
        }
    }

    /**
     * 原位更新模板：已有时间段保留 id（完成状态因此不丢），新段生成 id，
     * 被移除的段连同它的完成状态一起删除。
     */
    suspend fun update(
        templateId: String,
        name: String,
        blocks: List<ScheduleBlockInput>
    ): ScheduleTemplate {
        val cleanName = ScheduleValidation.cleanName(name)
        val cleanBlocks = ScheduleValidation.cleanBlocks(blocks)
        return write { dao ->
            val existing = dao.getTemplate(templateId)
                ?: throw ScheduleValidationException("E_SCHEDULE_TEMPLATE_NOT_FOUND", "模板不存在")
            val now = System.currentTimeMillis()
            val updatedTemplate = existing.copy(name = cleanName, updatedAt = now)
            dao.upsertTemplate(updatedTemplate)

            val existingBlocks = dao.getBlocksForTemplate(templateId)
            val existingById = existingBlocks.associateBy { it.id }
            val keepIds = cleanBlocks
                .mapNotNull { input -> input.id?.takeIf { it in existingById } }
                .toSet()
            val removed = existingBlocks.filter { it.id !in keepIds }
            removed.forEach { dao.deleteCompletionsForBlock(it.id) }
            removed.forEach { dao.deleteBlock(it.id) }

            val saved = cleanBlocks.map { input ->
                val id = input.id?.takeIf { it in existingById } ?: UUID.randomUUID().toString()
                input.toEntity(templateId, id)
            }
            if (saved.isNotEmpty()) dao.upsertBlocks(saved)
            updatedTemplate.toDomainModel(saved)
        }
    }

    suspend fun rename(templateId: String, name: String): Boolean {
        val cleanName = ScheduleValidation.cleanName(name)
        return write { dao ->
            val existing = dao.getTemplate(templateId) ?: return@write false
            dao.upsertTemplate(existing.copy(name = cleanName, updatedAt = System.currentTimeMillis()))
            true
        }
    }

    suspend fun delete(templateId: String): Boolean = write { dao ->
        dao.getTemplate(templateId) ?: return@write false
        // 先删完成状态（子查询依赖 blocks），再删时间段、指派与模板。
        dao.deleteCompletionsForTemplate(templateId)
        dao.deleteBlocksForTemplate(templateId)
        dao.deleteAssignmentsForTemplate(templateId)
        dao.deleteTemplate(templateId)
        true
    }

    suspend fun setDayTemplate(date: String, templateId: String): Boolean = write { dao ->
        if (date.isBlank()) {
            throw ScheduleValidationException("E_SCHEDULE_DATE", "日期不能为空")
        }
        if (dao.getTemplate(templateId) == null) {
            throw ScheduleValidationException("E_SCHEDULE_TEMPLATE_NOT_FOUND", "模板不存在")
        }
        dao.upsertAssignment(
            ScheduleDayAssignmentEntity(
                date = date,
                templateId = templateId,
                updatedAt = System.currentTimeMillis()
            )
        )
        true
    }

    /**
     * 标记某天某个时间段完成 / 未完成。
     *
     * 只接受「当天正在展示的模板」里的时间段：切换模板后旧 UI 的迟到点击会得到
     * `E_SCHEDULE_BLOCK_NOT_FOUND`，而不是往一个看不见的模板里写完成状态。
     */
    suspend fun setBlockCompleted(date: String, blockId: String, completed: Boolean): Boolean =
        write { dao ->
            if (date.isBlank()) {
                throw ScheduleValidationException("E_SCHEDULE_DATE", "日期不能为空")
            }
            val templates = readTemplates(dao)
            val assignment = dao.getAssignment(date)
            val assignedTemplate = assignment?.let { row -> templates.firstOrNull { it.id == row.templateId } }
            val template = assignedTemplate ?: templates.firstOrNull()
            template?.blocks?.firstOrNull { it.id == blockId }
                ?: throw ScheduleValidationException(
                    "E_SCHEDULE_BLOCK_NOT_FOUND",
                    "时间段不存在或不属于当天模板"
                )
            if (completed) {
                dao.insertCompletion(
                    ScheduleBlockCompletionEntity(
                        date = date,
                        blockId = blockId,
                        completedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.deleteCompletion(date, blockId)
            }
            true
        }

    // ------------------------------------------------------------ 内部

    private suspend fun <T> read(block: suspend (ScheduleDao) -> T): T =
        db().useWriterConnection { transactor ->
            transactor.immediateTransaction { block(db().scheduleDao()) }
        }

    private suspend fun <T> write(block: suspend (ScheduleDao) -> T): T =
        db().useWriterConnection { transactor ->
            transactor.immediateTransaction { block(db().scheduleDao()) }
        }

    private suspend fun readTemplates(dao: ScheduleDao): List<ScheduleTemplate> {
        val blocks = dao.getBlocks().groupBy { it.templateId }
        return dao.getTemplates().map { entity ->
            entity.toDomainModel(blocks[entity.id].orEmpty())
        }
    }

    private fun ScheduleBlockInput.toEntity(templateId: String, id: String): ScheduleTemplateBlockEntity =
        ScheduleTemplateBlockEntity(
            id = id,
            templateId = templateId,
            startMinute = startMinute,
            endMinute = endMinute,
            subjectIds = ScheduleTemplateBlockEntity.encodeSubjectIds(subjectIds),
            subjectLabel = subjectLabel,
            title = title
        )
}
