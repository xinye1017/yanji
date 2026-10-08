package com.example.yanji.bridge

import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyTask
import com.example.yanji.data.UserSettings
import com.example.yanji.data.timer.ActiveSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桥接契约词汇表测试（F2）。
 *
 * 权威：`src/bridge/index.ts` 的 `TimerPhase = 'FOCUS' | 'BREAK' | 'IDLE'` 与
 * `TimerMode = 'COUNTDOWN' | 'STOPWATCH'`。Kotlin 侧必须逐字匹配。
 *
 * 为什么必须挡住两类泄漏：
 *  1. `TimerPhase` 的枚举名（RUNNING / PAUSED / COMPLETED / CANCELLED）——
 *     TS 类型里根本没有这些取值，收到就是契约违背；
 *  2. 中文 `FocusModes` 展示名（`正向计时`）—— 那是给 Compose UI 看的，
 *     透传到桥对面会让 `mode` 失去可判定性。
 */
class TimerVocabularyTest {

    private val allowedPhases = setOf("FOCUS", "BREAK", "IDLE")
    private val allowedModes = setOf("COUNTDOWN", "STOPWATCH")

    /** 契约明令禁止出现在 phase 里的 `TimerPhase` 枚举名。 */
    private val forbiddenPhaseNames =
        setOf("RUNNING", "PAUSED", "COMPLETED", "CANCELLED", "IDLE_LEGACY")

    private fun session(
        paused: Boolean = false,
        targetDurationSeconds: Long = 0L,
        mode: String = "正向计时"
    ) = ActiveSession(
        sessionId = "s-1",
        kind = com.example.yanji.data.timer.ActiveSessionKind.FOCUS,
        subjectId = "math",
        subjectName = "高等数学",
        mode = mode,
        targetDurationSeconds = targetDurationSeconds,
        paused = paused
    )

    @Test
    fun runningCountdownSession_emitsFocusAndCountdown() {
        val fields = BridgeMappers.sessionFields(session(targetDurationSeconds = 1500L), 60L, 1440L)

        assertEquals("FOCUS", fields.phase)
        assertEquals("COUNTDOWN", fields.mode)
        assertFalse(fields.isPaused)
        assertTrue(fields.isCountdown)
        assertTrue(fields.phase in allowedPhases)
        assertTrue(fields.mode in allowedModes)
    }

    @Test
    fun pausedCountdownSession_isStillFocusWithPauseCarriedSeparately() {
        val fields = BridgeMappers.sessionFields(session(paused = true, targetDurationSeconds = 1500L), 60L, 1440L)

        // 暂停绝不占用 phase 的取值空间
        assertEquals("FOCUS", fields.phase)
        assertTrue(fields.isPaused)
        assertEquals("COUNTDOWN", fields.mode)
    }

    @Test
    fun stopwatchSession_emitsFocusAndStopwatch() {
        val fields = BridgeMappers.sessionFields(session(targetDurationSeconds = 0L), 120L, 0L)

        assertEquals("FOCUS", fields.phase)
        assertEquals("STOPWATCH", fields.mode)
        assertFalse(fields.isCountdown)
    }

    @Test
    fun pausedStopwatchSession_isStillFocusWithStopwatchMode() {
        val fields = BridgeMappers.sessionFields(session(paused = true, targetDurationSeconds = 0L), 120L, 0L)

        assertEquals("FOCUS", fields.phase)
        assertEquals("STOPWATCH", fields.mode)
        assertTrue(fields.isPaused)
    }

    @Test
    fun everyEmittedPhaseIsInTheContractSetAndNeverAnEnumName() {
        // 覆盖 running / paused × countdown / stopwatch 四种组合
        listOf(false, true).forEach { paused ->
            listOf(0L, 1500L).forEach { target ->
                val fields = BridgeMappers.sessionFields(
                    session(paused = paused, targetDurationSeconds = target),
                    30L,
                    0L
                )
                assertTrue(
                    "phase '${fields.phase}' 必须是 FOCUS/BREAK/IDLE 之一",
                    fields.phase in allowedPhases
                )
                assertTrue(
                    "phase 绝不可是 TimerPhase 枚举名（实测 ${fields.phase}）",
                    fields.phase !in forbiddenPhaseNames
                )
                assertTrue(
                    "mode '${fields.mode}' 必须是 COUNTDOWN/STOPWATCH 之一",
                    fields.mode in allowedModes
                )
                // 中文展示名绝不外泄
                assertFalse(fields.mode.contains("正"))
                assertFalse(fields.mode.contains("分钟"))
                assertFalse(fields.mode.contains("番茄"))
            }
        }
    }

    @Test
    fun modeIsDerivedFromTargetDurationAndNotFromTheChineseModeName() {
        // 同样的中文 mode 名，目标时长决定 COUNTDOWN / STOPWATCH
        val countdown = BridgeMappers.timerModeOf(1500L)
        val stopwatch = BridgeMappers.timerModeOf(0L)
        assertEquals("COUNTDOWN", countdown)
        assertEquals("STOPWATCH", stopwatch)

        // 负值同样按「不限时长」处理（不可能出现，但绝不产生第三种取值）
        assertEquals("STOPWATCH", BridgeMappers.timerModeOf(-1L))
    }

    @Test
    fun tickPhaseUsesTheSameVocabularyAsSessionPhase() {
        assertEquals(BridgeMappers.sessionPhaseOf(hasSession = true), BridgeMappers.tickPhase(hasSession = true))
        assertEquals("FOCUS", BridgeMappers.tickPhase(hasSession = true))
        // 没有会话时 onTimerTick 不会发射，但词汇表仍只允许这三个值
        assertEquals("IDLE", BridgeMappers.tickPhase(hasSession = false))
        assertEquals("BREAK", BridgeMappers.PHASE_BREAK)
    }

    @Test
    fun sessionFields_preservesSessionIdentityAndBlankTaskIdIsNormalisedToNull() {
        val s = session(targetDurationSeconds = 1500L).copy(taskId = "  ")
        val fields = BridgeMappers.sessionFields(s, 10L, 1490L)

        assertEquals("s-1", fields.sessionId)
        assertEquals("math", fields.subjectId)
        assertEquals("高等数学", fields.subjectName)
        assertNull("空白 taskId 必须归一化为 null，绝不透传空串", fields.taskId)
    }

    @Test
    fun companionConstantsPointAtTheSingleVocabularySource() {
        assertEquals("FOCUS", YanjiTimerModule.PHASE_FOCUS)
        assertEquals("BREAK", YanjiTimerModule.PHASE_BREAK)
        assertEquals("IDLE", YanjiTimerModule.PHASE_IDLE)
        assertEquals(BridgeMappers.PHASE_FOCUS, YanjiTimerModule.PHASE_FOCUS)
        assertEquals(BridgeMappers.MODE_COUNTDOWN, "COUNTDOWN")
        assertEquals(BridgeMappers.MODE_STOPWATCH, "STOPWATCH")
    }
}
