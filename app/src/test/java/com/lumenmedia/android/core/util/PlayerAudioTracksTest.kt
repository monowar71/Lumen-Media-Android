package com.lumenmedia.android.core.util

import com.google.common.truth.Truth.assertThat
import com.lumenmedia.android.core.model.AudioStreamOption
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
}
