package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Movie
import com.example.data.remote.MovieCatalogRepository
import kotlinx.coroutines.Job
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
  val isOfflineFallback: Boolean = false,
)

class SearchViewModel(
  private val catalogRepository: MovieCatalogRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SearchUiState())
  val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

  private var searchJob: Job? = null

  init {
    viewModelScope.launch { executeSearch("") }
  }

  fun onQueryChange(newQuery: String) {
    _uiState.update { it.copy(query = newQuery) }
    searchJob?.cancel()
    searchJob =
      viewModelScope.launch {
        delay(320L)
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

  private suspend fun executeSearch(query: String) {
    _uiState.update { it.copy(isLoading = true) }
    val (movies, offline) = catalogRepository.searchMovies(query)
    _uiState.update {
      it.copy(
        isLoading = false,
        results = movies,
        isOfflineFallback = offline,
      )
    }
  }
}
