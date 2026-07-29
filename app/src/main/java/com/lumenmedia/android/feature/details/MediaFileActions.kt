package com.lumenmedia.android.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.FpButton
import com.lumenmedia.android.core.designsystem.FpButtonVariant
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.isTvDevice
import com.lumenmedia.android.core.designsystem.tvFocusable
import com.lumenmedia.android.core.offline.CachedEpisodeStatus
import com.lumenmedia.android.core.offline.OfflineEpisodeState

data class MediaFileActionItem(
    val id: String,
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Phone: compact ⋮ overflow. TV: prefer [MediaFileActionsDialog] opened via long-press;
 * this trigger remains available when a focused "More" control is needed.
 */
@Composable
fun MediaFileActionsButton(
    actions: List<MediaFileActionItem>,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.details_more_actions),
) {
    if (actions.isEmpty()) return
    val tv = isTvDevice()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<MediaFileActionItem?>(null) }

    if (tv) {
        FpButton(
            onClick = { menuOpen = true },
            label = stringResource(R.string.details_more_actions),
            variant = FpButtonVariant.Secondary,
            compact = true,
            modifier = modifier,
        )
        if (menuOpen) {
            MediaFileActionsDialog(
                title = stringResource(R.string.details_more_actions),
                actions = actions,
                onDismiss = { menuOpen = false },
            )
        }
    } else {
        IconButton(
            onClick = { menuOpen = true },
            modifier = modifier.tvFocusable(onClick = { menuOpen = true }, scaleFocused = 1.05f),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            color = if (action.destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    enabled = action.enabled,
                    onClick = {
                        menuOpen = false
                        if (action.destructive) {
                            confirmDelete = action
                        } else {
                            action.onClick()
                        }
                    },
                )
            }
        }
    }

    confirmDelete?.let { action ->
        DeleteFileConfirmDialog(
            onConfirm = {
                confirmDelete = null
                action.onClick()
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
fun MediaFileActionsDialog(
    title: String,
    actions: List<MediaFileActionItem>,
    onDismiss: () -> Unit,
) {
    var pendingDestructive by remember { mutableStateOf<MediaFileActionItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(FpDimens.space8),
                modifier = Modifier.fillMaxWidth(),
            ) {
                actions.forEach { action ->
                    FpButton(
                        onClick = {
                            if (action.destructive) {
                                pendingDestructive = action
                            } else {
                                onDismiss()
                                action.onClick()
                            }
                        },
                        enabled = action.enabled,
                        label = action.label,
                        variant = FpButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )

    pendingDestructive?.let { action ->
        DeleteFileConfirmDialog(
            onConfirm = {
                pendingDestructive = null
                onDismiss()
                action.onClick()
            },
            onDismiss = { pendingDestructive = null },
        )
    }
}

@Composable
fun DeleteFileConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.details_delete_file_title)) },
        text = { Text(text = stringResource(R.string.details_delete_file_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.details_delete_file),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_cancel))
            }
        },
    )
}

fun buildEpisodeMediaActions(
    watched: Boolean,
    canMarkUnwatched: Boolean,
    watchedBusy: Boolean,
    deletingFile: Boolean,
    isAdmin: Boolean,
    offline: OfflineEpisodeState?,
    markWatchedLabel: String,
    markUnwatchedLabel: String,
    downloadLabel: String,
    cancelDownloadLabel: String,
    removeDownloadLabel: String,
    retryDownloadLabel: String,
    deleteFileLabel: String,
    deletingLabel: String,
    onMarkWatched: () -> Unit,
    onMarkUnwatched: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDeleteFile: () -> Unit,
): List<MediaFileActionItem> = buildList {
    if (!watched) {
        add(
            MediaFileActionItem(
                id = "mark-watched",
                label = markWatchedLabel,
                enabled = !watchedBusy,
                onClick = onMarkWatched,
            ),
        )
    }
    if (canMarkUnwatched) {
        add(
            MediaFileActionItem(
                id = "mark-unwatched",
                label = markUnwatchedLabel,
                enabled = !watchedBusy,
                onClick = onMarkUnwatched,
            ),
        )
    }
    when (offline?.status) {
        CachedEpisodeStatus.Ready -> add(
            MediaFileActionItem(
                id = "remove-offline",
                label = removeDownloadLabel,
                onClick = onRemoveDownload,
            ),
        )
        CachedEpisodeStatus.Queued, CachedEpisodeStatus.Downloading -> add(
            MediaFileActionItem(
                id = "cancel-offline",
                label = cancelDownloadLabel,
                onClick = onCancelDownload,
            ),
        )
        CachedEpisodeStatus.Failed, null -> add(
            MediaFileActionItem(
                id = "download",
                label = if (offline?.status == CachedEpisodeStatus.Failed) {
                    retryDownloadLabel
                } else {
                    downloadLabel
                },
                onClick = onDownload,
            ),
        )
    }
    if (isAdmin) {
        add(
            MediaFileActionItem(
                id = "delete",
                label = if (deletingFile) deletingLabel else deleteFileLabel,
                destructive = true,
                enabled = !deletingFile,
                onClick = onDeleteFile,
            ),
        )
    }
}

fun buildMovieMediaActions(
    canDelete: Boolean,
    deletingFile: Boolean,
    deleteFileLabel: String,
    deletingLabel: String,
    onDeleteFile: () -> Unit,
): List<MediaFileActionItem> = buildList {
    if (canDelete) {
        add(
            MediaFileActionItem(
                id = "delete",
                label = if (deletingFile) deletingLabel else deleteFileLabel,
                destructive = true,
                enabled = !deletingFile,
                onClick = onDeleteFile,
            ),
        )
    }
}
