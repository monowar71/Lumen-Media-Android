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
}
