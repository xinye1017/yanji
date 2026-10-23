package com.example.yanji.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 桥接层**仍然存活**的写入契约守卫（F08 / C6 / P3-1）。
 *
 * 背景：`YanjiDataModule.collectFirst` 曾在 collector 里 `throw` 私有异常来中断采集，
 * 异常穿出 `suspend` 函数让三个调用点的 `promise.resolve` 永不执行 —— JS promise 永不
 * settle。该坏写法及其同构复现测试（`CollectFirstSemanticsTest`）已随符号一起删除，
 * 这里只保留**目标仍在生产代码里**的那部分断言。
 *
 * 为什么保留而不是连带删掉：`saveQuickNote` 仍然把 `repository.addOrUpdateNoteAndAwait(entry)`
 * 当作唯一写入路径（`YanjiDataModule.kt:164`），而非同名的非等待 `addOrUpdateNote(entry)`。
 * 一旦回退到非等待变体，promise 会在行落库前 resolve，JS 侧紧接着的读取就会漏掉这条记录 ——
 * 这正是被删测试里唯一还有活的断言。`updateNote` 走同一条可等待约定。
 *
 * 断言前先剥掉注释与字符串字面量：KDoc 里可以合法地复述这段历史，但**代码**里不允许。
 */
class BridgeWriteContractTest {

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
    fun quickNoteWritePathStaysAwaitable() {
        val code = codeOnly(moduleSource())

        assertTrue(
            "saveQuickNote 必须走可等待的写入路径，否则 promise 会在落库前 resolve",
            code.contains("repository.addOrUpdateNoteAndAwait(entry)")
        )
        assertFalse(
            "不得调用非等待的 addOrUpdateNote(entry)：那会让 JS 读到漏行",
            code.contains("repository.addOrUpdateNote(entry)")
        )
        assertTrue(
            "updateNote 同样必须可等待，编辑后立刻读到的就是新正文",
            code.contains("repository.updateNoteContentAndAwait(noteId, cleanContent)")
        )
    }

    @Test
    fun favoriteToggleWaitsForRoomWithoutReadingTheMemoryCache() {
        val code = codeOnly(moduleSource())
            .substringAfter("fun toggleFavoriteNote(")
            .substringBefore("@ReactMethod")
        assertTrue(
            "收藏必须等待 Room 原子切换完成后再返回，不能读取可能尚未回灌的内存列表",
            code.contains("promise.resolve(repository.toggleNoteFavoriteAndAwait(noteId))")
        )
        assertFalse("收藏不能依赖内存缓存", code.contains("noteEntries"))
        assertTrue("写入失败必须拒绝 Promise，让界面结束等待并显示错误", code.contains("promise.reject("))
    }

    @Test
    fun oneShotReadsSettleTheirPromiseOnEveryPath() {
        val code = codeOnly(moduleSource())

        // getDailySnapshot 用 try/catch 包住一次性读取：成功 resolve、失败 reject，
        // 两种出口都存在，promise 不会悬挂。 CancellationException 必须原样抛出让协程取消。
        assertTrue(
            "一次性快照读取必须用 repository.getDailySnapshot(date)",
            code.contains("repository.getDailySnapshot(date)")
        )
        assertTrue(
            "失败路径必须 reject，否则 JS await 永不 settle",
            Regex("""catch \(error: Exception\) \{\s*promise\.reject\(""")
                .containsMatchIn(code)
        )
        assertTrue(
            "取消必须原样抛出，不能被 catch(Exception) 吞成 reject",
            code.contains("catch (cancelled: CancellationException) {")
        )
    }

    @Test
    fun throwInCollectorInterruptionStaysRemoved() {
        val code = codeOnly(moduleSource())

        assertFalse("collectFirst 必须保持删除状态", code.contains("collectFirst"))
        assertFalse("FirstEmissionException 必须保持删除状态", code.contains("FirstEmissionException"))
        assertFalse("不得引入在 collector 里 throw 来中断采集的写法", code.contains("throw FirstEmission"))
        // 回顾概览已改为一次性的批量投影读取（仓库层 suspend 事务），
        // 不再需要 Flow 的 `.first()`；关键是仍然不靠 collector 抛异常来结束采集。
        assertTrue(
            "回顾概览必须走一次性的批量读取器",
            code.contains("ReviewOverviewReader.read(repository, requests)")
        )
    }
}
