package com.lumenmedia.android.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.EditTextDialog
import com.lumenmedia.android.core.designsystem.EmptyState
import com.lumenmedia.android.core.designsystem.ErrorState
import com.lumenmedia.android.core.designsystem.FpChip
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.FpTextField
import com.lumenmedia.android.core.designsystem.FullPageLoading
import com.lumenmedia.android.core.designsystem.PosterCard
import com.lumenmedia.android.core.designsystem.SettingsChoiceRow
import com.lumenmedia.android.core.designsystem.SettingsClickRow
import com.lumenmedia.android.core.designsystem.fpContentPadding
import com.lumenmedia.android.core.designsystem.fpPosterGap
import com.lumenmedia.android.core.designsystem.isTvDevice
import com.lumenmedia.android.core.preferences.LibraryOrder
import com.lumenmedia.android.core.preferences.LibrarySort
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryScreen(
    onOpenItem: (String) -> Unit,
    onSelectLibrary: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()
    val minCell = if (tv) FpDimens.gridMinCellTv else FpDimens.gridMinCellPhone
    var sortFilterDialogOpen by remember { mutableStateOf(false) }

    if (sortFilterDialogOpen) {
        SortAndFiltersDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { sortFilterDialogOpen = false },
        )
    }

    if (state.loading) {
        FullPageLoading()
        return
    }
    if (state.error != null) {
        ErrorState(message = state.error!!, onRetry = viewModel::refresh)
        return
    }

    val activeFilterCount = listOfNotNull(
        state.query.takeIf { it.isNotBlank() },
        state.genre,
        state.year.takeIf { it.isNotBlank() },
        state.watchedFilter.takeIf { it != WatchedFilter.All },
    ).size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(fpContentPadding()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FpDimens.space12),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.library?.name ?: stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp,
                )
                state.library?.let { lib ->
                    Text(
                        text = stringResource(R.string.library_items_count, lib.itemCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = FpDimens.space2),
                    )
                }
            }
            // TV: one entry point keeps the poster grid dominant.
            if (tv) {
                FpChip(
                    label = if (activeFilterCount > 0) {
                        stringResource(R.string.library_sort_and_filters_count, activeFilterCount)
                    } else {
                        stringResource(R.string.library_sort_and_filters)
                    },
                    selected = activeFilterCount > 0,
                    onClick = { sortFilterDialogOpen = true },
                )
            }
        }

        if (!tv && state.libraries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = FpDimens.space12, bottom = FpDimens.space12),
                horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
            ) {
                state.libraries.forEach { lib ->
                    FpChip(
                        label = lib.name,
                        selected = lib.id == state.library?.id,
                        onClick = { onSelectLibrary(lib.id) },
                    )
                }
            }
        } else {
            Box(Modifier.padding(top = FpDimens.space10))
        }

        if (!tv) {
            SortChipsRow(state = state, viewModel = viewModel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = FpDimens.space8),
                horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
            ) {
                FpChip(
                    label = if (activeFilterCount > 0) {
                        "${stringResource(R.string.library_filters)} ($activeFilterCount)"
                    } else {
                        stringResource(R.string.library_filters)
                    },
                    selected = state.filtersOpen || activeFilterCount > 0,
                    onClick = viewModel::toggleFiltersOpen,
                )
            }
            if (state.filtersOpen) {
                PhoneFiltersPanel(state = state, viewModel = viewModel)
            }
        }

        if (state.items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.library_no_match),
                body = stringResource(R.string.library_empty_body),
            )
        } else {
            val gridState = rememberLazyGridState()
            LaunchedEffect(gridState) {
                snapshotFlow {
                    val info = gridState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    lastVisible >= info.totalItemsCount - 10
                }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { viewModel.loadMore() }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = minCell),
                contentPadding = PaddingValues(
                    top = if (tv) FpDimens.focusHalo else FpDimens.space4,
                    bottom = if (tv) 28.dp else FpDimens.space16,
                    start = if (tv) FpDimens.focusHalo else 0.dp,
                    end = if (tv) FpDimens.focusHalo + 8.dp else 0.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(fpPosterGap()),
                verticalArrangement = Arrangement.spacedBy(if (tv) FpDimens.space14 else FpDimens.space16),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        baseUrl = state.baseUrl,
                        onClick = { onOpenItem(item.id) },
                        fixedWidth = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.loadingMore) {
                    item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = FpDimens.space16),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortAndFiltersDialog(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    var pickGenre by remember { mutableStateOf(false) }
    var pickYear by remember { mutableStateOf(false) }

    if (pickGenre) {
        GenrePickDialog(
            selected = state.genre,
            onDismiss = { pickGenre = false },
            onSelect = {
                viewModel.onGenreChange(it)
                pickGenre = false
            },
        )
    }
    if (pickYear) {
        EditTextDialog(
            title = stringResource(R.string.library_year),
            initialValue = state.year,
            label = stringResource(R.string.library_year),
            onDismiss = { pickYear = false },
            onConfirm = {
                viewModel.onYearChange(it)
                pickYear = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_sort_and_filters)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FpDimens.space10),
            ) {
                Text(
                    text = stringResource(R.string.library_sort_by),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowChipRow {
                    LibrarySort.entries.forEach { sort ->
                        FpChip(
                            label = stringResource(sortLabelRes(sort)),
                            selected = state.sort == sort,
                            onClick = { viewModel.onSortChange(sort) },
                        )
                    }
                    FpChip(
                        label = if (state.order == LibraryOrder.Asc) {
                            stringResource(R.string.library_order_asc)
                        } else {
                            stringResource(R.string.library_order_desc)
                        },
                        selected = true,
                        onClick = {
                            viewModel.onOrderChange(
                                if (state.order == LibraryOrder.Asc) {
                                    LibraryOrder.Desc
                                } else {
                                    LibraryOrder.Asc
                                },
                            )
                        },
                    )
                    FpChip(
                        label = stringResource(R.string.library_in_progress),
                        selected = state.inProgressFirst,
                        onClick = { viewModel.onInProgressFirstChange(!state.inProgressFirst) },
                    )
                }

                SettingsClickRow(
                    title = stringResource(R.string.library_genre),
                    value = state.genre?.let { api ->
                        LibraryGenre.entries.find { it.apiValue == api }
                            ?.let { stringResource(it.labelRes) } ?: api
                    } ?: stringResource(R.string.library_all_genres),
                    onClick = { pickGenre = true },
                )
                SettingsClickRow(
                    title = stringResource(R.string.library_year),
                    value = state.year.ifBlank { stringResource(R.string.library_all) },
                    onClick = { pickYear = true },
                )
                SettingsChoiceRow(
                    title = stringResource(R.string.library_watched_filter),
                    options = WatchedFilter.entries.map {
                        it.name to stringResource(watchedLabelRes(it))
                    },
                    selectedId = state.watchedFilter.name,
                    onSelect = { id ->
                        WatchedFilter.entries.find { it.name == id }
                            ?.let(viewModel::onWatchedFilterChange)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_done))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.onGenreChange(null)
                    viewModel.onYearChange("")
                    viewModel.onWatchedFilterChange(WatchedFilter.All)
                    viewModel.onQueryChange("")
                },
            ) {
                Text(stringResource(R.string.library_clear_filters))
            }
        },
    )
}

/** Simple wrapping chip row without FlowLayout dependency. */
@Composable
private fun FlowChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
    ) {
        content()
    }
}

@Composable
private fun SortChipsRow(state: LibraryUiState, viewModel: LibraryViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = FpDimens.space10),
        horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
    ) {
        LibrarySort.entries.forEach { sort ->
            FpChip(
                label = stringResource(sortLabelRes(sort)),
                selected = state.sort == sort,
                onClick = { viewModel.onSortChange(sort) },
            )
        }
        FpChip(
            label = if (state.order == LibraryOrder.Asc) {
                stringResource(R.string.library_order_asc)
            } else {
                stringResource(R.string.library_order_desc)
            },
            selected = true,
            onClick = {
                viewModel.onOrderChange(
                    if (state.order == LibraryOrder.Asc) LibraryOrder.Desc else LibraryOrder.Asc,
                )
            },
        )
        FpChip(
            label = stringResource(R.string.library_in_progress),
            selected = state.inProgressFirst,
            onClick = { viewModel.onInProgressFirstChange(!state.inProgressFirst) },
        )
    }
}

@Composable
private fun PhoneFiltersPanel(state: LibraryUiState, viewModel: LibraryViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = FpDimens.space12),
        verticalArrangement = Arrangement.spacedBy(FpDimens.space8),
    ) {
        FpTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = stringResource(R.string.library_filter_placeholder),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
        ) {
            FpChip(
                label = stringResource(R.string.library_all_genres),
                selected = state.genre == null,
                onClick = { viewModel.onGenreChange(null) },
            )
            LibraryGenre.entries.forEach { g ->
                FpChip(
                    label = stringResource(g.labelRes),
                    selected = state.genre == g.apiValue,
                    onClick = { viewModel.onGenreChange(g.apiValue) },
                )
            }
        }
        FpTextField(
            value = state.year,
            onValueChange = viewModel::onYearChange,
            label = stringResource(R.string.library_year),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FpDimens.space8)) {
            WatchedFilter.entries.forEach { filter ->
                FpChip(
                    label = stringResource(watchedLabelRes(filter)),
                    selected = state.watchedFilter == filter,
                    onClick = { viewModel.onWatchedFilterChange(filter) },
                )
            }
        }
    }
}

@Composable
private fun GenrePickDialog(
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_genre)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FpDimens.space8),
            ) {
                FpChip(
                    label = stringResource(R.string.library_all_genres),
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
                LibraryGenre.entries.forEach { g ->
                    FpChip(
                        label = stringResource(g.labelRes),
                        selected = selected == g.apiValue,
                        onClick = { onSelect(g.apiValue) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@StringRes
private fun sortLabelRes(sort: LibrarySort): Int =
    when (sort) {
        LibrarySort.Title -> R.string.library_sort_title
        LibrarySort.Year -> R.string.library_sort_year
        LibrarySort.Added -> R.string.library_sort_added
        LibrarySort.Rating -> R.string.library_sort_rating
        LibrarySort.Runtime -> R.string.library_sort_runtime
    }

@StringRes
private fun watchedLabelRes(filter: WatchedFilter): Int =
    when (filter) {
        WatchedFilter.All -> R.string.library_all
        WatchedFilter.Watched -> R.string.library_watched
        WatchedFilter.Unwatched -> R.string.library_unwatched
    }
