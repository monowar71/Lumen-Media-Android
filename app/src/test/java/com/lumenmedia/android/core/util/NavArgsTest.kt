package com.lumenmedia.android.core.util

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavArgsTest {
    @Test
    fun navBoolean_readsBoolStringAndNumber() {
        assertThat(SavedStateHandle(mapOf("isEpisode" to "true")).navBoolean("isEpisode")).isTrue()
        assertThat(SavedStateHandle(mapOf("isEpisode" to "false")).navBoolean("isEpisode")).isFalse()
        assertThat(SavedStateHandle(mapOf("isEpisode" to true)).navBoolean("isEpisode")).isTrue()
        assertThat(SavedStateHandle(mapOf("isEpisode" to 1)).navBoolean("isEpisode")).isTrue()
        assertThat(SavedStateHandle().navBoolean("isEpisode")).isFalse()
    }
}
