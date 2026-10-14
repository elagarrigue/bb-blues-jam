package com.bbbjam.core.data.assistant

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The status read of the assistant's gateway: one plain `GET <base>/v1/models` with the key as a Bearer
 * token over [client] with a short call timeout. It lists models and consumes no tokens, so it is a read
 * (D-24). The key is sent only as that header and never logged; a missing key makes no request. Cancelling
 * the caller cancels the call.
 */
internal class AssistantStatusProbe(
    private val baseUrl: String = AssistantModel.BASE_URL,
    client: OkHttpClient = AssistantHttp.okHttpClient(),
) {
    private val client: OkHttpClient = client.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun check(key: String): AssistantStatus {
        if (key.isEmpty()) return AssistantStatus.NotConfigured
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/${AssistantModel.MODELS_PATH}")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(failureOf(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response.use { statusOf(it.code) })
                    }
                },
            )
        }
    }

    private fun statusOf(code: Int): AssistantStatus = when (code) {
        in HTTP_OK_RANGE -> AssistantStatus.Available
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> AssistantStatus.Unauthorized
        else -> AssistantStatus.ServiceDown
    }

    // A timeout is a gateway that does not answer; any other connection error is no network.
    private fun failureOf(error: IOException): AssistantStatus =
        if (error is InterruptedIOException) AssistantStatus.ServiceDown else AssistantStatus.Offline

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 10L
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        val HTTP_OK_RANGE = 200..299
    }
}
