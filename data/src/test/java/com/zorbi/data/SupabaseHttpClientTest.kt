package com.zorbi.data

import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class SupabaseHttpClientTest {

    private val validConfig = SupabaseConfig.fromValues(
        url = "https://mock.supabase.co",
        anonKey = "test-anon-key-12345"
    )

    @Test
    fun testTimeoutsConfiguredToTenSeconds() {
        val client = SupabaseHttpClient(validConfig)

        assertThat(client.client.connectTimeoutMillis).isEqualTo(10_000)
        assertThat(client.client.readTimeoutMillis).isEqualTo(10_000)
    }

    @Test
    fun testHeaderInjectionWithValidConfig() {
        var interceptedApikey: String? = null
        var interceptedAuth: String? = null
        var interceptedAccept: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                interceptedApikey = request.header("apikey")
                interceptedAuth = request.header("Authorization")
                interceptedAccept = request.header("Accept")

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val url = client.buildUrl("rest/v1/game_profiles")!!
        val request = Request.Builder().url(url).build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(200)
        assertThat(interceptedApikey).isEqualTo("test-anon-key-12345")
        assertThat(interceptedAuth).isEqualTo("Bearer test-anon-key-12345")
        assertThat(interceptedAccept).isEqualTo("application/json")
    }

    @Test
    fun testContentTypeInjectedWhenBodyPresent() {
        var interceptedContentType: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                interceptedContentType = request.header("Content-Type")

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val url = client.buildUrl("rest/v1/sessions")!!
        val body = "{}".toRequestBody(null)
        val request = Request.Builder().url(url).post(body).build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(201)
        assertThat(interceptedContentType).isEqualTo("application/json")
    }

    @Test
    fun testUnconfiguredClientDoesNotInjectAuthHeaders() {
        var interceptedApikey: String? = null
        var interceptedAuth: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                interceptedApikey = request.header("apikey")
                interceptedAuth = request.header("Authorization")

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val unconfiguredClient = SupabaseHttpClient(SupabaseConfig.UNCONFIGURED, mockBase)
        val request = Request.Builder().url("https://mock.supabase.co/rest/v1/test").build()

        val result = unconfiguredClient.execute(request) { response -> response.code }

        assertThat(result.isSuccess).isTrue()
        assertThat(interceptedApikey).isNull()
        assertThat(interceptedAuth).isNull()
    }

    // -------------------------------------------------------------------------
    // Finding M-1 Tests: HTTP Status Error Handling
    // -------------------------------------------------------------------------

    private fun createErrorClient(statusCode: Int, statusMessage: String, responseBody: String): SupabaseHttpClient {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(statusMessage)
                    .body(responseBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return SupabaseHttpClient(validConfig, mockBase)
    }

    @Test
    fun testHttp400ReturnsResultFailureWithSupabaseHttpException() {
        val client = createErrorClient(400, "Bad Request", "{\"message\":\"invalid input syntax\"}")
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        val result = client.execute(request) { response -> response.body?.string() }

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(SupabaseHttpException::class.java)
        val httpEx = exception as SupabaseHttpException
        assertThat(httpEx.statusCode).isEqualTo(400)
        assertThat(httpEx.statusMessage).isEqualTo("Bad Request")
        assertThat(httpEx.errorBody).contains("invalid input syntax")
    }

    @Test
    fun testHttp401ReturnsResultFailure() {
        val client = createErrorClient(401, "Unauthorized", "{\"message\":\"JWT expired\"}")
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        val result = client.execute(request) { response -> response.body?.string() }

        assertThat(result.isFailure).isTrue()
        val httpEx = result.exceptionOrNull() as SupabaseHttpException
        assertThat(httpEx.statusCode).isEqualTo(401)
        assertThat(httpEx.statusMessage).isEqualTo("Unauthorized")
    }

    @Test
    fun testHttp403ReturnsResultFailure() {
        val client = createErrorClient(403, "Forbidden", "{\"message\":\"violates row-level security policy\"}")
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        val result = client.execute(request) { response -> response.body?.string() }

        assertThat(result.isFailure).isTrue()
        val httpEx = result.exceptionOrNull() as SupabaseHttpException
        assertThat(httpEx.statusCode).isEqualTo(403)
        assertThat(httpEx.errorBody).contains("violates row-level security")
    }

    @Test
    fun testHttp500ReturnsResultFailure() {
        val client = createErrorClient(500, "Internal Server Error", "Internal database error")
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        val result = client.execute(request) { response -> response.body?.string() }

        assertThat(result.isFailure).isTrue()
        val httpEx = result.exceptionOrNull() as SupabaseHttpException
        assertThat(httpEx.statusCode).isEqualTo(500)
    }

    @Test
    fun testHttp204EmptyBodyHandledSuccessfully() {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val url = client.buildUrl("rest/v1/rpc/increment_profile_downloads")!!
        val request = Request.Builder().url(url).post("{}".toRequestBody(null)).build()

        val result = client.execute(request) { response ->
            response.code == 204
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun testParserExceptionReturnsResultFailure() {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{invalid json}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        val result = client.execute(request) {
            throw IllegalArgumentException("JSON parse failed")
        }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test(expected = java.util.concurrent.CancellationException::class)
    fun testCancellationExceptionPropagatesDirectlyWithoutResultFailureWrapping() {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        client.execute(request) {
            throw java.util.concurrent.CancellationException("Coroutine job was cancelled")
        }
    }

    @Test(expected = java.util.concurrent.CancellationException::class)
    fun testCancellationExceptionFromInterceptorPropagatesDirectly() {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw java.util.concurrent.CancellationException("Network call cancelled")
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        client.execute(request) { response -> response.code }
    }

    @Test
    fun testErrorSanitizationDoesNotExposeKeyInException() {
        val secretKey = validConfig.anonKey
        val errorWithKey = "Error occurred using token $secretKey on backend"
        val client = createErrorClient(400, "Bad Request", errorWithKey)
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isFailure).isTrue()
        val httpEx = result.exceptionOrNull() as SupabaseHttpException
        assertThat(httpEx.message).doesNotContain(secretKey)
        assertThat(httpEx.message).contains("[PROTECTED]")
    }

    // -------------------------------------------------------------------------
    // Finding M-2 Tests: Main Thread Guard
    // -------------------------------------------------------------------------

    @Test
    fun testMainThreadInvocationIsRejected() {
        val client = SupabaseHttpClient(
            config = validConfig,
            mainThreadChecker = { true } // Deterministically simulate main thread
        )
        val request = Request.Builder().url("https://mock.supabase.co/rest/v1/test").build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception!!.message).contains("must not be executed on the main UI thread")
    }

    @Test
    fun testBackgroundThreadInvocationIsPermitted() {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = SupabaseHttpClient(
            config = validConfig,
            baseClient = mockBase,
            mainThreadChecker = { false } // Deterministically simulate background thread
        )
        val request = Request.Builder().url("https://mock.supabase.co/rest/v1/test").build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(200)
    }

    // -------------------------------------------------------------------------
    // Finding L-2 Tests: Timeouts, Network Exceptions & URL Encoding
    // -------------------------------------------------------------------------

    @Test
    fun testSocketTimeoutExceptionReturnsResultFailure() {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw SocketTimeoutException("Read timed out after 10000ms")
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val request = Request.Builder().url("https://mock.supabase.co/rest/v1/test").build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SocketTimeoutException::class.java)
    }

    @Test
    fun testConnectExceptionReturnsResultFailure() {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw ConnectException("Failed to connect to host")
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val request = Request.Builder().url("https://mock.supabase.co/rest/v1/test").build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ConnectException::class.java)
    }

    @Test
    fun testHtmlErrorResponseBoundedCorrectly() {
        val hugeHtml = "<html><body>" + "x".repeat(2000) + "</body></html>"
        val client = createErrorClient(502, "Bad Gateway", hugeHtml)
        val request = Request.Builder().url("https://mock.supabase.co/rest/v1/test").build()

        val result = client.execute(request) { response -> response.code }

        assertThat(result.isFailure).isTrue()
        val httpEx = result.exceptionOrNull() as SupabaseHttpException
        assertThat(httpEx.errorBody!!.length).isAtMost(SupabaseHttpException.MAX_ERROR_BODY_LENGTH + 3)
        assertThat(httpEx.errorBody).endsWith("...")
    }

    @Test
    fun testBuildUrlEncodesSpacesAmpersandsAndOperators() {
        val client = SupabaseHttpClient(validConfig)

        val queryParams = mapOf(
            "package_name" to "eq.com.test.game & app",
            "select" to "*,targets->x",
            "display_name" to "eq.Subway Surfers"
        )
        val url = client.buildUrl("rest/v1/game_profiles", queryParams)

        assertThat(url).isNotNull()
        // Query parameters should be properly encoded in URL string
        val urlString = url.toString()
        assertThat(urlString).contains("package_name=eq.com.test.game%20%26%20app")
        assertThat(urlString).contains("display_name=eq.Subway%20Surfers")
        assertThat(url.queryParameter("package_name")).isEqualTo("eq.com.test.game & app")
        assertThat(url.queryParameter("display_name")).isEqualTo("eq.Subway Surfers")
    }

    @Test
    fun testResponseBodyIsClosedAfterExecution() {
        val closed = AtomicBoolean(false)

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val responseBody = object : okhttp3.ResponseBody() {
                    override fun contentLength(): Long = 2
                    override fun contentType() = "application/json".toMediaType()
                    override fun source() = okio.Buffer().writeUtf8("{}")
                    override fun close() {
                        closed.set(true)
                        super.close()
                    }
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBody)
                    .build()
            })
            .build()

        val client = SupabaseHttpClient(validConfig, mockBase)
        val url = client.buildUrl("rest/v1/test")!!
        val request = Request.Builder().url(url).build()

        client.execute(request) { response ->
            response.body?.string()
        }

        assertThat(closed.get()).isTrue()
    }
}
