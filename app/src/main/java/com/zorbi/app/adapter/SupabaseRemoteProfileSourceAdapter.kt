package com.zorbi.app.adapter

import com.zorbi.core.GameProfile
import com.zorbi.data.RemoteProfileSource as DataRemoteProfileSource
import com.zorbi.engine.RemoteProfileSource as EngineRemoteProfileSource
import kotlinx.coroutines.CancellationException

/**
 * Adapter bridging Person 4's data-layer [DataRemoteProfileSource] to Person 2's
 * engine-layer [EngineRemoteProfileSource] per Gate I-2 Phase 4.
 */
class SupabaseRemoteProfileSourceAdapter(
    private val dataRemoteSource: DataRemoteProfileSource
) : EngineRemoteProfileSource {

    override suspend fun fetchProfile(id: String): GameProfile? {
        if (id.isBlank()) return null
        return try {
            val result = dataRemoteSource.getProfileById(id)
            result.getOrNull()?.profileJson
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
}

