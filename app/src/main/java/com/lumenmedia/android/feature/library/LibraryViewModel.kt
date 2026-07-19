package com.lumenmedia.android.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumenmedia.android.core.library.LibraryCatalog
import com.lumenmedia.android.core.model.LibraryDto
import com.lumenmedia.android.core.model.MediaItemSummary
import com.lumenmedia.android.core.model.PagedResult
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.network.toUserMessage
import com.lumenmedia.android.core.preferences.LibrarySort
import com.lumenmedia.android.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val library: LibraryDto? = null,
    val items: List<MediaItemSummary> = emptyList(),
    val libraries: List<LibraryDto> = emptyList(),
    val baseUrl: String = "",
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Added,
    val inProgressFirst: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: LumenMediaRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryCatalog: LibraryCatalog,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var queryJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _state.update {
                it.copy(sort = settings.librarySort, inProgressFirst = settings.libraryInProgressFirst)
            }
            val initialId = checkNotNull(savedStateHandle.get<String>("libraryId"))
            savedStateHandle.getStateFlow("libraryId", initialId)
                .collectLatest { id -> load(id) }
        }
    }

    fun onQueryChange(q: String) {
        if (q == _state.value.query) return
        _state.update { it.copy(query = q) }
        // Debounce so typing does not fire one request per keystroke.
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(300)
            currentLibraryId()?.let { load(it) }
        }
    }

    fun onSortChange(sort: LibrarySort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        viewModelScope.launch {
            settingsRepository.setLibrarySort(sort)
            currentLibraryId()?.let { load(it) }
        }
    }

    fun onInProgressFirstChange(enabled: Boolean) {
        if (enabled == _state.value.inProgressFirst) return
        _state.update { it.copy(inProgressFirst = enabled, items = orderItems(it.items, enabled)) }
        viewModelScope.launch { settingsRepository.setLibraryInProgressFirst(enabled) }
    }

    fun refresh() {
        val id = currentLibraryId() ?: return
        queryJob?.cancel()
        viewModelScope.launch { load(id) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        val libraryId = currentLibraryId() ?: return
        val nextPage = current.page + 1
        val query = current.query.ifBlank { null }
        _state.update { it.copy(loadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            runCatching {
                repository.libraryItems(
                    libraryId,
                    page = nextPage,
                    sort = current.sort.apiSort,
                    order = current.sort.apiOrder,
                    q = query,
                )
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loadingMore = false,
                        // distinctBy: items added on the server between page
                        // fetches shift the pages, and a repeated id would
                        // crash LazyVerticalGrid (duplicate keys).
                        items = orderItems(
                            (it.items + result.items).distinctBy { item -> item.id },
                            it.inProgressFirst,
                        ),
                        page = nextPage,
                        hasMore = hasMore(result),
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                // A failed page append is not fatal: keep what we have and allow retry.
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun currentLibraryId(): String? = savedStateHandle.get<String>("libraryId")

    private suspend fun load(libraryId: String) {
        loadMoreJob?.cancel()
        _state.update { it.copy(loading = true, error = null, loadingMore = false) }
        val baseUrl = settingsRepository.settings.first().baseUrl
        val current = _state.value
        runCatching {
            // Reuse the shared catalog; only hit the network when it is cold.
            if (libraryCatalog.libraries.value.isEmpty()) libraryCatalog.refresh().getOrThrow()
            val libraries = libraryCatalog.libraries.value
            val library = libraries.find { it.id == libraryId }
            val page = repository.libraryItems(
                libraryId,
                page = 1,
                sort = current.sort.apiSort,
                order = current.sort.apiOrder,
                q = current.query.ifBlank { null },
            )
            Triple(libraries, library, page)
        }.onSuccess { (libraries, library, result) ->
            _state.update {
                it.copy(
                    loading = false,
                    libraries = libraries,
                    library = library,
                    items = orderItems(result.items, it.inProgressFirst),
                    page = 1,
                    hasMore = hasMore(result),
                    baseUrl = baseUrl,
                )
            }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            _state.update { it.copy(loading = false, error = err.toUserMessage("Failed to load library")) }
        }
    }

    private fun hasMore(result: PagedResult<MediaItemSummary>): Boolean =
        if (result.totalPages > 0) {
            result.page < result.totalPages
        } else {
            // Server did not report totals — infer from a full page.
            result.items.size >= result.pageSize
        }

    /** Stable partition: started-but-unfinished items bubble to the top of the grid. */
    private fun orderItems(items: List<MediaItemSummary>, inProgressFirst: Boolean): List<MediaItemSummary> {
        if (!inProgressFirst) return items
        val (started, rest) = items.partition { item ->
            item.userData.watched != true && (item.userData.playbackPositionMs ?: 0L) > 0L
        }
        return started + rest
    }
}

@HiltViewModel
class LibrariesDrawerViewModel @Inject constructor(
    private val libraryCatalog: LibraryCatalog,
) : ViewModel() {
    val libraries: StateFlow<List<LibraryDto>> = libraryCatalog.libraries

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            libraryCatalog.refresh()
        }
    }
}
