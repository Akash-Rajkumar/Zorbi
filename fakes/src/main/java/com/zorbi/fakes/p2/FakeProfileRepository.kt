package com.zorbi.fakes.p2

import com.zorbi.core.GameProfile
import com.zorbi.core.ProfileRepository

class FakeProfileRepository : ProfileRepository {
    private val profiles = mutableMapOf<String, GameProfile>()

    override suspend fun get(packageName: String): GameProfile? = profiles[packageName]

    override suspend fun list(): List<GameProfile> = profiles.values.toList()

    override suspend fun importFromCloud(id: String): GameProfile? = null

    override suspend fun saveLocal(p: GameProfile) {
        profiles[p.packageName] = p
    }
}

