package com.lumenmedia.android.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lumenmedia.android.R

/** Web-aligned LumenMedia palette (client_web/src/index.css + docs/brand). */
object FpColors {
    val Bg = Color(0xFF0B1F1A)
    val Surface = Color(0xFF122A23)
    val Surface2 = Color(0xFF1A3830)
    val Surface3 = Color(0xFF224840)
    val Border = Color(0xFF2A554A)
    val Muted = Color(0xFF8AA399)
    val Text = Color(0xFFE7F6F0)
    val Accent = Color(0xFF3ECF9A)
    val AccentHover = Color(0xFF5FE0B0)
    val AccentSoft = Color(0x2E3ECF9A) // ~18% accent
    val OnAccent = Color(0xFF0B1F1A)
    val Error = Color(0xFFF87171)
    val Success = Color(0xFF6EE7B7)
}

private val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val Scheme = darkColorScheme(
    primary = FpColors.Accent,
    onPrimary = FpColors.OnAccent,
    primaryContainer = FpColors.AccentSoft,
    onPrimaryContainer = FpColors.Accent,
    secondary = FpColors.Surface3,
    onSecondary = FpColors.Text,
    tertiary = FpColors.Success,
    onTertiary = FpColors.OnAccent,
    background = FpColors.Bg,
    onBackground = FpColors.Text,
    surface = FpColors.Surface,
    onSurface = FpColors.Text,
    onSurfaceVariant = FpColors.Muted,
    surfaceVariant = FpColors.Surface2,
    surfaceContainerHighest = FpColors.Surface3,
    outline = FpColors.Border,
    outlineVariant = FpColors.Border.copy(alpha = 0.6f),
    error = FpColors.Error,
    onError = FpColors.OnAccent,
)

private val FpShapes = Shapes(
    extraSmall = RoundedCornerShape(FpDimens.radiusSm),
    small = RoundedCornerShape(FpDimens.radiusSm),
    medium = RoundedCornerShape(FpDimens.radiusMd),
    large = RoundedCornerShape(FpDimens.radiusLg),
    extraLarge = RoundedCornerShape(FpDimens.radiusXl),
)

private fun typeStyle(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    letterSpacing: Float = 0f,
    color: Color = FpColors.Text,
) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    color = color,
)

private val PhoneTypography = Typography(
    displayLarge = typeStyle(40, FontWeight.ExtraBold, 46, -0.5f),
    displayMedium = typeStyle(34, FontWeight.ExtraBold, 40, -0.4f),
    displaySmall = typeStyle(28, FontWeight.Bold, 34, -0.3f),
    headlineLarge = typeStyle(26, FontWeight.ExtraBold, 32, -0.3f),
    headlineMedium = typeStyle(22, FontWeight.Bold, 28, -0.2f),
    headlineSmall = typeStyle(18, FontWeight.Bold, 24, -0.2f),
    titleLarge = typeStyle(17, FontWeight.SemiBold, 24),
    titleMedium = typeStyle(15, FontWeight.SemiBold, 22),
    titleSmall = typeStyle(13, FontWeight.SemiBold, 18),
    bodyLarge = typeStyle(15, FontWeight.Normal, 22),
    bodyMedium = typeStyle(14, FontWeight.Normal, 20),
    bodySmall = typeStyle(12, FontWeight.Normal, 16, color = FpColors.Muted),
    labelLarge = typeStyle(13, FontWeight.SemiBold, 18),
    labelMedium = typeStyle(11, FontWeight.Medium, 14, color = FpColors.Muted),
    labelSmall = typeStyle(10, FontWeight.Medium, 12, color = FpColors.Muted),
)

/** Slightly denser for 10-foot UI — readable from a couch without oversized chrome. */
private val TvTypography = Typography(
    displayLarge = typeStyle(34, FontWeight.ExtraBold, 40, -0.4f),
    displayMedium = typeStyle(28, FontWeight.ExtraBold, 34, -0.3f),
    displaySmall = typeStyle(24, FontWeight.Bold, 30, -0.2f),
    headlineLarge = typeStyle(24, FontWeight.ExtraBold, 30, -0.2f),
    headlineMedium = typeStyle(20, FontWeight.Bold, 26, -0.2f),
    headlineSmall = typeStyle(17, FontWeight.Bold, 22),
    titleLarge = typeStyle(16, FontWeight.SemiBold, 22),
    titleMedium = typeStyle(14, FontWeight.SemiBold, 20),
    titleSmall = typeStyle(13, FontWeight.Medium, 18),
    bodyLarge = typeStyle(14, FontWeight.Normal, 20),
    bodyMedium = typeStyle(13, FontWeight.Normal, 18),
    bodySmall = typeStyle(12, FontWeight.Normal, 16, color = FpColors.Muted),
    labelLarge = typeStyle(12, FontWeight.SemiBold, 16),
    labelMedium = typeStyle(11, FontWeight.Medium, 14, color = FpColors.Muted),
    labelSmall = typeStyle(10, FontWeight.Medium, 12, color = FpColors.Muted),
)

@Composable
fun LumenMediaTheme(content: @Composable () -> Unit) {
    val typography = if (isTvDevice()) TvTypography else PhoneTypography
    MaterialTheme(
        colorScheme = Scheme,
        typography = typography,
        shapes = FpShapes,
        content = content,
    )
}
