package com.example.yanji.data

/**
 * 时间表模板的纯校验逻辑。
 *
 * 放在领域层而不是桥接层：同一套规则要同时约束 RN 编辑器、备份导入和原生写入，
 * 任何一处绕过都会让库里出现重叠时段或空标题。校验失败抛
 * [ScheduleValidationException]，错误码就是桥接契约的一部分。
 */
internal object ScheduleValidation {

    const val MINUTES_PER_DAY = 24 * 60
    const val MAX_TEMPLATE_NAME = 30
    const val MAX_BLOCK_TITLE = 80
    const val MAX_SUBJECT_LABEL = 40
    const val MAX_BLOCKS_PER_TEMPLATE = 32
    const val MAX_SUBJECTS_PER_BLOCK = 8

    /** 模板名：去首尾空白后必须非空且不超长。 */
    fun cleanName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) {
            throw ScheduleValidationException("E_SCHEDULE_TEMPLATE_NAME", "模板名称不能为空")
        }
        if (name.length > MAX_TEMPLATE_NAME) {
            throw ScheduleValidationException(
                "E_SCHEDULE_TEMPLATE_NAME",
                "模板名称不能超过 $MAX_TEMPLATE_NAME 个字"
            )
        }
        return name
    }

    /**
     * 时间段：校验边界、标题、科目标签与重叠，并按开始时间排序返回。
     *
     * 允许模板暂时没有时间段（空模板是合法状态），但一旦有，就必须是一份
     * 自洽的计划：结束晚于开始、标题非空、任意两段不重叠。相邻（上一段结束
     * 等于下一段开始）是允许的，那是紧凑但不重叠的排法。
     */
    fun cleanBlocks(inputs: List<ScheduleBlockInput>): List<ScheduleBlockInput> {
        if (inputs.size > MAX_BLOCKS_PER_TEMPLATE) {
            throw ScheduleValidationException(
                "E_SCHEDULE_BLOCK_INVALID",
                "一个模板最多 $MAX_BLOCKS_PER_TEMPLATE 个时间段"
            )
        }
        val providedIds = inputs.mapNotNull { it.id?.takeIf(String::isNotBlank) }
        if (providedIds.size != providedIds.toSet().size) {
            throw ScheduleValidationException("E_SCHEDULE_BLOCK_INVALID", "时间段 id 不能重复")
        }
        val cleaned = inputs.map { input ->
            if (input.startMinute < 0 || input.startMinute >= MINUTES_PER_DAY) {
                throw ScheduleValidationException("E_SCHEDULE_BLOCK_INVALID", "开始时间必须在 00:00 到 23:59 之间")
            }
            if (input.endMinute <= input.startMinute || input.endMinute > MINUTES_PER_DAY) {
                throw ScheduleValidationException("E_SCHEDULE_BLOCK_INVALID", "结束时间必须晚于开始时间，且不晚于 24:00")
            }
            val title = input.title.trim()
            if (title.isEmpty()) {
                throw ScheduleValidationException("E_SCHEDULE_BLOCK_INVALID", "时间段的任务内容不能为空")
            }
            if (title.length > MAX_BLOCK_TITLE) {
                throw ScheduleValidationException(
                    "E_SCHEDULE_BLOCK_INVALID",
                    "任务内容不能超过 $MAX_BLOCK_TITLE 个字"
                )
            }
            val label = input.subjectLabel.trim()
            if (label.length > MAX_SUBJECT_LABEL) {
                throw ScheduleValidationException(
                    "E_SCHEDULE_BLOCK_INVALID",
                    "科目名称不能超过 $MAX_SUBJECT_LABEL 个字"
                )
            }
            val subjectIds = input.subjectIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (subjectIds.size > MAX_SUBJECTS_PER_BLOCK) {
                throw ScheduleValidationException(
                    "E_SCHEDULE_BLOCK_INVALID",
                    "一个时间段最多关联 $MAX_SUBJECTS_PER_BLOCK 个科目"
                )
            }
            input.copy(
                startMinute = input.startMinute,
                endMinute = input.endMinute,
                subjectIds = subjectIds,
                subjectLabel = label,
                title = title
            )
        }.sortedWith(compareBy({ it.startMinute }, { it.endMinute }))

        for (index in 1 until cleaned.size) {
            if (cleaned[index].startMinute < cleaned[index - 1].endMinute) {
                throw ScheduleValidationException(
                    "E_SCHEDULE_BLOCK_OVERLAP",
                    "时间段不能重叠：${clock(cleaned[index - 1].startMinute)}–${clock(cleaned[index - 1].endMinute)} 与 ${clock(cleaned[index].startMinute)}–${clock(cleaned[index].endMinute)}"
                )
            }
        }
        return cleaned
    }

    private fun clock(minute: Int): String =
        "%02d:%02d".format(minute / 60, minute % 60)
}
