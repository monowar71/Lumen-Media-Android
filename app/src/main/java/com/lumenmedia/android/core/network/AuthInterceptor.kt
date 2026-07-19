package com.lumenmedia.android.core.network

import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import com.lumenmedia.android.core.util.normalizeBaseUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Runs after DynamicBaseUrlInterceptor, so server requests already
        // carry the server host. Never attach the token to third-party hosts
        // (TMDB cast photos etc.) — that would leak credentials.
        val serverHost = normalizeBaseUrl(settingsRepository.currentBaseUrl()).toHttpUrl().host
        if (original.url.host != serverHost) return chain.proceed(original)
        val token = sessionStore.accessToken
        if (token.isNullOrBlank()) return chain.proceed(original)
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}
