package com.lumenmedia.android.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumenmedia.android.core.model.EpisodeSummary
import com.lumenmedia.android.core.model.MovieDetail
import com.lumenmedia.android.core.model.ProgressRequest
import com.lumenmedia.android.core.model.Season
import com.lumenmedia.android.core.model.SeriesDetail
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.network.ItemDetailResult
import com.lumenmedia.android.core.network.toUserMessage
import com.lumenmedia.android.core.offline.OfflineDownloadManager
import com.lumenmedia.android.core.offline.OfflineEnqueueRequest
import com.lumenmedia.android.core.offline.OfflineEpisodeState
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val baseUrl: String = "",
    val movie: MovieDetail? = null,
    val series: SeriesDetail? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<EpisodeSummary> = emptyList(),
    val markingWatched: Boolean = false,
    val deletingFile: Boolean = false,
    val isAdmin: Boolean = false,
    val offlineByEpisodeId: Map<String, OfflineEpisodeState> = emptyMap(),
)

sealed interface DetailsEvent {
    /** Movie (or other top-level item) was removed — leave the details screen. */
    data object LeaveDetails : DetailsEvent
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LumenMediaRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val offlineDownloadManager: OfflineDownloadManager,
) : ViewModel() {
    private val itemId: String = checkNotNull(savedStateHandle["itemId"])
    private val _state = MutableStateFlow(DetailsUiState())
    private val _events = MutableSharedFlow<DetailsEvent>(
        replay = 1,
        extraBufferCapacity = 1,
    )

    val state: StateFlow<DetailsUiState> = combine(
        _state,
        offlineDownloadManager.entries,
    ) { ui, offline ->
        ui.copy(offlineByEpisodeId = offline.associateBy { it.episodeId })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DetailsUiState())

    val events: SharedFlow<DetailsEvent> = _events.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val baseUrl = settingsRepository.settings.first().baseUrl
            val isAdmin = sessionStore.readSession()?.role.equals("Admin", ignoreCase = true)
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
                                isAdmin = isAdmin,
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
                                    isAdmin = isAdmin,
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = err.toUserMessage("Failed to load details"),
                            isAdmin = isAdmin,
                        )
                    }
                }
        }
    }

    fun selectSeason(seasonId: String) {
        if (seasonId == _state.value.selectedSeasonId) return
        viewModelScope.launch {
            _state.update { it.copy(selectedSeasonId = seasonId, episodes = emptyList()) }
            runCatching { repository.episodes(seasonId) }
                .onSuccess { eps -> _state.update { it.copy(episodes = eps) } }
        }
    }

    fun downloadEpisode(episodeId: String) {
        val series = _state.value.series ?: return
        val episode = _state.value.episodes.firstOrNull { it.id == episodeId } ?: return
        viewModelScope.launch {
            offlineDownloadManager.enqueueEpisode(
                OfflineEnqueueRequest(
                    episodeId = episode.id,
                    seriesId = series.id,
                    seasonId = episode.seasonId.ifBlank { _state.value.selectedSeasonId.orEmpty() },
                    seriesTitle = series.title,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.title,
                ),
            )
        }
    }

    fun downloadSeason() {
        val series = _state.value.series ?: return
        val seasonId = _state.value.selectedSeasonId ?: return
        val episodes = _state.value.episodes
        if (episodes.isEmpty()) return
        viewModelScope.launch {
            offlineDownloadManager.enqueueSeason(
                seriesId = series.id,
                seriesTitle = series.title,
                seasonId = seasonId,
                episodes = episodes,
            )
        }
    }

    fun removeOfflineEpisode(episodeId: String) {
        viewModelScope.launch {
            offlineDownloadManager.remove(episodeId)
        }
    }

    fun cancelOfflineEpisode(episodeId: String) {
        viewModelScope.launch {
            offlineDownloadManager.cancel(episodeId)
        }
    }

    fun toggleMovieWatched() {
        val movie = _state.value.movie ?: return
        val next = movie.userData.watched != true
        setWatched(movie.id, next) {
            _state.update {
                it.copy(
                    movie = movie.copy(
                        userData = movie.userData.copy(
                            watched = next,
                            playbackPositionMs = if (next) 0L else movie.userData.playbackPositionMs,
                        ),
                    ),
                )
            }
        }
    }

    fun toggleSeriesWatched() {
        val series = _state.value.series ?: return
        val next = !isSeriesWatched(series)
        setWatched(series.id, next) {
            applyEpisodeWatched(watched = next)
            _state.update {
                val unwatched = if (next) 0 else (series.episodeCount)
                it.copy(
                    series = series.copy(
                        userData = series.userData.copy(unwatchedEpisodeCount = unwatched),
                    ),
                )
            }
        }
    }

    fun toggleSeasonWatched() {
        val seasonId = _state.value.selectedSeasonId ?: return
        val episodes = _state.value.episodes
        if (episodes.isEmpty()) return
        val next = !episodes.all { it.userData.watched == true }
        setWatched(seasonId, next) {
            applyEpisodeWatched(watched = next)
            refreshSeriesUnwatchedCount()
        }
    }

    fun toggleEpisodeWatched(episodeId: String) {
        val episode = _state.value.episodes.firstOrNull { it.id == episodeId } ?: return
        val next = episode.userData.watched != true
        setWatched(episodeId, next) {
            _state.update { state ->
                state.copy(
                    episodes = state.episodes.map { ep ->
                        if (ep.id != episodeId) ep
                        else ep.copy(
                            userData = ep.userData.copy(
                                watched = next,
                                playbackPositionMs = if (next) 0L else ep.userData.playbackPositionMs,
                            ),
                        )
                    },
                )
            }
            refreshSeriesUnwatchedCount()
        }
    }

    fun deleteMovieFile() {
        val movie = _state.value.movie ?: return
        if (!_state.value.isAdmin || movie.mediaSources.isEmpty()) return
        deleteMediaFile(movie.id, leaveOnRemoved = true) {
            _state.update { it.copy(movie = movie.copy(mediaSources = emptyList())) }
        }
    }

    fun deleteEpisodeFile(episodeId: String) {
        if (!_state.value.isAdmin) return
        if (_state.value.episodes.none { it.id == episodeId }) return
        deleteMediaFile(episodeId, leaveOnRemoved = false) {
            _state.update { state ->
                state.copy(episodes = state.episodes.filterNot { it.id == episodeId })
            }
            refreshSeriesUnwatchedCount()
        }
    }

    private fun deleteMediaFile(
        mediaId: String,
        leaveOnRemoved: Boolean,
        onSourcesCleared: () -> Unit,
    ) {
        if (_state.value.deletingFile) return
        viewModelScope.launch {
            _state.update { it.copy(deletingFile = true, error = null) }
            runCatching { repository.deleteMediaFile(mediaId) }
                .onSuccess { result ->
                    _state.update { it.copy(deletingFile = false) }
                    if (result.mediaRemoved && leaveOnRemoved) {
                        _events.emit(DetailsEvent.LeaveDetails)
                    } else {
                        onSourcesCleared()
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            deletingFile = false,
                            error = err.toUserMessage("Failed to delete media file"),
                        )
                    }
                }
        }
    }

    private fun setWatched(targetId: String, watched: Boolean, onSuccess: () -> Unit) {
        if (_state.value.markingWatched) return
        viewModelScope.launch {
            _state.update { it.copy(markingWatched = true, error = null) }
            runCatching {
                repository.putProgress(targetId, ProgressRequest(watched = watched))
            }.onSuccess {
                onSuccess()
                _state.update { it.copy(markingWatched = false) }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        markingWatched = false,
                        error = err.toUserMessage("Failed to update watched status"),
                    )
                }
            }
        }
    }

    private fun applyEpisodeWatched(watched: Boolean) {
        _state.update { state ->
            state.copy(
                episodes = state.episodes.map { ep ->
                    ep.copy(
                        userData = ep.userData.copy(
                            watched = watched,
                            playbackPositionMs = if (watched) 0L else ep.userData.playbackPositionMs,
                        ),
                    )
                },
            )
        }
    }

    private fun refreshSeriesUnwatchedCount() {
        val series = _state.value.series ?: return
        val seasonId = _state.value.selectedSeasonId
        viewModelScope.launch {
            runCatching { repository.itemDetail(series.id) }
                .onSuccess { detail ->
                    if (detail is ItemDetailResult.Series) {
                        _state.update { it.copy(series = detail.value) }
                    }
                }
            if (seasonId != null) {
                runCatching { repository.episodes(seasonId) }
                    .onSuccess { eps -> _state.update { it.copy(episodes = eps) } }
            }
        }
    }

    companion object {
        fun isSeriesWatched(series: SeriesDetail): Boolean =
            series.episodeCount > 0 && (series.userData.unwatchedEpisodeCount ?: series.episodeCount) == 0

        fun isSeasonWatched(episodes: List<EpisodeSummary>): Boolean =
            episodes.isNotEmpty() && episodes.all { it.userData.watched == true }
    }
}
