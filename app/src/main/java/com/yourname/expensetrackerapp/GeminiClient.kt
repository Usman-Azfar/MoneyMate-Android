package com.yourname.expensetrackerapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The one place that talks to the Gemini REST API (`models/{model}:generateContent`), shared by
 * Smart Voice Input ([GeminiTransactionParser]) and Ask MoneyMate AI ([AiAdvisor]). Callers build
 * the JSON request body; this handles the HTTP call on a background thread, a hard overall
 * deadline, cancellation, one retry on transient overload (429/5xx), and delivering exactly one
 * result on the main thread.
 *
 * Plain HttpURLConnection + callbacks, matching this app's listener style with no networking or
 * AI SDK dependency. The key comes from local.properties via BuildConfig — see app/build.gradle.kts.
 */
object GeminiClient {

    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
    private const val RETRY_BACKOFF_MILLIS = 1_200L
    /** Don't bother retrying if less than this would be left for the second attempt. */
    private const val MIN_RETRY_WINDOW_MILLIS = 3_000L

    enum class Failure { OFFLINE, TIMEOUT, RATE_LIMITED, SERVER, REQUEST_REJECTED, NETWORK, EMPTY_RESPONSE }

    sealed class Result {
        /** [body] is the raw generateContent response JSON — see [extractText]. */
        data class Success(val body: String) : Result()
        data class Error(val failure: Failure) : Result()
    }

    /** Returned by [generate]; [cancel] suppresses the callback and aborts the request, e.g.
     *  when the screen that asked is closed. */
    class Call internal constructor(
        private val delivered: AtomicBoolean,
        private val connection: AtomicReference<HttpURLConnection?>
    ) {
        fun cancel() {
            if (delivered.compareAndSet(false, true)) {
                executor.execute { connection.get()?.disconnect() }
            }
        }
    }

    // Lazy so request/response helpers elsewhere stay unit-testable on the plain JVM, where
    // touching Looper would throw. A cached pool (not a single thread) so a slow Ask AI request
    // never queues a Smart Voice one behind it.
    private val executor by lazy { Executors.newCachedThreadPool() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * POSTs [requestBody] and calls [onResult] exactly once on the main thread, no later than
     * [timeoutMillis] from now. With [retryTransient], a 429/5xx answer is retried once after a
     * short backoff if enough of the deadline remains.
     */
    fun generate(
        requestBody: String,
        timeoutMillis: Long,
        retryTransient: Boolean,
        onResult: (Result) -> Unit
    ): Call {
        val delivered = AtomicBoolean(false)
        val connection = AtomicReference<HttpURLConnection?>(null)
        val deadline = System.currentTimeMillis() + timeoutMillis

        fun deliver(result: Result) {
            if (delivered.compareAndSet(false, true)) mainHandler.post { onResult(result) }
        }

        val timeout = Runnable {
            deliver(Result.Error(Failure.TIMEOUT))
            executor.execute { connection.get()?.disconnect() } // frees the worker thread early
        }
        mainHandler.postDelayed(timeout, timeoutMillis)

        executor.execute {
            var result: Result = Result.Error(Failure.NETWORK)
            var attempt = 0
            while (!delivered.get()) {
                attempt++
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) { result = Result.Error(Failure.TIMEOUT); break }

                val (code, body) = try {
                    postOnce(requestBody, remaining.toInt(), connection)
                } catch (e: SocketTimeoutException) {
                    result = Result.Error(Failure.TIMEOUT); break
                } catch (e: Exception) {
                    result = Result.Error(Failure.NETWORK); break
                }

                result = when {
                    code == HttpURLConnection.HTTP_OK && body != null -> Result.Success(body)
                    code == 429 -> Result.Error(Failure.RATE_LIMITED)
                    code >= 500 -> Result.Error(Failure.SERVER)
                    else -> Result.Error(Failure.REQUEST_REJECTED)
                }
                val transient = code == 429 || code >= 500
                val canRetry = retryTransient && transient && attempt == 1 &&
                    deadline - System.currentTimeMillis() > RETRY_BACKOFF_MILLIS + MIN_RETRY_WINDOW_MILLIS
                if (!canRetry) break
                Thread.sleep(RETRY_BACKOFF_MILLIS)
            }
            mainHandler.removeCallbacks(timeout)
            deliver(result)
        }

        return Call(delivered, connection)
    }

    private fun postOnce(
        requestBody: String,
        timeoutMillis: Int,
        connectionRef: AtomicReference<HttpURLConnection?>
    ): Pair<Int, String?> {
        val conn = URL(String.format(ENDPOINT, BuildConfig.GEMINI_MODEL)).openConnection() as HttpURLConnection
        connectionRef.set(conn)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMillis
            conn.readTimeout = timeoutMillis
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val body = if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                null
            }
            return code to body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Pulls the model's answer text out of a generateContent response (candidates[0].content
     * .parts[].text, skipping "thought" parts). Returns null if the prompt or answer was blocked,
     * or the answer is empty. A MAX_TOKENS cut-off is returned only when [allowTruncated] — fine for
     * a prose answer, but a truncated JSON object would be unusable.
     */
    fun extractText(responseJson: String, allowTruncated: Boolean): TextResult? {
        return try {
            val root = JsonParser.parseString(responseJson).asJsonObject
            if (root.getAsJsonObject("promptFeedback")?.get("blockReason") != null) return null
            val candidate = root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject ?: return null
            val finishReason = candidate.get("finishReason")?.asString
            val truncated = finishReason == "MAX_TOKENS"
            if (finishReason != null && finishReason != "STOP" && !(truncated && allowTruncated)) return null

            val text = candidate.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.map { it.asJsonObject }
                ?.filterNot { it.get("thought")?.asBoolean == true }
                ?.mapNotNull { it.get("text")?.asString }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            TextResult(text, truncated)
        } catch (e: Exception) {
            null
        }
    }

    data class TextResult(val text: String, val truncated: Boolean)
}
