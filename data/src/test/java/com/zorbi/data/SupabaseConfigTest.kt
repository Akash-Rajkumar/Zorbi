package com.zorbi.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Properties

class SupabaseConfigTest {

    @Test
    fun testValidHttpsUrlConfiguration() {
        val config = SupabaseConfig.fromValues(
            url = "https://example.supabase.co/",
            anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        )

        assertThat(config.isConfigured).isTrue()
        assertThat(config.normalizedUrl).isEqualTo("https://example.supabase.co")
        assertThat(config.anonKey).isEqualTo("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    }

    @Test
    fun testValidHttpUrlConfiguration() {
        val config = SupabaseConfig.fromValues(
            url = "http://127.0.0.1:54321",
            anonKey = "local-anon-key"
        )

        assertThat(config.isConfigured).isTrue()
        assertThat(config.normalizedUrl).isEqualTo("http://127.0.0.1:54321")
        assertThat(config.anonKey).isEqualTo("local-anon-key")
    }

    @Test
    fun testRejectFtpUrlScheme() {
        val config = SupabaseConfig.fromValues(
            url = "ftp://example.supabase.co/rest/v1",
            anonKey = "some-key"
        )

        assertThat(config.isConfigured).isFalse()
        assertThat(config).isEqualTo(SupabaseConfig.UNCONFIGURED)
    }

    @Test
    fun testRejectFileUrlScheme() {
        val config = SupabaseConfig.fromValues(
            url = "file:///etc/passwd",
            anonKey = "some-key"
        )

        assertThat(config.isConfigured).isFalse()
        assertThat(config).isEqualTo(SupabaseConfig.UNCONFIGURED)
    }

    @Test
    fun testRejectMissingHost() {
        val configNoHost = SupabaseConfig.fromValues(
            url = "https://",
            anonKey = "some-key"
        )

        assertThat(configNoHost.isConfigured).isFalse()
        assertThat(configNoHost).isEqualTo(SupabaseConfig.UNCONFIGURED)
    }

    @Test
    fun testRejectMalformedUrl() {
        val configMalformed = SupabaseConfig.fromValues(
            url = "not-a-valid-url:123/path",
            anonKey = "some-key"
        )

        assertThat(configMalformed.isConfigured).isFalse()
        assertThat(configMalformed).isEqualTo(SupabaseConfig.UNCONFIGURED)
    }

    @Test
    fun testRejectWhitespaceOnlyValues() {
        val configWhitespace = SupabaseConfig.fromValues(
            url = "   ",
            anonKey = "  "
        )

        assertThat(configWhitespace.isConfigured).isFalse()
        assertThat(configWhitespace).isEqualTo(SupabaseConfig.UNCONFIGURED)
    }

    @Test
    fun testMissingUrlOrKeyReturnsUnconfigured() {
        val configNullUrl = SupabaseConfig.fromValues(url = null, anonKey = "secret-key")
        val configNullKey = SupabaseConfig.fromValues(url = "https://example.supabase.co", anonKey = null)

        assertThat(configNullUrl.isConfigured).isFalse()
        assertThat(configNullKey.isConfigured).isFalse()
    }

    @Test
    fun testFromPropertiesValid() {
        val props = Properties().apply {
            setProperty("SUPABASE_URL", "https://project.supabase.co/")
            setProperty("SUPABASE_ANON_KEY", "anon-token-12345")
        }

        val config = SupabaseConfig.fromProperties(props)
        assertThat(config.isConfigured).isTrue()
        assertThat(config.normalizedUrl).isEqualTo("https://project.supabase.co")
        assertThat(config.anonKey).isEqualTo("anon-token-12345")
    }

    @Test
    fun testFromPropertiesInvalidSchemeOrMissing() {
        val propsFtp = Properties().apply {
            setProperty("SUPABASE_URL", "ftp://project.supabase.co/")
            setProperty("SUPABASE_ANON_KEY", "anon-token-12345")
        }
        val configFtp = SupabaseConfig.fromProperties(propsFtp)
        assertThat(configFtp.isConfigured).isFalse()

        val configNull = SupabaseConfig.fromProperties(null)
        val configEmpty = SupabaseConfig.fromProperties(Properties())
        assertThat(configNull.isConfigured).isFalse()
        assertThat(configEmpty.isConfigured).isFalse()
    }

    @Test
    fun testToStringDoesNotExposeKey() {
        val secretKey = "super-sensitive-jwt-token"
        val config = SupabaseConfig.fromValues("https://test.supabase.co", secretKey)

        val debugString = config.toString()
        assertThat(debugString).doesNotContain(secretKey)
        assertThat(debugString).contains("[PROTECTED]")
    }
}
