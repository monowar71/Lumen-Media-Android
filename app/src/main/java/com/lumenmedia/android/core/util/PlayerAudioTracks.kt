package com.lumenmedia.android.core.util

import com.lumenmedia.android.core.model.AudioStreamOption

/** One audio track as exposed by ExoPlayer (group + index in that group). */
data class AudioTrackRef(
    val groupIndex: Int,
    val trackIndexInGroup: Int,
    val id: String? = null,
    val language: String? = null,
    val label: String? = null,
    val channelCount: Int = 0,
    val codecs: String? = null,
)

/**
 * Picks the ExoPlayer audio track that matches a server [AudioStreamOption].
 * DirectPlay streams the original file with every audio track; switching
 * dubs must be a local TrackSelector override, not a new transcode session.
 */
object PlayerAudioTracks {
    fun match(
        tracks: List<AudioTrackRef>,
        option: AudioStreamOption,
        allOptions: List<AudioStreamOption>,
    ): AudioTrackRef? {
        if (tracks.isEmpty()) return null

        val byId = tracks.firstOrNull { ref ->
            !ref.id.isNullOrBlank() && (
                ref.id.equals(option.id, ignoreCase = true) ||
                    option.streamIndex?.toString() == ref.id
                )
        }
        if (byId != null) return byId

        val lang = normalizeLang(option.language)
        val title = option.title?.trim().orEmpty()
        val scored = tracks.map { ref ->
            val langMatch = lang != null && lang == normalizeLang(ref.language)
            val titleMatch = title.isNotEmpty() && titleMatches(ref.label, title)
            val channelMatch = option.channels != null &&
                option.channels > 0 &&
                ref.channelCount == option.channels
            val codecMatch = codecMatches(option.codec, ref.codecs)
            val score = (if (titleMatch) 8 else 0) +
                (if (langMatch) 4 else 0) +
                (if (channelMatch) 2 else 0) +
                (if (codecMatch) 1 else 0)
            ref to score
        }
        val best = scored.maxByOrNull { it.second }
        if (best != null && best.second >= 8) return best.first
        if (best != null && best.second >= 6) return best.first

        val optionIndex = allOptions.indexOfFirst { it.id == option.id }
        if (optionIndex in tracks.indices) {
            // Unique language among remaining options: prefer that over raw index.
            if (lang != null) {
                val sameLangTracks = tracks.filter { normalizeLang(it.language) == lang }
                val sameLangOptions = allOptions.filter { normalizeLang(it.language) == lang }
                if (sameLangTracks.size == 1 && sameLangOptions.size == 1) {
                    return sameLangTracks.first()
                }
                if (sameLangTracks.size == sameLangOptions.size && sameLangTracks.isNotEmpty()) {
                    val indexInLang = sameLangOptions.indexOfFirst { it.id == option.id }
                    if (indexInLang in sameLangTracks.indices) return sameLangTracks[indexInLang]
                }
            }
            return tracks[optionIndex]
        }
        return best?.takeIf { it.second > 0 }?.first
    }

    internal fun normalizeLang(raw: String?): String? {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isEmpty() || value == "und" || value == "unknown") return null
        val mapped = ISO3_TO_2[value] ?: ISO3_TO_2[value.take(3)]
        if (mapped != null) return mapped
        return if (value.length >= 2) value.take(2) else value
    }

    private fun titleMatches(label: String?, title: String): Boolean {
        val l = label?.trim().orEmpty()
        if (l.isEmpty()) return false
        return l.equals(title, ignoreCase = true) || l.contains(title, ignoreCase = true)
    }

    private fun codecMatches(optionCodec: String?, trackCodecs: String?): Boolean {
        val wanted = optionCodec?.trim()?.lowercase().orEmpty()
        val have = trackCodecs?.trim()?.lowercase().orEmpty()
        if (wanted.isEmpty() || have.isEmpty()) return false
        if (have.contains(wanted)) return true
        if (wanted == "ac3" && (have.contains("ac-3") || have.contains("ac3"))) return true
        if (wanted == "eac3" && (have.contains("ec-3") || have.contains("eac3"))) return true
        if ((wanted == "dts" || wanted == "dca") && have.contains("dts")) return true
        return false
    }

    private val ISO3_TO_2 = mapOf(
        "rus" to "ru",
        "eng" to "en",
        "ukr" to "uk",
        "deu" to "de",
        "ger" to "de",
        "fra" to "fr",
        "fre" to "fr",
        "spa" to "es",
        "ita" to "it",
        "jpn" to "ja",
        "chi" to "zh",
        "zho" to "zh",
        "kor" to "ko",
        "pol" to "pl",
    )
}
