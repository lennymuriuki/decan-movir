package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Movie
import com.example.data.model.MovieRail
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
  val spotlight: List<Movie> = emptyList(),
  val rankings: List<Movie> = emptyList(),
  val anime: List<Movie> = emptyList(),
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

  init {
    loadCatalog(forceRefresh = false)
  }

  fun refreshCatalog() {
    loadCatalog(forceRefresh = true)
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
          (listOf("All", "Action", "Sci-Fi", "Animation", "Drama", "Thriller", "Comedy", "Horror", "Romance") +
              dynamicGenres)
            .distinct()
            .take(14)

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
  }

  fun loadMoreForActiveFilter() {
    val state = _uiState.value
    if (state.isLoadingMore || state.selectedMediaFilter == MediaFilter.ALL || state.selectedMediaFilter == MediaFilter.RANKINGS) {
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingMore = true) }
      currentPage += 1
      val extra =
        catalogRepository.loadMorePage(
          isSeries = state.selectedMediaFilter == MediaFilter.SERIES,
          isAnime = state.selectedMediaFilter == MediaFilter.ANIME,
          page = currentPage,
        )
      _uiState.update { s ->
        s.copy(
          isLoadingMore = false,
          anime = if (s.selectedMediaFilter == MediaFilter.ANIME) (s.anime + extra).distinctBy { it.id } else s.anime,
          allMovies = (s.allMovies + extra).distinctBy { it.id },
        )
      }
    }
  }
}
