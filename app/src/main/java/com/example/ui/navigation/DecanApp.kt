package com.example.ui.navigation

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.NotificationDeepLink
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.download.StreamDownloadManager
import com.example.data.firebase.MovieLibraryRepository
import com.example.data.model.DramaExperienceDetector
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.example.data.remote.MovieCatalogRepository
import com.example.data.settings.AppSettings
import com.example.data.settings.AppSettingsRepository
import com.example.data.settings.LocalLibraryStore
import com.example.data.settings.PlaybackProgressStore
import com.example.notifications.DecanNotificationManager
import com.example.ui.components.NotificationCenterSheet
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MediaPlayerScreen
import com.example.ui.screens.MovieDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VerticalDramaPlayerScreen
import com.example.ui.viewmodel.DiscoverViewModel
import com.example.ui.viewmodel.LibraryFilter
import com.example.ui.viewmodel.LibraryUiState
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.UpdateViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class TopTab(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  DISCOVER("discover", R.string.nav_discover, Icons.Filled.LocalMovies, Icons.Outlined.LocalMovies),
  SEARCH("search", R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
  DOWNLOADS("downloads", R.string.nav_downloads, Icons.Filled.DownloadForOffline, Icons.Outlined.DownloadForOffline),
  LIBRARY("library", R.string.nav_library, Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
  SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

private const val ROUTE_DETAIL = "detail/{movieId}"
private const val ROUTE_PLAYER =
  "player?movieId={movieId}&se={se}&ep={ep}&pos={pos}&localUri={localUri}&localTitle={localTitle}"
private const val ROUTE_DRAMA_PLAYER =
  "drama_player?movieId={movieId}&se={se}&ep={ep}&pos={pos}"

private fun buildDetailRoute(movieId: String): String = "detail/${Uri.encode(movieId)}"

private fun buildPlayerRoute(
  movieId: String = "",
  season: Int = 0,
  episode: Int = 0,
  positionMs: Long = 0L,
  localUri: String = "",
  localTitle: String = "",
): String {
  return "player?movieId=${Uri.encode(movieId)}&se=$season&ep=$episode&pos=$positionMs&localUri=${Uri.encode(localUri)}&localTitle=${Uri.encode(localTitle)}"
}

private fun buildDramaPlayerRoute(
  movieId: String,
  season: Int = 1,
  episode: Int = 1,
  positionMs: Long = 0L,
): String {
  return "drama_player?movieId=${Uri.encode(movieId)}&se=${season.coerceAtLeast(1)}&ep=${episode.coerceAtLeast(1)}&pos=$positionMs"
}

@Composable
fun DecanApp(
  settingsRepository: AppSettingsRepository,
  pendingDeepLink: NotificationDeepLink? = null,
  onConsumedDeepLink: () -> Unit = {},
) {
  val context = LocalContext.current
  val isFirebaseReady = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
  val localLibraryStore = remember(context) { LocalLibraryStore(context) }
  val appSettings by settingsRepository.settings.collectAsStateWithLifecycle()

  if (!isFirebaseReady) {
    GuestDecanApp(
      isFirebaseConfigured = false,
      localLibraryStore = localLibraryStore,
      settingsRepository = settingsRepository,
      pendingDeepLink = pendingDeepLink,
      onConsumedDeepLink = onConsumedDeepLink,
      onAuthSuccess = {},
    )
    return
  }

  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  val user = currentUser
  when {
    user != null -> {
      key(user.uid) {
        AuthenticatedDecanApp(
          user = user,
          localLibraryStore = localLibraryStore,
          settingsRepository = settingsRepository,
          pendingDeepLink = pendingDeepLink,
          onConsumedDeepLink = onConsumedDeepLink,
        )
      }
    }
    appSettings.skippedSignIn -> {
      GuestDecanApp(
        isFirebaseConfigured = true,
        localLibraryStore = localLibraryStore,
        settingsRepository = settingsRepository,
        pendingDeepLink = pendingDeepLink,
        onConsumedDeepLink = onConsumedDeepLink,
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
      )
    }
    else -> {
      AuthScreen(
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        onSkipSignIn = { settingsRepository.setSkippedSignIn(true) },
      )
    }
  }
}

@Composable
private fun GuestDecanApp(
  isFirebaseConfigured: Boolean,
  localLibraryStore: LocalLibraryStore,
  settingsRepository: AppSettingsRepository,
  pendingDeepLink: NotificationDeepLink?,
  onConsumedDeepLink: () -> Unit,
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val catalogRepository = remember(context) {
    MovieCatalogRepository(
      snapshotPrefs =
        context.applicationContext.getSharedPreferences("decan_catalog_cache", Context.MODE_PRIVATE)
    )
  }
  val discoverViewModel: DiscoverViewModel =
    viewModel(
      key = "discover_guest",
      factory = viewModelFactory { initializer { DiscoverViewModel(catalogRepository) } },
    )
  val searchViewModel: SearchViewModel =
    viewModel(
      key = "search_guest",
      factory = viewModelFactory { initializer { SearchViewModel(catalogRepository) } },
    )
  val updateViewModel: UpdateViewModel =
    viewModel(
      key = "updates_guest",
      factory = viewModelFactory {
        initializer { UpdateViewModel(settingsRepository = settingsRepository) }
      },
    )

  val localEntries by localLibraryStore.entries.collectAsStateWithLifecycle()
  var selectedLibraryFilter by remember { mutableStateOf(LibraryFilter.ALL) }
  val savedLibraryMap =
    remember(localEntries) { localEntries.associateBy { it.movieId.ifBlank { it.id } } }

  val snackbarHostState = remember { SnackbarHostState() }
  fun notify(msg: String) {
    scope.launch { snackbarHostState.showSnackbar(msg) }
  }

  DecanMainShell(
    isFirebaseConfigured = isFirebaseConfigured,
    isSignedIn = false,
    userProfile = null,
    userDisplayName = "Guest Cinema",
    userEmail = null,
    userPhotoUrl = null,
    catalogRepository = catalogRepository,
    settingsRepository = settingsRepository,
    discoverViewModel = discoverViewModel,
    searchViewModel = searchViewModel,
    updateViewModel = updateViewModel,
    libraryState = LibraryUiState.Success(localEntries),
    savedLibraryMap = savedLibraryMap,
    selectedLibraryFilter = selectedLibraryFilter,
    isSavingLibrary = false,
    pendingDeepLink = pendingDeepLink,
    onConsumedDeepLink = onConsumedDeepLink,
    snackbarHostState = snackbarHostState,
    onNotify = ::notify,
    onSelectLibraryFilter = { selectedLibraryFilter = it },
    onToggleQuickWatchlist = { movie ->
      val existing = savedLibraryMap[movie.id] ?: savedLibraryMap[Movie.sanitizeId(movie.id)]
      notify(localLibraryStore.toggleQuickWatchlist(movie, existing))
    },
    onToggleFavorite = { entry ->
      notify(localLibraryStore.toggleFavorite(entry.toMovie(), entry))
    },
    onSaveEntryDetails = { movie, status, fav, rating, note ->
      notify(localLibraryStore.saveEntryDetails(movie, status, fav, rating, note))
    },
    onRemoveLibraryMovie = { movieId, title ->
      notify(localLibraryStore.removeMovie(movieId, title))
    },
    onSignOut = null,
    onAuthSuccess = onAuthSuccess,
  )
}

@Composable
private fun AuthenticatedDecanApp(
  user: FirebaseUser,
  localLibraryStore: LocalLibraryStore,
  settingsRepository: AppSettingsRepository,
  pendingDeepLink: NotificationDeepLink?,
  onConsumedDeepLink: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val catalogRepository = remember(context) {
    MovieCatalogRepository(
      snapshotPrefs =
        context.applicationContext.getSharedPreferences("decan_catalog_cache", Context.MODE_PRIVATE)
    )
  }

  val discoverViewModel: DiscoverViewModel =
    viewModel(
      key = "discover_${user.uid}",
      factory = viewModelFactory { initializer { DiscoverViewModel(catalogRepository) } },
    )
  val searchViewModel: SearchViewModel =
    viewModel(
      key = "search_${user.uid}",
      factory = viewModelFactory { initializer { SearchViewModel(catalogRepository) } },
    )
  val updateViewModel: UpdateViewModel =
    viewModel(
      key = "updates_${user.uid}",
      factory = viewModelFactory {
        initializer { UpdateViewModel(settingsRepository = settingsRepository) }
      },
    )
  val libraryViewModel: LibraryViewModel =
    viewModel(
      key = user.uid,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            LibraryViewModel(MovieLibraryRepository(db), user.uid)
          }
        },
    )

  LaunchedEffect(user.uid) {
    libraryViewModel.syncUserProfile(user)
    val guestEntries = localLibraryStore.entries.value
    if (guestEntries.isNotEmpty()) {
      libraryViewModel.importGuestEntries(guestEntries) { localLibraryStore.clearAll() }
    }
  }

  val libraryState by libraryViewModel.libraryState.collectAsStateWithLifecycle()
  val userProfile by libraryViewModel.userProfile.collectAsStateWithLifecycle()
  val selectedLibraryFilter by libraryViewModel.selectedFilter.collectAsStateWithLifecycle()
  val actionMessage by libraryViewModel.actionMessage.collectAsStateWithLifecycle()
  val isSaving by libraryViewModel.isSaving.collectAsStateWithLifecycle()

  val savedLibraryMap =
    remember(libraryState) {
      val list = (libraryState as? LibraryUiState.Success)?.entries.orEmpty()
      list.associateBy { it.movieId.ifBlank { it.id } }
    }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(actionMessage) {
    val msg = actionMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      libraryViewModel.clearMessage()
    }
  }

  DecanMainShell(
    isFirebaseConfigured = true,
    isSignedIn = true,
    userProfile = userProfile,
    userDisplayName = user.displayName ?: "Cinema Viewer",
    userEmail = user.email.orEmpty(),
    userPhotoUrl = user.photoUrl?.toString(),
    catalogRepository = catalogRepository,
    settingsRepository = settingsRepository,
    discoverViewModel = discoverViewModel,
    searchViewModel = searchViewModel,
    updateViewModel = updateViewModel,
    libraryState = libraryState,
    savedLibraryMap = savedLibraryMap,
    selectedLibraryFilter = selectedLibraryFilter,
    isSavingLibrary = isSaving,
    pendingDeepLink = pendingDeepLink,
    onConsumedDeepLink = onConsumedDeepLink,
    snackbarHostState = snackbarHostState,
    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
    onSelectLibraryFilter = libraryViewModel::selectFilter,
    onToggleQuickWatchlist = { movie ->
      libraryViewModel.toggleQuickWatchlist(movie, savedLibraryMap[movie.id])
    },
    onToggleFavorite = { entry ->
      libraryViewModel.toggleFavorite(entry.toMovie(), entry)
    },
    onSaveEntryDetails = libraryViewModel::saveEntryDetails,
    onRemoveLibraryMovie = libraryViewModel::removeMovie,
    onSignOut = {
      settingsRepository.setSkippedSignIn(true)
      signOut(
        credentialManager = credentialManager,
        scope = scope,
      )
    },
    onAuthSuccess = {},
  )
}

@Composable
private fun DecanMainShell(
  isFirebaseConfigured: Boolean,
  isSignedIn: Boolean,
  userProfile: UserProfile?,
  userDisplayName: String?,
  userEmail: String?,
  userPhotoUrl: String?,
  catalogRepository: MovieCatalogRepository,
  settingsRepository: AppSettingsRepository,
  discoverViewModel: DiscoverViewModel,
  searchViewModel: SearchViewModel,
  updateViewModel: UpdateViewModel,
  libraryState: LibraryUiState,
  savedLibraryMap: Map<String, SavedMovieEntry>,
  selectedLibraryFilter: LibraryFilter,
  isSavingLibrary: Boolean,
  pendingDeepLink: NotificationDeepLink?,
  onConsumedDeepLink: () -> Unit,
  snackbarHostState: SnackbarHostState,
  onNotify: (String) -> Unit,
  onSelectLibraryFilter: (LibraryFilter) -> Unit,
  onToggleQuickWatchlist: (Movie) -> Unit,
  onToggleFavorite: (SavedMovieEntry) -> Unit,
  onSaveEntryDetails: (Movie, WatchStatus, Boolean, Int, String) -> Unit,
  onRemoveLibraryMovie: (String, String) -> Unit,
  onSignOut: (() -> Unit)?,
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val progressStore = remember(context) { PlaybackProgressStore(context) }

  val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
  val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
  val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
  val appSettings: AppSettings by settingsRepository.settings.collectAsStateWithLifecycle()
  val continueWatching by progressStore.items.collectAsStateWithLifecycle()
  val notifications by DecanNotificationManager.notifications.collectAsStateWithLifecycle()
  val unreadNotifications = remember(notifications) { notifications.count { !it.isRead } }

  var showNotificationSheet by remember { mutableStateOf(false) }

  val currentSettingsState by rememberUpdatedState(appSettings)

  // Listen for Android DownloadManager completion broadcasts for instant download alerts
  DisposableEffect(context) {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
          if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
          val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
          if (downloadId <= 0L) return
          val task = StreamDownloadManager.queryDownloadById(context, downloadId) ?: return
          if (task.isCompleted) {
            DecanNotificationManager.notifyDownloadCompleted(
              context = context,
              downloadTitle = task.title,
              systemEnabled = currentSettingsState.enableDownloadNotifications,
            )
          }
        }
      }
    ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
      ContextCompat.RECEIVER_EXPORTED,
    )
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
  }

  LaunchedEffect(Unit) {
    DecanNotificationManager.ensureInitialized(context)
    if (!updateState.checkedOnce) {
      updateViewModel.checkForUpdatesIfDue(appSettings.githubRepo)
    }
  }

  // Trigger a release notification once per newly detected app update tag
  LaunchedEffect(updateState.latestRelease, appSettings.enableReleaseNotifications) {
    val latest = updateState.latestRelease
    if (
      latest != null &&
        latest.isNewerThanCurrent &&
        latest.tagName != appSettings.lastNotifiedReleaseTag
    ) {
      settingsRepository.markReleaseTagNotified(latest.tagName)
      val posterHint =
        discoverState.spotlight.firstOrNull()?.posterUrl
          ?: discoverState.rankings.firstOrNull()?.posterUrl
      DecanNotificationManager.notifyReleaseAvailable(
        context = context,
        tagName = latest.tagName,
        releaseTitle = latest.title,
        releaseUrl = latest.htmlUrl,
        systemEnabled = appSettings.enableReleaseNotifications,
        imageUrl = posterHint,
      )
    }
  }

  // Trigger one rich cinema spotlight notification per day when live feed loads
  LaunchedEffect(discoverState.spotlight, appSettings.enableTrendingNotifications) {
    val spotlightMovie =
      discoverState.rankings.firstOrNull { it.posterUrl.isNotBlank() }
        ?: discoverState.spotlight.firstOrNull { it.posterUrl.isNotBlank() }
        ?: return@LaunchedEffect
    val prefs =
      context.applicationContext.getSharedPreferences("decan_promo_prefs", Context.MODE_PRIVATE)
    val todayBucket = System.currentTimeMillis() / (12L * 3600L * 1000L)
    val promoKey = "${todayBucket}_${spotlightMovie.id}"
    if (prefs.getString("last_promo_key", null) != promoKey) {
      prefs.edit().putString("last_promo_key", promoKey).apply()
      DecanNotificationManager.notifyTrendingPick(
        context = context,
        movieTitle = spotlightMovie.title,
        movieId = spotlightMovie.id,
        subtitle =
          listOfNotNull(
            spotlightMovie.genre.takeIf { it.isNotBlank() },
            spotlightMovie.releaseYear.takeIf { it.isNotBlank() },
            "Tap to watch in HD Theater Player",
          ).joinToString(" • "),
        systemEnabled = appSettings.enableTrendingNotifications,
        imageUrl = spotlightMovie.posterUrl,
      )
    }
  }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route
  val showTopLevelNav = TopTab.entries.any { it.route == currentRoute }

  fun navigateTopTab(tab: TopTab) {
    navController.navigate(tab.route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  // Handle Notification Deep-Links
  LaunchedEffect(pendingDeepLink) {
    val link = pendingDeepLink ?: return@LaunchedEffect
    if (!link.movieId.isNullOrBlank()) {
      navController.navigate(buildDetailRoute(link.movieId))
    } else if (!link.tab.isNullOrBlank()) {
      val tab = TopTab.entries.firstOrNull { it.route == link.tab }
      if (tab != null) navigateTopTab(tab)
    }
    onConsumedDeepLink()
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 840.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = {
        if (showTopLevelNav && !useNavRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets.navigationBars,
          ) {
            TopTab.entries.forEach { tab ->
              val selected = currentRoute == tab.route
              val label = stringResource(tab.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopTab(tab) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (tab == TopTab.SETTINGS && updateState.hasUpdate) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary)
                      }
                    }
                  ) {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = label,
                    )
                  }
                },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
                modifier = Modifier.testTag("nav_tab_${tab.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (showTopLevelNav && useNavRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
          ) {
            TopTab.entries.forEach { tab ->
              val selected = currentRoute == tab.route
              val label = stringResource(tab.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTopTab(tab) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (tab == TopTab.SETTINGS && updateState.hasUpdate) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary)
                      }
                    }
                  ) {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = label,
                    )
                  }
                },
                label = { Text(label) },
                modifier = Modifier.testTag("rail_tab_${tab.route}"),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(
            navController = navController,
            startDestination = TopTab.DISCOVER.route,
          ) {
            composable(TopTab.DISCOVER.route) {
              DiscoverScreen(
                uiState = discoverState,
                updateState = updateState,
                savedLibraryMap = savedLibraryMap,
                continueWatching = continueWatching,
                unreadNotifications = unreadNotifications,
                onRefresh = discoverViewModel::refreshCatalog,
                onSelectMediaFilter = discoverViewModel::selectMediaFilter,
                onSelectGenre = discoverViewModel::selectGenre,
                onSelectCategorySort = discoverViewModel::selectCategorySort,
                onSelectAnimeGenre = discoverViewModel::selectAnimeGenre,
                onSelectAnimeSort = discoverViewModel::selectAnimeSort,
                onOpenMovie = { movie -> navController.navigate(buildDetailRoute(movie.id)) },
                onPlayMovie = { movie ->
                  val isSeries =
                    movie.mediaType.equals("Series", ignoreCase = true) ||
                      movie.mediaType.equals("Anime", ignoreCase = true) ||
                      movie.mediaType.equals("Drama", ignoreCase = true)
                  if (DramaExperienceDetector.isEpisodicDrama(movie)) {
                    navController.navigate(
                      buildDramaPlayerRoute(movieId = movie.id, season = 1, episode = 1)
                    )
                  } else {
                    navController.navigate(
                      buildPlayerRoute(
                        movieId = movie.id,
                        season = if (isSeries) 1 else 0,
                        episode = if (isSeries) 1 else 0,
                      )
                    )
                  }
                },
                onResumeWatching = { cw ->
                  if (cw.localUri.isNotBlank()) {
                    navController.navigate(
                      buildPlayerRoute(
                        movieId = cw.movieId,
                        season = cw.season,
                        episode = cw.episode,
                        positionMs = cw.positionMs,
                        localUri = cw.localUri,
                        localTitle = cw.title,
                      )
                    )
                  } else {
                    val cachedMovie = catalogRepository.getCachedMovie(cw.movieId)
                    val isDrama =
                      DramaExperienceDetector.isEpisodicDrama(cachedMovie) ||
                        cw.mediaType.equals("Drama", ignoreCase = true)
                    if (isDrama && cw.season > 0 && cw.episode > 0) {
                      navController.navigate(
                        buildDramaPlayerRoute(
                          movieId = cw.movieId,
                          season = cw.season,
                          episode = cw.episode,
                          positionMs = cw.positionMs,
                        )
                      )
                    } else {
                      navController.navigate(
                        buildPlayerRoute(
                          movieId = cw.movieId,
                          season = cw.season,
                          episode = cw.episode,
                          positionMs = cw.positionMs,
                        )
                      )
                    }
                  }
                },
                onRemoveContinueWatching = progressStore::removeProgress,
                onToggleWatchlist = onToggleQuickWatchlist,
                onLoadMore = discoverViewModel::loadMoreForActiveFilter,
                onOpenDownloads = { navigateTopTab(TopTab.DOWNLOADS) },
                onOpenNotifications = { showNotificationSheet = true },
                onOpenSettings = { navigateTopTab(TopTab.SETTINGS) },
                onOpenSearch = { navigateTopTab(TopTab.SEARCH) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.SEARCH.route) {
              SearchScreen(
                uiState = searchState,
                savedLibraryMap = savedLibraryMap,
                onQueryChange = searchViewModel::onQueryChange,
                onSubmitSearch = searchViewModel::submitSearch,
                onSubmitKeyword = searchViewModel::submitKeyword,
                onSelectSuggestion = { sug ->
                  navController.navigate(buildDetailRoute("mbox_${sug.subjectId}"))
                },
                onSelectGenre = searchViewModel::selectGenre,
                onSelectSort = searchViewModel::selectSortOption,
                onOpenMovie = { movie -> navController.navigate(buildDetailRoute(movie.id)) },
                onToggleWatchlist = onToggleQuickWatchlist,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.DOWNLOADS.route) {
              DownloadsScreen(
                storageLocation = appSettings.storageLocation,
                progressStore = progressStore,
                onChangeStorageLocation = settingsRepository::setStorageLocation,
                onPlayLocalFile = { localUri, title ->
                  navController.navigate(buildPlayerRoute(localUri = localUri, localTitle = title))
                },
                onPlayDownloadedTask = { task, resumeMs ->
                  navController.navigate(
                    buildPlayerRoute(
                      movieId = task.movieId,
                      season = task.seasonNumber,
                      episode = task.episodeNumber,
                      positionMs = resumeMs,
                      localUri = task.localUri.orEmpty(),
                      localTitle = task.title,
                    )
                  )
                },
                onExploreMovies = { navigateTopTab(TopTab.DISCOVER) },
                onNotify = onNotify,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.LIBRARY.route) {
              LibraryScreen(
                libraryState = libraryState,
                userProfile = userProfile,
                userDisplayName = userDisplayName ?: "Cinema Viewer",
                userEmail = userEmail.orEmpty(),
                userPhotoUrl = userPhotoUrl,
                isGuestMode = !isSignedIn,
                isFirebaseConfigured = isFirebaseConfigured,
                selectedFilter = selectedLibraryFilter,
                onSelectFilter = onSelectLibraryFilter,
                onOpenMovie = { entry ->
                  navController.navigate(buildDetailRoute(entry.movieId.ifBlank { entry.id }))
                },
                onToggleFavorite = onToggleFavorite,
                onRemoveEntry = { entry ->
                  onRemoveLibraryMovie(entry.movieId.ifBlank { entry.id }, entry.title)
                },
                onExploreMovies = { navigateTopTab(TopTab.DISCOVER) },
                onSignOut = { onSignOut?.invoke() },
                onAuthSuccess = onAuthSuccess,
                onAuthError = onNotify,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.SETTINGS.route) {
              SettingsScreen(
                appSettings = appSettings,
                updateState = updateState,
                isFirebaseConfigured = isFirebaseConfigured,
                isSignedIn = isSignedIn,
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                userPhotoUrl = userPhotoUrl,
                onSignOut = onSignOut,
                onAuthSuccess = onAuthSuccess,
                onSetThemeMode = settingsRepository::setThemeMode,
                onSetPreferredQuality = settingsRepository::setPreferredQuality,
                onSetAutoPlayNext = settingsRepository::setAutoPlayNextEpisode,
                onSetAutoLoadSubtitles = settingsRepository::setAutoLoadSubtitles,
                onSetDefaultPlaybackSpeed = settingsRepository::setDefaultPlaybackSpeed,
                onSetAllowMeteredDownloads = settingsRepository::setAllowMeteredDownloads,
                onSetIncludeSubtitlesInBatch = settingsRepository::setIncludeSubtitlesInBatch,
                onSetStorageLocation = settingsRepository::setStorageLocation,
                onSetEnableDownloadNotifications = settingsRepository::setEnableDownloadNotifications,
                onSetEnableReleaseNotifications = settingsRepository::setEnableReleaseNotifications,
                onSetEnableTrendingNotifications = settingsRepository::setEnableTrendingNotifications,
                onSetAutoCheckUpdates = settingsRepository::setAutoCheckUpdates,
                onSendTestNotification = {
                  val topPick =
                    discoverState.rankings.firstOrNull { it.posterUrl.isNotBlank() }
                      ?: discoverState.rankings.firstOrNull()
                      ?: discoverState.spotlight.firstOrNull()
                  if (topPick != null) {
                    DecanNotificationManager.notifyTrendingPick(
                      context = context,
                      movieTitle = topPick.title,
                      movieId = topPick.id,
                      subtitle =
                        "${topPick.genre} • ${topPick.releaseYear} — Watch in HD Theater Player now.",
                      systemEnabled = appSettings.enableTrendingNotifications,
                      imageUrl = topPick.posterUrl,
                    )
                    onNotify("Sent alert for ${topPick.title}")
                  } else {
                    DecanNotificationManager.notifyTrendingPick(
                      context = context,
                      movieTitle = "Decan Cinema Spotlight",
                      movieId = "",
                      subtitle = "Notifications are active for downloads, updates, and top picks.",
                      systemEnabled = appSettings.enableTrendingNotifications,
                    )
                    onNotify("Sent test cinema alert")
                  }
                },
                onCheckUpdates = {
                  updateViewModel.checkForUpdates(appSettings.githubRepo, manualTrigger = true)
                },
                onDownloadInstallUpdate = { asset, fallbackUrl, tag ->
                  if (asset != null) {
                    updateViewModel.downloadAndInstallApk(
                      context = context,
                      apkUrl = asset.downloadUrl,
                      fallbackWebUrl = fallbackUrl,
                      versionTag = tag,
                      expectedSha256 = asset.sha256,
                      autoLaunchInstaller = true,
                      onNotify = onNotify,
                    )
                  }
                },
                onInstallDownloadedApk = { path ->
                  updateViewModel.launchApkInstaller(context, java.io.File(path), onNotify)
                },
                onNotify = onNotify,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(
              route = ROUTE_DETAIL,
              arguments = listOf(navArgument("movieId") { type = NavType.StringType }),
            ) { entry ->
              val movieId = Uri.decode(entry.arguments?.getString("movieId").orEmpty())
              MovieDetailScreen(
                movieId = movieId,
                savedEntry = savedLibraryMap[movieId] ?: savedLibraryMap[Movie.sanitizeId(movieId)],
                savedLibraryMap = savedLibraryMap,
                catalogRepository = catalogRepository,
                allowMeteredDownloads = appSettings.allowMeteredDownloads,
                includeSubtitlesInBatch = appSettings.includeSubtitlesInBatch,
                storageLocation = appSettings.storageLocation,
                onChangeStorageLocation = settingsRepository::setStorageLocation,
                notifyDownloads = appSettings.enableDownloadNotifications,
                isSaving = isSavingLibrary,
                onBack = { navController.popBackStack() },
                onSwitchSubject = { targetId ->
                  navController.navigate(buildDetailRoute(targetId)) {
                    popUpTo(buildDetailRoute(movieId)) { inclusive = true }
                  }
                },
                onOpenPlayer = { movie, se, ep ->
                  navController.navigate(
                    buildPlayerRoute(movieId = movie.id, season = se, episode = ep)
                  )
                },
                onOpenDramaPlayer = { movie, se, ep ->
                  navController.navigate(
                    buildDramaPlayerRoute(movieId = movie.id, season = se, episode = ep)
                  )
                },
                onOpenMovie = { rec -> navController.navigate(buildDetailRoute(rec.id)) },
                onToggleQuickWatchlist = onToggleQuickWatchlist,
                onNotify = onNotify,
                onSaveEntry = onSaveEntryDetails,
                onRemoveEntry = { id, title ->
                  onRemoveLibraryMovie(id, title)
                  navController.popBackStack()
                },
              )
            }

            composable(
              route = ROUTE_PLAYER,
              arguments =
                listOf(
                  navArgument("movieId") {
                    type = NavType.StringType
                    defaultValue = ""
                  },
                  navArgument("se") {
                    type = NavType.IntType
                    defaultValue = 0
                  },
                  navArgument("ep") {
                    type = NavType.IntType
                    defaultValue = 0
                  },
                  navArgument("pos") {
                    type = NavType.LongType
                    defaultValue = 0L
                  },
                  navArgument("localUri") {
                    type = NavType.StringType
                    defaultValue = ""
                  },
                  navArgument("localTitle") {
                    type = NavType.StringType
                    defaultValue = ""
                  },
                ),
            ) { entry ->
              val rawMovieId = entry.arguments?.getString("movieId").orEmpty()
              val se = entry.arguments?.getInt("se") ?: 0
              val ep = entry.arguments?.getInt("ep") ?: 0
              val pos = entry.arguments?.getLong("pos") ?: 0L
              val rawLocalUri = entry.arguments?.getString("localUri").orEmpty()
              val rawLocalTitle = entry.arguments?.getString("localTitle").orEmpty()

              val decodedMovieId = remember(rawMovieId) { Uri.decode(rawMovieId) }
              val decodedLocalUri =
                remember(rawLocalUri) { Uri.decode(rawLocalUri).takeIf { it.isNotBlank() } }
              val decodedLocalTitle =
                remember(rawLocalTitle) { Uri.decode(rawLocalTitle).takeIf { it.isNotBlank() } }

              MediaPlayerScreen(
                movieId = decodedMovieId,
                initialSeason = se,
                initialEpisode = ep,
                initialPositionMs = pos,
                localPlayUri = decodedLocalUri,
                localTitle = decodedLocalTitle,
                savedEntry =
                  savedLibraryMap[decodedMovieId] ?: savedLibraryMap[Movie.sanitizeId(decodedMovieId)],
                savedLibraryMap = savedLibraryMap,
                catalogRepository = catalogRepository,
                appSettings = appSettings,
                progressStore = progressStore,
                onBack = { navController.popBackStack() },
                onSwitchMovie = { targetId ->
                  navController.navigate(buildPlayerRoute(movieId = targetId)) {
                    popUpTo(ROUTE_PLAYER) { inclusive = true }
                  }
                },
                onToggleWatchlist = onToggleQuickWatchlist,
                onNotify = onNotify,
              )
            }

            composable(
              route = ROUTE_DRAMA_PLAYER,
              arguments =
                listOf(
                  navArgument("movieId") {
                    type = NavType.StringType
                    defaultValue = ""
                  },
                  navArgument("se") {
                    type = NavType.IntType
                    defaultValue = 1
                  },
                  navArgument("ep") {
                    type = NavType.IntType
                    defaultValue = 1
                  },
                  navArgument("pos") {
                    type = NavType.LongType
                    defaultValue = 0L
                  },
                ),
            ) { entry ->
              val rawMovieId = entry.arguments?.getString("movieId").orEmpty()
              val se = entry.arguments?.getInt("se") ?: 1
              val ep = entry.arguments?.getInt("ep") ?: 1
              val pos = entry.arguments?.getLong("pos") ?: 0L
              val decodedMovieId = remember(rawMovieId) { Uri.decode(rawMovieId) }

              VerticalDramaPlayerScreen(
                movieId = decodedMovieId,
                initialSeason = se,
                initialEpisode = ep,
                initialPositionMs = pos,
                savedEntry =
                  savedLibraryMap[decodedMovieId] ?: savedLibraryMap[Movie.sanitizeId(decodedMovieId)],
                catalogRepository = catalogRepository,
                appSettings = appSettings,
                progressStore = progressStore,
                onBack = { navController.popBackStack() },
                onOpenStandardTheater = { targetId, season, episode, positionMs ->
                  navController.navigate(
                    buildPlayerRoute(
                      movieId = targetId,
                      season = season,
                      episode = episode,
                      positionMs = positionMs,
                    )
                  ) {
                    popUpTo(ROUTE_DRAMA_PLAYER) { inclusive = true }
                  }
                },
                onToggleWatchlist = onToggleQuickWatchlist,
                onSaveFavorite = onSaveEntryDetails,
                onNotify = onNotify,
              )
            }
          }
        }
      }
    }
  }

  if (updateState.showUpdateDialog && updateState.hasUpdate) {
    UpdateAvailableDialog(
      updateState = updateState,
      onDownloadUpdate = { asset, release ->
        if (asset != null) {
          updateViewModel.downloadAndInstallApk(
            context = context,
            apkUrl = asset.downloadUrl,
            fallbackWebUrl = release.htmlUrl,
            versionTag = release.tagName,
            expectedSha256 = asset.sha256,
            autoLaunchInstaller = false,
            onNotify = onNotify,
          )
        } else {
          runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
          }.onFailure { onNotify("Could not open release page") }
        }
      },
      onInstallUpdate = { apkPath ->
        updateViewModel.launchApkInstaller(context, java.io.File(apkPath), onNotify)
      },
      onLater = { updateViewModel.dismissUpdatePrompt() },
      onNotify = onNotify,
    )
  }

  if (showNotificationSheet) {
    NotificationCenterSheet(
      notifications = notifications,
      onMarkAllRead = { DecanNotificationManager.markAllRead(context) },
      onClearAll = { DecanNotificationManager.clearAll(context) },
      onNotificationClick = { item ->
        DecanNotificationManager.markRead(context, item.id)
        showNotificationSheet = false
        when {
          !item.targetMovieId.isNullOrBlank() ->
            navController.navigate(buildDetailRoute(item.targetMovieId))
          !item.targetTab.isNullOrBlank() -> {
            val tab = TopTab.entries.firstOrNull { it.route == item.targetTab }
            if (tab != null) navigateTopTab(tab)
          }
          !item.externalUrl.isNullOrBlank() -> {
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.externalUrl)))
            }
          }
        }
      },
      onDismiss = { showNotificationSheet = false },
    )
  }
}
