package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.auth.GoogleSignInButton
import com.example.data.remote.GitHubReleaseAsset
import com.example.data.remote.GitHubUpdateRepository
import com.example.data.settings.AppSettings
import com.example.data.settings.AppThemeMode
import com.example.data.settings.DownloadStorageLocation
import com.example.notifications.DecanNotificationManager
import com.example.ui.viewmodel.UpdateUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  appSettings: AppSettings,
  updateState: UpdateUiState,
  isFirebaseConfigured: Boolean,
  isSignedIn: Boolean,
  userDisplayName: String?,
  userEmail: String?,
  userPhotoUrl: String?,
  onSignOut: (() -> Unit)?,
  onAuthSuccess: () -> Unit,
  onSetThemeMode: (AppThemeMode) -> Unit,
  onSetPreferredQuality: (Int) -> Unit,
  onSetAutoPlayNext: (Boolean) -> Unit,
  onSetAutoLoadSubtitles: (Boolean) -> Unit,
  onSetDefaultPlaybackSpeed: (Float) -> Unit,
  onSetAllowMeteredDownloads: (Boolean) -> Unit,
  onSetIncludeSubtitlesInBatch: (Boolean) -> Unit,
  onSetStorageLocation: (DownloadStorageLocation) -> Unit = {},
  onSetEnableDownloadNotifications: (Boolean) -> Unit,
  onSetEnableReleaseNotifications: (Boolean) -> Unit,
  onSetEnableTrendingNotifications: (Boolean) -> Unit,
  onSetAutoCheckUpdates: (Boolean) -> Unit = {},
  onSendTestNotification: () -> Unit,
  onCheckUpdates: () -> Unit,
  onDownloadInstallUpdate: (asset: GitHubReleaseAsset?, fallbackUrl: String, tag: String) -> Unit = { _, _, _ -> },
  onInstallDownloadedApk: (String) -> Unit = {},
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasNotifPerm by remember {
    mutableStateOf(DecanNotificationManager.hasPostPermission(context))
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotifPerm = granted
      onNotify(
        if (granted) "System notifications enabled!"
        else "Notifications denied — alerts will still appear in the in-app bell."
      )
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("settings_list"),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Column {
        Text(
          text = "Settings & Account",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "Decan Movie v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Account Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            if (!userPhotoUrl.isNullOrBlank()) {
              AsyncImage(
                model = userPhotoUrl,
                contentDescription = userDisplayName ?: "Account avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(50.dp).clip(CircleShape),
              )
            } else {
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(50.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector =
                      if (isSignedIn) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  when {
                    isSignedIn -> userDisplayName?.ifBlank { "Google Account" } ?: "Signed In"
                    else -> "Guest Mode"
                  },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  when {
                    isSignedIn ->
                      userEmail?.ifBlank { "Cloud Watchlist Sync Active" }
                        ?: "Cloud Watchlist Sync Active"
                    isFirebaseConfigured ->
                      "Local watchlist, streaming & downloads active. Sign in anytime (optional) to sync."
                    else ->
                      "Local watchlist, streaming & offline downloads are active on this device."
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (isSignedIn && onSignOut != null) {
            OutlinedButton(
              onClick = onSignOut,
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign Out of Google Account")
            }
          } else if (isFirebaseConfigured) {
            GoogleSignInButton(
              onAuthSuccess = onAuthSuccess,
              onAuthError = { msg -> onNotify(msg) },
              modifier = Modifier.testTag("settings_sign_in_button"),
            )
          }
        }
      }
    }

    // Notifications Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Notifications & Alerts",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
            FilledTonalButton(
              onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth().testTag("grant_notifications_button"),
            ) {
              Text("Enable Android System Notifications")
            }
          }

          SettingToggleRow(
            title = "Download & Batch Alerts",
            subtitle = "Notify when queued movies, episodes, and .srt subtitles complete",
            checked = appSettings.enableDownloadNotifications,
            onCheckedChange = onSetEnableDownloadNotifications,
          )
          SettingToggleRow(
            title = "App Update Alerts",
            subtitle = "Notify whenever a newer version of Decan Movie is available",
            checked = appSettings.enableReleaseNotifications,
            onCheckedChange = onSetEnableReleaseNotifications,
          )
          SettingToggleRow(
            title = "Trending Cinema Picks",
            subtitle = "Receive spotlight highlights from Top 10 rankings & anime drops",
            checked = appSettings.enableTrendingNotifications,
            onCheckedChange = onSetEnableTrendingNotifications,
          )

          OutlinedButton(
            onClick = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                onSendTestNotification()
              }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("test_notification_button"),
          ) {
            Text(
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm)
                "Grant Permission & Test Notification"
              else "Send Test Cinema Alert"
            )
          }
        }
      }
    }

    // Application Updates Card
    item {
      var showFullReleaseNotes by remember { mutableStateOf(false) }
      val effectiveLastCheckMs =
        maxOf(updateState.lastCheckedAtMs, appSettings.lastUpdateCheckMs)
      val formattedLastCheck =
        remember(effectiveLastCheckMs) { formatUpdateTimestamp(effectiveLastCheckMs) }

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("application_updates_card"),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.SystemUpdate,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Application Updates",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Installed version",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "v${BuildConfig.VERSION_NAME}",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.testTag("settings_installed_version"),
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Latest available",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    when {
                      updateState.isChecking -> "Checking…"
                      updateState.latestRelease != null -> updateState.latestRelease.tagName
                      else -> "Not checked yet"
                    },
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color =
                    if (updateState.hasUpdate) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.testTag("settings_latest_version"),
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Last update check",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = formattedLastCheck,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.testTag("settings_last_update_check"),
                )
              }
            }
          }

          SettingToggleRow(
            title = "Automatic Update Checking",
            subtitle = "Check GitHub releases automatically when opening Decan Movie",
            checked = appSettings.autoCheckUpdates,
            onCheckedChange = onSetAutoCheckUpdates,
            toggleTestTag = "auto_check_updates_switch",
          )

          Button(
            onClick = onCheckUpdates,
            enabled = !updateState.isChecking,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("check_updates_button"),
          ) {
            if (updateState.isChecking) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Checking for Updates…")
            } else {
              Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Check for Updates")
            }
          }

          val latest = updateState.latestRelease
          if (latest != null) {
            val preferredAsset =
              GitHubUpdateRepository.selectCompatibleApkAsset(
                assets = latest.assets,
                isDebugBuild = BuildConfig.DEBUG,
              )
            val isDownloadedForLatest =
              !updateState.downloadedApkPath.isNullOrBlank() &&
                updateState.downloadedReleaseTag == latest.tagName

            Surface(
              color =
                if (latest.isNewerThanCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                  Text(
                    text =
                      if (latest.isNewerThanCurrent) "Update Available: ${latest.tagName}"
                      else "You're up to date (${latest.tagName})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }

                if (latest.changelog.isNotBlank()) {
                  Text(
                    text =
                      if (showFullReleaseNotes) latest.changelog
                      else latest.changelog.take(260),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                if (preferredAsset != null) {
                  val sizeText =
                    if (preferredAsset.sizeMb > 0.0) " (${preferredAsset.sizeMb} MB)" else ""
                  val shaBadge =
                    if (!preferredAsset.sha256.isNullOrBlank()) " • SHA-256 Verified" else ""
                  Text(
                    text = "Package: ${preferredAsset.name}$sizeText$shaBadge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                if (updateState.isDownloadingApk) {
                  LinearProgressIndicator(
                    progress = { updateState.downloadProgress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                  )
                  Text(
                    text =
                      "Downloading update (${(updateState.downloadProgress * 100).toInt()}%)…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                } else if (isDownloadedForLatest && !updateState.downloadedApkPath.isNullOrBlank()) {
                  Button(
                    onClick = { onInstallDownloadedApk(updateState.downloadedApkPath) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("install_downloaded_apk_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.SystemUpdate,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install Update (${latest.tagName})")
                  }
                } else if (latest.isNewerThanCurrent) {
                  FilledTonalButton(
                    onClick = {
                      if (preferredAsset != null) {
                        onDownloadInstallUpdate(preferredAsset, latest.htmlUrl, latest.tagName)
                      } else {
                        runCatching {
                          context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl))
                          )
                        }.onFailure { onNotify("Could not open update page") }
                      }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("download_latest_update_button"),
                  ) {
                    Icon(
                      imageVector =
                        if (preferredAsset != null) Icons.Filled.SystemUpdate
                        else Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      if (preferredAsset != null) "Download Update"
                      else "View Update Details"
                    )
                  }
                }

                if (!updateState.downloadErrorMessage.isNullOrBlank()) {
                  Text(
                    text = updateState.downloadErrorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  if (latest.changelog.length > 260) {
                    OutlinedButton(
                      onClick = { showFullReleaseNotes = !showFullReleaseNotes },
                      shape = MaterialTheme.shapes.medium,
                      modifier = Modifier.weight(1f),
                    ) {
                      Text(if (showFullReleaseNotes) "Show Less" else "Release Notes")
                    }
                  }
                  if (latest.htmlUrl.isNotBlank()) {
                    OutlinedButton(
                      onClick = {
                        runCatching {
                          context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl))
                          )
                        }.onFailure { onNotify("Could not open GitHub release") }
                      },
                      shape = MaterialTheme.shapes.medium,
                      modifier = Modifier.weight(1f).testTag("settings_view_release_notes_button"),
                    ) {
                      Text("View Release Notes")
                    }
                  }
                }
              }
            }
          } else if (updateState.checkedOnce && updateState.errorMessage == null) {
            Text(
              text = "No new updates available right now.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else if (updateState.errorMessage != null) {
            Text(
              text = updateState.errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (updateState.recentReleases.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
              text = "Version History",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            updateState.recentReleases.take(4).forEach { rel ->
              val relApkUrl =
                GitHubUpdateRepository.selectCompatibleApkAsset(
                  assets = rel.assets,
                  isDebugBuild = BuildConfig.DEBUG,
                )?.downloadUrl
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      runCatching {
                        context.startActivity(
                          Intent(Intent.ACTION_VIEW, Uri.parse(relApkUrl ?: rel.htmlUrl))
                        )
                      }
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "• ${rel.tagName} — ${rel.title}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                  contentDescription = "Open version",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }
    }

    // Playback & Theater Settings Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Theater Playback & Downloads",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          Text(
            text = "Preferred Stream Resolution",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1080, 720, 480).forEach { res ->
              FilterChip(
                selected = appSettings.preferredQuality == res,
                onClick = { onSetPreferredQuality(res) },
                label = { Text("${res}p") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                  ),
              )
            }
          }

          Text(
            text = "Default Playback Speed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
              FilterChip(
                selected = appSettings.defaultPlaybackSpeed == spd,
                onClick = { onSetDefaultPlaybackSpeed(spd) },
                label = { Text("${spd}x") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  ),
              )
            }
          }

          SettingToggleRow(
            title = "Auto-Play Next Episode",
            subtitle = "Advance automatically with countdown when a TV or anime episode finishes",
            checked = appSettings.autoPlayNextEpisode,
            onCheckedChange = onSetAutoPlayNext,
          )
          SettingToggleRow(
            title = "Auto-Load English Subtitles (CC)",
            subtitle = "Automatically fetch synchronized .srt captions in the Theater Player",
            checked = appSettings.autoLoadSubtitles,
            onCheckedChange = onSetAutoLoadSubtitles,
          )
          SettingToggleRow(
            title = "Allow Metered / Mobile Downloads",
            subtitle = "Permit batch video & subtitle downloads over cellular data",
            checked = appSettings.allowMeteredDownloads,
            onCheckedChange = onSetAllowMeteredDownloads,
          )
          SettingToggleRow(
            title = "Include .srt Subtitles in Batch DL",
            subtitle = "Bundle English subtitles whenever multi-selecting episodes",
            checked = appSettings.includeSubtitlesInBatch,
            onCheckedChange = onSetIncludeSubtitlesInBatch,
          )

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Default Download Folder",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadStorageLocation.entries.forEach { loc ->
              FilterChip(
                selected = appSettings.storageLocation == loc,
                onClick = { onSetStorageLocation(loc) },
                label = { Text(loc.label) },
                modifier = Modifier.testTag("settings_storage_${loc.name}"),
              )
            }
          }
          Text(
            text =
              "Series go into ${appSettings.storageLocation.pathHint}/Series/<Title>/ and movies into ${appSettings.storageLocation.pathHint}/Movies/<Title>/.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Community & Official Links Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("community_links_card"),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Language,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Community & Official Links",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          FilledTonalButton(
            onClick = {
              runCatching {
                context.startActivity(
                  Intent(Intent.ACTION_VIEW, Uri.parse("https://www.decankonnect.co.ke/"))
                )
              }.onFailure { onNotify("Could not open website") }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("link_decankonnect_website"),
          ) {
            Icon(
              imageVector = Icons.Filled.Language,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Main Website — decankonnect.co.ke")
            Spacer(modifier = Modifier.weight(1f))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          }

          OutlinedButton(
            onClick = {
              runCatching {
                context.startActivity(
                  Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://chat.whatsapp.com/GEIVBf7Mx21EBqZn2FOaHF"),
                  )
                )
              }.onFailure { onNotify("Could not open WhatsApp group") }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("link_whatsapp_group"),
          ) {
            Icon(
              imageVector = Icons.Filled.Forum,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Main WhatsApp Group")
            Spacer(modifier = Modifier.weight(1f))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    // Appearance Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Palette,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "App Theme",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = appSettings.themeMode == mode,
                onClick = { onSetThemeMode(mode) },
                label = { Text(mode.label) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.testTag("theme_mode_${mode.name}"),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  toggleTestTag: String? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = if (toggleTestTag != null) Modifier.testTag(toggleTestTag) else Modifier,
    )
  }
}

private fun formatUpdateTimestamp(timestampMs: Long): String {
  if (timestampMs <= 0L) return "Never"
  return runCatching {
    SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(timestampMs))
  }.getOrDefault("Recently")
}
