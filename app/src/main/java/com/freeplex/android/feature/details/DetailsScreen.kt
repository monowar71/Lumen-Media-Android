@file:OptIn(ExperimentalComposeUiApi::class)

package com.freeplex.android.feature.details

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.freeplex.android.core.model.EpisodeSummary
import com.freeplex.android.core.model.Person
import com.freeplex.android.core.util.absoluteUrl
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
        val watched = movie.userData.watched == true
        val canResume = resume > 0L && !watched
        val progress = progressFraction(
            positionMs = if (canResume) resume else 0L,
            runtimeMs = movie.runtimeMs,
        )
        val cast = movie.people.orEmpty()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (tv) TvContentPadding else PaddingValues()),
        ) {
            item(key = "header") {
                DetailScaffold(
                    title = movie.title,
                    subtitle = listOfNotNull(movie.year?.toString(), movie.officialRating)
                        .joinToString(" · "),
                    overview = movie.overview,
                    backdrop = movie.artwork.backdrop ?: movie.artwork.poster,
                    poster = movie.artwork.poster,
                    progress = progress,
                    baseUrl = state.baseUrl,
                    playLabel = if (canResume) "Продолжить просмотр" else "Посмотреть",
                    onPlay = { onPlay(movie.id, if (canResume) resume else 0L, false) },
                    playFromStartLabel = if (canResume) "С начала" else null,
                    onPlayFromStart = if (canResume) {
                        { onPlay(movie.id, 0L, false) }
                    } else {
                        null
                    },
                    trailerUrl = movie.trailerUrl,
                    tv = tv,
                )
            }
            if (cast.isNotEmpty()) {
                item(key = "cast") {
                    CastSection(people = cast, baseUrl = state.baseUrl, tv = tv)
                }
            }
            item(key = "bottom-spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        return
    }

    val series = state.series
    if (series != null) {
        val playTarget = resolveSeriesPlayTarget(series.userData.nextUp, state.episodes)
        val resume = playTarget
            ?.takeIf { it.userData.watched != true }
            ?.userData?.playbackPositionMs
            ?.takeIf { it > 0L }
            ?: 0L
        val canResume = resume > 0L
        // Virtualize episodes — a full season of AsyncImage rows blows TV memory.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (tv) TvContentPadding else PaddingValues()),
        ) {
            item(key = "header") {
                DetailScaffold(
                    title = series.title,
                    subtitle = listOfNotNull(
                        series.year?.toString(),
                        "${series.seasonCount} seasons",
                    ).joinToString(" · "),
                    overview = series.overview,
                    backdrop = series.artwork.backdrop ?: series.artwork.poster,
                    poster = series.artwork.poster,
                    progress = 0f,
                    baseUrl = state.baseUrl,
                    playLabel = when {
                        playTarget == null -> null
                        canResume -> "Продолжить просмотр"
                        else -> "Посмотреть"
                    },
                    onPlay = playTarget?.let { ep ->
                        { onPlay(ep.id, resume, true) }
                    },
                    trailerUrl = series.trailerUrl,
                    tv = tv,
                    // Play/Trailer are focusable; only make the whole header a
                    // focus sink when neither button exists.
                    focusableHeader = tv && playTarget == null && series.trailerUrl == null,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Keep seasons in the same LazyColumn item as the header so
                // DPAD_DOWN from Trailer/Play can reach the chips (Compose
                // focus search often fails across LazyColumn item boundaries).
                SeasonPicker(
                    seasons = state.seasons.map { it.id to it.name },
                    selectedId = state.selectedSeasonId,
                    onSelect = viewModel::selectSeason,
                    tv = tv,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            val cast = series.people.orEmpty()
            if (cast.isNotEmpty()) {
                item(key = "cast") {
                    CastSection(people = cast, baseUrl = state.baseUrl, tv = tv)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            items(state.episodes, key = { it.id }) { ep ->
                EpisodeRow(
                    episode = ep,
                    baseUrl = state.baseUrl,
                    tv = tv,
                    onPlay = { onPlay(ep.id, ep.userData.playbackPositionMs ?: 0L, true) },
                )
            }
            item(key = "bottom-spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SeasonPicker(
    seasons: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    tv: Boolean,
) {
    if (seasons.isEmpty()) return
    // LazyRow so many seasons stay reachable via D-pad / swipe — a plain Row
    // clipped chips that could never receive focus or clicks.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = if (tv) TvDimens.focusHalo else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tv) Modifier.focusRestorer() else Modifier),
    ) {
        items(seasons, key = { it.first }) { (id, name) ->
            val selected = id == selectedId
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .tvFocusable(
                        onClick = { onSelect(id) },
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
}

@Composable
private fun EpisodeRow(
    episode: EpisodeSummary,
    baseUrl: String,
    tv: Boolean,
    onPlay: () -> Unit,
) {
    val thumb = artworkUrl(
        baseUrl = baseUrl,
        path = episode.artwork.thumb ?: episode.artwork.poster,
        width = 320,
        height = 180,
    )
    val watched = episode.userData.watched == true
    val progress = progressFraction(
        positionMs = if (watched) 0L else episode.userData.playbackPositionMs,
        runtimeMs = episode.runtimeMs,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onPlay, scaleFocused = 1.015f)
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
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ProgressBar(progress = progress, modifier = Modifier.align(Alignment.BottomStart))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "S${episode.seasonNumber}E${episode.episodeNumber}  ·  " +
                        (episode.title ?: "Episode ${episode.episodeNumber}"),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (watched) {
                    Text(
                        text = "Watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            episode.overview?.let {
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

@Composable
private fun DetailScaffold(
    title: String,
    subtitle: String,
    overview: String?,
    backdrop: String?,
    poster: String?,
    progress: Float,
    baseUrl: String,
    playLabel: String?,
    onPlay: (() -> Unit)?,
    tv: Boolean,
    trailerUrl: String? = null,
    playFromStartLabel: String? = null,
    onPlayFromStart: (() -> Unit)? = null,
    focusableHeader: Boolean = false,
) {
    val context = LocalContext.current
    // Trailer is an external (YouTube) link; ignore failure when no app can open it.
    val openTrailer: (() -> Unit)? = trailerUrl?.let { url ->
        { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    }
    val backdropModel = artworkUrl(
        baseUrl = baseUrl,
        path = backdrop,
        width = if (tv) 1280 else 960,
        height = if (tv) 720 else 540,
        quality = 70,
    )
    val posterModel = artworkUrl(
        baseUrl = baseUrl,
        path = poster,
        width = if (tv) 260 else 240,
        height = if (tv) 390 else 360,
    )
    val posterWidth = if (tv) 120.dp else 112.dp
    val hasActions = (onPlay != null && playLabel != null) ||
        (onPlayFromStart != null && playFromStartLabel != null) ||
        openTrailer != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusableHeader) {
                    Modifier
                        .clip(RoundedCornerShape(TvDimens.corner))
                        .tvFocusable(scaleFocused = 1f)
                } else {
                    Modifier
                },
            ),
    ) {
        // Soft backdrop behind the poster + meta row (web hero pattern).
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(TvDimens.corner))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (backdropModel != null) {
                AsyncImage(
                    model = backdropModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.4f,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(0.82f), Color.Black.copy(0.45f), Color.Transparent),
                        ),
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (tv) 18.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 18.dp else 14.dp),
        ) {
            // Poster "icon" matching the web detail layout.
            Box(
                modifier = Modifier
                    .width(posterWidth)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (posterModel != null) {
                    AsyncImage(
                        model = posterModel,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ProgressBar(progress = progress, modifier = Modifier.align(Alignment.BottomStart))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title,
                    style = if (tv) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
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
                        maxLines = if (tv) 4 else 5,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    )
                }
                if (hasActions) {
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(
                                if (!tv) {
                                    Modifier.horizontalScroll(rememberScrollState())
                                } else {
                                    Modifier
                                },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (onPlay != null && playLabel != null) {
                            Button(
                                onClick = onPlay,
                                modifier = Modifier.tvFocusable(onClick = onPlay, scaleFocused = 1.05f),
                            ) {
                                Text(playLabel, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        if (onPlayFromStart != null && playFromStartLabel != null) {
                            OutlinedButton(
                                onClick = onPlayFromStart,
                                modifier = Modifier.tvFocusable(
                                    onClick = onPlayFromStart,
                                    scaleFocused = 1.05f,
                                ),
                            ) {
                                Text(playFromStartLabel, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        if (openTrailer != null) {
                            OutlinedButton(
                                onClick = openTrailer,
                                modifier = Modifier.tvFocusable(onClick = openTrailer, scaleFocused = 1.05f),
                            ) {
                                Text("Трейлер", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
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

@Composable
private fun CastSection(
    people: List<Person>,
    baseUrl: String,
    tv: Boolean,
) {
    val avatar = if (tv) 76.dp else 68.dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = if (tv) 0.dp else 0.dp,
                top = 10.dp,
                bottom = 8.dp,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = if (tv) TvDimens.focusHalo else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 14.dp else 12.dp),
            modifier = if (tv) Modifier.focusRestorer() else Modifier,
        ) {
            // Positional keys: provider data can repeat a name.
            items(people.take(20)) { person ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(avatar + 16.dp)
                        .then(
                            if (tv) {
                                Modifier.tvFocusable(
                                    scaleFocused = 1.05f,
                                    shape = RoundedCornerShape(TvDimens.corner),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(avatar)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        person.thumb?.let { thumb ->
                            AsyncImage(
                                model = absoluteUrl(baseUrl, thumb),
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    person.role?.let { role ->
                        Text(
                            text = role,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Resume fraction for progress bars; 0 when unknown / fully watched. */
private fun progressFraction(positionMs: Long?, runtimeMs: Long?): Float {
    val position = positionMs ?: return 0f
    val runtime = runtimeMs ?: return 0f
    if (position <= 0L || runtime <= 0L) return 0f
    return (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
}

/**
 * Pick the episode the header Play button should start.
 * Prefer server `nextUp`, then an in-progress episode, then the first unwatched,
 * otherwise the first episode of the loaded season.
 */
private fun resolveSeriesPlayTarget(
    nextUp: EpisodeSummary?,
    episodes: List<EpisodeSummary>,
): EpisodeSummary? {
    if (nextUp != null) return nextUp
    episodes.firstOrNull {
        it.userData.watched != true && (it.userData.playbackPositionMs ?: 0L) > 0L
    }?.let { return it }
    episodes.firstOrNull { it.userData.watched != true }?.let { return it }
    return episodes.firstOrNull()
}
