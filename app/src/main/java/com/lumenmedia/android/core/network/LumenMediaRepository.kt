package com.lumenmedia.android.core.network

import com.lumenmedia.android.core.model.CreateLibraryRequest
import com.lumenmedia.android.core.model.EpisodeDetail
import com.lumenmedia.android.core.model.EpisodeSummary
import com.lumenmedia.android.core.model.HomeResponse
import com.lumenmedia.android.core.model.JobDto
import com.lumenmedia.android.core.model.LibraryDto
import com.lumenmedia.android.core.model.LoginRequest
import com.lumenmedia.android.core.model.MediaItemSummary
import com.lumenmedia.android.core.model.MovieDetail
import com.lumenmedia.android.core.model.PagedResult
import com.lumenmedia.android.core.model.PlaybackDecisionRequest
import com.lumenmedia.android.core.model.PlaybackDecisionResponse
import com.lumenmedia.android.core.model.ProgressRequest
import com.lumenmedia.android.core.model.ProgressResponse
import com.lumenmedia.android.core.model.SearchResponse
import com.lumenmedia.android.core.model.Season
import com.lumenmedia.android.core.model.ServerInfo
import com.lumenmedia.android.core.model.ServerSettingsDto
import com.lumenmedia.android.core.model.SetQualityRequest
import com.lumenmedia.android.core.model.SetupRequest
import com.lumenmedia.android.core.model.SeriesDetail
import com.lumenmedia.android.core.model.TokenResponse
import com.lumenmedia.android.core.model.UserDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton

sealed class ItemDetailResult {
    data class Movie(val value: MovieDetail) : ItemDetailResult()
    data class Series(val value: SeriesDetail) : ItemDetailResult()
}

@Singleton
class LumenMediaRepository @Inject constructor(
    private val api: LumenMediaApi,
    private val json: Json,
) {
    suspend fun serverInfo(): ServerInfo = api.serverInfo()
    suspend fun setup(body: SetupRequest) = api.setup(body)
    suspend fun login(username: String, password: String): TokenResponse =
        api.login(LoginRequest(username, password))
    suspend fun logout() = runCatching { api.logout() }
    suspend fun me(): UserDto = api.me()
    suspend fun home(): HomeResponse = api.home()
    suspend fun libraries(): List<LibraryDto> = api.libraries()
    suspend fun createLibrary(body: CreateLibraryRequest) = api.createLibrary(body)
    suspend fun deleteLibrary(id: String) = api.deleteLibrary(id)
    suspend fun scanLibrary(id: String): JobDto = api.scanLibrary(id)
    suspend fun libraryItems(
        id: String,
        page: Int,
        sort: String = "added",
        order: String = "desc",
        watched: Boolean? = null,
        q: String? = null,
        genre: String? = null,
        year: Int? = null,
    ): PagedResult<MediaItemSummary> =
        api.libraryItems(
            id,
            page = page,
            sort = sort,
            order = order,
            watched = watched,
            q = q,
            genre = genre,
            year = year,
        )
    suspend fun itemDetail(id: String): ItemDetailResult {
        val element = api.item(id)
        val kind = element.jsonObject["kind"]?.toString()?.trim('"')
        return if (kind == "Series") {
            ItemDetailResult.Series(json.decodeFromJsonElement(SeriesDetail.serializer(), element))
        } else {
            ItemDetailResult.Movie(json.decodeFromJsonElement(MovieDetail.serializer(), element))
        }
    }
    suspend fun seasons(seriesId: String): List<Season> = api.seasons(seriesId).items
    suspend fun episodes(seasonId: String): List<EpisodeSummary> = api.episodes(seasonId).items
    suspend fun episode(id: String): EpisodeDetail = api.episode(id)
    suspend fun search(q: String): SearchResponse = api.search(q)
    suspend fun playbackDecision(body: PlaybackDecisionRequest): PlaybackDecisionResponse =
        api.playbackDecision(body)
    suspend fun setQuality(sessionId: String, body: SetQualityRequest) = api.setQuality(sessionId, body)
    suspend fun seekSession(sessionId: String, positionMs: Long) =
        api.seekSession(sessionId, mapOf("positionMs" to positionMs))
    suspend fun pingSession(sessionId: String) = runCatching { api.pingSession(sessionId) }
    suspend fun stopSession(sessionId: String) = runCatching { api.stopSession(sessionId) }
    suspend fun putProgress(itemId: String, body: ProgressRequest) = api.putProgress(itemId, body)
    suspend fun getProgress(itemId: String) = api.getProgress(itemId)
    suspend fun serverSettings(): ServerSettingsDto = api.serverSettings()
    suspend fun jobs(): List<JobDto> = api.jobs().items
}

private val kotlinx.serialization.json.JsonElement.jsonObject
    get() = this as kotlinx.serialization.json.JsonObject
