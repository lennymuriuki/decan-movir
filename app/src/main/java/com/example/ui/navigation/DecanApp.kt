package com.example.ui.navigation

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
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.Search
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
import com.example.data.remote.MovieCatalogRepository
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MovieDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.viewmodel.DiscoverViewModel
import com.example.ui.viewmodel.LibraryUiState
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.google.firebase.Firebase
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
  LIBRARY("library", R.string.nav_library, Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
}

private const val ROUTE_DETAIL = "detail/{movieId}"

@Composable
fun DecanApp() {
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
      AuthenticatedDecanApp(user = user)
    }
  }
}

@Composable
private fun AuthenticatedDecanApp(user: FirebaseUser) {
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
  }

  val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
  val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
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

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route
  val showTopLevelNav = TopTab.entries.any { it.route == currentRoute }

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
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
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
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
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
                savedLibraryMap = savedLibraryMap,
                onRefresh = discoverViewModel::refreshCatalog,
                onSelectMediaFilter = discoverViewModel::selectMediaFilter,
                onSelectGenre = discoverViewModel::selectGenre,
                onOpenMovie = { movie -> navController.navigate("detail/${movie.id}") },
                onToggleWatchlist = { movie ->
                  libraryViewModel.toggleQuickWatchlist(movie, savedLibraryMap[movie.id])
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.SEARCH.route) {
              SearchScreen(
                uiState = searchState,
                savedLibraryMap = savedLibraryMap,
                onQueryChange = searchViewModel::onQueryChange,
                onSubmitSearch = searchViewModel::submitSearch,
                onSelectSuggestion = { sug ->
                  navController.navigate("detail/mbox_${sug.subjectId}")
                },
                onSelectGenre = searchViewModel::selectGenre,
                onSelectSort = searchViewModel::selectSortOption,
                onOpenMovie = { movie -> navController.navigate("detail/${movie.id}") },
                onToggleWatchlist = { movie ->
                  libraryViewModel.toggleQuickWatchlist(movie, savedLibraryMap[movie.id])
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
              )
            }

            composable(TopTab.LIBRARY.route) {
              LibraryScreen(
                libraryState = libraryState,
                userProfile = userProfile,
                userDisplayName = user.displayName ?: "Cinema Viewer",
                userEmail = user.email.orEmpty(),
                userPhotoUrl = user.photoUrl?.toString(),
                selectedFilter = selectedLibraryFilter,
                onSelectFilter = libraryViewModel::selectFilter,
                onOpenMovie = { entry ->
                  navController.navigate("detail/${entry.movieId.ifBlank { entry.id }}")
                },
                onToggleFavorite = { entry ->
                  libraryViewModel.toggleFavorite(entry.toMovie(), entry)
                },
                onRemoveEntry = { entry ->
                  libraryViewModel.removeMovie(entry.movieId.ifBlank { entry.id }, entry.title)
                },
                onExploreMovies = {
                  navController.navigate(TopTab.DISCOVER.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                  }
                },
                onSignOut = {
                  signOut(credentialManager = credentialManager, scope = scope)
                },
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
                isSaving = isSaving,
                onBack = { navController.popBackStack() },
                onSwitchSubject = { targetId ->
                  navController.navigate("detail/$targetId") {
                    popUpTo("detail/$movieId") { inclusive = true }
                  }
                },
                onNotify = { msg ->
                  scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onSaveEntry = { movie, status, fav, rating, note ->
                  libraryViewModel.saveEntryDetails(movie, status, fav, rating, note)
                },
                onRemoveEntry = { id, title ->
                  libraryViewModel.removeMovie(id, title)
                  navController.popBackStack()
                },
              )
            }
          }
        }
      }
    }
  }
}
