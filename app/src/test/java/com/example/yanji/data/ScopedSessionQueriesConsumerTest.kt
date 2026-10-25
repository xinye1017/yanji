package com.example.yanji.data

import com.example.yanji.data.achievement.AchievementTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScopedSessionQueriesConsumerTest {
    private fun repository(fixture: AchievementTestDatabase): YanjiRepository {
        val repo = YanjiRepository::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        YanjiRepository::class.java.getDeclaredField("database").apply { isAccessible = true }.set(repo, fixture.db)
        return repo
    }

    @Test fun focusByIdReadsOnlyTheMatchingPersistedRow() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val target = FocusSession("target", "math", "数学", 1, 3600001, 3600)
            fixture.seed(focus = listOf(target, target.copy(id = "other")))
            val repo = repository(fixture)
            assertEquals(target, repo.getFocusSessionByIdFlow("target").first())
            assertNull(repo.getFocusSessionByIdFlow("missing").first())
        }
    }

    @Test fun examByIdReadsOnlyTheMatchingPersistedRow() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val target = ExamSession("target", "math", "数学", startTime = 1, endTime = 2, createdAt = 3)
            fixture.seed(exams = listOf(target, target.copy(id = "other")))
            val repo = repository(fixture)
            assertEquals(target, repo.getExamSessionByIdFlow("target").first())
            assertNull(repo.getExamSessionByIdFlow("missing").first())
        }
    }

    @Test fun todayDurationComesFromRoomAggregates() = runBlocking {
        AchievementTestDatabase().use { fixture ->
            val range = YanjiTime.dayRange(YanjiTime.today())
            val start = range.startInclusive + 1000
            val focus = FocusSession("focus", "math", "数学", start, start + 3600000, 3600)
            fixture.seed(
                focus = listOf(focus, focus.copy(id = "old", startTime = range.startInclusive - 1),
                    focus.copy(id = "cancelled", status = SessionStatus.CANCELLED)),
                exams = listOf(ExamSession("exam", "math", "数学", actualDurationSeconds = 1800, startTime = start))
            )
            fixture.reads.clear()
            assertEquals(5400L, repository(fixture).observeTodayStudyDurationSeconds().first())
            assertFalse(fixture.reads.any { "SELECT *" in it.sql.uppercase() })
        }
    }

    @Test fun aiDiagnosticSnapshotBuildsWithScopedSessions() {
        val now = System.currentTimeMillis()
        val snapshot = StudyDiagnosticSnapshot.from(
            periodDays = 7, settings = UserSettings(),
            focusSessions = listOf(FocusSession("f", "sub_math", "数学一", now - 3600000, now, 3600)),
            examSessions = emptyList(), noteEntries = emptyList(), now = now
        )
        assertTrue(snapshot.toPromptData().contains("数学一"))
    }
}
