package com.decanmovie.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.decanmovie.desktop.model.DesktopDestination
import com.decanmovie.desktop.model.DownloadTaskStatus
import com.decanmovie.desktop.platform.DesktopTrayAndNotifier
import com.decanmovie.desktop.ui.components.DesktopSidebarRail
import com.decanmovie.desktop.ui.components.DesktopWindowTitleBar
import com.decanmovie.desktop.ui.screens.CatalogBrowseKind
import com.decanmovie.desktop.ui.screens.CatalogBrowseScreen
import com.decanmovie.desktop.ui.screens.DesktopTheaterPlayerScreen
import com.decanmovie.desktop.ui.screens.DesktopVerticalDramaPlayerScreen
import com.decanmovie.desktop.ui.screens.DownloadsScreen
import com.decanmovie.desktop.ui.screens.HistoryScreen
import com.decanmovie.desktop.ui.screens.HomeScreen
import com.decanmovie.desktop.ui.screens.MovieDetailScreen
import com.decanmovie.desktop.ui.screens.SearchScreen
import com.decanmovie.desktop.ui.screens.SettingsScreen
import com.decanmovie.desktop.ui.screens.UpdateAvailableDialog
import com.decanmovie.desktop.ui.screens.WatchlistScreen
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import com.decanmovie.desktop.ui.theme.DecanDesktopTheme
import com.decanmovie.desktop.viewmodel.ActiveDesktopOverlay
import com.decanmovie.desktop.viewmodel.DesktopAppController
import java.awt.Dimension
import javax.imageio.ImageIO

fun main(args: Array<String>) = application {
  val startMinimized = args.any { it.equals("--minimized", ignoreCase = true) }
  var isWindowVisible by remember { mutableStateOf(!startMinimized) }

  var controllerRef by remember { mutableStateOf<DesktopAppController?>(null) }

  val trayNotifier = remember {
    DesktopTrayAndNotifier(
      onRestoreWindow = { isWindowVisible = true },
      onOpenDownloads = {
        isWindowVisible = true
        controllerRef?.navigateTo(DesktopDestination.DOWNLOADS)
      },
      onCheckUpdates = {
        isWindowVisible = true
        controllerRef?.checkForUpdates(manual = true)
      },
      onQuitApp = {
        controllerRef?.shutdown()
        exitApplication()
      },
    )
  }

  val controller = remember {
    DesktopAppController(notifier = trayNotifier).also { controllerRef = it }
  }

  val settings by controller.storage.settings.collectAsState()
  val isFullscreen by controller.isFullscreen.collectAsState()

  val windowState = rememberWindowState(
    placement = WindowPlacement.Floating,
    position = WindowPosition(Alignment.Center),
    size = DpSize(1340.dp, 840.dp),
  )

  LaunchedEffect(isFullscreen) {
    windowState.placement =
      if (isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
  }

  DisposableEffect(Unit) {
    trayNotifier.install()
    onDispose {
      trayNotifier.uninstall()
      controller.shutdown()
    }
  }

  val appIcon = remember {
    runCatching {
      val stream = DesktopAppController::class.java.getResourceAsStream("/icons/decan-movie.png")
      stream?.use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }
    }.getOrNull()
  }

  Window(
    onCloseRequest = {
      if (settings.minimizeToTrayOnClose && trayNotifier.install()) {
        isWindowVisible = false
      } else {
        controller.shutdown()
        exitApplication()
      }
    },
    visible = isWindowVisible,
    state = windowState,
    title = "Decan Movie",
    icon = appIcon,
    undecorated = true,
    onPreviewKeyEvent = { ev ->
      if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.K) {
        controller.navigateTo(DesktopDestination.SEARCH)
        true
      } else {
        false
      }
    },
  ) {
    LaunchedEffect(Unit) {
      window.minimumSize = Dimension(1024, 660)
    }

    val searchState by controller.searchState.collectAsState()
    val updateState by controller.updateState.collectAsState()

    DecanDesktopTheme {
      DecanDesktopShell(
        controller = controller,
        titleBar = {
          if (!isFullscreen) {
            DesktopWindowTitleBar(
              searchQuery = searchState.query,
              onSearchQueryChange = { q ->
                controller.updateSearchQuery(q)
                if (controller.destination.value != DesktopDestination.SEARCH && q.isNotEmpty()) {
                  controller.navigateTo(DesktopDestination.SEARCH)
                }
              },
              onSubmitSearch = {
                controller.navigateTo(DesktopDestination.SEARCH)
                controller.executeSearch()
              },
              onOpenLocalFile = { controller.pickAndPlayLocalFile() },
              hasUpdateAvailable = updateState.hasUpdate,
              updateTag = updateState.latestRelease?.tagName,
              onClickUpdateBadge = { controller.openUpdateDialog() },
              isMaximized = windowState.placement == WindowPlacement.Maximized,
              onMinimize = { windowState.isMinimized = true },
              onToggleMaximize = {
                windowState.placement =
                  if (windowState.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                  } else {
                    WindowPlacement.Maximized
                  }
              },
              onClose = {
                if (settings.minimizeToTrayOnClose && trayNotifier.install()) {
                  isWindowVisible = false
                } else {
                  controller.shutdown()
                  exitApplication()
                }
              },
            )
          }
        },
      )
    }
  }
}

@Composable
fun DecanDesktopShell(
  controller: DesktopAppController,
  titleBar: (@Composable () -> Unit)? = null,
) {
  val destination by controller.destination.collectAsState()
  val overlay by controller.overlay.collectAsState()
  val isFullscreen by controller.isFullscreen.collectAsState()

  val homeFeed by controller.homeFeed.collectAsState()
  val isHomeLoading by controller.isHomeLoading.collectAsState()
  val moviesState by controller.moviesState.collectAsState()
  val seriesState by controller.seriesState.collectAsState()
  val animeState by controller.animeState.collectAsState()
  val dramasState by controller.dramasState.collectAsState()
  val searchState by controller.searchState.collectAsState()
  val updateState by controller.updateState.collectAsState()
  val bannerMessage by controller.bannerMessage.collectAsState()

  val settings by controller.storage.settings.collectAsState()
  val watchlist by controller.storage.watchlist.collectAsState()
  val history by controller.storage.history.collectAsState()
  val downloadTasks by controller.downloadManager.tasks.collectAsState()
  val localItems by controller.downloadManager.localLibrary.collectAsState()

  val watchlistIds = remember(watchlist) { watchlist.mapTo(HashSet()) { it.movie.id } }
  val activeDownloadsCount =
    remember(downloadTasks) {
      downloadTasks.count {
        it.status == DownloadTaskStatus.DOWNLOADING || it.status == DownloadTaskStatus.QUEUED
      }
    }

  val isPlaybackOverlay =
    overlay is ActiveDesktopOverlay.TheaterPlayer ||
      overlay is ActiveDesktopOverlay.VerticalDramaPlayer

  Box(modifier = Modifier.fillMaxSize().background(DecanDesktopColors.Obsidian)) {
    Column(modifier = Modifier.fillMaxSize()) {
      titleBar?.invoke()

      Row(modifier = Modifier.weight(1f).fillMaxSize()) {
        if (!isFullscreen && !isPlaybackOverlay) {
          DesktopSidebarRail(
            currentDestination = destination,
            activeDownloadCount = activeDownloadsCount,
            watchlistCount = watchlist.size,
            hasUpdateAvailable = updateState.hasUpdate,
            onSelectDestination = { controller.navigateTo(it) },
          )
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          // Base Route Screen
          when (destination) {
            DesktopDestination.HOME ->
              HomeScreen(
                feed = homeFeed,
                isLoading = isHomeLoading,
                history = history,
                watchlistIds = watchlistIds,
                compactCards = settings.compactCards,
                onRefresh = { controller.refreshHome(force = true) },
                onMovieClick = { controller.openMovieDetail(it) },
                onQuickPlay = { controller.openPlaybackForMovie(it) },
                onResumeHistory = { h ->
                  val m = controller.catalogRepository.getCachedMovie(h.movieId)
                  if (m != null) {
                    controller.openPlaybackForMovie(m, h.season, h.episode, h.positionMs)
                  } else {
                    controller.openMovieId(h.movieId)
                  }
                },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
              )

            DesktopDestination.MOVIES ->
              CatalogBrowseScreen(
                kind = CatalogBrowseKind.MOVIES,
                state = moviesState,
                watchlistIds = watchlistIds,
                compactCards = settings.compactCards,
                onSelectFilter = { controller.setMoviesFilter(genre = it) },
                onSelectCategorySort = { controller.setMoviesFilter(sortMode = it) },
                onSelectAnimeSort = {},
                onRefresh = { controller.loadMovies(reset = true) },
                onLoadMore = { controller.loadMovies(reset = false) },
                onMovieClick = { controller.openMovieDetail(it) },
                onQuickPlay = { controller.openPlaybackForMovie(it) },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
              )

            DesktopDestination.SERIES ->
              CatalogBrowseScreen(
                kind = CatalogBrowseKind.SERIES,
                state = seriesState,
                watchlistIds = watchlistIds,
                compactCards = settings.compactCards,
                onSelectFilter = { controller.setSeriesFilter(genre = it) },
                onSelectCategorySort = { controller.setSeriesFilter(sortMode = it) },
                onSelectAnimeSort = {},
                onRefresh = { controller.loadSeries(reset = true) },
                onLoadMore = { controller.loadSeries(reset = false) },
                onMovieClick = { controller.openMovieDetail(it) },
                onQuickPlay = { controller.openPlaybackForMovie(it) },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
              )

            DesktopDestination.ANIME ->
              CatalogBrowseScreen(
                kind = CatalogBrowseKind.ANIME,
                state = animeState,
                watchlistIds = watchlistIds,
                compactCards = settings.compactCards,
                onSelectFilter = { controller.setAnimeFilter(genre = it) },
                onSelectCategorySort = {},
                onSelectAnimeSort = { controller.setAnimeFilter(sortMode = it) },
                onRefresh = { controller.loadAnime(reset = true) },
                onLoadMore = { controller.loadAnime(reset = false) },
                onMovieClick = { controller.openMovieDetail(it) },
                onQuickPlay = { controller.openPlaybackForMovie(it) },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
              )

            DesktopDestination.DRAMAS ->
              CatalogBrowseScreen(
                kind = CatalogBrowseKind.DRAMAS,
                state = dramasState,
                watchlistIds = watchlistIds,
                compactCards = settings.compactCards,
                onSelectFilter = { controller.setDramasFilter(region = it) },
                onSelectCategorySort = { controller.setDramasFilter(sortMode = it) },
                onSelectAnimeSort = {},
                onRefresh = { controller.loadDramas(reset = true) },
                onLoadMore = { controller.loadDramas(reset = false) },
                onMovieClick = { controller.openMovieDetail(it) },
                onQuickPlay = { controller.openPlaybackForMovie(it, season = 1, episode = 1) },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
              )

            DesktopDestination.SEARCH ->
              SearchScreen(
                state = searchState,
                trendingKeywords = homeFeed?.trendingKeywords.orEmpty(),
                watchlistIds = watchlistIds,
                compactCards = settings.compactCards,
                onQueryChange = { controller.updateSearchQuery(it) },
                onSearchSubmit = { controller.executeSearch() },
                onFilterChange = { controller.setSearchMediaFilter(it) },
                onMovieClick = { controller.openMovieDetail(it) },
                onQuickPlay = { controller.openPlaybackForMovie(it) },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
              )

            DesktopDestination.WATCHLIST ->
              WatchlistScreen(
                entries = watchlist,
                compactCards = settings.compactCards,
                onMovieClick = { controller.openMovieDetail(it) },
                onQuickPlay = { controller.openPlaybackForMovie(it) },
                onRemove = { controller.storage.removeFromWatchlist(it) },
                onSaveReview = { m, st, fav, r, n -> controller.saveWatchlistDetails(m, st, fav, r, n) },
              )

            DesktopDestination.DOWNLOADS ->
              DownloadsScreen(
                downloadRoot = settings.downloadDirectory,
                tasks = downloadTasks,
                localItems = localItems,
                onRefreshLibrary = { controller.downloadManager.refreshLocalLibrary(settings.downloadDirectory) },
                onPickLocalFile = { controller.pickAndPlayLocalFile() },
                onPauseTask = { controller.downloadManager.pauseDownload(it) },
                onResumeTask = { controller.downloadManager.resumeDownload(it, settings.downloadDirectory) },
                onCancelTask = { controller.downloadManager.cancelDownload(it) },
                onRemoveTask = { controller.downloadManager.removeTask(it) },
                onPlayLocalItem = { controller.playLocalMedia(it) },
              )

            DesktopDestination.HISTORY ->
              HistoryScreen(
                history = history,
                onResume = { h ->
                  val m = controller.catalogRepository.getCachedMovie(h.movieId)
                  if (m != null) {
                    controller.openPlaybackForMovie(m, h.season, h.episode, h.positionMs)
                  } else {
                    controller.openMovieId(h.movieId)
                  }
                },
                onOpenDetail = { h -> controller.openMovieId(h.movieId) },
                onRemoveItem = { controller.storage.removeHistoryItem(it) },
                onClearAll = { controller.storage.clearHistory() },
              )

            DesktopDestination.SETTINGS ->
              SettingsScreen(
                settings = settings,
                updateState = updateState,
                onUpdateSettings = { transform -> controller.storage.updateSettings(transform) },
                onChooseDownloadFolder = { controller.chooseDownloadFolder() },
                onCheckUpdates = { controller.checkForUpdates(manual = true) },
                onDownloadUpdate = { asset, url, tag -> controller.downloadWindowsUpdate(asset, url, tag) },
                onInstallUpdate = { controller.installDownloadedUpdate() },
                onOpenUpdateDialog = { controller.openUpdateDialog() },
              )
          }

          // Active Detail / Theater / Vertical Drama Overlay
          when (val ov = overlay) {
            ActiveDesktopOverlay.None -> Unit

            is ActiveDesktopOverlay.Detail ->
              MovieDetailScreen(
                movieId = ov.movieId,
                catalogRepository = controller.catalogRepository,
                downloadManager = controller.downloadManager,
                settings = settings,
                watchlistEntry = watchlist.firstOrNull { it.movie.id == ov.movieId },
                watchlistIds = watchlistIds,
                onBack = { controller.closeOverlay() },
                onPlayMovie = { m, s, e, forceTheater ->
                  controller.openPlaybackForMovie(m, s, e, forceStandardTheater = forceTheater)
                },
                onOpenRecommendation = { rec -> controller.openMovieDetail(rec) },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
                onSaveReview = { m, st, fav, r, n -> controller.saveWatchlistDetails(m, st, fav, r, n) },
                onNotify = { controller.postBanner(it) },
              )

            is ActiveDesktopOverlay.TheaterPlayer ->
              DesktopTheaterPlayerScreen(
                request = ov,
                catalogRepository = controller.catalogRepository,
                downloadManager = controller.downloadManager,
                storage = controller.storage,
                settings = settings,
                isFullscreen = isFullscreen,
                onToggleFullscreen = { controller.toggleFullscreen() },
                onBack = { controller.closeOverlay() },
                onNotify = { controller.postBanner(it) },
              )

            is ActiveDesktopOverlay.VerticalDramaPlayer ->
              DesktopVerticalDramaPlayerScreen(
                request = ov,
                catalogRepository = controller.catalogRepository,
                storage = controller.storage,
                settings = settings,
                isInWatchlist = ov.movieId in watchlistIds,
                isFullscreen = isFullscreen,
                onToggleFullscreen = { controller.toggleFullscreen() },
                onToggleWatchlist = { controller.toggleWatchlist(it) },
                onSwitchToStandardPlayer = { id, s, e, posMs ->
                  controller.openStandardTheaterById(id, s, e, posMs)
                },
                onBack = { controller.closeOverlay() },
                onNotify = { controller.postBanner(it) },
              )
          }
        }
      }
    }

    // Toast / Banner Notification Overlay
    AnimatedVisibility(
      visible = bannerMessage != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
    ) {
      bannerMessage?.let { msg ->
        Surface(
          color = DecanDesktopColors.SurfaceGlassStrong,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.Crimson),
          shadowElevation = 8.dp,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = DecanDesktopColors.CrimsonBright)
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = DecanDesktopColors.Mist)
            IconButton(onClick = { controller.dismissBanner() }) {
              Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = DecanDesktopColors.Slate)
            }
          }
        }
      }
    }

    if (updateState.showUpdateDialog && updateState.latestRelease != null) {
      UpdateAvailableDialog(
        updateState = updateState,
        preferPortable = settings.preferPortableUpdates,
        onDismiss = { controller.dismissUpdateDialog() },
        onDownload = { asset, url, tag -> controller.downloadWindowsUpdate(asset, url, tag) },
        onInstall = { controller.installDownloadedUpdate() },
      )
    }
  }
}
