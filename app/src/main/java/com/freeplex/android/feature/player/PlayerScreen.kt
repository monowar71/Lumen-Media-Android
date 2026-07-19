package com.freeplex.android.feature.player

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.freeplex.android.core.designsystem.ErrorState
import com.freeplex.android.core.designsystem.TvFocusColor
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvFocusable
import kotlinx.coroutines.delay
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tv = isTvDevice()
    var controlsVisible by remember { mutableStateOf(true) }
    var showQuality by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableFloatStateOf(0f) }
    var seekFocused by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val skipBackFocus = remember { FocusRequester() }
    val skipFwdFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    val displayMs = if (scrubbing) scrubMs.toLong() else state.positionMs
    val duration = state.durationMs.coerceAtLeast(1L)

    fun commitScrub() {
        if (!scrubbing) return
        scrubbing = false
        viewModel.seekTo(scrubMs.toLong())
        controlsVisible = true
    }

    fun revealControls() {
        controlsVisible = true
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(controlsVisible, state.playing, scrubbing, seekFocused) {
        if (controlsVisible && state.playing && !scrubbing && !seekFocused) {
            delay(4_000)
            controlsVisible = false
            showQuality = false
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

        if ((state.loading || state.buffering || state.seeking) && state.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = when {
                            state.seeking -> "Seeking…"
                            state.loading -> "Loading…"
                            else -> "Buffering…"
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        state.error?.let {
            ErrorState(it, onRetry = viewModel::retry)
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
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = methodLabel(state.decision?.method) ?: "Now playing",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.selectedQualityId,
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(
                            onClick = {
                                showQuality = !showQuality
                                revealControls()
                            },
                            modifier = Modifier.tvFocusable(
                                onClick = {
                                    showQuality = !showQuality
                                    revealControls()
                                },
                                scaleFocused = 1.06f,
                            ),
                        ) {
                            Text("Quality", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (showQuality) {
                        val qualities = state.decision?.availableQualities.orEmpty()
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            items(qualities, key = { it.id }) { q ->
                                val selected = q.id == state.selectedQualityId
                                Text(
                                    text = q.label,
                                    color = if (selected) Color.Black else Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .tvFocusable(
                                            onClick = {
                                                viewModel.changeQuality(q.id)
                                                showQuality = false
                                                revealControls()
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                        )
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else Color.White.copy(alpha = 0.15f),
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (tv) 18.dp else 18.dp),
                ) {
                    TransportButton(
                        onClick = {
                            viewModel.skipBy(-10_000)
                            revealControls()
                        },
                        large = tv,
                        modifier = Modifier
                            .focusRequester(skipBackFocus)
                            .then(
                                if (tv) {
                                    Modifier.focusProperties {
                                        right = playFocus
                                        down = seekFocus
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Back 10s",
                            tint = Color.White,
                            modifier = Modifier.size(if (tv) 26.dp else 28.dp),
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
                            .then(
                                if (tv) {
                                    Modifier.focusProperties {
                                        left = skipBackFocus
                                        right = skipFwdFocus
                                        down = seekFocus
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Icon(
                            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(if (tv) 30.dp else 34.dp),
                        )
                    }
                    TransportButton(
                        onClick = {
                            viewModel.skipBy(10_000)
                            revealControls()
                        },
                        large = tv,
                        modifier = Modifier
                            .focusRequester(skipFwdFocus)
                            .then(
                                if (tv) {
                                    Modifier.focusProperties {
                                        left = playFocus
                                        down = seekFocus
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(if (tv) 26.dp else 28.dp),
                        )
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
                                text = if (seekFocused) "← → seek · OK confirm" else "↓ seek bar",
                                color = Color.White.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.labelMedium,
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
                down = FocusRequester.Cancel
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

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
