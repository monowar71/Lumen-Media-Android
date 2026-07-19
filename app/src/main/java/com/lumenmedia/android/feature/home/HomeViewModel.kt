package com.lumenmedia.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumenmedia.android.core.model.HomeSection
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.network.toUserMessage
import com.lumenmedia.android.core.preferences.SettingsRepository
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LumenMediaRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val baseUrl = settingsRepository.settings.first().baseUrl
            runCatching { repository.home() }
                .onSuccess { home ->
                    _state.update {
                        it.copy(
                            loading = false,
                            sections = home.sections.filter { s -> s.items.isNotEmpty() },
                            baseUrl = baseUrl,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, error = err.toUserMessage("Failed to load home")) }
                }
        }
    }
}
