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
import com.lumenmedia.android.core.model.RefreshRequest
import com.lumenmedia.android.core.model.RefreshResponse
import com.lumenmedia.android.core.model.SearchResponse
import com.lumenmedia.android.core.model.Season
import com.lumenmedia.android.core.model.ServerInfo
import com.lumenmedia.android.core.model.ServerSettingsDto
import com.lumenmedia.android.core.model.SetQualityRequest
import com.lumenmedia.android.core.model.SetupRequest
import com.lumenmedia.android.core.model.SetupResponse
import com.lumenmedia.android.core.model.TokenResponse
import com.lumenmedia.android.core.model.UserDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface LumenMediaApi {
    @GET("api/v1/server/info")
    suspend fun serverInfo(): ServerInfo

    @POST("api/v1/setup")
    suspend fun setup(@Body body: SetupRequest): SetupResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @POST("api/v1/auth/logout")
    suspend fun logout()

    @GET("api/v1/auth/me")
    suspend fun me(): UserDto

    @GET("api/v1/home")
    suspend fun home(): HomeResponse

    @GET("api/v1/libraries")
    suspend fun libraries(): List<LibraryDto>

    @POST("api/v1/libraries")
    suspend fun createLibrary(@Body body: CreateLibraryRequest): LibraryDto

    @DELETE("api/v1/libraries/{id}")
    suspend fun deleteLibrary(@Path("id") id: String)

    @POST("api/v1/libraries/{id}/scan")
    suspend fun scanLibrary(@Path("id") id: String): JobDto

    @GET("api/v1/libraries/{id}/items")
    suspend fun libraryItems(
        @Path("id") id: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("sort") sort: String? = "added",
        @Query("order") order: String? = "desc",
        @Query("watched") watched: Boolean? = null,
        @Query("q") q: String? = null,
    ): PagedResult<MediaItemSummary>

    @GET("api/v1/items/{id}")
    suspend fun item(@Path("id") id: String): JsonElement

    @GET("api/v1/series/{id}/seasons")
    suspend fun seasons(@Path("id") id: String): PagedResult<Season>

    @GET("api/v1/seasons/{id}/episodes")
    suspend fun episodes(@Path("id") id: String): PagedResult<EpisodeSummary>

    @GET("api/v1/episodes/{id}")
    suspend fun episode(@Path("id") id: String): EpisodeDetail

    @GET("api/v1/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("limit") limit: Int = 20,
    ): SearchResponse

    @POST("api/v1/playback/decision")
    suspend fun playbackDecision(@Body body: PlaybackDecisionRequest): PlaybackDecisionResponse

    @POST("api/v1/playback/{sessionId}/set-quality")
    suspend fun setQuality(
        @Path("sessionId") sessionId: String,
        @Body body: SetQualityRequest,
    ): PlaybackDecisionResponse

    @POST("api/v1/playback/{sessionId}/seek")
    suspend fun seekSession(
        @Path("sessionId") sessionId: String,
        @Body body: Map<String, Long>,
    ): PlaybackDecisionResponse

    @POST("api/v1/playback/{sessionId}/ping")
    suspend fun pingSession(@Path("sessionId") sessionId: String)

    @POST("api/v1/playback/{sessionId}/stop")
    suspend fun stopSession(@Path("sessionId") sessionId: String)

    @PUT("api/v1/progress/{itemId}")
    suspend fun putProgress(
        @Path("itemId") itemId: String,
        @Body body: ProgressRequest,
    ): ProgressResponse

    @GET("api/v1/progress/{itemId}")
    suspend fun getProgress(@Path("itemId") itemId: String): ProgressResponse

    @GET("api/v1/settings")
    suspend fun serverSettings(): ServerSettingsDto

    @PUT("api/v1/settings")
    suspend fun putServerSettings(@Body body: ServerSettingsDto): ServerSettingsDto

    @GET("api/v1/jobs")
    suspend fun jobs(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): PagedResult<JobDto>
}
