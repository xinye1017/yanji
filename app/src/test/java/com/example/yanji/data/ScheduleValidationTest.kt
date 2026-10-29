package com.example.yanji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleValidationTest {

    private fun block(
        start: Int,
        end: Int,
        title: String = "核心知识复习",
        label: String = "数学",
        ids: List<String> = listOf("math"),
        id: String? = null
    ) = ScheduleBlockInput(
        id = id,
        startMinute = start,
        endMinute = end,
        subjectIds = ids,
        subjectLabel = label,
        title = title
    )

    @Test
    fun cleanNameTrimsAndRejectsBlank() {
        assertEquals("复习冲刺", ScheduleValidation.cleanName("  复习冲刺  "))
        val error = runCatching { ScheduleValidation.cleanName("   ") }.exceptionOrNull()
        assertTrue(error is ScheduleValidationException)
        assertEquals("E_SCHEDULE_TEMPLATE_NAME", (error as ScheduleValidationException).code)
    }

    @Test
    fun cleanBlocksSortsByStartAndAllowsAdjacentBlocks() {
        val cleaned = ScheduleValidation.cleanBlocks(
            listOf(block(645, 675), block(510, 630))
        )
        assertEquals(listOf(510, 645), cleaned.map { it.startMinute })
    }

    @Test
    fun overlappingBlocksAreRejected() {
        val error = runCatching {
            ScheduleValidation.cleanBlocks(listOf(block(510, 630), block(600, 660)))
        }.exceptionOrNull()
        assertTrue(error is ScheduleValidationException)
        assertEquals("E_SCHEDULE_BLOCK_OVERLAP", (error as ScheduleValidationException).code)
    }

    @Test
    fun invalidBoundariesTitlesAndLabelsAreRejected() {
        assertEquals(
            "E_SCHEDULE_BLOCK_INVALID",
            (runCatching { ScheduleValidation.cleanBlocks(listOf(block(630, 510))) }
                .exceptionOrNull() as ScheduleValidationException).code
        )
        assertEquals(
            "E_SCHEDULE_BLOCK_INVALID",
            (runCatching { ScheduleValidation.cleanBlocks(listOf(block(510, 1441))) }
                .exceptionOrNull() as ScheduleValidationException).code
        )
        assertEquals(
            "E_SCHEDULE_BLOCK_INVALID",
            (runCatching { ScheduleValidation.cleanBlocks(listOf(block(510, 630, title = " "))) }
                .exceptionOrNull() as ScheduleValidationException).code
        )
        assertEquals(
            "E_SCHEDULE_BLOCK_INVALID",
            (runCatching { ScheduleValidation.cleanBlocks(listOf(block(510, 630, label = ""))) }
                .exceptionOrNull() as ScheduleValidationException).code
        )
    }

    @Test
    fun subjectIdsAreTrimmedDeduplicatedAndMayBeEmpty() {
        val cleaned = ScheduleValidation.cleanBlocks(
            listOf(block(1200, 1230, title = "错题整理", label = "复盘", ids = listOf(" math ", "math")))
        )
        assertEquals(listOf("math"), cleaned.single().subjectIds)

        val review = ScheduleValidation.cleanBlocks(
            listOf(block(1200, 1230, title = "错题整理", label = "复盘", ids = emptyList()))
        )
        assertTrue(review.single().subjectIds.isEmpty())
    }

    @Test
    fun duplicateProvidedIdsAreRejected() {
        val error = runCatching {
            ScheduleValidation.cleanBlocks(
                listOf(block(510, 630, id = "same"), block(645, 675, id = "same"))
            )
        }.exceptionOrNull()
        assertTrue(error is ScheduleValidationException)
        assertEquals("E_SCHEDULE_BLOCK_INVALID", (error as ScheduleValidationException).code)
    }

    @Test
    fun emptyTemplateIsValid() {
        assertTrue(ScheduleValidation.cleanBlocks(emptyList()).isEmpty())
    }
}
