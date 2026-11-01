package com.zorbi.app.adapter

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.TrackerId
import com.zorbi.data.RemoteProfileSource as DataRemoteProfileSource
import com.zorbi.data.model.GameProfileRowDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class SupabaseRemoteProfileSourceAdapterTest {

    private val testProfile = GameProfile(
        schemaVersion = 1,
        packageName = "com.kiloo.subwaysurf",
        displayName = "Subway Surfers",
        orientation = ProfileOrientation.PORTRAIT,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.LANE_SWIPE
    )

    private val testRowDto = GameProfileRowDto(
        id = "550e8400-e29b-41d4-a716-446655440000",
        packageName = testProfile.packageName,
        displayName = testProfile.displayName,
        tracker = "HAND_SINGLE",
        mapping = "LANE_SWIPE",
        profileJson = testProfile,
        schemaVersion = 1,
        author = "zorbi-team",
        approved = true,
        downloads = 42,
        isMock = false
    )

    private class FakeDataRemoteProfileSource(
        var getProfileByIdHandler: (suspend (String) -> Result<GameProfileRowDto?>)? = null
    ) : DataRemoteProfileSource {
        var lastRequestedId: String? = null

        override suspend fun listCatalog(): Result<List<GameProfileRowDto>> = Result.success(emptyList())

        override suspend fun getProfileByPackage(packageName: String): Result<GameProfileRowDto?> = Result.success(null)

        override suspend fun getProfileById(profileId: String): Result<GameProfileRowDto?> {
            lastRequestedId = profileId
            return getProfileByIdHandler?.invoke(profileId) ?: Result.success(null)
        }

        override suspend fun submitProfile(profile: GameProfile, author: String, id: String?): Result<Unit> = Result.success(Unit)

        override suspend fun incrementDownloads(profileId: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun successfulProfileRetrieval_returnsProfile() = runTest {
        val fakeSource = FakeDataRemoteProfileSource { id ->
            if (id == testRowDto.id) Result.success(testRowDto) else Result.success(null)
        }
        val adapter = SupabaseRemoteProfileSourceAdapter(fakeSource)

        val profile = adapter.fetchProfile(testRowDto.id!!)

        assertThat(profile).isEqualTo(testProfile)
        assertThat(fakeSource.lastRequestedId).isEqualTo(testRowDto.id)
    }

    @Test
    fun missingProfile_returnsNull() = runTest {
        val fakeSource = FakeDataRemoteProfileSource { Result.success(null) }
        val adapter = SupabaseRemoteProfileSourceAdapter(fakeSource)

        val profile = adapter.fetchProfile("non-existent-id")

        assertThat(profile).isNull()
    }

    @Test
    fun failureResult_returnsNull() = runTest {
        val fakeSource = FakeDataRemoteProfileSource { Result.failure(IOException("Network unreachable")) }
        val adapter = SupabaseRemoteProfileSourceAdapter(fakeSource)

        val profile = adapter.fetchProfile("any-id")

        assertThat(profile).isNull()
    }

    @Test
    fun blankOrEmptyId_returnsNullWithoutCallingSource() = runTest {
        val fakeSource = FakeDataRemoteProfileSource { Result.success(testRowDto) }
        val adapter = SupabaseRemoteProfileSourceAdapter(fakeSource)

        assertThat(adapter.fetchProfile("")).isNull()
        assertThat(adapter.fetchProfile("   ")).isNull()
        assertThat(fakeSource.lastRequestedId).isNull()
    }

    @Test
    fun cancellationException_isRethrown() = runTest {
        val fakeSource = FakeDataRemoteProfileSource { throw CancellationException("Job was cancelled") }
        val adapter = SupabaseRemoteProfileSourceAdapter(fakeSource)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                adapter.fetchProfile("some-id")
            }
        }
    }
}

