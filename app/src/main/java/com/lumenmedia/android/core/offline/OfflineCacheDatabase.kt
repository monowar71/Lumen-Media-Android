package com.lumenmedia.android.core.offline

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class OfflineConverters {
    @TypeConverter
    fun fromStatus(value: CachedEpisodeStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): CachedEpisodeStatus = CachedEpisodeStatus.valueOf(value)
}

@Database(
    entities = [CachedEpisodeEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(OfflineConverters::class)
abstract class OfflineCacheDatabase : RoomDatabase() {
    abstract fun offlineCacheDao(): OfflineCacheDao
}
