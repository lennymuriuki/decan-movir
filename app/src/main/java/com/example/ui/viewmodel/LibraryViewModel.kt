package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.MovieLibraryRepository
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
  data object Loading : LibraryUiState

  data class Success(val entries: List<SavedMovieEntry>) : LibraryUiState

  data class Error(val message: String) : LibraryUiState
}

enum class LibraryFilter(val label: String) {
  ALL("All"),
  WATCHLIST("Watchlist"),
  WATCHING("Watching"),
  WATCHED("Watched"),
  FAVORITES("Favorites"),
}

class LibraryViewModel(
  private val libraryRepository: MovieLibraryRepository,
  private val currentUserId: String,
) : ViewModel() {

  val libraryState: StateFlow<LibraryUiState> =
    libraryRepository
      .observeLibrary(currentUserId)
      .map<List<SavedMovieEntry>, LibraryUiState> { list ->
        val sorted = list.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        LibraryUiState.Success(sorted)
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing library", rootError)
        emit(LibraryUiState.Error(rootError.message ?: "Failed to load your movie library"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LibraryUiState.Loading,
      )

  val userProfile: StateFlow<UserProfile?> =
    libraryRepository
      .observeUserProfile(currentUserId)
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing user profile", firestoreError ?: error)
        emit(null)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
  val selectedFilter: StateFlow<LibraryFilter> = _selectedFilter.asStateFlow()

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  fun syncUserProfile(user: FirebaseUser) {
    viewModelScope.launch { libraryRepository.ensureUserProfile(user) }
  }

  fun selectFilter(filter: LibraryFilter) {
    _selectedFilter.update { filter }
  }

  fun updatePreferredGenre(genre: String) {
    viewModelScope.launch {
      libraryRepository.updatePreferredGenre(genre).onFailure { err ->
        _actionMessage.value = err.localizedMessage ?: "Could not update preference"
      }
    }
  }

  fun toggleQuickWatchlist(movie: Movie, existingEntry: SavedMovieEntry?) {
    viewModelScope.launch {
      _isSaving.value = true
      val result =
        if (existingEntry == null) {
          libraryRepository.saveOrUpdateLibraryEntry(
            movie = movie,
            watchStatus = WatchStatus.WANT_TO_WATCH,
            favorite = false,
            personalRating = 0,
            personalNote = null,
          )
        } else {
          libraryRepository.removeFromLibrary(movie.id)
        }
      _isSaving.value = false
      result
        .onSuccess {
          _actionMessage.value =
            if (existingEntry == null) "Added \"${movie.title}\" to Watchlist"
            else "Removed \"${movie.title}\" from Library"
        }
        .onFailure { err ->
          _actionMessage.value = err.localizedMessage ?: "Failed to update watchlist"
        }
    }
  }

  fun toggleFavorite(movie: Movie, existingEntry: SavedMovieEntry?) {
    viewModelScope.launch {
      _isSaving.value = true
      val nextFav = !(existingEntry?.favorite ?: false)
      val status = existingEntry?.watchStatusEnum() ?: WatchStatus.WANT_TO_WATCH
      val rating = existingEntry?.personalRating?.toInt() ?: 0
      val note = existingEntry?.personalNote
      val result =
        libraryRepository.saveOrUpdateLibraryEntry(
          movie = movie,
          watchStatus = status,
          favorite = nextFav,
          personalRating = rating,
          personalNote = note,
        )
      _isSaving.value = false
      result
        .onSuccess {
          _actionMessage.value =
            if (nextFav) "Starred \"${movie.title}\" as favorite" else "Removed from favorites"
        }
        .onFailure { err ->
          _actionMessage.value = err.localizedMessage ?: "Failed to update favorite"
        }
    }
  }

  fun saveEntryDetails(
    movie: Movie,
    watchStatus: WatchStatus,
    favorite: Boolean,
    personalRating: Int,
    personalNote: String,
  ) {
    viewModelScope.launch {
      _isSaving.value = true
      val result =
        libraryRepository.saveOrUpdateLibraryEntry(
          movie = movie,
          watchStatus = watchStatus,
          favorite = favorite,
          personalRating = personalRating,
          personalNote = personalNote,
        )
      _isSaving.value = false
      result
        .onSuccess { _actionMessage.value = "Saved \"${movie.title}\" to your library" }
        .onFailure { err ->
          _actionMessage.value = err.localizedMessage ?: "Failed to save movie details"
        }
    }
  }

  fun removeMovie(movieId: String, title: String) {
    viewModelScope.launch {
      _isSaving.value = true
      libraryRepository
        .removeFromLibrary(movieId)
        .onSuccess { _actionMessage.value = "Removed \"$title\" from your library" }
        .onFailure { err ->
          _actionMessage.value = err.localizedMessage ?: "Failed to remove movie"
        }
      _isSaving.value = false
    }
  }

  fun clearMessage() {
    _actionMessage.value = null
  }

  private companion object {
    const val TAG = "LibraryVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
