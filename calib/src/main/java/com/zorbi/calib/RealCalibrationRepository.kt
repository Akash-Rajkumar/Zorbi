package com.zorbi.calib

import android.content.Context
import com.zorbi.calib.internal.JsonCalibrationSerializer
import com.zorbi.core.Calibration
import com.zorbi.core.CalibrationRepository
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Production implementation of [CalibrationRepository] (Person 1 / P1-7).
 *
 * Persists per-package calibration profiles to:
 * `filesDir/calibrations/<packageName>.json`
 *
 * ## Features:
 * - Atomic file writes using temporary files and filesystem atomic move to prevent corruption.
 * - Strict package name validation against path traversal attacks.
 * - Resilient error handling: corrupted or unreadable files return `null` safely.
 * - Pure, deterministic [computeFromSamples] without wall-clock side-effects.
 * - Dual constructors: [File]-based primary for fast pure-JVM testing and [Context]-based for Android runtime.
 */
class RealCalibrationRepository(
    val storageDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : CalibrationRepository {

    /**
     * Android runtime constructor resolving the storage directory to `context.filesDir/calibrations`.
     */
    constructor(context: Context) : this(File(context.filesDir, "calibrations"))

    companion object {
        private const val FILE_EXTENSION = ".json"
        private const val TMP_EXTENSION = ".tmp"

        // Package name validation: alphanumeric identifiers separated by dots, no directory traversal
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*$")

        /**
         * Validates a package name against illegal path characters and directory traversal attempts.
         */
        fun isValidPackageName(packageName: String): Boolean {
            if (packageName.isBlank()) return false
            if (packageName.contains("..") || packageName.contains('/') || packageName.contains('\\')) {
                return false
            }
            return PACKAGE_NAME_REGEX.matches(packageName)
        }
    }

    override suspend fun load(packageName: String): Calibration? = withContext(ioDispatcher) {
        if (!isValidPackageName(packageName)) {
            return@withContext null
        }

        val targetFile = File(storageDir, "$packageName$FILE_EXTENSION")
        if (!targetFile.exists() || !targetFile.isFile) {
            return@withContext null
        }

        try {
            val jsonContent = targetFile.readText(Charsets.UTF_8)
            JsonCalibrationSerializer.deserialize(jsonContent)
        } catch (e: Exception) {
            // Return null on I/O failure, corrupted format, or unreadable file
            null
        }
    }

    override suspend fun save(packageName: String, c: Calibration): Unit = withContext(ioDispatcher) {
        require(isValidPackageName(packageName)) {
            "Invalid package name for calibration persistence: $packageName"
        }

        if (!storageDir.exists()) {
            if (!storageDir.mkdirs() && !storageDir.exists()) {
                throw IOException("Failed to create calibration directory: ${storageDir.absolutePath}")
            }
        }

        // Assign actual Unix epoch timestamp using the system clock if sentinel or uninitialized (<= 0L)
        val calibrationToPersist = if (c.createdAtEpochMs <= 0L) {
            c.copy(createdAtEpochMs = clock())
        } else {
            c
        }

        val targetFile = File(storageDir, "$packageName$FILE_EXTENSION")
        val tempFile = File(storageDir, "$packageName$FILE_EXTENSION$TMP_EXTENSION.${System.nanoTime()}")

        try {
            val jsonString = JsonCalibrationSerializer.serialize(calibrationToPersist)
            tempFile.writeText(jsonString, Charsets.UTF_8)

            // Attempt atomic move first
            val moved = try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            } catch (e: Exception) {
                false
            }

            // Fallback for filesystems that do not support ATOMIC_MOVE
            if (!moved) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Failed to rename temporary calibration file to ${targetFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    override fun computeFromSamples(trackerId: TrackerId, samples: List<TrackerFrame>): Calibration {
        return CalibrationMath.computeFromSamples(trackerId, samples)
    }
}
