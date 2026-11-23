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
import com.example.data.settings.AppSettingsRepository
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
  val lastCheckedAtMs: Long = 0L,
  val errorMessage: String? = null,
  val isDownloadingApk: Boolean = false,
  val downloadProgress: Float = 0f,
  val downloadedApkPath: String? = null,
  val downloadedReleaseTag: String? = null,
  val isChecksumVerified: Boolean = false,
  val downloadErrorMessage: String? = null,
  val installStatusMessage: String? = null,
  val showUpdateDialog: Boolean = false,
) {
  val hasUpdate: Boolean
    get() = latestRelease?.isNewerThanCurrent == true
}

class UpdateViewModel(
  private val updateRepository: GitHubUpdateRepository = GitHubUpdateRepository(),
  private val settingsRepository: AppSettingsRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(loadInitialCachedState())
  val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

  private fun loadInitialCachedState(): UpdateUiState {
    val cachedJson = settingsRepository?.getCachedReleaseJson()
    val cachedRelease =
      cachedJson?.let {
        GitHubUpdateRepository.parseReleaseJson(it, BuildConfig.VERSION_NAME)
      }
    val currentSettings = settingsRepository?.settings?.value
    val lastCheck = currentSettings?.lastUpdateCheckMs ?: 0L
    val shouldPrompt =
      cachedRelease != null &&
        cachedRelease.isNewerThanCurrent &&
        cachedRelease.tagName != currentSettings?.dismissedReleaseTag
    return UpdateUiState(
      latestRelease = cachedRelease,
      checkedOnce = lastCheck > 0L && cachedRelease != null,
      lastCheckedAtMs = lastCheck,
      showUpdateDialog = shouldPrompt,
    )
  }

  fun checkForUpdatesIfDue(repoSlug: String) {
    val repo = settingsRepository
    if (repo != null && !repo.shouldAutoCheckUpdates()) {
      val cur = _uiState.value
      val latest = cur.latestRelease
      _uiState.update {
        it.copy(
          checkedOnce = true,
          lastCheckedAtMs = repo.settings.value.lastUpdateCheckMs,
          showUpdateDialog =
            latest != null &&
              latest.isNewerThanCurrent &&
              latest.tagName != repo.settings.value.dismissedReleaseTag,
        )
      }
      return
    }
    checkForUpdates(repoSlug = repoSlug, manualTrigger = false)
  }

  fun checkForUpdates(repoSlug: String, manualTrigger: Boolean = true) {
    if (_uiState.value.isChecking) return
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isChecking = true,
          errorMessage = null,
          downloadErrorMessage = null,
          installStatusMessage = null,
        )
      }
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
      val nowMs = System.currentTimeMillis()
      val dismissedTag = settingsRepository?.settings?.value?.dismissedReleaseTag.orEmpty()

      latestRes
        .onSuccess { latest ->
          val resolvedLatest = latest ?: history.firstOrNull()
          settingsRepository?.markUpdateCheckedNow(nowMs)
          if (resolvedLatest != null) {
            settingsRepository?.saveCachedReleaseJson(resolvedLatest.toJsonString())
          }
          val shouldPrompt =
            resolvedLatest != null &&
              resolvedLatest.isNewerThanCurrent &&
              (manualTrigger || resolvedLatest.tagName != dismissedTag)
          _uiState.update {
            it.copy(
              isChecking = false,
              latestRelease = resolvedLatest,
              recentReleases = history,
              checkedOnce = true,
              lastCheckedAtMs = nowMs,
              errorMessage = null,
              showUpdateDialog = shouldPrompt,
            )
          }
        }
        .onFailure {
          if (history.isNotEmpty()) {
            val first = history.first()
            settingsRepository?.markUpdateCheckedNow(nowMs)
            settingsRepository?.saveCachedReleaseJson(first.toJsonString())
            val shouldPrompt =
              first.isNewerThanCurrent && (manualTrigger || first.tagName != dismissedTag)
            _uiState.update {
              it.copy(
                isChecking = false,
                latestRelease = first,
                recentReleases = history,
                checkedOnce = true,
                lastCheckedAtMs = nowMs,
                errorMessage = null,
                showUpdateDialog = shouldPrompt,
              )
            }
          } else {
            _uiState.update {
              it.copy(
                isChecking = false,
                checkedOnce = true,
                errorMessage = "Updates unavailable right now. Check connection and try again.",
              )
            }
          }
        }
    }
  }

  fun dismissUpdatePrompt(tagName: String? = _uiState.value.latestRelease?.tagName) {
    if (!tagName.isNullOrBlank()) {
      settingsRepository?.dismissReleaseDialogForTag(tagName)
    }
    _uiState.update { it.copy(showUpdateDialog = false) }
  }

  fun openUpdatePrompt() {
    if (_uiState.value.hasUpdate) {
      _uiState.update { it.copy(showUpdateDialog = true) }
    }
  }

  fun downloadAndInstallApk(
    context: Context,
    apkUrl: String,
    fallbackWebUrl: String,
    versionTag: String,
    expectedSha256: String? = null,
    autoLaunchInstaller: Boolean = false,
    onNotify: (String) -> Unit,
  ) {
    if (_uiState.value.isDownloadingApk) return
    val appCtx = context.applicationContext
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isDownloadingApk = true,
          downloadProgress = 0f,
          downloadErrorMessage = null,
          isChecksumVerified = false,
          installStatusMessage = "Downloading $versionTag update…",
        )
      }
      val updatesDir = File(appCtx.cacheDir, "updates")
      val safeTag = versionTag.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val apkFile = File(updatesDir, "decan-movie-$safeTag.apk")
      val result =
        updateRepository.downloadApkToFile(
          apkUrl = apkUrl,
          destFile = apkFile,
          expectedSha256 = expectedSha256,
          onProgress = { progress ->
            _uiState.update { it.copy(downloadProgress = progress) }
          },
        )
      result
        .onSuccess { file ->
          val verified = !expectedSha256.isNullOrBlank()
          _uiState.update {
            it.copy(
              isDownloadingApk = false,
              downloadProgress = 1f,
              downloadedApkPath = file.absolutePath,
              downloadedReleaseTag = versionTag,
              isChecksumVerified = verified,
              downloadErrorMessage = null,
              installStatusMessage =
                if (verified) "Verified $versionTag package ready to install"
                else "Update $versionTag downloaded and ready to install",
            )
          }
          if (autoLaunchInstaller) {
            launchApkInstaller(context, file, onNotify)
          } else {
            onNotify("Update $versionTag ready — tap Install Update.")
          }
        }
        .onFailure { err ->
          val errText = err.message?.takeIf { it.isNotBlank() } ?: "Download failed"
          _uiState.update {
            it.copy(
              isDownloadingApk = false,
              downloadProgress = 0f,
              downloadErrorMessage = errText,
              installStatusMessage = errText,
            )
          }
          onNotify(errText)
        }
    }
  }

  fun launchApkInstaller(context: Context, apkFile: File, onNotify: (String) -> Unit) {
    if (!apkFile.exists() || !GitHubUpdateRepository.hasValidApkHeader(apkFile)) {
      _uiState.update {
        it.copy(
          downloadedApkPath = null,
          downloadedReleaseTag = null,
          installStatusMessage = "Update package missing or corrupted — please re-download.",
        )
      }
      onNotify("Update package missing — tap Download Update again.")
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      !context.packageManager.canRequestPackageInstalls()
    ) {
      onNotify("Allow 'Install unknown apps' for Decan Movie, then tap Install Update again.")
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
