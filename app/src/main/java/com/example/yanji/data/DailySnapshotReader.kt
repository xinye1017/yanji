package com.example.yanji.data

import androidx.room.withTransaction
import com.example.yanji.data.db.ExamSessionEntity
import com.example.yanji.data.db.FocusSessionEntity
import com.example.yanji.data.db.NoteEntryEntity
import com.example.yanji.data.db.StudyTaskActualSecondsRow
import com.example.yanji.data.db.YanjiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DailyStudySnapshot(
    val date: String,
    val formattedDate: String,
    val totalDurationSeconds: Long,
    val focusCount: Int,
    val examCount: Int,
    val sessions: List<DailySessionItem>,
    val tasks: List<StudyTask>,
    val taskActualSeconds: Map<String, Long>,
    val notes: List<NoteEntry>
)

/** Assembles the bridge snapshot from the same entity projections read by Room. */
internal object DailySnapshotAssembler {
    fun assemble(
        date: String,
        focusEntities: List<FocusSessionEntity>,
        examEntities: List<ExamSessionEntity>,
        taskRows: List<StudyTaskActualSecondsRow>,
        noteEntities: List<NoteEntryEntity>
    ): DailyStudySnapshot {
        val focusSessions = focusEntities.map(FocusSessionEntity::toDomainModel)
        val examSessions = examEntities.map(ExamSessionEntity::toDomainModel)
        val tasks = taskRows.map { it.task.toDomainModel() }.filter { it.date == date }
        val taskIds = tasks.map { it.id }.toSet()
        val notes = noteEntities.asSequence()
            .map(NoteEntryEntity::toDomainModel)
            .filter { it.date == date }
            .sortedBy { it.createdAt }
            .toList()
        val summary = buildDailyStudySummary(
            dateStr = date,
            focusList = focusSessions,
            examList = examSessions,
            subjects = SubjectCatalog.all
        )

        return DailyStudySnapshot(
            date = summary.date,
            formattedDate = summary.formattedDate,
            totalDurationSeconds = summary.totalDurationSeconds,
            focusCount = summary.focusCount,
            examCount = summary.examCount,
            sessions = summary.sessions,
            tasks = tasks,
            taskActualSeconds = taskRows.asSequence()
                .filter { it.task.id in taskIds }
                .associate { it.task.id to it.actualSeconds },
            notes = notes
        )
    }
}

/** Reads every daily projection under one Room read transaction on the IO dispatcher. */
object DailySnapshotReader {
    suspend fun read(db: YanjiDatabase, date: String): DailyStudySnapshot = withContext(Dispatchers.IO) {
        db.withTransaction {
            val range = YanjiTime.parseIsoDate(date)
                ?.let { YanjiTime.dayRange(it) }
                ?: EpochRange(0L, 0L)
            val focus = db.focusSessionDao()
                .getCompletedInRange(range.startInclusive, range.endExclusive)
            val exams = db.examSessionDao()
                .getCompletedInRange(range.startInclusive, range.endExclusive)
            val tasks = db.studyTaskDao().getByDateWithActualSeconds(date)
            val notes = db.noteEntryDao().getAllByDate(date)

            DailySnapshotAssembler.assemble(date, focus, exams, tasks, notes)
        }
    }
}
