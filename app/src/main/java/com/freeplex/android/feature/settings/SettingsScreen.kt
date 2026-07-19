package com.freeplex.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.freeplex.android.core.designsystem.FpTextField
import com.freeplex.android.core.designsystem.TvContentPadding
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable
import com.freeplex.android.core.model.LibraryDto

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()
    // Deleting a library is destructive and easy to hit with a remote —
    // always confirm before calling the API.
    var libraryPendingDelete by remember { mutableStateOf<LibraryDto?>(null) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (tv) TvContentPadding else androidx.compose.foundation.layout.PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(if (tv) 16.dp else 12.dp),
    ) {
        Text(
            text = "Settings",
            style = if (tv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "Signed in as ${state.username ?: "?"} (${state.role ?: "?"})")

        Text(text = "General", fontWeight = FontWeight.Bold)
        FpTextField(value = state.baseUrl, onValueChange = viewModel::onBaseUrl, label = "Server URL")

        Text(text = "Playback", fontWeight = FontWeight.Bold)
        FpTextField(
            value = state.lanCapKbps.toString(),
            onValueChange = viewModel::onLanCap,
            label = "LAN cap kbps (0 = unlimited)",
        )
        FpTextField(
            value = state.externalCapKbps.toString(),
            onValueChange = viewModel::onExternalCap,
            label = "External/mobile cap kbps",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto", "manual").forEach { mode ->
                FilterChip(
                    selected = state.preferredMode == mode,
                    onClick = { viewModel.onMode(mode) },
                    label = { Text(text = mode) },
                )
            }
        }
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Save")
        }

        if (state.role == "Admin") {
            Text(text = "Libraries", fontWeight = FontWeight.Bold)
            state.libraries.forEach { lib ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(text = lib.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${lib.type} · ${lib.itemCount} items",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.scanLibrary(lib.id) },
                            modifier = Modifier.tvFocusable(
                                onClick = { viewModel.scanLibrary(lib.id) },
                                scaleFocused = 1.05f,
                            ),
                        ) {
                            Text(text = "Scan")
                        }
                        Button(
                            onClick = { libraryPendingDelete = lib },
                            modifier = Modifier.tvFocusable(
                                onClick = { libraryPendingDelete = lib },
                                scaleFocused = 1.05f,
                            ),
                        ) {
                            Text(text = "Delete")
                        }
                    }
                }
            }
            FpTextField(
                value = state.newLibraryName,
                onValueChange = viewModel::onNewLibraryName,
                label = "New library name",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Movies", "Series").forEach { type ->
                    FilterChip(
                        selected = state.newLibraryType == type,
                        onClick = { viewModel.onNewLibraryType(type) },
                        label = { Text(text = type) },
                    )
                }
            }
            FpTextField(
                value = state.newLibraryPath,
                onValueChange = viewModel::onNewLibraryPath,
                label = "Server path",
            )
            Button(onClick = viewModel::createLibrary, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Create library")
            }

            Text(text = "Recent jobs", fontWeight = FontWeight.Bold)
            state.jobs.take(10).forEach { job ->
                Text(text = "${job.type} · ${job.state} · ${(job.progress * 100).toInt()}%")
            }
        }

        state.message?.let { Text(text = it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { viewModel.logout(onLoggedOut) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Sign out")
        }
    }
}
