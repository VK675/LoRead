package io.github.vk675.folio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vk675.folio.ui.components.AppBackground
import io.github.vk675.folio.ui.navigation.FolioNavHost
import io.github.vk675.folio.ui.theme.FolioTheme

// The only Activity in the app. It hosts the Compose UI and nothing else:
// every screen is a composable function reached through FolioNavHost.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars. SystemBarStyle.dark means "the content is dark",
        // so the clock and icons in the status bar are drawn light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            FolioTheme {
                // The background sits outside the NavHost, so it stays still
                // while screens fade in and out on top of it.
                AppBackground {
                    FolioNavHost()
                }
            }
        }
    }
}
