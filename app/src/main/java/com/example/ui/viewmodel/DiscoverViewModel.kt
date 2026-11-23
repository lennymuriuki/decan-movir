package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Movie
import com.example.data.model.MovieRail
import com.example.data.remote.AnimeSortMode
import com.example.data.remote.JikanTvMazeService
import com.example.data.remote.MovieCatalogRepository
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
  val isLoadingMore: Boolean = false,
  val isLoadingAnime: Boolean = false,
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
  val availableGenres: List<String> =
    listOf("All", "Action", "Sci-Fi", "Animation", "Drama", "Thriller", "Comedy", "Horror", "Romance"),
  val trendingKeywords: List<String> = emptyList(),
  val isOfflineFallback: Boolean = false,
  val errorMessage: String? = null,
)

class DiscoverViewModel(
  private val catalogRepository: MovieCatalogRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(DiscoverUiState())
  val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

  private var currentPage = 1
  private var animePage = 1

  init {
    loadCatalog(forceRefresh = false)
  }

  fun refreshCatalog() {
    loadCatalog(forceRefresh = true)
    if (_uiState.value.selectedMediaFilter == MediaFilter.ANIME) {
      fetchAnimeFiltered(
        genre = _uiState.value.selectedAnimeGenre,
        sortMode = _uiState.value.selectedAnimeSort,
        page = 1,
        append = false,
      )
    }
  }

  private fun loadCatalog(forceRefresh: Boolean) {
    viewModelScope.launch {
      currentPage = 1
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        val feed = catalogRepository.loadHomeCatalog(forceRefresh = forceRefresh)
        val dynamicGenres =
          feed.allMovies
            .map { it.genre }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val mergedGenres =
          (listOf("All", "Action", "Sci-Fi", "Animation", "Drama", "Thriller", "Comedy", "Horror", "Romance", "Supernatural", "Adventure") +
              dynamicGenres)
            .distinct()
            .take(16)

        _uiState.update {
          it.copy(
            isLoading = false,
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
            errorMessage = e.localizedMessage ?: "Failed to load movie catalog",
          )
        }
      }
    }
  }

  fun selectGenre(genre: String) {
    _uiState.update { it.copy(selectedGenre = genre, genreFeedMovies = emptyList()) }
    if (genre != "All") {
      viewModelScope.launch {
        val remote = catalogRepository.loadGenreFeed(genre)
        if (remote.isNotEmpty()) {
          _uiState.update { state ->
            state.copy(
              genreFeedMovies = remote,
              allMovies = (state.allMovies + remote).distinctBy { it.id },
            )
          }
        }
      }
    }
  }

  fun selectMediaFilter(filter: MediaFilter) {
    _uiState.update { it.copy(selectedMediaFilter = filter) }
    if (filter == MediaFilter.ANIME && _uiState.value.animeFeed.isEmpty()) {
      fetchAnimeFiltered(
        genre = _uiState.value.selectedAnimeGenre,
        sortMode = _uiState.value.selectedAnimeSort,
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

  private fun fetchAnimeFiltered(
    genre: String,
    sortMode: AnimeSortMode,
    page: Int,
    append: Boolean,
  ) {
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
    if (state.isLoadingMore || state.selectedMediaFilter == MediaFilter.ALL || state.selectedMediaFilter == MediaFilter.RANKINGS) {
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
