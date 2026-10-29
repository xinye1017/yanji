package com.example.yanji.data

import kotlinx.serialization.Serializable

/**
 * 时间表模板里的一个时间段。
 *
 * [subjectIds] 是学科 id 列表（0..n 个），用来表达「数学 / 408」这类组合时段；
 * [subjectLabel] 是展示名快照，口径与 `StudyTask.subjectName` 一致——学科改名或删除后，
 * 模板仍能如实显示当时的计划，而不是变成空白。
 */
@Serializable
data class ScheduleBlock(
    val id: String,
    val templateId: String,
    val startMinute: Int,
    val endMinute: Int,
    val subjectIds: List<String> = emptyList(),
    val subjectLabel: String,
    val title: String
)

/**
 * 一套可复用的每日时间表。
 *
 * 模板只描述「理想的一天」，不持有任何日期或完成状态；日期通过
 * `schedule_day_assignments` 引用模板，完成状态通过 `schedule_block_completions`
 * 按 (日期, 时间段 id) 记录。切换某一天的模板不会改写其他日期，也不会触碰专注记录。
 */
@Serializable
data class ScheduleTemplate(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val blocks: List<ScheduleBlock> = emptyList()
)

/** 某一天显式选择的模板。没有行时由第一个模板兜底。 */
@Serializable
data class ScheduleDayAssignment(
    val date: String,
    val templateId: String,
    val updatedAt: Long = 0L
)

/** 某一天某个时间段的完成状态；未完成时不保留行。 */
@Serializable
data class ScheduleBlockCompletion(
    val date: String,
    val blockId: String,
    val completedAt: Long = 0L
)

/** 某一天的时间表：解析后的模板 + 当天完成状态。 */
data class ScheduleDay(
    val date: String,
    val template: ScheduleTemplate?,
    /** 当天是否有显式模板指派；false 表示当前展示的是默认模板。 */
    val assigned: Boolean,
    val completedBlockIds: List<String>
)

/** 桥接层接收的模板编辑载荷；id 为空表示新增时间段。 */
data class ScheduleBlockInput(
    val id: String?,
    val startMinute: Int,
    val endMinute: Int,
    val subjectIds: List<String>,
    val subjectLabel: String,
    val title: String
)

/**
 * 模板 / 时间段的校验失败。
 *
 * 带一个稳定的错误码，桥接层原样 reject 给 RN，UI 可以据此给出可操作的提示，
 * 而不是把所有失败都显示成「保存失败」。
 */
class ScheduleValidationException(
    val code: String,
    message: String
) : Exception(message)
