package com.lumenmedia.android.core.util

import androidx.lifecycle.SavedStateHandle

/**
 * Navigation query params are not always stored as the declared NavType.
 * [resumeMs] is a String; [isEpisode] is declared BoolType but Hilt's
 * SavedStateHandle sometimes still has the raw "true"/"false" string.
 */
fun SavedStateHandle.navBoolean(key: String, default: Boolean = false): Boolean {
    return when (val value = get<Any?>(key)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        is Number -> value.toInt() != 0
        else -> default
    }
}
