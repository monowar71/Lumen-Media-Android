package com.lumenmedia.android.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumenmedia.android.R
import com.lumenmedia.android.core.model.MediaItemSummary
import com.lumenmedia.android.core.util.artworkUrl

@Composable
fun FullPageLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FpDimens.space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(FpDimens.space16))
            FpButton(onClick = onRetry, label = "Retry")
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FpDimens.space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = FpDimens.space8),
        )
    }
}

enum class FpButtonVariant { Primary, Secondary, Ghost }

@Composable
fun FpButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    variant: FpButtonVariant = FpButtonVariant.Primary,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(FpDimens.radiusMd)
    val height = if (compact) FpDimens.buttonHeightSm else FpDimens.buttonHeight
    val focusMod = Modifier.tvFocusable(
        onClick = if (enabled) onClick else null,
        scaleFocused = 1.04f,
        shape = shape,
    )
    when (variant) {
        FpButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
            ),
            contentPadding = PaddingValues(horizontal = FpDimens.space16, vertical = FpDimens.space8),
            modifier = modifier
                .heightIn(min = height)
                .then(focusMod),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        FpButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
            contentPadding = PaddingValues(horizontal = FpDimens.space14, vertical = FpDimens.space8),
            modifier = modifier
                .heightIn(min = height)
                .then(focusMod),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        FpButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = FpDimens.space12, vertical = FpDimens.space6),
            modifier = modifier
                .heightIn(min = height)
                .then(focusMod),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun FpChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FpDimens.radiusPill)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .tvFocusable(onClick = onClick, scaleFocused = 1.05f, shape = shape)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = FpDimens.space14, vertical = FpDimens.space8),
    )
}

@Composable
fun FpBadge(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(FpDimens.radiusSm))
            .background(
                if (accent) FpColors.AccentSoft
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                width = 1.dp,
                color = if (accent) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(FpDimens.radiusSm),
            )
            .padding(horizontal = FpDimens.space6, vertical = 2.dp),
    )
}

@Composable
fun FpBrandMark(size: Dp = 28.dp) {
    Image(
        painter = painterResource(R.drawable.ic_brand_mark),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(FpDimens.radiusSm)),
    )
}

@Composable
fun FpSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
        modifier = modifier.padding(vertical = FpDimens.space8),
    )
}

@Composable
fun FpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(FpDimens.radiusMd),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
    )
}

@Composable
fun PosterCard(
    item: MediaItemSummary,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fixedWidth: Boolean = true,
) {
    val tv = isTvDevice()
    val cardWidth = if (tv) FpDimens.posterTv else FpDimens.posterPhone
    val poster = item.artwork.poster ?: item.artwork.thumb
    val model = artworkUrl(
        baseUrl = baseUrl,
        path = poster,
        width = if (tv) 280 else 264,
        height = if (tv) 420 else 396,
    )
    val progress = playedFraction(item)
    val watched = item.userData.watched == true
    val shape = RoundedCornerShape(FpDimens.radiusMd)
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .then(if (fixedWidth) Modifier.width(cardWidth) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(
                onClick = onClick,
                scaleFocused = if (tv) FpDimens.focusScale else 1.03f,
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (focused || !tv) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = if (focused) 0.45f else 0.15f)),
                            ),
                        ),
                )
            }
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (watched) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(FpDimens.space6)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = FpDimens.space6),
        )
        val kindLabel = when {
            item.kind.equals("Series", ignoreCase = true) -> "Series"
            item.kind.equals("Movie", ignoreCase = true) -> "Movie"
            else -> null
        }
        val meta = listOfNotNull(item.year?.toString(), kindLabel).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Resume fraction for the poster progress bar; 0 when unknown or watched. */
private fun playedFraction(item: MediaItemSummary): Float {
    if (item.userData.watched == true) return 0f
    val position = item.userData.playbackPositionMs ?: return 0f
    if (position <= 0L) return 0f
    val runtime = item.runtimeMs
    if (runtime == null || runtime <= 0L) return 0.08f
    return (position.toFloat() / runtime.toFloat()).coerceIn(0.08f, 1f)
}

@Composable
fun MediaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.Black.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
