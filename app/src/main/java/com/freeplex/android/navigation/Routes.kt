package com.freeplex.android.navigation

object Routes {
    const val Login = "login"
    const val Home = "home"
    const val Library = "library/{libraryId}"
    const val Item = "item/{itemId}"
    const val Search = "search"
    const val Settings = "settings"
    const val Player = "watch/{itemId}?resumeMs={resumeMs}&isEpisode={isEpisode}"

    fun library(id: String) = "library/$id"
    fun item(id: String) = "item/$id"
    fun player(itemId: String, resumeMs: Long = 0, isEpisode: Boolean = false) =
        "watch/$itemId?resumeMs=$resumeMs&isEpisode=$isEpisode"
}
