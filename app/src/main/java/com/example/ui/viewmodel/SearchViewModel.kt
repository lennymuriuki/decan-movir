package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Movie
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MovieSortOption(val label: String) {
  FEATURED("Featured"),
  NEWEST("Newest"),
  RUNTIME("Shortest"),
}

data class SearchUiState(
  val query: String = "",
  val selectedGenre: String = "All",
  val sortOption: MovieSortOption = MovieSortOption.FEATURED,
  val isLoading: Boolean = false,
  val results: List<Movie> = emptyList(),
  val suggestions: List<MovieBoxSuggestion> = emptyList(),
  val trendingKeywords: List<String> =
    listOf("Avatar", "Breaking Bad", "Anime", "Oppenheimer", "Sci-Fi", "Action"),
  val isOfflineFallback: Boolean = false,
)

class SearchViewModel(
  private val catalogRepository: MovieCatalogRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SearchUiState())
  val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

  private var searchJob: Job? = null

  init {
    viewModelScope.launch {
      val trendingDeferred = async { catalogRepository.getTrendingKeywords() }
      executeSearch("")
      val trending = trendingDeferred.await()
      if (trending.isNotEmpty()) {
        _uiState.update { it.copy(trendingKeywords = trending) }
      }
    }
  }

  fun onQueryChange(newQuery: String) {
    _uiState.update { it.copy(query = newQuery) }
    searchJob?.cancel()
    searchJob =
      viewModelScope.launch {
        delay(280L)
        executeSearch(newQuery)
      }
  }

  fun submitSearch() {
    searchJob?.cancel()
    searchJob = viewModelScope.launch { executeSearch(_uiState.value.query) }
  }

  fun selectGenre(genre: String) {
    _uiState.update { it.copy(selectedGenre = genre) }
  }

  fun selectSortOption(option: MovieSortOption) {
    _uiState.update { it.copy(sortOption = option) }
  }

  private suspend fun executeSearch(query: String) = coroutineScope {
    _uiState.update { it.copy(isLoading = true) }
    val searchDeferred = async { catalogRepository.searchMovies(query) }
    val suggestDeferred = async { catalogRepository.searchSuggestions(query) }
    val (movies, offline) = searchDeferred.await()
    val suggestions = suggestDeferred.await()
    _uiState.update {
      it.copy(
        isLoading = false,
        results = movies,
        suggestions = suggestions,
        isOfflineFallback = offline,
      )
    }
  }
}
