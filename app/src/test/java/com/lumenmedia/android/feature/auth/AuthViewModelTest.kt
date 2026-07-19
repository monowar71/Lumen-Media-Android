package com.lumenmedia.android.feature.auth

import app.cash.turbine.test
import com.lumenmedia.android.core.model.ServerInfo
import com.lumenmedia.android.core.model.TokenResponse
import com.lumenmedia.android.core.model.UserDto
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.preferences.AppSettings
import com.lumenmedia.android.core.preferences.LibrarySort
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<LumenMediaRepository>(relaxed = true)
    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsRepository.settings } returns flowOf(
            AppSettings(
                baseUrl = "http://192.168.0.2:8096",
                lanCapKbps = 0,
                externalCapKbps = 8000,
                preferredMode = "auto",
                librarySort = LibrarySort.Added,
                libraryInProgressFirst = false,
                maxCacheBytes = 0L,
            ),
        )
        every { sessionStore.readSession() } returns null
        every { sessionStore.isRememberCredentials() } returns false
        every { sessionStore.readSavedCredentials() } returns null
        coEvery { repository.serverInfo() } returns ServerInfo(setupCompleted = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submit_login_savesSession_andAuthenticates() = runTest(dispatcher) {
        val user = UserDto(id = "u1", username = "admin", role = "Admin")
        coEvery { repository.login("admin", "secret") } returns TokenResponse(
            accessToken = "a",
            refreshToken = "r",
            user = user,
        )

        val vm = AuthViewModel(repository, sessionStore, settingsRepository)
        advanceUntilIdle()

        vm.onUsernameChange("admin")
        vm.onPasswordChange("secret")
        vm.onRememberCredentialsChange(false)
        vm.submit()
        advanceUntilIdle()

        vm.state.test {
            val state = awaitItem()
            assertThat(state.status).isEqualTo(AuthStatus.Authenticated)
            assertThat(state.displayName).isEqualTo("admin")
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { sessionStore.saveSession(any()) }
        coVerify { sessionStore.clearSavedCredentials() }
    }

    @Test
    fun submit_withRemember_savesCredentials() = runTest(dispatcher) {
        val user = UserDto(id = "u1", username = "admin", role = "Admin")
        coEvery { repository.login("admin", "secret") } returns TokenResponse(
            accessToken = "a",
            refreshToken = "r",
            user = user,
        )

        val vm = AuthViewModel(repository, sessionStore, settingsRepository)
        advanceUntilIdle()

        vm.onUsernameChange("admin")
        vm.onPasswordChange("secret")
        vm.onRememberCredentialsChange(true)
        vm.submit()
        advanceUntilIdle()

        coVerify { sessionStore.saveCredentials("admin", "secret") }
    }
}
