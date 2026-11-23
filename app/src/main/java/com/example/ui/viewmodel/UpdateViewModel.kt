package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateRepository
import java.io.File
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
  val isDownloadingApk: Boolean = false,
  val downloadProgress: Float = 0f,
  val downloadedApkPath: String? = null,
  val installStatusMessage: String? = null,
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
      _uiState.update { it.copy(isChecking = true, errorMessage = null, installStatusMessage = null) }
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
      val history = historyRes.getOrDefault(emptyList())
      latestRes
        .onSuccess { latest ->
          val resolvedLatest = latest ?: history.firstOrNull()
          _uiState.update {
            it.copy(
              isChecking = false,
              latestRelease = resolvedLatest,
              recentReleases = history,
              checkedOnce = true,
              errorMessage = null,
            )
          }
        }
        .onFailure {
          if (history.isNotEmpty()) {
            _uiState.update {
              it.copy(
                isChecking = false,
                latestRelease = history.first(),
                recentReleases = history,
                checkedOnce = true,
                errorMessage = null,
              )
            }
          } else {
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

  fun downloadAndInstallApk(
    context: Context,
    apkUrl: String,
    fallbackWebUrl: String,
    versionTag: String,
    onNotify: (String) -> Unit,
  ) {
    if (_uiState.value.isDownloadingApk) return
    val appCtx = context.applicationContext
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isDownloadingApk = true,
          downloadProgress = 0f,
          installStatusMessage = "Downloading $versionTag update…",
        )
      }
      val updatesDir = File(appCtx.cacheDir, "updates")
      val apkFile = File(updatesDir, "decan-movie-$versionTag.apk")
      val result =
        updateRepository.downloadApkToFile(
          apkUrl = apkUrl,
          destFile = apkFile,
          onProgress = { progress ->
            _uiState.update { it.copy(downloadProgress = progress) }
          },
        )
      result
        .onSuccess { file ->
          _uiState.update {
            it.copy(
              isDownloadingApk = false,
              downloadProgress = 1f,
              downloadedApkPath = file.absolutePath,
              installStatusMessage = "Ready to install $versionTag",
            )
          }
          launchApkInstaller(context, file, onNotify)
        }
        .onFailure {
          _uiState.update {
            it.copy(
              isDownloadingApk = false,
              installStatusMessage = "Direct download unavailable — opening browser",
            )
          }
          runCatching {
            context.startActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse(fallbackWebUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            )
          }.onFailure {
            onNotify("Update download unavailable right now")
          }
        }
    }
  }

  fun launchApkInstaller(context: Context, apkFile: File, onNotify: (String) -> Unit) {
    if (!apkFile.exists()) {
      onNotify("Update package missing — tap Download & Install again.")
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      !context.packageManager.canRequestPackageInstalls()
    ) {
      onNotify("Allow 'Install unknown apps' for Decan Movie, then tap Install again.")
      runCatching {
        val permIntent =
          Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
          ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(permIntent)
      }
      return
    }

    runCatching {
      val contentUri =
        FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          apkFile,
        )
      val installIntent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(contentUri, "application/vnd.android.package-archive")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(installIntent)
    }.onFailure {
      onNotify("Could not open installer — check app permissions.")
    }
  }
}
