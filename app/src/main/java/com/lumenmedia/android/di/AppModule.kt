package com.lumenmedia.android.di

import android.content.Context
import androidx.room.Room
import com.lumenmedia.android.BuildConfig
import com.lumenmedia.android.core.network.AuthInterceptor
import com.lumenmedia.android.core.network.DynamicBaseUrlInterceptor
import com.lumenmedia.android.core.network.LumenMediaApi
import com.lumenmedia.android.core.network.TokenAuthenticator
import com.lumenmedia.android.core.offline.OfflineCacheDao
import com.lumenmedia.android.core.offline.OfflineCacheDatabase
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageLoaderEntryPoint {
    fun okHttpClient(): OkHttpClient
}

/** Scope that outlives ViewModels — for work that must survive screen teardown. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** OkHttp client tuned for long-running media downloads (no short read timeout). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: DynamicBaseUrlInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(authenticator)
            .build()
    }

    @Provides
    @Singleton
    @DownloadHttpClient
    fun provideDownloadOkHttp(apiClient: OkHttpClient): OkHttpClient =
        apiClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.DEFAULT_API_BASE_URL + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): LumenMediaApi = retrofit.create(LumenMediaApi::class.java)

    @Provides
    @Singleton
    fun provideOfflineDatabase(@ApplicationContext context: Context): OfflineCacheDatabase =
        Room.databaseBuilder(context, OfflineCacheDatabase::class.java, "offline_cache.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideOfflineCacheDao(db: OfflineCacheDatabase): OfflineCacheDao = db.offlineCacheDao()
}
