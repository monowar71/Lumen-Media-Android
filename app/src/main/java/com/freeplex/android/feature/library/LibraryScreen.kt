package com.freeplex.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeplex.android.core.designsystem.EmptyState
import com.freeplex.android.core.designsystem.ErrorState
import com.freeplex.android.core.designsystem.FpTextField
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.PosterCard
import com.freeplex.android.core.designsystem.TvContentPadding
import com.freeplex.android.core.designsystem.TvDimens
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable
import com.freeplex.android.core.preferences.LibrarySort
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
    // Cell sized to poster + focus halo so titles/edges are not clipped.
    val minCell = if (tv) TvDimens.gridMinCell else 120.dp

    if (state.loading) {
        FullPageLoading()
        return
    }
    if (state.error != null) {
        ErrorState(message = state.error!!, onRetry = viewModel::refresh)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (tv) TvContentPadding else PaddingValues(horizontal = 12.dp)),
    ) {
        Text(
            text = state.library?.name ?: "Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = if (tv) 6.dp else 12.dp),
        )
        // Phone keeps chips; TV switches libraries from the left sidebar.
        if (!tv && state.libraries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.libraries.forEach { lib ->
                    val selected = lib.id == state.library?.id
                    Text(
                        text = lib.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .tvFocusable(
                                onClick = { onSelectLibrary(lib.id) },
                                scaleFocused = 1.06f,
                                shape = RoundedCornerShape(28.dp),
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                }
            }
        }
        if (!tv) {
            FpTextField(
                value = state.query,
                // Reload is debounced inside the ViewModel.
                onValueChange = viewModel::onQueryChange,
                label = "Filter",
            )
        }
        // Sort controls: mirror the web client's library toolbar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = if (tv) 0.dp else 10.dp, bottom = if (tv) 8.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortChip(
                label = "By name",
                selected = state.sort == LibrarySort.Name,
                onClick = { viewModel.onSortChange(LibrarySort.Name) },
            )
            SortChip(
                label = "Recently added",
                selected = state.sort == LibrarySort.Added,
                onClick = { viewModel.onSortChange(LibrarySort.Added) },
            )
            SortChip(
                label = "In progress first",
                selected = state.inProgressFirst,
                onClick = { viewModel.onInProgressFirstChange(!state.inProgressFirst) },
            )
        }
        if (state.items.isEmpty()) {
            EmptyState(title = "Nothing here", body = "Try another library or scan media files.")
        } else {
            val gridState = rememberLazyGridState()
            // Fetch the next page when scrolling (touch or D-pad) approaches the end.
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
                    top = if (tv) TvDimens.focusHalo else 4.dp,
                    bottom = if (tv) 28.dp else 16.dp,
                    start = if (tv) TvDimens.focusHalo else 0.dp,
                    // Extra end inset — Adaptive grids otherwise kiss the right overscan edge.
                    end = if (tv) TvDimens.focusHalo + 8.dp else 0.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (tv) TvDimens.posterGap else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 14.dp),
                // Restore D-pad focus to the last focused card when the user
                // comes back from details instead of resetting to the sidebar.
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        baseUrl = state.baseUrl,
                        onClick = { onOpenItem(item.id) },
                        // Adaptive cells are usually wider than the minimum;
                        // stretch the card so the grid does not look ragged.
                        fixedWidth = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.loadingMore) {
                    item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .tvFocusable(
                onClick = onClick,
                scaleFocused = 1.06f,
                shape = RoundedCornerShape(20.dp),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
