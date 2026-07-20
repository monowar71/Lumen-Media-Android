package com.lumenmedia.android.feature.library

import androidx.lifecycle.SavedStateHandle
import com.lumenmedia.android.core.library.LibraryCatalog
import com.lumenmedia.android.core.model.LibraryDto
import com.lumenmedia.android.core.model.MediaItemSummary
import com.lumenmedia.android.core.model.PagedResult
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.model.UserData
import com.lumenmedia.android.core.preferences.AppSettings
import com.lumenmedia.android.core.preferences.LibrarySort
import com.lumenmedia.android.core.preferences.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val library = LibraryDto(id = "lib1", name = "Movies", type = "movies")
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
        coJustRun { setLibrarySort(any()) }
        coJustRun { setLibraryOrder(any()) }
        coJustRun { setLibraryInProgressFirst(any()) }
    }
    private val libraryCatalog = mockk<LibraryCatalog> {
        every { libraries } returns MutableStateFlow(listOf(library))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = LibraryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("libraryId" to "lib1")),
        repository = repository,
        settingsRepository = settingsRepository,
        libraryCatalog = libraryCatalog,
    )

    private fun items(page: Int, count: Int): List<MediaItemSummary> =
        List(count) { i -> MediaItemSummary(id = "item-$page-$i", kind = "Movie", title = "Movie $page-$i") }

    private fun pagedResult(page: Int, count: Int, totalPages: Int) = PagedResult(
        items = items(page, count),
        page = page,
        pageSize = 50,
        total = totalPages * 50,
        totalPages = totalPages,
    )

    @Test
    fun initialLoad_populatesFirstPage_andComputesHasMore() = runTest(dispatcher) {
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns pagedResult(1, 50, 3)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.items).hasSize(50)
        assertThat(state.page).isEqualTo(1)
        assertThat(state.hasMore).isTrue()
        // Cached catalog is reused — no extra network call for the libraries list.
        coVerify(exactly = 0) { libraryCatalog.refresh() }
    }

    @Test
    fun loadMore_appendsNextPage_andStopsOnLastPage() = runTest(dispatcher) {
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns pagedResult(1, 50, 2)
        coEvery { repository.libraryItems("lib1", page = 2, q = null) } returns pagedResult(2, 20, 2)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.items).hasSize(70)
        assertThat(state.page).isEqualTo(2)
        assertThat(state.hasMore).isFalse()
        assertThat(state.loadingMore).isFalse()

        // Nothing more to fetch — further calls must be no-ops.
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.libraryItems("lib1", page = 2, q = null) }
    }

    @Test
    fun loadMore_ignoresConcurrentCalls() = runTest(dispatcher) {
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns pagedResult(1, 50, 3)
        coEvery { repository.libraryItems("lib1", page = 2, q = null) } coAnswers {
            delay(100)
            pagedResult(2, 50, 3)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceTimeBy(10)
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.state.value.items).hasSize(100)
        coVerify(exactly = 1) { repository.libraryItems("lib1", page = 2, q = null) }
    }

    @Test
    fun loadMore_failure_keepsExistingItems_andClearsLoadingMore() = runTest(dispatcher) {
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns pagedResult(1, 50, 3)
        coEvery { repository.libraryItems("lib1", page = 2, q = null) } throws RuntimeException("boom")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.items).hasSize(50)
        assertThat(state.page).isEqualTo(1)
        assertThat(state.loadingMore).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun queryChange_debounces_andResetsPagination() = runTest(dispatcher) {
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns pagedResult(1, 50, 3)
        coEvery { repository.libraryItems("lib1", page = 2, q = null) } returns pagedResult(2, 50, 3)
        coEvery { repository.libraryItems("lib1", page = 1, q = "ab") } returns pagedResult(1, 10, 1)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.state.value.page).isEqualTo(2)

        viewModel.onQueryChange("a")
        advanceTimeBy(100)
        viewModel.onQueryChange("ab")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.items).hasSize(10)
        assertThat(state.page).isEqualTo(1)
        assertThat(state.hasMore).isFalse()
        // The intermediate "a" query never hit the network.
        coVerify(exactly = 0) { repository.libraryItems("lib1", page = 1, q = "a") }
        coVerify(exactly = 1) { repository.libraryItems("lib1", page = 1, q = "ab") }
    }

    @Test
    fun sortChange_persists_andReloadsWithNewSortParams() = runTest(dispatcher) {
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns pagedResult(1, 50, 1)
        coEvery {
            repository.libraryItems("lib1", page = 1, sort = "title", order = "asc", q = null)
        } returns pagedResult(1, 10, 1)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSortChange(LibrarySort.Title)
        advanceUntilIdle()

        assertThat(viewModel.state.value.sort).isEqualTo(LibrarySort.Title)
        assertThat(viewModel.state.value.items).hasSize(10)
        coVerify(exactly = 1) { settingsRepository.setLibrarySort(LibrarySort.Title) }
        coVerify(exactly = 1) {
            repository.libraryItems("lib1", page = 1, sort = "title", order = "asc", q = null)
        }
    }

    @Test
    fun genreAndWatchedFilters_reloadWithQueryParams() = runTest(dispatcher) {
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns pagedResult(1, 50, 1)
        coEvery {
            repository.libraryItems(
                "lib1",
                page = 1,
                sort = "added",
                order = "desc",
                watched = true,
                q = null,
                genre = "Drama",
                year = 2020,
            )
        } returns pagedResult(1, 5, 1)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onGenreChange("Drama")
        advanceUntilIdle()
        viewModel.onWatchedFilterChange(WatchedFilter.Watched)
        advanceUntilIdle()
        viewModel.onYearChange("2020")
        advanceUntilIdle()

        assertThat(viewModel.state.value.genre).isEqualTo("Drama")
        assertThat(viewModel.state.value.watchedFilter).isEqualTo(WatchedFilter.Watched)
        assertThat(viewModel.state.value.year).isEqualTo("2020")
        assertThat(viewModel.state.value.items).hasSize(5)
        coVerify(atLeast = 1) {
            repository.libraryItems(
                "lib1",
                page = 1,
                sort = "added",
                order = "desc",
                watched = true,
                q = null,
                genre = "Drama",
                year = 2020,
            )
        }
    }

    @Test
    fun inProgressFirst_movesStartedUnfinishedItemsToTop_withoutRefetch() = runTest(dispatcher) {
        val watched = MediaItemSummary(
            id = "watched", kind = "Movie", title = "Watched",
            userData = UserData(watched = true, playbackPositionMs = 100),
        )
        val fresh = MediaItemSummary(id = "fresh", kind = "Movie", title = "Fresh")
        val started = MediaItemSummary(
            id = "started", kind = "Movie", title = "Started",
            userData = UserData(watched = false, playbackPositionMs = 5_000),
        )
        coEvery { repository.libraryItems("lib1", page = 1, q = null) } returns PagedResult(
            items = listOf(watched, fresh, started),
            page = 1, pageSize = 50, total = 3, totalPages = 1,
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onInProgressFirstChange(true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.items.map { it.id })
            .containsExactly("started", "watched", "fresh")
            .inOrder()
        coVerify(exactly = 1) { settingsRepository.setLibraryInProgressFirst(true) }
        // Local reorder only — no second fetch of page 1.
        coVerify(exactly = 1) { repository.libraryItems("lib1", page = 1, q = null) }
    }
}
