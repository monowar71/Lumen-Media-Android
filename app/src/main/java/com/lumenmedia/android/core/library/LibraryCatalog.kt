package com.lumenmedia.android.core.library

import com.lumenmedia.android.core.model.LibraryDto
import com.lumenmedia.android.core.network.LumenMediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared library list so Settings create/delete and the main nav stay in sync.
 */
@Singleton
class LibraryCatalog @Inject constructor(
    private val repository: LumenMediaRepository,
) {
    private val _libraries = MutableStateFlow<List<LibraryDto>>(emptyList())
    val libraries: StateFlow<List<LibraryDto>> = _libraries.asStateFlow()

    suspend fun refresh(): Result<List<LibraryDto>> =
        runCatching { repository.libraries() }
            .onSuccess { publish(it) }

    fun publish(libraries: List<LibraryDto>) {
        _libraries.value = libraries
    }
}
