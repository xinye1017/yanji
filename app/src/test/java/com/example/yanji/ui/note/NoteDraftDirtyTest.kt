package com.example.yanji.ui.note

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑页判脏逻辑的行为契约：决定「返回时是否弹未保存确认」与「完成按钮是否点亮」。
 */
class NoteDraftDirtyTest {

    private fun snapshot(
        content: String = "原文",
        mood: Int = 5,
        favorite: Boolean = false
    ) = EditorSnapshot(content = content, moodScore = mood, isFavorite = favorite)

    @Test
    fun identicalContentIsNotDirty() {
        assertFalse(
            isNoteDraftDirty(currentContent = "原文", snapshot = snapshot(), moodScore = 5, isFavorite = false)
        )
    }

    @Test
    fun whitespaceOnlyDifferenceIsNotDirty() {
        // 首尾空白在 trim 后等价：改了又抹平不应算脏。
        assertFalse(
            isNoteDraftDirty(currentContent = "  原文  \n", snapshot = snapshot("原文"), moodScore = 5, isFavorite = false)
        )
        assertFalse(
            isNoteDraftDirty(currentContent = "", snapshot = snapshot(""), moodScore = 5, isFavorite = false)
        )
    }

    @Test
    fun bodyChangeIsDirtyIncludingSingleCharacter() {
        assertTrue(
            isNoteDraftDirty(currentContent = "原文！", snapshot = snapshot("原文"), moodScore = 5, isFavorite = false)
        )
    }

    @Test
    fun internalWhitespaceChangeIsDirty() {
        // trim 只削首尾，正文中间的空白差异必须仍然算脏，否则会误判为「没改过」。
        assertTrue(
            isNoteDraftDirty(currentContent = "原 文", snapshot = snapshot("原文"), moodScore = 5, isFavorite = false)
        )
    }

    @Test
    fun moodOrFavoriteChangeIsDirty() {
        assertTrue(
            isNoteDraftDirty(currentContent = "原文", snapshot = snapshot(mood = 5), moodScore = 3, isFavorite = false)
        )
        assertTrue(
            isNoteDraftDirty(currentContent = "原文", snapshot = snapshot(), moodScore = 5, isFavorite = true)
        )
    }
}
