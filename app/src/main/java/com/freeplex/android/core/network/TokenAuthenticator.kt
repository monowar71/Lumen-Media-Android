package com.freeplex.android.core.network

import com.freeplex.android.core.model.RefreshRequest
import com.freeplex.android.core.preferences.SessionStore
import com.freeplex.android.core.preferences.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val refresh = sessionStore.refreshToken ?: return null
        return try {
            val baseUrl = runBlocking { settingsRepository.settings.first().baseUrl }
            val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
            val req = Request.Builder()
                .url("$baseUrl/api/v1/auth/refresh")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val client = OkHttpClient()
            client.newCall(req).execute().use { refreshResponse ->
                if (!refreshResponse.isSuccessful) {
                    sessionStore.clear()
                    return null
                }
                val payload = refreshResponse.body?.string().orEmpty()
                val tokens = json.decodeFromString(com.freeplex.android.core.model.RefreshResponse.serializer(), payload)
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
