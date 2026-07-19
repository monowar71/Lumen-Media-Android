package com.freeplex.android.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freeplex.android.BuildConfig
import com.freeplex.android.core.util.ConnectionKind
import com.freeplex.android.core.util.normalizeBaseUrl
import com.freeplex.android.core.util.rewriteLoopbackForEmulator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("freeplex_settings")

data class AppSettings(
    val baseUrl: String,
    val lanCapKbps: Int,
    val externalCapKbps: Int,
    val preferredMode: String,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val lanCapKey = intPreferencesKey("lan_cap")
    private val externalCapKey = intPreferencesKey("external_cap")
    private val modeKey = stringPreferencesKey("preferred_mode")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = rewriteLoopbackForEmulator(
                prefs[baseUrlKey] ?: BuildConfig.DEFAULT_API_BASE_URL,
            ),
            lanCapKbps = prefs[lanCapKey] ?: 0,
            externalCapKbps = prefs[externalCapKey] ?: 8_000,
            preferredMode = prefs[modeKey] ?: "auto",
        )
    }

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

    fun capFor(settings: AppSettings, kind: ConnectionKind): Int {
        return when (kind) {
            ConnectionKind.External -> if (settings.externalCapKbps > 0) settings.externalCapKbps else 100_000
            ConnectionKind.Lan -> if (settings.lanCapKbps > 0) settings.lanCapKbps else 100_000
        }
    }
}
