package com.lumenmedia.android.feature.details

import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.lumenmedia.android.core.model.DeleteMediaFileResponse
import com.lumenmedia.android.core.model.EpisodeSummary
import com.lumenmedia.android.core.model.MediaSource
import com.lumenmedia.android.core.model.MovieDetail
import com.lumenmedia.android.core.model.ProgressRequest
import com.lumenmedia.android.core.model.ProgressResponse
import com.lumenmedia.android.core.model.SeriesDetail
import com.lumenmedia.android.core.model.UserData
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.network.ItemDetailResult
import com.lumenmedia.android.core.preferences.AppSettings
import com.lumenmedia.android.core.preferences.AuthSession
import com.lumenmedia.android.core.preferences.LibrarySort
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class DetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<LumenMediaRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { settings } returns flowOf(
            AppSettings(
                baseUrl = "http://server",
                lanCapKbps = 0,
                externalCapKbps = 0,
                preferredMode = "auto",
                librarySort = LibrarySort.Added,
                libraryOrder = com.lumenmedia.android.core.preferences.LibraryOrder.Desc,
                libraryInProgressFirst = false,
                locale = "ru",
                maxCacheBytes = 0L,
            ),
        )
    }
    private val sessionStore = mockk<SessionStore> {
        every { readSession() } returns AuthSession(
            accessToken = "a",
            refreshToken = "r",
            userId = "u1",
            username = "admin",
            role = "Admin",
        )
    }
    private val offlineDownloadManager = mockk<com.lumenmedia.android.core.offline.OfflineDownloadManager>(relaxed = true) {
        every { entries } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(itemId: String = "m1") = DetailsViewModel(
        SavedStateHandle(mapOf("itemId" to itemId)),
        repository,
        settingsRepository,
        sessionStore,
        offlineDownloadManager,
    )

    @Test
    fun toggleMovieWatched_marks_via_progress_api() = runTest {
        val movie = MovieDetail(
            id = "m1",
            kind = "Movie",
            title = "Matrix",
            userData = UserData(watched = false, playbackPositionMs = 1_000),
        )
        coEvery { repository.itemDetail("m1") } returns ItemDetailResult.Movie(movie)
        coEvery { repository.putProgress(any(), any()) } returns ProgressResponse(
            itemId = "m1",
            watched = true,
        )

        val vm = createVm()
        advanceUntilIdle()

        vm.toggleMovieWatched()
        advanceUntilIdle()

        val body = slot<ProgressRequest>()
        coVerify { repository.putProgress("m1", capture(body)) }
        assertThat(body.captured.watched).isTrue()
        assertThat(vm.state.value.movie?.userData?.watched).isTrue()
    }

    @Test
    fun deleteMovieFile_calls_api_and_leaves_when_removed() = runTest {
        val movie = MovieDetail(
            id = "m1",
            kind = "Movie",
            title = "Matrix",
            mediaSources = listOf(MediaSource(id = "src1")),
            userData = UserData(watched = false),
        )
        coEvery { repository.itemDetail("m1") } returns ItemDetailResult.Movie(movie)
        coEvery { repository.deleteMediaFile("m1") } returns DeleteMediaFileResponse(
            deletedFiles = 1,
            sourcesRemoved = 1,
            mediaRemoved = true,
        )

        val vm = createVm()
        advanceUntilIdle()
        assertThat(vm.state.value.isAdmin).isTrue()

        vm.events.test {
            vm.deleteMovieFile()
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(DetailsEvent.LeaveDetails)
        }
        coVerify { repository.deleteMediaFile("m1") }
    }

    @Test
    fun setMovieWatched_false_clears_in_progress_position() = runTest {
        val movie = MovieDetail(
            id = "m1",
            kind = "Movie",
            title = "Matrix",
            userData = UserData(watched = false, playbackPositionMs = 1_000),
        )
        coEvery { repository.itemDetail("m1") } returns ItemDetailResult.Movie(movie)
        coEvery { repository.putProgress(any(), any()) } returns ProgressResponse(
            itemId = "m1",
            watched = false,
        )

        val vm = createVm()
        advanceUntilIdle()

        vm.setMovieWatched(false)
        advanceUntilIdle()

        val body = slot<ProgressRequest>()
        coVerify { repository.putProgress("m1", capture(body)) }
        assertThat(body.captured.watched).isFalse()
        assertThat(vm.state.value.movie?.userData?.watched).isFalse()
        assertThat(vm.state.value.movie?.userData?.playbackPositionMs).isEqualTo(0L)
    }

    @Test
    fun canMarkUnwatched_true_for_in_progress() {
        assertThat(DetailsViewModel.canMarkUnwatched(false, 1_000)).isTrue()
        assertThat(DetailsViewModel.canMarkUnwatched(true, 0)).isTrue()
        assertThat(DetailsViewModel.canMarkUnwatched(false, 0)).isFalse()
    }

    @Test
    fun isSeriesWatched_requires_zero_unwatched() {
        val watched = SeriesDetail(
            id = "s1",
            kind = "Series",
            title = "Show",
            seasonCount = 1,
            episodeCount = 3,
            userData = UserData(unwatchedEpisodeCount = 0),
        )
        val unwatched = watched.copy(userData = UserData(unwatchedEpisodeCount = 2))
        assertThat(DetailsViewModel.isSeriesWatched(watched)).isTrue()
        assertThat(DetailsViewModel.isSeriesWatched(unwatched)).isFalse()
    }

    @Test
    fun isSeasonWatched_requires_all_episodes() {
        val episodes = listOf(
            EpisodeSummary(
                id = "e1",
                title = "One",
                seasonNumber = 1,
                episodeNumber = 1,
                userData = UserData(watched = true),
            ),
            EpisodeSummary(
                id = "e2",
                title = "Two",
                seasonNumber = 1,
                episodeNumber = 2,
                userData = UserData(watched = false),
            ),
        )
        assertThat(DetailsViewModel.isSeasonWatched(episodes)).isFalse()
        assertThat(
            DetailsViewModel.isSeasonWatched(
                episodes.map { it.copy(userData = UserData(watched = true)) },
            ),
        ).isTrue()
    }
}
