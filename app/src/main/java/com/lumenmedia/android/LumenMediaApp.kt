package com.lumenmedia.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lumenmedia.android.core.offline.OfflineDownloadManager
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.di.ApplicationScope
import com.lumenmedia.android.di.ImageLoaderEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LumenMediaApp : Application(), ImageLoaderFactory {
    @Inject lateinit var sessionStore: SessionStore

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
    }

    override fun newImageLoader(): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        return ImageLoader.Builder(this)
            .okHttpClient(entryPoint.okHttpClient())
            .crossfade(true)
            .build()
    }
}
