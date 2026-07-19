package com.freeplex.android.feature.search

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freeplex.android.core.designsystem.FpTextField
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.PosterCard
import com.freeplex.android.core.designsystem.TvContentPadding
import com.freeplex.android.core.designsystem.TvDimens
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable
import com.freeplex.android.core.model.EpisodeSummary
import com.freeplex.android.core.model.MediaItemSummary
import com.freeplex.android.core.util.artworkUrl

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    onOpenItem: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (tv) TvContentPadding else PaddingValues(16.dp)),
    ) {
        Text(
            "Search",
            style = if (tv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        FpTextField(state.query, viewModel::onQueryChange, "Movies, shows…")
        when {
            state.loading -> FullPageLoading()
            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                if (state.movies.isNotEmpty()) {
                    item(key = "movies-header") { SectionHeader("Movies") }
                    item(key = "movies-row") {
                        PosterResultRow(
                            items = state.movies,
                            baseUrl = state.baseUrl,
                            tv = tv,
                            onOpenItem = onOpenItem,
                        )
                    }
                }
                if (state.series.isNotEmpty()) {
                    item(key = "series-header") { SectionHeader("Series") }
                    item(key = "series-row") {
                        PosterResultRow(
                            items = state.series,
                            baseUrl = state.baseUrl,
                            tv = tv,
                            onOpenItem = onOpenItem,
                        )
                    }
                }
                if (state.episodes.isNotEmpty()) {
                    item(key = "episodes-header") { SectionHeader("Episodes") }
                    items(state.episodes, key = { it.id }) { ep ->
                        EpisodeResultRow(
                            episode = ep,
                            baseUrl = state.baseUrl,
                            tv = tv,
                            onClick = { onOpenItem(ep.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PosterResultRow(
    items: List<MediaItemSummary>,
    baseUrl: String,
    tv: Boolean,
    onOpenItem: (String) -> Unit,
) {
    LazyRow(
        // Re-entering the row with D-pad restores focus to the last card.
        modifier = Modifier.focusRestorer(),
        contentPadding = PaddingValues(
            horizontal = if (tv) TvDimens.focusHalo else 0.dp,
            vertical = if (tv) TvDimens.focusHalo else 4.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(if (tv) TvDimens.posterGap else 10.dp),
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
    tv: Boolean,
    onClick: () -> Unit,
) {
    val thumb = artworkUrl(
        baseUrl = baseUrl,
        path = episode.artwork.thumb ?: episode.artwork.poster,
        width = 320,
        height = 180,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, scaleFocused = 1.015f)
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
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.seasonNumber}E${episode.episodeNumber}  ·  " +
                    (episode.title ?: "Episode ${episode.episodeNumber}"),
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
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
