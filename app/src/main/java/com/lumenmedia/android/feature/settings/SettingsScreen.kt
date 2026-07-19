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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    libraryPendingDelete?.let { lib ->
        AlertDialog(
            onDismissRequest = { libraryPendingDelete = null },
            title = { Text(text = "Delete library?") },
            text = {
                Text(
                    text = "\"${lib.name}\" (${lib.itemCount} items) will be removed " +
                        "from the server. Media files on disk are not touched.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLibrary(lib.id)
                        libraryPendingDelete = null
                    },
                ) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { libraryPendingDelete = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (clearCachePending) {
        AlertDialog(
            onDismissRequest = { clearCachePending = false },
            title = { Text(text = "Clear offline cache?") },
            text = {
                Text(
                    text = "Deletes all downloaded episodes " +
                        "(${formatByteSize(state.cacheSummary.readyBytes)}, " +
                        "${state.cacheSummary.readyCount} files).",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        clearCachePending = false
                    },
                ) {
                    Text(text = "Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearCachePending = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    when (editTarget) {
        SettingsEditTarget.BaseUrl -> EditTextDialog(
            title = "Server URL",
            initialValue = state.baseUrl,
            label = "http://host:port",
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveBaseUrl(it)
                editTarget = null
            },
        )
        SettingsEditTarget.LanCap -> EditNumberDialog(
            title = "LAN bandwidth cap",
            initialValue = state.lanCapKbps,
            label = "kbps (0 = unlimited)",
            presets = listOf("Unlimited" to 0, "40 Mbps" to 40_000, "20 Mbps" to 20_000, "8 Mbps" to 8_000),
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveLanCap(it)
                editTarget = null
            },
        )
        SettingsEditTarget.ExternalCap -> EditNumberDialog(
            title = "External / mobile cap",
            initialValue = state.externalCapKbps,
            label = "kbps (0 = unlimited)",
            presets = listOf("Unlimited" to 0, "8 Mbps" to 8_000, "4 Mbps" to 4_000, "2 Mbps" to 2_000),
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveExternalCap(it)
                editTarget = null
            },
        )
        SettingsEditTarget.MaxCache -> EditNumberDialog(
            title = "Max offline cache",
            initialValue = bytesToGib(state.maxCacheBytes),
            label = "GiB (0 = unlimited)",
            presets = listOf("Unlimited" to 0, "20 GiB" to 20, "50 GiB" to 50, "100 GiB" to 100),
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.saveMaxCacheBytes(gibToBytes(it))
                editTarget = null
            },
        )
        SettingsEditTarget.NewLibraryName -> EditTextDialog(
            title = "Library name",
            initialValue = state.newLibraryName,
            label = "Name",
            onDismiss = { editTarget = null },
            onConfirm = {
                viewModel.onNewLibraryName(it)
                editTarget = null
            },
        )
        SettingsEditTarget.NewLibraryPath -> EditTextDialog(
            title = "Server path",
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
            text = "Settings",
            style = if (tv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "Signed in as ${state.username ?: "?"} (${state.role ?: "?"})",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        SettingsSection(title = "General") {
            SettingsClickRow(
                title = "Server URL",
                value = state.baseUrl,
                subtitle = "OK / tap to edit",
                onClick = { editTarget = SettingsEditTarget.BaseUrl },
            )
        }

        SettingsSection(title = "Playback") {
            SettingsClickRow(
                title = "LAN bandwidth cap",
                value = formatCap(state.lanCapKbps),
                subtitle = "Used on Wi‑Fi / Ethernet",
                onClick = { editTarget = SettingsEditTarget.LanCap },
            )
            SettingsClickRow(
                title = "External / mobile cap",
                value = formatCap(state.externalCapKbps),
                subtitle = "Used on cellular / remote access",
                onClick = { editTarget = SettingsEditTarget.ExternalCap },
            )
            SettingsChoiceRow(
                title = "Preferred quality mode",
                options = listOf("auto" to "Auto", "manual" to "Manual"),
                selectedId = state.preferredMode,
                onSelect = viewModel::saveMode,
            )
        }

        SettingsSection(title = "Offline cache") {
            SettingsClickRow(
                title = "Storage used",
                value = "${formatByteSize(state.cacheSummary.readyBytes)} · " +
                    "${state.cacheSummary.readyCount} episodes",
                subtitle = buildString {
                    append("Limit: ${formatCacheLimit(state.maxCacheBytes)}")
                    if (state.cacheSummary.activeCount > 0) {
                        append(" · ${state.cacheSummary.activeCount} downloading")
                    }
                },
                onClick = { editTarget = SettingsEditTarget.MaxCache },
            )
            SettingsClickRow(
                title = "Max cache size",
                value = formatCacheLimit(state.maxCacheBytes),
                subtitle = "Oldest episodes are removed when full",
                onClick = { editTarget = SettingsEditTarget.MaxCache },
            )
            SettingsActionRow(
                title = if (cacheExpanded) "Hide downloaded episodes" else "Show downloaded episodes",
                subtitle = if (cacheEntries.isEmpty()) "Cache is empty" else "${cacheEntries.size} entries",
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
                        text = "…and ${cacheEntries.size - 40} more",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SettingsActionRow(
                title = "Remove failed downloads",
                enabled = cacheEntries.any { it.status == CachedEpisodeStatus.Failed },
                onClick = viewModel::removeFailedDownloads,
            )
            SettingsActionRow(
                title = "Clear offline cache",
                subtitle = "Deletes all locally stored episodes",
                destructive = true,
                enabled = cacheEntries.isNotEmpty(),
                onClick = { clearCachePending = true },
            )
        }

        if (state.role == "Admin") {
            SettingsSection(title = "Libraries") {
                state.libraries.forEach { lib ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = lib.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${lib.type} · ${lib.itemCount} items",
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
                                Text(text = "Scan")
                            }
                            TextButton(
                                onClick = { libraryPendingDelete = lib },
                                modifier = Modifier.tvFocusable(
                                    onClick = { libraryPendingDelete = lib },
                                    scaleFocused = 1.05f,
                                ),
                            ) {
                                Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                SettingsClickRow(
                    title = "New library name",
                    value = state.newLibraryName.ifBlank { "Not set" },
                    onClick = { editTarget = SettingsEditTarget.NewLibraryName },
                )
                SettingsChoiceRow(
                    title = "Library type",
                    options = listOf("Movies" to "Movies", "Series" to "Series"),
                    selectedId = state.newLibraryType,
                    onSelect = viewModel::onNewLibraryType,
                )
                SettingsClickRow(
                    title = "Server path",
                    value = state.newLibraryPath.ifBlank { "Not set" },
                    onClick = { editTarget = SettingsEditTarget.NewLibraryPath },
                )
                SettingsActionRow(
                    title = "Create library",
                    enabled = state.newLibraryName.isNotBlank() && state.newLibraryPath.isNotBlank(),
                    onClick = viewModel::createLibrary,
                )

                Text(
                    text = "Recent jobs",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (state.jobs.isEmpty()) {
                    Text(
                        text = "No recent jobs",
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
            title = "Sign out",
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
            "Downloading ${(entry.progress * 100).toInt()}%"
        CachedEpisodeStatus.Queued -> "Queued"
        CachedEpisodeStatus.Failed -> entry.errorMessage ?: "Failed"
    }
    SettingsActionRow(
        title = entry.displayTitle,
        subtitle = statusLabel,
        destructive = true,
        onClick = onRemove,
    )
}

private fun formatCap(kbps: Int): String =
    if (kbps <= 0) "Unlimited" else "$kbps kbps"

private fun formatCacheLimit(bytes: Long): String =
    if (bytes <= 0L) "Unlimited" else formatByteSize(bytes)

private fun bytesToGib(bytes: Long): Int {
    if (bytes <= 0L) return 0
    return ((bytes + GIB / 2) / GIB).toInt().coerceAtLeast(1)
}

private fun gibToBytes(gib: Int): Long =
    if (gib <= 0) 0L else gib.toLong() * GIB

private const val GIB = 1024L * 1024L * 1024L
