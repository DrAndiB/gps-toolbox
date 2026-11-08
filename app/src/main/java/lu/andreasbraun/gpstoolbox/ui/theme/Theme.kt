package lu.andreasbraun.gpstoolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Palette from the design canvas: dark instrument look, teal accent, one colour per constellation. */
object C {
    val Bg = Color(0xFF0E1114)
    val Deep = Color(0xFF12161A)
    val Surface = Color(0xFF171B20)
    val SurfaceHi = Color(0xFF1E2329)
    val Line = Color(0xFF262C33)
    val Grid = Color(0xFF2F363E)
    val Text = Color(0xFFE8ECEF)
    val TextSoft = Color(0xFFC7CED5)
    val Muted = Color(0xFF9AA4AE)
    val Faint = Color(0xFF6B757F)
    val Accent = Color(0xFF4CC9B0)
    val Amber = Color(0xFFF5B83D)
    val Red = Color(0xFFE86F6F)
    val Blue = Color(0xFF6EA8FE)
}

val Mono: FontFamily = FontFamily.Monospace

@Composable
fun GpsTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.Accent,
        onPrimary = C.Bg,
        secondary = C.Amber,
        onSecondary = C.Bg,
        background = C.Bg,
        onBackground = C.Text,
        surface = C.Surface,
        onSurface = C.Text,
        surfaceVariant = C.SurfaceHi,
        onSurfaceVariant = C.Muted,
        surfaceContainer = C.Surface,
        surfaceContainerHigh = C.Surface,
        surfaceContainerHighest = C.SurfaceHi,
        outline = C.Line,
        error = C.Red,
        onError = C.Bg,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
