package com.lumenmedia.android.core.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {
    const val DEFAULT_TAG = "ru"

    fun apply(tag: String) {
        val normalized = tag.trim().lowercase().ifBlank { DEFAULT_TAG }
        val locales = LocaleListCompat.forLanguageTags(normalized)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
