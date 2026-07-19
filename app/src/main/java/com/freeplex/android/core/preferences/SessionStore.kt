package com.freeplex.android.core.preferences

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
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "freeplex_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        context.getSharedPreferences("freeplex_session_fallback", Context.MODE_PRIVATE)
    }

    @Volatile
    var accessToken: String? = prefs.getString(KEY_ACCESS, null)
        private set

    @Volatile
    var refreshToken: String? = prefs.getString(KEY_REFRESH, null)
        private set

    fun readSession(): AuthSession? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val role = prefs.getString(KEY_ROLE, "User") ?: "User"
        accessToken = access
        refreshToken = refresh
        return AuthSession(access, refresh, userId, username, role)
    }

    fun saveSession(session: AuthSession) {
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_ROLE, session.role)
            .apply()
    }

    fun updateTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
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
        accessToken = null
        refreshToken = null
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
