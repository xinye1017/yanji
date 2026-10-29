package com.example.yanji.data.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerCompletion(
    val sessionId: String,
    val subjectName: String,
    val elapsedSeconds: Long,
    val isExam: Boolean
)

/** Presentation only. The service calls this after the coordinator successfully completes. */
class TimerCompletionFeedbackState {
    @Volatile var appVisible: Boolean = false
    private val _pending = MutableStateFlow<TimerCompletion?>(null)
    val pending = _pending.asStateFlow()

    /** True means the visible app owns the result; otherwise the service posts a notification. */
    @Synchronized
    fun present(completion: TimerCompletion): Boolean {
        if (!appVisible) return false
        _pending.value = completion
        return true
    }

    @Synchronized
    fun acknowledge(sessionId: String) {
        if (_pending.value?.sessionId == sessionId) _pending.value = null
    }
}

object TimerCompletionFeedback {
    val state = TimerCompletionFeedbackState()
}
