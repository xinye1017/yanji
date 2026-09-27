package com.example.yanji.data.ai

import android.util.Log
import com.example.yanji.data.StudyDiagnosticSnapshot
import com.example.yanji.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

internal fun interface HttpConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

/**
 * Third-party AI transport. Every connection has one owner, is disconnected in a finally block,
 * and is also disconnected immediately when the surrounding coroutine is cancelled.
 */
internal class AiClient(
    private val connectionFactory: HttpConnectionFactory =
        HttpConnectionFactory { url -> url.openConnection() as HttpURLConnection },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dataProvider: StudyDataProvider = DefaultStudyDataProvider()
) {

    companion object {
        private const val TAG = "YanjiAI"
        private const val USER_AGENT = "Yanji-Android/1.0"
    }

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        protocol: AiProtocolType = AiProtocolType.OPENAI_CHAT
    ): List<String> = withContext(ioDispatcher) {
        if (baseUrl.isBlank()) {
            throw AiException("Base URL 不能为空", failure = AiFailure.Endpoint)
        }

        var lastFailure: AiException = AiException("未能连接到模型接口", failure = AiFailure.Network)
        for (endpoint in AiProtocol.modelsUrls(baseUrl, protocol)) {
            try {
                val models = execute(
                    endpoint = endpoint,
                    method = "GET",
                    apiKey = apiKey,
                    protocol = protocol,
                    connectTimeout = 15_000,
                    readTimeout = 15_000
                ) { connection ->
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        throw httpException(
                            kind = AiCallKind.MODELS,
                            code = code,
                            detail = AiProtocol.extractErrorDetail(readErrorBody(connection)),
                            url = endpoint
                        )
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    AiProtocol.parseModels(body).distinct().sorted().also {
                        if (it.isEmpty()) {
                            throw AiException(
                                "模型接口返回了无法识别的 JSON",
                                failure = AiFailure.InvalidResponse
                            )
                        }
                    }
                }
                return@withContext models
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AiException) {
                lastFailure = failure
            }
        }
        throw lastFailure
    }

    suspend fun testModelConnection(
        baseUrl: String,
        apiKey: String,
        model: String,
        protocol: AiProtocolType
    ): String = withContext(ioDispatcher) {
        if (baseUrl.isBlank()) {
            throw AiException("API 地址不能为空", failure = AiFailure.Endpoint)
        }
        if (model.isBlank()) {
            throw AiException("请先选择或输入模型名称", failure = AiFailure.Endpoint)
        }

        val endpoint = AiProtocol.endpointUrl(baseUrl, protocol)
        val body = AiProtocol.buildRequestBody(
            protocol = protocol,
            model = model.trim(),
            systemPrompt = "You are a helpful assistant.",
            userPrompt = "hi",
            temperature = 0.0,
            maxTokens = 10
        )

        execute(
            endpoint = endpoint,
            method = "POST",
            apiKey = apiKey,
            protocol = protocol,
            connectTimeout = 15_000,
            readTimeout = 20_000
        ) { connection ->
            writeBody(connection, body)
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = AiProtocol.extractErrorDetail(readErrorBody(connection))
                throw httpException(AiCallKind.DIAGNOSIS, code, detail, endpoint)
            }
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val content = AiProtocol.extractContent(responseText, protocol)
            if (content.isNullOrBlank()) {
                throw AiException("模型响应内容为空", failure = AiFailure.InvalidResponse)
            }
            content.trim()
        }
    }

    suspend fun diagnoseRaw(
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings,
        companionName: String = snapshot.companionName
    ): String = withContext(ioDispatcher) {
        val protocol = AiProtocolType.fromId(settings.aiProtocol)
        val endpoint = AiProtocol.endpointUrl(settings.aiBaseUrl, protocol)
        val systemPrompt = dataProvider.buildSystemPrompt(companionName)
        val userPrompt = snapshot.toPromptData()
        val body = AiProtocol.buildRequestBody(
            protocol = protocol,
            model = effectiveModel(settings.aiModel, settings),
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.2,
            maxTokens = 3_500
        )

        execute(
            endpoint = endpoint,
            method = "POST",
            apiKey = settings.aiApiKey,
            protocol = protocol,
            connectTimeout = 20_000,
            readTimeout = 60_000
        ) { connection ->
            writeBody(connection, body)
            val code = connection.responseCode
            if (code !in 200..299) {
                readErrorBody(connection)
                Log.e(TAG, "AI diagnosis failed with HTTP $code (provider body redacted)")
                throw httpException(AiCallKind.DIAGNOSIS, code)
            }
            requireContent(
                response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                protocol = protocol,
                emptyMessage = "AI 诊断接口返回内容为空"
            )
        }
    }

    private fun effectiveModel(requested: String, settings: UserSettings): String = when {
        requested.isNotBlank() -> requested
        settings.aiModel.isNotBlank() -> settings.aiModel
        else -> AiProtocol.DEFAULT_MODEL
    }

    /**
     * Runs blocking URLConnection I/O on [ioDispatcher]. The cancellation handler can execute on
     * another thread and disconnect the socket while responseCode/readText is blocked.
     */
    private suspend fun <T> execute(
        endpoint: String,
        method: String,
        apiKey: String,
        protocol: AiProtocolType,
        connectTimeout: Int,
        readTimeout: Int,
        block: (HttpURLConnection) -> T
    ): T {
        val connection = try {
            openConnection(endpoint, method, apiKey, protocol, connectTimeout, readTimeout)
        } catch (error: Throwable) {
            throw translateTransportFailure(error)
        }

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { connection.disconnect() }
            try {
                val result = block(connection)
                if (continuation.isActive) continuation.resumeWith(Result.success(result))
            } catch (error: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(translateTransportFailure(error)))
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun openConnection(
        endpoint: String,
        method: String,
        apiKey: String,
        protocol: AiProtocolType,
        connectTimeout: Int,
        readTimeout: Int
    ): HttpURLConnection = connectionFactory.open(URL(endpoint)).apply {
        requestMethod = method
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", USER_AGENT)

        if (apiKey.isNotBlank()) {
            when (protocol) {
                AiProtocolType.ANTHROPIC -> {
                    setRequestProperty("x-api-key", apiKey.trim())
                    setRequestProperty("anthropic-version", "2023-06-01")
                }
                AiProtocolType.OPENAI_CHAT,
                AiProtocolType.OPENAI_RESPONSE -> {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
        } else if (protocol == AiProtocolType.ANTHROPIC) {
            setRequestProperty("anthropic-version", "2023-06-01")
        }

        this.connectTimeout = connectTimeout
        this.readTimeout = readTimeout
        if (method == "POST") doOutput = true
    }

    private fun writeBody(connection: HttpURLConnection, body: JSONObject) {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
            writer.flush()
        }
    }

    private fun readErrorBody(connection: HttpURLConnection): String =
        runCatching {
            BufferedReader(
                InputStreamReader(connection.errorStream ?: connection.inputStream, Charsets.UTF_8)
            ).use { it.readText() }
        }.getOrDefault("")

    private fun requireContent(
        response: String,
        protocol: AiProtocolType,
        emptyMessage: String
    ): String {
        if (response.isBlank()) {
            throw AiException(emptyMessage, failure = AiFailure.InvalidResponse)
        }
        if (runCatching { JSONObject(response) }.isFailure) {
            throw AiException("AI 返回的 JSON 无法解析", failure = AiFailure.InvalidResponse)
        }
        return AiProtocol.extractContent(response, protocol)
            ?: throw AiException(emptyMessage, failure = AiFailure.InvalidResponse)
    }

    private fun httpException(
        kind: AiCallKind,
        code: Int,
        detail: String = "",
        url: String = ""
    ): AiException = AiException(
        message = AiProtocol.describeHttpError(kind, code, detail, url),
        failure = AiFailure.fromHttpStatus(code)
    )

    private fun translateTransportFailure(error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is AiException -> error
        is SocketTimeoutException -> AiException(
            "AI 请求超时",
            cause = error,
            failure = AiFailure.Timeout
        )
        is IOException -> AiException(
            "无法连接 AI 服务",
            cause = error,
            failure = AiFailure.Network
        )
        else -> error
    }
}
