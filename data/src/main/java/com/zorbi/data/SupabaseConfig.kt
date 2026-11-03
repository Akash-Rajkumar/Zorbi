package com.zorbi.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Properties

/**
 * Encapsulates Supabase endpoint configuration and anonymous key.
 *
 * Requirements:
 * - Never hardcodes credentials.
 * - Validates URL scheme (strictly http or https with valid host).
 * - Handles missing, blank, or malformed credentials gracefully.
 * - Exposes clear [isConfigured] status to enable offline no-op execution.
 * - Custom [toString] protects sensitive anon key from leakages in logs or exception traces.
 */
data class SupabaseConfig(
    val url: String = "",
    val anonKey: String = ""
) {
    /**
     * Whether valid endpoint URL and anon key are configured.
     * When false, the client operates in offline no-op mode without throwing exceptions.
     */
    val isConfigured: Boolean
        get() {
            if (url.isBlank() || anonKey.isBlank()) return false
            val parsed = url.toHttpUrlOrNull() ?: return false
            return (parsed.scheme == "http" || parsed.scheme == "https") && parsed.host.isNotBlank()
        }

    /**
     * Normalized URL with whitespace trimmed and trailing slash removed.
     */
    val normalizedUrl: String
        get() = url.trim().trimEnd('/')

    /**
     * Protects anon key from log extraction or reflection leaks.
     */
    override fun toString(): String {
        val safeKey = if (anonKey.isNotBlank()) "[PROTECTED]" else "[EMPTY]"
        return "SupabaseConfig(url=$normalizedUrl, isConfigured=$isConfigured, anonKey=$safeKey)"
    }

    companion object {
        /**
         * Represents unconfigured / offline state.
         */
        val UNCONFIGURED = SupabaseConfig(url = "", anonKey = "")

        /**
         * Creates a [SupabaseConfig] from optional or nullable strings.
         * Validates scheme (http/https), host, and presence of anon key.
         * Returns [UNCONFIGURED] if invalid, blank, or malformed.
         */
        fun fromValues(url: String?, anonKey: String?): SupabaseConfig {
            val cleanUrl = url?.trim().orEmpty()
            val cleanKey = anonKey?.trim().orEmpty()
            if (cleanUrl.isBlank() || cleanKey.isBlank()) {
                return UNCONFIGURED
            }
            val parsed = cleanUrl.toHttpUrlOrNull() ?: return UNCONFIGURED
            if (parsed.scheme != "http" && parsed.scheme != "https") {
                return UNCONFIGURED
            }
            if (parsed.host.isBlank()) {
                return UNCONFIGURED
            }
            val normalized = cleanUrl.trimEnd('/')
            return SupabaseConfig(url = normalized, anonKey = cleanKey)
        }

        /**
         * Loads configuration from a [Properties] object (e.g., local.properties).
         * Looks for keys "SUPABASE_URL" and "SUPABASE_ANON_KEY".
         */
        fun fromProperties(properties: Properties?): SupabaseConfig {
            if (properties == null) return UNCONFIGURED
            val url = properties.getProperty("SUPABASE_URL")
            val anonKey = properties.getProperty("SUPABASE_ANON_KEY")
            return fromValues(url, anonKey)
        }
    }
}
