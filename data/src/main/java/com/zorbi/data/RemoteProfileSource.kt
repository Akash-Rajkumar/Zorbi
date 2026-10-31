package com.zorbi.data

import com.zorbi.core.GameProfile
import com.zorbi.data.model.GameProfileRowDto
import com.zorbi.data.model.ProfileDownloadRpcDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Remote profile source contract providing access to Supabase GameProfile endpoints (R1–R4).
 */
interface RemoteProfileSource {
    /**
     * R1: Fetches community catalog of approved game profiles, ordered by downloads descending.
     */
    suspend fun listCatalog(): Result<List<GameProfileRowDto>>

    /**
     * R2: Fetches approved game profile for an installed package.
     * Returns null if no approved profile is found.
     */
    suspend fun getProfileByPackage(packageName: String): Result<GameProfileRowDto?>

    /**
     * Fetches an approved game profile by UUID ID (for cloud import).
     */
    suspend fun getProfileById(profileId: String): Result<GameProfileRowDto?>

    /**
     * R3: Submits a new community game profile for review.
     * Strictly forces `approved = false` and `is_mock = false` with `Prefer: return=minimal`.
     */
    suspend fun submitProfile(
        profile: GameProfile,
        author: String = "zorbi-team",
        id: String? = null
    ): Result<Unit>

    /**
     * R4: Invokes the `increment_profile_downloads` RPC to increment the download counter
     * for an approved profile via SECURITY DEFINER stored procedure.
     */
    suspend fun incrementDownloads(profileId: String): Result<Unit>
}

/**
 * Supabase implementation of [RemoteProfileSource] utilizing [SupabaseHttpClient].
 *
 * Requirements:
 * - Offloads all network and deserialization tasks to [ioDispatcher] (off the main thread).
 * - Enforces R1, R2, R3, R4 endpoint structures and parameter requirements.
 * - Guarantees `approved = false` and `is_mock = false` on submissions.
 * - Handles offline / unconfigured mode safely by returning [Result.failure] without throwing.
 */
class SupabaseRemoteProfileSource(
    private val httpClient: SupabaseHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = defaultJson
) : RemoteProfileSource {

    override suspend fun listCatalog(): Result<List<GameProfileRowDto>> = withContext(ioDispatcher) {
        if (!httpClient.config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase client is not configured (offline mode)"))
        }

        val url = httpClient.buildUrl(
            path = "rest/v1/game_profiles",
            queryParams = mapOf(
                "approved" to "eq.true",
                "select" to "*",
                "order" to "downloads.desc"
            )
        ) ?: return@withContext Result.failure(IllegalStateException("Malformed URL for game_profiles catalog"))

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.execute(request) { response ->
            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString(ListSerializer(GameProfileRowDto.serializer()), bodyString)
            }
        }
    }

    override suspend fun getProfileByPackage(packageName: String): Result<GameProfileRowDto?> = withContext(ioDispatcher) {
        if (!httpClient.config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase client is not configured (offline mode)"))
        }
        if (packageName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Package name must not be blank"))
        }

        val url = httpClient.buildUrl(
            path = "rest/v1/game_profiles",
            queryParams = mapOf(
                "package_name" to "eq.$packageName",
                "approved" to "eq.true",
                "select" to "*"
            )
        ) ?: return@withContext Result.failure(IllegalStateException("Malformed URL for game_profiles lookup"))

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.execute(request) { response ->
            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) {
                null
            } else {
                val list = json.decodeFromString(ListSerializer(GameProfileRowDto.serializer()), bodyString)
                list.firstOrNull()
            }
        }
    }

    override suspend fun getProfileById(profileId: String): Result<GameProfileRowDto?> = withContext(ioDispatcher) {
        if (!httpClient.config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase client is not configured (offline mode)"))
        }
        if (profileId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Profile ID must not be blank"))
        }

        val url = httpClient.buildUrl(
            path = "rest/v1/game_profiles",
            queryParams = mapOf(
                "id" to "eq.$profileId",
                "approved" to "eq.true",
                "select" to "*"
            )
        ) ?: return@withContext Result.failure(IllegalStateException("Malformed URL for game_profiles ID lookup"))

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.execute(request) { response ->
            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) {
                null
            } else {
                val list = json.decodeFromString(ListSerializer(GameProfileRowDto.serializer()), bodyString)
                list.firstOrNull()
            }
        }
    }

    override suspend fun submitProfile(
        profile: GameProfile,
        author: String,
        id: String?
    ): Result<Unit> = withContext(ioDispatcher) {
        if (!httpClient.config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase client is not configured (offline mode)"))
        }

        val url = httpClient.buildUrl("rest/v1/game_profiles")
            ?: return@withContext Result.failure(IllegalStateException("Malformed URL for game_profiles submission"))

        val submissionDto = GameProfileRowDto(
            id = id,
            packageName = profile.packageName,
            displayName = profile.displayName,
            tracker = profile.tracker.name,
            mapping = profile.mapping.name,
            profileJson = profile,
            schemaVersion = profile.schemaVersion,
            author = author,
            approved = false, // Strictly forced false per R3 and RLS policy
            downloads = 0,
            isMock = false    // Strictly forced false
        )

        val jsonPayload = json.encodeToString(GameProfileRowDto.serializer(), submissionDto)
        val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .header("Prefer", "return=minimal")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.execute(request) {
            Unit
        }
    }

    override suspend fun incrementDownloads(profileId: String): Result<Unit> = withContext(ioDispatcher) {
        if (!httpClient.config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase client is not configured (offline mode)"))
        }
        if (profileId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Profile ID must not be blank"))
        }

        val url = httpClient.buildUrl("rest/v1/rpc/increment_profile_downloads")
            ?: return@withContext Result.failure(IllegalStateException("Malformed URL for increment_profile_downloads RPC"))

        val rpcDto = ProfileDownloadRpcDto(profileId = profileId)
        val jsonPayload = json.encodeToString(ProfileDownloadRpcDto.serializer(), rpcDto)
        val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.execute(request) {
            Unit
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        val defaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}

