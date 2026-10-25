package com.example.yanji.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
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
    /**
     * 当天「参与」过的专注次数：按时间区间相交统计，跨午夜的那条会在两天各计一次，
     * 但 [sessions] 里仍只在开始日保留一条完整记录、总时长也只归属开始日。
     */
    val focusParticipationCount: Int,
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
        noteEntities: List<NoteEntryEntity>,
        focusParticipationCount: Int
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
        // Participation is a separate indexed COUNT. The detail list now only
        // contains sessions starting on this day, so cross-day tails never need
        // to be loaded as full entities merely to count them.

        return DailyStudySnapshot(
            date = summary.date,
            formattedDate = summary.formattedDate,
            totalDurationSeconds = summary.totalDurationSeconds,
            focusCount = summary.focusCount,
            focusParticipationCount = focusParticipationCount,
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
        // One serialized read transaction on the writer connection. `withTransaction`
        // is Android-room-ktx only, so JVM tests would fail on it; this form keeps
        // production and the unit-test driver on the same code path.
        var snapshot: DailyStudySnapshot? = null
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val range = YanjiTime.parseIsoDate(date)
                    ?.let { YanjiTime.dayRange(it) }
                    ?: EpochRange(0L, 0L)
                val focus = db.focusSessionDao()
                    .getCompletedStartingBetween(range.startInclusive, range.endExclusive)
                val participation = db.focusSessionDao()
                    .countCompletedOverlapping(range.startInclusive, range.endExclusive)
                val exams = db.examSessionDao()
                    .getCompletedInRange(range.startInclusive, range.endExclusive)
                val tasks = db.studyTaskDao().getByDateWithActualSeconds(date)
                val notes = db.noteEntryDao().getAllByDate(date)

                snapshot = DailySnapshotAssembler.assemble(date, focus, exams, tasks, notes, participation)
            }
        }
        checkNotNull(snapshot) { "daily snapshot was not assembled inside the transaction" }
    }
}
