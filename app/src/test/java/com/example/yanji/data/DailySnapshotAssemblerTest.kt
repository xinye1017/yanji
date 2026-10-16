package com.example.yanji.data

import com.example.yanji.bridge.BridgeMappers
import com.example.yanji.data.db.ExamSessionEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.StudyTaskActualSecondsRow
import com.example.yanji.data.db.StudyTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailySnapshotAssemblerTest {

    private val date = "2026-10-10"
    private val dayStart = YanjiTime.dayRange(LocalDate.parse(date)).startInclusive

    @Test
    fun emptyRoomRowsProduceAnEmptySnapshot() {
        val snapshot = DailySnapshotAssembler.assemble(
            date = date,
            focusEntities = emptyList(),
            examEntities = emptyList(),
            taskRows = emptyList(),
            noteEntities = emptyList()
        )

        assertEquals(date, snapshot.date)
        assertEquals(YanjiTime.formatFullDateWithWeekday(LocalDate.parse(date)), snapshot.formattedDate)
        assertEquals(0L, snapshot.totalDurationSeconds)
        assertEquals(0, snapshot.focusCount)
        assertEquals(0, snapshot.examCount)
        assertTrue(snapshot.sessions.isEmpty())
        assertTrue(snapshot.tasks.isEmpty())
        assertTrue(snapshot.taskActualSeconds.isEmpty())
        assertTrue(snapshot.notes.isEmpty())
    }

    @Test
    fun sessionsAreSortedNewestFirstAndNotesOldestFirst() {
        val snapshot = DailySnapshotAssembler.assemble(
            date = date,
            focusEntities = listOf(
                focus("focus-early", dayStart + 100_000, 10),
                focus("focus-late", dayStart + 200_000, 30)
            ),
            examEntities = listOf(exam("exam", dayStart + 300_000, 50)),
            taskRows = listOf(
                taskRow(task("incomplete", date, completed = false, createdAt = 1_000), 0L),
                taskRow(task("complete", date, completed = true, createdAt = 2_000), 60L)
            ),
            noteEntities = listOf(
                note("note-late", date, createdAt = 2_000),
                note("note-early", date, createdAt = 1_000),
                note("note-outside", "2026-10-09", createdAt = 500)
            )
        )

        assertEquals(listOf("exam", "focus-late", "focus-early"), snapshot.sessions.map { it.id })
        assertEquals(listOf(true, false, false), snapshot.sessions.map { it.isExam })
        assertEquals(90L, snapshot.totalDurationSeconds)
        assertEquals(2, snapshot.focusCount)
        assertEquals(1, snapshot.examCount)
        assertEquals(listOf("incomplete", "complete"), snapshot.tasks.map { it.id })
        assertEquals(listOf(false, true), snapshot.tasks.map { it.isCompleted })
        assertEquals(listOf("note-early", "note-late"), snapshot.notes.map { it.id })
    }

    @Test
    fun taskAggregateIncludesHistoricalFocusAndBridgeTruncatesRemainderSeconds() {
        val selectedTask = task("selected", date, completed = false)
        val snapshot = DailySnapshotAssembler.assemble(
            date = date,
            // This is the selected-day session query. Historical sessions contribute
            // through the pre-aggregated task row, not through this timeline list.
            focusEntities = listOf(focus("today-focus", dayStart + 10_000, 120, "selected")),
            examEntities = emptyList(),
            taskRows = listOf(
                taskRow(selectedTask, 3_599L),
                taskRow(task("other-date", "2026-10-09"), 600L)
            ),
            noteEntities = emptyList()
        )

        assertEquals(listOf("selected"), snapshot.tasks.map { it.id })
        assertEquals(3_599L, snapshot.taskActualSeconds["selected"])
        assertEquals(
            59,
            BridgeMappers.taskFields(
                snapshot.tasks.single(),
                snapshot.taskActualSeconds["selected"]
            ).actualMinutes
        )
        assertEquals(listOf("today-focus"), snapshot.sessions.map { it.id })
    }

    /**
     * 时间轴上的「达成关联任务」胶囊只认 `taskId`。
     *
     * 不靠标题或学科反推匹配 —— 两条同名计划、同一门课的两段专注都会让反推张冠李戴。
     * 模考不挂计划，因此恒为 null。
     */
    @Test
    fun sessionsCarryTheirBoundPlanIdAndLeaveUnboundOnesNull() {
        val snapshot = DailySnapshotAssembler.assemble(
            date = date,
            focusEntities = listOf(
                focus("bound", dayStart + 100_000, 10, taskId = "task-os-ch4"),
                focus("unbound", dayStart + 200_000, 20)
            ),
            examEntities = listOf(exam("exam", dayStart + 300_000, 30)),
            taskRows = emptyList(),
            noteEntities = emptyList()
        )

        assertEquals(
            mapOf("bound" to "task-os-ch4", "unbound" to null, "exam" to null),
            snapshot.sessions.associate { it.id to it.taskId }
        )
    }

    private fun focus(
        id: String,
        startTime: Long,
        seconds: Long,
        taskId: String? = null
    ): FocusSessionEntity = FocusSessionEntity(
        id = id,
        subjectId = "math",
        subjectName = "数学",
        startTime = startTime,
        endTime = startTime + seconds * 1_000,
        durationSeconds = seconds,
        taskId = taskId,
        status = SessionStatus.COMPLETED.name
    )

    private fun exam(id: String, startTime: Long, seconds: Long): ExamSessionEntity =
        ExamSessionEntity(
            id = id,
            subjectId = "english",
            subjectName = "英语一",
            plannedDurationSeconds = seconds,
            actualDurationSeconds = seconds,
            startTime = startTime,
            endTime = startTime + seconds * 1_000,
            score = null,
            maxScore = 150.0,
            note = "",
            status = SessionStatus.COMPLETED.name,
            createdAt = startTime
        )

    private fun task(
        id: String,
        taskDate: String,
        completed: Boolean = false,
        createdAt: Long = 1_000L
    ): StudyTaskEntity = StudyTaskEntity(
        id = id,
        date = taskDate,
        subjectId = "math",
        subjectName = "数学",
        title = id,
        plannedMinutes = 30,
        isCompleted = completed,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun taskRow(task: StudyTaskEntity, actualSeconds: Long) =
        StudyTaskActualSecondsRow(task = task, actualSeconds = actualSeconds)

    private fun note(id: String, noteDate: String, createdAt: Long): NoteEntryEntity =
        NoteEntryEntity(
            id = id,
            date = noteDate,
            title = "",
            content = id,
            moodScore = 5,
            energyScore = 4,
            studySatisfaction = 5,
            tomorrowPlan = "",
            blockers = "",
            tags = "",
            createdAt = createdAt,
            updatedAt = createdAt
        )
}
