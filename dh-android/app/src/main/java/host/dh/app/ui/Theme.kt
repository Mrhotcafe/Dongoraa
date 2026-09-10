package host.dh.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF22D3EE)
val AccentDim = Color(0xFF0E7490)
val Danger = Color(0xFFEF4444)
val Bg = Color(0xFF0B0F14)
val Surface = Color(0xFF141B23)
val SurfaceHi = Color(0xFF1E2733)
val OnBg = Color(0xFFE6EDF3)
val Muted = Color(0xFF8B9AA7)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00202B),
    secondary = AccentDim,
    background = Bg,
    onBackground = OnBg,
    surface = Surface,
    onSurface = OnBg,
    surfaceVariant = SurfaceHi,
    error = Danger,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
