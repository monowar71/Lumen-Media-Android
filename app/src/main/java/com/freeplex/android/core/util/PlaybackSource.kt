package com.freeplex.android.core.util

import com.freeplex.android.core.model.PlaybackDecisionResponse

sealed class PlaybackSource {
    data class Direct(val url: String) : PlaybackSource()
    data class Hls(val url: String) : PlaybackSource()
}

/**
 * Maps a server playback decision to a concrete ExoPlayer source.
 * Mirrors client_web/src/features/player/playbackSource.ts.
 */
fun resolvePlaybackSource(
    decision: PlaybackDecisionResponse,
    baseUrl: String,
    cacheToken: String? = null,
): PlaybackSource {
    var url = absoluteUrl(baseUrl, decision.streamUrl)
    if (decision.method == "DirectPlay") {
        return PlaybackSource.Direct(url)
    }
    if (!cacheToken.isNullOrBlank()) {
        url = if (url.contains("?")) "$url&_cp=$cacheToken" else "$url?_cp=$cacheToken"
    }
    return PlaybackSource.Hls(url)
}
