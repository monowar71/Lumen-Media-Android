package com.freeplex.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFE8B84A)
private val Bg = Color(0xFF0B0D10)
private val Surface = Color(0xFF12151C)
private val SurfaceHigh = Color(0xFF1A1F2A)
private val Text = Color(0xFFF2F4F8)
private val Muted = Color(0xFF9AA3B2)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Bg,
    background = Bg,
    onBackground = Text,
    surface = Surface,
    onSurface = Text,
    onSurfaceVariant = Muted,
    surfaceVariant = SurfaceHigh,
    secondary = Color(0xFF3B82F6),
    error = Color(0xFFEF4444),
)

private val PhoneTypography = Typography()

/** Dense TV type scale — readable from a couch, not oversized. */
private val TvTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Text, lineHeight = 38.sp),
    displayMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Text, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Text, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Text, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Text, lineHeight = 22.sp),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Text, lineHeight = 20.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Text, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, color = Text, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, color = Text, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = Muted, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Muted, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Muted, lineHeight = 12.sp),
)

@Composable
fun FreePlexTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val unused = isSystemInDarkTheme()
    val typography = if (isTvDevice()) TvTypography else PhoneTypography
    MaterialTheme(colorScheme = Scheme, typography = typography, content = content)
}
