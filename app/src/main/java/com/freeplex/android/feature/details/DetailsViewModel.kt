package com.freeplex.android.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeplex.android.core.model.EpisodeSummary
import com.freeplex.android.core.model.MovieDetail
import com.freeplex.android.core.model.Season
import com.freeplex.android.core.model.SeriesDetail
import com.freeplex.android.core.network.FreePlexRepository
import com.freeplex.android.core.network.ItemDetailResult
import com.freeplex.android.core.network.toUserMessage
import com.freeplex.android.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val baseUrl: String = "",
    val accessToken: String? = null,
    val movie: MovieDetail? = null,
    val series: SeriesDetail? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<EpisodeSummary> = emptyList(),
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FreePlexRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: com.freeplex.android.core.preferences.SessionStore,
) : ViewModel() {
    private val itemId: String = checkNotNull(savedStateHandle["itemId"])
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val baseUrl = settingsRepository.settings.first().baseUrl
            val token = sessionStore.accessToken
            runCatching { repository.itemDetail(itemId) }
                .onSuccess { detail ->
                    when (detail) {
                        is ItemDetailResult.Movie -> _state.update {
                            it.copy(
                                loading = false,
                                movie = detail.value,
                                series = null,
                                seasons = emptyList(),
                                episodes = emptyList(),
                                baseUrl = baseUrl,
                                accessToken = token,
                            )
                        }
                        is ItemDetailResult.Series -> {
                            val seasons = repository.seasons(itemId)
                            val first = seasons.firstOrNull()
                            val episodes = if (first != null) repository.episodes(first.id) else emptyList()
                            _state.update {
                                it.copy(
                                    loading = false,
                                    series = detail.value,
                                    movie = null,
                                    seasons = seasons,
                                    selectedSeasonId = first?.id,
                                    episodes = episodes,
                                    baseUrl = baseUrl,
                                    accessToken = token,
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(loading = false, error = err.toUserMessage("Failed to load details"))
                    }
                }
        }
    }

    fun selectSeason(seasonId: String) {
        viewModelScope.launch {
            _state.update { it.copy(selectedSeasonId = seasonId) }
            runCatching { repository.episodes(seasonId) }
                .onSuccess { eps -> _state.update { it.copy(episodes = eps) } }
        }
    }
}
