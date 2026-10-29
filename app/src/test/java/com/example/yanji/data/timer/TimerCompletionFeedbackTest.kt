package com.example.yanji.data.timer

import org.junit.Assert.*
import org.junit.Test

class TimerCompletionFeedbackTest {
    private val completion = TimerCompletion("session", "高等数学", 1500L, false)

    @Test fun backgroundCompletionBelongsToNotificationAndIsNotReplayedAsCard() {
        val state = TimerCompletionFeedbackState()
        assertFalse(state.present(completion))
        state.appVisible = true
        assertNull(state.pending.value)
    }

    @Test fun foregroundCompletionKeepsActualSubjectAndDurationUntilAcknowledged() {
        val state = TimerCompletionFeedbackState()
        state.appVisible = true
        assertTrue(state.present(completion))
        assertEquals(completion, state.pending.value)
        state.acknowledge("different-session")
        assertEquals(completion, state.pending.value)
        state.acknowledge("session")
        assertNull(state.pending.value)
    }

    @Test fun acknowledgingPreviousCardCannotDismissNewCompletion() {
        val state = TimerCompletionFeedbackState()
        state.appVisible = true
        state.present(completion)
        val next = completion.copy(sessionId = "new", elapsedSeconds = 3600)
        state.present(next)
        state.acknowledge(completion.sessionId)
        assertEquals(next, state.pending.value)
    }
}
