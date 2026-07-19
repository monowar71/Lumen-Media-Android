package com.freeplex.android.feature.library

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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeplex.android.core.designsystem.EmptyState
import com.freeplex.android.core.designsystem.ErrorState
import com.freeplex.android.core.designsystem.FpChip
import com.freeplex.android.core.designsystem.FpDimens
import com.freeplex.android.core.designsystem.FpTextField
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.PosterCard
import com.freeplex.android.core.designsystem.fpContentPadding
import com.freeplex.android.core.designsystem.fpPosterGap
import com.freeplex.android.core.designsystem.isTvDevice
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
    val minCell = if (tv) FpDimens.gridMinCellTv else FpDimens.gridMinCellPhone

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
            .padding(fpContentPadding()),
    ) {
        Text(
            text = state.library?.name ?: "Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.2).sp,
        )
        state.library?.let { lib ->
            Text(
                text = "${lib.itemCount} titles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = FpDimens.space2, bottom = FpDimens.space12),
            )
        } ?: SpacerBottom()

        if (!tv && state.libraries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = FpDimens.space12),
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
        }
        if (!tv) {
            FpTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = "Filter",
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = if (tv) 0.dp else FpDimens.space10, bottom = FpDimens.space10),
            horizontalArrangement = Arrangement.spacedBy(FpDimens.space8),
        ) {
            FpChip(
                label = "Name",
                selected = state.sort == LibrarySort.Name,
                onClick = { viewModel.onSortChange(LibrarySort.Name) },
            )
            FpChip(
                label = "Recently added",
                selected = state.sort == LibrarySort.Added,
                onClick = { viewModel.onSortChange(LibrarySort.Added) },
            )
            FpChip(
                label = "In progress",
                selected = state.inProgressFirst,
                onClick = { viewModel.onInProgressFirstChange(!state.inProgressFirst) },
            )
        }
        if (state.items.isEmpty()) {
            EmptyState(title = "Nothing here", body = "Try another library or scan media files.")
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
private fun SpacerBottom() {
    Box(Modifier.padding(bottom = FpDimens.space12))
}
