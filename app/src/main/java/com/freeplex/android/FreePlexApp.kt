package com.freeplex.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.freeplex.android.di.ImageLoaderEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FreePlexApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
        return ImageLoader.Builder(this)
            .okHttpClient(entryPoint.okHttpClient())
            .crossfade(true)
            .build()
    }
}
