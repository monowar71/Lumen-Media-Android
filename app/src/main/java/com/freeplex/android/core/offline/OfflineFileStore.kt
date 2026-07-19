package com.freeplex.android.core.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk layout for offline episode files.
 *
 * `<filesDir>/offline_media/<episodeId>.partial` while downloading,
 * renamed to `<episodeId>.<ext>` when complete.
 */
@Singleton
class OfflineFileStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root: File = File(context.filesDir, "offline_media").also { it.mkdirs() }

    fun rootDir(): File = root

    fun partialFile(episodeId: String): File = File(root, "$episodeId.partial")

    fun readyFile(episodeId: String, extension: String): File {
        val ext = extension.trimStart('.').ifBlank { "bin" }
        return File(root, "$episodeId.$ext")
    }

    fun findReadyFile(episodeId: String): File? {
        val matches = root.listFiles { file ->
            file.isFile &&
                file.name.startsWith("$episodeId.") &&
                !file.name.endsWith(".partial")
        }.orEmpty()
        return matches.maxByOrNull { it.length() }
    }

    fun deleteEpisodeFiles(episodeId: String) {
        root.listFiles { file ->
            file.isFile && (file.name == "$episodeId.partial" || file.name.startsWith("$episodeId."))
        }?.forEach { it.delete() }
    }

    fun deleteAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
        root.mkdirs()
    }

    fun totalBytesOnDisk(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
