package com.freeplex.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeplex.android.core.model.SetupRequest
import com.freeplex.android.core.network.FreePlexRepository
import com.freeplex.android.core.network.toUserMessage
import com.freeplex.android.core.preferences.AuthSession
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

enum class AuthStatus { Restoring, Authenticated, Anonymous }

data class AuthUiState(
    val status: AuthStatus = AuthStatus.Restoring,
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val rememberCredentials: Boolean = true,
    val serverName: String = "FreePlex",
    val needsSetup: Boolean? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val displayName: String? = null,
    val role: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: FreePlexRepository,
    private val sessionStore: SessionStore,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val saved = sessionStore.readSavedCredentials()
            _state.update {
                it.copy(
                    baseUrl = settings.baseUrl,
                    username = saved?.username.orEmpty(),
                    password = saved?.password.orEmpty(),
                    rememberCredentials = sessionStore.isRememberCredentials() || saved != null,
                )
            }
            val session = sessionStore.readSession()
            if (session != null) {
                _state.update {
                    it.copy(
                        status = AuthStatus.Authenticated,
                        displayName = session.username,
                        role = session.role,
                    )
                }
            } else {
                _state.update { it.copy(status = AuthStatus.Anonymous) }
            }
            refreshServerInfo()
        }
    }

    fun onBaseUrlChange(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onServerNameChange(value: String) = _state.update { it.copy(serverName = value) }
    fun onRememberCredentialsChange(value: Boolean) =
        _state.update { it.copy(rememberCredentials = value) }

    fun refreshServerInfo() {
        viewModelScope.launch {
            runCatching {
                settingsRepository.setBaseUrl(_state.value.baseUrl)
                repository.serverInfo()
            }.onSuccess { info ->
                _state.update { it.copy(needsSetup = !info.setupCompleted, error = null) }
            }.onFailure {
                _state.update { s -> s.copy(needsSetup = null) }
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Username and password are required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            runCatching {
                settingsRepository.setBaseUrl(current.baseUrl)
                if (current.needsSetup == true) {
                    repository.setup(
                        SetupRequest(
                            username = current.username,
                            password = current.password,
                            serverName = current.serverName.ifBlank { "FreePlex" },
                        ),
                    )
                }
                val token = repository.login(current.username, current.password)
                val user = token.user ?: repository.me()
                sessionStore.saveSession(
                    AuthSession(
                        accessToken = token.accessToken,
                        refreshToken = token.refreshToken,
                        userId = user.id,
                        username = user.username,
                        role = user.role,
                    ),
                )
                if (current.rememberCredentials) {
                    sessionStore.saveCredentials(current.username, current.password)
                } else {
                    sessionStore.clearSavedCredentials()
                }
                user
            }.onSuccess { user ->
                _state.update {
                    it.copy(
                        submitting = false,
                        status = AuthStatus.Authenticated,
                        displayName = user.username,
                        role = user.role,
                        password = if (current.rememberCredentials) current.password else "",
                        needsSetup = false,
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        submitting = false,
                        error = err.toUserMessage(
                            if (current.needsSetup == true) "Setup failed" else "Login failed",
                        ),
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            sessionStore.clear()
            val saved = sessionStore.readSavedCredentials()
            _state.update {
                it.copy(
                    status = AuthStatus.Anonymous,
                    displayName = null,
                    role = null,
                    username = saved?.username.orEmpty(),
                    password = saved?.password.orEmpty(),
                    rememberCredentials = sessionStore.isRememberCredentials() || saved != null,
                )
            }
        }
    }
}
