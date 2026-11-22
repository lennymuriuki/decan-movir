package com.example.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class UpdateUiState(
  val isChecking: Boolean = false,
  val latestRelease: GitHubReleaseInfo? = null,
  val recentReleases: List<GitHubReleaseInfo> = emptyList(),
  val checkedOnce: Boolean = false,
  val errorMessage: String? = null,
) {
  val hasUpdate: Boolean
    get() = latestRelease?.isNewerThanCurrent == true
}

class UpdateViewModel(
  private val updateRepository: GitHubUpdateRepository = GitHubUpdateRepository(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(UpdateUiState())
  val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

  fun checkForUpdates(repoSlug: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isChecking = true, errorMessage = null) }
      val latestRes =
        updateRepository.checkLatestRelease(
          repoSlug = repoSlug,
          currentVersion = BuildConfig.VERSION_NAME,
        )
      val historyRes =
        updateRepository.fetchRecentReleases(
          repoSlug = repoSlug,
          currentVersion = BuildConfig.VERSION_NAME,
        )
      latestRes
        .onSuccess { latest ->
          _uiState.update {
            it.copy(
              isChecking = false,
              latestRelease = latest,
              recentReleases = historyRes.getOrDefault(emptyList()),
              checkedOnce = true,
              errorMessage = null,
            )
          }
        }
        .onFailure {
          _uiState.update {
            it.copy(
              isChecking = false,
              checkedOnce = true,
              errorMessage = "Updates unavailable right now",
            )
          }
        }
    }
  }
}
