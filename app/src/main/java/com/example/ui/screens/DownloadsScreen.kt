package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.StreamDownloadManager
import kotlinx.coroutines.delay
import java.util.Locale

private enum class DownloadFilter(val label: String) {
  ALL("All"),
  ACTIVE("Active"),
  COMPLETED("Completed"),
}

@Composable
fun DownloadsScreen(
  onPlayLocalFile: (localUri: String, title: String) -> Unit,
  onExploreMovies: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var tasks by remember { mutableStateOf<List<DownloadTaskInfo>>(emptyList()) }
  var filter by remember { mutableStateOf(DownloadFilter.ALL) }
  var selectionMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

  LaunchedEffect(Unit) {
    while (true) {
      tasks = StreamDownloadManager.queryAllDownloads(context)
      delay(1200L)
    }
  }

  val filteredTasks =
    remember(tasks, filter) {
      when (filter) {
        DownloadFilter.ALL -> tasks
        DownloadFilter.ACTIVE -> tasks.filter { it.isActive }
        DownloadFilter.COMPLETED -> tasks.filter { it.isCompleted }
      }
    }

  val activeCount = tasks.count { it.isActive }
  val completedCount = tasks.count { it.isCompleted }
  val downloadedMb = tasks.sumOf { it.bytesDownloaded } / (1024.0 * 1024.0)

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("downloads_list"),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Downloads Hub",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Saved to Downloads/DecanMovie • Offline Theater ready",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (tasks.isNotEmpty()) {
            IconButton(
              onClick = {
                selectionMode = !selectionMode
                if (!selectionMode) selectedIds = emptySet()
              },
              modifier = Modifier.testTag("downloads_select_mode_button"),
            ) {
              Icon(
                imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.Checklist,
                contentDescription = if (selectionMode) "Exit multi-select" else "Multi-select",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
          IconButton(
            onClick = { tasks = StreamDownloadManager.queryAllDownloads(context) },
            modifier = Modifier.testTag("downloads_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Refresh downloads",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }

    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          DownloadStatTile(
            label = "Active",
            value = activeCount.toString(),
            modifier = Modifier.weight(1f),
          )
          DownloadStatTile(
            label = "Completed",
            value = completedCount.toString(),
            modifier = Modifier.weight(1f),
          )
          DownloadStatTile(
            label = "Downloaded",
            value = String.format(Locale.US, "%.1f MB", downloadedMb),
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DownloadFilter.entries.forEach { option ->
            FilterChip(
              selected = filter == option,
              onClick = { filter = option },
              label = { Text(option.label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
          }
        }

        if (selectionMode && filteredTasks.isNotEmpty()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                selectedIds =
                  if (selectedIds.size == filteredTasks.size) emptySet()
                  else filteredTasks.map { it.id }.toSet()
              }
            ) {
              Icon(
                imageVector = Icons.Filled.SelectAll,
                contentDescription = "Select all",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            if (selectedIds.isNotEmpty()) {
              TextButton(
                onClick = {
                  val removed = StreamDownloadManager.removeDownload(context, *selectedIds.toLongArray())
                  selectedIds = emptySet()
                  selectionMode = false
                  tasks = StreamDownloadManager.queryAllDownloads(context)
                  onNotify("Removed $removed download(s)")
                }
              ) {
                Icon(
                  imageVector = Icons.Filled.DeleteSweep,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete (${selectedIds.size})")
              }
            }
          }
        }
      }
    }

    if (filteredTasks.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = CircleShape,
              modifier = Modifier.size(60.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.FolderOpen,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
              }
            }
            Text(
              text = "No Downloads in This View",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Open any movie or series and tap Batch DL to queue multiple episodes, resolutions, and .srt subtitles at once.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onExploreMovies,
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.testTag("downloads_explore_button"),
            ) {
              Text("Browse Catalog")
            }
          }
        }
      }
    } else {
      items(filteredTasks, key = { it.id }) { task ->
        val isSelected = task.id in selectedIds
        DownloadTaskCard(
          task = task,
          selectionMode = selectionMode,
          isSelected = isSelected,
          onToggleSelect = {
            selectedIds =
              if (isSelected) selectedIds - task.id else selectedIds + task.id
          },
          onPlayOffline = {
            val uri = task.localUri
            if (!uri.isNullOrBlank()) {
              onPlayLocalFile(uri, task.title)
            } else {
              onNotify("File URI unavailable for playback")
            }
          },
          onDelete = {
            StreamDownloadManager.removeDownload(context, task.id)
            tasks = StreamDownloadManager.queryAllDownloads(context)
            onNotify("Removed ${task.title}")
          },
        )
      }
    }
  }
}

@Composable
private fun DownloadStatTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(vertical = 10.dp, horizontal = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DownloadTaskCard(
  task: DownloadTaskInfo,
  selectionMode: Boolean,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onPlayOffline: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .clickable {
          if (selectionMode) onToggleSelect()
          else if (task.isCompleted && task.isVideo) onPlayOffline()
        }
        .testTag("download_item_${task.id}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (selectionMode) {
          Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
        } else {
          Surface(
            color =
              if (task.isCompleted) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier.size(38.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector =
                  if (task.isCompleted) Icons.Filled.DownloadDone else Icons.Filled.Downloading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = task.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val sizeSummary =
            if (task.totalBytes > 0L) {
              String.format(
                Locale.US,
                "%.1f / %.1f MB",
                task.bytesDownloaded / (1024.0 * 1024.0),
                task.totalBytes / (1024.0 * 1024.0),
              )
            } else {
              String.format(
                Locale.US,
                "%.1f MB",
                task.bytesDownloaded / (1024.0 * 1024.0),
              )
            }
          Text(
            text = "${task.statusLabel} • $sizeSummary",
            style = MaterialTheme.typography.bodySmall,
            color =
              if (task.isCompleted) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (task.isCompleted && task.isVideo && !task.localUri.isNullOrBlank()) {
            FilledTonalIconButton(
              onClick = onPlayOffline,
              colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier = Modifier.testTag("play_offline_${task.id}"),
            ) {
              Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Play offline")
            }
          } else if (task.isCompleted) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = "Completed",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp).size(20.dp),
            )
          }

          IconButton(onClick = onDelete) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Cancel or remove download",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (task.isActive) {
        if (task.totalBytes > 0L) {
          LinearProgressIndicator(
            progress = { task.progressFraction },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
          )
        } else {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
          )
        }
      }
    }
  }
}
