package com.freeplex.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeplex.android.core.model.HomeSection
import com.freeplex.android.core.network.FreePlexRepository
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

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val sections: List<HomeSection> = emptyList(),
    val baseUrl: String = "",
    val accessToken: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FreePlexRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: com.freeplex.android.core.preferences.SessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val baseUrl = settingsRepository.settings.first().baseUrl
            val token = sessionStore.accessToken
            runCatching { repository.home() }
                .onSuccess { home ->
                    _state.update {
                        it.copy(
                            loading = false,
                            sections = home.sections.filter { s -> s.items.isNotEmpty() },
                            baseUrl = baseUrl,
                            accessToken = token,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, error = err.toUserMessage("Failed to load home")) }
                }
        }
    }
}
