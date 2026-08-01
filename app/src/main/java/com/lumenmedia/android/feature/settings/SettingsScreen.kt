package com.lumenmedia.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.EditNumberDialog
import com.lumenmedia.android.core.designsystem.EditTextDialog
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.SettingsActionRow
import com.lumenmedia.android.core.designsystem.SettingsChoiceRow
import com.lumenmedia.android.core.designsystem.SettingsClickRow
import com.lumenmedia.android.core.designsystem.SettingsSection
import com.lumenmedia.android.core.designsystem.TvContentPadding
import com.lumenmedia.android.core.designsystem.formatByteSize
import com.lumenmedia.android.core.designsystem.isTvDevice
import com.lumenmedia.android.core.designsystem.tvFocusable
import com.lumenmedia.android.core.model.LibraryDto
import com.lumenmedia.android.core.offline.CachedEpisodeStatus
import com.lumenmedia.android.core.offline.OfflineEpisodeState

private enum class SettingsEditTarget {
    BaseUrl,
    LanCap,
    ExternalCap,
    MaxCache,
    NewLibraryName,
    NewLibraryPath,
}

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cacheEntries by viewModel.cacheEntries.collectAsStateWithLifecycle()
    val tv = isTvDevice()
    var libraryPendingDelete by remember { mutableStateOf<LibraryDto?>(null) }
    var clearCachePending by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SettingsEditTarget?>(null) }
    var cacheExpanded by remember { mutableStateOf(false) }
    val unlimited = stringResource(R.string.settings_cap_unlimited)

    libraryPendingDelete?.let { lib ->
        AlertDialog(
            onDismissRequest = { libraryPendingDelete = null },
            title = { Text(text = stringResource(R.string.settings_delete_library_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_delete_library_body,
                        lib.name,
                        lib.itemCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLibrary(lib.id)
                        libraryPendingDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { libraryPendingDelete = null }) {
                    Text(text = stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (clearCachePending) {
        AlertDialog(
            onDismissRequest = { clearCachePending = false },
            title = { Text(text = stringResource(R.string.settings_clear_cache_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_clear_cache_body,
                        formatByteSize(state.cacheSummary.readyBytes),
                        state.cacheSummary.readyCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        clearCachePending = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearCachePending = false }) {
                    Text(text = stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    when (editTarget) {
        SettingsEditTarget.BaseUrl -> EditTextDialog(
            title = stringResource(R.string.settings_server_url),
            initialValue = state.baseUrl,
            label = "http://host:port",
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveBaseUrl(it)
                editTarget = null
            },
        )
        SettingsEditTarget.LanCap -> EditNumberDialog(
            title = stringResource(R.string.settings_lan_cap),
            initialValue = state.lanCapKbps,
            label = stringResource(R.string.settings_cap_kbps),
            presets = listOf(
                unlimited to 0,
                "40 Mbps" to 40_000,
                "20 Mbps" to 20_000,
                "8 Mbps" to 8_000,
            ),
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveLanCap(it)
                editTarget = null
            },
        )
        SettingsEditTarget.ExternalCap -> EditNumberDialog(
            title = stringResource(R.string.settings_external_cap),
            initialValue = state.externalCapKbps,
            label = stringResource(R.string.settings_cap_kbps),
            presets = listOf(
                unlimited to 0,
                "8 Mbps" to 8_000,
                "4 Mbps" to 4_000,
                "2 Mbps" to 2_000,
            ),
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveExternalCap(it)
                editTarget = null
            },
        )
        SettingsEditTarget.MaxCache -> EditNumberDialog(
            title = stringResource(R.string.settings_max_cache_title),
            initialValue = bytesToGib(state.maxCacheBytes),
            label = stringResource(R.string.settings_max_cache_label),
            presets = listOf(
                unlimited to 0,
                "20 GiB" to 20,
                "50 GiB" to 50,
                "100 GiB" to 100,
            ),
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveMaxCacheBytes(gibToBytes(it))
                editTarget = null
            },
        )
        SettingsEditTarget.NewLibraryName -> EditTextDialog(
            title = stringResource(R.string.settings_library_name),
            initialValue = state.newLibraryName,
            label = stringResource(R.string.settings_library_name_label),
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.onNewLibraryName(it)
                editTarget = null
            },
        )
        SettingsEditTarget.NewLibraryPath -> EditTextDialog(
            title = stringResource(R.string.settings_server_path),
            initialValue = state.newLibraryPath,
            label = "/media/movies",
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.onNewLibraryPath(it)
                editTarget = null
            },
        )
        null -> Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (tv) TvContentPadding else androidx.compose.foundation.layout.PaddingValues(FpDimens.space16)),
        verticalArrangement = Arrangement.spacedBy(FpDimens.space12),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = if (tv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = stringResource(
                R.string.settings_signed_in_as,
                state.username ?: "?",
                state.role ?: "?",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        SettingsSection(title = stringResource(R.string.settings_general)) {
            SettingsClickRow(
                title = stringResource(R.string.settings_server_url),
                value = state.baseUrl,
                subtitle = stringResource(R.string.settings_tap_to_edit),
                onClick = { editTarget = SettingsEditTarget.BaseUrl },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_language_title),
                options = listOf(
                    "ru" to stringResource(R.string.settings_language_ru),
                    "en" to stringResource(R.string.settings_language_en),
                ),
                selectedId = state.locale,
                onSelect = viewModel::saveLocale,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_playback)) {
            SettingsClickRow(
                title = stringResource(R.string.settings_lan_cap),
                value = formatCap(state.lanCapKbps),
                subtitle = stringResource(R.string.settings_lan_cap_subtitle),
                onClick = { editTarget = SettingsEditTarget.LanCap },
            )
            SettingsClickRow(
                title = stringResource(R.string.settings_external_cap),
                value = formatCap(state.externalCapKbps),
                subtitle = stringResource(R.string.settings_external_cap_subtitle),
                onClick = { editTarget = SettingsEditTarget.ExternalCap },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_preferred_mode),
                options = listOf(
                    "auto" to stringResource(R.string.settings_mode_auto),
                    "manual" to stringResource(R.string.settings_mode_manual),
                ),
                selectedId = state.preferredMode,
                onSelect = viewModel::saveMode,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_offline)) {
            SettingsClickRow(
                title = stringResource(R.string.settings_storage_used),
                value = stringResource(
                    R.string.settings_storage_episodes,
                    formatByteSize(state.cacheSummary.readyBytes),
                    state.cacheSummary.readyCount,
                ),
                subtitle = buildString {
                    append(
                        stringResource(
                            R.string.settings_limit_label,
                            formatCacheLimit(state.maxCacheBytes),
                        ),
                    )
                    if (state.cacheSummary.activeCount > 0) {
                        append(
                            stringResource(
                                R.string.settings_downloading_count,
                                state.cacheSummary.activeCount,
                            ),
                        )
                    }
                },
                onClick = { editTarget = SettingsEditTarget.MaxCache },
            )
            SettingsClickRow(
                title = stringResource(R.string.settings_max_cache),
                value = formatCacheLimit(state.maxCacheBytes),
                subtitle = stringResource(R.string.settings_max_cache_subtitle),
                onClick = { editTarget = SettingsEditTarget.MaxCache },
            )
            SettingsActionRow(
                title = if (cacheExpanded) {
                    stringResource(R.string.settings_hide_list)
                } else {
                    stringResource(R.string.settings_show_list)
                },
                subtitle = if (cacheEntries.isEmpty()) {
                    stringResource(R.string.settings_cache_empty)
                } else {
                    stringResource(R.string.settings_cache_entries, cacheEntries.size)
                },
                onClick = { cacheExpanded = !cacheExpanded },
                enabled = cacheEntries.isNotEmpty(),
            )
            if (cacheExpanded) {
                cacheEntries.take(40).forEach { entry ->
                    CachedEpisodeRow(
                        entry = entry,
                        onRemove = { viewModel.removeCachedEpisode(entry.episodeId) },
                    )
                }
                if (cacheEntries.size > 40) {
                    Text(
                        text = stringResource(R.string.settings_and_more, cacheEntries.size - 40),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SettingsActionRow(
                title = stringResource(R.string.settings_remove_failed),
                enabled = cacheEntries.any { it.status == CachedEpisodeStatus.Failed },
                onClick = viewModel::removeFailedDownloads,
            )
            SettingsActionRow(
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_clear_cache_subtitle),
                destructive = true,
                enabled = cacheEntries.isNotEmpty(),
                onClick = { clearCachePending = true },
            )
        }

        if (state.role == "Admin") {
            SettingsSection(title = stringResource(R.string.settings_libraries)) {
                state.libraries.forEach { lib ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = lib.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringResource(R.string.settings_items_meta, lib.type, lib.itemCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { viewModel.scanLibrary(lib.id) },
                                modifier = Modifier.tvFocusable(
                                    onClick = { viewModel.scanLibrary(lib.id) },
                                    scaleFocused = 1.05f,
                                ),
                            ) {
                                Text(text = stringResource(R.string.settings_scan))
                            }
                            TextButton(
                                onClick = { libraryPendingDelete = lib },
                                modifier = Modifier.tvFocusable(
                                    onClick = { libraryPendingDelete = lib },
                                    scaleFocused = 1.05f,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                SettingsClickRow(
                    title = stringResource(R.string.settings_new_library_name),
                    value = state.newLibraryName.ifBlank { stringResource(R.string.settings_not_set) },
                    onClick = { editTarget = SettingsEditTarget.NewLibraryName },
                )
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_library_type),
                    options = listOf(
                        "Movies" to stringResource(R.string.search_movies),
                        "Series" to stringResource(R.string.search_series),
                    ),
                    selectedId = state.newLibraryType,
                    onSelect = viewModel::onNewLibraryType,
                )
                SettingsClickRow(
                    title = stringResource(R.string.settings_server_path),
                    value = state.newLibraryPath.ifBlank { stringResource(R.string.settings_not_set) },
                    onClick = { editTarget = SettingsEditTarget.NewLibraryPath },
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_create_library),
                    enabled = state.newLibraryName.isNotBlank() && state.newLibraryPath.isNotBlank(),
                    onClick = viewModel::createLibrary,
                )

                Text(
                    text = stringResource(R.string.settings_jobs),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (state.jobs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_jobs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    state.jobs.take(10).forEach { job ->
                        Text(
                            text = "${job.type} · ${job.state} · ${(job.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        state.message?.let { Text(text = it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        SettingsActionRow(
            title = stringResource(R.string.nav_sign_out),
            destructive = true,
            onClick = { viewModel.logout(onLoggedOut) },
        )
    }
}

@Composable
private fun CachedEpisodeRow(
    entry: OfflineEpisodeState,
    onRemove: () -> Unit,
) {
    val statusLabel = when (entry.status) {
        CachedEpisodeStatus.Ready -> formatByteSize(entry.bytesTotal.coerceAtLeast(entry.bytesDownloaded))
        CachedEpisodeStatus.Downloading ->
            stringResource(R.string.details_downloading) + " ${(entry.progress * 100).toInt()}%"
        CachedEpisodeStatus.Queued -> stringResource(R.string.details_queued)
        CachedEpisodeStatus.Failed -> entry.errorMessage ?: stringResource(R.string.details_failed)
    }
    SettingsActionRow(
        title = entry.displayTitle,
        subtitle = statusLabel,
        destructive = true,
        onClick = onRemove,
    )
}

@Composable
private fun formatCap(kbps: Int): String =
    if (kbps <= 0) {
        stringResource(R.string.settings_cap_unlimited)
    } else {
        stringResource(R.string.settings_cap_value, kbps)
    }

@Composable
private fun formatCacheLimit(bytes: Long): String =
    if (bytes <= 0L) stringResource(R.string.settings_cap_unlimited) else formatByteSize(bytes)

private fun bytesToGib(bytes: Long): Int {
    if (bytes <= 0L) return 0
    return ((bytes + GIB / 2) / GIB).toInt().coerceAtLeast(1)
}

private fun gibToBytes(gib: Int): Long =
    if (gib <= 0) 0L else gib.toLong() * GIB

private const val GIB = 1024L * 1024L * 1024L
