package com.freeplex.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeplex.android.core.model.CreateLibraryRequest
import com.freeplex.android.core.model.JobDto
import com.freeplex.android.core.model.LibraryDto
import com.freeplex.android.core.network.FreePlexRepository
import com.freeplex.android.core.network.toUserMessage
import com.freeplex.android.core.preferences.SessionStore
import com.freeplex.android.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val lanCapKbps: Int = 0,
    val externalCapKbps: Int = 8000,
    val preferredMode: String = "auto",
    val username: String? = null,
    val role: String? = null,
    val libraries: List<LibraryDto> = emptyList(),
    val jobs: List<JobDto> = emptyList(),
    val newLibraryName: String = "",
    val newLibraryType: String = "Movies",
    val newLibraryPath: String = "",
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: FreePlexRepository,
    private val sessionStore: SessionStore,
    private val libraryCatalog: com.freeplex.android.core.library.LibraryCatalog,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val session = sessionStore.readSession()
            _state.update {
                it.copy(
                    baseUrl = settings.baseUrl,
                    lanCapKbps = settings.lanCapKbps,
                    externalCapKbps = settings.externalCapKbps,
                    preferredMode = settings.preferredMode,
                    username = session?.username,
                    role = session?.role,
                )
            }
            refreshAdmin()
        }
    }

    fun onBaseUrl(v: String) = _state.update { it.copy(baseUrl = v) }
    fun onLanCap(v: String) = _state.update { it.copy(lanCapKbps = v.toIntOrNull() ?: 0) }
    fun onExternalCap(v: String) = _state.update { it.copy(externalCapKbps = v.toIntOrNull() ?: 0) }
    fun onMode(v: String) = _state.update { it.copy(preferredMode = v) }
    fun onNewLibraryName(v: String) = _state.update { it.copy(newLibraryName = v) }
    fun onNewLibraryType(v: String) = _state.update { it.copy(newLibraryType = v) }
    fun onNewLibraryPath(v: String) = _state.update { it.copy(newLibraryPath = v) }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            settingsRepository.setBaseUrl(s.baseUrl)
            settingsRepository.setLanCap(s.lanCapKbps)
            settingsRepository.setExternalCap(s.externalCapKbps)
            settingsRepository.setPreferredMode(s.preferredMode)
            _state.update { it.copy(message = "Settings saved") }
        }
    }

    fun refreshAdmin() {
        viewModelScope.launch {
            runCatching {
                val libraries = repository.libraries()
                val jobs = if (_state.value.role == "Admin") repository.jobs() else emptyList()
                libraries to jobs
            }.onSuccess { (libraries, jobs) ->
                libraryCatalog.publish(libraries)
                _state.update { it.copy(libraries = libraries, jobs = jobs, error = null) }
            }.onFailure { err ->
                _state.update { it.copy(error = err.toUserMessage()) }
            }
        }
    }

    fun createLibrary() {
        viewModelScope.launch {
            val s = _state.value
            runCatching {
                repository.createLibrary(
                    CreateLibraryRequest(
                        name = s.newLibraryName,
                        type = s.newLibraryType,
                        paths = listOf(s.newLibraryPath),
                    ),
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        newLibraryName = "",
                        newLibraryPath = "",
                        message = "Library created",
                    )
                }
                refreshAdmin()
            }.onFailure { err ->
                _state.update { it.copy(error = err.toUserMessage("Failed to create library")) }
            }
        }
    }

    fun scanLibrary(id: String) {
        viewModelScope.launch {
            runCatching { repository.scanLibrary(id) }
                .onSuccess { _state.update { it.copy(message = "Scan started") } }
                .onFailure { err -> _state.update { it.copy(error = err.toUserMessage()) } }
        }
    }

    fun deleteLibrary(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteLibrary(id) }
                .onSuccess {
                    _state.update { it.copy(message = "Library deleted") }
                    refreshAdmin()
                }
                .onFailure { err -> _state.update { it.copy(error = err.toUserMessage()) } }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            sessionStore.clear()
            onDone()
        }
    }
}
