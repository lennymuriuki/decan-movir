package com.example.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.desktop.player.PlayerScreen
import com.example.desktop.ui.BrowseKind
import com.example.desktop.ui.BrowseScreen
import com.example.desktop.ui.DecanTheme
import com.example.desktop.ui.DetailScreen
import com.example.desktop.ui.DownloadsScreen
import com.example.desktop.ui.HomeScreen
import com.example.desktop.ui.LibraryScreen
import com.example.desktop.ui.SearchScreen
import com.example.desktop.ui.SettingsScreen
import kotlinx.coroutines.delay

fun main() = application {
  val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp))
  val app = remember { AppState() }
  val icon =
    remember {
      runCatching {
        Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { BitmapPainter(loadImageBitmap(it)) }
      }.getOrNull()
    }

  LaunchedEffect(app.fullscreen) {
    windowState.placement = if (app.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
  }

  Window(onCloseRequest = ::exitApplication, title = "Decan Movie", state = windowState, icon = icon) {
    DecanTheme(app.store.settings.value.darkTheme) {
      Surface(Modifier.fillMaxSize()) { AppRoot(app) }
    }
  }
}

private data class NavItem(val label: String, val icon: ImageVector, val route: Route)

private val NAV =
  listOf(
    NavItem("Home", Icons.Filled.Home, Route.Home),
    NavItem("Movies", Icons.Filled.PlayArrow, Route.Movies),
    NavItem("Series", Icons.Filled.List, Route.Series),
    NavItem("Anime", Icons.Filled.Star, Route.Anime),
    NavItem("Search", Icons.Filled.Search, Route.Search),
    NavItem("Library", Icons.Filled.Favorite, Route.Library),
    NavItem("Downloads", Icons.Filled.ShoppingCart, Route.Downloads),
    NavItem("Settings", Icons.Filled.Settings, Route.Settings),
  )

@Composable
private fun AppRoot(app: AppState) {
  val route = app.route
  // Highlight the section the current page belongs to.
  Row(Modifier.fillMaxSize()) {
    if (!app.fullscreen && route !is Route.Player) {
      NavigationRail {
        NAV.forEach { item ->
          NavigationRailItem(
            selected = route == item.route,
            onClick = { app.goTo(item.route) },
            icon = { Icon(item.icon, item.label) },
            label = { Text(item.label) },
          )
        }
      }
    }
    Box(Modifier.weight(1f).fillMaxSize()) {
      when (route) {
        Route.Home -> HomeScreen(app)
        Route.Movies -> BrowseScreen(app, BrowseKind.MOVIES)
        Route.Series -> BrowseScreen(app, BrowseKind.SERIES)
        Route.Anime -> BrowseScreen(app, BrowseKind.ANIME)
        Route.Search -> SearchScreen(app)
        Route.Library -> LibraryScreen(app)
        Route.Downloads -> DownloadsScreen(app)
        Route.Settings -> SettingsScreen(app)
        is Route.Detail -> DetailScreen(app, route.movie)
        is Route.Player -> PlayerScreen(app, route.request)
      }
      app.toast?.let { msg ->
        LaunchedEffect(msg) { delay(3500); if (app.toast == msg) app.toast = null }
        Snackbar(Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text(msg) }
      }
    }
  }
}
