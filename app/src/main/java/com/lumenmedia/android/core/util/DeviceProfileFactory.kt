package com.lumenmedia.android.core.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import com.lumenmedia.android.core.model.DeviceProfile

object DeviceProfileFactory {
    fun build(
        context: Context,
        maxBitrateKbps: Int,
        maxResolution: String = "2160p",
    ): DeviceProfile {
        val hevc = supportsCodec("video/hevc")
        val hdr = supportsHdr(context)
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

    /** True when at least one connected display reports an HDR type. */
    fun supportsHdr(context: Context): Boolean {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                ?: return false
            dm.displays.any { display -> displaySupportsHdr(display) }
        } catch (_: Throwable) {
            false
        }
    }

    private fun displaySupportsHdr(display: Display): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val caps = display.hdrCapabilities ?: return false
        val types = caps.supportedHdrTypes
        return types.isNotEmpty()
    }
}
