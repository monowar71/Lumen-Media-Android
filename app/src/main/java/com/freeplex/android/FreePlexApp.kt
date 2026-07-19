package com.freeplex.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.freeplex.android.core.preferences.SessionStore
import com.freeplex.android.di.ApplicationScope
import com.freeplex.android.di.ImageLoaderEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class FreePlexApp : Application(), ImageLoaderFactory {
    @Inject lateinit var sessionStore: SessionStore

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
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
