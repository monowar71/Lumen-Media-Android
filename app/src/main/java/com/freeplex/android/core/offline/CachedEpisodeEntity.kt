package com.freeplex.android.core.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CachedEpisodeStatus {
    Queued,
    Downloading,
    Ready,
    Failed,
}

@Entity(
    tableName = "cached_episodes",
    indices = [
        Index("seriesId"),
        Index("seasonId"),
        Index("status"),
    ],
)
data class CachedEpisodeEntity(
    @PrimaryKey val episodeId: String,
    val seriesId: String,
    val seasonId: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val status: CachedEpisodeStatus,
    val bytesDownloaded: Long = 0L,
    val bytesTotal: Long = 0L,
    /** Absolute path when Ready; temporary path while downloading. */
    val localPath: String? = null,
    val container: String? = null,
    val errorMessage: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

data class OfflineEpisodeState(
    val episodeId: String,
    val seriesId: String,
    val seasonId: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val status: CachedEpisodeStatus,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val localPath: String?,
    val errorMessage: String?,
) {
    val progress: Float
        get() = when {
            status == CachedEpisodeStatus.Ready -> 1f
            bytesTotal > 0L -> (bytesDownloaded.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val displayTitle: String
        get() = buildString {
            append(seriesTitle)
            append(" · S")
            append(seasonNumber)
            append('E')
            append(episodeNumber)
            episodeTitle?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
}

fun CachedEpisodeEntity.toState(): OfflineEpisodeState = OfflineEpisodeState(
    episodeId = episodeId,
    seriesId = seriesId,
    seasonId = seasonId,
    seriesTitle = seriesTitle,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeTitle = episodeTitle,
    status = status,
    bytesDownloaded = bytesDownloaded,
    bytesTotal = bytesTotal,
    localPath = localPath,
    errorMessage = errorMessage,
)

data class OfflineCacheSummary(
    val entries: List<OfflineEpisodeState> = emptyList(),
    val readyBytes: Long = 0L,
    val readyCount: Int = 0,
    val activeCount: Int = 0,
)
