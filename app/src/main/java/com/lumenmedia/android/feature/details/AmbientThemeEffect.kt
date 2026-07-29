package com.lumenmedia.android.feature.details

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lumenmedia.android.core.util.absoluteUrl
import java.net.URLEncoder

/**
 * Soft ambient theme on the details screen when the server cached a ThemerrDB MP3.
 * Released on leave / when [themeUrl] becomes null.
 */
@Composable
fun AmbientThemeEffect(
    themeUrl: String?,
    baseUrl: String,
    accessToken: String?,
) {
    val context = LocalContext.current
    val resolved = remember(themeUrl, baseUrl, accessToken) {
        if (themeUrl.isNullOrBlank() || baseUrl.isBlank()) {
            null
        } else {
            val absolute = absoluteUrl(baseUrl, themeUrl)
            if (accessToken.isNullOrBlank()) {
                absolute
            } else {
                val sep = if (absolute.contains('?')) '&' else '?'
                absolute + sep + "access_token=" + URLEncoder.encode(accessToken, Charsets.UTF_8.name())
            }
        }
    }

    DisposableEffect(resolved) {
        if (resolved == null) {
            return@DisposableEffect onDispose { }
        }

        var player: MediaPlayer? = null
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                isLooping = true
                setVolume(0.35f, 0.35f)
                setDataSource(context, android.net.Uri.parse(resolved))
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (_: Exception) {
            player?.release()
            player = null
        }

        onDispose {
            try {
                player?.stop()
            } catch (_: Exception) {
            }
            player?.release()
        }
    }
}
