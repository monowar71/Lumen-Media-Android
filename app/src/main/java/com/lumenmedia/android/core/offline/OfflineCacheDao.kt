package com.lumenmedia.android.core.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineCacheDao {
    @Query("SELECT * FROM cached_episodes ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<CachedEpisodeEntity>>

    @Query("SELECT * FROM cached_episodes WHERE episodeId = :episodeId LIMIT 1")
    fun observeEpisode(episodeId: String): Flow<CachedEpisodeEntity?>

    @Query("SELECT * FROM cached_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun get(episodeId: String): CachedEpisodeEntity?

    @Query("SELECT * FROM cached_episodes WHERE status = :status ORDER BY createdAtEpochMs ASC")
    suspend fun listByStatus(status: CachedEpisodeStatus): List<CachedEpisodeEntity>

    @Query(
        "SELECT * FROM cached_episodes WHERE status IN (:queued, :downloading) " +
            "ORDER BY createdAtEpochMs ASC",
    )
    suspend fun listActive(
        queued: CachedEpisodeStatus = CachedEpisodeStatus.Queued,
        downloading: CachedEpisodeStatus = CachedEpisodeStatus.Downloading,
    ): List<CachedEpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedEpisodeEntity)

    @Update
    suspend fun update(entity: CachedEpisodeEntity)

    @Query("DELETE FROM cached_episodes WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)

    @Query("DELETE FROM cached_episodes")
    suspend fun deleteAll()

    @Query(
        "SELECT COALESCE(SUM(bytesTotal), 0) FROM cached_episodes " +
            "WHERE status = :ready",
    )
    suspend fun readyBytes(ready: CachedEpisodeStatus = CachedEpisodeStatus.Ready): Long
}
