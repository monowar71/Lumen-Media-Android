@file:OptIn(ExperimentalComposeUiApi::class)

package com.lumenmedia.android.feature.details

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.ErrorState
import com.lumenmedia.android.core.designsystem.FpBadge
import com.lumenmedia.android.core.designsystem.FpButton
import com.lumenmedia.android.core.designsystem.FpButtonVariant
import com.lumenmedia.android.core.designsystem.FpChip
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.FpSectionTitle
import com.lumenmedia.android.core.designsystem.FullPageLoading
import com.lumenmedia.android.core.designsystem.MediaProgressBar
import com.lumenmedia.android.core.designsystem.fpContentPadding
import com.lumenmedia.android.core.designsystem.isTvDevice
import com.lumenmedia.android.core.designsystem.tvFocusable
import com.lumenmedia.android.core.model.EpisodeSummary
import com.lumenmedia.android.core.model.Person
import com.lumenmedia.android.core.offline.CachedEpisodeStatus
import com.lumenmedia.android.core.offline.OfflineEpisodeState
import com.lumenmedia.android.core.util.absoluteUrl
import com.lumenmedia.android.core.util.artworkUrl

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
                .then(if (tv) Modifier.padding(fpContentPadding()) else Modifier),
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
                    playLabel = if (canResume) {
                        stringResource(R.string.details_resume_short)
                    } else {
                        stringResource(R.string.details_play)
                    },
                    onPlay = { onPlay(movie.id, if (canResume) resume else 0L, false) },
                    playFromStartLabel = if (canResume) {
                        stringResource(R.string.details_play_from_start)
                    } else {
                        null
                    },
                    onPlayFromStart = if (canResume) {
                        { onPlay(movie.id, 0L, false) }
                    } else {
                        null
                    },
                    watchedLabel = if (watched) {
                        stringResource(R.string.details_mark_unwatched)
                    } else {
                        stringResource(R.string.details_mark_watched)
                    },
                    onToggleWatched = viewModel::toggleMovieWatched,
                    watchedBusy = state.markingWatched,
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
                Spacer(modifier = Modifier.height(FpDimens.space24))
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
        val seriesWatched = DetailsViewModel.isSeriesWatched(series)
        val seasonWatched = DetailsViewModel.isSeasonWatched(state.episodes)
        val seasonsFocusRequester = remember { FocusRequester() }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (tv) Modifier.padding(fpContentPadding()) else Modifier),
        ) {
            item(key = "header") {
                DetailScaffold(
                    title = series.title,
                    subtitle = listOfNotNull(
                        series.year?.toString(),
                        stringResource(R.string.details_seasons_count, series.seasonCount),
                    ).joinToString(" · "),
                    overview = series.overview,
                    backdrop = series.artwork.backdrop ?: series.artwork.poster,
                    poster = series.artwork.poster,
                    progress = 0f,
                    baseUrl = state.baseUrl,
                    playLabel = when {
                        playTarget == null -> null
                        canResume -> stringResource(R.string.details_resume_short)
                        else -> stringResource(R.string.details_play)
                    },
                    onPlay = playTarget?.let { ep ->
                        { onPlay(ep.id, resume, true) }
                    },
                    watchedLabel = if (seriesWatched) {
                        stringResource(R.string.details_mark_unwatched)
                    } else {
                        stringResource(R.string.details_mark_watched)
                    },
                    onToggleWatched = viewModel::toggleSeriesWatched,
                    watchedBusy = state.markingWatched,
                    trailerUrl = series.trailerUrl,
                    tv = tv,
                    focusableHeader = tv && playTarget == null && series.trailerUrl == null,
                    // Force D-pad Down from CTA into season chips (otherwise LazyColumn
                    // focus search jumps straight to episodes / season actions).
                    actionsDownFocus = if (tv && state.seasons.isNotEmpty()) {
                        seasonsFocusRequester
                    } else {
                        null
                    },
                )
            }
            // Own LazyColumn item: keeps season chips a distinct focus band.
            item(key = "seasons") {
                Spacer(modifier = Modifier.height(FpDimens.space12))
                SeasonPicker(
                    seasons = state.seasons.map { it.id to it.name },
                    selectedId = state.selectedSeasonId,
                    onSelect = viewModel::selectSeason,
                    tv = tv,
                    entryFocusRequester = if (tv) seasonsFocusRequester else null,
                )
                if (state.episodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(FpDimens.space8))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
                        modifier = Modifier.padding(horizontal = if (tv) 0.dp else FpDimens.contentPadHPhone),
                    ) {
                        FpButton(
                            onClick = viewModel::toggleSeasonWatched,
                            enabled = !state.markingWatched,
                            label = if (seasonWatched) {
                                stringResource(R.string.details_mark_season_unwatched)
                            } else {
                                stringResource(R.string.details_mark_season_watched)
                            },
                            variant = FpButtonVariant.Secondary,
                            compact = true,
                        )
                        val seasonOffline = seasonOfflineLabel(state.episodes, state.offlineByEpisodeId)
                        FpButton(
                            onClick = viewModel::downloadSeason,
                            enabled = seasonOffline == SeasonOfflineAction.Download,
                            label = when (seasonOffline) {
                                SeasonOfflineAction.Download ->
                                    stringResource(R.string.details_download_season)
                                SeasonOfflineAction.None ->
                                    stringResource(R.string.details_offline_ready)
                            },
                            variant = FpButtonVariant.Secondary,
                            compact = true,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(FpDimens.space8))
            }
            val cast = series.people.orEmpty()
            if (cast.isNotEmpty()) {
                item(key = "cast") {
                    CastSection(people = cast, baseUrl = state.baseUrl, tv = tv)
                    Spacer(modifier = Modifier.height(FpDimens.space8))
                }
            }
            items(state.episodes, key = { it.id }) { ep ->
                EpisodeRow(
                    episode = ep,
                    baseUrl = state.baseUrl,
                    tv = tv,
                    watchedBusy = state.markingWatched,
                    offline = state.offlineByEpisodeId[ep.id],
                    onPlay = { onPlay(ep.id, ep.userData.playbackPositionMs ?: 0L, true) },
                    onToggleWatched = { viewModel.toggleEpisodeWatched(ep.id) },
                    onDownload = { viewModel.downloadEpisode(ep.id) },
                    onCancelDownload = { viewModel.cancelOfflineEpisode(ep.id) },
                    onRemoveDownload = { viewModel.removeOfflineEpisode(ep.id) },
                )
            }
            item(key = "bottom-spacer") {
                Spacer(modifier = Modifier.height(FpDimens.space24))
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
    entryFocusRequester: FocusRequester? = null,
) {
    if (seasons.isEmpty()) return
    val entryId = selectedId ?: seasons.firstOrNull()?.first
    // Non-lazy Row: nested LazyRow inside LazyColumn is skipped by D-pad Down.
    Row(
        horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (tv) FpDimens.focusBorder else FpDimens.contentPadHPhone,
                vertical = if (tv) FpDimens.focusBorder else 0.dp,
            )
            .then(if (tv) Modifier.focusRestorer() else Modifier)
            .horizontalScroll(rememberScrollState()),
    ) {
        seasons.forEach { (id, name) ->
            FpChip(
                label = name,
                selected = id == selectedId,
                onClick = { onSelect(id) },
                scaleFocused = 1f,
                modifier = if (entryFocusRequester != null && id == entryId) {
                    Modifier.focusRequester(entryFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeSummary,
    baseUrl: String,
    tv: Boolean,
    watchedBusy: Boolean,
    offline: OfflineEpisodeState?,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
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
    val shape = RoundedCornerShape(FpDimens.radiusMd)
    val episodeTitle = episode.title
        ?: stringResource(R.string.details_episode_n, episode.episodeNumber)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (tv) 0.dp else FpDimens.contentPadHPhone,
                vertical = FpDimens.space4,
            )
            .tvFocusable(onClick = onPlay, scaleFocused = 1.015f, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .padding(FpDimens.space10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FpDimens.space12),
    ) {
        Box(
            modifier = Modifier
                .width(if (tv) FpDimens.episodeThumbWTv else FpDimens.episodeThumbW)
                .height(if (tv) FpDimens.episodeThumbHTv else FpDimens.episodeThumbH)
                .clip(shape)
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
            MediaProgressBar(progress = progress, modifier = Modifier.align(Alignment.BottomStart))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "S${episode.seasonNumber}E${episode.episodeNumber}  ·  $episodeTitle",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FpDimens.space6)) {
                    OfflineBadge(offline = offline)
                    if (watched) {
                        FpBadge(label = stringResource(R.string.details_watched), accent = true)
                    }
                }
            }
            episode.overview?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (tv) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = FpDimens.space2),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(FpDimens.space4),
                modifier = Modifier.padding(top = FpDimens.space4),
            ) {
                FpButton(
                    onClick = onToggleWatched,
                    enabled = !watchedBusy,
                    label = if (watched) {
                        stringResource(R.string.details_mark_unwatched)
                    } else {
                        stringResource(R.string.details_watched)
                    },
                    variant = FpButtonVariant.Ghost,
                    compact = true,
                )
                when (offline?.status) {
                    CachedEpisodeStatus.Ready -> FpButton(
                        onClick = onRemoveDownload,
                        label = stringResource(R.string.details_remove_download),
                        variant = FpButtonVariant.Ghost,
                        compact = true,
                    )
                    CachedEpisodeStatus.Queued, CachedEpisodeStatus.Downloading -> FpButton(
                        onClick = onCancelDownload,
                        label = stringResource(R.string.details_cancel_download),
                        variant = FpButtonVariant.Ghost,
                        compact = true,
                    )
                    CachedEpisodeStatus.Failed, null -> FpButton(
                        onClick = onDownload,
                        label = if (offline?.status == CachedEpisodeStatus.Failed) {
                            stringResource(R.string.state_try_again)
                        } else {
                            stringResource(R.string.details_download)
                        },
                        variant = FpButtonVariant.Ghost,
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineBadge(offline: OfflineEpisodeState?) {
    val label = when (offline?.status) {
        CachedEpisodeStatus.Ready -> stringResource(R.string.details_offline_ready)
        CachedEpisodeStatus.Downloading ->
            stringResource(R.string.details_downloading) + " ${(offline.progress * 100).toInt()}%"
        CachedEpisodeStatus.Queued -> stringResource(R.string.details_queued)
        CachedEpisodeStatus.Failed -> stringResource(R.string.details_failed)
        null -> return
    }
    FpBadge(
        label = label,
        accent = offline.status != CachedEpisodeStatus.Failed,
    )
}

private enum class SeasonOfflineAction {
    Download,
    None,
}

private fun seasonOfflineLabel(
    episodes: List<EpisodeSummary>,
    offline: Map<String, OfflineEpisodeState>,
): SeasonOfflineAction {
    if (episodes.isEmpty()) return SeasonOfflineAction.None
    val allReady = episodes.all { offline[it.id]?.status == CachedEpisodeStatus.Ready }
    return if (allReady) SeasonOfflineAction.None else SeasonOfflineAction.Download
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
    watchedLabel: String? = null,
    onToggleWatched: (() -> Unit)? = null,
    watchedBusy: Boolean = false,
    focusableHeader: Boolean = false,
    /** When set (TV), D-pad Down from the action row lands on season chips. */
    actionsDownFocus: FocusRequester? = null,
) {
    val context = LocalContext.current
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
        width = if (tv) 280 else 260,
        height = if (tv) 420 else 390,
    )
    val posterWidth = if (tv) 140.dp else 128.dp
    val hasActions = (onPlay != null && playLabel != null) ||
        (onPlayFromStart != null && playFromStartLabel != null) ||
        (onToggleWatched != null && watchedLabel != null) ||
        openTrailer != null
    val shape = RoundedCornerShape(if (tv) FpDimens.radiusLg else 0.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusableHeader) {
                    Modifier
                        .clip(shape)
                        .tvFocusable(scaleFocused = 1f, shape = shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (backdropModel != null) {
                AsyncImage(
                    model = backdropModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.42f,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(0.35f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(0.55f), Color.Transparent),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (tv) FpDimens.space20 else FpDimens.contentPadHPhone,
                    vertical = if (tv) FpDimens.space16 else FpDimens.space20,
                ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (tv) FpDimens.space20 else FpDimens.space14),
            ) {
                Box(
                    modifier = Modifier
                        .width(posterWidth)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(FpDimens.radiusMd))
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
                    MediaProgressBar(progress = progress, modifier = Modifier.align(Alignment.BottomStart))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.spacedBy(FpDimens.space6),
                ) {
                    Text(
                        title,
                        style = if (tv) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.ExtraBold,
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
                    if (tv) {
                        overview?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                            )
                        }
                    }
                }
            }
            if (!tv) {
                overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                        modifier = Modifier.padding(top = FpDimens.space14),
                    )
                }
            }
            if (hasActions) {
                Row(
                    modifier = Modifier
                        .padding(top = FpDimens.space14)
                        .then(
                            if (actionsDownFocus != null) {
                                Modifier.focusProperties { down = actionsDownFocus }
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (!tv) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
                ) {
                    if (onPlay != null && playLabel != null) {
                        FpButton(onClick = onPlay, label = playLabel)
                    }
                    if (onPlayFromStart != null && playFromStartLabel != null) {
                        FpButton(
                            onClick = onPlayFromStart,
                            label = playFromStartLabel,
                            variant = FpButtonVariant.Secondary,
                        )
                    }
                    if (onToggleWatched != null && watchedLabel != null) {
                        FpButton(
                            onClick = onToggleWatched,
                            enabled = !watchedBusy,
                            label = watchedLabel,
                            variant = FpButtonVariant.Secondary,
                        )
                    }
                    if (openTrailer != null) {
                        FpButton(
                            onClick = openTrailer,
                            label = stringResource(R.string.details_trailer),
                            variant = FpButtonVariant.Secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CastSection(
    people: List<Person>,
    baseUrl: String,
    tv: Boolean,
) {
    val avatar = if (tv) 76.dp else 68.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (tv) 0.dp else FpDimens.contentPadHPhone),
    ) {
        FpSectionTitle(stringResource(R.string.details_cast))
        LazyRow(
            contentPadding = PaddingValues(horizontal = if (tv) FpDimens.focusHalo else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) FpDimens.space14 else FpDimens.space12),
            modifier = if (tv) Modifier.focusRestorer() else Modifier,
        ) {
            items(people.take(20)) { person ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(avatar + 16.dp)
                        .then(
                            if (tv) {
                                Modifier.tvFocusable(
                                    scaleFocused = 1.05f,
                                    shape = RoundedCornerShape(FpDimens.radiusMd),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(FpDimens.space4),
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
                        modifier = Modifier.padding(top = FpDimens.space4),
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

private fun progressFraction(positionMs: Long?, runtimeMs: Long?): Float {
    val position = positionMs ?: return 0f
    val runtime = runtimeMs ?: return 0f
    if (position <= 0L || runtime <= 0L) return 0f
    return (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
}

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
