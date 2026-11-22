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

@Immutable
data class AppSettings(
  val themeMode: AppThemeMode = AppThemeMode.DARK,
  val preferredQuality: Int = 1080,
  val autoPlayNextEpisode: Boolean = true,
  val autoLoadSubtitles: Boolean = true,
  val defaultPlaybackSpeed: Float = 1.0f,
  val allowMeteredDownloads: Boolean = true,
  val includeSubtitlesInBatch: Boolean = true,
  val enableDownloadNotifications: Boolean = true,
  val enableReleaseNotifications: Boolean = true,
  val enableTrendingNotifications: Boolean = true,
  val lastNotifiedReleaseTag: String = "",
  val githubRepo: String = DEFAULT_GITHUB_REPO,
) {
  companion object {
    const val DEFAULT_GITHUB_REPO = "lennymuriuki4/decan-movir"
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
    return AppSettings(
      themeMode = theme,
      preferredQuality = prefs.getInt(KEY_QUALITY, 1080),
      autoPlayNextEpisode = prefs.getBoolean(KEY_AUTO_NEXT, true),
      autoLoadSubtitles = prefs.getBoolean(KEY_AUTO_SUBS, true),
      defaultPlaybackSpeed = prefs.getFloat(KEY_SPEED, 1.0f),
      allowMeteredDownloads = prefs.getBoolean(KEY_METERED_DL, true),
      includeSubtitlesInBatch = prefs.getBoolean(KEY_BATCH_SUBS, true),
      enableDownloadNotifications = prefs.getBoolean(KEY_NOTIF_DOWNLOADS, true),
      enableReleaseNotifications = prefs.getBoolean(KEY_NOTIF_RELEASES, true),
      enableTrendingNotifications = prefs.getBoolean(KEY_NOTIF_TRENDING, true),
      lastNotifiedReleaseTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, "").orEmpty(),
      githubRepo =
        prefs.getString(KEY_GITHUB_REPO, AppSettings.DEFAULT_GITHUB_REPO)
          ?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_GITHUB_REPO,
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
    val clean = repo.trim().removePrefix("https://github.com/").trim('/')
    val finalRepo = clean.ifBlank { AppSettings.DEFAULT_GITHUB_REPO }
    prefs.edit().putString(KEY_GITHUB_REPO, finalRepo).apply()
    _settings.update { it.copy(githubRepo = finalRepo) }
  }

  private companion object {
    const val KEY_THEME = "theme_mode"
    const val KEY_QUALITY = "preferred_quality"
    const val KEY_AUTO_NEXT = "auto_play_next"
    const val KEY_AUTO_SUBS = "auto_load_subs"
    const val KEY_SPEED = "default_speed"
    const val KEY_METERED_DL = "allow_metered_dl"
    const val KEY_BATCH_SUBS = "include_batch_subs"
    const val KEY_NOTIF_DOWNLOADS = "notif_downloads"
    const val KEY_NOTIF_RELEASES = "notif_releases"
    const val KEY_NOTIF_TRENDING = "notif_trending"
    const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    const val KEY_GITHUB_REPO = "github_repo"
  }
}
