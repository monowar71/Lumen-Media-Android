package com.lumenmedia.android.core.network

import com.lumenmedia.android.core.model.RefreshRequest
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : Authenticator {
    // Serializes refresh so parallel 401s don't race with the same refresh
    // token (server-side rotation would log the "losers" out).
    private val refreshLock = Any()

    // Dedicated client: routing the refresh call through the main client
    // would recurse into this authenticator on another 401.
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        synchronized(refreshLock) {
            // Another thread may have refreshed while we waited for the lock;
            // if so, just retry with the fresh token instead of refreshing again.
            val currentAccess = sessionStore.accessToken
            val failedAuth = response.request.header("Authorization")
            if (!currentAccess.isNullOrBlank() && failedAuth != "Bearer $currentAccess") {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccess")
                    .build()
            }
            val refresh = sessionStore.refreshToken ?: return null
            return try {
                val baseUrl = settingsRepository.currentBaseUrl()
                val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/auth/refresh")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                refreshClient.newCall(req).execute().use { refreshResponse ->
                    if (!refreshResponse.isSuccessful) {
                        sessionStore.clear()
                        return null
                    }
                    val payload = refreshResponse.body?.string().orEmpty()
                    val tokens = json.decodeFromString(
                        com.lumenmedia.android.core.model.RefreshResponse.serializer(),
                        payload,
                    )
                    sessionStore.updateTokens(tokens.accessToken, tokens.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${tokens.accessToken}")
                        .build()
                }
            } catch (_: Exception) {
                sessionStore.clear()
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
