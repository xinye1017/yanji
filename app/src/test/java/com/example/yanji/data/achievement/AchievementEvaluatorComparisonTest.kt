package com.example.yanji.data.achievement

import com.example.yanji.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.random.Random

class AchievementEvaluatorComparisonTest {
    private val evaluator = AchievementEvaluator()

    @Test fun emptyDatabaseUnlocksNothing() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            assertTrue(evaluator.evaluateAndPersist(AchievementEvent.ReconcileAll, fixture.db).isEmpty())
        }
    }

    @Test fun focusHourBoundariesAndAlreadyUnlockedCandidates() = runBlocking {
        for (seconds in listOf(3599L, 3600L, 3601L)) AchievementTestDatabase().use { fixture ->
            val focus = FocusSession("focus", "math", "数学", 1, 10000, seconds)
            fixture.seed(focus = listOf(focus))
            val event = AchievementEvent.FocusCompleted(focus)
            val unlocked = evaluator.evaluateAndPersist(event, fixture.db)
            assertTrue("journey_focus_first" in unlocked)
            assertEquals(seconds >= 3600, "focus_1h" in unlocked)
            assertFalse("focus_5h" in unlocked)
            assertTrue(evaluator.evaluateAndPersist(event, fixture.db).isEmpty())
        }
    }

    @Test fun noteEventsOnlyEvaluateJournalCandidates() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val note = NoteEntry("note", "2026-09-18", content = "复盘", createdAt = 100)
            fixture.seed(notes = listOf(note), focus = listOf(FocusSession("focus", "math", "数学", 1, 2, 36000)))
            assertEquals(setOf("journey_journal_first", "review_1"), evaluator.evaluateAndPersist(AchievementEvent.NoteCreated(note), fixture.db).toSet())
        }
    }

    @Test fun everyProgressValueMatchesThePreOptimizationRules() = runBlocking {
        val originalZone = TimeZone.getDefault()
        val originalSubjects = SubjectCatalog.all
        try {
            SubjectCatalog.replaceAll(SubjectCatalog.defaults + Subject("custom-algebra", "代数", "#123456", 30, parentId = "math"))
            for (zone in listOf("Asia/Shanghai", "America/New_York", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val base = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val now = base + 10 * 86400000L
                for (seed in 0..5) AchievementTestDatabase().use { fixture ->
                    val random = Random(seed)
                    val statuses = SessionStatus.entries
                    val focus = (0 until 20).map { index ->
                        val start = base + index / 3 * 86400000L + listOf(0L, 4 * 3600000L, 8 * 3600000L, 8 * 3600000L + 1, 23 * 3600000L, 23 * 3600000L + 30 * 60000L)[index % 6]
                        FocusSession("f-$index", "other", "其他", start, start + random.nextLong(60, 20000) * 1000,
                            listOf(0L, 59, 60, 3599, 3600, 43199, 43200)[(index + seed) % 7],
                            note = "正文不参与成就", status = statuses[index % statuses.size])
                    }
                    val exam = (0 until 20).map { index ->
                        val start = base + random.nextLong(0, 8 * 86400000L)
                        val subjectId = listOf("math_advanced", "custom-algebra", "MATH-legacy", "old", "english")[index % 5]
                        ExamSession("e-$index", subjectId, if (subjectId == "old") "旧数学" else "科目", 7201,
                            listOf(6480L, 6481, 10799, 10800)[index % 4], start,
                            base + index / 3 * 86400000L + (index % 3) * 3600000L,
                            listOf(null, 89.9, 90.9, 100.1, 99.9, 110.0, 140.9, 150.0)[(index + seed) % 8],
                            note = "模考正文", status = statuses[index % statuses.size])
                    }
                    val notes = (0 until 14).map { index ->
                        NoteEntry("n-$index", "1999-01-01", content = "复盘", createdAt = base + index / 2 * 86400000L + 3600000L,
                            isDraft = index % 2 == 0)
                    }
                    val checkIns = (0 until 9).map { index ->
                        CheckIn(LocalDate.of(2026, 11, 1).plusDays(if (index < 6) index.toLong() else index + 4L).toString(),
                            streak = if (seed % 2 == 0) index + 1 else 1)
                    }
                    fixture.seed(focus, exam, notes, checkIns)
                    // Fetch the real persisted rows in exactly the original input order.
                    val persistedFocus = fixture.db.focusSessionDao().getAllOnce().map { it.toDomainModel() }
                    val persistedExams = fixture.db.examSessionDao().getAllOnce().map { it.toDomainModel() }
                    val persistedNotes = fixture.db.noteEntryDao().getAllOnce().map { it.toDomainModel() }
                    val persistedCheckIns = fixture.db.checkInDao().getAllOnce().map { it.toDomainModel() }
                    val facts = fixture.facts(nowMs = now)
                    for (def in AchievementCatalog.definitions) {
                        assertEquals("${def.id}, zone=$zone, seed=$seed",
                            legacyAchievementProgress(def, persistedFocus, persistedExams, persistedNotes, persistedCheckIns, now),
                            def.calculateProgress(facts))
                    }
                    val expected = AchievementCatalog.definitions.filter {
                        legacyAchievementProgress(it, persistedFocus, persistedExams, persistedNotes, persistedCheckIns, now) >= it.target
                    }.map { it.id }
                    assertEquals(expected, evaluator.evaluateAndPersist(AchievementEvent.ReconcileAll, fixture.db, now))
                }
            }
        } finally {
            TimeZone.setDefault(originalZone)
            SubjectCatalog.replaceAll(originalSubjects)
        }
    }

    @Test fun statusAndDraftRowsKeepTheirExistingAchievementMeaning() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            fixture.seed(
                focus = listOf(FocusSession("cancelled", "math", "数学", 1, 2, 3600, status = SessionStatus.CANCELLED)),
                exams = listOf(ExamSession("running", "math", "数学", score = 145.9, status = SessionStatus.RUNNING)),
                notes = listOf(NoteEntry("draft", "2026-01-01", isDraft = true))
            )
            val facts = fixture.facts()
            assertEquals(1L, AchievementCatalog.find("focus_1h")!!.calculateProgress(facts))
            assertEquals(145L, AchievementCatalog.find("math_score_150")!!.calculateProgress(facts))
            assertEquals(1L, AchievementCatalog.find("review_1")!!.calculateProgress(facts))
        }
    }

    /**
     * 可复现的读取量证据：事件级评估只碰被解锁的候选所需的最小投影。
     *
     * 这条用例同时钉住「不再读整表」这件事：旧路径每写一次就 `getAllOnce()` 拉
     * `SELECT *` 全量行（含 note 正文），新路径只允许窄列投影与聚合。断言的
     * 是**列集合**而不是行数，因为行数会随数据规模变化，列集合才是这次优化的契约。
     *
     * 用 [AchievementEvent.FocusCompleted] 是因为它的候选集合里没有任何随笔口径，
     * 因此这次评估**完全不该**碰 `journal_entries`；随笔口径自身的事件另有用例覆盖。
     */
    @Test fun focusEventEvaluationReadsOnlyNarrowProjectionsOfThePersistedTables() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val base = LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val focus = (0 until 40).map { index ->
                FocusSession("f-$index", "math", "数学", base + index * 60_000L,
                    base + index * 60_000L + 600_000L, 600L, note = "这条正文不该被成就读取")
            }
            val completed = focus.first()
            fixture.seed(
                focus = focus,
                exams = listOf(ExamSession("e", "math", "数学", 7200, 7200,
                    startTime = base, endTime = base + 7_200_000L, score = 120.0)),
                notes = listOf(NoteEntry("n", "2026-11-01", content = "复盘", createdAt = base + 1000))
            )
            fixture.reads.clear()
            evaluator.evaluateAndPersist(AchievementEvent.FocusCompleted(completed), fixture.db, base + 5_000)
            val businessReads = fixture.reads.filter {
                Regex("\\b(focus_sessions|exam_sessions|journal_entries)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.sql)
            }
            assertTrue("a focus event must not read the journal table at all: $businessReads",
                businessReads.none { it.sql.contains("journal_entries") })
            assertFalse("no achievement read may select the full row: $businessReads",
                businessReads.any { it.sql.contains("SELECT *") })
            assertTrue("counts must stay SQL aggregates: $businessReads",
                businessReads.any { it.sql.contains("COUNT(*)", ignoreCase = true) })
            assertTrue("temporal rules must read only the time columns: $businessReads",
                businessReads.any { it.sql.contains("startTime", ignoreCase = true) && it.sql.contains("durationSeconds") })
            // The old path transferred every note body through `SELECT *`. Narrow
            // projections must move strictly less text than one full copy of the
            // fixture's note column, which is the reproducible volume claim.
            val fullNoteBytes = focus.sumOf { it.note.toByteArray(Charsets.UTF_8).size.toLong() }
            assertTrue("fixture must carry real text to compare against", fullNoteBytes > 0L)
            val readTextBytes = businessReads.sumOf { it.textBytes }
            assertTrue(
                "achievement reads transferred $readTextBytes text bytes, one full note copy is $fullNoteBytes",
                readTextBytes < fullNoteBytes
            )
        }
    }
}
