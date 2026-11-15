package io.github.vk675.folio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is dark only, so system light mode and
// wallpaper-based dynamic color are ignored on purpose.
private val FolioColorScheme = darkColorScheme(
    primary = ButtonLight,
    onPrimary = OnButtonLight,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = PanelBottom,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun FolioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FolioColorScheme,
        typography = Typography,
        content = content
    )
}
