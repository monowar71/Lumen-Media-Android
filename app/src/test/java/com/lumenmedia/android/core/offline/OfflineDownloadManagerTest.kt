package com.lumenmedia.android.core.offline

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineDownloadManagerTest {
    @Test
    fun guessExtension_prefers_content_disposition_filename() {
        val ext = OfflineDownloadManager.guessExtension(
            contentType = "application/octet-stream",
            contentDisposition = """attachment; filename="episode.mkv"""",
        )
        assertThat(ext).isEqualTo("mkv")
    }

    @Test
    fun guessExtension_uses_content_type_when_no_filename() {
        assertThat(
            OfflineDownloadManager.guessExtension("video/mp4", null),
        ).isEqualTo("mp4")
        assertThat(
            OfflineDownloadManager.guessExtension("video/x-matroska", null),
        ).isEqualTo("mkv")
    }

    @Test
    fun guessExtension_falls_back_to_bin() {
        assertThat(
            OfflineDownloadManager.guessExtension(null, null),
        ).isEqualTo("bin")
    }

    @Test
    fun offlineEpisodeState_progress_ready_is_full() {
        val state = OfflineEpisodeState(
            episodeId = "e1",
            seriesId = "s1",
            seasonId = "sea1",
            seriesTitle = "Show",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "Pilot",
            status = CachedEpisodeStatus.Ready,
            bytesDownloaded = 100,
            bytesTotal = 100,
            localPath = "/tmp/e1.mkv",
            errorMessage = null,
        )
        assertThat(state.progress).isEqualTo(1f)
        assertThat(state.displayTitle).isEqualTo("Show · S1E2 · Pilot")
    }

    @Test
    fun offlineEpisodeState_progress_partial_download() {
        val state = OfflineEpisodeState(
            episodeId = "e1",
            seriesId = "s1",
            seasonId = "sea1",
            seriesTitle = "Show",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = null,
            status = CachedEpisodeStatus.Downloading,
            bytesDownloaded = 25,
            bytesTotal = 100,
            localPath = null,
            errorMessage = null,
        )
        assertThat(state.progress).isWithin(0.001f).of(0.25f)
    }
}
