package com.example.yanji.data.db

/**
 * 每日时间表的建表与默认模板种子。
 *
 * 两处使用同一份 SQL：
 *  - `MIGRATION_29_30`：老用户升级时建表并写入两套默认模板；
 *  - `RoomDatabase.Callback.onCreate`：全新安装时表已由 Room 建好，只执行种子。
 *
 * 默认模板严格对应用户需求里的两张表（复习冲刺 / 真题冲刺），每天净学习 8 小时。
 * 时间段 id 固定，便于迁移测试与备份恢复断言；`INSERT OR IGNORE` 让重复执行成为 no-op。
 */
internal object ScheduleSeed {

    const val REVIEW_TEMPLATE_ID = "schedule-template-review"
    const val EXAM_TEMPLATE_ID = "schedule-template-exam"

    val createStatements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `schedule_templates` (
            `id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `sortOrder` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_schedule_templates_sortOrder` " +
            "ON `schedule_templates` (`sortOrder`)",
        """
        CREATE TABLE IF NOT EXISTS `schedule_template_blocks` (
            `id` TEXT NOT NULL,
            `templateId` TEXT NOT NULL,
            `startMinute` INTEGER NOT NULL,
            `endMinute` INTEGER NOT NULL,
            `subjectIds` TEXT NOT NULL,
            `subjectLabel` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_schedule_template_blocks_templateId` " +
            "ON `schedule_template_blocks` (`templateId`)",
        """
        CREATE TABLE IF NOT EXISTS `schedule_day_assignments` (
            `date` TEXT NOT NULL,
            `templateId` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`date`)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_schedule_day_assignments_templateId` " +
            "ON `schedule_day_assignments` (`templateId`)",
        """
        CREATE TABLE IF NOT EXISTS `schedule_block_completions` (
            `date` TEXT NOT NULL,
            `blockId` TEXT NOT NULL,
            `completedAt` INTEGER NOT NULL,
            PRIMARY KEY(`date`, `blockId`)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_schedule_block_completions_blockId` " +
            "ON `schedule_block_completions` (`blockId`)"
    )

    val seedStatements: List<String> = listOf(
        insertTemplate(REVIEW_TEMPLATE_ID, "复习冲刺", 0),
        insertTemplate(EXAM_TEMPLATE_ID, "真题冲刺", 1),

        // 复习冲刺：2h + 30m + 2.5h + 1.5h + 1h + 30m = 8h
        insertBlock("schedule-block-review-1", REVIEW_TEMPLATE_ID, 510, 630, "[\"math\"]", "数学", "核心知识复习"),
        insertBlock("schedule-block-review-2", REVIEW_TEMPLATE_ID, 645, 675, "[\"math\"]", "数学", "习题巩固"),
        insertBlock("schedule-block-review-3", REVIEW_TEMPLATE_ID, 780, 930, "[\"major\"]", "408", "知识点学习与练习"),
        insertBlock("schedule-block-review-4", REVIEW_TEMPLATE_ID, 945, 1035, "[\"english\"]", "英语", "单词、阅读"),
        insertBlock("schedule-block-review-5", REVIEW_TEMPLATE_ID, 1140, 1200, "[\"politics\"]", "政治", "知识点、选择题"),
        insertBlock("schedule-block-review-6", REVIEW_TEMPLATE_ID, 1200, 1230, "[]", "复盘", "错题整理"),

        // 真题冲刺：3h + 2h + 1.5h + 1h + 30m = 8h
        insertBlock("schedule-block-exam-1", EXAM_TEMPLATE_ID, 510, 690, "[\"math\",\"major\"]", "数学 / 408", "完整真题限时训练"),
        insertBlock("schedule-block-exam-2", EXAM_TEMPLATE_ID, 780, 900, "[\"math\",\"major\"]", "数学 / 408", "真题复盘与专题强化"),
        insertBlock("schedule-block-exam-3", EXAM_TEMPLATE_ID, 915, 1005, "[\"english\"]", "英语", "真题、作文"),
        insertBlock("schedule-block-exam-4", EXAM_TEMPLATE_ID, 1140, 1200, "[\"politics\"]", "政治", "选择题、背诵"),
        insertBlock("schedule-block-exam-5", EXAM_TEMPLATE_ID, 1200, 1230, "[]", "复盘", "错题与明日规划")
    )

    private fun insertTemplate(id: String, name: String, sortOrder: Int): String =
        "INSERT OR IGNORE INTO `schedule_templates` " +
            "(`id`, `name`, `sortOrder`, `createdAt`, `updatedAt`) VALUES " +
            "('$id', '$name', $sortOrder, 0, 0)"

    private fun insertBlock(
        id: String,
        templateId: String,
        startMinute: Int,
        endMinute: Int,
        subjectIds: String,
        subjectLabel: String,
        title: String
    ): String =
        "INSERT OR IGNORE INTO `schedule_template_blocks` " +
            "(`id`, `templateId`, `startMinute`, `endMinute`, `subjectIds`, `subjectLabel`, `title`) VALUES " +
            "('$id', '$templateId', $startMinute, $endMinute, '$subjectIds', '$subjectLabel', '$title')"
}
