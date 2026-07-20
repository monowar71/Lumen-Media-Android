package com.lumenmedia.android.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumenmedia.android.BuildConfig
import com.lumenmedia.android.core.util.ConnectionKind
import com.lumenmedia.android.core.util.LocaleHelper
import com.lumenmedia.android.core.util.normalizeBaseUrl
import com.lumenmedia.android.core.util.rewriteLoopbackForEmulator
import com.lumenmedia.android.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("lumenmedia_settings")

/** Library grid sort field; mapped to the server's `sort` query param. */
enum class LibrarySort(val apiSort: String) {
    Title("title"),
    Year("year"),
    Added("added"),
    Rating("rating"),
    Runtime("runtime"),
}

/** Sort direction; mapped to the server's `order` query param. */
enum class LibraryOrder(val apiOrder: String) {
    Asc("asc"),
    Desc("desc"),
}

data class AppSettings(
    val baseUrl: String,
    val lanCapKbps: Int,
    val externalCapKbps: Int,
    val preferredMode: String,
    val librarySort: LibrarySort,
    val libraryOrder: LibraryOrder,
    val libraryInProgressFirst: Boolean,
    /** UI locale tag: `ru` (default) or `en`. */
    val locale: String,
    /** Max local episode cache size in bytes; 0 = unlimited. Default 50 GiB. */
    val maxCacheBytes: Long,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val lanCapKey = intPreferencesKey("lan_cap")
    private val externalCapKey = intPreferencesKey("external_cap")
    private val modeKey = stringPreferencesKey("preferred_mode")
    private val librarySortKey = stringPreferencesKey("library_sort")
    private val libraryOrderKey = stringPreferencesKey("library_order")
    private val libraryInProgressFirstKey = booleanPreferencesKey("library_in_progress_first")
    private val localeKey = stringPreferencesKey("locale")
    private val maxCacheBytesKey = longPreferencesKey("max_cache_bytes")

    private val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = rewriteLoopbackForEmulator(
                prefs[baseUrlKey] ?: BuildConfig.DEFAULT_API_BASE_URL,
            ),
            lanCapKbps = prefs[lanCapKey] ?: 0,
            externalCapKbps = prefs[externalCapKey] ?: 8_000,
            preferredMode = prefs[modeKey] ?: "auto",
            librarySort = prefs[librarySortKey]
                ?.let { stored -> resolveLibrarySort(stored) }
                ?: LibrarySort.Added,
            libraryOrder = prefs[libraryOrderKey]
                ?.let { stored -> LibraryOrder.entries.find { it.name == stored } }
                ?: defaultOrderFor(prefs[librarySortKey]?.let { resolveLibrarySort(it) } ?: LibrarySort.Added),
            libraryInProgressFirst = prefs[libraryInProgressFirstKey] ?: false,
            locale = prefs[localeKey] ?: LocaleHelper.DEFAULT_TAG,
            maxCacheBytes = prefs[maxCacheBytesKey] ?: DEFAULT_MAX_CACHE_BYTES,
        )
    }

    // Eagerly cached in memory so OkHttp interceptors can read settings
    // without blocking a network thread on a DataStore read per request.
    private val cachedSettings: StateFlow<AppSettings?> =
        settingsFlow.stateIn(scope, SharingStarted.Eagerly, null)

    val settings: Flow<AppSettings> = cachedSettings.filterNotNull()

    /**
     * Synchronous accessor for interceptors/authenticator. Falls back to a
     * blocking DataStore read only on the very first call before the cache warms.
     */
    fun currentBaseUrl(): String =
        cachedSettings.value?.baseUrl ?: runBlocking { settingsFlow.first().baseUrl }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[baseUrlKey] = rewriteLoopbackForEmulator(normalizeBaseUrl(url)) }
    }

    suspend fun setLanCap(kbps: Int) {
        context.dataStore.edit { it[lanCapKey] = kbps.coerceAtLeast(0) }
    }

    suspend fun setExternalCap(kbps: Int) {
        context.dataStore.edit { it[externalCapKey] = kbps.coerceAtLeast(0) }
    }

    suspend fun setPreferredMode(mode: String) {
        context.dataStore.edit { it[modeKey] = mode }
    }

    suspend fun setLibrarySort(sort: LibrarySort) {
        context.dataStore.edit { it[librarySortKey] = sort.name }
    }

    suspend fun setLibraryOrder(order: LibraryOrder) {
        context.dataStore.edit { it[libraryOrderKey] = order.name }
    }

    suspend fun setLibraryInProgressFirst(enabled: Boolean) {
        context.dataStore.edit { it[libraryInProgressFirstKey] = enabled }
    }

    suspend fun setLocale(tag: String) {
        val normalized = tag.trim().lowercase().ifBlank { LocaleHelper.DEFAULT_TAG }
        context.dataStore.edit { it[localeKey] = normalized }
        LocaleHelper.apply(normalized)
    }

    suspend fun setMaxCacheBytes(bytes: Long) {
        context.dataStore.edit { it[maxCacheBytesKey] = bytes.coerceAtLeast(0L) }
    }

    fun capFor(settings: AppSettings, kind: ConnectionKind): Int {
        return when (kind) {
            ConnectionKind.External -> if (settings.externalCapKbps > 0) settings.externalCapKbps else 100_000
            ConnectionKind.Lan -> if (settings.lanCapKbps > 0) settings.lanCapKbps else 100_000
        }
    }

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES: Long = 50L * 1024L * 1024L * 1024L

        /** Migrates legacy `Name` enum value to `Title`. */
        fun resolveLibrarySort(stored: String): LibrarySort? =
            when (stored) {
                "Name" -> LibrarySort.Title
                else -> LibrarySort.entries.find { it.name == stored }
            }

        fun defaultOrderFor(sort: LibrarySort): LibraryOrder =
            when (sort) {
                LibrarySort.Title -> LibraryOrder.Asc
                else -> LibraryOrder.Desc
            }
    }
}
