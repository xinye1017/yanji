package com.example.yanji.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTableEventMappingTest {

    @Test
    fun roomTablesMapToTheirDataChangedTypes() {
        assertEquals(
            setOf("tasks", "notes", "sessions", "settings", "subjects"),
            dataEventTypesForTables(
                setOf("study_tasks", "journal_entries", "focus_sessions", "exam_sessions", "user_settings", "subjects")
            )
        )
    }

    @Test
    fun eitherSessionTableMapsToOneSessionsEvent() {
        assertEquals(setOf("sessions"), dataEventTypesForTables(setOf("focus_sessions", "exam_sessions")))
        assertEquals(setOf("sessions"), dataEventTypesForTables(setOf("exam_sessions")))
    }

    @Test
    fun unknownTablesProduceNoDataChangedEvents() {
        assertTrue(dataEventTypesForTables(setOf("unknown_table", "chat_messages")).isEmpty())
        assertTrue(dataEventTypesForTables(emptySet()).isEmpty())
    }
}
