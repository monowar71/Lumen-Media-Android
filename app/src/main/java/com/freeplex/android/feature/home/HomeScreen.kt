package com.freeplex.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freeplex.android.core.designsystem.EmptyState
import com.freeplex.android.core.designsystem.ErrorState
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.PosterCard
import com.freeplex.android.core.designsystem.TvContentPadding
import com.freeplex.android.core.designsystem.TvDimens
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable
import com.freeplex.android.core.model.MediaItemSummary
import com.freeplex.android.core.util.artworkUrl

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    onOpenItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()
    when {
        state.loading -> FullPageLoading()
        state.error != null -> ErrorState(state.error!!, onRetry = viewModel::refresh)
        state.sections.isEmpty() -> EmptyState(
            title = "Your library is empty",
            body = "Add a library in Settings and scan media files.",
        )
        else -> {
            val hero = state.sections.firstOrNull()?.items?.firstOrNull()
            val heroFocus = remember { FocusRequester() }
            // Saveable so it survives back-navigation from Details: on return
            // the rows' focusRestorer must win, not the hero grabbing focus again.
            var heroFocusRequested by rememberSaveable { mutableStateOf(false) }
            if (tv && hero != null) {
                // Land D-pad focus on the content (hero) instead of the sidebar,
                // on the first entry only. requestFocus throws if the node is
                // not attached yet, hence runCatching.
                LaunchedEffect(Unit) {
                    if (!heroFocusRequested) {
                        heroFocusRequested = true
                        runCatching { heroFocus.requestFocus() }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = if (tv) {
                    PaddingValues(
                        start = TvDimens.contentPadH,
                        end = TvDimens.contentPadH,
                        top = TvDimens.contentPadV,
                        bottom = TvDimens.contentPadV + TvDimens.focusHalo,
                    )
                } else {
                    PaddingValues(vertical = 12.dp)
                },
            ) {
                if (tv && hero != null) {
                    item {
                        HomeHero(
                            item = hero,
                            baseUrl = state.baseUrl,
                            onOpen = { onOpenItem(hero.id) },
                            modifier = Modifier.focusRequester(heroFocus),
                        )
                        Spacer(Modifier.height(TvDimens.sectionGap))
                    }
                }
                itemsIndexed(state.sections) { index, section ->
                    val items = if (tv && index == 0 && hero != null) {
                        section.items.drop(1).ifEmpty { section.items }
                    } else {
                        section.items
                    }
                    if (items.isEmpty()) return@itemsIndexed
                    Column(modifier = Modifier.padding(bottom = if (tv) TvDimens.sectionGap else 16.dp)) {
                        Text(
                            section.title,
                            style = if (tv) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                horizontal = if (tv) 0.dp else 16.dp,
                                vertical = if (tv) 2.dp else 8.dp,
                            ),
                        )
                        LazyRow(
                            // Re-entering the row with D-pad restores focus to
                            // the card that was focused before opening details.
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(
                                horizontal = if (tv) TvDimens.focusHalo else 16.dp,
                                vertical = if (tv) TvDimens.focusHalo else 0.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(
                                if (tv) TvDimens.posterGap else 10.dp,
                            ),
                        ) {
                            items(items, key = { it.id }) { item ->
                                PosterCard(
                                    item = item,
                                    baseUrl = state.baseUrl,
                                    onClick = { onOpenItem(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    item: MediaItemSummary,
    baseUrl: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = item.artwork.backdrop ?: item.artwork.poster ?: item.artwork.thumb
    val model = artworkUrl(
        baseUrl = baseUrl,
        path = backdrop,
        width = 1280,
        height = 720,
        quality = 70,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvDimens.heroHeight)
            .clip(RoundedCornerShape(TvDimens.corner))
            .tvFocusable(onClick = onOpen, scaleFocused = 1.015f),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .fillMaxWidth(0.52f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(item.year?.toString(), item.officialRating, item.genres?.take(2)?.joinToString(", "))
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            Button(
                onClick = onOpen,
                modifier = Modifier.tvFocusable(onClick = onOpen, scaleFocused = 1.04f),
            ) {
                Text("Open", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
