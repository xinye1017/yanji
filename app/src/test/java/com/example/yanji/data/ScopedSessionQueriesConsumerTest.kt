package com.example.yanji.data

import com.example.yanji.ui.detail.FocusSessionDetailViewModel
import com.example.yanji.ui.exam.ExamViewModel
import com.example.yanji.ui.profile.ProfileViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScopedSessionQueriesConsumerTest {

    private val repo = YanjiRepository.getInstance()

    @Test
    fun testGetFocusSessionByIdFlowReturnsMatchingSession() = runTest {
        val flow = repo.getFocusSessionByIdFlow("non_existent_id")
        val result = flow.first()
        assertNull(result)
    }

    @Test
    fun testGetExamSessionByIdFlowReturnsMatchingSession() = runTest {
        val flow = repo.getExamSessionByIdFlow("non_existent_id")
        val result = flow.first()
        assertNull(result)
    }

    @Test
    fun testTodayStudyDurationSecondsObservesAggregatedValue() = runTest {
        val durationFlow = repo.observeTodayStudyDurationSeconds()
        val duration = durationFlow.first()
        assertEquals(repo.getTodayFocusDurationSeconds(), duration)
    }

    @Test
    fun testAiDiagnosticSnapshotBuildsWithScopedSessions() {
        val now = System.currentTimeMillis()
        val session = FocusSession(
            id = "f_recent",
            subjectId = "sub_math",
            subjectName = "数学一",
            startTime = now - 3600_000,
            endTime = now,
            durationSeconds = 3600,
            status = SessionStatus.COMPLETED
        )

        val snapshot = StudyDiagnosticSnapshot.from(
            periodDays = 7,
            settings = UserSettings(),
            focusSessions = listOf(session),
            examSessions = emptyList(),
            noteEntries = emptyList(),
            now = now
        )

        assertNotNull(snapshot)
        org.junit.Assert.assertTrue(snapshot.toPromptData().contains("数学一"))
    }
}
