package com.freeplex.android.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.freeplex.android.core.model.PlaybackDecisionRequest
import com.freeplex.android.core.model.PlaybackDecisionResponse
import com.freeplex.android.core.model.ProgressRequest
import com.freeplex.android.core.model.SetQualityRequest
import com.freeplex.android.core.network.FreePlexRepository
import com.freeplex.android.core.network.toUserMessage
import com.freeplex.android.core.offline.OfflineDownloadManager
import com.freeplex.android.core.preferences.SessionStore
import com.freeplex.android.core.preferences.SettingsRepository
import com.freeplex.android.core.util.DeviceProfileFactory
import com.freeplex.android.core.util.NetworkKindDetector
import com.freeplex.android.core.util.PlaybackSource
import com.freeplex.android.core.util.resolvePlaybackSource
import com.freeplex.android.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val loading: Boolean = true,
    val buffering: Boolean = false,
    val error: String? = null,
    val decision: PlaybackDecisionResponse? = null,
    val selectedQualityId: String = "auto",
    val baseUrl: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val playing: Boolean = false,
    val seeking: Boolean = false,
    val offline: Boolean = false,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: FreePlexRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val offlineDownloadManager: OfflineDownloadManager,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val itemId: String = checkNotNull(savedStateHandle["itemId"])
    private val initialResumeMs: Long = savedStateHandle.get<String>("resumeMs")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("resumeMs")
        ?: 0L

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // Whether player chrome is on screen. Drives the ticker rate: smooth 250ms
    // updates only while the user can actually see the seek bar.
    private val uiVisible = MutableStateFlow(true)

    val player: ExoPlayer = ExoPlayer.Builder(context).build().also {
        it.playWhenReady = true
        it.repeatMode = Player.REPEAT_MODE_OFF
    }

    private var progressJob: Job? = null
    private var pingJob: Job? = null
    private var tickerJob: Job? = null
    private var seekJob: Job? = null
    private var sessionId: String? = null
    private var cacheToken: Long = 0
    private var timelineOffsetMs: Long = 0L
    private var seekEpoch: Long = 0
    private var offlinePlayback: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    playing = player.isPlaying,
                )
            }
        }
    }

    init {
        player.addListener(playerListener)
        startTicker()
        startPlayback(initialResumeMs)
    }

    fun startPlayback(resumeMs: Long, qualityId: String? = null, mode: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val previous = sessionId
            if (previous != null && previous != OFFLINE_SESSION_ID) {
                repository.stopSession(previous)
            }
            sessionId = null
            offlinePlayback = false

            val localFile = offlineDownloadManager.readyFile(itemId)
            if (localFile != null) {
                playLocalFile(localFile, resumeMs)
                return@launch
            }

            runCatching {
                val settings = settingsRepository.settings.first()
                val kind = NetworkKindDetector.detect(context)
                val cap = settingsRepository.capFor(settings, kind)
                val preferredMode = mode ?: settings.preferredMode
                val profile = DeviceProfileFactory.build(cap)
                val decision = repository.playbackDecision(
                    PlaybackDecisionRequest(
                        mediaId = itemId,
                        mode = preferredMode,
                        qualityId = qualityId,
                        resumePositionMs = resumeMs,
                        profile = profile,
                    ),
                )
                sessionId = decision.sessionId
                cacheToken += 1
                val source = resolvePlaybackSource(decision, settings.baseUrl, cacheToken.toString())
                attachSource(source, decision.startPositionMs ?: resumeMs, decision)
                decision to settings.baseUrl
            }.onSuccess { (decision, baseUrl) ->
                _state.update {
                    it.copy(
                        loading = false,
                        decision = decision,
                        selectedQualityId = decision.selectedQualityId,
                        baseUrl = baseUrl,
                        durationMs = decision.durationMs ?: it.durationMs,
                        positionMs = decision.startPositionMs ?: resumeMs,
                        offline = false,
                    )
                }
                startProgressLoop()
                startPingLoop()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = err.toUserMessage("Playback failed"),
                        offline = false,
                    )
                }
            }
        }
    }

    private suspend fun playLocalFile(file: File, resumeMs: Long) {
        val settings = runCatching { settingsRepository.settings.first() }.getOrNull()
        val decision = PlaybackDecisionResponse(
            sessionId = OFFLINE_SESSION_ID,
            method = "DirectPlay",
            mode = "manual",
            streamUrl = file.toURI().toString(),
            container = file.extension,
            startPositionMs = resumeMs,
            selectedQualityId = "original",
            reason = "offline-cache",
        )
        offlinePlayback = true
        sessionId = OFFLINE_SESSION_ID
        attachLocalFile(file, resumeMs, decision)
        _state.update {
            it.copy(
                loading = false,
                decision = decision,
                selectedQualityId = "original",
                baseUrl = settings?.baseUrl.orEmpty(),
                positionMs = resumeMs,
                offline = true,
                error = null,
            )
        }
        startProgressLoop()
        pingJob?.cancel()
    }

    private fun attachLocalFile(
        file: File,
        startMs: Long,
        decision: PlaybackDecisionResponse,
    ) {
        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        val mediaSource = DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        timelineOffsetMs = 0L
        if (startMs > 0) player.seekTo(startMs)
        player.playWhenReady = true
        _state.update {
            it.copy(
                decision = decision,
                seeking = false,
                positionMs = startMs.coerceAtLeast(0L),
                durationMs = decision.durationMs ?: it.durationMs,
                offline = true,
            )
        }
    }

    private fun attachSource(
        source: PlaybackSource,
        startMs: Long,
        decision: PlaybackDecisionResponse,
    ) {
        val token = sessionStore.accessToken
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                buildMap {
                    if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
                },
            )
        val mediaItem = MediaItem.fromUri(
            when (source) {
                is PlaybackSource.Direct -> source.url
                is PlaybackSource.Hls -> source.url
            },
        )
        val mediaSource = if (source is PlaybackSource.Hls) {
            HlsMediaSource.Factory(factory).createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(factory).createMediaSource(mediaItem)
        }
        player.setMediaSource(mediaSource)
        player.prepare()
        if (source is PlaybackSource.Direct) {
            timelineOffsetMs = 0L
            if (startMs > 0) player.seekTo(startMs)
        } else {
            // HLS/transcode sessions are already offset by server -ss.
            timelineOffsetMs = startMs.coerceAtLeast(0L)
        }
        player.playWhenReady = true
        _state.update {
            it.copy(
                decision = decision,
                seeking = false,
                positionMs = startMs.coerceAtLeast(0L),
                durationMs = decision.durationMs ?: it.durationMs,
                offline = false,
            )
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipBy(deltaMs: Long) {
        val duration = effectiveDuration()
        val current = absolutePosition()
        val target = (current + deltaMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        seekTo(target)
    }

    /**
     * Seek like web: DirectPlay uses ExoPlayer; Transcode/HLS prefers local buffer seek,
     * otherwise restarts the session via POST /playback/{id}/seek.
     */
    fun seekTo(ms: Long) {
        val decision = _state.value.decision ?: return
        val duration = effectiveDuration()
        val target = ms.coerceIn(0L, if (duration > 0) duration else ms.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = target, seeking = true) }

        if (decision.method == "DirectPlay" || offlinePlayback) {
            timelineOffsetMs = 0L
            player.seekTo(target)
            _state.update { it.copy(seeking = false) }
            return
        }

        val relative = target - timelineOffsetMs
        val buffered = player.bufferedPosition
        val canLocal = relative >= 0 &&
            relative <= buffered + 15_000 &&
            player.duration > 0 &&
            player.duration != Long.MAX_VALUE

        if (canLocal) {
            player.seekTo(relative.coerceAtLeast(0L))
            _state.update { it.copy(seeking = false) }
            return
        }

        remoteSeek(target)
    }

    private fun remoteSeek(targetMs: Long) {
        if (offlinePlayback) return
        val sid = sessionId ?: return
        val epoch = ++seekEpoch
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            _state.update { it.copy(buffering = true, seeking = true) }
            runCatching {
                repository.seekSession(sid, targetMs)
            }.onSuccess { next ->
                if (epoch != seekEpoch) return@onSuccess
                sessionId = next.sessionId
                cacheToken += 1
                val settings = settingsRepository.settings.first()
                val source = resolvePlaybackSource(next, settings.baseUrl, cacheToken.toString())
                val start = next.startPositionMs ?: targetMs
                attachSource(source, start, next)
            }.onFailure { err ->
                if (epoch != seekEpoch) return@onFailure
                _state.update {
                    it.copy(
                        seeking = false,
                        buffering = false,
                        error = err.toUserMessage("Could not seek"),
                    )
                }
            }
        }
    }

    fun changeQuality(qualityId: String) {
        if (offlinePlayback) return
        val decision = _state.value.decision ?: return
        val position = absolutePosition()
        val mode = if (qualityId == "auto" || decision.availableQualities.any { it.id == qualityId && it.adaptive == true }) {
            "auto"
        } else {
            "manual"
        }
        viewModelScope.launch {
            runCatching {
                repository.setQuality(
                    decision.sessionId,
                    SetQualityRequest(qualityId = qualityId, mode = mode, resumePositionMs = position),
                )
            }.onSuccess { next ->
                sessionId = next.sessionId
                cacheToken += 1
                val settings = settingsRepository.settings.first()
                val source = resolvePlaybackSource(next, settings.baseUrl, cacheToken.toString())
                attachSource(source, next.startPositionMs ?: position, next)
                _state.update {
                    it.copy(decision = next, selectedQualityId = next.selectedQualityId)
                }
            }.onFailure {
                startPlayback(position, qualityId, mode)
            }
        }
    }

    private fun absolutePosition(): Long {
        val decision = _state.value.decision
        return if (decision?.method == "DirectPlay" || offlinePlayback) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            (timelineOffsetMs + player.currentPosition.coerceAtLeast(0L))
        }
    }

    private fun effectiveDuration(): Long {
        val fromDecision = _state.value.decision?.durationMs ?: 0L
        val fromPlayer = player.duration.takeIf { it > 0 && it != Long.MAX_VALUE } ?: 0L
        return maxOf(fromDecision, fromPlayer, _state.value.durationMs)
    }

    /** Called by the screen when controls visibility changes. */
    fun setUiVisible(visible: Boolean) {
        uiVisible.value = visible
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                if (!_state.value.seeking) {
                    val duration = effectiveDuration()
                    val position = absolutePosition()
                    val bufferedAbs = if (_state.value.decision?.method == "DirectPlay" || offlinePlayback) {
                        player.bufferedPosition.coerceAtLeast(0L)
                    } else {
                        timelineOffsetMs + player.bufferedPosition.coerceAtLeast(0L)
                    }
                    val buffered = bufferedAbs.coerceAtMost(if (duration > 0) duration else bufferedAbs)
                    val playing = player.isPlaying
                    val buffering = player.playbackState == Player.STATE_BUFFERING
                    val current = _state.value
                    // Skip no-op emissions (e.g. while paused) so the screen
                    // does not recompose for identical state.
                    val changed = current.positionMs != position ||
                        current.durationMs != duration ||
                        current.bufferedMs != buffered ||
                        current.playing != playing ||
                        current.buffering != buffering
                    if (changed) {
                        _state.update {
                            it.copy(
                                positionMs = position,
                                durationMs = duration,
                                bufferedMs = buffered,
                                playing = playing,
                                buffering = buffering,
                            )
                        }
                    }
                }
                // Coarse 1s tick while chrome is hidden; smooth only when visible.
                delay(if (uiVisible.value) 250 else 1_000)
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                reportProgress(if (player.isPlaying) "playing" else "paused")
            }
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        if (offlinePlayback) return
        pingJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                sessionId?.let { repository.pingSession(it) }
            }
        }
    }

    private suspend fun reportProgress(stateName: String) {
        val duration = effectiveDuration()
        val sid = sessionId?.takeUnless { it == OFFLINE_SESSION_ID }
        runCatching {
            repository.putProgress(
                itemId,
                ProgressRequest(
                    positionMs = absolutePosition(),
                    durationMs = duration.coerceAtLeast(0),
                    sessionId = sid,
                    state = stateName,
                ),
            )
        }
    }

    fun retry() = startPlayback(absolutePosition().coerceAtLeast(initialResumeMs))

    /** Screen no longer visible (Home/app switch): pause and persist progress now. */
    fun onBackground() {
        player.pause()
        viewModelScope.launch { reportProgress("paused") }
    }

    override fun onCleared() {
        progressJob?.cancel()
        pingJob?.cancel()
        tickerJob?.cancel()
        seekJob?.cancel()
        player.removeListener(playerListener)
        // Capture playback state before release: the player cannot be queried
        // afterwards, and the final report runs asynchronously.
        val sid = sessionId?.takeUnless { it == OFFLINE_SESSION_ID }
        val position = absolutePosition()
        val duration = effectiveDuration()
        player.release()
        appScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    repository.putProgress(
                        itemId,
                        ProgressRequest(
                            positionMs = position,
                            durationMs = duration.coerceAtLeast(0),
                            sessionId = sid,
                            state = "stopped",
                        ),
                    )
                }
                if (sid != null) {
                    repository.stopSession(sid)
                }
            }
        }
        super.onCleared()
    }

    companion object {
        private const val OFFLINE_SESSION_ID = "offline"
    }
}
