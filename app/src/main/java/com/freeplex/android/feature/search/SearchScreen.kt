package com.freeplex.android.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeplex.android.core.designsystem.FpTextField
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.TvContentPadding
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable

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
            .padding(if (tv) TvContentPadding else androidx.compose.foundation.layout.PaddingValues(16.dp)),
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
                    item {
                        Text(
                            "Movies",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(state.movies, key = { it.id }) { item ->
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusable(onClick = { onOpenItem(item.id) })
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                        )
                    }
                }
                if (state.series.isNotEmpty()) {
                    item {
                        Text(
                            "Series",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(state.series, key = { it.id }) { item ->
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusable(onClick = { onOpenItem(item.id) })
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                        )
                    }
                }
                if (state.episodes.isNotEmpty()) {
                    item {
                        Text(
                            "Episodes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(state.episodes, key = { it.id }) { ep ->
                        Text(
                            "S${ep.seasonNumber}E${ep.episodeNumber} · ${ep.title ?: ""}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusable(onClick = { onOpenItem(ep.id) })
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
