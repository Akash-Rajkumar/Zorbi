package com.zorbi.data

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException

class RemoteProfileSourceTest {

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
        targets = mapOf(
            "left" to TargetPoint(0.20f, 0.80f),
            "right" to TargetPoint(0.80f, 0.80f),
            "center" to TargetPoint(0.50f, 0.50f)
        ),
        params = ProfileParams()
    )

    private val sampleRowJson = """
        [
            {
                "id": "10000000-0000-0000-0000-000000000001",
                "package_name": "com.kiloo.subwaysurf",
                "display_name": "Subway Surfers",
                "tracker": "HAND_SINGLE",
                "mapping": "LANE_SWIPE",
                "profile_json": {
                    "schemaVersion": 1,
                    "packageName": "com.kiloo.subwaysurf",
                    "displayName": "Subway Surfers",
                    "orientation": "PORTRAIT",
                    "tracker": "HAND_SINGLE",
                    "mapping": "LANE_SWIPE",
                    "targets": {
                        "left": { "x": 0.20, "y": 0.80 },
                        "right": { "x": 0.80, "y": 0.80 },
                        "center": { "x": 0.50, "y": 0.50 }
                    },
                    "params": {
                        "deadzoneDeg": 8.0,
                        "cooldownMs": 350,
                        "swipeMinSpeed": 1.2,
                        "slashMinLength": 0.15
                    }
                },
                "schema_version": 1,
                "author": "zorbi-team",
                "approved": true,
                "downloads": 142,
                "is_mock": false,
                "created_at": "2026-10-31T00:00:00Z"
            }
        ]
    """.trimIndent()

    // -------------------------------------------------------------------------
    // R1: Community Profile Catalog Tests
    // -------------------------------------------------------------------------

    @Test
    fun testR1ListCatalogQueriesApprovedProfilesOrderedByDownloads() = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url
                capturedPath = url.encodedPath
                capturedQuery = url.encodedQuery

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleRowJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.listCatalog()

        assertThat(result.isSuccess).isTrue()
        val list = result.getOrNull()!!
        assertThat(list).hasSize(1)
        assertThat(list[0].packageName).isEqualTo("com.kiloo.subwaysurf")
        assertThat(list[0].approved).isTrue()
        assertThat(list[0].downloads).isEqualTo(142)

        assertThat(capturedPath).isEqualTo("/rest/v1/game_profiles")
        assertThat(capturedQuery).contains("approved=eq.true")
        assertThat(capturedQuery).contains("order=downloads.desc")
        assertThat(capturedQuery).contains("select=*")
    }

    @Test
    fun testR1ListCatalogReturnsEmptyListWhenNoProfilesExist() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.listCatalog()

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEmpty()
    }

    @Test
    fun testR1ListCatalogReturnsFailureOnHttpError() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("Database error".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.listCatalog()

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as SupabaseHttpException
        assertThat(exception.statusCode).isEqualTo(500)
    }

    // -------------------------------------------------------------------------
    // R2: Installed Game Lookup Tests
    // -------------------------------------------------------------------------

    @Test
    fun testR2GetProfileByPackageFiltersApprovedProfile() = runBlocking {
        var capturedQuery: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                capturedQuery = chain.request().url.query

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleRowJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileByPackage("com.kiloo.subwaysurf")

        assertThat(result.isSuccess).isTrue()
        val profileDto = result.getOrNull()
        assertThat(profileDto).isNotNull()
        assertThat(profileDto!!.packageName).isEqualTo("com.kiloo.subwaysurf")
        assertThat(profileDto.approved).isTrue()

        assertThat(capturedQuery).contains("package_name=eq.com.kiloo.subwaysurf")
        assertThat(capturedQuery).contains("approved=eq.true")
    }

    @Test
    fun testR2GetProfileByPackageEncodesSpecialCharacters() = runBlocking {
        var capturedEncodedQuery: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                capturedEncodedQuery = chain.request().url.encodedQuery

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileByPackage("com.test.special & game")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(capturedEncodedQuery).contains("package_name=eq.com.test.special%20%26%20game")
    }

    @Test
    fun testR2GetProfileByPackageReturnsNullWhenNotFound() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileByPackage("com.unknown.game")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun testR2GetProfileByPackageFailsOnBlankInput() = runBlocking {
        val httpClient = SupabaseHttpClient(validConfig)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileByPackage("   ")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    // -------------------------------------------------------------------------
    // Cloud Profile Import: getProfileById Tests (Finding L-1)
    // -------------------------------------------------------------------------

    @Test
    fun testCloudImportGetProfileByIdSendsCorrectQueryAndDeserializes() = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null
        val targetUuid = "10000000-0000-0000-0000-000000000001"

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url
                capturedPath = url.encodedPath
                capturedQuery = url.encodedQuery

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleRowJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileById(targetUuid)

        assertThat(result.isSuccess).isTrue()
        val profileDto = result.getOrNull()
        assertThat(profileDto).isNotNull()
        assertThat(profileDto!!.id).isEqualTo(targetUuid)
        assertThat(profileDto.packageName).isEqualTo("com.kiloo.subwaysurf")
        assertThat(profileDto.approved).isTrue()

        // 1. Path must be /rest/v1/game_profiles
        assertThat(capturedPath).isEqualTo("/rest/v1/game_profiles")
        // 2. Query must include id=eq.<UUID>
        assertThat(capturedQuery).contains("id=eq.$targetUuid")
        // 3. Query must include approved=eq.true
        assertThat(capturedQuery).contains("approved=eq.true")
        // 4. Query must include select=*
        assertThat(capturedQuery).contains("select=*")
    }

    @Test
    fun testCloudImportGetProfileByIdReturnsNullWhenNotFound() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileById("non-existent-uuid")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun testCloudImportGetProfileByIdFailsOnBlankInput() = runBlocking {
        val httpClient = SupabaseHttpClient(validConfig)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileById("   ")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun testCloudImportGetProfileByIdFailsOnHttpError() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("Database error".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.getProfileById("10000000-0000-0000-0000-000000000001")

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as SupabaseHttpException
        assertThat(exception.statusCode).isEqualTo(500)
    }

    // -------------------------------------------------------------------------
    // R3: Profile Submission Tests
    // -------------------------------------------------------------------------

    @Test
    fun testR3SubmitProfileForcesApprovedFalseAndReturnMinimal() = runBlocking {
        var capturedBody: String? = null
        var capturedPrefer: String? = null
        var capturedContentType: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedPrefer = req.header("Prefer")
                capturedContentType = req.header("Content-Type")

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

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.submitProfile(sampleGameProfile, author = "test-author")

        assertThat(result.isSuccess).isTrue()
        assertThat(capturedPrefer).isEqualTo("return=minimal")
        assertThat(capturedContentType).isEqualTo("application/json")

        // Assert approved is forced false and is_mock is false
        assertThat(capturedBody).contains("\"approved\":false")
        assertThat(capturedBody).contains("\"is_mock\":false")
        assertThat(capturedBody).contains("\"author\":\"test-author\"")
        assertThat(capturedBody).contains("\"package_name\":\"com.kiloo.subwaysurf\"")
    }

    @Test
    fun testR3SubmitProfileRejectsOnHttp403Forbidden() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .body("{\"message\":\"new row violates row-level security policy\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.submitProfile(sampleGameProfile)

        assertThat(result.isFailure).isTrue()
        val httpEx = result.exceptionOrNull() as SupabaseHttpException
        assertThat(httpEx.statusCode).isEqualTo(403)
        assertThat(httpEx.errorBody).contains("violates row-level security")
    }

    @Test
    fun testR3SubmitProfileFailsOnNetworkError() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw IOException("Connection dropped")
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.submitProfile(sampleGameProfile)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }

    // -------------------------------------------------------------------------
    // R4: Download Counter RPC Tests
    // -------------------------------------------------------------------------

    @Test
    fun testR4IncrementDownloadsSendsProfileIdPayload() = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null

        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedPath = req.url.encodedPath
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val targetUuid = "10000000-0000-0000-0000-000000000001"
        val result = source.incrementDownloads(targetUuid)

        assertThat(result.isSuccess).isTrue()
        assertThat(capturedPath).isEqualTo("/rest/v1/rpc/increment_profile_downloads")
        assertThat(capturedBody).contains("\"profile_id\":\"$targetUuid\"")
    }

    @Test
    fun testR4IncrementDownloadsHandlesHttp204NoContent() = runBlocking {
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

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.incrementDownloads("10000000-0000-0000-0000-000000000001")

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun testR4IncrementDownloadsFailsOnHttp404OrNetworkError() = runBlocking {
        val mockBase = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("Profile not found".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val httpClient = SupabaseHttpClient(validConfig, mockBase)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val result = source.incrementDownloads("non-existent-uuid")

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as SupabaseHttpException
        assertThat(exception.statusCode).isEqualTo(404)
    }

    // -------------------------------------------------------------------------
    // Unconfigured / Offline Mode Tests
    // -------------------------------------------------------------------------

    @Test
    fun testUnconfiguredClientReturnsFailureWithoutNetworkExecution() = runBlocking {
        val httpClient = SupabaseHttpClient(SupabaseConfig.UNCONFIGURED)
        val source = SupabaseRemoteProfileSource(httpClient, Dispatchers.Unconfined)

        val r1 = source.listCatalog()
        assertThat(r1.isFailure).isTrue()
        assertThat(r1.exceptionOrNull()!!.message).contains("not configured")

        val r2 = source.getProfileByPackage("com.kiloo.subwaysurf")
        assertThat(r2.isFailure).isTrue()
        assertThat(r2.exceptionOrNull()!!.message).contains("not configured")

        val rById = source.getProfileById("10000000-0000-0000-0000-000000000001")
        assertThat(rById.isFailure).isTrue()
        assertThat(rById.exceptionOrNull()!!.message).contains("not configured")

        val r3 = source.submitProfile(sampleGameProfile)
        assertThat(r3.isFailure).isTrue()
        assertThat(r3.exceptionOrNull()!!.message).contains("not configured")

        val r4 = source.incrementDownloads("10000000-0000-0000-0000-000000000001")
        assertThat(r4.isFailure).isTrue()
        assertThat(r4.exceptionOrNull()!!.message).contains("not configured")
    }
}

