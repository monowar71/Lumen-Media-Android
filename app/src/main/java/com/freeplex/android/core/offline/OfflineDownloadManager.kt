package com.freeplex.android.core.offline

import com.freeplex.android.BuildConfig
import com.freeplex.android.core.model.EpisodeSummary
import com.freeplex.android.core.preferences.SettingsRepository
import com.freeplex.android.core.util.normalizeBaseUrl
import com.freeplex.android.di.ApplicationScope
import com.freeplex.android.di.DownloadHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

data class OfflineEnqueueRequest(
    val episodeId: String,
    val seriesId: String,
    val seasonId: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
)

/**
 * Queues and downloads original media files via `GET /api/v1/items/{id}/download`.
 *
 * One download at a time to keep TV devices and LAN links usable for streaming.
 */
@Singleton
class OfflineDownloadManager @Inject constructor(
    private val dao: OfflineCacheDao,
    private val files: OfflineFileStore,
    @DownloadHttpClient private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val cancelFlags = mutableMapOf<String, AtomicBoolean>()
    private val cancelMutex = Mutex()
    private var workerJob: Job? = null

    val entries: StateFlow<List<OfflineEpisodeState>> = dao.observeAll()
        .map { list -> list.map { it.toState() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val summary: StateFlow<OfflineCacheSummary> = entries
        .map { list ->
            OfflineCacheSummary(
                entries = list,
                readyBytes = list.filter { it.status == CachedEpisodeStatus.Ready }
                    .sumOf { maxOf(it.bytesTotal, it.bytesDownloaded) },
                readyCount = list.count { it.status == CachedEpisodeStatus.Ready },
                activeCount = list.count {
                    it.status == CachedEpisodeStatus.Queued ||
                        it.status == CachedEpisodeStatus.Downloading
                },
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, OfflineCacheSummary())

    init {
        workerJob = scope.launch { workerLoop() }
        scope.launch { recoverInterruptedDownloads() }
    }

    fun observeEpisode(episodeId: String): Flow<OfflineEpisodeState?> =
        dao.observeEpisode(episodeId).map { it?.toState() }

    fun stateFor(episodeId: String): OfflineEpisodeState? =
        entries.value.firstOrNull { it.episodeId == episodeId }

    fun readyFile(episodeId: String): File? {
        val entity = entries.value.firstOrNull { it.episodeId == episodeId }
            ?: return files.findReadyFile(episodeId)
        if (entity.status != CachedEpisodeStatus.Ready) return null
        val path = entity.localPath ?: return files.findReadyFile(episodeId)
        val file = File(path)
        return file.takeIf { it.isFile && it.length() > 0L } ?: files.findReadyFile(episodeId)
    }

    suspend fun enqueueEpisode(request: OfflineEnqueueRequest) {
        val existing = dao.get(request.episodeId)
        if (existing?.status == CachedEpisodeStatus.Ready ||
            existing?.status == CachedEpisodeStatus.Downloading ||
            existing?.status == CachedEpisodeStatus.Queued
        ) {
            return
        }
        enforceBudgetIfNeeded(estimatedNewBytes = 0L)
        val now = System.currentTimeMillis()
        dao.upsert(
            CachedEpisodeEntity(
                episodeId = request.episodeId,
                seriesId = request.seriesId,
                seasonId = request.seasonId,
                seriesTitle = request.seriesTitle,
                seasonNumber = request.seasonNumber,
                episodeNumber = request.episodeNumber,
                episodeTitle = request.episodeTitle,
                status = CachedEpisodeStatus.Queued,
                bytesDownloaded = 0L,
                bytesTotal = 0L,
                localPath = null,
                container = null,
                errorMessage = null,
                updatedAtEpochMs = now,
                createdAtEpochMs = existing?.createdAtEpochMs ?: now,
            ),
        )
        wake.trySend(Unit)
    }

    suspend fun enqueueEpisodes(requests: List<OfflineEnqueueRequest>) {
        requests.forEach { enqueueEpisode(it) }
    }

    suspend fun enqueueSeason(
        seriesId: String,
        seriesTitle: String,
        seasonId: String,
        episodes: List<EpisodeSummary>,
    ) {
        enqueueEpisodes(
            episodes.map { ep ->
                OfflineEnqueueRequest(
                    episodeId = ep.id,
                    seriesId = seriesId.ifBlank { ep.seriesId },
                    seasonId = seasonId.ifBlank { ep.seasonId },
                    seriesTitle = seriesTitle,
                    seasonNumber = ep.seasonNumber,
                    episodeNumber = ep.episodeNumber,
                    episodeTitle = ep.title,
                )
            },
        )
    }

    suspend fun cancel(episodeId: String) {
        cancelMutex.withLock {
            cancelFlags.getOrPut(episodeId) { AtomicBoolean(false) }.set(true)
        }
        val current = dao.get(episodeId) ?: return
        when (current.status) {
            CachedEpisodeStatus.Queued -> remove(episodeId)
            CachedEpisodeStatus.Downloading -> {
                // Worker will flip to Failed/remove after noticing the flag.
            }
            CachedEpisodeStatus.Ready, CachedEpisodeStatus.Failed -> Unit
        }
    }

    suspend fun remove(episodeId: String) {
        cancelMutex.withLock {
            cancelFlags.getOrPut(episodeId) { AtomicBoolean(false) }.set(true)
        }
        files.deleteEpisodeFiles(episodeId)
        dao.delete(episodeId)
        cancelMutex.withLock { cancelFlags.remove(episodeId) }
    }

    suspend fun clearAll() {
        val all = dao.observeAll().first()
        all.forEach { cancelMutex.withLock { cancelFlags.getOrPut(it.episodeId) { AtomicBoolean(false) }.set(true) } }
        files.deleteAll()
        dao.deleteAll()
        cancelMutex.withLock { cancelFlags.clear() }
    }

    suspend fun removeFailed() {
        dao.listByStatus(CachedEpisodeStatus.Failed).forEach { remove(it.episodeId) }
    }

    private suspend fun recoverInterruptedDownloads() {
        dao.listByStatus(CachedEpisodeStatus.Downloading).forEach { entity ->
            files.deleteEpisodeFiles(entity.episodeId)
            dao.update(
                entity.copy(
                    status = CachedEpisodeStatus.Queued,
                    bytesDownloaded = 0L,
                    bytesTotal = 0L,
                    localPath = null,
                    errorMessage = null,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        wake.trySend(Unit)
    }

    private suspend fun workerLoop() {
        while (true) {
            val next = dao.listByStatus(CachedEpisodeStatus.Queued).firstOrNull()
            if (next == null) {
                wake.receive()
                continue
            }
            downloadOne(next)
        }
    }

    private suspend fun downloadOne(entity: CachedEpisodeEntity) {
        val cancelFlag = cancelMutex.withLock {
            cancelFlags.getOrPut(entity.episodeId) { AtomicBoolean(false) }.also { it.set(false) }
        }
        val partial = files.partialFile(entity.episodeId)
        partial.parentFile?.mkdirs()
        if (partial.exists()) partial.delete()

        dao.update(
            entity.copy(
                status = CachedEpisodeStatus.Downloading,
                bytesDownloaded = 0L,
                bytesTotal = 0L,
                localPath = partial.absolutePath,
                errorMessage = null,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        try {
            enforceBudgetIfNeeded(estimatedNewBytes = 0L)
            val baseUrl = normalizeBaseUrl(settingsRepository.settings.first().baseUrl)
                .ifBlank { normalizeBaseUrl(BuildConfig.DEFAULT_API_BASE_URL) }
            val url = "$baseUrl/api/v1/items/${entity.episodeId}/download"
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val total = body.contentLength().takeIf { it > 0 } ?: 0L
                if (total > 0L) {
                    enforceBudgetIfNeeded(estimatedNewBytes = total)
                }
                val extension = guessExtension(
                    contentType = body.contentType()?.toString(),
                    contentDisposition = response.header("Content-Disposition"),
                )
                var downloaded = 0L
                var lastPersistAt = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER)
                        while (true) {
                            if (cancelFlag.get()) throw DownloadCancelledException()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastPersistAt >= PROGRESS_THROTTLE_MS) {
                                lastPersistAt = now
                                dao.update(
                                    entity.copy(
                                        status = CachedEpisodeStatus.Downloading,
                                        bytesDownloaded = downloaded,
                                        bytesTotal = total,
                                        localPath = partial.absolutePath,
                                        updatedAtEpochMs = now,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (cancelFlag.get()) throw DownloadCancelledException()
                val ready = files.readyFile(entity.episodeId, extension)
                if (ready.exists()) ready.delete()
                if (!partial.renameTo(ready)) {
                    partial.copyTo(ready, overwrite = true)
                    partial.delete()
                }
                dao.update(
                    entity.copy(
                        status = CachedEpisodeStatus.Ready,
                        bytesDownloaded = downloaded,
                        bytesTotal = if (total > 0L) total else downloaded,
                        localPath = ready.absolutePath,
                        container = extension,
                        errorMessage = null,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                enforceBudgetIfNeeded(estimatedNewBytes = 0L)
            }
        } catch (_: DownloadCancelledException) {
            files.deleteEpisodeFiles(entity.episodeId)
            dao.delete(entity.episodeId)
        } catch (err: Exception) {
            files.deleteEpisodeFiles(entity.episodeId)
            dao.update(
                entity.copy(
                    status = CachedEpisodeStatus.Failed,
                    bytesDownloaded = 0L,
                    localPath = null,
                    errorMessage = err.message ?: "Download failed",
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        } finally {
            cancelMutex.withLock { cancelFlags.remove(entity.episodeId) }
        }
    }

    /**
     * Evict oldest Ready entries until under the configured max cache size.
     * [estimatedNewBytes] reserves room for an in-flight download.
     */
    private suspend fun enforceBudgetIfNeeded(estimatedNewBytes: Long) {
        val maxBytes = settingsRepository.settings.first().maxCacheBytes
        if (maxBytes <= 0L) return
        var used = dao.readyBytes() + estimatedNewBytes
        if (used <= maxBytes) return
        val ready = dao.listByStatus(CachedEpisodeStatus.Ready)
            .sortedBy { it.updatedAtEpochMs }
        for (entry in ready) {
            if (used <= maxBytes) break
            val size = maxOf(entry.bytesTotal, entry.bytesDownloaded)
            files.deleteEpisodeFiles(entry.episodeId)
            dao.delete(entry.episodeId)
            used -= size
        }
    }

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val PROGRESS_THROTTLE_MS = 500L

        internal fun guessExtension(contentType: String?, contentDisposition: String?): String {
            filenameFromDisposition(contentDisposition)?.substringAfterLast('.', missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() && it.length <= 8 }
                ?.lowercase()
                ?.let { return it }
            val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when {
                type.contains("matroska") || type.contains("x-matroska") -> "mkv"
                type.contains("mp4") || type.contains("m4v") -> "mp4"
                type.contains("mpeg") -> "mpg"
                type.contains("webm") -> "webm"
                type.contains("quicktime") -> "mov"
                type.contains("avi") -> "avi"
                type.contains("x-msvideo") -> "avi"
                else -> "bin"
            }
        }

        private fun filenameFromDisposition(header: String?): String? {
            if (header.isNullOrBlank()) return null
            val utf8 = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
                .find(header)
                ?.groupValues
                ?.getOrNull(1)
            if (!utf8.isNullOrBlank()) {
                return runCatching { java.net.URLDecoder.decode(utf8, Charsets.UTF_8) }.getOrDefault(utf8)
            }
            val plain = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
                .find(header)
                ?.groupValues
                ?.getOrNull(1)
            return plain?.trim()
        }
    }

    private class DownloadCancelledException : IOException("Download cancelled")
}
