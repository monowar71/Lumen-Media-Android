package com.freeplex.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.freeplex.android.core.model.MediaItemSummary
import com.freeplex.android.core.util.artworkUrl

@Composable
fun FullPageLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun FpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
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
    // Rows want a fixed poster width; adaptive grids pass false + fillMaxWidth
    // so cards stretch to their cell instead of leaving ragged gaps.
    fixedWidth: Boolean = true,
) {
    val tv = isTvDevice()
    val cardWidth = if (tv) TvDimens.posterWidth else 120.dp
    val poster = item.artwork.poster ?: item.artwork.thumb
    val model = artworkUrl(
        baseUrl = baseUrl,
        path = poster,
        width = if (tv) 260 else 240,
        height = if (tv) 390 else 360,
    )
    val progress = playedFraction(item)

    Column(
        modifier = modifier
            .then(if (fixedWidth) Modifier.width(cardWidth) else Modifier)
            .tvFocusable(
                onClick = onClick,
                scaleFocused = if (tv) TvDimens.focusScale else 1.04f,
                shape = RoundedCornerShape(TvDimens.corner),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(TvDimens.corner))
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
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
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
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = if (tv) 5.dp else 6.dp)
                .heightIn(min = if (tv) 16.dp else 0.dp),
        )
        // Match web: year · Movies|Series under the title.
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
    // Series continue-watching cards sometimes omit runtime — still show that
    // playback has started so the row does not look empty.
    if (runtime == null || runtime <= 0L) return 0.08f
    return (position.toFloat() / runtime.toFloat()).coerceIn(0.08f, 1f)
}
