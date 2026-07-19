package com.freeplex.android.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun normalizeBaseUrl_stripsTrailingSlash_andAddsScheme() {
        assertThat(normalizeBaseUrl("example.com/")).isEqualTo("http://example.com")
        assertThat(normalizeBaseUrl("https://host:8096/")).isEqualTo("https://host:8096")
    }

    @Test
    fun absoluteUrl_joinsRelativePaths() {
        assertThat(absoluteUrl("http://host:8096", "/api/v1/x")).isEqualTo("http://host:8096/api/v1/x")
        assertThat(absoluteUrl("http://host:8096", "http://cdn/img")).isEqualTo("http://cdn/img")
    }

    @Test
    fun rewriteLoopbackForEmulator_mapsLocalhost_onlyOnEmulator() {
        assertThat(rewriteLoopbackForEmulator("http://localhost:8096", isEmulator = true))
            .isEqualTo("http://10.0.2.2:8096")
        assertThat(rewriteLoopbackForEmulator("http://127.0.0.1:8096", isEmulator = true))
            .isEqualTo("http://10.0.2.2:8096")
        assertThat(rewriteLoopbackForEmulator("http://localhost:8096", isEmulator = false))
            .isEqualTo("http://localhost:8096")
    }

    @Test
    fun artworkUrl_addsSizeAndAccessToken() {
        val url = artworkUrl(
            baseUrl = "http://host:8096",
            path = "/api/v1/items/1/artwork/Poster",
            token = "tok",
            width = 240,
            height = 360,
        )
        assertThat(url).contains("w=240")
        assertThat(url).contains("h=360")
        assertThat(url).contains("access_token=tok")
        assertThat(url).startsWith("http://host:8096/api/v1/items/1/artwork/Poster")
    }
}
