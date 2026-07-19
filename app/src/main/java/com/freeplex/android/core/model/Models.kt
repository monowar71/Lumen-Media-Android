package com.freeplex.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProblemDetails(
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
    val errors: Map<String, List<String>>? = null,
)

@Serializable
data class PagedResult<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 50,
    val total: Int = 0,
    val totalPages: Int = 0,
    val nextCursor: String? = null,
)

@Serializable
data class ServerInfo(
    val setupCompleted: Boolean = false,
    val serverName: String? = null,
    val version: String? = null,
)

@Serializable
data class SetupRequest(
    val username: String,
    val password: String,
    val serverName: String = "FreePlex",
)

@Serializable
data class SetupResponse(
    val serverName: String? = null,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val role: String = "User",
    val libraryAccess: JsonElement? = null,
    val allowTranscoding: Boolean = true,
    val maxBitrateKbpsRemote: Int? = null,
    val createdAt: String? = null,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long = 0,
    val tokenType: String = "Bearer",
    val user: UserDto? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long = 0,
)

@Serializable
data class ArtworkSet(
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val thumb: String? = null,
    val banner: String? = null,
)

@Serializable
data class UserData(
    val watched: Boolean? = null,
    val playbackPositionMs: Long? = null,
    val isFavorite: Boolean? = null,
    val unwatchedEpisodeCount: Int? = null,
)

@Serializable
data class MediaItemSummary(
    val id: String,
    val kind: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val runtimeMs: Long? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String>? = null,
    val artwork: ArtworkSet = ArtworkSet(),
    val userData: UserData = UserData(),
    val addedAt: String? = null,
)

@Serializable
data class LibraryDto(
    val id: String,
    val name: String,
    val type: String,
    val paths: List<String>? = null,
    val itemCount: Int = 0,
    val lastScanAt: String? = null,
)

@Serializable
data class CreateLibraryRequest(
    val name: String,
    val type: String,
    val paths: List<String>,
)

@Serializable
data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaItemSummary> = emptyList(),
)

@Serializable
data class HomeResponse(
    val sections: List<HomeSection> = emptyList(),
)

@Serializable
data class SearchResponse(
    val movies: List<MediaItemSummary> = emptyList(),
    val series: List<MediaItemSummary> = emptyList(),
    val episodes: List<EpisodeSummary> = emptyList(),
)

@Serializable
data class Person(
    val name: String,
    val role: String? = null,
    val type: String? = null,
    val order: Int? = null,
    val thumb: String? = null,
)

@Serializable
data class MediaStream(
    val id: String,
    val kind: String,
    val index: Int = 0,
    val codec: String = "",
    val language: String? = null,
    val title: String? = null,
    val isDefault: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
    val channels: Int? = null,
    val isExternal: Boolean? = null,
    val format: String? = null,
)

@Serializable
data class MediaSource(
    val id: String,
    val container: String = "",
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val overallBitrateKbps: Int = 0,
    val streams: List<MediaStream> = emptyList(),
)

@Serializable
data class MovieDetail(
    val id: String,
    val kind: String = "Movie",
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val runtimeMs: Long? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String>? = null,
    val people: List<Person>? = null,
    val artwork: ArtworkSet = ArtworkSet(),
    val mediaSources: List<MediaSource> = emptyList(),
    val userData: UserData = UserData(),
    val libraryId: String = "",
    val addedAt: String? = null,
)

@Serializable
data class SeriesDetail(
    val id: String,
    val kind: String = "Series",
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String>? = null,
    val people: List<Person>? = null,
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val artwork: ArtworkSet = ArtworkSet(),
    val userData: UserData = UserData(),
    val libraryId: String = "",
    val addedAt: String? = null,
)

@Serializable
data class Season(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int = 0,
    val artwork: ArtworkSet = ArtworkSet(),
)

@Serializable
data class EpisodeSummary(
    val id: String,
    val kind: String = "Episode",
    val seriesId: String = "",
    val seasonId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String? = null,
    val overview: String? = null,
    val runtimeMs: Long? = null,
    val artwork: ArtworkSet = ArtworkSet(),
    val userData: UserData = UserData(),
)

@Serializable
data class EpisodeDetail(
    val id: String,
    val kind: String = "Episode",
    val seriesId: String = "",
    val seasonId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String? = null,
    val overview: String? = null,
    val runtimeMs: Long? = null,
    val artwork: ArtworkSet = ArtworkSet(),
    val userData: UserData = UserData(),
    val mediaSources: List<MediaSource> = emptyList(),
)

@Serializable
data class DeviceProfile(
    val maxResolution: String,
    val maxBitrateKbps: Int,
    val videoCodecs: List<String>,
    val audioCodecs: List<String>,
    val containers: List<String>,
    val subtitleFormats: List<String>,
    val supportsHevc: Boolean,
    val supportsHdr: Boolean,
)

@Serializable
data class PlaybackDecisionRequest(
    val mediaId: String,
    val mediaSourceId: String? = null,
    val mode: String = "auto",
    val qualityId: String? = null,
    val audioStreamId: String? = null,
    val subtitleStreamId: String? = null,
    val resumePositionMs: Long = 0,
    val profile: DeviceProfile,
)

@Serializable
data class QualityOption(
    val id: String,
    val label: String,
    val adaptive: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrateKbps: Int? = null,
)

@Serializable
data class AudioStreamOption(
    val id: String,
    val language: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val isDefault: Boolean? = null,
)

@Serializable
data class SubtitleStreamOption(
    val id: String,
    val language: String? = null,
    val format: String? = null,
    val deliveryUrl: String = "",
)

@Serializable
data class PlaybackDecisionResponse(
    val sessionId: String,
    val method: String,
    val mode: String = "auto",
    val streamUrl: String,
    val container: String = "",
    val startPositionMs: Long? = null,
    val durationMs: Long? = null,
    val selectedQualityId: String = "auto",
    val availableQualities: List<QualityOption> = emptyList(),
    val audioStreams: List<AudioStreamOption> = emptyList(),
    val subtitleStreams: List<SubtitleStreamOption> = emptyList(),
    val expiresAt: String? = null,
    val reason: String? = null,
)

@Serializable
data class SetQualityRequest(
    val qualityId: String,
    val mode: String,
    val resumePositionMs: Long,
)

@Serializable
data class ProgressRequest(
    val positionMs: Long,
    val durationMs: Long,
    val sessionId: String? = null,
    val state: String,
)

@Serializable
data class ProgressResponse(
    val itemId: String,
    val positionMs: Long = 0,
    val watched: Boolean = false,
    val updatedAt: String? = null,
)

@Serializable
data class JobDto(
    val id: String,
    val type: String,
    val state: String,
    val progress: Double = 0.0,
    val message: String? = null,
    val libraryId: String? = null,
    val error: String? = null,
)

@Serializable
data class ServerSettingsDto(
    val serverName: String? = null,
    val metadataLanguage: String? = null,
)
