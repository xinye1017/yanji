package com.example.yanji.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import com.example.yanji.YanjiSubScreen
import com.example.yanji.YanjiSubScreenStackSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航栈跨进程销毁/重建（Process Death / Activity Recreation）单测。
 * 验证：
 * 1. 子页面路由全部可序列化；
 * 2. 深度导航栈保存后通过 Saver 还原时完全保留层级与参数；
 * 3. 空栈还原不崩溃；
 * 4. 损坏的 JSON 容错恢复不导致崩溃。
 */
import androidx.compose.runtime.saveable.SaverScope

private val testSaverScope = SaverScope { true }

private fun <Original, Saveable : Any> androidx.compose.runtime.saveable.Saver<Original, Saveable>.saveValue(value: Original): Saveable? =
    with(this) { testSaverScope.save(value) }

class NavigationRecreationTest {

    @Test
    fun testSubScreenStackSavesAndRestoresAcrossProcessRecreation() {
        val originalStack = mutableStateListOf<YanjiSubScreen>(
            YanjiSubScreen.ExamHistory,
            YanjiSubScreen.ExamDetail(examId = "exam-2026-09-13"),
            YanjiSubScreen.DailyStudyDetail(date = "2026-09-13"),
            YanjiSubScreen.SubjectStudyDetail(subjectId = "math_advanced"),
            YanjiSubScreen.JournalEditor(journalId = "j-123", date = "2026-09-13"),
            YanjiSubScreen.JuanjuanChat,
            YanjiSubScreen.Achievements
        )

        // 模拟 onSaveInstanceState：通过 Saver 序列化
        val savedRepresentation = YanjiSubScreenStackSaver.saveValue(originalStack)
        org.junit.Assert.assertNotNull(savedRepresentation)
        assertEquals(7, savedRepresentation!!.size)

        // 模拟进程重建：通过 Saver 还原
        val restoredStack = YanjiSubScreenStackSaver.restore(savedRepresentation)
        org.junit.Assert.assertNotNull(restoredStack)
        assertEquals(7, restoredStack!!.size)

        assertEquals(YanjiSubScreen.ExamHistory, restoredStack[0])
        assertEquals(YanjiSubScreen.ExamDetail("exam-2026-09-13"), restoredStack[1])
        assertEquals(YanjiSubScreen.DailyStudyDetail("2026-09-13"), restoredStack[2])
        assertEquals(YanjiSubScreen.SubjectStudyDetail("math_advanced"), restoredStack[3])
        assertEquals(YanjiSubScreen.JournalEditor(journalId = "j-123", date = "2026-09-13"), restoredStack[4])
        assertEquals(YanjiSubScreen.JuanjuanChat, restoredStack[5])
        assertEquals(YanjiSubScreen.Achievements, restoredStack[6])
    }

    @Test
    fun testEmptyStackSavesAndRestoresCleanly() {
        val emptyStack = mutableStateListOf<YanjiSubScreen>()
        val saved = YanjiSubScreenStackSaver.saveValue(emptyStack)
        org.junit.Assert.assertNotNull(saved)
        assertTrue(saved!!.isEmpty())

        val restored = YanjiSubScreenStackSaver.restore(saved)
        org.junit.Assert.assertNotNull(restored)
        assertTrue(restored!!.isEmpty())
    }

    @Test
    fun testCorruptedEntriesAreDroppedGracefullyWithoutCrashing() {
        val corruptedSavedList = arrayListOf(
            """{"type":"com.example.yanji.YanjiSubScreen.Achievements"}""",
            """{corrupted-json-content}""",
            """{"type":"com.example.yanji.YanjiSubScreen.ExamHistory"}"""
        )

        val restored = YanjiSubScreenStackSaver.restore(corruptedSavedList)
        org.junit.Assert.assertNotNull(restored)
        // 损坏项被丢弃，合法项仍保留
        assertEquals(2, restored!!.size)
        assertEquals(YanjiSubScreen.Achievements, restored[0])
        assertEquals(YanjiSubScreen.ExamHistory, restored[1])
    }
}
