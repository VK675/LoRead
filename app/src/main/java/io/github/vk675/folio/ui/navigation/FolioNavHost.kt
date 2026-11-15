package io.github.vk675.folio.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.vk675.folio.library.LibraryScreen
import io.github.vk675.folio.player.PlayerScreen
import io.github.vk675.folio.reader.ReaderScreen

// Route names live in one place so a typo cannot break navigation silently.
object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player"

    // {uri} is a placeholder: the real route looks like "book/content%3A%2F%2F...".
    const val BOOK_URI = "uri"
    const val BOOK = "book/{$BOOK_URI}"

    // A file URI contains "/" and ":", which would break the route,
    // so it is percent-encoded here and decoded by Navigation on the other side.
    fun book(uri: Uri): String = "book/${Uri.encode(uri.toString())}"
}

@Composable
fun FolioNavHost(modifier: Modifier = Modifier) {
    // NavController owns the back stack; remember keeps it across recompositions.
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier
    ) {
        // Screens get plain callbacks instead of the NavController itself,
        // so they stay easy to preview and know nothing about navigation.
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenBook = { uri -> navController.navigate(Routes.book(uri)) },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) }
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.BOOK,
            arguments = listOf(navArgument(Routes.BOOK_URI) { type = NavType.StringType })
        ) { entry ->
            val uri = remember(entry) {
                Uri.parse(entry.arguments?.getString(Routes.BOOK_URI).orEmpty())
            }
            ReaderScreen(uri = uri, onBack = { navController.popBackStack() })
        }
    }
}
