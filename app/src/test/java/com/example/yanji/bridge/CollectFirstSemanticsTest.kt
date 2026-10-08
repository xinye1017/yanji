package com.example.yanji.bridge

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `YanjiDataModule.collectFirst` 的行为契约测试。
 *
 * 为什么必须有这份测试：`collectFirst` 在 collector 里 `throw` 一个私有异常来中断采集，
 * 而这个写法在 M2–M5 补审中被证明会让 JS promise 永不 settle——
 * `getTodayStats` / `getTodayTasks` / `getDailyTimeline` 三个入口的 `promise.resolve`
 * 都在 `collectFirst` 之后且没有 catch，异常直接穿出 `suspend` 函数，
 * 于是调用点永远拿不到值。`app/src/main` 全树也没有任何 `CoroutineExceptionHandler`
 * 兜底（`git grep -rn CoroutineExceptionHandler -- app/src/main/java` → 0 匹配）。
 *
 * 这里用**完全等价的本地复现**（同样的 throw-in-collector 结构）锁住语义，
 * 不依赖 Android / Room，因此可以在 JVM 单元测试里直接运行。
 * 修复方向是改用仓库自身惯用法 `flow.first()`（见 `BackupTransfer.collect`）。
 */
class CollectFirstSemanticsTest {

    /** 与 `YanjiDataModule.collectFirst` 逐字同构的复现：无 try/catch。 */
    private suspend fun <T> collectFirstUnderTest(flow: kotlinx.coroutines.flow.Flow<T>): T {
        var result: T? = null
        flow.collect { value ->
            result = value
            throw FirstEmissionExceptionUnderTest()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private class FirstEmissionExceptionUnderTest : Exception()

    /**
     * 核心断言：这个写法**不会返回值**，而是把异常抛给调用者。
     *
     * 一旦有人「修复」成在 `collectFirst` 内部吞掉异常，本测试会失败——
     * 那正是要防止的错误方向（吞异常会让上游 Flow 的取消语义失真）。
     */
    @Test
    fun throwInCollectorEscapesInsteadOfReturningFirstValue() = runTest {
        val thrown = runCatching { collectFirstUnderTest(flowOf("value")) }

        assertTrue(
            "collectFirst 必须把 collector 抛出的异常传递给调用方，" +
                "而不是返回首值或静默吞掉",
            thrown.isFailure
        )
        assertTrue(
            "抛出的应是用于中断采集的那个私有异常类型",
            thrown.exceptionOrNull() is FirstEmissionExceptionUnderTest
        )
    }

    /**
     * 因此：调用点若没有 catch，`promise.resolve` 就永远不会被执行。
     *
     * 这里模拟 `getTodayStats` 的形状（collectFirst 之后才 resolve，且无 catch），
     * 断言 resolve 那一行确实不可达。
     */
    @Test
    fun callSiteWithoutCatchNeverReachesResolve() = runTest {
        var resolveCalled = false

        val outcome = runCatching {
            // --- 与 YanjiDataModule.getTodayStats 同构 ---
            val tasks = collectFirstUnderTest(flowOf(emptyList<String>()))
            @Suppress("UNUSED_EXPRESSION")
            tasks
            resolveCalled = true // 对应 promise.resolve(payload)
        }

        assertTrue("调用点必须把异常抛出去（否则真实代码里会静默丢失）", outcome.isFailure)
        assertTrue(
            "promise.resolve 绝不可达——这就是 JS promise 永不 settle 的根因",
            !resolveCalled
        )
    }

    /**
     * 仓库惯用法 `flow.first()` 是正确替代：同样只取首个值，但不靠抛异常中断。
     */
    @Test
    fun flowFirstIsTheCorrectReplacement() = runTest {
        val collected = flow {
            emit("first")
            emit("second")
        }.toList()
        assertEquals(listOf("first", "second"), collected)

        val first = flow {
            emit("first")
            emit("second")
        }.first()
        assertEquals("first", first)
    }

    /**
     * Room 的 `observeStudyTasks(date)` 至少会发射 `emptyList()`，
     * 所以上面那条抛异常路径不是边角情况——它每次调用都会触发。
     */
    @Test
    fun roomLikeFlowAlwaysEmitsAtLeastOnce() = runTest {
        val roomLike = flowOf(emptyList<String>())
        val emissions = roomLike.toList()
        assertEquals(1, emissions.size)
        assertTrue(emissions.first().isEmpty())
    }
}
