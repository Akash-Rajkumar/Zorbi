package com.zorbi.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Exception representing an unsuccessful HTTP response from Supabase.
 *
 * Requirements:
 * - Contains status code and sanitized status message.
 * - Error body is bounded to [MAX_ERROR_BODY_LENGTH] to prevent memory issues.
 * - Sanitizes content to ensure credentials and tokens are never leaked in error messages.
 */
class SupabaseHttpException(
    val statusCode: Int,
    val statusMessage: String,
    val errorBody: String? = null
) : IOException(
    buildString {
        append("HTTP ").append(statusCode).append(" ").append(statusMessage)
        if (!errorBody.isNullOrBlank()) {
            append(": ").append(errorBody)
        }
    }
) {
    companion object {
        const val MAX_ERROR_BODY_LENGTH = 500

        fun fromResponse(response: Response, config: SupabaseConfig): SupabaseHttpException {
            val rawBody = try {
                response.body?.string()
            } catch (_: Throwable) {
                null
            }

            val sanitized = sanitizeErrorBody(rawBody, config)
            val bounded = if (sanitized != null && sanitized.length > MAX_ERROR_BODY_LENGTH) {
                sanitized.substring(0, MAX_ERROR_BODY_LENGTH) + "..."
            } else {
                sanitized
            }

            return SupabaseHttpException(
                statusCode = response.code,
                statusMessage = response.message.ifBlank { "HTTP Error" },
                errorBody = bounded
            )
        }

        private fun sanitizeErrorBody(body: String?, config: SupabaseConfig): String? {
            if (body == null) return null
            var result = body
            if (config.anonKey.isNotBlank()) {
                result = result.replace(config.anonKey, "[PROTECTED]")
            }
            return result
        }
    }
}

/**
 * Central HTTP client for interacting with Supabase REST endpoints.
 *
 * Requirements:
 * - Uses pinned OkHttp 4.12.0.
 * - 10-second connect and read timeouts.
 * - Injects apikey and Authorization Bearer headers.
 * - Injects Accept and Content-Type: application/json headers.
 * - Verifies response.isSuccessful before parser invocation.
 * - Returns structured [SupabaseHttpException] on HTTP errors.
 * - Enforces safe resource cleanup via Response.use {}.
 * - Guards against executing network calls on the main thread via injectable checker.
 * - Sanitizes logs and errors to prevent secret leakages.
 */
class SupabaseHttpClient(
    val config: SupabaseConfig,
    baseClient: OkHttpClient = defaultOkHttpClient(),
    private val mainThreadChecker: () -> Boolean = { isAndroidMainThread() }
) {
    val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(SupabaseAuthInterceptor(config))
        .build()

    /**
     * Constructs a validated [HttpUrl] targeting this Supabase instance.
     * Returns null if unconfigured or if the URL format is invalid.
     */
    fun buildUrl(path: String, queryParams: Map<String, String> = emptyMap()): HttpUrl? {
        if (!config.isConfigured) return null
        val trimmedBase = config.normalizedUrl
        if (trimmedBase.isBlank()) return null

        val cleanPath = path.trim().trimStart('/')
        val combined = "$trimmedBase/$cleanPath"
        val parsedUrl = combined.toHttpUrlOrNull() ?: return null

        if (queryParams.isEmpty()) {
            return parsedUrl
        }

        val builder = parsedUrl.newBuilder()
        for ((key, value) in queryParams) {
            builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    /**
     * Executes an HTTP request synchronously on a background thread.
     * Checks [response.isSuccessful], parses payload via [parser], and always closes the response body.
     * Returns [Result.failure] with [SupabaseHttpException] on HTTP 4xx/5xx status codes.
     */
    fun <T> execute(request: Request, parser: (Response) -> T): Result<T> {
        if (mainThreadChecker()) {
            return Result.failure(IllegalStateException("Network request must not be executed on the main UI thread."))
        }
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(SupabaseHttpException.fromResponse(response, config))
                } else {
                    Result.success(parser(response))
                }
            }
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: ThreadDeath) {
            throw e
        } catch (e: LinkageError) {
            throw e
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 10L

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder().build()
        }

        /**
         * Detects Android main UI Looper in Android runtime environments.
         */
        fun isAndroidMainThread(): Boolean {
            return try {
                val looperClass = Class.forName("android.os.Looper")
                val myLooperMethod = looperClass.getMethod("myLooper")
                val getMainLooperMethod = looperClass.getMethod("getMainLooper")
                val myLooper = myLooperMethod.invoke(null)
                val mainLooper = getMainLooperMethod.invoke(null)
                myLooper != null && myLooper == mainLooper
            } catch (_: Throwable) {
                // Not in Android runtime (standard JVM)
                false
            }
        }
    }
}

/**
 * OkHttp interceptor that injects Supabase authentication and JSON content headers.
 */
class SupabaseAuthInterceptor(
    private val config: SupabaseConfig
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        if (config.isConfigured) {
            builder.header("apikey", config.anonKey)
            builder.header("Authorization", "Bearer ${config.anonKey}")
        }

        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }

        if (original.body != null && original.header("Content-Type") == null) {
            builder.header("Content-Type", "application/json")
        }

        return chain.proceed(builder.build())
    }
}
