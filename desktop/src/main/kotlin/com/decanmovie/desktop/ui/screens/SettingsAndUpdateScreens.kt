package com.decanmovie.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.decanmovie.desktop.data.remote.DesktopGitHubUpdateRepository
import com.decanmovie.desktop.data.remote.WindowsReleaseAsset
import com.decanmovie.desktop.model.DesktopSettings
import com.decanmovie.desktop.platform.WindowsSystemHelper
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import com.decanmovie.desktop.viewmodel.DesktopUpdateState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
  settings: DesktopSettings,
  updateState: DesktopUpdateState,
  onUpdateSettings: ((DesktopSettings) -> DesktopSettings) -> Unit,
  onChooseDownloadFolder: () -> Unit,
  onCheckUpdates: () -> Unit,
  onDownloadUpdate: (WindowsReleaseAsset?, String, String) -> Unit,
  onInstallUpdate: () -> Unit,
  onOpenUpdateDialog: () -> Unit,
) {
  val latest = updateState.latestRelease
  val selectedAsset =
    latest?.let { DesktopGitHubUpdateRepository.selectWindowsAsset(it.assets, settings.preferPortableUpdates) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("settings_screen"),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    item {
      Text("Settings & Application Updates", style = MaterialTheme.typography.headlineMedium, color = DecanDesktopColors.Mist)
    }

    // Application Updates Card
    item {
      Surface(
        color = DecanDesktopColors.SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (updateState.hasUpdate) DecanDesktopColors.Crimson else DecanDesktopColors.Outline),
        modifier = Modifier.fillMaxWidth().testTag("settings_updates_card"),
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, tint = DecanDesktopColors.CrimsonBright)
                Text("Decan Movie Windows Updater", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
              }
              Text(
                text = "Current Installed Version: v${updateState.currentVersion} • Repository: ${settings.githubRepo}",
                style = MaterialTheme.typography.bodySmall,
                color = DecanDesktopColors.Slate,
              )
              if (settings.lastUpdateCheckMs > 0L) {
                val formatted = remember(settings.lastUpdateCheckMs) {
                  SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(settings.lastUpdateCheckMs))
                }
                Text("Last checked: $formatted", style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.Slate)
              }
            }

            Button(
              onClick = onCheckUpdates,
              enabled = !updateState.isChecking,
              colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
              modifier = Modifier.testTag("check_updates_button"),
            ) {
              if (updateState.isChecking) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking…")
              } else {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Check for Updates")
              }
            }
          }

          if (latest != null) {
            Surface(
              color = DecanDesktopColors.Surface,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, DecanDesktopColors.Outline),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                  Column {
                    Text(
                      text = if (latest.isNewerThanCurrent) "New Windows Release: ${latest.tagName}" else "Up to Date (${latest.tagName})",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = if (latest.isNewerThanCurrent) DecanDesktopColors.CrimsonBright else DecanDesktopColors.EmeraldOk,
                    )
                    if (selectedAsset != null) {
                      Text(
                        text = "Windows Package: ${selectedAsset.name}" +
                          (if (selectedAsset.sizeMb > 0) " (${selectedAsset.sizeMb} MB)" else "") +
                          (if (!selectedAsset.sha256.isNullOrBlank()) " • SHA-256 verified" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = DecanDesktopColors.Slate,
                      )
                    }
                  }
                  TextButton(onClick = onOpenUpdateDialog) { Text("View Release Notes") }
                }

                if (updateState.isDownloading) {
                  LinearProgressIndicator(
                    progress = { updateState.downloadProgress },
                    color = DecanDesktopColors.Crimson,
                    trackColor = DecanDesktopColors.Obsidian,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                  )
                  Text("Downloading Windows update (${(updateState.downloadProgress * 100).toInt()}%)…", style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.Mist)
                } else if (updateState.downloadedFilePath != null && updateState.downloadedTag == latest.tagName) {
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DecanDesktopColors.EmeraldOk, modifier = Modifier.size(18.dp))
                    Text(
                      text = if (updateState.isChecksumVerified) "Verified SHA-256 & ready to install" else "Downloaded & ready to install",
                      style = MaterialTheme.typography.bodySmall,
                      color = DecanDesktopColors.EmeraldOk,
                      modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onInstallUpdate, colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.EmeraldOk)) {
                      Text("Install Update", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                  }
                } else if (latest.isNewerThanCurrent) {
                  Button(
                    onClick = { onDownloadUpdate(selectedAsset, latest.htmlUrl, latest.tagName) },
                    colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
                  ) {
                    Text(if (selectedAsset != null) "Download ${selectedAsset.name}" else "Open GitHub Release")
                  }
                }
              }
            }
          }

          if (!updateState.errorMessage.isNullOrBlank()) {
            Text(updateState.errorMessage, style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.CrimsonBright)
          }
          if (!updateState.downloadError.isNullOrBlank()) {
            Text(updateState.downloadError, style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.CrimsonBright)
          }

          SettingsToggleRow(
            title = "Automatically check GitHub Releases for Windows updates",
            subtitle = "Checks https://github.com/${settings.githubRepo}/releases every 6 hours",
            checked = settings.autoCheckUpdates,
            onCheckedChange = { v -> onUpdateSettings { it.copy(autoCheckUpdates = v) } },
          )
          SettingsToggleRow(
            title = "Prefer Portable ZIP updates instead of Windows Installer (.exe/.msi)",
            subtitle = "Recommended if you run Decan Movie Portable from USB or custom directory",
            checked = settings.preferPortableUpdates,
            onCheckedChange = { v -> onUpdateSettings { it.copy(preferPortableUpdates = v) } },
          )
        }
      }
    }

    // Download Directory Card
    item {
      Surface(
        color = DecanDesktopColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DecanDesktopColors.Outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Downloads & Subtitles Folder", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
          Text(settings.downloadDirectory, style = MaterialTheme.typography.bodyMedium, color = DecanDesktopColors.Slate)
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onChooseDownloadFolder, colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson)) {
              Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Change Folder…")
            }
            OutlinedButton(
              onClick = { WindowsSystemHelper.openFolderInExplorer(settings.downloadDirectory) },
              border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
            ) {
              Text("Open in Explorer")
            }
          }
          SettingsToggleRow(
            title = "Download matching .srt subtitles with videos",
            subtitle = "Saves English/default .srt captions beside episode video files",
            checked = settings.includeSubtitlesInDownloads,
            onCheckedChange = { v -> onUpdateSettings { it.copy(includeSubtitlesInDownloads = v) } },
          )
        }
      }
    }

    // Playback Preferences
    item {
      Surface(
        color = DecanDesktopColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DecanDesktopColors.Outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Playback & Quality", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
          Text("Preferred Stream Resolution", style = MaterialTheme.typography.labelMedium, color = DecanDesktopColors.Slate)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(480, 720, 1080).forEach { q ->
              FilterPillChip(
                label = "${q}p",
                selected = settings.preferredQuality == q,
                onClick = { onUpdateSettings { it.copy(preferredQuality = q) } },
              )
            }
          }
          Text("Default Playback Speed", style = MaterialTheme.typography.labelMedium, color = DecanDesktopColors.Slate)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.0f, 1.25f, 1.5f).forEach { spd ->
              FilterPillChip(
                label = "${spd}x",
                selected = settings.defaultPlaybackSpeed == spd,
                accentSky = true,
                onClick = { onUpdateSettings { it.copy(defaultPlaybackSpeed = spd) } },
              )
            }
          }
          SettingsToggleRow(
            title = "Auto-play next episode",
            subtitle = "Automatically advances to the next episode when playback ends",
            checked = settings.autoPlayNextEpisode,
            onCheckedChange = { v -> onUpdateSettings { it.copy(autoPlayNextEpisode = v) } },
          )
          SettingsToggleRow(
            title = "Auto-load subtitles during playback",
            subtitle = "Enables CC captions by default when available",
            checked = settings.autoLoadSubtitles,
            onCheckedChange = { v -> onUpdateSettings { it.copy(autoLoadSubtitles = v) } },
          )
        }
      }
    }

    // Windows Desktop Integration
    item {
      Surface(
        color = DecanDesktopColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DecanDesktopColors.Outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Windows Desktop Integration", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
          SettingsToggleRow(
            title = "Launch Decan Movie at Windows startup",
            subtitle = "Adds/removes HKCU Windows Run entry (no admin privileges required)",
            checked = settings.launchAtStartup,
            onCheckedChange = { v ->
              WindowsSystemHelper.setWindowsStartupEnabled(v)
              onUpdateSettings { it.copy(launchAtStartup = v) }
            },
          )
          SettingsToggleRow(
            title = "Minimize to Windows System Tray on close",
            subtitle = "Keep background downloads and updates active in the system tray",
            checked = settings.minimizeToTrayOnClose,
            onCheckedChange = { v -> onUpdateSettings { it.copy(minimizeToTrayOnClose = v) } },
          )
          SettingsToggleRow(
            title = "Show Windows desktop notifications",
            subtitle = "Alerts when downloads finish or new GitHub updates are published",
            checked = settings.enableDesktopNotifications,
            onCheckedChange = { v -> onUpdateSettings { it.copy(enableDesktopNotifications = v) } },
          )
          SettingsToggleRow(
            title = "Compact movie poster grid",
            subtitle = "Display more titles per row on widescreen monitors",
            checked = settings.compactCards,
            onCheckedChange = { v -> onUpdateSettings { it.copy(compactCards = v) } },
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = DecanDesktopColors.Mist)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.Slate)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DecanDesktopColors.Crimson),
    )
  }
}

@Composable
fun UpdateAvailableDialog(
  updateState: DesktopUpdateState,
  preferPortable: Boolean,
  onDismiss: () -> Unit,
  onDownload: (WindowsReleaseAsset?, String, String) -> Unit,
  onInstall: () -> Unit,
) {
  val release = updateState.latestRelease ?: return
  val windowsAssets = remember(release.assets) { release.assets.filter { it.isWindowsDesktopPackage } }
  val defaultSelected =
    remember(windowsAssets, preferPortable) {
      DesktopGitHubUpdateRepository.selectWindowsAsset(release.assets, preferPortable)
    }
  var chosenAsset by remember(release.tagName, defaultSelected) { mutableStateOf(defaultSelected) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = DecanDesktopColors.SurfaceElevated,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, tint = DecanDesktopColors.CrimsonBright)
        Column {
          Text("Decan Movie ${release.tagName} Available", color = DecanDesktopColors.Mist)
          Text(
            text = "Installed: v${updateState.currentVersion} → New: ${release.tagName}",
            style = MaterialTheme.typography.bodySmall,
            color = DecanDesktopColors.Slate,
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.width(480.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (windowsAssets.isNotEmpty()) {
          Text("Select Windows Package", style = MaterialTheme.typography.labelMedium, color = DecanDesktopColors.Slate)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            windowsAssets.forEach { asset ->
              val selected = chosenAsset?.name == asset.name
              Surface(
                color = if (selected) DecanDesktopColors.CrimsonContainer else DecanDesktopColors.Surface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (selected) DecanDesktopColors.CrimsonBright else DecanDesktopColors.Outline),
                onClick = { chosenAsset = asset },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(asset.name, style = MaterialTheme.typography.titleSmall, color = DecanDesktopColors.Mist)
                    val meta = buildString {
                      if (asset.sizeMb > 0) append("${asset.sizeMb} MB")
                      if (!asset.sha256.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("SHA-256 available")
                      }
                    }
                    if (meta.isNotBlank()) {
                      Text(meta, style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.Slate)
                    }
                  }
                  if (!asset.sha256.isNullOrBlank()) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = "Verified", tint = DecanDesktopColors.EmeraldOk, modifier = Modifier.size(18.dp))
                  }
                }
              }
            }
          }
        }

        Surface(
          color = DecanDesktopColors.Surface,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          modifier = Modifier.fillMaxWidth().height(150.dp),
        ) {
          Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            Text(
              text = release.changelog,
              style = MaterialTheme.typography.bodySmall,
              color = DecanDesktopColors.Mist,
            )
          }
        }

        if (updateState.isDownloading) {
          LinearProgressIndicator(
            progress = { updateState.downloadProgress },
            color = DecanDesktopColors.Crimson,
            trackColor = DecanDesktopColors.Obsidian,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
          )
          Text("Downloading ${(updateState.downloadProgress * 100).toInt()}%…", style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.Slate)
        }

        if (!updateState.downloadError.isNullOrBlank()) {
          Text(updateState.downloadError, style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.CrimsonBright)
        }
      }
    },
    confirmButton = {
      when {
        updateState.downloadedFilePath != null && updateState.downloadedTag == release.tagName -> {
          Button(onClick = onInstall, colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.EmeraldOk)) {
            Text("Install Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
          }
        }
        chosenAsset != null -> {
          Button(
            onClick = { onDownload(chosenAsset, release.htmlUrl, release.tagName) },
            enabled = !updateState.isDownloading,
            colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
            modifier = Modifier.testTag("update_dialog_download_btn"),
          ) {
            Text(if (updateState.isDownloading) "Downloading…" else "Download Update")
          }
        }
        else -> {
          Button(
            onClick = { WindowsSystemHelper.openExternalUrl(release.htmlUrl) },
            colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
          ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("View on GitHub")
          }
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Later") }
    },
  )
}
