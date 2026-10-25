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
}
