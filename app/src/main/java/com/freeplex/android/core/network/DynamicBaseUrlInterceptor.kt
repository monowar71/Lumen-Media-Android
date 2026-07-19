package com.freeplex.android.core.network

import com.freeplex.android.core.preferences.SettingsRepository
import com.freeplex.android.core.util.normalizeBaseUrl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val base = runBlocking { settingsRepository.settings.first().baseUrl }
        val normalized = normalizeBaseUrl(base).toHttpUrl()
        val newUrl = original.url.newBuilder()
            .scheme(normalized.scheme)
            .host(normalized.host)
            .port(normalized.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
