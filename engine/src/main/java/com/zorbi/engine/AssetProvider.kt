package com.zorbi.engine

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Abstraction for reading bundled asset files, enabling Android runtime execution
 * via [android.content.res.AssetManager] as well as deterministic, pure JVM unit testing.
 */
interface AssetProvider {
    fun open(path: String): InputStream?
    fun list(path: String): List<String>
}

/**
 * Production implementation using Android [AssetManager].
 */
class AndroidAssetProvider(private val context: Context) : AssetProvider {
    override fun open(path: String): InputStream? {
        return try {
            context.assets.open(path)
        } catch (_: Throwable) {
            null
        }
    }

    override fun list(path: String): List<String> {
        return try {
            context.assets.list(path)?.toList() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

/**
 * File-system backed implementation for JVM unit tests and offline testing.
 */
class DirectoryAssetProvider(private val baseDir: File) : AssetProvider {
    override fun open(path: String): InputStream? {
        val file = File(baseDir, path)
        return if (file.exists() && file.isFile) {
            try {
                FileInputStream(file)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    override fun list(path: String): List<String> {
        val dir = File(baseDir, path)
        return if (dir.exists() && dir.isDirectory) {
            dir.list()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
}

