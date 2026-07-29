package com.lumenmedia.android.feature.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lumenmedia.android.R
import com.lumenmedia.android.core.designsystem.ErrorState
import com.lumenmedia.android.core.designsystem.FpColors
import com.lumenmedia.android.core.designsystem.FpDimens
import com.lumenmedia.android.core.designsystem.TvFocusColor
import com.lumenmedia.android.core.designsystem.isTvDevice
import com.lumenmedia.android.core.designsystem.tvFocusable
import com.lumenmedia.android.core.util.formatTrackLanguage
import kotlinx.coroutines.delay
import kotlin.math.max

private enum class OpenTrackMenu { Quality, Audio, Subtitles, AudioLayout, HdrToSdr }

private data class TrackMenuItem(
    val id: String?,
    val label: String,
    val subtitle: String? = null,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tv = isTvDevice()
    var controlsVisible by remember { mutableStateOf(true) }
    var openMenu by remember { mutableStateOf<OpenTrackMenu?>(null) }
    var lastOpenedMenu by remember { mutableStateOf<OpenTrackMenu?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableFloatStateOf(0f) }
    var seekFocused by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val qualityButtonFocus = remember { FocusRequester() }
    val audioButtonFocus = remember { FocusRequester() }
    val subtitleButtonFocus = remember { FocusRequester() }
    val audioLayoutButtonFocus = remember { FocusRequester() }
    val hdrButtonFocus = remember { FocusRequester() }
    val menuSelectedFocus = remember { FocusRequester() }

    val displayMs = if (scrubbing) scrubMs.toLong() else state.positionMs
    val duration = state.durationMs.coerceAtLeast(1L)
    val isBusy = (state.loading || state.buffering || state.seeking) && state.error == null
    val showCenterTransport = state.error == null && (controlsVisible || isBusy || !state.playing)

    val backLabel = stringResource(R.string.player_back)
    val playLabel = stringResource(R.string.player_play)
    val pauseLabel = stringResource(R.string.player_pause)
    val qualityTitle = stringResource(R.string.player_quality)
    val audioTitle = stringResource(R.string.player_audio)
    val subtitlesTitle = stringResource(R.string.player_subtitles)
    val audioLayoutTitle = stringResource(R.string.player_audio_layout)
    val hdrTitle = stringResource(R.string.player_hdr_to_sdr)
    val offLabel = stringResource(R.string.player_off)
    val audioFallback = stringResource(R.string.player_audio_fallback)
    val subtitleFallback = stringResource(R.string.player_subtitle_fallback)
    val tvSeekHint = stringResource(R.string.player_tv_seek_hint)
    val tvSeekBarHint = stringResource(R.string.player_tv_seek_bar_hint)

    val qualityItems = remember(state.decision?.availableQualities) {
        state.decision?.availableQualities.orEmpty().map { q ->
            TrackMenuItem(
                id = q.id,
                label = q.label,
                subtitle = q.bitrateKbps?.let { kbps ->
                    if (kbps >= 1000) "%.1f Mbps".format(kbps / 1000.0) else "$kbps kbps"
                },
            )
        }
    }
    val audioLayoutItems = remember(state.decision?.availableAudioLayouts) {
        state.decision?.availableAudioLayouts.orEmpty().map { layout ->
            TrackMenuItem(id = layout.id, label = layout.label)
        }
    }
    val hdrItems = remember(state.decision?.sourceHdr) {
        if (state.decision?.sourceHdr.isNullOrBlank()) {
            emptyList()
        } else {
            listOf(
                TrackMenuItem(
                    id = "on",
                    label = context.getString(R.string.player_hdr_to_sdr_on),
                    subtitle = context.getString(R.string.player_hdr_to_sdr_on_hint),
                ),
                TrackMenuItem(
                    id = "off",
                    label = context.getString(R.string.player_hdr_to_sdr_off),
                    subtitle = context.getString(R.string.player_hdr_to_sdr_off_hint),
                ),
            )
        }
    }
    val audioItems = remember(state.decision?.audioStreams) {
        state.decision?.audioStreams.orEmpty().map { stream ->
            val lang = formatTrackLanguage(context, stream.language)
                .ifBlank { audioFallback }
            val title = stream.title?.trim().orEmpty()
            val label = title.ifBlank { lang }
            val subtitleParts = buildList {
                if (title.isNotBlank() && lang.isNotBlank() && !title.equals(lang, ignoreCase = true)) {
                    add(lang)
                }
                stream.channels?.let { ch ->
                    add(context.getString(R.string.player_channels, ch))
                }
                stream.codec?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            TrackMenuItem(
                id = stream.id,
                label = label,
                subtitle = subtitleParts.takeIf { it.isNotEmpty() }?.joinToString(" "),
            )
        }
    }
    val subtitleItems = remember(state.decision?.subtitleStreams, offLabel, subtitleFallback) {
        listOf(TrackMenuItem(id = null, label = offLabel)) +
            state.decision?.subtitleStreams.orEmpty().map { stream ->
                val lang = formatTrackLanguage(context, stream.language)
                    .ifBlank { subtitleFallback }
                val title = stream.title?.trim().orEmpty()
                val label = title.ifBlank { lang }
                val subtitleParts = buildList {
                    if (title.isNotBlank() && lang.isNotBlank() && !title.equals(lang, ignoreCase = true)) {
                        add(lang)
                    }
                    stream.format?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                TrackMenuItem(
                    id = stream.id,
                    label = label,
                    subtitle = subtitleParts.takeIf { it.isNotEmpty() }?.joinToString(" "),
                )
            }
    }

    fun commitScrub() {
        if (!scrubbing) return
        scrubbing = false
        viewModel.seekTo(scrubMs.toLong())
        controlsVisible = true
    }

    fun revealControls() {
        controlsVisible = true
    }

    fun toggleMenu(menu: OpenTrackMenu) {
        openMenu = if (openMenu == menu) null else menu
        if (openMenu != null) lastOpenedMenu = openMenu
        revealControls()
    }

    // TV: Back closes the track menu, then hides the controls, then exits.
    // Phone: Back closes the track menu, then exits.
    BackHandler {
        when {
            openMenu != null -> openMenu = null
            tv && controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    // Pause when the app goes to background (Home/app switch): otherwise audio
    // keeps playing and the server transcode session keeps running. No
    // auto-resume on return — the user resumes manually.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ticker only needs smooth (250ms) position updates while the chrome with
    // the seek bar is actually on screen.
    LaunchedEffect(controlsVisible, scrubbing, seekFocused) {
        viewModel.setUiVisible(controlsVisible || scrubbing || seekFocused)
        if (!controlsVisible) openMenu = null
    }

    LaunchedEffect(controlsVisible, state.playing, scrubbing, seekFocused, openMenu) {
        if (controlsVisible && state.playing && !scrubbing && !seekFocused && openMenu == null) {
            delay(4_000)
            controlsVisible = false
        }
    }

    // Move focus into the dropdown when it opens; back to the button when it
    // closes. Skip the very first composition so the play button keeps its
    // initial focus.
    var menuSeen by remember { mutableStateOf(false) }
    LaunchedEffect(openMenu) {
        if (!tv) return@LaunchedEffect
        if (openMenu != null) {
            menuSeen = true
            lastOpenedMenu = openMenu
        }
        if (openMenu == null && !menuSeen) return@LaunchedEffect
        delay(80)
        runCatching {
            if (openMenu != null) {
                menuSelectedFocus.requestFocus()
            } else {
                when (lastOpenedMenu) {
                    OpenTrackMenu.Quality -> qualityButtonFocus.requestFocus()
                    OpenTrackMenu.Audio -> audioButtonFocus.requestFocus()
                    OpenTrackMenu.Subtitles -> subtitleButtonFocus.requestFocus()
                    OpenTrackMenu.AudioLayout -> audioLayoutButtonFocus.requestFocus()
                    OpenTrackMenu.HdrToSdr -> hdrButtonFocus.requestFocus()
                    null -> Unit
                }
            }
        }
    }

    // Initial focus on play when chrome appears; do not steal seek focus later.
    LaunchedEffect(controlsVisible, tv) {
        if (tv && controlsVisible) {
            delay(100)
            runCatching { playFocus.requestFocus() }
        } else if (tv && !controlsVisible) {
            delay(40)
            runCatching { rootFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (!tv || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (!controlsVisible) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionUp, Key.DirectionDown,
                        Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause,
                        -> {
                            revealControls()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .then(
                if (tv) {
                    // Only focusable when chrome is hidden so DPAD can wake controls.
                    Modifier
                        .focusProperties { canFocus = !controlsVisible }
                        .focusable()
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        controlsVisible = !controlsVisible
                    }
                },
            ),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    player = viewModel.player
                }
            },
            update = { it.player = viewModel.player },
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false },
        )

        AnimatedVisibility(
            visible = controlsVisible || !state.playing || state.loading || state.error != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                            ),
                        ),
                    )
                    .focusProperties { canFocus = false },
            )
        }

        state.error?.let {
            ErrorState(it, onRetry = viewModel::retry)
        }

        // Center play/pause; buffering/loading/seeking = ring around the button.
        AnimatedVisibility(
            visible = showCenterTransport,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val playSize = if (tv) 60.dp else 56.dp
                Box(contentAlignment = Alignment.Center) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(playSize + 16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            trackColor = Color.White.copy(alpha = 0.15f),
                        )
                    }
                    TransportButton(
                        onClick = {
                            viewModel.togglePlay()
                            revealControls()
                        },
                        primary = true,
                        large = tv,
                        modifier = Modifier
                            .focusRequester(playFocus)
                            .focusProperties {
                                canFocus = controlsVisible
                                if (tv && controlsVisible) {
                                    down = seekFocus
                                }
                            },
                    ) {
                        Icon(
                            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.playing) pauseLabel else playLabel,
                            tint = Color.Black,
                            modifier = Modifier.size(if (tv) 30.dp else 34.dp),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && state.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (tv) 32.dp else 12.dp,
                            vertical = if (tv) 14.dp else 8.dp,
                        ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.tvFocusable(onClick = onBack, scaleFocused = 1.08f),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backLabel,
                                tint = Color.White,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val titleText = buildPlayerTitle(
                                title = state.mediaTitle,
                                year = state.mediaYear,
                                fallback = methodLabel(state.decision?.method)
                                    ?: stringResource(R.string.player_now_playing),
                            )
                            Text(
                                text = titleText,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val episodeLabel = if (
                                state.isEpisode &&
                                state.seasonNumber != null &&
                                state.episodeNumber != null
                            ) {
                                stringResource(
                                    R.string.player_season_episode,
                                    state.seasonNumber!!,
                                    state.episodeNumber!!,
                                )
                            } else {
                                null
                            }
                            val method = methodLabel(state.decision?.method)
                            val subtitle = listOfNotNull(episodeLabel, method).joinToString(" · ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val hudChips = buildList {
                                addAll(state.videoBadges)
                                addAll(state.audioBadges)
                                state.networkMbpsLabel?.let {
                                    add(stringResource(R.string.player_network_load, it))
                                }
                            }
                            if (hudChips.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    hudChips.forEach { chip ->
                                        Text(
                                            text = chip,
                                            color = Color.White.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(6.dp),
                                                )
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.canMarkUnwatched) {
                            Text(
                                text = stringResource(R.string.player_mark_unwatched),
                                color = Color.White.copy(alpha = if (state.markingUnwatched) 0.5f else 0.92f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .tvFocusable(
                                        onClick = {
                                            if (!state.markingUnwatched) viewModel.markUnwatched()
                                        },
                                        scaleFocused = 1.04f,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = if (tv) 32.dp else 16.dp,
                            end = if (tv) 32.dp else 16.dp,
                            top = 20.dp,
                            bottom = if (tv) 24.dp else 20.dp,
                        ),
                ) {
                    when (openMenu) {
                        OpenTrackMenu.Quality -> TrackDropdown(
                            title = qualityTitle,
                            items = qualityItems,
                            selectedId = state.selectedQualityId,
                            selectedFocus = menuSelectedFocus,
                            onSelect = { id ->
                                if (id != null && id != state.selectedQualityId) {
                                    viewModel.changeQuality(id)
                                }
                                openMenu = null
                                revealControls()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 10.dp),
                        )
                        OpenTrackMenu.Audio -> TrackDropdown(
                            title = audioTitle,
                            items = audioItems,
                            selectedId = state.selectedAudioId,
                            selectedFocus = menuSelectedFocus,
                            onSelect = { id ->
                                if (id != null) viewModel.changeAudio(id)
                                openMenu = null
                                revealControls()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 10.dp),
                        )
                        OpenTrackMenu.Subtitles -> TrackDropdown(
                            title = subtitlesTitle,
                            items = subtitleItems,
                            selectedId = state.selectedSubtitleId,
                            selectedFocus = menuSelectedFocus,
                            onSelect = { id ->
                                viewModel.changeSubtitle(id)
                                openMenu = null
                                revealControls()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 10.dp),
                        )
                        OpenTrackMenu.AudioLayout -> TrackDropdown(
                            title = audioLayoutTitle,
                            items = audioLayoutItems,
                            selectedId = state.selectedAudioLayout,
                            selectedFocus = menuSelectedFocus,
                            onSelect = { id ->
                                if (id != null) viewModel.changeAudioLayout(id)
                                openMenu = null
                                revealControls()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 10.dp),
                        )
                        OpenTrackMenu.HdrToSdr -> TrackDropdown(
                            title = hdrTitle,
                            items = hdrItems,
                            selectedId = if (state.forceHdrToSdr) "on" else "off",
                            selectedFocus = menuSelectedFocus,
                            onSelect = { id ->
                                viewModel.changeForceHdrToSdr(id == "on")
                                openMenu = null
                                revealControls()
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 10.dp),
                        )
                        null -> Unit
                    }

                    if (scrubbing || seekFocused) {
                        Box(Modifier.fillMaxWidth().height(20.dp)) {
                            Text(
                                text = formatTime(displayMs),
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    PlayerSeekBar(
                        value = displayMs.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { value ->
                            scrubbing = true
                            scrubMs = value
                            revealControls()
                        },
                        onValueChangeFinished = { commitScrub() },
                        valueRange = 0f..duration.toFloat(),
                        tv = tv,
                        focusRequester = seekFocus,
                        upFocus = playFocus,
                        downFocus = qualityButtonFocus,
                        onFocusedChange = { seekFocused = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (tv) 44.dp else 36.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${formatTime(displayMs)} / ${formatTime(state.durationMs)}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        if (tv) {
                            Text(
                                text = if (seekFocused) tvSeekHint else tvSeekBarHint,
                                color = Color.White.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TrackMenuButton(
                                label = state.decision?.availableQualities
                                    ?.firstOrNull { it.id == state.selectedQualityId }
                                    ?.label
                                    ?: state.selectedQualityId.replaceFirstChar(Char::uppercase),
                                icon = Icons.Default.Tune,
                                contentDescription = qualityTitle,
                                focusRequester = qualityButtonFocus,
                                upFocus = seekFocus,
                                rightFocus = if (hdrItems.isNotEmpty()) hdrButtonFocus else if (audioLayoutItems.size > 1) audioLayoutButtonFocus else audioButtonFocus,
                                onClick = { toggleMenu(OpenTrackMenu.Quality) },
                            )
                            if (hdrItems.isNotEmpty()) {
                                TrackMenuButton(
                                    label = hdrTitle,
                                    icon = Icons.Default.Tune,
                                    contentDescription = hdrTitle,
                                    focusRequester = hdrButtonFocus,
                                    upFocus = seekFocus,
                                    leftFocus = qualityButtonFocus,
                                    rightFocus = if (audioLayoutItems.size > 1) audioLayoutButtonFocus else audioButtonFocus,
                                    onClick = { toggleMenu(OpenTrackMenu.HdrToSdr) },
                                )
                            }
                            if (audioLayoutItems.size > 1) {
                                TrackMenuButton(
                                    label = audioLayoutTitle,
                                    icon = Icons.Default.Audiotrack,
                                    contentDescription = audioLayoutTitle,
                                    focusRequester = audioLayoutButtonFocus,
                                    upFocus = seekFocus,
                                    leftFocus = if (hdrItems.isNotEmpty()) hdrButtonFocus else qualityButtonFocus,
                                    rightFocus = audioButtonFocus,
                                    onClick = { toggleMenu(OpenTrackMenu.AudioLayout) },
                                )
                            }
                            TrackMenuButton(
                                label = audioTitle,
                                icon = Icons.Default.Audiotrack,
                                contentDescription = audioTitle,
                                focusRequester = audioButtonFocus,
                                upFocus = seekFocus,
                                leftFocus = when {
                                    audioLayoutItems.size > 1 -> audioLayoutButtonFocus
                                    hdrItems.isNotEmpty() -> hdrButtonFocus
                                    else -> qualityButtonFocus
                                },
                                rightFocus = subtitleButtonFocus,
                                onClick = { toggleMenu(OpenTrackMenu.Audio) },
                            )
                            TrackMenuButton(
                                label = subtitlesTitle,
                                icon = Icons.Default.ClosedCaption,
                                contentDescription = subtitlesTitle,
                                focusRequester = subtitleButtonFocus,
                                upFocus = seekFocus,
                                leftFocus = audioButtonFocus,
                                onClick = { toggleMenu(OpenTrackMenu.Subtitles) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    tv: Boolean,
    focusRequester: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    onFocusedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tv) {
        TvSeekBar(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            focusRequester = focusRequester,
            upFocus = upFocus,
            downFocus = downFocus,
            onFocusedChange = onFocusedChange,
            modifier = modifier,
        )
    } else {
        val colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    colors = colors,
                    thumbSize = DpSize(18.dp, 18.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(5.dp),
                    colors = colors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
    }
}

/**
 * Custom TV scrubber: one focus target, DPAD left/right scrub, OK commits.
 * Cancel left/right focus exit so keys scrub instead of leaving the bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    focusRequester: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    onFocusedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var keyScrubbed by remember { mutableStateOf(false) }
    val durationMs = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val stepMs = remember(durationMs) { max(10_000f, durationMs / 120f).coerceAtMost(30_000f) }
    val fraction = ((value - valueRange.start) / durationMs).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                up = upFocus
                // Keep DPAD L/R on this node — they scrub, they must not move focus.
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                down = downFocus
            }
            .onFocusChanged { state ->
                val leaving = focused && !state.isFocused
                focused = state.isFocused
                onFocusedChange(state.isFocused)
                if (leaving && keyScrubbed) {
                    keyScrubbed = false
                    onValueChangeFinished()
                }
            }
            .semantics { contentDescription = "Seek bar" }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) TvFocusColor.copy(alpha = 0.14f) else Color.Transparent,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvFocusColor else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
            // Preview keys before focus system moves focus away.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        keyScrubbed = true
                        onValueChange(
                            (value - stepMs).coerceIn(valueRange.start, valueRange.endInclusive),
                        )
                        true
                    }
                    Key.DirectionRight -> {
                        keyScrubbed = true
                        onValueChange(
                            (value + stepMs).coerceIn(valueRange.start, valueRange.endInclusive),
                        )
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (keyScrubbed) {
                            keyScrubbed = false
                            onValueChangeFinished()
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 8.dp else 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(if (focused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.001f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(if (focused) 16.dp else 11.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .then(
                        if (focused) {
                            Modifier.border(2.dp, Color.White, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** Compact pill next to the timeline; opens a track dropdown. */
@Composable
private fun TrackMenuButton(
    label: String,
    icon: ImageVector,
    contentDescription: String,
    focusRequester: FocusRequester,
    upFocus: FocusRequester,
    onClick: () -> Unit,
    leftFocus: FocusRequester? = null,
    rightFocus: FocusRequester? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusProperties {
                up = upFocus
                leftFocus?.let { left = it }
                rightFocus?.let { right = it }
            }
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * Vertical dropdown anchored above the timeline (end-aligned). One focusable
 * row per option; the selected row gets initial focus so OK re-confirms.
 */
@Composable
private fun TrackDropdown(
    title: String,
    items: List<TrackMenuItem>,
    selectedId: String?,
    selectedFocus: FocusRequester,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .clip(RoundedCornerShape(FpDimens.radiusMd))
            .background(FpColors.Surface.copy(alpha = 0.96f))
            .border(1.dp, FpColors.Border.copy(alpha = 0.8f), RoundedCornerShape(FpDimens.radiusMd))
            .padding(vertical = 6.dp)
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        items.forEach { item ->
            val selected = item.id == selectedId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier)
                    .tvFocusable(onClick = { onSelect(item.id) }, shape = RoundedCornerShape(FpDimens.radiusSm))
                    .clip(RoundedCornerShape(FpDimens.radiusSm))
                    .background(
                        if (selected) FpColors.AccentSoft else Color.Transparent,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                        Text(
                            text = sub,
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    large: Boolean = false,
    content: @Composable () -> Unit,
) {
    val size = when {
        primary && large -> 60.dp
        primary -> 56.dp
        large -> 48.dp
        else -> 48.dp
    }
    Box(
        modifier = modifier
            .size(size)
            .tvFocusable(
                onClick = onClick,
                scaleFocused = 1.1f,
                shape = CircleShape,
                // Yellow-on-yellow is invisible on the primary transport control.
                borderColor = if (primary) Color.White else TvFocusColor,
            )
            .clip(CircleShape)
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.18f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun methodLabel(method: String?): String? = when (method) {
    "DirectPlay" -> "Direct Play"
    "DirectStream" -> "Direct Stream"
    "Transcode" -> "Transcode"
    else -> method
}

private fun buildPlayerTitle(title: String?, year: Int?, fallback: String): String {
    val base = title?.takeIf { it.isNotBlank() } ?: return fallback
    return if (year != null && year > 0) "$base ($year)" else base
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
