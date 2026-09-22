package com.example.yanji.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs

/**
 * 随笔编辑器撤销 / 重做（Undo / Redo）历史管理器。
 *
 * 核心机制：
 *  - **打字合并（Debounce & Coalesce）**：连续单字符键入合并为一个检查点，避免打一个字撤销一次；
 *    遇到空格、换行或停顿（> 800ms）自动开启新的检查点。
 *  - **格式操作显式检查点**：加粗、斜体、列表、标题、分割线等格式应用立即生成独立检查点。
 *  - **光标位置同步保留**：完整保存 [TextFieldValue]，撤销/重做时准确恢复光标位置。
 *  - **历史深度上限**：默认保留最近 50 步，避免内存泄漏。
 */
class EditorHistory(
    private val maxDepth: Int = 50
) {
    private val undoStack = mutableListOf<TextFieldValue>()
    private val redoStack = mutableListOf<TextFieldValue>()
    private var isPerformingUndoOrRedo = false
    private var lastEditTime = 0L

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /**
     * 记录普通键盘输入。
     */
    fun recordTyping(oldValue: TextFieldValue, newValue: TextFieldValue) {
        if (isPerformingUndoOrRedo) return
        if (oldValue.text == newValue.text) return // 仅光标变动不记录

        val now = System.currentTimeMillis()
        val textDiff = newValue.text.length - oldValue.text.length
        val isSingleChar = abs(textDiff) == 1
        val isRecent = (now - lastEditTime) < 800L
        val isDelimiter = isSingleChar && (
            newValue.text.endsWith(" ") || newValue.text.endsWith("\n") ||
            oldValue.text.endsWith(" ") || oldValue.text.endsWith("\n")
        )

        redoStack.clear()

        if (isSingleChar && isRecent && !isDelimiter && undoStack.isNotEmpty()) {
            // 连续输入单字，合并在同一批次中
        } else {
            undoStack.add(oldValue)
            if (undoStack.size > maxDepth) {
                undoStack.removeAt(0)
            }
        }
        lastEditTime = now
        updateFlags()
    }

    /**
     * 显式记录一个检查点（用于格式按钮、删除整段等格式化操作前）。
     */
    fun recordExplicitSnapshot(currentValue: TextFieldValue) {
        if (isPerformingUndoOrRedo) return
        redoStack.clear()
        undoStack.add(currentValue)
        if (undoStack.size > maxDepth) {
            undoStack.removeAt(0)
        }
        lastEditTime = 0L
        updateFlags()
    }

    /**
     * 执行撤回。返回要恢复的目标 [TextFieldValue]，若无可撤回项则返回 null。
     */
    fun undo(currentValue: TextFieldValue): TextFieldValue? {
        if (undoStack.isEmpty()) return null
        isPerformingUndoOrRedo = true
        val target = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(currentValue)
        if (redoStack.size > maxDepth) {
            redoStack.removeAt(0)
        }
        isPerformingUndoOrRedo = false
        lastEditTime = 0L
        updateFlags()
        return target
    }

    /**
     * 执行反撤回（重做）。返回要恢复的目标 [TextFieldValue]，若无可重做项则返回 null。
     */
    fun redo(currentValue: TextFieldValue): TextFieldValue? {
        if (redoStack.isEmpty()) return null
        isPerformingUndoOrRedo = true
        val target = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(currentValue)
        if (undoStack.size > maxDepth) {
            undoStack.removeAt(0)
        }
        isPerformingUndoOrRedo = false
        lastEditTime = 0L
        updateFlags()
        return target
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        isPerformingUndoOrRedo = false
        lastEditTime = 0L
        updateFlags()
    }
}
