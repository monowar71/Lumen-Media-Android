package com.lumenmedia.android.core.designsystem

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

/** @deprecated Prefer [FpDimens] — kept for gradual migration. */
@Deprecated("Use FpDimens", ReplaceWith("FpDimens"))
object TvDimens {
    val sidebarWidth = FpDimens.sidebarWidth
    val contentPadH = FpDimens.contentPadHTv
    val contentPadV = FpDimens.contentPadVTv
    val focusHalo = FpDimens.focusHalo
    val posterWidth = FpDimens.posterTv
    val posterGap = FpDimens.posterGapTv
    val gridMinCell = FpDimens.gridMinCellTv
    val heroHeight = FpDimens.heroTv
    val focusBorder = FpDimens.focusBorder
    val navItemPadH = FpDimens.space10
    val navItemPadV = FpDimens.space8
    val corner = FpDimens.radiusMd
    val focusScale = FpDimens.focusScale
    val sectionGap = FpDimens.space16
}

val TvFocusColor = FpColors.Accent

val TvContentPadding = PaddingValues(
    horizontal = FpDimens.contentPadHTv,
    vertical = FpDimens.contentPadVTv,
)

@Composable
fun Modifier.tvFocusable(
    onClick: (() -> Unit)? = null,
    scaleFocused: Float = FpDimens.focusScale,
    borderWidth: Dp = FpDimens.focusBorder,
    borderColor: Color = FpColors.Accent,
    shape: RoundedCornerShape = RoundedCornerShape(FpDimens.radiusMd),
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
        focused -> FpColors.AccentSoft
        selected -> FpColors.AccentSoft.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    return this
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(FpDimens.radiusMd))
        .background(bg)
        .border(
            width = if (focused) FpDimens.focusBorder else 0.dp,
            color = if (focused) FpColors.Accent else Color.Transparent,
            shape = RoundedCornerShape(FpDimens.radiusMd),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = FpDimens.space12, vertical = FpDimens.space10)
}
