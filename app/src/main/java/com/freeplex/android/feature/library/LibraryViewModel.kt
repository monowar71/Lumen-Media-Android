package com.freeplex.android.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeplex.android.core.model.LibraryDto
import com.freeplex.android.core.model.MediaItemSummary
import com.freeplex.android.core.network.FreePlexRepository
import com.freeplex.android.core.network.toUserMessage
import com.freeplex.android.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val accessToken: String? = null,
    val query: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: FreePlexRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: com.freeplex.android.core.preferences.SessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val initialId = checkNotNull(savedStateHandle.get<String>("libraryId"))
            savedStateHandle.getStateFlow("libraryId", initialId)
                .collectLatest { id -> load(id) }
        }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun refresh() {
        val id = savedStateHandle.get<String>("libraryId") ?: return
        viewModelScope.launch { load(id) }
    }

    private suspend fun load(libraryId: String) {
        _state.update { it.copy(loading = true, error = null) }
        val baseUrl = settingsRepository.settings.first().baseUrl
        val token = sessionStore.accessToken
        runCatching {
            val libraries = repository.libraries()
            val library = libraries.find { it.id == libraryId }
            val page = repository.libraryItems(libraryId, page = 1, q = _state.value.query.ifBlank { null })
            Triple(libraries, library, page.items)
        }.onSuccess { (libraries, library, items) ->
            _state.update {
                it.copy(
                    loading = false,
                    libraries = libraries,
                    library = library,
                    items = items,
                    baseUrl = baseUrl,
                    accessToken = token,
                )
            }
        }.onFailure { err ->
            _state.update { it.copy(loading = false, error = err.toUserMessage("Failed to load library")) }
        }
    }
}

@HiltViewModel
class LibrariesDrawerViewModel @Inject constructor(
    private val libraryCatalog: com.freeplex.android.core.library.LibraryCatalog,
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
