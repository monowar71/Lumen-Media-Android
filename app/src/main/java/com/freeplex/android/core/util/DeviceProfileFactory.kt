package com.freeplex.android.core.util

import android.media.MediaCodecList
import com.freeplex.android.core.model.DeviceProfile

object DeviceProfileFactory {
    fun build(maxBitrateKbps: Int, maxResolution: String = "2160p"): DeviceProfile {
        val hevc = supportsCodec("video/hevc")
        val hdr = supportsHdr()
        val video = mutableListOf("h264")
        if (hevc) video += "hevc"
        if (supportsCodec("video/av01")) video += "av1"
        val audio = mutableListOf("aac", "mp3")
        if (supportsCodec("audio/ac3")) audio += "ac3"
        if (supportsCodec("audio/eac3")) audio += "eac3"
        if (supportsCodec("audio/opus")) audio += "opus"
        return DeviceProfile(
            maxResolution = maxResolution,
            maxBitrateKbps = maxBitrateKbps,
            videoCodecs = video,
            audioCodecs = audio,
            containers = listOf("hls", "mp4", "mkv", "webm"),
            subtitleFormats = listOf("vtt", "srt", "ass"),
            supportsHevc = hevc,
            supportsHdr = hdr,
        )
    }

    private fun supportsCodec(mime: String): Boolean {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        } catch (_: Throwable) {
            mime == "video/avc" || mime == "audio/mp4a-latm"
        }
    }

    private fun supportsHdr(): Boolean = supportsCodec("video/hevc")
}
