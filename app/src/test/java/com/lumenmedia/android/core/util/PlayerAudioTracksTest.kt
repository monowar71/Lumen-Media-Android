package com.lumenmedia.android.core.util

import com.google.common.truth.Truth.assertThat
import com.lumenmedia.android.core.model.AudioStreamOption
import com.lumenmedia.android.core.model.EpisodeSummary
import org.junit.Test

class PlayerAudioTracksTest {
    private val lostFilm = AudioStreamOption(
        id = "a1",
        language = "rus",
        title = "LostFilm",
        codec = "ac3",
        channels = 6,
        isDefault = true,
        streamIndex = 1,
    )
    private val tvShows = AudioStreamOption(
        id = "a2",
        language = "rus",
        title = "TVShows",
        codec = "ac3",
        channels = 2,
        streamIndex = 2,
    )
    private val original = AudioStreamOption(
        id = "a3",
        language = "eng",
        title = "Original",
        codec = "eac3",
        channels = 6,
        streamIndex = 3,
    )
    private val options = listOf(lostFilm, tvShows, original)

    private val tracks = listOf(
        AudioTrackRef(0, 0, id = "1", language = "ru", label = "LostFilm", channelCount = 6, codecs = "ac3"),
        AudioTrackRef(0, 1, id = "2", language = "ru", label = "TVShows", channelCount = 2, codecs = "ac3"),
        AudioTrackRef(0, 2, id = "3", language = "en", label = "Original", channelCount = 6, codecs = "ec-3"),
    )

    @Test
    fun match_prefersTitleAmongSameLanguage() {
        val match = PlayerAudioTracks.match(tracks, tvShows, options)
        assertThat(match?.trackIndexInGroup).isEqualTo(1)
        assertThat(match?.label).isEqualTo("TVShows")
    }

    @Test
    fun match_mapsIso639AndEnglishOriginal() {
        val match = PlayerAudioTracks.match(tracks, original, options)
        assertThat(match?.language).isEqualTo("en")
        assertThat(match?.trackIndexInGroup).isEqualTo(2)
    }

    @Test
    fun match_fallsBackToOptionOrder() {
        val untitled = listOf(
            AudioStreamOption(id = "a1", language = "rus"),
            AudioStreamOption(id = "a2", language = "rus"),
        )
        val untitledTracks = listOf(
            AudioTrackRef(0, 0, language = "ru", channelCount = 6),
            AudioTrackRef(0, 1, language = "ru", channelCount = 2),
        )
        val match = PlayerAudioTracks.match(untitledTracks, untitled[1], untitled)
        assertThat(match?.trackIndexInGroup).isEqualTo(1)
    }

    @Test
    fun match_identicalDubMetadata_usesOptionOrderNotFirstTrack() {
        val dubs = listOf(
            AudioStreamOption(id = "a1", language = "rus", title = "LostFilm", codec = "ac3", channels = 6, streamIndex = 1),
            AudioStreamOption(id = "a2", language = "rus", title = "TVShows", codec = "ac3", channels = 6, streamIndex = 2),
            AudioStreamOption(id = "a3", language = "rus", title = "Jaskier", codec = "ac3", channels = 6, streamIndex = 3),
        )
        // Android TV often omits Matroska track titles from Format.label.
        // Format.id is the Matroska track number, not ffprobe StreamIndex.
        val unlabeled = listOf(
            AudioTrackRef(0, 0, id = "2", language = "ru", channelCount = 6, codecs = "ac3"),
            AudioTrackRef(0, 1, id = "3", language = "ru", channelCount = 6, codecs = "ac3"),
            AudioTrackRef(0, 2, id = "4", language = "ru", channelCount = 6, codecs = "ac3"),
        )
        val match = PlayerAudioTracks.match(unlabeled, dubs[1], dubs)
        assertThat(match?.trackIndexInGroup).isEqualTo(1)
        assertThat(match?.id).isEqualTo("3")
    }
}

class NextEpisodePromptTest {
    @Test
    fun hiddenWithoutNextIdOrDuration() {
        assertThat(NextEpisodePrompt.shouldShow(null, 95_000, 100_000, 5)).isFalse()
        assertThat(NextEpisodePrompt.shouldShow("next", 1_000, 0, 5)).isFalse()
    }

    @Test
    fun shownWithinPercentFromEnd() {
        assertThat(NextEpisodePrompt.shouldShow("next", 96_000, 100_000, 5)).isTrue()
        assertThat(NextEpisodePrompt.shouldShow("next", 90_000, 100_000, 5)).isFalse()
    }

    @Test
    fun shownWhenPlaybackEndedEvenIfDurationMismatch() {
        assertThat(
            NextEpisodePrompt.shouldShow(
                nextEpisodeId = "next",
                positionMs = 50_000,
                durationMs = 100_000,
                percentFromEnd = 5,
                playbackEnded = true,
            ),
        ).isTrue()
    }

    @Test
    fun finishedWhenPausedOnLastFrame() {
        assertThat(
            NextEpisodePrompt.isPlaybackFinished(
                playing = false,
                playbackEnded = false,
                positionMs = 99_200,
                durationMs = 100_000,
            ),
        ).isTrue()
        assertThat(
            NextEpisodePrompt.isPlaybackFinished(
                playing = true,
                playbackEnded = false,
                positionMs = 99_200,
                durationMs = 100_000,
            ),
        ).isFalse()
    }

    @Test
    fun nextAfter_picksFollowingEpisodeThenNextSeason() {
        val e1 = EpisodeSummary(id = "e1", seasonNumber = 5, episodeNumber = 1)
        val e2 = EpisodeSummary(id = "e2", seasonNumber = 5, episodeNumber = 2)
        val e3 = EpisodeSummary(id = "e3", seasonNumber = 6, episodeNumber = 1)
        assertThat(NextEpisodePrompt.nextAfter("e1", 5, 1, listOf(e3, e1, e2))?.id).isEqualTo("e2")
        assertThat(NextEpisodePrompt.nextAfter("e2", 5, 2, listOf(e3, e1, e2))?.id).isEqualTo("e3")
        assertThat(NextEpisodePrompt.nextAfter("e3", 6, 1, listOf(e3, e1, e2))).isNull()
    }
}
