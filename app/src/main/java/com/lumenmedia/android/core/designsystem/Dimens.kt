package com.lumenmedia.android.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing / sizing tokens aligned with the web client.
 * TV values keep couch readability and overscan margin without oversized chrome.
 */
object FpDimens {
    val space2 = 2.dp
    val space4 = 4.dp
    val space6 = 6.dp
    val space8 = 8.dp
    val space10 = 10.dp
    val space12 = 12.dp
    val space14 = 14.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space28 = 28.dp
    val space32 = 32.dp

    val radiusSm = 8.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 20.dp
    val radiusPill = 999.dp

    val posterPhone = 132.dp
    val posterTv = 128.dp
    val posterGapPhone = 12.dp
    val posterGapTv = 16.dp
    val gridMinCellPhone = 112.dp
    val gridMinCellTv = 140.dp

    val heroPhone = 220.dp
    val heroTv = 240.dp

    val sidebarWidth = 220.dp
    val focusHalo = 12.dp
    val focusBorder = 2.dp
    val focusScale = 1.04f

    val contentPadHPhone = 16.dp
    val contentPadHTv = 28.dp
    val contentPadVPhone = 12.dp
    val contentPadVTv = 16.dp

    val buttonHeight = 44.dp
    val buttonHeightSm = 36.dp
    val episodeThumbW = 140.dp
    val episodeThumbH = 79.dp
    val episodeThumbWTv = 160.dp
    val episodeThumbHTv = 90.dp
}

@Composable
fun fpContentPadding(): PaddingValues {
    val tv = isTvDevice()
    return PaddingValues(
        horizontal = if (tv) FpDimens.contentPadHTv else FpDimens.contentPadHPhone,
        vertical = if (tv) FpDimens.contentPadVTv else FpDimens.contentPadVPhone,
    )
}

@Composable
fun fpPosterWidth(): Dp = if (isTvDevice()) FpDimens.posterTv else FpDimens.posterPhone

@Composable
fun fpPosterGap(): Dp = if (isTvDevice()) FpDimens.posterGapTv else FpDimens.posterGapPhone
