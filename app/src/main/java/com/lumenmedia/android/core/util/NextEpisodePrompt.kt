package com.lumenmedia.android.core.util

import com.lumenmedia.android.core.model.EpisodeSummary

object NextEpisodePrompt {
    fun shouldShow(
        nextEpisodeId: String?,
        positionMs: Long,
        durationMs: Long,
        percentFromEnd: Int,
        playbackEnded: Boolean = false,
    ): Boolean {
        if (nextEpisodeId.isNullOrBlank()) return false
        if (playbackEnded) return true
        if (durationMs <= 0L) return false
        val remaining = (durationMs - positionMs).coerceAtLeast(0L)
        val pct = percentFromEnd.coerceIn(1, 50)
        val thresholdMs = durationMs * pct / 100L
        return remaining <= thresholdMs
    }

    /** True when the player stopped on the last frame without STATE_ENDED (common on Android TV). */
    fun isPlaybackFinished(
        playing: Boolean,
        playbackEnded: Boolean,
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        if (playbackEnded) return true
        if (playing || durationMs <= 0L) return false
        return positionMs >= (durationMs - 1_500L).coerceAtLeast(0L)
    }

    /**
     * Chronological next episode (regular seasons first, specials last).
     * Used when GET /episodes/{id} omitted nextEpisode.
     */
    fun nextAfter(
        currentId: String,
        currentSeasonNumber: Int,
        currentEpisodeNumber: Int,
        all: List<EpisodeSummary>,
    ): EpisodeSummary? {
        if (all.isEmpty()) return null
        val ordered = all.sortedWith(
            compareBy<EpisodeSummary> { if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber }
                .thenBy { it.episodeNumber },
        )
        val idx = ordered.indexOfFirst { it.id == currentId }
        if (idx >= 0) {
            return ordered.getOrNull(idx + 1)
        }
        val currentSeasonRank = if (currentSeasonNumber == 0) Int.MAX_VALUE else currentSeasonNumber
        return ordered.firstOrNull { candidate ->
            val seasonRank = if (candidate.seasonNumber == 0) Int.MAX_VALUE else candidate.seasonNumber
            seasonRank > currentSeasonRank ||
                (seasonRank == currentSeasonRank && candidate.episodeNumber > currentEpisodeNumber)
        }
    }
}
