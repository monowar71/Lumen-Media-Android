package com.freeplex.android.core.designsystem

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

fun Configuration.isTelevision(): Boolean =
    uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
fun isTvDevice(): Boolean = LocalConfiguration.current.isTelevision()

/**
 * Compact TV chrome sized for ~1080p with overscan margin.
 * Keep focus scale modest so LazyRow/Grid edges do not clip posters.
 */
object TvDimens {
    val sidebarWidth = 188.dp
    val contentPadH = 20.dp
    val contentPadV = 14.dp
    /** Extra inset so scale-on-focus does not clip at list edges. */
    val focusHalo = 12.dp
    val posterWidth = 110.dp
    val posterGap = 14.dp
    val gridMinCell = 128.dp
    val heroHeight = 168.dp
    val focusBorder = 2.dp
    val navItemPadH = 10.dp
    val navItemPadV = 7.dp
    val corner = 8.dp
    val focusScale = 1.04f
    val sectionGap = 12.dp
}

val TvFocusColor = Color(0xFFE8B84A)

val TvContentPadding = PaddingValues(
    horizontal = TvDimens.contentPadH,
    vertical = TvDimens.contentPadV,
)

@Composable
fun Modifier.tvFocusable(
    onClick: (() -> Unit)? = null,
    scaleFocused: Float = TvDimens.focusScale,
    borderWidth: Dp = TvDimens.focusBorder,
    borderColor: Color = TvFocusColor,
    shape: RoundedCornerShape = RoundedCornerShape(TvDimens.corner),
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleFocused else 1f, label = "tvFocusScale")
    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            clip = false
        }
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = if (focused) borderWidth else 0.dp,
            color = if (focused) borderColor else Color.Transparent,
            shape = shape,
        )
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick)
            else Modifier.focusable(),
        )
}

@Composable
fun Modifier.tvNavItem(
    selected: Boolean,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> TvFocusColor.copy(alpha = 0.22f)
        selected -> TvFocusColor.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    return this
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(TvDimens.corner))
        .background(bg)
        .border(
            width = if (focused) TvDimens.focusBorder else 0.dp,
            color = if (focused) TvFocusColor else Color.Transparent,
            shape = RoundedCornerShape(TvDimens.corner),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = TvDimens.navItemPadH, vertical = TvDimens.navItemPadV)
}
