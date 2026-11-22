package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.NewReleases
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.settings.AppSettings
import com.example.data.settings.AppThemeMode
import com.example.ui.viewmodel.UpdateUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  appSettings: AppSettings,
  updateState: UpdateUiState,
  isFirebaseConfigured: Boolean,
  userDisplayName: String?,
  userEmail: String?,
  userPhotoUrl: String?,
  onSignOut: (() -> Unit)?,
  onSetThemeMode: (AppThemeMode) -> Unit,
  onSetPreferredQuality: (Int) -> Unit,
  onSetAutoPlayNext: (Boolean) -> Unit,
  onSetAutoLoadSubtitles: (Boolean) -> Unit,
  onSetDefaultPlaybackSpeed: (Float) -> Unit,
  onSetAllowMeteredDownloads: (Boolean) -> Unit,
  onSetIncludeSubtitlesInBatch: (Boolean) -> Unit,
  onSaveGithubRepo: (String) -> Unit,
  onCheckUpdates: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var repoInput by remember(appSettings.githubRepo) { mutableStateOf(appSettings.githubRepo) }

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
          text = "Decan Movie v${BuildConfig.VERSION_NAME} • Theater, Downloads & GitHub Releases",
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
                      if (isFirebaseConfigured) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  if (isFirebaseConfigured) (userDisplayName?.ifBlank { "Google Account" } ?: "Signed In")
                  else "Catalog Guest Mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  if (isFirebaseConfigured)
                    (userEmail?.ifBlank { "Firestore Watchlist Sync Active" }
                      ?: "Firestore Watchlist Sync Active")
                  else "Add GOOGLE_SERVICES_JSON in GitHub Secrets to enable Google Sign-In & Cloud Sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (isFirebaseConfigured && onSignOut != null) {
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
          }
        }
      }
    }

    // GitHub Releases & Updater Card
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
              imageVector = Icons.Filled.SystemUpdate,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "GitHub Updates & Releases",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }

          OutlinedTextField(
            value = repoInput,
            onValueChange = { repoInput = it },
            label = { Text("GitHub Repository (owner/repo)") },
            placeholder = { Text("lennymuriuki4/decan-movir") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("github_repo_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = {
                onSaveGithubRepo(repoInput)
                onNotify("Saved GitHub repository")
              },
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.weight(1f),
            ) {
              Text("Save Repo")
            }

            Button(
              onClick = {
                onSaveGithubRepo(repoInput)
                onCheckUpdates()
              },
              enabled = !updateState.isChecking,
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.weight(1.4f).testTag("check_github_updates_button"),
            ) {
              if (updateState.isChecking) {
                CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Checking…")
              } else {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Check Updates")
              }
            }
          }

          val latest = updateState.latestRelease
          if (latest != null) {
            Surface(
              color =
                if (latest.isNewerThanCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                      else "Up to date (${latest.tagName})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }

                if (latest.changelog.isNotBlank()) {
                  Text(
                    text = latest.changelog.take(260),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                val preferredApkUrl =
                  latest.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.downloadUrl
                if (latest.assets.isNotEmpty()) {
                  val assetNames =
                    latest.assets.joinToString(", ") { "${it.name} (${it.sizeMb} MB)" }
                  Text(
                    text = "Release APKs: $assetNames",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }

                FilledTonalButton(
                  onClick = {
                    val targetUrl = preferredApkUrl ?: latest.htmlUrl
                    runCatching {
                      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                    }
                  },
                  shape = MaterialTheme.shapes.medium,
                  modifier = Modifier.fillMaxWidth().testTag("open_github_release_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    if (preferredApkUrl != null) "Download Release APK on GitHub"
                    else "View Release on GitHub"
                  )
                }
              }
            }
          } else if (updateState.errorMessage != null) {
            Text(
              text = "GitHub check: ${updateState.errorMessage}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          }

          if (updateState.recentReleases.size > 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
              text = "Recent Release History",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            updateState.recentReleases.take(3).forEach { rel ->
              Text(
                text = "• ${rel.tagName} — ${rel.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Text(
            text =
              "Publish releases automatically by pushing a tag (git tag v1.1.0 && git push origin v1.1.0) or running the Android Release workflow in GitHub Actions.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
            subtitle = "Advance automatically when a TV or anime episode finishes",
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
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
