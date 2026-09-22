package com.example.yanji.data.ai

import com.example.yanji.data.FocusSession
import com.example.yanji.data.StudyDiagnosticSnapshot
import com.example.yanji.data.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AiClientTest {

    private val settings = UserSettings(
        aiBaseUrl = "https://example.test/v1",
        aiApiKey = "test-key",
        aiModel = "test-model"
    )
    private val snapshot = StudyDiagnosticSnapshot.from(
        periodDays = 7,
        settings = UserSettings(),
        focusSessions = listOf(FocusSession("f1", "math", "数学", 1_700_000_000_000L, 1_700_000_003_600L, 3_600L)),
        examSessions = emptyList(),
        noteEntries = emptyList(),
        now = 1_700_000_004_000L
    )

    @Test
    fun validResponseReturnsContentAndAlwaysDisconnects() = runBlocking {
        val connection = FakeConnection(
            status = 200,
            responseBody = """{"choices":[{"message":{"content":"可用回复"}}]}"""
        )

        val result = client(connection).diagnoseRaw(snapshot, settings)

        assertEquals("可用回复", result)
        assertTrue(connection.disconnected)
        assertTrue(connection.writtenBody.toString(Charsets.UTF_8.name()).contains("\"messages\""))
        assertTrue(connection.writtenBody.toString(Charsets.UTF_8.name()).contains("study_snapshot"))
    }

    @Test
    fun fourHundredAndFiveHundredFailuresAreClassifiedWithoutRawHtml() = runBlocking {
        val clientError = FakeConnection(422, errorBody = "<html>provider secret diagnostics</html>")
        val clientException = expectAiException {
            client(clientError).diagnoseRaw(snapshot, settings)
        }
        assertEquals(AiFailure.ClientRequest, clientException.failure)
        assertFalse(clientException.message.orEmpty().contains("provider secret"))
        assertTrue(clientError.disconnected)

        val serverError = FakeConnection(503, errorBody = """{"error":{"message":"temporary"}}""")
        val serverException = expectAiException {
            client(serverError).diagnoseRaw(snapshot, settings)
        }
        assertEquals(AiFailure.Service, serverException.failure)
        assertTrue(serverError.disconnected)
    }

    @Test
    fun authenticationAndRateLimitHaveStableCategories() = runBlocking {
        val unauthorized = expectAiException {
            client(FakeConnection(401)).diagnoseRaw(snapshot, settings)
        }
        assertEquals(AiFailure.Authentication, unauthorized.failure)

        val throttled = expectAiException {
            client(FakeConnection(429)).diagnoseRaw(snapshot, settings)
        }
        assertEquals(AiFailure.Quota, throttled.failure)
    }

    @Test
    fun malformedJsonAndEmptyPayloadAreInvalidResponses() = runBlocking {
        val malformed = expectAiException {
            client(FakeConnection(200, responseBody = "<html>not json</html>"))
                .diagnoseRaw(snapshot, settings)
        }
        assertEquals(AiFailure.InvalidResponse, malformed.failure)
        assertTrue(malformed.message.orEmpty().contains("JSON"))

        val empty = expectAiException {
            client(FakeConnection(200, responseBody = "   "))
                .diagnoseRaw(snapshot, settings)
        }
        assertEquals(AiFailure.InvalidResponse, empty.failure)
        assertTrue(empty.message.orEmpty().contains("内容为空"))
    }

    @Test
    fun socketTimeoutHasTimeoutCategoryAndDisconnects() = runBlocking {
        val connection = FakeConnection(200, responseCodeFailure = SocketTimeoutException("read timed out"))

        val exception = expectAiException {
            client(connection).diagnoseRaw(snapshot, settings)
        }

        assertEquals(AiFailure.Timeout, exception.failure)
        assertTrue(connection.disconnected)
    }

    @Test
    fun coroutineCancellationDisconnectsBlockedConnectionPromptly() = runBlocking {
        val connection = BlockingConnection()
        val request = async(Dispatchers.Default) {
            client(connection).diagnoseRaw(snapshot, settings)
        }
        assertTrue("request did not reach responseCode", connection.entered.await(2, TimeUnit.SECONDS))

        request.cancelAndJoin()

        assertTrue("cancellation must disconnect the active socket", connection.disconnected)
        assertTrue("blocked responseCode must be released", connection.released.await(2, TimeUnit.SECONDS))
    }

    private fun client(connection: HttpURLConnection): AiClient =
        AiClient(HttpConnectionFactory { connection }, Dispatchers.IO)

    private suspend fun expectAiException(block: suspend () -> Unit): AiException {
        try {
            block()
        } catch (error: AiException) {
            return error
        }
        throw AssertionError("Expected AiException")
    }

    private open class FakeConnection(
        private val status: Int,
        private val responseBody: String = "",
        private val errorBody: String = "",
        private val responseCodeFailure: Throwable? = null
    ) : HttpURLConnection(URL("https://example.test/v1/chat/completions")) {
        val writtenBody = ByteArrayOutputStream()
        @Volatile
        var disconnected: Boolean = false

        override fun connect() = Unit
        override fun usingProxy(): Boolean = false
        override fun disconnect() {
            disconnected = true
        }

        override fun getResponseCode(): Int {
            responseCodeFailure?.let { throw it }
            return status
        }

        override fun getInputStream(): InputStream =
            ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getErrorStream(): InputStream? =
            errorBody.takeIf { it.isNotEmpty() }
                ?.let { ByteArrayInputStream(it.toByteArray(Charsets.UTF_8)) }

        override fun getOutputStream(): OutputStream = writtenBody
    }

    private class BlockingConnection : FakeConnection(status = 200) {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)

        override fun getResponseCode(): Int {
            entered.countDown()
            released.await(5, TimeUnit.SECONDS)
            if (disconnected) throw IOException("disconnected")
            return 200
        }

        override fun disconnect() {
            super.disconnect()
            released.countDown()
        }
    }
}
