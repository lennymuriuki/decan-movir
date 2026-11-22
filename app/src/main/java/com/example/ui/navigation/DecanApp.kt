package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.firebase.MovieLibraryRepository
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.example.data.remote.MovieCatalogRepository
import com.example.data.settings.AppSettings
import com.example.data.settings.AppSettingsRepository
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MovieDetailScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
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
  "player?movieId={movieId}&se={se}&ep={ep}&localUri={localUri}&localTitle={localTitle}"

private fun buildPlayerRoute(
  movieId: String = "",
  season: Int = 0,
  episode: Int = 0,
  localUri: String = "",
  localTitle: String = "",
): String {
  return "player?movieId=${Uri.encode(movieId)}&se=$season&ep=$episode&localUri=${Uri.encode(localUri)}&localTitle=${Uri.encode(localTitle)}"
}

@Composable
fun DecanApp(settingsRepository: AppSettingsRepository) {
  val context = LocalContext.current
  val isFirebaseReady = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }

  if (!isFirebaseReady) {
    CatalogOnlyDecanApp(settingsRepository = settingsRepository)
    return
  }

  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
  } else {
    key(user.uid) {
      AuthenticatedDecanApp(user = user, settingsRepository = settingsRepository)
    }
  }
}

@Composable
private fun CatalogOnlyDecanApp(settingsRepository: AppSettingsRepository) {
  val scope = rememberCoroutineScope()
  val catalogRepository = remember { MovieCatalogRepository() }
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
      factory = viewModelFactory { initializer { UpdateViewModel() } },
    )
  val snackbarHostState = remember { SnackbarHostState() }

  DecanMainShell(
    isFirebaseConfigured = false,
    userProfile = null,
    userDisplayName = null,
    userEmail = null,
    userPhotoUrl = null,
    catalogRepository = catalogRepository,
    settingsRepository = settingsRepository,
    discoverViewModel = discoverViewModel,
    searchViewModel = searchViewModel,
    updateViewModel = updateViewModel,
    libraryState = LibraryUiState.Success(emptyList()),
    savedLibraryMap = emptyMap(),
    selectedLibraryFilter = LibraryFilter.ALL,
    isSavingLibrary = false,
    snackbarHostState = snackbarHostState,
    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
    onSelectLibraryFilter = {},
    onToggleQuickWatchlist = {
      scope.launch {
        snackbarHostState.showSnackbar("Cloud Watchlist requires GOOGLE_SERVICES_JSON in your build")
      }
    },
    onToggleFavorite = {},
    onSaveEntryDetails = { _, _, _, _, _ ->
      scope.launch {
        snackbarHostState.showSnackbar("Cloud Watchlist requires GOOGLE_SERVICES_JSON in your build")
      }
    },
    onRemoveLibraryMovie = { _, _ -> },
    onSignOut = null,
  )
}

@Composable
private fun AuthenticatedDecanApp(
  user: FirebaseUser,
  settingsRepository: AppSettingsRepository,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val catalogRepository = remember { MovieCatalogRepository() }

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
      factory = viewModelFactory { initializer { UpdateViewModel() } },
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

  LaunchedEffect(user.uid) { libraryViewModel.syncUserProfile(user) }

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
    onSignOut = { signOut(credentialManager = credentialManager, scope = scope) },
  )
}

@Composable
private fun DecanMainShell(
  isFirebaseConfigured: Boolean,
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
  snackbarHostState: SnackbarHostState,
  onNotify: (String) -> Unit,
  onSelectLibraryFilter: (LibraryFilter) -> Unit,
  onToggleQuickWatchlist: (Movie) -> Unit,
  onToggleFavorite: (SavedMovieEntry) -> Unit,
  onSaveEntryDetails: (Movie, WatchStatus, Boolean, Int, String) -> Unit,
  onRemoveLibraryMovie: (String, String) -> Unit,
  onSignOut: (() -> Unit)?,
) {
  val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
  val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
  val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
  val appSettings: AppSettings by settingsRepository.settings.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    if (!updateState.checkedOnce) {
      updateViewModel.checkForUpdates(appSettings.githubRepo)
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
                onRefresh = discoverViewModel::refreshCatalog,
                onSelectMediaFilter = discoverViewModel::selectMediaFilter,
                onSelectGenre = discoverViewModel::selectGenre,
                onOpenMovie = { movie -> navController.navigate("detail/${movie.id}") },
                onPlayMovie = { movie ->
                  navController.navigate(
                    buildPlayerRoute(
                      movieId = movie.id,
                      season = if (movie.mediaType.equals("Series", ignoreCase = true) || movie.mediaType.equals("Anime", ignoreCase = true)) 1 else 0,
                      episode = if (movie.mediaType.equals("Series", ignoreCase = true) || movie.mediaType.equals("Anime", ignoreCase = true)) 1 else 0,
                    )
                  )
                },
                onToggleWatchlist = onToggleQuickWatchlist,
                onLoadMore = discoverViewModel::loadMoreForActiveFilter,
                onOpenDownloads = { navigateTopTab(TopTab.DOWNLOADS) },
                onOpenSettings = { navigateTopTab(TopTab.SETTINGS) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.SEARCH.route) {
              SearchScreen(
                uiState = searchState,
                savedLibraryMap = savedLibraryMap,
                onQueryChange = searchViewModel::onQueryChange,
                onSubmitSearch = searchViewModel::submitSearch,
                onSelectSuggestion = { sug -> navController.navigate("detail/mbox_${sug.subjectId}") },
                onSelectGenre = searchViewModel::selectGenre,
                onSelectSort = searchViewModel::selectSortOption,
                onOpenMovie = { movie -> navController.navigate("detail/${movie.id}") },
                onToggleWatchlist = onToggleQuickWatchlist,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.DOWNLOADS.route) {
              DownloadsScreen(
                onPlayLocalFile = { localUri, title ->
                  navController.navigate(buildPlayerRoute(localUri = localUri, localTitle = title))
                },
                onExploreMovies = { navigateTopTab(TopTab.DISCOVER) },
                onNotify = onNotify,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.LIBRARY.route) {
              if (!isFirebaseConfigured) {
                Box(
                  modifier =
                    Modifier.fillMaxSize()
                      .windowInsetsPadding(WindowInsets.statusBars)
                      .padding(24.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Card(
                    colors =
                      CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                      ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                  ) {
                    Column(
                      modifier = Modifier.padding(24.dp),
                      verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      Text(
                        text = "Cloud Library Not Configured",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      Text(
                        text =
                          "This APK was compiled without app/google-services.json. Streaming, Theater Player, search, and batch downloads work normally. To enable Google Sign-In and Firestore watchlist sync, add GOOGLE_SERVICES_JSON to your GitHub Secrets and rebuild.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Spacer(modifier = Modifier.height(4.dp))
                      Button(onClick = { navigateTopTab(TopTab.DISCOVER) }) {
                        Text("Browse Movies & Series")
                      }
                    }
                  }
                }
              } else {
                LibraryScreen(
                  libraryState = libraryState,
                  userProfile = userProfile,
                  userDisplayName = userDisplayName ?: "Cinema Viewer",
                  userEmail = userEmail.orEmpty(),
                  userPhotoUrl = userPhotoUrl,
                  selectedFilter = selectedLibraryFilter,
                  onSelectFilter = onSelectLibraryFilter,
                  onOpenMovie = { entry ->
                    navController.navigate("detail/${entry.movieId.ifBlank { entry.id }}")
                  },
                  onToggleFavorite = onToggleFavorite,
                  onRemoveEntry = { entry ->
                    onRemoveLibraryMovie(entry.movieId.ifBlank { entry.id }, entry.title)
                  },
                  onExploreMovies = { navigateTopTab(TopTab.DISCOVER) },
                  onSignOut = { onSignOut?.invoke() },
                  modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                )
              }
            }

            composable(TopTab.SETTINGS.route) {
              SettingsScreen(
                appSettings = appSettings,
                updateState = updateState,
                isFirebaseConfigured = isFirebaseConfigured,
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                userPhotoUrl = userPhotoUrl,
                onSignOut = onSignOut,
                onSetThemeMode = settingsRepository::setThemeMode,
                onSetPreferredQuality = settingsRepository::setPreferredQuality,
                onSetAutoPlayNext = settingsRepository::setAutoPlayNextEpisode,
                onSetAutoLoadSubtitles = settingsRepository::setAutoLoadSubtitles,
                onSetDefaultPlaybackSpeed = settingsRepository::setDefaultPlaybackSpeed,
                onSetAllowMeteredDownloads = settingsRepository::setAllowMeteredDownloads,
                onSetIncludeSubtitlesInBatch = settingsRepository::setIncludeSubtitlesInBatch,
                onSaveGithubRepo = settingsRepository::setGithubRepo,
                onCheckUpdates = { updateViewModel.checkForUpdates(appSettings.githubRepo) },
                onNotify = onNotify,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(
              route = ROUTE_DETAIL,
              arguments = listOf(navArgument("movieId") { type = NavType.StringType }),
            ) { entry ->
              val movieId = entry.arguments?.getString("movieId").orEmpty()
              MovieDetailScreen(
                movieId = movieId,
                savedEntry = savedLibraryMap[movieId],
                catalogRepository = catalogRepository,
                allowMeteredDownloads = appSettings.allowMeteredDownloads,
                includeSubtitlesInBatch = appSettings.includeSubtitlesInBatch,
                isSaving = isSavingLibrary,
                onBack = { navController.popBackStack() },
                onSwitchSubject = { targetId ->
                  navController.navigate("detail/$targetId") {
                    popUpTo("detail/$movieId") { inclusive = true }
                  }
                },
                onOpenPlayer = { movie, se, ep ->
                  navController.navigate(
                    buildPlayerRoute(movieId = movie.id, season = se, episode = ep)
                  )
                },
                onOpenMovie = { rec -> navController.navigate("detail/${rec.id}") },
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
              val rawLocalUri = entry.arguments?.getString("localUri").orEmpty()
              val rawLocalTitle = entry.arguments?.getString("localTitle").orEmpty()

              val decodedMovieId = remember(rawMovieId) { Uri.decode(rawMovieId) }
              val decodedLocalUri =
                remember(rawLocalUri) { Uri.decode(rawLocalUri).takeIf { it.isNotBlank() } }
              val decodedLocalTitle =
                remember(rawLocalTitle) { Uri.decode(rawLocalTitle).takeIf { it.isNotBlank() } }

              PlayerScreen(
                movieId = decodedMovieId,
                initialSeason = se,
                initialEpisode = ep,
                localPlayUri = decodedLocalUri,
                localTitle = decodedLocalTitle,
                savedEntry = savedLibraryMap[decodedMovieId],
                catalogRepository = catalogRepository,
                appSettings = appSettings,
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
          }
        }
      }
    }
  }
}
