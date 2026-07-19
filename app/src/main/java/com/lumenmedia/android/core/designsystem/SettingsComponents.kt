package com.lumenmedia.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
    val shape = RoundedCornerShape(FpDimens.radiusMd)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                onClick = if (enabled) onClick else null,
                scaleFocused = if (tv) 1.02f else 1.02f,
                shape = shape,
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = shape,
            )
            .padding(horizontal = FpDimens.space14, vertical = if (tv) 12.dp else 10.dp),
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
            modifier = Modifier.padding(top = FpDimens.space4),
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
    val shape = RoundedCornerShape(FpDimens.radiusMd)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                onClick = if (enabled) onClick else null,
                scaleFocused = if (tv) 1.02f else 1.02f,
                shape = shape,
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = shape,
            )
            .padding(horizontal = FpDimens.space14, vertical = if (tv) 12.dp else 10.dp),
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
        modifier = modifier
            .fillMaxWidth()
            .clipSurface(RoundedCornerShape(FpDimens.radiusXl))
            .padding(FpDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FpDimens.space8),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = FpDimens.space4),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FpDimens.space4),
        verticalArrangement = Arrangement.spacedBy(FpDimens.space8),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FpDimens.space8)) {
            options.forEach { (id, label) ->
                FpChip(
                    label = label,
                    selected = selectedId == id,
                    onClick = { onSelect(id) },
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
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
                Text(text = "Save", color = MaterialTheme.colorScheme.primary)
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
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FpDimens.space10)) {
                if (presets.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        presets.forEach { (labelText, value) ->
                            FpChip(
                                label = labelText,
                                selected = draft.toIntOrNull() == value,
                                onClick = { draft = value.toString() },
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
                Text(text = "Save", color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun Modifier.clipSurface(shape: RoundedCornerShape): Modifier =
    this
        .background(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = shape,
        )
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            shape = shape,
        )
