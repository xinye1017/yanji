package com.example.yanji.ui.note

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * [EditorHistory] 单元测试：验证撤回/反撤回、打字合并、显式快照与边界条件。
 */
class NoteEditorHistoryTest {

    private lateinit var history: EditorHistory

    @Before
    fun setUp() {
        history = EditorHistory(maxDepth = 10)
    }

    @Test
    fun initialStateHasNoUndoOrRedo() {
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo(TextFieldValue("test")))
        assertNull(history.redo(TextFieldValue("test")))
    }

    @Test
    fun explicitSnapshotEnablesUndoAndClearsRedo() {
        val initial = TextFieldValue("Hello", TextRange(5))
        history.recordExplicitSnapshot(initial)

        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        val current = TextFieldValue("**Hello**", TextRange(9))
        val restored = history.undo(current)

        assertNotNull(restored)
        assertEquals("Hello", restored?.text)
        assertEquals(TextRange(5), restored?.selection)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        val redone = history.redo(restored!!)
        assertNotNull(redone)
        assertEquals("**Hello**", redone?.text)
        assertEquals(TextRange(9), redone?.selection)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun newChangeClearsRedoStack() {
        val v1 = TextFieldValue("A")
        val v2 = TextFieldValue("AB")
        history.recordExplicitSnapshot(v1)

        val undone = history.undo(v2)
        assertEquals("A", undone?.text)
        assertTrue(history.canRedo)

        // 用户在撤回状态下进行了新的修改，必须清空 redo 栈
        history.recordExplicitSnapshot(TextFieldValue("AC"))
        assertFalse(history.canRedo)
    }

    @Test
    fun typingWithDelimitersCreatesNewCheckpoints() {
        val v0 = TextFieldValue("")
        val v1 = TextFieldValue("H")
        val v2 = TextFieldValue("Hi")
        val v3 = TextFieldValue("Hi ")
        val v4 = TextFieldValue("Hi there")

        history.recordTyping(v0, v1)
        history.recordTyping(v1, v2)
        // 空格触发新的检查点
        history.recordTyping(v2, v3)
        history.recordExplicitSnapshot(v3)
        history.recordTyping(v3, v4)

        assertTrue(history.canUndo)
        val undone1 = history.undo(v4)
        assertNotNull(undone1)
        assertEquals("Hi ", undone1?.text)
    }

    @Test
    fun maxDepthLimitIsEnforced() {
        val smallHistory = EditorHistory(maxDepth = 3)
        for (i in 1..5) {
            smallHistory.recordExplicitSnapshot(TextFieldValue("Step $i"))
        }

        // 最多只能撤回 3 步
        var count = 0
        var current = TextFieldValue("Final")
        while (smallHistory.canUndo) {
            val prev = smallHistory.undo(current)
            if (prev != null) {
                current = prev
                count++
            }
        }
        assertEquals(3, count)
        assertEquals("Step 3", current.text)
    }
}
