package com.drivesync.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens. Single source: design/tokens.json (same names as the Figma variables).
 * Car display, dark by default, large type: minimum body 20sp, minimum touch target 76dp.
 */
object DS {
    val Bg = Color(0xFF0B0F14)
    val Surface = Color(0xFF141A22)
    val SurfaceRaised = Color(0xFF1C2430)
    val Border = Color(0xFF2A3441)
    val Text = Color(0xFFF2F5F8)
    val TextMuted = Color(0xFF9AA7B5)
    val Primary = Color(0xFF3DA9FC)
    val OnPrimary = Color(0xFF04121F)
    val Success = Color(0xFF34C38F)
    val Warning = Color(0xFFF5B342)
    val Danger = Color(0xFFFF5A5F)
    val SourceUser = Color(0xFF3DA9FC)
    val SourceAi = Color(0xFFB48CFF)
    val Simulation = Color(0xFFF5B342)

    val Gutter = 32.dp
    val Gap = 16.dp
    val Radius = 20.dp
    val TouchTarget = 76.dp
    val MemoButton = 200.dp
}

private val typography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun DriveSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DS.Bg,
            surface = DS.Surface,
            surfaceVariant = DS.SurfaceRaised,
            primary = DS.Primary,
            onPrimary = DS.OnPrimary,
            onBackground = DS.Text,
            onSurface = DS.Text,
            onSurfaceVariant = DS.TextMuted,
            error = DS.Danger,
            outline = DS.Border,
        ),
        typography = typography,
        content = content,
    )
}
