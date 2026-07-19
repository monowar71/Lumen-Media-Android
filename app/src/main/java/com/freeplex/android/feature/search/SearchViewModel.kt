package com.freeplex.android.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeplex.android.core.model.EpisodeSummary
import com.freeplex.android.core.model.MediaItemSummary
import com.freeplex.android.core.network.FreePlexRepository
import com.freeplex.android.core.network.toUserMessage
import com.freeplex.android.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val movies: List<MediaItemSummary> = emptyList(),
    val series: List<MediaItemSummary> = emptyList(),
    val episodes: List<EpisodeSummary> = emptyList(),
    val baseUrl: String = "",
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FreePlexRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        job?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(movies = emptyList(), series = emptyList(), episodes = emptyList(), error = null) }
            return
        }
        job = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(loading = true, error = null) }
            val baseUrl = settingsRepository.settings.first().baseUrl
            runCatching { repository.search(q) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            loading = false,
                            movies = result.movies,
                            series = result.series,
                            episodes = result.episodes,
                            baseUrl = baseUrl,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, error = err.toUserMessage("Search failed")) }
                }
        }
    }
}
