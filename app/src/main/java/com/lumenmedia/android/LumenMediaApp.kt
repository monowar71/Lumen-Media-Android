package com.lumenmedia.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.lumenmedia.android.core.offline.OfflineDownloadManager
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import com.lumenmedia.android.core.util.LocaleHelper
import com.lumenmedia.android.di.ApplicationScope
import com.lumenmedia.android.di.ImageLoaderEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class LumenMediaApp : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var sessionStore: SessionStore

    @Inject lateinit var settingsRepository: SettingsRepository

    /** Eagerly created so interrupted downloads resume after process start. */
    @Inject lateinit var offlineDownloadManager: OfflineDownloadManager

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Touch singleton so the download worker starts even before Settings/Details.
        offlineDownloadManager.summary
        // EncryptedSharedPreferences creation is slow (MasterKey); pay the cost
        // off the main thread before the first request needs a token.
        appScope.launch { sessionStore.warmUp() }
        appScope.launch {
            val locale = settingsRepository.settings.first().locale
            LocaleHelper.apply(locale)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        val okHttpClient = entryPoint.okHttpClient()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .crossfade(true)
            .build()
    }
}
