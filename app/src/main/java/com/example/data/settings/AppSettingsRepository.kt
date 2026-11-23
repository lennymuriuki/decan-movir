package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppThemeMode(val label: String) {
  DARK("Cinema Dark"),
  LIGHT("Editorial Light"),
}

enum class DownloadStorageLocation(val label: String, val pathHint: String) {
  PUBLIC_DOWNLOADS("Device Downloads", "Downloads/DecanMovie"),
  GALLERY_MOVIES("Gallery (Movies)", "Movies/DecanMovie"),
  APP_PRIVATE("App Folder", "Decan App Library"),
}

@Immutable
data class AppSettings(
  val themeMode: AppThemeMode = AppThemeMode.DARK,
  val preferredQuality: Int = 1080,
  val autoPlayNextEpisode: Boolean = true,
  val autoLoadSubtitles: Boolean = true,
  val defaultPlaybackSpeed: Float = 1.0f,
  val allowMeteredDownloads: Boolean = true,
  val includeSubtitlesInBatch: Boolean = true,
  val storageLocation: DownloadStorageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
  val enableDownloadNotifications: Boolean = true,
  val enableReleaseNotifications: Boolean = true,
  val enableTrendingNotifications: Boolean = true,
  val lastNotifiedReleaseTag: String = "",
  val githubRepo: String = DEFAULT_GITHUB_REPO,
  val skippedSignIn: Boolean = false,
  val autoCheckUpdates: Boolean = true,
  val lastUpdateCheckMs: Long = 0L,
  val dismissedReleaseTag: String = "",
) {
  companion object {
    const val DEFAULT_GITHUB_REPO = "lennymuriuki/decan-movir"
    private const val LEGACY_GITHUB_REPO = "lennymuriuki4/decan-movir"
    const val UPDATE_CHECK_COOLDOWN_MS = 6L * 3600L * 1000L

    internal fun normalizeRepoSlug(raw: String?): String {
      val clean = raw?.trim().orEmpty().removePrefix("https://github.com/").trim('/')
      return when {
        clean.isBlank() -> DEFAULT_GITHUB_REPO
        clean.equals(LEGACY_GITHUB_REPO, ignoreCase = true) -> DEFAULT_GITHUB_REPO
        else -> clean
      }
    }
  }
}

class AppSettingsRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("decan_movie_settings", Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(readSettings())
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private fun readSettings(): AppSettings {
    val themeName = prefs.getString(KEY_THEME, AppThemeMode.DARK.name)
    val theme = AppThemeMode.entries.firstOrNull { it.name == themeName } ?: AppThemeMode.DARK
    val storageName = prefs.getString(KEY_STORAGE_LOCATION, DownloadStorageLocation.PUBLIC_DOWNLOADS.name)
    val storage =
      DownloadStorageLocation.entries.firstOrNull { it.name == storageName }
        ?: DownloadStorageLocation.PUBLIC_DOWNLOADS
    val storedRepo = prefs.getString(KEY_GITHUB_REPO, null)
    val normalizedRepo = AppSettings.normalizeRepoSlug(storedRepo)
    if (storedRepo != normalizedRepo) {
      prefs.edit().putString(KEY_GITHUB_REPO, normalizedRepo).apply()
    }
    return AppSettings(
      themeMode = theme,
      preferredQuality = prefs.getInt(KEY_QUALITY, 1080),
      autoPlayNextEpisode = prefs.getBoolean(KEY_AUTO_NEXT, true),
      autoLoadSubtitles = prefs.getBoolean(KEY_AUTO_SUBS, true),
      defaultPlaybackSpeed = prefs.getFloat(KEY_SPEED, 1.0f),
      allowMeteredDownloads = prefs.getBoolean(KEY_METERED_DL, true),
      includeSubtitlesInBatch = prefs.getBoolean(KEY_BATCH_SUBS, true),
      storageLocation = storage,
      enableDownloadNotifications = prefs.getBoolean(KEY_NOTIF_DOWNLOADS, true),
      enableReleaseNotifications = prefs.getBoolean(KEY_NOTIF_RELEASES, true),
      enableTrendingNotifications = prefs.getBoolean(KEY_NOTIF_TRENDING, true),
      lastNotifiedReleaseTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, "").orEmpty(),
      githubRepo = normalizedRepo,
      skippedSignIn = prefs.getBoolean(KEY_SKIPPED_SIGN_IN, false),
      autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true),
      lastUpdateCheckMs = prefs.getLong(KEY_LAST_UPDATE_CHECK_MS, 0L),
      dismissedReleaseTag = prefs.getString(KEY_DISMISSED_RELEASE_TAG, "").orEmpty(),
    )
  }

  fun setThemeMode(mode: AppThemeMode) {
    prefs.edit().putString(KEY_THEME, mode.name).apply()
    _settings.update { it.copy(themeMode = mode) }
  }

  fun setPreferredQuality(quality: Int) {
    prefs.edit().putInt(KEY_QUALITY, quality).apply()
    _settings.update { it.copy(preferredQuality = quality) }
  }

  fun setAutoPlayNextEpisode(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_NEXT, enabled).apply()
    _settings.update { it.copy(autoPlayNextEpisode = enabled) }
  }

  fun setAutoLoadSubtitles(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_SUBS, enabled).apply()
    _settings.update { it.copy(autoLoadSubtitles = enabled) }
  }

  fun setDefaultPlaybackSpeed(speed: Float) {
    prefs.edit().putFloat(KEY_SPEED, speed).apply()
    _settings.update { it.copy(defaultPlaybackSpeed = speed) }
  }

  fun setAllowMeteredDownloads(allowed: Boolean) {
    prefs.edit().putBoolean(KEY_METERED_DL, allowed).apply()
    _settings.update { it.copy(allowMeteredDownloads = allowed) }
  }

  fun setIncludeSubtitlesInBatch(include: Boolean) {
    prefs.edit().putBoolean(KEY_BATCH_SUBS, include).apply()
    _settings.update { it.copy(includeSubtitlesInBatch = include) }
  }

  fun setStorageLocation(location: DownloadStorageLocation) {
    prefs.edit().putString(KEY_STORAGE_LOCATION, location.name).apply()
    _settings.update { it.copy(storageLocation = location) }
  }

  fun setEnableDownloadNotifications(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_NOTIF_DOWNLOADS, enabled).apply()
    _settings.update { it.copy(enableDownloadNotifications = enabled) }
  }

  fun setEnableReleaseNotifications(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_NOTIF_RELEASES, enabled).apply()
    _settings.update { it.copy(enableReleaseNotifications = enabled) }
  }

  fun setEnableTrendingNotifications(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_NOTIF_TRENDING, enabled).apply()
    _settings.update { it.copy(enableTrendingNotifications = enabled) }
  }

  fun markReleaseTagNotified(tagName: String) {
    prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, tagName).apply()
    _settings.update { it.copy(lastNotifiedReleaseTag = tagName) }
  }

  fun setGithubRepo(repo: String) {
    val finalRepo = AppSettings.normalizeRepoSlug(repo)
    prefs.edit().putString(KEY_GITHUB_REPO, finalRepo).apply()
    _settings.update { it.copy(githubRepo = finalRepo) }
  }

  fun setSkippedSignIn(skipped: Boolean) {
    prefs.edit().putBoolean(KEY_SKIPPED_SIGN_IN, skipped).apply()
    _settings.update { it.copy(skippedSignIn = skipped) }
  }

  fun setAutoCheckUpdates(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
    _settings.update { it.copy(autoCheckUpdates = enabled) }
  }

  fun markUpdateCheckedNow(nowMs: Long = System.currentTimeMillis()) {
    prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_MS, nowMs).apply()
    _settings.update { it.copy(lastUpdateCheckMs = nowMs) }
  }

  fun shouldAutoCheckUpdates(
    nowMs: Long = System.currentTimeMillis(),
    cooldownMs: Long = AppSettings.UPDATE_CHECK_COOLDOWN_MS,
  ): Boolean {
    val cur = _settings.value
    if (!cur.autoCheckUpdates) return false
    if (cur.lastUpdateCheckMs <= 0L) return true
    return (nowMs - cur.lastUpdateCheckMs) >= cooldownMs
  }

  fun dismissReleaseDialogForTag(tagName: String) {
    prefs.edit().putString(KEY_DISMISSED_RELEASE_TAG, tagName).apply()
    _settings.update { it.copy(dismissedReleaseTag = tagName) }
  }

  fun saveCachedReleaseJson(json: String) {
    prefs.edit().putString(KEY_CACHED_RELEASE_JSON, json).apply()
  }

  fun getCachedReleaseJson(): String? =
    prefs.getString(KEY_CACHED_RELEASE_JSON, null)?.takeIf { it.isNotBlank() }

  private companion object {
    const val KEY_THEME = "theme_mode"
    const val KEY_QUALITY = "preferred_quality"
    const val KEY_AUTO_NEXT = "auto_play_next"
    const val KEY_AUTO_SUBS = "auto_load_subs"
    const val KEY_SPEED = "default_speed"
    const val KEY_METERED_DL = "allow_metered_dl"
    const val KEY_BATCH_SUBS = "include_batch_subs"
    const val KEY_STORAGE_LOCATION = "download_storage_location"
    const val KEY_NOTIF_DOWNLOADS = "notif_downloads"
    const val KEY_NOTIF_RELEASES = "notif_releases"
    const val KEY_NOTIF_TRENDING = "notif_trending"
    const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    const val KEY_GITHUB_REPO = "github_repo"
    const val KEY_SKIPPED_SIGN_IN = "skipped_sign_in"
    const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
    const val KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms"
    const val KEY_DISMISSED_RELEASE_TAG = "dismissed_release_tag"
    const val KEY_CACHED_RELEASE_JSON = "cached_release_json"
  }
}
