package com.freeplex.android.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freeplex.android.BuildConfig
import com.freeplex.android.core.util.ConnectionKind
import com.freeplex.android.core.util.normalizeBaseUrl
import com.freeplex.android.core.util.rewriteLoopbackForEmulator
import com.freeplex.android.di.ApplicationScope
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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("freeplex_settings")

/** Library grid sort options; mapped to the server's `sort`/`order` query params. */
enum class LibrarySort(val apiSort: String, val apiOrder: String) {
    /** Alphabetical by title. */
    Name("title", "asc"),

    /** Newest additions first. */
    Added("added", "desc"),
}

data class AppSettings(
    val baseUrl: String,
    val lanCapKbps: Int,
    val externalCapKbps: Int,
    val preferredMode: String,
    val librarySort: LibrarySort,
    val libraryInProgressFirst: Boolean,
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
    private val libraryInProgressFirstKey = booleanPreferencesKey("library_in_progress_first")

    private val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = rewriteLoopbackForEmulator(
                prefs[baseUrlKey] ?: BuildConfig.DEFAULT_API_BASE_URL,
            ),
            lanCapKbps = prefs[lanCapKey] ?: 0,
            externalCapKbps = prefs[externalCapKey] ?: 8_000,
            preferredMode = prefs[modeKey] ?: "auto",
            librarySort = prefs[librarySortKey]
                ?.let { stored -> LibrarySort.entries.find { it.name == stored } }
                ?: LibrarySort.Added,
            libraryInProgressFirst = prefs[libraryInProgressFirstKey] ?: false,
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

    suspend fun setLibraryInProgressFirst(enabled: Boolean) {
        context.dataStore.edit { it[libraryInProgressFirstKey] = enabled }
    }

    fun capFor(settings: AppSettings, kind: ConnectionKind): Int {
        return when (kind) {
            ConnectionKind.External -> if (settings.externalCapKbps > 0) settings.externalCapKbps else 100_000
            ConnectionKind.Lan -> if (settings.lanCapKbps > 0) settings.lanCapKbps else 100_000
        }
    }
}
