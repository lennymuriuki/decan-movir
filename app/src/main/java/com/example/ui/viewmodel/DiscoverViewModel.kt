package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Movie
import com.example.data.model.MovieRail
import com.example.data.remote.AnimeSortMode
import com.example.data.remote.CategorySortMode
import com.example.data.remote.JikanTvMazeService
import com.example.data.remote.MovieCatalogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MediaFilter(val label: String) {
  ALL("All"),
  MOVIES("Movies"),
  SERIES("TV Series"),
  ANIME("Anime"),
  RANKINGS("Rankings"),
}

data class DiscoverUiState(
  val isLoading: Boolean = true,
  val isRefreshing: Boolean = false,
  val isLoadingMore: Boolean = false,
  val isLoadingAnime: Boolean = false,
  val isLoadingCategory: Boolean = false,
  val spotlight: List<Movie> = emptyList(),
  val rankings: List<Movie> = emptyList(),
  val anime: List<Movie> = emptyList(),
  val animeFeed: List<Movie> = emptyList(),
  val selectedAnimeGenre: String = "All",
  val selectedAnimeSort: AnimeSortMode = AnimeSortMode.TOP_RANKED,
  val availableAnimeGenres: List<String> = JikanTvMazeService.ANIME_GENRES.map { it.label },
  val recommendations: List<Movie> = emptyList(),
  val rails: List<MovieRail> = emptyList(),
  val allMovies: List<Movie> = emptyList(),
  val genreFeedMovies: List<Movie> = emptyList(),
  val selectedMediaFilter: MediaFilter = MediaFilter.ALL,
  val selectedGenre: String = "All",
  val selectedCategorySort: CategorySortMode = CategorySortMode.FEATURED,
  val availableGenres: List<String> = DEFAULT_CATEGORIES,
  val trendingKeywords: List<String> = emptyList(),
  val isOfflineFallback: Boolean = false,
  val errorMessage: String? = null,
)

private val DEFAULT_CATEGORIES =
  listOf(
    "All",
    "Action",
    "Adventure",
    "Anime",
    "Supernatural",
    "Sci-Fi",
    "Fantasy",
    "Drama",
    "Thriller",
    "Comedy",
    "Horror",
    "Romance",
    "Mystery",
  )

class DiscoverViewModel(
  private val catalogRepository: MovieCatalogRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(DiscoverUiState())
  val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

  private var currentPage = 1
  private var animePage = 1
  private var categoryPage = 1
  private var categoryJob: Job? = null
  private var animeJob: Job? = null

  init {
    val snapshot = catalogRepository.getCachedHomeSnapshot()
    if (snapshot != null && snapshot.allMovies.isNotEmpty()) {
      _uiState.update {
        it.copy(
          isLoading = false,
          isRefreshing = true,
          spotlight = snapshot.spotlight,
          rankings = snapshot.rankings,
          anime = snapshot.anime,
          recommendations = snapshot.recommendations,
          rails = snapshot.rails,
          allMovies = snapshot.allMovies,
          availableGenres = buildMergedCategories(snapshot.allMovies),
          trendingKeywords = snapshot.trendingKeywords,
          isOfflineFallback = false,
        )
      }
      loadCatalog(forceRefresh = true, showFullLoader = false)
    } else {
      loadCatalog(forceRefresh = false, showFullLoader = true)
    }
  }

  fun refreshCatalog() {
    val hasMovies = _uiState.value.allMovies.isNotEmpty()
    loadCatalog(forceRefresh = true, showFullLoader = !hasMovies)
    val state = _uiState.value
    if (state.selectedMediaFilter == MediaFilter.ANIME) {
      fetchAnimeFiltered(
        genre = state.selectedAnimeGenre,
        sortMode = state.selectedAnimeSort,
        page = 1,
        append = false,
      )
    } else if (state.selectedGenre != "All") {
      fetchCategoryFiltered(
        category = state.selectedGenre,
        mediaFilter = state.selectedMediaFilter,
        sortMode = state.selectedCategorySort,
        page = 1,
        append = false,
      )
    }
  }

  private fun buildMergedCategories(allMovies: List<Movie>): List<String> {
    val dynamicGenres =
      allMovies
        .flatMap { it.genre.split(",", "/") }
        .map { it.trim() }
        .filter {
          it.isNotBlank() &&
            !it.equals("Music", ignoreCase = true) &&
            !it.equals("Song", ignoreCase = true) &&
            !it.equals("Cinema", ignoreCase = true) &&
            !it.equals("Featured", ignoreCase = true)
        }
        .distinct()
        .sorted()
    return (DEFAULT_CATEGORIES + dynamicGenres).distinct().take(18)
  }

  private fun loadCatalog(forceRefresh: Boolean, showFullLoader: Boolean) {
    viewModelScope.launch {
      currentPage = 1
      _uiState.update {
        it.copy(
          isLoading = showFullLoader && it.allMovies.isEmpty(),
          isRefreshing = !showFullLoader || it.allMovies.isNotEmpty(),
          errorMessage = null,
        )
      }
      try {
        val feed = catalogRepository.loadHomeCatalog(forceRefresh = forceRefresh)
        val mergedGenres = buildMergedCategories(feed.allMovies)

        _uiState.update {
          it.copy(
            isLoading = false,
            isRefreshing = false,
            spotlight = feed.spotlight,
            rankings = feed.rankings,
            anime = feed.anime,
            recommendations = feed.recommendations,
            rails = feed.rails,
            allMovies = feed.allMovies,
            availableGenres = mergedGenres,
            trendingKeywords = feed.trendingKeywords,
            isOfflineFallback = feed.isOfflineFallback,
            errorMessage = null,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoading = false,
            isRefreshing = false,
            errorMessage =
              if (it.allMovies.isEmpty()) e.localizedMessage ?: "Failed to load movie catalog"
              else null,
          )
        }
      }
    }
  }

  fun selectGenre(genre: String) {
    categoryPage = 1
    _uiState.update {
      it.copy(
        selectedGenre = genre,
        genreFeedMovies = emptyList(),
        isLoadingCategory = genre != "All",
      )
    }
    if (genre == "All") {
      categoryJob?.cancel()
      _uiState.update { it.copy(isLoadingCategory = false) }
      return
    }
    val state = _uiState.value
    fetchCategoryFiltered(
      category = genre,
      mediaFilter = state.selectedMediaFilter,
      sortMode = state.selectedCategorySort,
      page = 1,
      append = false,
    )
  }

  fun selectCategorySort(sortMode: CategorySortMode) {
    val prev = _uiState.value
    if (prev.selectedCategorySort == sortMode) return
    _uiState.update { it.copy(selectedCategorySort = sortMode) }
    if (prev.selectedGenre != "All") {
      categoryPage = 1
      fetchCategoryFiltered(
        category = prev.selectedGenre,
        mediaFilter = prev.selectedMediaFilter,
        sortMode = sortMode,
        page = 1,
        append = false,
      )
    }
  }

  fun selectMediaFilter(filter: MediaFilter) {
    val prev = _uiState.value
    _uiState.update { it.copy(selectedMediaFilter = filter) }
    if (filter == MediaFilter.ANIME) {
      if (prev.animeFeed.isEmpty()) {
        fetchAnimeFiltered(
          genre = prev.selectedAnimeGenre,
          sortMode = prev.selectedAnimeSort,
          page = 1,
          append = false,
        )
      }
    } else if (prev.selectedGenre != "All") {
      categoryPage = 1
      fetchCategoryFiltered(
        category = prev.selectedGenre,
        mediaFilter = filter,
        sortMode = prev.selectedCategorySort,
        page = 1,
        append = false,
      )
    }
  }

  fun selectAnimeGenre(genre: String) {
    _uiState.update { it.copy(selectedAnimeGenre = genre) }
    animePage = 1
    fetchAnimeFiltered(
      genre = genre,
      sortMode = _uiState.value.selectedAnimeSort,
      page = 1,
      append = false,
    )
  }

  fun selectAnimeSort(sortMode: AnimeSortMode) {
    _uiState.update { it.copy(selectedAnimeSort = sortMode) }
    animePage = 1
    fetchAnimeFiltered(
      genre = _uiState.value.selectedAnimeGenre,
      sortMode = sortMode,
      page = 1,
      append = false,
    )
  }

  private fun fetchCategoryFiltered(
    category: String,
    mediaFilter: MediaFilter,
    sortMode: CategorySortMode,
    page: Int,
    append: Boolean,
  ) {
    categoryJob?.cancel()
    categoryJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(isLoadingCategory = !append, isLoadingMore = append)
        }
        val fetched =
          catalogRepository.loadCategoryCatalog(
            category = category,
            mediaFilterName = mediaFilter.name,
            sortMode = sortMode,
            page = page,
          )
        _uiState.update { s ->
          val updatedGenreFeed =
            if (append) {
              catalogRepository.sortCatalogMovies(
                (s.genreFeedMovies + fetched).distinctBy { it.id },
                sortMode,
              )
            } else {
              fetched
            }
          s.copy(
            isLoadingCategory = false,
            isLoadingMore = false,
            genreFeedMovies = updatedGenreFeed,
            allMovies = (s.allMovies + fetched).distinctBy { it.id },
          )
        }
      }
  }

  private fun fetchAnimeFiltered(
    genre: String,
    sortMode: AnimeSortMode,
    page: Int,
    append: Boolean,
  ) {
    animeJob?.cancel()
    animeJob =
      viewModelScope.launch {
        _uiState.update {
          it.copy(isLoadingAnime = !append, isLoadingMore = append)
        }
        val fetched =
          catalogRepository.loadAnimeCatalog(
            genreLabel = genre,
            sortMode = sortMode,
            page = page,
          )
        _uiState.update { s ->
          val updatedAnime =
            if (append) (s.animeFeed + fetched).distinctBy { it.id }
            else fetched
          s.copy(
            isLoadingAnime = false,
            isLoadingMore = false,
            animeFeed = updatedAnime,
            allMovies = (s.allMovies + fetched).distinctBy { it.id },
          )
        }
      }
  }

  fun loadMoreForActiveFilter() {
    val state = _uiState.value
    if (state.isLoadingMore || state.selectedMediaFilter == MediaFilter.RANKINGS) {
      return
    }
    if (state.selectedMediaFilter == MediaFilter.ANIME) {
      animePage += 1
      fetchAnimeFiltered(
        genre = state.selectedAnimeGenre,
        sortMode = state.selectedAnimeSort,
        page = animePage,
        append = true,
      )
      return
    }
    if (state.selectedGenre != "All") {
      categoryPage += 1
      fetchCategoryFiltered(
        category = state.selectedGenre,
        mediaFilter = state.selectedMediaFilter,
        sortMode = state.selectedCategorySort,
        page = categoryPage,
        append = true,
      )
      return
    }
    if (state.selectedMediaFilter == MediaFilter.ALL) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingMore = true) }
      currentPage += 1
      val extra =
        catalogRepository.loadMorePage(
          isSeries = state.selectedMediaFilter == MediaFilter.SERIES,
          isAnime = false,
          page = currentPage,
        )
      _uiState.update { s ->
        s.copy(
          isLoadingMore = false,
          allMovies = (s.allMovies + extra).distinctBy { it.id },
        )
      }
    }
  }
}
