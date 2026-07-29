package com.lumenmedia.android.core.util

import com.lumenmedia.android.core.model.QualityOption

/**
 * Compact labels for player HUD chips (HDR, Atmos, codecs, network Mbps)
 * and source→output conversion when transcoding.
 */
object MediaFormatLabels {

    data class PlaybackFormatPaths(
        val videoLabel: String?,
        val audioLabel: String?,
    )

    fun hdrLabel(hdr: String?): String? {
        val h = hdr?.trim().orEmpty()
        if (h.isEmpty()) return null
        return when (h.lowercase()) {
            "dolbyvision", "dolby vision", "dv" -> "Dolby Vision"
            "hdr10+" -> "HDR10+"
            "hdr10" -> "HDR10"
            "hlg" -> "HLG"
            else -> h
        }
    }

    fun resolutionLabel(width: Int?, height: Int?): String? {
        val h = height ?: return null
        if (h <= 0) return null
        val w = width ?: 0
        return when {
            h >= 2160 || w >= 3840 -> "2160p"
            h >= 1440 || w >= 2560 -> "1440p"
            h >= 1080 || w >= 1920 -> "1080p"
            h >= 720 || w >= 1280 -> "720p"
            h >= 480 -> "480p"
            h >= 360 -> "360p"
            else -> "${h}p"
        }
    }

    private fun videoCodecLabel(codec: String?): String? {
        val c = codec?.lowercase().orEmpty()
        if (c.isEmpty()) return null
        return when (c) {
            "h264", "avc", "avc1" -> "H.264"
            "hevc", "h265", "hvc1" -> "HEVC"
            "av1", "av01" -> "AV1"
            "vp9" -> "VP9"
            else -> codec!!.uppercase()
        }
    }

    fun isAtmosAudio(codec: String?, title: String?): Boolean {
        val t = title.orEmpty().lowercase()
        if (t.contains("atmos")) return true
        val c = codec.orEmpty().lowercase()
        return (c == "truehd" || c == "eac3" || c == "ec-3") && t.contains("joc")
    }

    fun audioFormatLabel(codec: String?, channels: Int?, title: String?): String? {
        if (isAtmosAudio(codec, title)) return "Dolby Atmos"
        val c = codec.orEmpty().lowercase()
        val t = title.orEmpty().lowercase()
        if (t.contains("dts:x") || t.contains("dts-x")) return "DTS:X"
        return when {
            c == "truehd" -> "Dolby TrueHD"
            c == "eac3" || c == "ec-3" -> "Dolby Digital+"
            c == "ac3" || c == "ac-3" -> "Dolby Digital"
            c.contains("dts") && (c.contains("hd") || t.contains("hd ma")) -> "DTS-HD"
            c == "dts" -> "DTS"
            c == "flac" -> "FLAC"
            c == "opus" -> "Opus"
            c == "aac" || c == "mp4a" -> "AAC"
            c == "pcm" || c.startsWith("pcm_") -> "PCM"
            c.isEmpty() -> null
            else -> codec!!.uppercase()
        }
    }

    fun channelLayoutLabel(channels: Int?): String? = when (channels) {
        null, 0 -> null
        1 -> "Mono"
        2 -> "Stereo"
        3 -> "2.1"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }

    fun channelsForAudioLayout(layoutId: String?): Int? = when (layoutId?.trim()?.lowercase()) {
        "mono" -> 1
        "stereo" -> 2
        "2.1" -> 3
        "5.1" -> 6
        "7.1" -> 8
        else -> null
    }

    fun videoFormatBadges(
        codec: String?,
        hdr: String?,
        width: Int?,
        height: Int?,
        includeSdr: Boolean = false,
    ): List<String> = buildList {
        resolutionLabel(width, height)?.let(::add)
        val hdrBadge = hdrLabel(hdr)
        when {
            hdrBadge != null -> add(hdrBadge)
            includeSdr -> add("SDR")
        }
        videoCodecLabel(codec)?.let(::add)
    }

    fun audioFormatBadges(
        codec: String?,
        channels: Int?,
        title: String?,
    ): List<String> = buildList {
        audioFormatLabel(codec, channels, title)?.let(::add)
        if (!isAtmosAudio(codec, title)) {
            channelLayoutLabel(channels)?.let(::add)
        }
    }

    fun videoFormatSummary(
        codec: String?,
        hdr: String?,
        width: Int?,
        height: Int?,
        includeSdr: Boolean = false,
    ): String? {
        val parts = videoFormatBadges(codec, hdr, width, height, includeSdr)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun audioFormatSummary(codec: String?, channels: Int?, title: String?): String? {
        val parts = audioFormatBadges(codec, channels, title)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun joinArrow(from: String?, to: String?): String? = when {
        from != null && to != null && from != to -> "$from → $to"
        else -> to ?: from
    }

    /**
     * Player HUD: on Transcode show source → H.264/AAC output; otherwise source only.
     */
    fun playbackFormatPaths(
        method: String?,
        sourceCodec: String?,
        sourceHdr: String?,
        sourceWidth: Int?,
        sourceHeight: Int?,
        sourceAudioCodec: String?,
        sourceAudioChannels: Int?,
        sourceAudioTitle: String?,
        selectedQualityId: String?,
        availableQualities: List<QualityOption>,
        toneMapActive: Boolean,
        selectedAudioLayout: String?,
    ): PlaybackFormatPaths {
        val sourceVideo = videoFormatSummary(sourceCodec, sourceHdr, sourceWidth, sourceHeight)
        val sourceAudio = audioFormatSummary(sourceAudioCodec, sourceAudioChannels, sourceAudioTitle)
        if (!method.equals("Transcode", ignoreCase = true)) {
            return PlaybackFormatPaths(sourceVideo, sourceAudio)
        }

        val quality = availableQualities.firstOrNull { it.id == selectedQualityId }
        val outHeight = quality?.height ?: sourceHeight
        val outWidth = quality?.width ?: sourceWidth
        val hadHdr = !sourceHdr.isNullOrBlank() || toneMapActive
        val outputVideo = videoFormatSummary(
            codec = "h264",
            hdr = null,
            width = outWidth,
            height = outHeight,
            includeSdr = hadHdr,
        )
        val outChannels = channelsForAudioLayout(selectedAudioLayout) ?: 2
        val outputAudio = audioFormatSummary(codec = "aac", channels = outChannels, title = null)

        return PlaybackFormatPaths(
            videoLabel = joinArrow(sourceVideo, outputVideo),
            audioLabel = joinArrow(sourceAudio, outputAudio),
        )
    }

    fun formatNetworkMbps(bps: Long): String? {
        if (bps <= 0L) return null
        val mbps = bps / 1_000_000.0
        return when {
            mbps >= 100 -> "${mbps.toInt()} Mbps"
            mbps >= 10 -> "${"%.1f".format(java.util.Locale.US, mbps)} Mbps"
            else -> "${"%.2f".format(java.util.Locale.US, mbps)} Mbps"
        }
    }

    fun formatBytes(bytes: Long): String? {
        if (bytes <= 0L) return null
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> "${"%.1f".format(java.util.Locale.US, bytes / gb)} GB"
            bytes >= mb -> "${"%.0f".format(java.util.Locale.US, bytes / mb)} MB"
            bytes >= kb -> "${"%.0f".format(java.util.Locale.US, bytes / kb)} KB"
            else -> "$bytes B"
        }
    }

    fun containerBitrateLine(container: String?, sizeBytes: Long, overallBitrateKbps: Int): String? {
        val parts = buildList {
            container?.takeIf { it.isNotBlank() }?.uppercase()?.let(::add)
            formatBytes(sizeBytes)?.let(::add)
            if (overallBitrateKbps > 0) {
                val mbps = overallBitrateKbps / 1000.0
                add(
                    if (mbps >= 10) "${mbps.toInt()} Mbps"
                    else "${"%.1f".format(java.util.Locale.US, mbps)} Mbps",
                )
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
