package com.example.yanji.bridge

import com.facebook.react.bridge.Promise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PersistedWritePromiseTest {
    private class RecordingPromise {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val promise = Proxy.newProxyInstance(Promise::class.java.classLoader, arrayOf(Promise::class.java)) { _, method, args ->
            calls += method.name to (args?.toList() ?: emptyList())
            null
        } as Promise
    }

    @Test fun promiseRemainsPendingUntilWriteCompletes() = runTest {
        val result = RecordingPromise()
        val commit = CompletableDeferred<Unit>()
        launchPersistedWrite(result.promise, "E_WRITE") {
            commit.await()
            result.promise.resolve(true)
        }
        runCurrent()
        assertTrue(result.calls.isEmpty())
        commit.complete(Unit)
        runCurrent()
        assertEquals(listOf("resolve" to listOf(true)), result.calls)
    }

    @Test fun writeExceptionRejectsOnceWithOriginalCauseAndAllowsRetry() = runTest {
        val result = RecordingPromise()
        val failure = IllegalStateException("write failed")
        launchPersistedWrite(result.promise, "E_WRITE") { throw failure }
        runCurrent()
        assertEquals("reject", result.calls.single().first)
        assertEquals("E_WRITE", result.calls.single().second[0])
        assertSame(failure, result.calls.single().second.last())
        launchPersistedWrite(result.promise, "E_WRITE") { result.promise.resolve(true) }
        runCurrent()
        assertEquals(listOf("reject", "resolve"), result.calls.map { it.first })
    }

    @Test fun bridgeInvalidationCancelsWriteWithoutReportingSuccessOrSaveFailure() = runTest {
        val result = RecordingPromise()
        val job = launchPersistedWrite(result.promise, "E_WRITE") { CompletableDeferred<Unit>().await() }
        runCurrent()
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertTrue(result.calls.isEmpty())
    }
}
