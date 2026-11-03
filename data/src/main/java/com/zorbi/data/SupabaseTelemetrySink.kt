package com.zorbi.data

import com.zorbi.core.GameProfile
import com.zorbi.core.PerfMode
import com.zorbi.core.TelemetrySink
import com.zorbi.core.TrackerStats
import com.zorbi.data.model.GestureEventRowDto
import com.zorbi.data.model.SessionRowDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Functional contract for telemetry consent checks.
 * Must fail closed (return false) when consent is not explicitly granted.
 */
fun interface TelemetryConsentProvider {
    fun isConsentGiven(): Boolean
}

/**
 * Provider interface for device metadata required for session telemetry (R5).
 */
interface DeviceInfoProvider {
    val deviceId: String
    val deviceModel: String?
    val androidVersion: Int?
}

/**
 * Default in-memory / platform implementation of [DeviceInfoProvider].
 */
class DefaultDeviceInfoProvider(
    override val deviceId: String = UUID.randomUUID().toString(),
    override val deviceModel: String? = "Zorbi Host",
    override val androidVersion: Int? = 34
) : DeviceInfoProvider

/**
 * Supabase implementation of [TelemetrySink] for R5 (session creation) and R6 (batched gesture events).
 *
 * Hardened Features:
 * - Exception safety (L-1): [TelemetryConsentProvider] lookup exceptions are caught and fail-closed as false.
 *   Exceptions never escape synchronous producer methods ([session], [gestureEvent]).
 * - Rate-limit boundary (L-2): Enforces sequential execution via [Mutex] and rate limit (<= 5 batches/sec)
 *   directly at the [sendEventBatch] transmission boundary. Zero overlapping requests.
 * - Rolling rate limit (M-1): Enforces at most [MAX_BATCHES_PER_SECOND] across any [rollingWindowMs] window.
 * - Bounded queue: Uses bounded buffer with capacity [queueCapacity] (default 100).
 * - Overflow policy: Drops oldest events when queue is full; producer never blocks.
 * - Zero transmission when consent disabled: Clears pending queue on revocation.
 * - Cancellation: Preserves [CancellationException] propagation in suspend functions.
 */
class SupabaseTelemetrySink(
    private val httpClient: SupabaseHttpClient,
    private val consentProvider: TelemetryConsentProvider = TelemetryConsentProvider { false },
    private val deviceInfoProvider: DeviceInfoProvider = DefaultDeviceInfoProvider(),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val batchIntervalMs: Long = DEFAULT_BATCH_INTERVAL_MS,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val json: Json = defaultTelemetryJson,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val rollingWindowMs: Long = ROLLING_WINDOW_MS,
    autoStartWorker: Boolean = true
) : TelemetrySink, Closeable {

    private val eventQueue = ArrayBlockingQueue<GestureEventRowDto>(queueCapacity)
    private val activeSessionId = AtomicReference<String?>(null)
    private var workerJob: Job? = null

    // Rate-limiting and transmission boundary synchronization (L-2, M-1)
    private val transmissionMutex = Mutex()
    private val recentBatchDispatchTimes = ArrayDeque<Long>()
    private val inFlightTransmissionsCount = AtomicInteger(0)

    val currentSessionId: String? get() = activeSessionId.get()
    val queuedEventsCount: Int get() = synchronized(eventQueue) { eventQueue.size }
    val currentInFlightTransmissions: Int get() = inFlightTransmissionsCount.get()
    val isWorkerRunning: Boolean get() = workerJob?.isActive == true

    init {
        if (autoStartWorker) {
            startWorker()
        }
    }

    /**
     * Safely checks consent, failing closed (returning false) on any exception (L-1).
     * Rethrows [CancellationException] in coroutines.
     */
    private fun isConsentSafelyGranted(): Boolean {
        return try {
            consentProvider.isConsentGiven()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts the background batch transmission worker loop.
     * Prevents duplicate worker loops if already running.
     */
    @Synchronized
    fun startWorker() {
        if (workerJob != null && workerJob?.isActive == true) return
        workerJob = coroutineScope.launch(ioDispatcher) {
            while (isActive) {
                delay(batchIntervalMs)
                try {
                    drainAndSendBatch()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Safe suppression for background dispatch loop
                }
            }
        }
    }

    /**
     * Stops the background batch transmission worker loop.
     */
    @Synchronized
    fun stopWorker() {
        workerJob?.cancel()
        workerJob = null
    }

    override fun close() {
        stopWorker()
        clearQueue()
    }

    /**
     * Synchronous [TelemetrySink.session] entrypoint.
     * Guarantees exceptions never escape to the calling engine thread (L-1).
     * Returns immediately without blocking.
     */
    override fun session(
        started: Long,
        p: GameProfile,
        stats: TrackerStats,
        durationS: Int,
        gestures: Int,
        perf: PerfMode
    ) {
        try {
            if (!httpClient.config.isConfigured || !isConsentSafelyGranted()) {
                return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }

        coroutineScope.launch(ioDispatcher) {
            try {
                createSession(started, p, stats, durationS, gestures, perf)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Handled safely without bubbling to engine
            }
        }
    }

    /**
     * R5: Suspends and executes session creation request to POST /rest/v1/sessions.
     * Extracts and validates the generated session UUID from the returned representation.
     */
    suspend fun createSession(
        started: Long,
        p: GameProfile,
        stats: TrackerStats,
        durationS: Int,
        gestures: Int,
        perf: PerfMode
    ): Result<String> = withContext(ioDispatcher) {
        if (!httpClient.config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured (offline mode)"))
        }
        if (!isConsentSafelyGranted()) {
            return@withContext Result.failure(IllegalStateException("Telemetry consent is not granted"))
        }

        val devId = try {
            deviceInfoProvider.deviceId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("Failed to obtain device ID", e))
        }

        if (!isValidUuid(devId)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid device ID: must be a valid UUID"))
        }

        val sessionDto = SessionRowDto(
            id = null,
            deviceId = devId,
            deviceModel = deviceInfoProvider.deviceModel,
            androidVersion = deviceInfoProvider.androidVersion,
            gamePackage = p.packageName,
            tracker = p.tracker.name,
            perfMode = perf.name,
            avgFps = stats.fps,
            p95LatencyMs = stats.inferenceMsP95,
            durationS = durationS,
            gesturesCount = gestures,
            isMock = false,
            createdAt = null
        )

        val url = httpClient.buildUrl("rest/v1/sessions")
            ?: return@withContext Result.failure(IllegalStateException("Malformed URL for sessions"))

        val bodyJson = json.encodeToString(SessionRowDto.serializer(), sessionDto)
        val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .header("Prefer", "return=representation")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        httpClient.execute(request) { response ->
            val responseBody = response.body?.string().orEmpty()
            if (responseBody.isBlank()) {
                throw IllegalStateException("Empty response body from /rest/v1/sessions")
            }

            val extractedId = extractIdFromRepresentation(responseBody)
                ?: throw IllegalStateException("No valid session UUID returned in representation: $responseBody")

            activeSessionId.set(extractedId)
            extractedId
        }
    }

    private fun extractIdFromRepresentation(responseBody: String): String? {
        return try {
            val element = json.parseToJsonElement(responseBody)
            val idCandidate = when (element) {
                is JsonArray -> {
                    if (element.isEmpty()) null
                    else (element[0] as? JsonObject)?.get("id")?.jsonPrimitive?.content
                }
                is JsonObject -> {
                    element["id"]?.jsonPrimitive?.content
                }
                else -> null
            }
            if (idCandidate != null && isValidUuid(idCandidate)) idCandidate else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Synchronous [TelemetrySink.gestureEvent] entrypoint.
     * Guarantees exceptions never escape to the calling engine thread (L-1).
     * Enqueues validated event into bounded buffer and returns immediately without blocking.
     */
    override fun gestureEvent(sessionId: String, type: String, magnitude: Float) {
        try {
            if (!httpClient.config.isConfigured || !isConsentSafelyGranted()) {
                return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }

        if (type !in ALLOWED_EVENT_TYPES) {
            return
        }

        val effectiveSessionId = sessionId.ifBlank { activeSessionId.get().orEmpty() }
        if (effectiveSessionId.isBlank() || !isValidUuid(effectiveSessionId)) {
            return
        }

        val eventDto = GestureEventRowDto(
            id = null,
            sessionId = effectiveSessionId,
            eventType = type,
            magnitude = magnitude,
            isMock = false,
            createdAt = null
        )

        // Bounded queue with DROP_OLDEST overflow policy: producer never blocks
        synchronized(eventQueue) {
            if (eventQueue.remainingCapacity() == 0) {
                eventQueue.poll() // Drop oldest
            }
            eventQueue.offer(eventDto)
        }
    }

    /**
     * Clears all currently queued gesture events without transmission.
     */
    fun clearQueue() {
        synchronized(eventQueue) {
            eventQueue.clear()
        }
    }

    /**
     * Drains up to [maxBatchSize] events from the buffer and transmits them via [sendEventBatch].
     * Strictly re-verifies consent prior to dequeuing and immediately before network transmission.
     */
    suspend fun drainAndSendBatch(maxBatchSize: Int = 50): Result<Unit> = withContext(ioDispatcher) {
        // 1. Consent and configuration check prior to draining
        if (!httpClient.config.isConfigured || !isConsentSafelyGranted()) {
            clearQueue()
            return@withContext Result.failure(IllegalStateException("Telemetry consent not granted"))
        }

        // 2. Atomic drain of up to maxBatchSize
        val batch = mutableListOf<GestureEventRowDto>()
        synchronized(eventQueue) {
            eventQueue.drainTo(batch, maxBatchSize)
        }

        if (batch.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        // 3. Strict consent re-check immediately before network call
        if (!isConsentSafelyGranted()) {
            clearQueue()
            return@withContext Result.failure(IllegalStateException("Telemetry consent withdrawn before transmission"))
        }

        // 4. Transmit batch through shared rate-limited boundary
        sendEventBatch(batch)
    }

    /**
     * R6: Suspends and executes batch event transmission to POST /rest/v1/gesture_events.
     * Enforces sequential execution, zero overlapping requests, and rolling 1-second rate limit
     * via [transmissionMutex] and [enforceRateLimit] (L-2, M-1).
     */
    suspend fun sendEventBatch(events: List<GestureEventRowDto>): Result<Unit> = withContext(ioDispatcher) {
        if (events.isEmpty()) {
            return@withContext Result.success(Unit)
        }
        if (!httpClient.config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured (offline mode)"))
        }
        if (!isConsentSafelyGranted()) {
            clearQueue()
            return@withContext Result.failure(IllegalStateException("Telemetry consent is not granted"))
        }

        for (event in events) {
            if (!isValidUuid(event.sessionId)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid session UUID: ${event.sessionId}"))
            }
            if (event.eventType !in ALLOWED_EVENT_TYPES) {
                return@withContext Result.failure(IllegalArgumentException("Invalid event type: ${event.eventType}"))
            }
        }

        // Shared transmission boundary: Mutex guarantees sequential execution and zero overlapping HTTP requests
        transmissionMutex.withLock {
            // Re-check consent once mutex is held
            if (!isConsentSafelyGranted()) {
                clearQueue()
                return@withContext Result.failure(IllegalStateException("Telemetry consent withdrawn before transmission"))
            }

            // Enforce rolling rate limit: <= MAX_BATCHES_PER_SECOND in rollingWindowMs
            enforceRateLimit()

            val url = httpClient.buildUrl("rest/v1/gesture_events")
                ?: return@withContext Result.failure(IllegalStateException("Malformed URL for gesture_events"))

            val bodyJson = json.encodeToString(ListSerializer(GestureEventRowDto.serializer()), events)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .header("Prefer", "return=minimal")
                .post(requestBody)
                .build()

            inFlightTransmissionsCount.incrementAndGet()
            try {
                httpClient.execute(request) {
                    Unit
                }
            } finally {
                inFlightTransmissionsCount.decrementAndGet()
                recordDispatchTime(clock())
            }
        }
    }

    /**
     * Enforces the rolling rate limit window (max 5 batches per 1 second window).
     * Suspends if the rolling quota has been exhausted or if consecutive batch spacing is violated.
     */
    private suspend fun enforceRateLimit() {
        while (true) {
            val now = clock()

            // Prune timestamps outside rolling window
            while (recentBatchDispatchTimes.isNotEmpty() && (now - recentBatchDispatchTimes.first) >= rollingWindowMs) {
                recentBatchDispatchTimes.removeFirst()
            }

            if (recentBatchDispatchTimes.size < MAX_BATCHES_PER_SECOND) {
                val lastTime = recentBatchDispatchTimes.peekLast()
                if (lastTime != null) {
                    val elapsed = now - lastTime
                    if (elapsed < batchIntervalMs) {
                        val waitMs = batchIntervalMs - elapsed
                        delay(waitMs)
                        continue
                    }
                }
                break
            }

            val oldest = recentBatchDispatchTimes.first
            val waitMs = (oldest + rollingWindowMs) - now
            if (waitMs > 0) {
                delay(waitMs)
            }
        }
    }

    private fun recordDispatchTime(dispatchTimeMs: Long) {
        recentBatchDispatchTimes.addLast(dispatchTimeMs)
        while (recentBatchDispatchTimes.size > MAX_BATCHES_PER_SECOND * 2) {
            recentBatchDispatchTimes.removeFirst()
        }
    }

    companion object {
        const val MAX_BATCHES_PER_SECOND = 5
        const val ROLLING_WINDOW_MS = 1000L
        const val DEFAULT_BATCH_INTERVAL_MS = 200L // 5 batches/sec max
        const val DEFAULT_QUEUE_CAPACITY = 100
        val ALLOWED_EVENT_TYPES: Set<String> = setOf("STEER", "SWIPE", "SLASH", "PAUSE")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val defaultTelemetryJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            isLenient = true
        }

        fun isValidUuid(str: String): Boolean {
            return try {
                UUID.fromString(str)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }
}
