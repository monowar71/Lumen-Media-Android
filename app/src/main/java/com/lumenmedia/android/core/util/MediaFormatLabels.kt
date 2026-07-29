package com.lumenmedia.android.core.util

/**
 * Compact labels for player HUD chips (HDR, Atmos, codecs, network Mbps).
 */
object MediaFormatLabels {

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
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }

    fun videoFormatBadges(
        codec: String?,
        hdr: String?,
        width: Int?,
        height: Int?,
    ): List<String> = buildList {
        resolutionLabel(width, height)?.let(::add)
        hdrLabel(hdr)?.let(::add)
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

    fun formatNetworkMbps(bps: Long): String? {
        if (bps <= 0L) return null
        val mbps = bps / 1_000_000.0
        return when {
            mbps >= 100 -> "${mbps.toInt()} Mbps"
            mbps >= 10 -> "${"%.1f".format(java.util.Locale.US, mbps)} Mbps"
            else -> "${"%.2f".format(java.util.Locale.US, mbps)} Mbps"
        }
    }
}
