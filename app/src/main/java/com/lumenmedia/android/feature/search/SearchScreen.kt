package com.lumenmedia.android.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.EditTextDialog
import com.lumenmedia.android.core.designsystem.EmptyState
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.FpSectionTitle
import com.lumenmedia.android.core.designsystem.FpTextField
import com.lumenmedia.android.core.designsystem.FullPageLoading
import com.lumenmedia.android.core.designsystem.PosterCard
import com.lumenmedia.android.core.designsystem.SettingsClickRow
import com.lumenmedia.android.core.designsystem.fpContentPadding
import com.lumenmedia.android.core.designsystem.fpPosterGap
import com.lumenmedia.android.core.designsystem.isTvDevice
import com.lumenmedia.android.core.designsystem.tvFocusable
import com.lumenmedia.android.core.model.EpisodeSummary
import com.lumenmedia.android.core.model.MediaItemSummary
import com.lumenmedia.android.core.util.artworkUrl

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    onOpenItem: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()
    var editQuery by remember { mutableStateOf(false) }

    if (editQuery) {
        EditTextDialog(
            title = stringResource(R.string.search_title),
            initialValue = state.query,
            label = stringResource(R.string.search_placeholder),
            onDismiss = { editQuery = false },
            onConfirm = {
                viewModel.onQueryChange(it)
                editQuery = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(fpContentPadding()),
    ) {
        Text(
            stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        if (tv) {
            SettingsClickRow(
                title = stringResource(R.string.search_query),
                value = state.query.ifBlank { stringResource(R.string.search_hint_tv) },
                subtitle = stringResource(R.string.search_hint_tv_subtitle),
                onClick = { editQuery = true },
                modifier = Modifier.padding(top = FpDimens.space12, bottom = FpDimens.space8),
            )
        } else {
            FpTextField(
                state.query,
                viewModel::onQueryChange,
                stringResource(R.string.search_placeholder),
                modifier = Modifier.padding(top = FpDimens.space12),
            )
        }
        when {
            state.loading -> FullPageLoading()
            state.error != null -> Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = FpDimens.space16),
            )
            state.query.isBlank() -> EmptyState(
                title = stringResource(R.string.search_empty_title),
                body = stringResource(R.string.search_empty_body),
                modifier = Modifier.weight(1f),
            )
            state.movies.isEmpty() && state.series.isEmpty() && state.episodes.isEmpty() -> EmptyState(
                title = stringResource(R.string.search_no_results),
                body = stringResource(R.string.search_no_results_body),
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(top = FpDimens.space8)) {
                if (state.movies.isNotEmpty()) {
                    item(key = "movies-header") { FpSectionTitle(stringResource(R.string.search_movies)) }
                    item(key = "movies-row") {
                        PosterResultRow(
                            items = state.movies,
                            baseUrl = state.baseUrl,
                            onOpenItem = onOpenItem,
                        )
                    }
                }
                if (state.series.isNotEmpty()) {
                    item(key = "series-header") { FpSectionTitle(stringResource(R.string.search_series)) }
                    item(key = "series-row") {
                        PosterResultRow(
                            items = state.series,
                            baseUrl = state.baseUrl,
                            onOpenItem = onOpenItem,
                        )
                    }
                }
                if (state.episodes.isNotEmpty()) {
                    item(key = "episodes-header") { FpSectionTitle(stringResource(R.string.search_episodes)) }
                    items(state.episodes, key = { it.id }) { ep ->
                        EpisodeResultRow(
                            episode = ep,
                            baseUrl = state.baseUrl,
                            onClick = { onOpenItem(ep.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PosterResultRow(
    items: List<MediaItemSummary>,
    baseUrl: String,
    onOpenItem: (String) -> Unit,
) {
    val tv = isTvDevice()
    LazyRow(
        modifier = Modifier.focusRestorer(),
        contentPadding = PaddingValues(
            horizontal = if (tv) FpDimens.focusHalo else 0.dp,
            vertical = if (tv) FpDimens.focusHalo else FpDimens.space4,
        ),
        horizontalArrangement = Arrangement.spacedBy(fpPosterGap()),
    ) {
        items(items, key = { it.id }) { item ->
            PosterCard(
                item = item,
                baseUrl = baseUrl,
                onClick = { onOpenItem(item.id) },
            )
        }
    }
}

@Composable
private fun EpisodeResultRow(
    episode: EpisodeSummary,
    baseUrl: String,
    onClick: () -> Unit,
) {
    val tv = isTvDevice()
    val thumb = artworkUrl(
        baseUrl = baseUrl,
        path = episode.artwork.thumb ?: episode.artwork.poster,
        width = 320,
        height = 180,
    )
    val shape = RoundedCornerShape(FpDimens.radiusMd)
    val episodeTitle = episode.title
        ?: stringResource(R.string.details_episode_n, episode.episodeNumber)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FpDimens.space4)
            .tvFocusable(onClick = onClick, scaleFocused = 1.015f, shape = shape)
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
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.seasonNumber}E${episode.episodeNumber}  ·  $episodeTitle",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        }
    }
}
