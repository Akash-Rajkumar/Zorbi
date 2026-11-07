package com.zorbi.data

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.PerfMode
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class SupabaseTelemetrySinkTest {

    private val validConfig = SupabaseConfig.fromValues(
        url = "https://mock.supabase.co",
        anonKey = "test-anon-key-12345"
    )

    private val sampleGameProfile = GameProfile(
        schemaVersion = 1,
        packageName = "com.kiloo.subwaysurf",
        displayName = "Subway Surfers",
        orientation = ProfileOrientation.PORTRAIT,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.LANE_SWIPE,
        targets = mapOf("center" to TargetPoint(0.5f, 0.5f)),
        params = ProfileParams()
    )

    private val sampleTrackerStats = TrackerStats(
        fps = 29.8f,
        inferenceMsP50 = 18.2f,
        inferenceMsP95 = 28.5f,
        dropped = 2
    )

    private val validSessionUuid = "7f9c2d1b-8e34-4b91-a123-456789abcdef"
    private val validDeviceUuid = "11111111-2222-3333-4444-555555555555"

    private val fixedDeviceInfoProvider = object : DeviceInfoProvider {
        override val deviceId: String = validDeviceUuid
        override val deviceModel: String = "Test Model Pixel 8"
        override val androidVersion: Int = 34
    }

    // -------------------------------------------------------------------------
    // R5: Session Creation Tests
    // -------------------------------------------------------------------------

    @Test
    fun testR5CreateSessionSuccessWithCorrectEndpointAndHeaders() = runBlocking {
        var capturedPath: String? = null
        var capturedMethod: String? = null
        var capturedPrefer: String? = null
        var capturedAccept: String? = null
        var capturedApiKey: String? = null
        var capturedAuth: String? = null

        val responseJson = """
            [
                {
                    "id": "$validSessionUuid",
                    "device_id": "$validDeviceUuid",
                    "game_package": "com.kiloo.subwaysurf",
                    "tracker": "HAND_SINGLE",
                    "perf_mode": "BALANCED",
                    "avg_fps": 29.8,
                    "p95_latency_ms": 28.5,
                    "duration_s": 45,
                    "gestures_count": 18,
                    "is_mock": false,
                    "created_at": "2026-11-01T00:00:00Z"
                }
            ]
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedPath = req.url.encodedPath
                capturedMethod = req.method
                capturedPrefer = req.header("Prefer")
                capturedAccept = req.header("Accept")
                capturedApiKey = req.header("apikey")
                capturedAuth = req.header("Authorization")

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(
            started = 1000L,
            p = sampleGameProfile,
            stats = sampleTrackerStats,
            durationS = 45,
            gestures = 18,
            perf = PerfMode.BALANCED
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(validSessionUuid)
        assertThat(sink.currentSessionId).isEqualTo(validSessionUuid)

        assertThat(capturedMethod).isEqualTo("POST")
        assertThat(capturedPath).isEqualTo("/rest/v1/sessions")
        assertThat(capturedPrefer).isEqualTo("return=representation")
        assertThat(capturedAccept).isEqualTo("application/json")
        assertThat(capturedApiKey).isEqualTo("test-anon-key-12345")
        assertThat(capturedAuth).isEqualTo("Bearer test-anon-key-12345")
    }

    @Test
    fun testR5CreateSessionSuccessWithExactJsonFields() = runBlocking {
        var capturedBody: String? = null

        val responseJson = """[{"id":"$validSessionUuid"}]"""
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.createSession(
            started = 1000L,
            p = sampleGameProfile,
            stats = sampleTrackerStats,
            durationS = 45,
            gestures = 18,
            perf = PerfMode.BALANCED
        )

        assertThat(capturedBody).isNotNull()
        val jsonElement = Json.parseToJsonElement(capturedBody!!).jsonObject
        assertThat(jsonElement["device_id"]?.jsonPrimitive?.content).isEqualTo(validDeviceUuid)
        assertThat(jsonElement["device_model"]?.jsonPrimitive?.content).isEqualTo("Test Model Pixel 8")
        assertThat(jsonElement["android_version"]?.jsonPrimitive?.content).isEqualTo("34")
        assertThat(jsonElement["game_package"]?.jsonPrimitive?.content).isEqualTo("com.kiloo.subwaysurf")
        assertThat(jsonElement["tracker"]?.jsonPrimitive?.content).isEqualTo("HAND_SINGLE")
        assertThat(jsonElement["perf_mode"]?.jsonPrimitive?.content).isEqualTo("BALANCED")
        assertThat(jsonElement["avg_fps"]?.jsonPrimitive?.content).isEqualTo("29.8")
        assertThat(jsonElement["p95_latency_ms"]?.jsonPrimitive?.content).isEqualTo("28.5")
        assertThat(jsonElement["duration_s"]?.jsonPrimitive?.content).isEqualTo("45")
        assertThat(jsonElement["gestures_count"]?.jsonPrimitive?.content).isEqualTo("18")
        assertThat(jsonElement["is_mock"]?.jsonPrimitive?.content).isEqualTo("false")

        // Crucial invariant: id and created_at must be omitted so PostgreSQL uses DEFAULTs
        assertThat(jsonElement.containsKey("id")).isFalse()
        assertThat(jsonElement.containsKey("created_at")).isFalse()
    }

    @Test
    fun testR5CreateSessionSuccessWithJsonObjectRepresentation() = runBlocking {
        val responseJson = """{"id":"$validSessionUuid","device_id":"$validDeviceUuid"}"""
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(validSessionUuid)
        assertThat(sink.currentSessionId).isEqualTo(validSessionUuid)
    }

    @Test
    fun testR5CreateSessionFailsOnMalformedJsonResponse() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("{ not valid json".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun testR5CreateSessionFailsOnEmptyResponseBody() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Empty response body")
    }

    @Test
    fun testR5CreateSessionFailsOnMissingIdInRepresentation() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("""[{"device_id":"$validDeviceUuid"}]""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No valid session UUID")
    }

    @Test
    fun testR5CreateSessionFailsOnInvalidUuidInRepresentation() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("""[{"id":"not-a-valid-uuid"}]""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No valid session UUID")
    }

    @Test
    fun testR5CreateSessionFailsOnHttpError() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("""{"message":"Database down"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SupabaseHttpException::class.java)
        val ex = result.exceptionOrNull() as SupabaseHttpException
        assertThat(ex.statusCode).isEqualTo(500)
    }

    @Test
    fun testR5CreateSessionFailsWhenUnconfiguredWithoutNetworkCall() = runBlocking {
        var requestsMade = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val unconfiguredClient = SupabaseHttpClient(SupabaseConfig.UNCONFIGURED, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = unconfiguredClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("offline mode")
        assertThat(requestsMade).isEqualTo(0)
    }

    @Test
    fun testR5CreateSessionFailsWhenConsentDisabledWithoutNetworkCall() = runBlocking {
        var requestsMade = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { false }, // Consent disabled
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("consent")
        assertThat(requestsMade).isEqualTo(0)
    }

    @Test
    fun testR5CreateSessionFailsWhenDeviceIdIsInvalidUuid() = runBlocking {
        var requestsMade = 0
        val invalidDevInfo = object : DeviceInfoProvider {
            override val deviceId: String = "invalid-not-a-uuid"
            override val deviceModel: String? = null
            override val androidVersion: Int? = null
        }

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = invalidDevInfo,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(requestsMade).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // R6: Batched Gesture Events Tests
    // -------------------------------------------------------------------------

    @Test
    fun testR6GestureBatchTransmissionSendsCorrectEndpointAndHeaders() = runBlocking {
        var capturedPath: String? = null
        var capturedMethod: String? = null
        var capturedPrefer: String? = null
        var capturedBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedPath = req.url.encodedPath
                capturedMethod = req.method
                capturedPrefer = req.header("Prefer")

                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.gestureEvent(validSessionUuid, "STEER", 0.75f)
        sink.gestureEvent(validSessionUuid, "SWIPE", 1.5f)

        assertThat(sink.queuedEventsCount).isEqualTo(2)

        val batchResult = sink.drainAndSendBatch()
        assertThat(batchResult.isSuccess).isTrue()
        assertThat(sink.queuedEventsCount).isEqualTo(0)

        assertThat(capturedMethod).isEqualTo("POST")
        assertThat(capturedPath).isEqualTo("/rest/v1/gesture_events")
        assertThat(capturedPrefer).isEqualTo("return=minimal")
        assertThat(capturedBody).isNotNull()
    }

    @Test
    fun testR6GestureBatchTransmissionSendsValidJsonArray() = runBlocking {
        var capturedBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.gestureEvent(validSessionUuid, "STEER", -0.4f)
        sink.gestureEvent(validSessionUuid, "SLASH", 0.88f)

        sink.drainAndSendBatch()

        val jsonArray = Json.parseToJsonElement(capturedBody!!).jsonArray
        assertThat(jsonArray).hasSize(2)

        val item1 = jsonArray[0].jsonObject
        assertThat(item1["session_id"]?.jsonPrimitive?.content).isEqualTo(validSessionUuid)
        assertThat(item1["event_type"]?.jsonPrimitive?.content).isEqualTo("STEER")
        assertThat(item1["magnitude"]?.jsonPrimitive?.content).isEqualTo("-0.4")
        assertThat(item1["is_mock"]?.jsonPrimitive?.content).isEqualTo("false")
        assertThat(item1.containsKey("id")).isFalse() // Generated identity omitted

        val item2 = jsonArray[1].jsonObject
        assertThat(item2["session_id"]?.jsonPrimitive?.content).isEqualTo(validSessionUuid)
        assertThat(item2["event_type"]?.jsonPrimitive?.content).isEqualTo("SLASH")
        assertThat(item2["magnitude"]?.jsonPrimitive?.content).isEqualTo("0.88")
        assertThat(item2["is_mock"]?.jsonPrimitive?.content).isEqualTo("false")
    }

    @Test
    fun testR6GestureEventAssociatesWithExplicitSessionId() = runBlocking {
        var capturedBody: String? = null
        val explicitUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(201).message("Created").body("".toResponseBody(null)).build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.gestureEvent(explicitUuid, "PAUSE", 0f)
        sink.drainAndSendBatch()

        val array = Json.parseToJsonElement(capturedBody!!).jsonArray
        assertThat(array[0].jsonObject["session_id"]?.jsonPrimitive?.content).isEqualTo(explicitUuid)
    }

    @Test
    fun testR6GestureEventAssociatesWithRetainedSessionIdWhenBlankPassed() = runBlocking {
        var capturedBody: String? = null

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                val resp = if (req.url.encodedPath.contains("sessions")) {
                    """[{"id":"$validSessionUuid"}]"""
                } else {
                    ""
                }

                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(201).message("Created").body(resp.toResponseBody("application/json".toMediaType())).build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        // R5 creates session and retains UUID
        sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.BALANCED)
        assertThat(sink.currentSessionId).isEqualTo(validSessionUuid)

        // R6 with blank sessionId falls back to retained active UUID
        sink.gestureEvent("", "SWIPE", 1.8f)
        sink.drainAndSendBatch()

        val array = Json.parseToJsonElement(capturedBody!!).jsonArray
        assertThat(array[0].jsonObject["session_id"]?.jsonPrimitive?.content).isEqualTo(validSessionUuid)
    }

    @Test
    fun testR6GestureEventRejectsWhenNoSessionIdAvailable() = runBlocking {
        val httpClient = SupabaseHttpClient(validConfig, OkHttpClient())
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        // No session created, pass blank session id
        sink.gestureEvent("", "SWIPE", 1.0f)
        assertThat(sink.queuedEventsCount).isEqualTo(0)
    }

    @Test
    fun testR6GestureEventValidatesAllAllowedTypes() = runBlocking {
        val httpClient = SupabaseHttpClient(validConfig, OkHttpClient())
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.gestureEvent(validSessionUuid, "STEER", 0.1f)
        sink.gestureEvent(validSessionUuid, "SWIPE", 0.2f)
        sink.gestureEvent(validSessionUuid, "SLASH", 0.3f)
        sink.gestureEvent(validSessionUuid, "PAUSE", 0.4f)

        assertThat(sink.queuedEventsCount).isEqualTo(4)
    }

    @Test
    fun testR6GestureEventRejectsInvalidEventTypes() = runBlocking {
        val httpClient = SupabaseHttpClient(validConfig, OkHttpClient())
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.gestureEvent(validSessionUuid, "TAP", 1.0f)
        sink.gestureEvent(validSessionUuid, "PINCH", 1.0f)
        sink.gestureEvent(validSessionUuid, "steer", 1.0f) // Must be uppercase
        sink.gestureEvent(validSessionUuid, "UNKNOWN", 1.0f)

        assertThat(sink.queuedEventsCount).isEqualTo(0)
    }

    @Test
    fun testR6EmptyBatchBehaviorDoesNotSendHttpRequest() = runBlocking {
        var requestsMade = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.drainAndSendBatch()
        assertThat(result.isSuccess).isTrue()
        assertThat(requestsMade).isEqualTo(0)
    }

    @Test
    fun testR6BoundedQueueDropsOldestOnOverflowWithoutBlocking() = runBlocking {
        var capturedBody: String? = null
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val buffer = okio.Buffer()
                chain.request().body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(201).message("Created").body("".toResponseBody(null)).build()
            })
            .build()

        val sink = SupabaseTelemetrySink(
            httpClient = SupabaseHttpClient(validConfig, mockClient),
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            queueCapacity = 3,
            autoStartWorker = false
        )

        sink.gestureEvent(validSessionUuid, "STEER", 1.0f)
        sink.gestureEvent(validSessionUuid, "STEER", 2.0f)
        sink.gestureEvent(validSessionUuid, "SWIPE", 3.0f)
        sink.gestureEvent(validSessionUuid, "SLASH", 4.0f)
        sink.gestureEvent(validSessionUuid, "PAUSE", 5.0f)

        assertThat(sink.queuedEventsCount).isEqualTo(3)

        sink.drainAndSendBatch()

        val array = Json.parseToJsonElement(capturedBody!!).jsonArray
        assertThat(array).hasSize(3)
        assertThat(array[0].jsonObject["magnitude"]?.jsonPrimitive?.content).isEqualTo("3.0")
        assertThat(array[1].jsonObject["magnitude"]?.jsonPrimitive?.content).isEqualTo("4.0")
        assertThat(array[2].jsonObject["magnitude"]?.jsonPrimitive?.content).isEqualTo("5.0")
    }

    @Test
    fun testR6ConsentDisabledZeroTransmission() = runBlocking {
        var requestsMade = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { false }, // Disabled consent
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.gestureEvent(validSessionUuid, "STEER", 1.0f)
        assertThat(sink.queuedEventsCount).isEqualTo(0)

        val result = sink.drainAndSendBatch()
        assertThat(result.isFailure).isTrue()
        assertThat(requestsMade).isEqualTo(0)
    }

    @Test
    fun testR6ConsentWithdrawnAfterEnqueueDiscardsQueuedEvents() = runBlocking {
        var requestsMade = 0
        var consent = true

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { consent },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        // Consent is true during enqueue
        sink.gestureEvent(validSessionUuid, "STEER", 1.0f)
        sink.gestureEvent(validSessionUuid, "SWIPE", 2.0f)
        assertThat(sink.queuedEventsCount).isEqualTo(2)

        // User revokes consent before scheduled batch transmits
        consent = false

        val result = sink.drainAndSendBatch()

        // Verify zero transmission and queue purged
        assertThat(result.isFailure).isTrue()
        assertThat(sink.queuedEventsCount).isEqualTo(0)
        assertThat(requestsMade).isEqualTo(0)
    }

    @Test
    fun testR6GestureBatchHttpFailureReturnsSafeFailureWithoutCrash() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("""{"error":"Service Unavailable"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        sink.gestureEvent(validSessionUuid, "STEER", 0.5f)
        val result = sink.drainAndSendBatch()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SupabaseHttpException::class.java)
    }

    // -------------------------------------------------------------------------
    // Finding L-1: Consent Provider Exception Safety Tests
    // -------------------------------------------------------------------------

    @Test
    fun testL1ConsentProviderThrowsRuntimeExceptionDuringSessionReturnsSafelyWithoutNetworkCall() {
        var requestsMade = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val crashingConsent = TelemetryConsentProvider {
            throw RuntimeException("Consent database query failed")
        }

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = crashingConsent,
            deviceInfoProvider = fixedDeviceInfoProvider,
            autoStartWorker = false
        )

        // Producer call must not throw or escape
        sink.session(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)

        assertThat(requestsMade).isEqualTo(0)
    }

    @Test
    fun testL1ConsentProviderThrowsRuntimeExceptionDuringGestureEventReturnsSafelyWithoutEnqueue() {
        val crashingConsent = TelemetryConsentProvider {
            throw RuntimeException("Security permission manager crashed")
        }

        val httpClient = SupabaseHttpClient(validConfig, OkHttpClient())
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = crashingConsent,
            deviceInfoProvider = fixedDeviceInfoProvider,
            autoStartWorker = false
        )

        // Producer call must not throw and must fail closed without enqueuing
        sink.gestureEvent(validSessionUuid, "STEER", 0.5f)

        assertThat(sink.queuedEventsCount).isEqualTo(0)
    }

    @Test
    fun testL1ConsentProviderThrowsDuringWorkerDrainClearsQueueAndSendsZeroRequests() = runBlocking {
        var requestsMade = 0
        var shouldCrash = false

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val dynamicConsent = TelemetryConsentProvider {
            if (shouldCrash) throw IllegalStateException("Consent provider service dead")
            true
        }

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = dynamicConsent,
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        // Enqueue while healthy
        sink.gestureEvent(validSessionUuid, "STEER", 0.8f)
        sink.gestureEvent(validSessionUuid, "SWIPE", 1.2f)
        assertThat(sink.queuedEventsCount).isEqualTo(2)

        // Provider crashes when drain/send is initiated
        shouldCrash = true

        val result = sink.drainAndSendBatch()

        assertThat(result.isFailure).isTrue()
        assertThat(sink.queuedEventsCount).isEqualTo(0)
        assertThat(requestsMade).isEqualTo(0)
    }

    @Test
    fun testL1DeviceInfoProviderThrowsDuringSessionCreationReturnsFailureWithoutCrash() = runBlocking {
        var requestsMade = 0
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestsMade++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            })
            .build()

        val crashingDevInfo = object : DeviceInfoProvider {
            override val deviceId: String get() = throw RuntimeException("Keystore unavailable")
            override val deviceModel: String? = null
            override val androidVersion: Int? = null
        }

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = crashingDevInfo,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        val result = sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(requestsMade).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // Finding L-2: Transmission Boundary Rate Limit & Mutex Tests
    // -------------------------------------------------------------------------

    @Test
    fun testL2TransmissionBoundaryMutexPreventsConcurrentOverlappingRequests() = runBlocking {
        val activeConcurrentCount = AtomicInteger(0)
        val maxConcurrentInFlight = AtomicInteger(0)
        val requestsCompleted = AtomicInteger(0)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val current = activeConcurrentCount.incrementAndGet()
                maxConcurrentInFlight.updateAndGet { maxOf(it, current) }
                Thread.sleep(40) // Simulate in-flight I/O
                activeConcurrentCount.decrementAndGet()
                requestsCompleted.incrementAndGet()

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody(null))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            batchIntervalMs = 10L, // Fast interval for testing
            rollingWindowMs = 2000L,
            autoStartWorker = false
        )

        // Launch 4 concurrent callers hitting the transmission boundary
        val jobs = (1..4).map { i ->
            async(Dispatchers.IO) {
                sink.sendEventBatch(
                    listOf(
                        com.zorbi.data.model.GestureEventRowDto(
                            sessionId = validSessionUuid,
                            eventType = "STEER",
                            magnitude = i.toFloat()
                        )
                    )
                )
            }
        }

        jobs.awaitAll()

        assertThat(requestsCompleted.get()).isEqualTo(4)
        // Mutex guarantees zero overlapping requests
        assertThat(maxConcurrentInFlight.get()).isEqualTo(1)
        assertThat(sink.currentInFlightTransmissions).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // Finding M-1: Behavioral Rolling Rate Limit Tests
    // -------------------------------------------------------------------------

    @Test
    fun testM1BehavioralRateLimitingEnforcesMaxFiveBatchesInRollingOneSecondWindow() = runBlocking {
        val dispatchTimestamps = CopyOnWriteArrayList<Long>()
        val maxInFlight = AtomicInteger(0)
        val activeInFlight = AtomicInteger(0)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val active = activeInFlight.incrementAndGet()
                maxInFlight.updateAndGet { maxOf(it, active) }
                dispatchTimestamps.add(System.currentTimeMillis())
                Thread.sleep(15) // Simulate 15ms socket I/O
                activeInFlight.decrementAndGet()

                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(201).message("Created").body("".toResponseBody(null)).build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        // Default: 5 batches/sec max, batchIntervalMs = 40L, rollingWindowMs = 300L for swift deterministic test
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            batchIntervalMs = 40L,
            rollingWindowMs = 300L,
            autoStartWorker = false
        )

        // Transmit 7 batches in rapid succession
        for (i in 1..7) {
            val result = sink.sendEventBatch(
                listOf(
                    com.zorbi.data.model.GestureEventRowDto(
                        sessionId = validSessionUuid,
                        eventType = "STEER",
                        magnitude = i.toFloat()
                    )
                )
            )
            assertThat(result.isSuccess).isTrue()
        }

        assertThat(dispatchTimestamps).hasSize(7)
        assertThat(maxInFlight.get()).isEqualTo(1)

        // Verify that in any rolling window of rollingWindowMs (300ms), at most 5 batches were dispatched
        for (i in dispatchTimestamps.indices) {
            val windowStart = dispatchTimestamps[i]
            val countInWindow = dispatchTimestamps.count { it >= windowStart && it < windowStart + 300L }
            assertThat(countInWindow).isAtMost(SupabaseTelemetrySink.MAX_BATCHES_PER_SECOND)
        }
    }

    @Test
    fun testM1SlowHttpResponseMaintainsSequentialNonOverlappingExecution() = runBlocking {
        val activeConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val current = activeConcurrent.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, current) }
                Thread.sleep(100) // Slow 100ms response
                activeConcurrent.decrementAndGet()

                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(201).message("Created").body("".toResponseBody(null)).build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            batchIntervalMs = 10L,
            autoStartWorker = false
        )

        sink.sendEventBatch(listOf(com.zorbi.data.model.GestureEventRowDto(sessionId = validSessionUuid, eventType = "SWIPE", magnitude = 1.0f)))
        sink.sendEventBatch(listOf(com.zorbi.data.model.GestureEventRowDto(sessionId = validSessionUuid, eventType = "SLASH", magnitude = 2.0f)))

        assertThat(maxConcurrent.get()).isEqualTo(1)
        assertThat(sink.currentInFlightTransmissions).isEqualTo(0)
    }

    @Test
    fun testM1WorkerStartupDoesNotCreateDuplicateTransmissionLoops() {
        val httpClient = SupabaseHttpClient(validConfig, OkHttpClient())
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            autoStartWorker = false
        )

        assertThat(sink.isWorkerRunning).isFalse()

        // Multiple calls to startWorker must be idempotent
        sink.startWorker()
        sink.startWorker()
        sink.startWorker()

        assertThat(sink.isWorkerRunning).isTrue()

        sink.stopWorker()
        assertThat(sink.isWorkerRunning).isFalse()
    }

    // -------------------------------------------------------------------------
    // Thread Safety & Concurrency Tests
    // -------------------------------------------------------------------------

    @Test
    fun testSynchronousSessionReturnsImmediatelyWithoutBlocking() {
        val slowClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Thread.sleep(1500) // Simulate slow 1.5s network
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(201).message("Created").body("""[{"id":"$validSessionUuid"}]""".toResponseBody(null)).build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, slowClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            autoStartWorker = false
        )

        val start = System.currentTimeMillis()
        // Synchronous call must dispatch asynchronously and return immediately
        sink.session(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isLessThan(200L)
    }

    @Test
    fun testSynchronousGestureEventReturnsImmediatelyWithoutBlocking() {
        val httpClient = SupabaseHttpClient(validConfig, OkHttpClient())
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            autoStartWorker = false
        )

        val start = System.currentTimeMillis()
        sink.gestureEvent(validSessionUuid, "STEER", 0.5f)
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isLessThan(50L)
    }

    @Test
    fun testProducerDoesNotBlockWhenQueueIsFull() {
        val httpClient = SupabaseHttpClient(validConfig, OkHttpClient())
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            queueCapacity = 2,
            autoStartWorker = false
        )

        val start = System.currentTimeMillis()
        for (i in 1..25) {
            sink.gestureEvent(validSessionUuid, "STEER", i.toFloat())
        }
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isLessThan(100L)
        assertThat(sink.queuedEventsCount).isEqualTo(2)
    }

    @Test
    fun testCancellationExceptionPropagatesDirectlyInSuspendFunctions() {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { _ ->
                throw CancellationException("Simulated coroutine cancellation")
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockClient)
        val sink = SupabaseTelemetrySink(
            httpClient = httpClient,
            consentProvider = { true },
            deviceInfoProvider = fixedDeviceInfoProvider,
            ioDispatcher = Dispatchers.Unconfined,
            autoStartWorker = false
        )

        assertThrows(CancellationException::class.java) {
            runBlocking {
                sink.createSession(1000L, sampleGameProfile, sampleTrackerStats, 10, 5, PerfMode.PERFORMANCE)
            }
        }
    }
}
