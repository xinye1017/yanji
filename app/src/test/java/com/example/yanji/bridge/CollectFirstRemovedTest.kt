package com.example.yanji.bridge

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `collectFirst` 已被彻底删除的**源码级**守卫（F1）。
 *
 * 背景：`YanjiDataModule.collectFirst` 在 collector 里 `throw` 一个私有异常来中断采集，
 * 异常穿出 `suspend` 函数使 `return result` 不可达，三个调用点的 `promise.resolve`
 * 因此永不执行 —— JS promise 永不 settle。行为语义由 `CollectFirstSemanticsTest` 锁定，
 * 这里负责确保**坏代码没有悄悄回来**，且调用点用的是仓库惯用法 `flow.first()`。
 *
 * 断言前先剥掉注释与字符串字面量：KDoc 里可以合法地复述这段历史，但**代码**里
 * 不允许再出现这个符号。
 */
class CollectFirstRemovedTest {

    private fun moduleSource(): String {
        val file = File("src/main/java/com/example/yanji/bridge/YanjiDataModule.kt")
        assertTrue("找不到 $file", file.exists())
        return file.readText()
    }

    /** 去掉块注释 / 行注释，避免把历史说明误判成代码。 */
    private fun codeOnly(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    @Test
    fun collectFirstAndFirstEmissionExceptionAreGoneFromTheModule() {
        val code = codeOnly(moduleSource())

        assertFalse(
            "collectFirst 必须被删除，而不是留作兼容垫片",
            code.contains("collectFirst")
        )
        assertFalse(
            "FirstEmissionException 必须随之删除",
            code.contains("FirstEmissionException")
        )
        assertFalse(
            "绝不引入在 collector 里 throw 来中断采集的写法",
            code.contains("throw FirstEmission")
        )
    }

    @Test
    fun everyFormerCallSiteNowUsesFlowFirst() {
        val code = codeOnly(moduleSource())

        // 三处一次性快照读取各自都改用 flow.first()
        assertTrue(
            "getTodayStats 必须用 flow.first()",
            Regex("""repository\.observeStudyTasks\(date\)\.first\(\)""").containsMatchIn(code)
        )
        assertTrue(
            "getReviewOverview 必须用 flow.first() 读取窗口内的时段",
            code.contains("repository.observeFocusSessionsInRange(range.startInclusive, range.endExclusive).first()")
        )
        assertTrue(
            "getTodayTasks / getDailyTimeline 必须用 flow.first() 读计划与实际秒数",
            code.contains("repository.observeTaskActualSeconds().first()")
        )
        assertTrue("必须导入 kotlinx.coroutines.flow.first", code.contains("import kotlinx.coroutines.flow.first"))

        // 坏写法一次都不允许出现
        assertFalse("不得残留 collectFirst(...) 调用", code.contains("collectFirst("))
    }

    @Test
    fun saveQuickNoteUsesTheAwaitableWritePath() {
        val code = codeOnly(moduleSource())

        assertTrue(
            "saveQuickNote 必须走可等待的写入路径，否则 promise 会在落库前 resolve",
            code.contains("repository.addOrUpdateNoteAndAwait(entry)")
        )
        assertFalse(
            "不得再调用非等待的 addOrUpdateNote(entry)",
            code.contains("repository.addOrUpdateNote(entry)")
        )
    }

    @Test
    fun noFabricatedSettingsValuesRemain() {
        val code = codeOnly(moduleSource())

        assertFalse("focusDurationMinutes 必须被移除", code.contains("focusDurationMinutes"))
        assertFalse("breakDurationMinutes 必须被移除", code.contains("breakDurationMinutes"))
        assertFalse("不得保留恒为 0 的 actualMinutes 占位值", code.contains("""putInt("actualMinutes", 0)"""))
    }

    @Test
    fun flowFirstActuallyReturnsTheFirstEmission() = runTest {
        // 与桥接层的用法一致：Room 的 observe* Flow 至少发射一次，first() 取到它
        assertEquals(listOf<String>(), flowOf(emptyList<String>()).first())
        assertEquals("a", flowOf("a", "b", "c").first())
    }
}
