package com.decanmovie.desktop.viewmodel

import androidx.compose.runtime.Immutable
import com.decanmovie.desktop.data.download.DesktopDownloadManager
import com.decanmovie.desktop.data.remote.DesktopGitHubReleaseInfo
import com.decanmovie.desktop.data.remote.DesktopGitHubUpdateRepository
import com.decanmovie.desktop.data.remote.DesktopHomeCatalogResult
import com.decanmovie.desktop.data.remote.DesktopMovieCatalogRepository
import com.decanmovie.desktop.data.remote.WindowsReleaseAsset
import com.decanmovie.desktop.data.store.DesktopAppStorage
import com.decanmovie.desktop.model.AnimeSortMode
import com.decanmovie.desktop.model.CategorySortMode
import com.decanmovie.desktop.model.DesktopDestination
import com.decanmovie.desktop.model.DramaExperienceDetector
import com.decanmovie.desktop.model.LocalMediaItem
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.WatchStatus
import com.decanmovie.desktop.platform.DesktopTrayAndNotifier
import com.decanmovie.desktop.platform.WindowsAppPaths
import com.decanmovie.desktop.platform.WindowsSystemHelper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ActiveDesktopOverlay {
  data object None : ActiveDesktopOverlay

  data class Detail(val movieId: String) : ActiveDesktopOverlay

  data class TheaterPlayer(
    val movieId: String,
    val season: Int = 0,
    val episode: Int = 0,
    val startPositionMs: Long = 0L,
    val localVideoPath: String? = null,
    val localSubtitlePath: String? = null,
    val localTitle: String? = null,
  ) : ActiveDesktopOverlay

  data class VerticalDramaPlayer(
    val movieId: String,
    val season: Int = 1,
    val episode: Int = 1,
    val startPositionMs: Long = 0L,
  ) : ActiveDesktopOverlay
}

@Immutable
data class CatalogSectionState(
  val items: List<Movie> = emptyList(),
  val isLoading: Boolean = false,
  val isLoadingMore: Boolean = false,
  val page: Int = 1,
  val selectedGenre: String = "All",
  val sortMode: CategorySortMode = CategorySortMode.FEATURED,
  val animeSortMode: AnimeSortMode = AnimeSortMode.TOP_RANKED,
)

@Immutable
data class DesktopSearchState(
  val query: String = "",
  val results: List<Movie> = emptyList(),
  val suggestions: List<String> = emptyList(),
  val mediaFilter: String = "All",
  val isSearching: Boolean = false,
)

@Immutable
data class DesktopUpdateState(
  val currentVersion: String,
  val isChecking: Boolean = false,
  val checkedOnce: Boolean = false,
  val latestRelease: DesktopGitHubReleaseInfo? = null,
  val errorMessage: String? = null,
  val isDownloading: Boolean = false,
  val downloadProgress: Float = 0f,
  val downloadedFilePath: String? = null,
  val downloadedTag: String? = null,
  val isChecksumVerified: Boolean = false,
  val downloadError: String? = null,
  val showUpdateDialog: Boolean = false,
) {
  val hasUpdate: Boolean
    get() = latestRelease?.isNewerThanCurrent == true
}

class DesktopAppController(
  val appVersion: String = resolveDesktopAppVersion(),
  val storage: DesktopAppStorage = DesktopAppStorage(),
  val catalogRepository: DesktopMovieCatalogRepository = DesktopMovieCatalogRepository(storage = storage),
  val updateRepository: DesktopGitHubUpdateRepository = DesktopGitHubUpdateRepository(),
  private val notifier: DesktopTrayAndNotifier? = null,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var searchDebounceJob: Job? = null

  val downloadManager: DesktopDownloadManager =
    DesktopDownloadManager(
      onDownloadCompleted = { done ->
        val settings = storage.currentSettings()
        notifier?.showDesktopNotification(
          title = "Download Complete",
          message = "${done.title} (${done.displayLabel}) saved to ${done.folderPath}",
          enabled = settings.enableDesktopNotifications,
        )
        postBanner("${done.title} download finished!")
      }
    )

  private val _destination = MutableStateFlow(DesktopDestination.HOME)
  val destination: StateFlow<DesktopDestination> = _destination.asStateFlow()

  private val _overlay = MutableStateFlow<ActiveDesktopOverlay>(ActiveDesktopOverlay.None)
  val overlay: StateFlow<ActiveDesktopOverlay> = _overlay.asStateFlow()

  private val _homeFeed = MutableStateFlow<DesktopHomeCatalogResult?>(catalogRepository.getCachedHomeSnapshot())
  val homeFeed: StateFlow<DesktopHomeCatalogResult?> = _homeFeed.asStateFlow()

  private val _isHomeLoading = MutableStateFlow(_homeFeed.value == null)
  val isHomeLoading: StateFlow<Boolean> = _isHomeLoading.asStateFlow()

  private val _moviesState = MutableStateFlow(CatalogSectionState())
  val moviesState: StateFlow<CatalogSectionState> = _moviesState.asStateFlow()

  private val _seriesState = MutableStateFlow(CatalogSectionState())
  val seriesState: StateFlow<CatalogSectionState> = _seriesState.asStateFlow()

  private val _animeState = MutableStateFlow(CatalogSectionState())
  val animeState: StateFlow<CatalogSectionState> = _animeState.asStateFlow()

  private val _dramasState = MutableStateFlow(CatalogSectionState())
  val dramasState: StateFlow<CatalogSectionState> = _dramasState.asStateFlow()

  private val _searchState = MutableStateFlow(DesktopSearchState())
  val searchState: StateFlow<DesktopSearchState> = _searchState.asStateFlow()

  private val _updateState = MutableStateFlow(DesktopUpdateState(currentVersion = appVersion))
  val updateState: StateFlow<DesktopUpdateState> = _updateState.asStateFlow()

  private val _isFullscreen = MutableStateFlow(false)
  val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

  init {
    hydrateCachedRelease()
    downloadManager.refreshLocalLibrary(storage.currentSettings().downloadDirectory)
    refreshHome(force = false)
    checkForUpdatesIfDue()
  }

  private fun hydrateCachedRelease() {
    val s = storage.currentSettings()
    val cachedJson = s.cachedReleaseJson
    if (cachedJson.isBlank()) return
    val parsed = DesktopGitHubUpdateRepository.parseReleaseJson(cachedJson, appVersion) ?: return
    val shouldPrompt = parsed.isNewerThanCurrent && s.dismissedReleaseTag != parsed.tagName
    _updateState.update {
      it.copy(
        latestRelease = parsed,
        checkedOnce = true,
        showUpdateDialog = shouldPrompt,
      )
    }
  }

  fun navigateTo(dest: DesktopDestination) {
    _destination.value = dest
    _overlay.value = ActiveDesktopOverlay.None
    _isFullscreen.value = false
    when (dest) {
      DesktopDestination.MOVIES -> if (_moviesState.value.items.isEmpty()) loadMovies(reset = true)
      DesktopDestination.SERIES -> if (_seriesState.value.items.isEmpty()) loadSeries(reset = true)
      DesktopDestination.ANIME -> if (_animeState.value.items.isEmpty()) loadAnime(reset = true)
      DesktopDestination.DRAMAS -> if (_dramasState.value.items.isEmpty()) loadDramas(reset = true)
      DesktopDestination.DOWNLOADS ->
        downloadManager.refreshLocalLibrary(storage.currentSettings().downloadDirectory)
      else -> Unit
    }
  }

  fun openMovieDetail(movie: Movie) {
    catalogRepository.cacheMovie(movie)
    _isFullscreen.value = false
    _overlay.value = ActiveDesktopOverlay.Detail(movie.id)
  }

  fun openMovieId(movieId: String) {
    _isFullscreen.value = false
    _overlay.value = ActiveDesktopOverlay.Detail(movieId)
  }

  /**
   * Opens either the TikTok-style Vertical Drama Player (STRICTLY for episodic Dramas)
   * or the Standard Desktop Theater Player (Movies, non-drama Series, Anime).
   */
  fun openPlaybackForMovie(
    movie: Movie,
    season: Int = 0,
    episode: Int = 0,
    startPositionMs: Long = 0L,
    forceStandardTheater: Boolean = false,
  ) {
    catalogRepository.cacheMovie(movie)
    val resumeMs =
      if (startPositionMs > 0L) startPositionMs
      else storage.getEpisodeProgress(movie.id, season, episode)

    val isDrama = !forceStandardTheater && DramaExperienceDetector.isEpisodicDrama(movie)
    _overlay.value =
      if (isDrama) {
        ActiveDesktopOverlay.VerticalDramaPlayer(
          movieId = movie.id,
          season = season.coerceAtLeast(1),
          episode = episode.coerceAtLeast(1),
          startPositionMs = resumeMs,
        )
      } else {
        ActiveDesktopOverlay.TheaterPlayer(
          movieId = movie.id,
          season = season,
          episode = episode,
          startPositionMs = resumeMs,
        )
      }
  }

  fun openStandardTheaterById(
    movieId: String,
    season: Int = 0,
    episode: Int = 0,
    startPositionMs: Long = 0L,
  ) {
    _overlay.value =
      ActiveDesktopOverlay.TheaterPlayer(
        movieId = movieId,
        season = season,
        episode = episode,
        startPositionMs = startPositionMs,
      )
  }

  fun openVerticalDramaById(
    movieId: String,
    season: Int = 1,
    episode: Int = 1,
    startPositionMs: Long = 0L,
  ) {
    _overlay.value =
      ActiveDesktopOverlay.VerticalDramaPlayer(
        movieId = movieId,
        season = season.coerceAtLeast(1),
        episode = episode.coerceAtLeast(1),
        startPositionMs = startPositionMs,
      )
  }

  fun playLocalMedia(item: LocalMediaItem) {
    val syntheticId = "local_${item.filePath.hashCode()}"
    _overlay.value =
      ActiveDesktopOverlay.TheaterPlayer(
        movieId = syntheticId,
        localVideoPath = item.filePath,
        localSubtitlePath = item.subtitlePath,
        localTitle = "${item.titleFolder} — ${item.name}",
      )
  }

  fun pickAndPlayLocalFile() {
    scope.launch {
      val chosen = WindowsSystemHelper.pickLocalVideoFile(storage.currentSettings().downloadDirectory)
        ?: return@launch
      val siblingSub = DesktopDownloadManager.findSiblingSubtitle(chosen)
      _overlay.value =
        ActiveDesktopOverlay.TheaterPlayer(
          movieId = "local_${chosen.absolutePath.hashCode()}",
          localVideoPath = chosen.absolutePath,
          localSubtitlePath = siblingSub?.absolutePath,
          localTitle = chosen.nameWithoutExtension,
        )
    }
  }

  fun closeOverlay() {
    _isFullscreen.value = false
    _overlay.value = ActiveDesktopOverlay.None
  }

  fun setFullscreen(fullscreen: Boolean) {
    _isFullscreen.value = fullscreen
  }

  fun toggleFullscreen() {
    _isFullscreen.value = !_isFullscreen.value
  }

  fun refreshHome(force: Boolean = true) {
    scope.launch {
      if (_homeFeed.value == null || force) _isHomeLoading.value = true
      val result = catalogRepository.loadHomeCatalog(forceRefresh = force)
      _homeFeed.value = result
      _isHomeLoading.value = false
    }
  }

  fun setMoviesFilter(genre: String? = null, sortMode: CategorySortMode? = null) {
    _moviesState.update {
      it.copy(
        selectedGenre = genre ?: it.selectedGenre,
        sortMode = sortMode ?: it.sortMode,
        page = 1,
      )
    }
    loadMovies(reset = true)
  }

  fun loadMovies(reset: Boolean = false) {
    scope.launch {
      val st = _moviesState.value
      val nextPage = if (reset) 1 else st.page + 1
      _moviesState.update {
        if (reset) it.copy(isLoading = true) else it.copy(isLoadingMore = true)
      }
      val fetched = catalogRepository.loadMoviesCatalog(st.selectedGenre, st.sortMode, nextPage)
      _moviesState.update { cur ->
        val combined = if (reset) fetched else (cur.items + fetched).distinctBy { it.id }
        cur.copy(items = combined, page = nextPage, isLoading = false, isLoadingMore = false)
      }
    }
  }

  fun setSeriesFilter(genre: String? = null, sortMode: CategorySortMode? = null) {
    _seriesState.update {
      it.copy(
        selectedGenre = genre ?: it.selectedGenre,
        sortMode = sortMode ?: it.sortMode,
        page = 1,
      )
    }
    loadSeries(reset = true)
  }

  fun loadSeries(reset: Boolean = false) {
    scope.launch {
      val st = _seriesState.value
      val nextPage = if (reset) 1 else st.page + 1
      _seriesState.update {
        if (reset) it.copy(isLoading = true) else it.copy(isLoadingMore = true)
      }
      val fetched = catalogRepository.loadSeriesCatalog(st.selectedGenre, st.sortMode, nextPage)
      _seriesState.update { cur ->
        val combined = if (reset) fetched else (cur.items + fetched).distinctBy { it.id }
        cur.copy(items = combined, page = nextPage, isLoading = false, isLoadingMore = false)
      }
    }
  }

  fun setAnimeFilter(genre: String? = null, sortMode: AnimeSortMode? = null) {
    _animeState.update {
      it.copy(
        selectedGenre = genre ?: it.selectedGenre,
        animeSortMode = sortMode ?: it.animeSortMode,
        page = 1,
      )
    }
    loadAnime(reset = true)
  }

  fun loadAnime(reset: Boolean = false) {
    scope.launch {
      val st = _animeState.value
      val nextPage = if (reset) 1 else st.page + 1
      _animeState.update {
        if (reset) it.copy(isLoading = true) else it.copy(isLoadingMore = true)
      }
      val fetched = catalogRepository.loadAnimeCatalog(st.selectedGenre, st.animeSortMode, nextPage)
      _animeState.update { cur ->
        val combined = if (reset) fetched else (cur.items + fetched).distinctBy { it.id }
        cur.copy(items = combined, page = nextPage, isLoading = false, isLoadingMore = false)
      }
    }
  }

  fun setDramasFilter(region: String? = null, sortMode: CategorySortMode? = null) {
    _dramasState.update {
      it.copy(
        selectedGenre = region ?: it.selectedGenre,
        sortMode = sortMode ?: it.sortMode,
        page = 1,
      )
    }
    loadDramas(reset = true)
  }

  fun loadDramas(reset: Boolean = false) {
    scope.launch {
      val st = _dramasState.value
      val nextPage = if (reset) 1 else st.page + 1
      _dramasState.update {
        if (reset) it.copy(isLoading = true) else it.copy(isLoadingMore = true)
      }
      val fetched = catalogRepository.loadDramasCatalog(st.selectedGenre, st.sortMode, nextPage)
      _dramasState.update { cur ->
        val combined = if (reset) fetched else (cur.items + fetched).distinctBy { it.id }
        cur.copy(items = combined, page = nextPage, isLoading = false, isLoadingMore = false)
      }
    }
  }

  fun updateSearchQuery(query: String) {
    _searchState.update { it.copy(query = query) }
    searchDebounceJob?.cancel()
    if (query.trim().isEmpty()) {
      _searchState.update { it.copy(isSearching = false, suggestions = emptyList(), results = emptyList()) }
      return
    }
    searchDebounceJob =
      scope.launch {
        delay(280L)
        executeSearch(query)
      }
  }

  fun executeSearch(explicitQuery: String? = null) {
    val q = (explicitQuery ?: _searchState.value.query).trim()
    _searchState.update { it.copy(query = q, isSearching = q.isNotBlank()) }
    if (q.isBlank()) return
    scope.launch {
      val (results, suggestions, _) = catalogRepository.searchCatalogWithSuggestions(q)
      _searchState.update {
        it.copy(
          results = results,
          suggestions = suggestions.map { s -> s.title }.distinct(),
          isSearching = false,
        )
      }
    }
  }

  fun setSearchMediaFilter(filter: String) {
    _searchState.update { it.copy(mediaFilter = filter) }
  }

  fun toggleWatchlist(movie: Movie) {
    val added = storage.toggleWatchlist(movie)
    postBanner(if (added) "Added ${movie.title} to Watchlist" else "Removed ${movie.title} from Watchlist")
  }

  fun saveWatchlistDetails(
    movie: Movie,
    watchStatus: WatchStatus,
    favorite: Boolean,
    rating: Int,
    note: String,
  ) {
    storage.saveEntry(movie, watchStatus, favorite, rating, note)
    postBanner("Saved review & status for ${movie.title}")
  }

  fun chooseDownloadFolder() {
    scope.launch {
      val picked = WindowsSystemHelper.pickDownloadDirectory(storage.currentSettings().downloadDirectory)
        ?: return@launch
      storage.updateSettings { it.copy(downloadDirectory = picked) }
      downloadManager.refreshLocalLibrary(picked)
      postBanner("Download folder set to $picked")
    }
  }

  fun checkForUpdatesIfDue() {
    if (!storage.shouldAutoCheckUpdates()) return
    checkForUpdates(manual = false)
  }

  fun checkForUpdates(manual: Boolean = true) {
    val s = storage.currentSettings()
    val repoSlug = DesktopGitHubUpdateRepository.normalizeRepoSlug(s.githubRepo)
    _updateState.update {
      it.copy(isChecking = true, errorMessage = null, downloadError = null)
    }
    scope.launch {
      val res = updateRepository.checkLatestWindowsRelease(repoSlug, appVersion)
      val nowMs = System.currentTimeMillis()
      storage.updateSettings { it.copy(lastUpdateCheckMs = nowMs) }
      res
        .onSuccess { info ->
          if (info != null) {
            storage.updateSettings { it.copy(cachedReleaseJson = info.toJsonString()) }
          }
          val isNew = info?.isNewerThanCurrent == true
          val shouldOpenDialog =
            info != null &&
              isNew &&
              (manual || storage.currentSettings().dismissedReleaseTag != info.tagName)
          _updateState.update {
            it.copy(
              isChecking = false,
              checkedOnce = true,
              latestRelease = info,
              errorMessage = if (info == null && manual) "No published GitHub releases found yet." else null,
              showUpdateDialog = shouldOpenDialog || it.showUpdateDialog,
            )
          }
          if (isNew && !manual) {
            notifier?.showDesktopNotification(
              title = "Decan Movie Update Available",
              message = "Version ${info!!.tagName} is ready to download from GitHub.",
              enabled = s.enableDesktopNotifications,
            )
          } else if (manual && info != null && !isNew) {
            postBanner("Decan Movie v$appVersion is up to date (${info.tagName}).")
          }
        }
        .onFailure { err ->
          _updateState.update {
            it.copy(
              isChecking = false,
              checkedOnce = true,
              errorMessage = err.message ?: "Unable to check GitHub releases.",
            )
          }
        }
    }
  }

  fun openUpdateDialog() {
    _updateState.update { it.copy(showUpdateDialog = true) }
  }

  fun dismissUpdateDialog() {
    val tag = _updateState.value.latestRelease?.tagName.orEmpty()
    if (tag.isNotBlank()) {
      storage.updateSettings { it.copy(dismissedReleaseTag = tag) }
    }
    _updateState.update { it.copy(showUpdateDialog = false) }
  }

  fun downloadWindowsUpdate(asset: WindowsReleaseAsset?, fallbackUrl: String, tagName: String) {
    val s = storage.currentSettings()
    val chosen =
      asset
        ?: _updateState.value.latestRelease?.let {
          DesktopGitHubUpdateRepository.selectWindowsAsset(it.assets, s.preferPortableUpdates)
        }

    if (chosen == null || !chosen.isWindowsDesktopPackage) {
      if (fallbackUrl.isNotBlank()) {
        WindowsSystemHelper.openExternalUrl(fallbackUrl)
        postBanner("No direct Windows asset attached to $tagName yet — opened GitHub Releases page.")
      } else {
        _updateState.update { it.copy(downloadError = "No Windows installer found for $tagName.") }
      }
      return
    }

    val safeTag = tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val updatesDir = WindowsAppPaths.updatesDirectory()
    val targetName = chosen.name.ifBlank { "Decan-Movie-Setup-$safeTag.exe" }
    val destFile = File(updatesDir, "${safeTag}_$targetName")

    _updateState.update {
      it.copy(
        isDownloading = true,
        downloadProgress = 0f,
        downloadError = null,
        isChecksumVerified = false,
      )
    }

    scope.launch {
      val result =
        updateRepository.downloadWindowsPackage(
          packageUrl = chosen.downloadUrl,
          destFile = destFile,
          expectedSha256 = chosen.sha256,
          onProgress = { pct -> _updateState.update { it.copy(downloadProgress = pct) } },
        )
      result
        .onSuccess { file ->
          _updateState.update {
            it.copy(
              isDownloading = false,
              downloadProgress = 1f,
              downloadedFilePath = file.absolutePath,
              downloadedTag = tagName,
              isChecksumVerified = !chosen.sha256.isNullOrBlank(),
              downloadError = null,
            )
          }
          notifier?.showDesktopNotification(
            title = "Update Ready to Install",
            message = "${file.name} downloaded and verified. Click Install Update when ready.",
            enabled = storage.currentSettings().enableDesktopNotifications,
          )
          postBanner("Downloaded ${file.name}. Ready to install!")
        }
        .onFailure { err ->
          _updateState.update {
            it.copy(
              isDownloading = false,
              downloadProgress = 0f,
              downloadError = err.message ?: "Update download failed.",
            )
          }
        }
    }
  }

  fun installDownloadedUpdate() {
    val path = _updateState.value.downloadedFilePath ?: return
    val file = File(path)
    if (!DesktopGitHubUpdateRepository.hasValidWindowsPackageHeader(file)) {
      _updateState.update { it.copy(downloadError = "Update package verification failed.") }
      return
    }
    WindowsSystemHelper.launchWindowsInstaller(file)
      .onSuccess {
        postBanner("Launching ${file.name} installer…")
      }
      .onFailure { err ->
        _updateState.update {
          it.copy(downloadError = err.message ?: "Could not launch Windows installer.")
        }
      }
  }

  fun postBanner(message: String) {
    _bannerMessage.value = message
    scope.launch {
      delay(3500L)
      if (_bannerMessage.value == message) _bannerMessage.value = null
    }
  }

  fun dismissBanner() {
    _bannerMessage.value = null
  }

  fun shutdown() {
    downloadManager.shutdown()
  }

  companion object {
    fun resolveDesktopAppVersion(): String {
      val prop = System.getProperty("decan.app.version")?.trim()?.takeIf { it.isNotBlank() }
      if (prop != null) return prop.removePrefix("v")
      val pkg = DesktopAppController::class.java.`package`?.implementationVersion?.trim()
      return pkg?.takeIf { it.isNotBlank() }?.removePrefix("v") ?: "1.1.0"
    }
  }
}
