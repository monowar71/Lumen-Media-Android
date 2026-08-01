package com.lumenmedia.android.feature.home

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.EmptyState
import com.lumenmedia.android.core.designsystem.ErrorState
import com.lumenmedia.android.core.designsystem.FpButton
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.FpSectionTitle
import com.lumenmedia.android.core.designsystem.FullPageLoading
import com.lumenmedia.android.core.designsystem.PosterCard
import com.lumenmedia.android.core.designsystem.fpPosterGap
import com.lumenmedia.android.core.designsystem.isTvDevice
import com.lumenmedia.android.core.designsystem.tvFocusable
import com.lumenmedia.android.core.model.MediaItemSummary
import com.lumenmedia.android.core.util.artworkUrl

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
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
        )
        else -> {
            val hero = state.sections.firstOrNull()?.items?.firstOrNull()
            val heroFocus = remember { FocusRequester() }
            var heroFocusRequested by rememberSaveable { mutableStateOf(false) }
            if (tv && hero != null) {
                LaunchedEffect(Unit) {
                    if (!heroFocusRequested) {
                        heroFocusRequested = true
                        runCatching { heroFocus.requestFocus() }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (tv) FpDimens.contentPadHTv else 0.dp,
                    end = if (tv) FpDimens.contentPadHTv else 0.dp,
                    top = if (tv) FpDimens.contentPadVTv else 0.dp,
                    bottom = if (tv) FpDimens.contentPadVTv + FpDimens.focusHalo else FpDimens.space16,
                ),
            ) {
                if (hero != null) {
                    item(key = "hero") {
                        HomeHero(
                            item = hero,
                            baseUrl = state.baseUrl,
                            onOpen = { onOpenItem(hero.id) },
                            modifier = if (tv) Modifier.focusRequester(heroFocus) else Modifier,
                            compact = !tv,
                        )
                        Spacer(Modifier.height(if (tv) FpDimens.space16 else FpDimens.space12))
                    }
                }
                itemsIndexed(state.sections) { index, section ->
                    val items = if (index == 0 && hero != null) {
                        section.items.drop(1).ifEmpty { section.items }
                    } else {
                        section.items
                    }
                    if (items.isEmpty()) return@itemsIndexed
                    Column(
                        modifier = Modifier.padding(
                            bottom = if (tv) FpDimens.space16 else FpDimens.space20,
                        ),
                    ) {
                        FpSectionTitle(
                            title = section.title,
                            modifier = Modifier.padding(
                                horizontal = if (tv) 0.dp else FpDimens.contentPadHPhone,
                            ),
                        )
                        LazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(
                                horizontal = if (tv) FpDimens.focusHalo else FpDimens.contentPadHPhone,
                                vertical = if (tv) FpDimens.focusHalo else 0.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(fpPosterGap()),
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
    compact: Boolean = false,
) {
    val tv = isTvDevice()
    val backdrop = item.artwork.backdrop ?: item.artwork.poster ?: item.artwork.thumb
    val model = artworkUrl(
        baseUrl = baseUrl,
        path = backdrop,
        width = 1280,
        height = 720,
        quality = 70,
    )
    val shape = if (compact) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(FpDimens.radiusLg)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) FpDimens.heroPhone else FpDimens.heroTv)
            .then(if (!compact) Modifier.clip(shape) else Modifier)
            .tvFocusable(onClick = onOpen, scaleFocused = if (tv) 1.015f else 1f, shape = shape),
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
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    horizontal = if (compact) FpDimens.contentPadHPhone else FpDimens.space20,
                    vertical = if (compact) FpDimens.space16 else FpDimens.space16,
                )
                .fillMaxWidth(if (tv) 0.55f else 0.92f),
            verticalArrangement = Arrangement.spacedBy(FpDimens.space6),
        ) {
            Text(
                text = stringResource(R.string.home_featured).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.6.sp,
            )
            Text(
                text = item.title,
                style = if (tv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
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
            FpButton(onClick = onOpen, label = stringResource(R.string.home_details))
        }
    }
}
