package com.lumenmedia.android.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaFormatLabelsTest {
    @Test
    fun hdrLabel_maps_variants() {
        assertThat(MediaFormatLabels.hdrLabel("HDR10")).isEqualTo("HDR10")
        assertThat(MediaFormatLabels.hdrLabel("DolbyVision")).isEqualTo("Dolby Vision")
        assertThat(MediaFormatLabels.hdrLabel(null)).isNull()
    }

    @Test
    fun audioFormat_detects_atmos_and_surround() {
        assertThat(
            MediaFormatLabels.audioFormatLabel("eac3", 8, "English Atmos"),
        ).isEqualTo("Dolby Atmos")
        assertThat(MediaFormatLabels.audioFormatBadges("ac3", 6, null))
            .containsExactly("Dolby Digital", "5.1")
            .inOrder()
    }

    @Test
    fun videoFormat_includes_resolution_and_hdr() {
        assertThat(
            MediaFormatLabels.videoFormatBadges("hevc", "HDR10", 3840, 2160),
        ).containsExactly("2160p", "HDR10", "HEVC").inOrder()
    }

    @Test
    fun formatNetworkMbps_formats_throughput() {
        assertThat(MediaFormatLabels.formatNetworkMbps(12_400_000)).isEqualTo("12.4 Mbps")
        assertThat(MediaFormatLabels.formatNetworkMbps(0)).isNull()
    }

    @Test
    fun playbackFormatPaths_shows_transcode_conversion() {
        val paths = MediaFormatLabels.playbackFormatPaths(
            method = "Transcode",
            sourceCodec = "hevc",
            sourceHdr = "DolbyVision",
            sourceWidth = 3840,
            sourceHeight = 2160,
            sourceAudioCodec = "eac3",
            sourceAudioChannels = 6,
            sourceAudioTitle = null,
            selectedQualityId = "1080",
            availableQualities = listOf(
                com.lumenmedia.android.core.model.QualityOption(
                    id = "1080",
                    label = "1080p",
                    width = 1920,
                    height = 1080,
                ),
            ),
            toneMapActive = true,
            selectedAudioLayout = "stereo",
        )
        assertThat(paths.videoLabel)
            .isEqualTo("2160p · Dolby Vision · HEVC → 1080p · SDR · H.264")
        assertThat(paths.audioLabel)
            .isEqualTo("Dolby Digital+ · 5.1 → AAC · Stereo")
    }

    @Test
    fun playbackFormatPaths_hides_unknown_source_codec() {
        val paths = MediaFormatLabels.playbackFormatPaths(
            method = "Transcode",
            sourceCodec = "unknown",
            sourceHdr = null,
            sourceWidth = null,
            sourceHeight = null,
            sourceAudioCodec = "unknown",
            sourceAudioChannels = null,
            sourceAudioTitle = null,
            selectedQualityId = "auto",
            availableQualities = emptyList(),
            toneMapActive = false,
            selectedAudioLayout = "stereo",
        )
        assertThat(paths.videoLabel).isEqualTo("H.264")
        assertThat(paths.audioLabel).isEqualTo("AAC · Stereo")
    }

    @Test
    fun formatTorrentStatsLabel_builds_hud_chip() {
        assertThat(
            MediaFormatLabels.formatTorrentStatsLabel(12, 45, 2_100_000),
        ).isEqualTo("↓ 2.1 MB/s · 12↑ · 45 peers")
        assertThat(
            MediaFormatLabels.formatTorrentStatsLabel(0, 0, 0),
        ).isEqualTo("0↑ · 0 peers")
    }
}
