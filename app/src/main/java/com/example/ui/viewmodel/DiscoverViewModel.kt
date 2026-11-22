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
}

data class DiscoverUiState(
  val isLoading: Boolean = true,
  val spotlight: List<Movie> = emptyList(),
  val rails: List<MovieRail> = emptyList(),
  val allMovies: List<Movie> = emptyList(),
  val selectedMediaFilter: MediaFilter = MediaFilter.ALL,
  val selectedGenre: String = "All",
  val availableGenres: List<String> = listOf("All"),
  val isOfflineFallback: Boolean = false,
  val errorMessage: String? = null,
)

class DiscoverViewModel(
  private val catalogRepository: MovieCatalogRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(DiscoverUiState())
  val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

  init {
    refreshCatalog()
  }

  fun refreshCatalog() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        val feed = catalogRepository.loadHomeCatalog()
        val genres =
          listOf("All") +
            feed.allMovies
              .map { it.genre }
              .filter { it.isNotBlank() }
              .distinct()
              .sorted()
              .take(10)

        _uiState.update {
          it.copy(
            isLoading = false,
            spotlight = feed.spotlight,
            rails = feed.rails,
            allMovies = feed.allMovies,
            availableGenres = genres,
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
    _uiState.update { it.copy(selectedGenre = genre) }
  }

  fun selectMediaFilter(filter: MediaFilter) {
    _uiState.update { it.copy(selectedMediaFilter = filter) }
  }
}
