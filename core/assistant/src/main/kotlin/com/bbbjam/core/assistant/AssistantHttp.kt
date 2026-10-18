package com.bbbjam.core.assistant

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Builds the executor that talks to the gateway with [create]'s key; tests point it at a local server. */
internal fun interface AssistantExecutorFactory {
    fun create(apiKey: String): PromptExecutor
}

/**
 * The HTTP stack of the assistant: Koog's OpenAI client over Ktor, over the OkHttp engine, over an
 * [OkHttpClient] built here. The factory is passed to Koog explicitly, so its `ServiceLoader` lookup is
 * never used. Nothing here logs the key or a request body.
 */
internal object AssistantHttp {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val CALL_TIMEOUT_SECONDS = 60L
    private const val MILLIS_PER_SECOND = 1000L

    /** A reasoning model can think for a while: long read and call timeouts (the UI shows a loading state). */
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** The factory for the real gateway, or for [baseUrl] over [okHttp]. */
    fun executorFactory(
        baseUrl: String = AssistantModel.BASE_URL,
        okHttp: OkHttpClient = okHttpClient(),
    ): AssistantExecutorFactory = AssistantExecutorFactory { apiKey ->
        val ktor = HttpClient(OkHttp) { engine { preconfigured = okHttp } }
        val settings = OpenAIClientSettings(
            baseUrl = baseUrl,
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = CALL_TIMEOUT_SECONDS * MILLIS_PER_SECOND,
                connectTimeoutMillis = CONNECT_TIMEOUT_SECONDS * MILLIS_PER_SECOND,
                socketTimeoutMillis = READ_TIMEOUT_SECONDS * MILLIS_PER_SECOND,
            ),
            chatCompletionsPath = AssistantModel.CHAT_COMPLETIONS_PATH,
        )
        val client = OpenAILLMClient(
            apiKey = apiKey,
            settings = settings,
            httpClientFactory = KtorKoogHttpClient.Factory(baseClient = ktor),
        )
        MultiLLMPromptExecutor(mapOf(LLMProvider.OpenAI to client))
    }
}
