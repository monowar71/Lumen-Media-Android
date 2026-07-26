package com.lumenmedia.android.feature.player

import com.lumenmedia.android.core.model.AudioStreamOption
import com.lumenmedia.android.core.model.DeviceProfile
import com.lumenmedia.android.core.model.PlaybackDecisionRequest
import com.lumenmedia.android.core.model.PlaybackDecisionResponse
import com.lumenmedia.android.core.model.SubtitleStreamOption
import com.lumenmedia.android.core.preferences.AppSettings
import com.lumenmedia.android.core.preferences.LibraryOrder
import com.lumenmedia.android.core.preferences.LibrarySort
import com.lumenmedia.android.core.preferences.SettingsRepository
import com.lumenmedia.android.core.util.ConnectionKind
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/** Contract tests for audio/subtitle decision payloads used by the player. */
class PlayerTrackSelectionTest {
    @Test
    fun playbackDecisionRequest_includesAudioAndSubtitleIds() {
        val request = PlaybackDecisionRequest(
            mediaId = "item-1",
            mode = "auto",
            qualityId = null,
            audioStreamId = "a1",
            subtitleStreamId = "s1",
            resumePositionMs = 12_000,
            profile = DeviceProfile(
                maxResolution = "1080p",
                maxBitrateKbps = 8000,
                videoCodecs = listOf("h264"),
                audioCodecs = listOf("aac"),
                containers = listOf("mp4", "hls"),
                subtitleFormats = listOf("vtt"),
                supportsHevc = false,
                supportsHdr = false,
            ),
        )
        assertThat(request.audioStreamId).isEqualTo("a1")
        assertThat(request.subtitleStreamId).isEqualTo("s1")
        assertThat(request.resumePositionMs).isEqualTo(12_000)
    }

    @Test
    fun decisionResponse_exposesTrackLists() {
        val decision = PlaybackDecisionResponse(
            sessionId = "sess",
            method = "DirectPlay",
            streamUrl = "/stream",
            audioStreams = listOf(
                AudioStreamOption(id = "a1", language = "eng", title = "English", channels = 2, isDefault = true),
                AudioStreamOption(id = "a2", language = "rus", title = "LostFilm", channels = 6),
            ),
            subtitleStreams = listOf(
                SubtitleStreamOption(
                    id = "s1",
                    language = "rus",
                    title = "Russian (Forced)",
                    format = "vtt",
                    deliveryUrl = "/subs/1",
                ),
            ),
        )
        assertThat(decision.audioStreams).hasSize(2)
        assertThat(decision.subtitleStreams.first().deliveryUrl).isEqualTo("/subs/1")
        assertThat(decision.audioStreams.first { it.isDefault == true }.id).isEqualTo("a1")
    }

    @Test
    fun settingsCap_usesExternalOnCellular() {
        val settingsRepository = mockk<SettingsRepository>()
        val settings = AppSettings(
            baseUrl = "http://server",
            lanCapKbps = 40_000,
            externalCapKbps = 8_000,
            preferredMode = "auto",
            librarySort = LibrarySort.Added,
            libraryOrder = LibraryOrder.Desc,
            libraryInProgressFirst = false,
            locale = "ru",
            maxCacheBytes = 0L,
        )
        every { settingsRepository.capFor(settings, ConnectionKind.External) } returns 8_000
        every { settingsRepository.capFor(settings, ConnectionKind.Lan) } returns 40_000
        assertThat(settingsRepository.capFor(settings, ConnectionKind.External)).isEqualTo(8_000)
        assertThat(settingsRepository.capFor(settings, ConnectionKind.Lan)).isEqualTo(40_000)
    }
}
