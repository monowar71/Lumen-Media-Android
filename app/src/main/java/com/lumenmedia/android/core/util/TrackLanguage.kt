package com.lumenmedia.android.core.util

import android.content.Context
import com.lumenmedia.android.R

/** Maps ISO language codes from playback streams to localized display names. */
fun formatTrackLanguage(context: Context, code: String?): String {
    val key = code?.trim()?.lowercase().orEmpty()
    val resId = when (key) {
        "eng", "en" -> R.string.lang_eng
        "rus", "ru" -> R.string.lang_rus
        "und", "" -> R.string.lang_und
        else -> null
    }
    return if (resId != null) context.getString(resId) else (code?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lang_unknown))
}
