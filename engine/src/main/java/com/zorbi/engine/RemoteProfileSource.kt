package com.zorbi.engine

import com.zorbi.core.GameProfile

/**
 * Pluggable abstraction for fetching community game profiles from remote backend sources (e.g., Supabase PostgREST).
 *
 * Implemented by :data (Person 4) per Master Prompt Section 5.1.
 */
fun interface RemoteProfileSource {
    suspend fun fetchProfile(id: String): GameProfile?
}

