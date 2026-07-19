package com.lumenmedia.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumenmedia.android.core.model.CreateLibraryRequest
import com.lumenmedia.android.core.model.JobDto
import com.lumenmedia.android.core.model.LibraryDto
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.network.toUserMessage
import com.lumenmedia.android.core.offline.OfflineCacheSummary
import com.lumenmedia.android.core.offline.OfflineDownloadManager
import com.lumenmedia.android.core.offline.OfflineEpisodeState
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val lanCapKbps: Int = 0,
    val externalCapKbps: Int = 8000,
    val preferredMode: String = "auto",
    val maxCacheBytes: Long = SettingsRepository.DEFAULT_MAX_CACHE_BYTES,
    val username: String? = null,
    val role: String? = null,
    val libraries: List<LibraryDto> = emptyList(),
    val jobs: List<JobDto> = emptyList(),
    val newLibraryName: String = "",
    val newLibraryType: String = "Movies",
    val newLibraryPath: String = "",
    val cacheSummary: OfflineCacheSummary = OfflineCacheSummary(),
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: LumenMediaRepository,
    private val sessionStore: SessionStore,
    private val libraryCatalog: com.lumenmedia.android.core.library.LibraryCatalog,
    private val offlineDownloadManager: OfflineDownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(
        _state,
        offlineDownloadManager.summary,
    ) { ui, cache ->
        ui.copy(cacheSummary = cache)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    val cacheEntries: StateFlow<List<OfflineEpisodeState>> = offlineDownloadManager.entries

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
                    maxCacheBytes = settings.maxCacheBytes,
                    username = session?.username,
                    role = session?.role,
                )
            }
            refreshAdmin()
        }
    }

    fun onBaseUrl(v: String) = _state.update { it.copy(baseUrl = v) }
    fun onLanCap(v: Int) = _state.update { it.copy(lanCapKbps = v.coerceAtLeast(0)) }
    fun onExternalCap(v: Int) = _state.update { it.copy(externalCapKbps = v.coerceAtLeast(0)) }
    fun onMode(v: String) = _state.update { it.copy(preferredMode = v) }
    fun onMaxCacheBytes(v: Long) = _state.update { it.copy(maxCacheBytes = v.coerceAtLeast(0L)) }
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
            settingsRepository.setMaxCacheBytes(s.maxCacheBytes)
            _state.update { it.copy(message = "Settings saved", error = null) }
        }
    }

    fun saveBaseUrl(url: String) {
        onBaseUrl(url)
        viewModelScope.launch {
            settingsRepository.setBaseUrl(url)
            _state.update { it.copy(message = "Server URL saved", error = null) }
        }
    }

    fun saveLanCap(kbps: Int) {
        onLanCap(kbps)
        viewModelScope.launch {
            settingsRepository.setLanCap(kbps)
            _state.update { it.copy(message = "LAN cap saved", error = null) }
        }
    }

    fun saveExternalCap(kbps: Int) {
        onExternalCap(kbps)
        viewModelScope.launch {
            settingsRepository.setExternalCap(kbps)
            _state.update { it.copy(message = "External cap saved", error = null) }
        }
    }

    fun saveMode(mode: String) {
        onMode(mode)
        viewModelScope.launch {
            settingsRepository.setPreferredMode(mode)
            _state.update { it.copy(message = "Playback mode saved", error = null) }
        }
    }

    fun saveMaxCacheBytes(bytes: Long) {
        onMaxCacheBytes(bytes)
        viewModelScope.launch {
            settingsRepository.setMaxCacheBytes(bytes)
            _state.update { it.copy(message = "Cache limit saved", error = null) }
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

    fun removeCachedEpisode(episodeId: String) {
        viewModelScope.launch {
            offlineDownloadManager.remove(episodeId)
            _state.update { it.copy(message = "Removed from cache") }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            offlineDownloadManager.clearAll()
            _state.update { it.copy(message = "Offline cache cleared") }
        }
    }

    fun removeFailedDownloads() {
        viewModelScope.launch {
            offlineDownloadManager.removeFailed()
            _state.update { it.copy(message = "Failed downloads cleared") }
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
