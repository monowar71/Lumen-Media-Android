package com.freeplex.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Settings list row that is focusable as a whole. Never embeds a TextField, so
 * TV D-pad navigation does not open the IME when moving between items.
 */
@Composable
fun SettingsClickRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val tv = isTvDevice()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                onClick = if (enabled) onClick else null,
                scaleFocused = if (tv) 1.02f else 1.04f,
                shape = RoundedCornerShape(TvDimens.corner),
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (tv) 0.35f else 0.55f),
                shape = RoundedCornerShape(TvDimens.corner),
            )
            .padding(horizontal = 16.dp, vertical = if (tv) 14.dp else 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val tv = isTvDevice()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                onClick = if (enabled) onClick else null,
                scaleFocused = if (tv) 1.02f else 1.04f,
                shape = RoundedCornerShape(TvDimens.corner),
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (tv) 0.35f else 0.55f),
                shape = RoundedCornerShape(TvDimens.corner),
            )
            .padding(horizontal = 16.dp, vertical = if (tv) 14.dp else 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        content()
    }
}

@Composable
fun SettingsChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = isTvDevice()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (tv) 0.35f else 0.55f),
                shape = RoundedCornerShape(TvDimens.corner),
            )
            .padding(horizontal = 16.dp, vertical = if (tv) 14.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, label) ->
                FilterChip(
                    selected = selectedId == id,
                    onClick = { onSelect(id) },
                    label = { Text(text = label) },
                    modifier = Modifier.tvFocusable(
                        onClick = { onSelect(id) },
                        scaleFocused = 1.05f,
                    ),
                )
            }
        }
    }
}

@Composable
fun EditTextDialog(
    title: String,
    initialValue: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isPassword: Boolean = false,
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            FpTextField(
                value = draft,
                onValueChange = { draft = it },
                label = label,
                isPassword = isPassword,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft.trim()) }) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
fun EditNumberDialog(
    title: String,
    initialValue: Int,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    presets: List<Pair<String, Int>> = emptyList(),
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue.toString()) }
    val focusRequester = remember { FocusRequester() }
    // Only pull focus onto the field after the dialog is visible — avoids the
    // settings-list IME trap where every row was itself an OutlinedTextField.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (presets.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        presets.forEach { (labelText, value) ->
                            FilterChip(
                                selected = draft.toIntOrNull() == value,
                                onClick = { draft = value.toString() },
                                label = { Text(text = labelText) },
                                modifier = Modifier.tvFocusable(
                                    onClick = { draft = value.toString() },
                                    scaleFocused = 1.05f,
                                ),
                            )
                        }
                    }
                }
                FpTextField(
                    value = draft,
                    onValueChange = { draft = it.filter { ch -> ch.isDigit() }.take(9) },
                    label = label,
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft.toIntOrNull() ?: 0) }) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

/** Formats byte counts for cache UI (B / KB / MB / GB). */
fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
}
