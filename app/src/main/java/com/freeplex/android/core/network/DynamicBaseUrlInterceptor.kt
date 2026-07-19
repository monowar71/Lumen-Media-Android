package com.freeplex.android.core.network

import com.freeplex.android.BuildConfig
import com.freeplex.android.core.preferences.SettingsRepository
import com.freeplex.android.core.util.normalizeBaseUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : Interceptor {
    private val defaultHost = normalizeBaseUrl(BuildConfig.DEFAULT_API_BASE_URL).toHttpUrl().host

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val base = settingsRepository.currentBaseUrl()
        val normalized = normalizeBaseUrl(base).toHttpUrl()
        // Only server calls get rehosted: Coil also loads third-party images
        // (e.g. TMDB cast photos) through this client, and those must keep
        // their original host.
        val host = original.url.host
        if (host != defaultHost && host != normalized.host) return chain.proceed(original)
        val newUrl = original.url.newBuilder()
            .scheme(normalized.scheme)
            .host(normalized.host)
            .port(normalized.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
