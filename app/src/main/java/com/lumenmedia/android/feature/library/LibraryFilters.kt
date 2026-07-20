package com.lumenmedia.android.feature.library

import androidx.annotation.StringRes
import com.lumenmedia.android.R

/** Fixed genre list matching the web library filters. */
enum class LibraryGenre(val apiValue: String, @StringRes val labelRes: Int) {
    Action("Action", R.string.library_genre_action),
    Adventure("Adventure", R.string.library_genre_adventure),
    Animation("Animation", R.string.library_genre_animation),
    Comedy("Comedy", R.string.library_genre_comedy),
    Crime("Crime", R.string.library_genre_crime),
    Documentary("Documentary", R.string.library_genre_documentary),
    Drama("Drama", R.string.library_genre_drama),
    Fantasy("Fantasy", R.string.library_genre_fantasy),
    Horror("Horror", R.string.library_genre_horror),
    Romance("Romance", R.string.library_genre_romance),
    SciFi("Sci-Fi", R.string.library_genre_scifi),
    Thriller("Thriller", R.string.library_genre_thriller),
}

enum class WatchedFilter {
    All,
    Watched,
    Unwatched,
}

fun WatchedFilter.toApi(): Boolean? =
    when (this) {
        WatchedFilter.All -> null
        WatchedFilter.Watched -> true
        WatchedFilter.Unwatched -> false
    }
