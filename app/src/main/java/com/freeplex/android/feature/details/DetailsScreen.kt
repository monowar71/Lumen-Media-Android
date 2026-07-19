package com.freeplex.android.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freeplex.android.core.designsystem.ErrorState
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.TvContentPadding
import com.freeplex.android.core.designsystem.TvDimens
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable
import com.freeplex.android.core.util.artworkUrl

@Composable
fun DetailsScreen(
    onPlay: (itemId: String, resumeMs: Long, isEpisode: Boolean) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()

    if (state.loading) {
        FullPageLoading()
        return
    }
    if (state.error != null) {
        ErrorState(message = state.error!!, onRetry = viewModel::refresh)
        return
    }

    val movie = state.movie
    if (movie != null) {
        val resume = movie.userData.playbackPositionMs ?: 0L
        DetailScaffold(
            title = movie.title,
            subtitle = listOfNotNull(movie.year?.toString(), movie.officialRating).joinToString(" · "),
            overview = movie.overview,
            backdrop = movie.artwork.backdrop ?: movie.artwork.poster,
            baseUrl = state.baseUrl,
            accessToken = state.accessToken,
            playLabel = if (resume > 0) "Resume" else "Play",
            onPlay = { onPlay(movie.id, resume, false) },
            tv = tv,
            fillScreen = tv,
        )
        return
    }

    val series = state.series
    if (series != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (tv) TvContentPadding else androidx.compose.foundation.layout.PaddingValues()),
        ) {
            DetailScaffold(
                title = series.title,
                subtitle = listOfNotNull(
                    series.year?.toString(),
                    "${series.seasonCount} seasons",
                ).joinToString(" · "),
                overview = series.overview,
                backdrop = series.artwork.backdrop ?: series.artwork.poster,
                baseUrl = state.baseUrl,
                accessToken = state.accessToken,
                playLabel = null,
                onPlay = null,
                tv = tv,
                fillScreen = false,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.seasons.forEach { season ->
                    val selected = season.id == state.selectedSeasonId
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .tvFocusable(
                                onClick = { viewModel.selectSeason(season.id) },
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            state.episodes.forEach { ep ->
                val thumb = artworkUrl(
                    baseUrl = state.baseUrl,
                    path = ep.artwork.thumb ?: ep.artwork.poster,
                    token = state.accessToken,
                    width = 320,
                    height = 180,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusable(
                            onClick = {
                                onPlay(ep.id, ep.userData.playbackPositionMs ?: 0L, true)
                            },
                            scaleFocused = 1.015f,
                        )
                        .padding(horizontal = 6.dp, vertical = if (tv) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (tv) 140.dp else 120.dp)
                            .height(if (tv) 78.dp else 68.dp)
                            .clip(RoundedCornerShape(TvDimens.corner))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = ep.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "S${ep.seasonNumber}E${ep.episodeNumber}  ·  ${ep.title ?: "Episode ${ep.episodeNumber}"}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ep.overview?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (tv) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailScaffold(
    title: String,
    subtitle: String,
    overview: String?,
    backdrop: String?,
    baseUrl: String,
    accessToken: String?,
    playLabel: String?,
    onPlay: (() -> Unit)?,
    tv: Boolean,
    fillScreen: Boolean,
) {
    val model = artworkUrl(
        baseUrl = baseUrl,
        path = backdrop,
        token = accessToken,
        width = if (tv) 1280 else 960,
        height = if (tv) 720 else 540,
        quality = 70,
    )
    if (tv) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillScreen) Modifier.fillMaxSize() else Modifier.height(260.dp)),
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(0.88f), Color.Black.copy(0.4f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.55f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.48f)
                    .padding(if (fillScreen) 28.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (fillScreen) 5 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onPlay != null && playLabel != null) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(180.dp)
                            .tvFocusable(onClick = onPlay, scaleFocused = 1.05f),
                    ) {
                        Text(playLabel, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                overview?.let { Text(it) }
                if (onPlay != null && playLabel != null) {
                    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                        Text(playLabel)
                    }
                }
            }
        }
    }
}
