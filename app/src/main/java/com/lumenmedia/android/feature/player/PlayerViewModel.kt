package com.lumenmedia.android.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lumenmedia.android.core.model.MediaSource
import com.lumenmedia.android.core.model.PlaybackDecisionRequest
import com.lumenmedia.android.core.model.PlaybackDecisionResponse
import com.lumenmedia.android.core.model.ProgressRequest
import com.lumenmedia.android.core.model.SetQualityRequest
import com.lumenmedia.android.core.model.UserData
import com.lumenmedia.android.core.network.ItemDetailResult
import com.lumenmedia.android.core.network.LumenMediaRepository
import com.lumenmedia.android.core.network.toUserMessage
import com.lumenmedia.android.core.offline.OfflineDownloadManager
import com.lumenmedia.android.core.preferences.SessionStore
import com.lumenmedia.android.core.preferences.SettingsRepository
import com.lumenmedia.android.core.util.DeviceProfileFactory
import com.lumenmedia.android.core.util.MediaFormatLabels
import com.lumenmedia.android.core.util.NetworkKindDetector
import com.lumenmedia.android.core.util.PlaybackSource
import com.lumenmedia.android.core.util.absoluteUrl
import com.lumenmedia.android.core.util.resolvePlaybackSource
import com.lumenmedia.android.di.ApplicationScope
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
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
    val forceHdrToSdr: Boolean = false,
    val selectedHdrToneMapMethod: String? = null,
    val selectedAudioLayout: String? = null,
    val baseUrl: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val playing: Boolean = false,
    val seeking: Boolean = false,
    val offline: Boolean = false,
    /** Movie or series title shown in player chrome. */
    val mediaTitle: String? = null,
    val mediaYear: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isEpisode: Boolean = false,
    val videoBadges: List<String> = emptyList(),
    val audioBadges: List<String> = emptyList(),
    /** Source or source→output video summary for the HUD. */
    val videoFormatLabel: String? = null,
    /** Source or source→output audio summary for the HUD. */
    val audioFormatLabel: String? = null,
    val networkMbpsLabel: String? = null,
    val canMarkUnwatched: Boolean = false,
    val markingUnwatched: Boolean = false,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: LumenMediaRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val offlineDownloadManager: OfflineDownloadManager,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val itemId: String = checkNotNull(savedStateHandle["itemId"])
    private val initialResumeMs: Long = savedStateHandle.get<String>("resumeMs")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("resumeMs")
        ?: 0L
    private val isEpisodeArg: Boolean = savedStateHandle.get<Boolean>("isEpisode") ?: false

    private val _state = MutableStateFlow(PlayerUiState(isEpisode = isEpisodeArg))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // Whether player chrome is on screen. Drives the ticker rate: smooth 250ms
    // updates only while the user can actually see the seek bar.
    private val uiVisible = MutableStateFlow(true)

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setBandwidthMeter(bandwidthMeter)
        .build()
        .also {
            it.playWhenReady = true
            it.repeatMode = Player.REPEAT_MODE_OFF
        }

    private var progressJob: Job? = null
    private var pingJob: Job? = null
    private var tickerJob: Job? = null
    private var bandwidthJob: Job? = null
    private var seekJob: Job? = null
    private var sessionId: String? = null
    private var cacheToken: Long = 0
    private var timelineOffsetMs: Long = 0L
    private var seekEpoch: Long = 0
    private var offlinePlayback: Boolean = false
    private var mediaSource: MediaSource? = null
    private var userData: UserData? = null

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

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val id = _state.value.selectedSubtitleId ?: return
            val textAlreadySelected = tracks.groups.any { group ->
                group.type == C.TRACK_TYPE_TEXT && group.isSelected
            }
            if (!textAlreadySelected) {
                applyTextTrackSelection(id)
            }
        }
    }

    init {
        player.addListener(playerListener)
        startTicker()
        startBandwidthLoop()
        loadMediaMeta()
        startPlayback(initialResumeMs)
    }

    private fun startBandwidthLoop() {
        bandwidthJob?.cancel()
        bandwidthJob = viewModelScope.launch {
            while (isActive) {
                val estimate = bandwidthMeter.bitrateEstimate
                val label = MediaFormatLabels.formatNetworkMbps(estimate)
                _state.update { it.copy(networkMbpsLabel = label) }
                delay(2_000)
            }
        }
    }

    private fun refreshFormatBadges() {
        val decision = _state.value.decision
        val video = mediaSource?.streams?.firstOrNull { it.kind.equals("Video", ignoreCase = true) }
        val audioFromDecision = decision?.audioStreams
            ?.firstOrNull { it.id == _state.value.selectedAudioId }
        val audioStream = mediaSource?.streams
            ?.firstOrNull { it.kind.equals("Audio", ignoreCase = true) && it.isDefault == true }
            ?: mediaSource?.streams?.firstOrNull { it.kind.equals("Audio", ignoreCase = true) }
        val sourceAudioCodec = audioFromDecision?.codec ?: audioStream?.codec
        val sourceAudioChannels = audioFromDecision?.channels ?: audioStream?.channels
        val sourceAudioTitle = audioFromDecision?.title ?: audioStream?.title
        val sourceHdr = video?.hdr ?: decision?.sourceHdr
        val paths = MediaFormatLabels.playbackFormatPaths(
            method = decision?.method,
            sourceCodec = video?.codec,
            sourceHdr = sourceHdr,
            sourceWidth = video?.width,
            sourceHeight = video?.height,
            sourceAudioCodec = sourceAudioCodec,
            sourceAudioChannels = sourceAudioChannels,
            sourceAudioTitle = sourceAudioTitle,
            selectedQualityId = _state.value.selectedQualityId.ifBlank { decision?.selectedQualityId },
            availableQualities = decision?.availableQualities.orEmpty(),
            toneMapActive = decision?.toneMapActive == true,
            selectedAudioLayout = _state.value.selectedAudioLayout
                ?: decision?.selectedAudioLayout,
        )
        val canMark = canMarkUnwatched(userData) || _state.value.positionMs > 0L
        _state.update {
            it.copy(
                videoBadges = listOfNotNull(paths.videoLabel),
                audioBadges = listOfNotNull(paths.audioLabel),
                videoFormatLabel = paths.videoLabel,
                audioFormatLabel = paths.audioLabel,
                canMarkUnwatched = canMark,
            )
        }
    }

    private fun canMarkUnwatched(data: UserData?): Boolean =
        data?.watched == true || (data?.playbackPositionMs ?: 0L) > 0L

    private fun applyUserData(data: UserData?, sources: List<MediaSource>) {
        userData = data
        mediaSource = sources.firstOrNull()
        refreshFormatBadges()
    }

    fun markUnwatched() {
        if (_state.value.markingUnwatched) return
        viewModelScope.launch {
            _state.update { it.copy(markingUnwatched = true) }
            runCatching {
                repository.putProgress(itemId, ProgressRequest(watched = false))
            }.onSuccess {
                userData = (userData ?: UserData()).copy(watched = false, playbackPositionMs = 0L)
                _state.update {
                    it.copy(
                        markingUnwatched = false,
                        canMarkUnwatched = canMarkUnwatched(userData) || it.positionMs > 0L,
                    )
                }
            }.onFailure {
                _state.update { it.copy(markingUnwatched = false) }
            }
        }
    }

    private fun loadMediaMeta() {
        viewModelScope.launch {
            val offline = offlineDownloadManager.stateFor(itemId)
            if (offline != null) {
                _state.update {
                    it.copy(
                        mediaTitle = offline.seriesTitle,
                        seasonNumber = offline.seasonNumber,
                        episodeNumber = offline.episodeNumber,
                        isEpisode = true,
                    )
                }
                return@launch
            }

            if (isEpisodeArg) {
                runCatching { repository.episode(itemId) }
                    .onSuccess { episode ->
                        val seriesDetail = runCatching { repository.itemDetail(episode.seriesId) }.getOrNull()
                        val series = (seriesDetail as? ItemDetailResult.Series)?.value
                        applyUserData(episode.userData, episode.mediaSources)
                        _state.update {
                            it.copy(
                                mediaTitle = series?.title ?: episode.title,
                                mediaYear = series?.year,
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                                isEpisode = true,
                            )
                        }
                    }
                return@launch
            }

            runCatching { repository.itemDetail(itemId) }
                .onSuccess { detail ->
                    when (detail) {
                        is ItemDetailResult.Movie -> {
                            applyUserData(detail.value.userData, detail.value.mediaSources)
                            _state.update {
                                it.copy(
                                    mediaTitle = detail.value.title,
                                    mediaYear = detail.value.year,
                                    seasonNumber = null,
                                    episodeNumber = null,
                                    isEpisode = false,
                                )
                            }
                        }
                        is ItemDetailResult.Series -> _state.update {
                            it.copy(
                                mediaTitle = detail.value.title,
                                mediaYear = detail.value.year,
                                isEpisode = false,
                            )
                        }
                    }
                }
        }
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
                val profile = DeviceProfileFactory.build(context, cap)
                val forceHdr = _state.value.forceHdrToSdr
                val audioLayout = _state.value.selectedAudioLayout
                val decision = repository.playbackDecision(
                    PlaybackDecisionRequest(
                        mediaId = itemId,
                        mode = preferredMode,
                        qualityId = qualityId,
                        audioStreamId = _state.value.selectedAudioId,
                        subtitleStreamId = _state.value.selectedSubtitleId,
                        resumePositionMs = resumeMs,
                        profile = profile,
                        forceHdrToSdr = forceHdr,
                        audioLayout = audioLayout,
                    ),
                )
                sessionId = decision.sessionId
                cacheToken += 1
                val audioId = _state.value.selectedAudioId ?: pickDefaultAudio(decision)
                val subtitleId = _state.value.selectedSubtitleId
                val source = resolvePlaybackSource(decision, settings.baseUrl, cacheToken.toString())
                attachSource(
                    source = source,
                    startMs = decision.startPositionMs ?: resumeMs,
                    decision = decision,
                    baseUrl = settings.baseUrl,
                    selectedSubtitleId = subtitleId,
                )
                Triple(decision, settings.baseUrl, audioId)
            }.onSuccess { (decision, baseUrl, audioId) ->
                val autoForce = !decision.sourceHdr.isNullOrBlank() && !DeviceProfileFactory.supportsHdr(context)
                _state.update {
                    it.copy(
                        loading = false,
                        decision = decision,
                        selectedQualityId = decision.selectedQualityId,
                        selectedAudioId = audioId,
                        forceHdrToSdr = it.forceHdrToSdr || autoForce || decision.toneMapActive,
                        selectedHdrToneMapMethod = decision.selectedHdrToneMapMethod,
                        selectedAudioLayout = decision.selectedAudioLayout,
                        baseUrl = baseUrl,
                        durationMs = decision.durationMs ?: it.durationMs,
                        positionMs = decision.startPositionMs ?: resumeMs,
                        offline = false,
                    )
                }
                refreshFormatBadges()
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
        baseUrl: String,
        selectedSubtitleId: String?,
    ) {
        val token = sessionStore.accessToken
        // Feed the same BandwidthMeter ExoPlayer uses for the HUD — without a
        // TransferListener, bitrateEstimate stays at the static initial guess.
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setTransferListener(bandwidthMeter)
            .setDefaultRequestProperties(
                buildMap {
                    if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
                },
            )
        val uri = when (source) {
            is PlaybackSource.Direct -> source.url
            is PlaybackSource.Hls -> source.url
        }
        // Only the active sidecar. Server always delivers WebVTT (.vtt) even for SRT/ASS sources.
        // HlsMediaSource.Factory ignores MediaItem.SubtitleConfiguration — use DefaultMediaSourceFactory.
        val subtitleConfigs = decision.subtitleStreams.mapNotNull { stream ->
            if (stream.id != selectedSubtitleId) return@mapNotNull null
            if (stream.deliveryUrl.isBlank()) return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(absoluteUrl(baseUrl, stream.deliveryUrl)))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(stream.language)
                .setId(stream.id)
                .setLabel(stream.title?.takeIf { it.isNotBlank() } ?: stream.language ?: stream.id)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs)
        if (source is PlaybackSource.Hls) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        val mediaSource = DefaultMediaSourceFactory(factory).createMediaSource(mediaItemBuilder.build())
        player.setMediaSource(mediaSource)
        player.prepare()
        applyTextTrackSelection(selectedSubtitleId)
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
        refreshFormatBadges()
    }

    private fun applyTextTrackSelection(subtitleId: String?) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleId == null)
            .apply {
                if (subtitleId != null) {
                    setPreferredTextLanguage(null)
                }
            }
            .build()
        if (subtitleId == null) return
        val groups = player.currentTracks.groups
        for (groupIndex in 0 until groups.size) {
            val group = groups[groupIndex]
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                if (format.id == subtitleId || format.label == subtitleId || format.language == subtitleId) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)),
                        )
                        .build()
                    return
                }
            }
        }
    }

    private fun pickDefaultAudio(decision: PlaybackDecisionResponse): String? =
        decision.audioStreams.firstOrNull { it.isDefault == true }?.id
            ?: decision.audioStreams.firstOrNull()?.id

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
                attachSource(source, start, next, settings.baseUrl, _state.value.selectedSubtitleId)
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

    fun changeHdrToneMapMethod(methodId: String) {
        if (offlinePlayback) return
        val decision = _state.value.decision ?: return
        if (decision.sourceHdr.isNullOrBlank()) return
        val off = methodId == "off"
        if (off && !DeviceProfileFactory.supportsHdr(context)) return
        val currentId = if (_state.value.forceHdrToSdr || decision.toneMapActive) {
            _state.value.selectedHdrToneMapMethod
                ?: decision.selectedHdrToneMapMethod
                ?: "off"
        } else {
            "off"
        }
        if (methodId == currentId) return
        val position = absolutePosition()
        _state.update {
            it.copy(
                forceHdrToSdr = !off,
                selectedHdrToneMapMethod = if (off) it.selectedHdrToneMapMethod else methodId,
                buffering = true,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.setQuality(
                    decision.sessionId,
                    SetQualityRequest(
                        qualityId = _state.value.selectedQualityId,
                        mode = decision.mode,
                        resumePositionMs = position,
                        forceHdrToSdr = !off,
                        hdrToneMapMethod = if (off) null else methodId,
                        audioLayout = _state.value.selectedAudioLayout,
                    ),
                )
            }.onSuccess { next ->
                sessionId = next.sessionId
                cacheToken += 1
                val settings = settingsRepository.settings.first()
                val source = resolvePlaybackSource(next, settings.baseUrl, cacheToken.toString())
                attachSource(source, next.startPositionMs ?: position, next, settings.baseUrl, _state.value.selectedSubtitleId)
                _state.update {
                    it.copy(
                        decision = next,
                        selectedQualityId = next.selectedQualityId,
                        forceHdrToSdr = next.toneMapActive || !off,
                        selectedHdrToneMapMethod = next.selectedHdrToneMapMethod,
                        selectedAudioLayout = next.selectedAudioLayout,
                        buffering = false,
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        buffering = false,
                        error = err.toUserMessage("Could not change HDR→SDR"),
                    )
                }
            }
        }
    }

    fun changeAudioLayout(layoutId: String) {
        if (offlinePlayback) return
        val decision = _state.value.decision ?: return
        if (layoutId == _state.value.selectedAudioLayout) return
        val position = absolutePosition()
        _state.update { it.copy(selectedAudioLayout = layoutId, buffering = true) }
        viewModelScope.launch {
            runCatching {
                repository.setQuality(
                    decision.sessionId,
                    SetQualityRequest(
                        qualityId = _state.value.selectedQualityId,
                        mode = decision.mode,
                        resumePositionMs = position,
                        // Omit — server keeps session.ForceHdrToSdr until explicitly changed.
                        audioLayout = layoutId,
                    ),
                )
            }.onSuccess { next ->
                sessionId = next.sessionId
                cacheToken += 1
                val settings = settingsRepository.settings.first()
                val source = resolvePlaybackSource(next, settings.baseUrl, cacheToken.toString())
                attachSource(source, next.startPositionMs ?: position, next, settings.baseUrl, _state.value.selectedSubtitleId)
                _state.update {
                    it.copy(
                        decision = next,
                        selectedQualityId = next.selectedQualityId,
                        selectedAudioLayout = next.selectedAudioLayout,
                        buffering = false,
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        buffering = false,
                        error = err.toUserMessage("Could not change audio layout"),
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
                    SetQualityRequest(
                        qualityId = qualityId,
                        mode = mode,
                        resumePositionMs = position,
                        // Omit — server keeps session.ForceHdrToSdr until explicitly changed.
                        audioLayout = _state.value.selectedAudioLayout,
                    ),
                )
            }.onSuccess { next ->
                sessionId = next.sessionId
                cacheToken += 1
                val settings = settingsRepository.settings.first()
                val source = resolvePlaybackSource(next, settings.baseUrl, cacheToken.toString())
                attachSource(source, next.startPositionMs ?: position, next, settings.baseUrl, _state.value.selectedSubtitleId)
                _state.update {
                    it.copy(
                        decision = next,
                        selectedQualityId = next.selectedQualityId,
                        selectedAudioLayout = next.selectedAudioLayout,
                        forceHdrToSdr = it.forceHdrToSdr || next.toneMapActive,
                        selectedHdrToneMapMethod = next.selectedHdrToneMapMethod
                            ?: it.selectedHdrToneMapMethod,
                    )
                }
            }.onFailure {
                startPlayback(position, qualityId, mode)
            }
        }
    }

    fun changeAudio(audioId: String) {
        if (offlinePlayback) return
        val decision = _state.value.decision ?: return
        if (audioId == _state.value.selectedAudioId) return
        val position = absolutePosition()
        val previous = sessionId
        _state.update { it.copy(selectedAudioId = audioId, buffering = true) }
        refreshFormatBadges()
        viewModelScope.launch {
            if (previous != null && previous != OFFLINE_SESSION_ID) {
                repository.stopSession(previous)
            }
            sessionId = null
            runCatching {
                val settings = settingsRepository.settings.first()
                val kind = NetworkKindDetector.detect(context)
                val cap = settingsRepository.capFor(settings, kind)
                val profile = DeviceProfileFactory.build(context, cap)
                val next = repository.playbackDecision(
                    PlaybackDecisionRequest(
                        mediaId = itemId,
                        mode = decision.mode,
                        qualityId = if (decision.mode == "manual") _state.value.selectedQualityId else null,
                        audioStreamId = audioId,
                        subtitleStreamId = _state.value.selectedSubtitleId,
                        resumePositionMs = position,
                        profile = profile,
                        forceHdrToSdr = _state.value.forceHdrToSdr,
                        audioLayout = _state.value.selectedAudioLayout,
                    ),
                )
                sessionId = next.sessionId
                cacheToken += 1
                val source = resolvePlaybackSource(next, settings.baseUrl, cacheToken.toString())
                attachSource(source, next.startPositionMs ?: position, next, settings.baseUrl, _state.value.selectedSubtitleId)
                next to settings.baseUrl
            }.onSuccess { (next, baseUrl) ->
                _state.update {
                    it.copy(
                        decision = next,
                        selectedQualityId = next.selectedQualityId,
                        selectedAudioId = audioId,
                        baseUrl = baseUrl,
                        buffering = false,
                        loading = false,
                        error = null,
                    )
                }
                startPingLoop()
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        buffering = false,
                        error = err.toUserMessage("Could not change audio track"),
                    )
                }
            }
        }
    }

    fun changeSubtitle(subtitleId: String?) {
        if (offlinePlayback) return
        val decision = _state.value.decision
        val baseUrl = _state.value.baseUrl
        _state.update { it.copy(selectedSubtitleId = subtitleId) }
        if (decision == null) return
        // Off: drop sidecar without restarting playback.
        if (subtitleId == null) {
            val position = absolutePosition()
            val source = resolvePlaybackSource(decision, baseUrl, cacheToken.toString())
            attachSource(source, position, decision, baseUrl, selectedSubtitleId = null)
            return
        }
        // Sidecar WebVTT: swap the single subtitle config in-place (no new transcode session).
        val hasSidecar = decision.subtitleStreams.any { it.id == subtitleId && it.deliveryUrl.isNotBlank() }
        if (hasSidecar) {
            val position = absolutePosition()
            val source = resolvePlaybackSource(decision, baseUrl, cacheToken.toString())
            attachSource(source, position, decision, baseUrl, selectedSubtitleId = subtitleId)
            return
        }
        // Bitmap / burn-in: needs a server-side remap.
        val position = absolutePosition()
        val previous = sessionId
        viewModelScope.launch {
            runCatching {
                if (previous != null && previous != OFFLINE_SESSION_ID) {
                    repository.stopSession(previous)
                }
                sessionId = null
                val settings = settingsRepository.settings.first()
                val kind = NetworkKindDetector.detect(context)
                val cap = settingsRepository.capFor(settings, kind)
                val profile = DeviceProfileFactory.build(context, cap)
                val next = repository.playbackDecision(
                    PlaybackDecisionRequest(
                        mediaId = itemId,
                        mode = decision.mode,
                        qualityId = if (decision.mode == "manual") _state.value.selectedQualityId else null,
                        audioStreamId = _state.value.selectedAudioId,
                        subtitleStreamId = subtitleId,
                        resumePositionMs = position,
                        profile = profile,
                        forceHdrToSdr = _state.value.forceHdrToSdr,
                        audioLayout = _state.value.selectedAudioLayout,
                    ),
                )
                sessionId = next.sessionId
                cacheToken += 1
                val source = resolvePlaybackSource(next, settings.baseUrl, cacheToken.toString())
                attachSource(source, next.startPositionMs ?: position, next, settings.baseUrl, subtitleId)
                next to settings.baseUrl
            }.onSuccess { (next, baseUrlNext) ->
                _state.update {
                    it.copy(
                        decision = next,
                        selectedQualityId = next.selectedQualityId,
                        baseUrl = baseUrlNext,
                        error = null,
                    )
                }
                startPingLoop()
            }.onFailure {
                // Keep client-side subtitle selection even if the round-trip fails.
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
                    val canMark = canMarkUnwatched(userData) || position > 0L
                    val current = _state.value
                    // Skip no-op emissions (e.g. while paused) so the screen
                    // does not recompose for identical state.
                    val changed = current.positionMs != position ||
                        current.durationMs != duration ||
                        current.bufferedMs != buffered ||
                        current.playing != playing ||
                        current.buffering != buffering ||
                        current.canMarkUnwatched != canMark
                    if (changed) {
                        _state.update {
                            it.copy(
                                positionMs = position,
                                durationMs = duration,
                                bufferedMs = buffered,
                                playing = playing,
                                buffering = buffering,
                                canMarkUnwatched = canMark,
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
        bandwidthJob?.cancel()
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
