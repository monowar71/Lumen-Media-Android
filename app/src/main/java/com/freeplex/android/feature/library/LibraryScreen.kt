package com.freeplex.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                onValueChange = {
                    viewModel.onQueryChange(it)
                    viewModel.refresh()
                },
                label = "Filter",
            )
        }
        if (state.items.isEmpty()) {
            EmptyState(title = "Nothing here", body = "Try another library or scan media files.")
        } else {
            LazyVerticalGrid(
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
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        baseUrl = state.baseUrl,
                        accessToken = state.accessToken,
                        onClick = { onOpenItem(item.id) },
                    )
                }
            }
        }
    }
}
