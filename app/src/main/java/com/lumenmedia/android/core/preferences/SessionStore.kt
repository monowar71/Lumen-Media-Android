package com.lumenmedia.android.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val username: String,
    val role: String,
)

data class SavedCredentials(
    val username: String,
    val password: String,
)

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // MasterKey creation and opening EncryptedSharedPreferences are slow, and
    // the first injection happens on the main thread during startup. Defer the
    // work (lazy is synchronized) and warm it from a background thread via
    // warmUp() in LumenMediaApp.
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "lumenmedia_secure_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            context.getSharedPreferences("lumenmedia_session_fallback", Context.MODE_PRIVATE)
        }
    }

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    @Volatile
    private var tokensLoaded = false

    val accessToken: String?
        get() {
            ensureTokensLoaded()
            return cachedAccessToken
        }

    val refreshToken: String?
        get() {
            ensureTokensLoaded()
            return cachedRefreshToken
        }

    /** Opens the encrypted prefs and populates the token cache; call off the main thread. */
    fun warmUp() = ensureTokensLoaded()

    private fun ensureTokensLoaded() {
        if (tokensLoaded) return
        synchronized(this) {
            if (tokensLoaded) return
            cachedAccessToken = prefs.getString(KEY_ACCESS, null)
            cachedRefreshToken = prefs.getString(KEY_REFRESH, null)
            tokensLoaded = true
        }
    }

    private fun setCachedTokens(access: String?, refresh: String?) {
        synchronized(this) {
            cachedAccessToken = access
            cachedRefreshToken = refresh
            tokensLoaded = true
        }
    }

    fun readSession(): AuthSession? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val role = prefs.getString(KEY_ROLE, "User") ?: "User"
        setCachedTokens(access, refresh)
        return AuthSession(access, refresh, userId, username, role)
    }

    fun saveSession(session: AuthSession) {
        setCachedTokens(session.accessToken, session.refreshToken)
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_ROLE, session.role)
            .apply()
    }

    fun updateTokens(access: String, refresh: String) {
        setCachedTokens(access, refresh)
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .apply()
    }

    fun isRememberCredentials(): Boolean = prefs.getBoolean(KEY_REMEMBER, false)

    fun readSavedCredentials(): SavedCredentials? {
        if (!isRememberCredentials()) return null
        val username = prefs.getString(KEY_SAVED_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_SAVED_PASSWORD, null) ?: return null
        if (username.isBlank() || password.isBlank()) return null
        return SavedCredentials(username, password)
    }

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putBoolean(KEY_REMEMBER, true)
            .putString(KEY_SAVED_USERNAME, username)
            .putString(KEY_SAVED_PASSWORD, password)
            .apply()
    }

    fun clearSavedCredentials() {
        prefs.edit()
            .putBoolean(KEY_REMEMBER, false)
            .remove(KEY_SAVED_USERNAME)
            .remove(KEY_SAVED_PASSWORD)
            .apply()
    }

    /** Clears JWT session only — remembered login/password are kept. */
    fun clear() {
        setCachedTokens(null, null)
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
        private const val KEY_REMEMBER = "remember_credentials"
        private const val KEY_SAVED_USERNAME = "saved_username"
        private const val KEY_SAVED_PASSWORD = "saved_password"
    }
}
