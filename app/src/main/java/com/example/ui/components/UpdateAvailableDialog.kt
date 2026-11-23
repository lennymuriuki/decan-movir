package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.remote.GitHubReleaseAsset
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateRepository
import com.example.ui.viewmodel.UpdateUiState
import kotlin.math.roundToInt

@Composable
fun UpdateAvailableDialog(
  updateState: UpdateUiState,
  onDownloadUpdate: (asset: GitHubReleaseAsset?, release: GitHubReleaseInfo) -> Unit,
  onInstallUpdate: (path: String) -> Unit,
  onLater: () -> Unit,
  onNotify: (String) -> Unit,
) {
  val release = updateState.latestRelease ?: return
  if (!release.isNewerThanCurrent) return

  val context = LocalContext.current
  val compatibleAsset =
    GitHubUpdateRepository.selectCompatibleApkAsset(
      assets = release.assets,
      isDebugBuild = BuildConfig.DEBUG,
    )
  val isDownloadedForThisRelease =
    !updateState.downloadedApkPath.isNullOrBlank() &&
      updateState.downloadedReleaseTag == release.tagName

  AlertDialog(
    onDismissRequest = onLater,
    modifier = Modifier.testTag("update_available_dialog"),
    icon = {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.size(48.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    title = {
      Text(
        text = "DECAN MOVIE UPDATE AVAILABLE",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onSurface,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Current version",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Latest version",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = release.tagName,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
              )
            }
            if (compatibleAsset != null && compatibleAsset.sizeMb > 0.0) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "APK package",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "${compatibleAsset.sizeMb} MB",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        Text(
          text = "What's new",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp),
        ) {
          Text(
            text = release.changelog.ifBlank { "Performance improvements and bug fixes." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
              Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
          )
        }

        if (updateState.isDownloadingApk) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(
              progress = { updateState.downloadProgress },
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              text =
                "Downloading update (${(updateState.downloadProgress * 100f).roundToInt()}%)…",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        } else if (isDownloadedForThisRelease) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Verified,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text =
                if (updateState.isChecksumVerified) "APK verified (SHA-256) & ready to install"
                else "Update APK downloaded & ready to install",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        } else if (!updateState.downloadErrorMessage.isNullOrBlank()) {
          Text(
            text = updateState.downloadErrorMessage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isDownloadedForThisRelease && !updateState.downloadedApkPath.isNullOrBlank()) {
          Button(
            onClick = { onInstallUpdate(updateState.downloadedApkPath) },
            modifier = Modifier.fillMaxWidth().testTag("dialog_install_update_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.SystemUpdate,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Install Update")
          }
        } else {
          Button(
            onClick = { onDownloadUpdate(compatibleAsset, release) },
            enabled = !updateState.isDownloadingApk,
            modifier = Modifier.fillMaxWidth().testTag("dialog_download_update_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Download,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              if (updateState.isDownloadingApk) "Downloading…"
              else if (compatibleAsset != null) "Download Update"
              else "Open Release Page"
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = {
              val targetUrl =
                release.htmlUrl.ifBlank {
                  "https://github.com/lennymuriuki/decan-movir/releases"
                }
              runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
              }.onFailure { onNotify("Could not open release notes") }
            },
            modifier = Modifier.testTag("dialog_release_notes_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.OpenInNew,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("View Release Notes")
          }

          TextButton(
            onClick = onLater,
            modifier = Modifier.testTag("dialog_later_button"),
          ) {
            Text("Later")
          }
        }
      }
    },
  )
}
