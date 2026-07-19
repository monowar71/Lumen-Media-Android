package com.lumenmedia.android.core.util

import com.lumenmedia.android.core.model.PlaybackDecisionResponse
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackSourceTest {
    @Test
    fun directPlay_returnsDirectUrl() {
        val decision = PlaybackDecisionResponse(
            sessionId = "s1",
            method = "DirectPlay",
            streamUrl = "/api/v1/items/1/download",
        )
        val source = resolvePlaybackSource(decision, "http://10.0.2.2:8096")
        assertThat(source).isInstanceOf(PlaybackSource.Direct::class.java)
        assertThat((source as PlaybackSource.Direct).url)
            .isEqualTo("http://10.0.2.2:8096/api/v1/items/1/download")
    }

    @Test
    fun hls_appendsCacheToken() {
        val decision = PlaybackDecisionResponse(
            sessionId = "s1",
            method = "Transcode",
            streamUrl = "/api/v1/stream/s1/master.m3u8",
        )
        val source = resolvePlaybackSource(decision, "http://host", "42")
        assertThat(source).isInstanceOf(PlaybackSource.Hls::class.java)
        assertThat((source as PlaybackSource.Hls).url).contains("_cp=42")
    }
}
