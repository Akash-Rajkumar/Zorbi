package com.zorbi.engine

import android.content.Context
import com.zorbi.core.GameProfile
import com.zorbi.core.ProfileRepository
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Production implementation of [ProfileRepository] per Task P2-6 and Gate T-P2-6.
 *
 * Implements a resilient 2-tier storage system:
 * 1. Bundled default assets ([AssetProvider]) for subway, fruit, and hill climb games.
 * 2. User/community local files ([storageDir]) with atomic write persistence.
 *
 * Enforces strict package name sanitization, path traversal prevention, schemaVersion 1
 * validation, and safe offline fallback for corrupt or unavailable files.
 */
class RealProfileRepository(
    private val assetProvider: AssetProvider,
    private val storageDir: File,
    private val remoteSource: RemoteProfileSource? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }
) : ProfileRepository {

    /**
     * Primary Android constructor using [Context].
     */
    constructor(
        context: Context,
        remoteSource: RemoteProfileSource? = null
    ) : this(
        assetProvider = AndroidAssetProvider(context),
        storageDir = File(context.filesDir, "profiles"),
        remoteSource = remoteSource
    )

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    override suspend fun get(packageName: String): GameProfile? {
        if (!ProfileValidator.isValidPackageName(packageName)) return null

        // 1. Try local storage file first (user/imported overrides)
        val localFile = getSafeLocalFile(packageName)
        if (localFile != null && localFile.exists() && localFile.isFile) {
            val localProfile = parseProfileFromFile(localFile)
            if (localProfile != null) {
                return localProfile
            }
            // Local file was corrupt or had invalid schema: fall through to bundled asset fallback
        }

        // 2. Try bundled asset profile fallback
        val assetStream = assetProvider.open("profiles/$packageName.json")
        if (assetStream != null) {
            return parseProfileFromStream(assetStream)
        }

        return null
    }

    override suspend fun list(): List<GameProfile> {
        val resultMap = mutableMapOf<String, GameProfile>()

        // 1. Scan bundled asset profiles
        val assetNames = assetProvider.list("profiles")
        for (assetName in assetNames) {
            if (assetName.endsWith(".json")) {
                val stream = assetProvider.open("profiles/$assetName")
                if (stream != null) {
                    parseProfileFromStream(stream)?.let { profile ->
                        resultMap[profile.packageName] = profile
                    }
                }
            }
        }

        // 2. Scan local storage files (user/imported files override bundled defaults)
        if (storageDir.exists() && storageDir.isDirectory) {
            val files = storageDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()
            for (file in files) {
                parseProfileFromFile(file)?.let { profile ->
                    resultMap[profile.packageName] = profile
                }
            }
        }

        return resultMap.values.toList()
    }

    override suspend fun saveLocal(p: GameProfile) {
        if (!ProfileValidator.isValid(p)) {
            throw IllegalArgumentException("GameProfile failed validation: ${p.packageName}")
        }

        val targetFile = getSafeLocalFile(p.packageName)
            ?: throw IllegalArgumentException("Invalid package name for profile storage: ${p.packageName}")

        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        // Atomic write via temporary file
        val tempFile = File.createTempFile("profile_", ".tmp", storageDir)
        try {
            val serialized = json.encodeToString(p)
            tempFile.writeText(serialized, Charsets.UTF_8)

            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Throwable) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Throwable) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    override suspend fun importFromCloud(id: String): GameProfile? {
        val source = remoteSource ?: return null
        val fetched = try {
            source.fetchProfile(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }

        if (fetched != null && ProfileValidator.isValid(fetched)) {
            try {
                saveLocal(fetched)
                return fetched
            } catch (_: Throwable) {
                return null
            }
        }

        return null
    }

    private fun getSafeLocalFile(packageName: String): File? {
        if (!ProfileValidator.isValidPackageName(packageName)) return null
        val targetFile = File(storageDir, "$packageName.json")
        val canonicalStorage = storageDir.canonicalFile
        val canonicalTarget = targetFile.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalStorage.path)) {
            return null // Path traversal attempted
        }
        return targetFile
    }

    private fun parseProfileFromFile(file: File): GameProfile? {
        return try {
            val text = file.readText(Charsets.UTF_8)
            val parsed = json.decodeFromString<GameProfile>(text)
            if (ProfileValidator.isValid(parsed)) parsed else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseProfileFromStream(stream: InputStream): GameProfile? {
        return try {
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = json.decodeFromString<GameProfile>(text)
            if (ProfileValidator.isValid(parsed)) parsed else null
        } catch (_: Throwable) {
            null
        }
    }
}

