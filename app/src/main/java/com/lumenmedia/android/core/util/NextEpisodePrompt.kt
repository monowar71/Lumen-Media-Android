package com.lumenmedia.android.core.util

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
}
