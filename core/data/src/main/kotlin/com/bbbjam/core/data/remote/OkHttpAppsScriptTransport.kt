package com.bbbjam.core.data.remote

import com.bbbjam.core.data.DataFailure
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [AppsScriptTransport] over OkHttp. OkHttp follows the web app's 302 to
 * `script.googleusercontent.com` by default. Every contract answer is HTTP 200, so a non-2xx status
 * is an [DataFailure.InvalidResponse]; any [IOException] (timeouts included) is [DataFailure.Offline].
 * Nothing here logs: the URL never leaves this class.
 *
 * [baseUrl] is the configured [com.bbbjam.core.data.AppsScriptEndpoint]'s URL, or null when none
 * is configured, in which case no request is made. It is a plain URL here so tests can point it at
 * a local MockWebServer, which serves `http`.
 */
internal class OkHttpAppsScriptTransport(
    private val client: OkHttpClient,
    private val baseUrl: String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppsScriptTransport {

    override suspend fun get(resource: String): TransportResult {
        val base = baseUrl ?: return TransportResult.Failed(DataFailure.NotConfigured)
        val url = base.toHttpUrl().newBuilder().addQueryParameter(RESOURCE_PARAMETER, resource).build()
        val request = Request.Builder().url(url).get().build()
        return withContext(ioDispatcher) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        TransportResult.Body(response.body.bytes().toString(Charsets.UTF_8))
                    } else {
                        TransportResult.Failed(DataFailure.InvalidResponse("HTTP ${response.code}"))
                    }
                }
            } catch (ignored: IOException) {
                TransportResult.Failed(DataFailure.Offline)
            }
        }
    }

    companion object {
        private const val RESOURCE_PARAMETER = "resource"
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val CALL_TIMEOUT_SECONDS = 45L

        /**
         * The client for the web app. Reads measured 2–5 s (`docs/technical-discovery.md`); a cold
         * start is unmeasured, and the cache means a long timeout never blocks the UI.
         */
        fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
