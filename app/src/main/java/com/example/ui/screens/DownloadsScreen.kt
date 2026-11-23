package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.download.DownloadFolderSummary
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.StreamDownloadManager
import com.example.data.settings.DownloadStorageLocation
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DownloadFilter(val label: String) {
  FOLDERS("Folders"),
  ALL("All Files"),
  ACTIVE("Active"),
  COMPLETED("Completed"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
  storageLocation: DownloadStorageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
  onChangeStorageLocation: (DownloadStorageLocation) -> Unit = {},
  onPlayLocalFile: (localUri: String, title: String) -> Unit,
  onExploreMovies: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var tasks by remember { mutableStateOf<List<DownloadTaskInfo>>(emptyList()) }
  var filter by remember { mutableStateOf(DownloadFilter.FOLDERS) }
  var openFolderKey by remember { mutableStateOf<String?>(null) }
  var selectionMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

  LaunchedEffect(Unit) {
    while (true) {
      tasks = StreamDownloadManager.queryAllDownloads(context)
      delay(1200L)
    }
  }

  val folders = remember(tasks) { StreamDownloadManager.groupIntoFolders(tasks) }
  val openedFolder =
    remember(folders, openFolderKey) {
      openFolderKey?.let { key -> folders.firstOrNull { it.key == key } }
    }

  BackHandler(enabled = openedFolder != null) {
    openFolderKey = null
    selectionMode = false
    selectedIds = emptySet()
  }

  val displayedTasks =
    remember(tasks, filter, openedFolder) {
      if (openedFolder != null) {
        openedFolder.items
      } else {
        when (filter) {
          DownloadFilter.FOLDERS -> emptyList()
          DownloadFilter.ALL -> tasks
          DownloadFilter.ACTIVE -> tasks.filter { it.isActive }
          DownloadFilter.COMPLETED -> tasks.filter { it.isCompleted }
        }
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
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (openedFolder != null) {
            IconButton(
              onClick = {
                openFolderKey = null
                selectionMode = false
                selectedIds = emptySet()
              },
              modifier = Modifier.testTag("downloads_folder_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to folders",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
          Column {
            Text(
              text = openedFolder?.folderName ?: "Downloads Hub",
              style = MaterialTheme.typography.headlineLarge,
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                if (openedFolder != null) {
                  "${openedFolder.videoCount} video(s) • ${openedFolder.subtitleCount} CC subtitle(s)"
                } else {
                  "Organized folders in ${storageLocation.pathHint} • Auto offline CC"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (tasks.isNotEmpty() && (openedFolder != null || filter != DownloadFilter.FOLDERS)) {
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

    if (openedFolder == null) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              DownloadStatTile(
                label = "Folders",
                value = folders.size.toString(),
                modifier = Modifier.weight(1f),
              )
              DownloadStatTile(
                label = "Active",
                value = activeCount.toString(),
                modifier = Modifier.weight(1f),
              )
              DownloadStatTile(
                label = "Downloaded",
                value = String.format(Locale.US, "%.1f MB", downloadedMb),
                modifier = Modifier.weight(1f),
              )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Save Location",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadStorageLocation.entries.forEach { loc ->
                  FilterChip(
                    selected = storageLocation == loc,
                    onClick = {
                      onChangeStorageLocation(loc)
                      onNotify("New downloads will save to ${loc.pathHint}")
                    },
                    label = { Text(loc.label) },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      ),
                    modifier = Modifier.testTag("storage_loc_${loc.name}"),
                  )
                }
              }
            }
          }
        }
      }

      item {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DownloadFilter.entries.forEach { option ->
            FilterChip(
              selected = filter == option,
              onClick = {
                filter = option
                selectionMode = false
                selectedIds = emptySet()
              },
              label = { Text(option.label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier = Modifier.testTag("download_filter_${option.name}"),
            )
          }
        }
      }
    }

    if (selectionMode && displayedTasks.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = {
              selectedIds =
                if (selectedIds.size == displayedTasks.size) emptySet()
                else displayedTasks.map { it.id }.toSet()
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

    if (openedFolder == null && filter == DownloadFilter.FOLDERS) {
      if (folders.isEmpty()) {
        item {
          EmptyDownloadsCard(onExploreMovies = onExploreMovies)
        }
      } else {
        items(folders, key = { it.key }) { folder ->
          DownloadFolderCard(
            folder = folder,
            onOpenFolder = {
              openFolderKey = folder.key
              selectionMode = false
              selectedIds = emptySet()
            },
            onDeleteFolder = {
              val ids = folder.items.map { it.id }.toLongArray()
              val removed = StreamDownloadManager.removeDownload(context, *ids)
              tasks = StreamDownloadManager.queryAllDownloads(context)
              onNotify("Removed ${folder.folderName} ($removed file(s))")
            },
          )
        }
      }
    } else if (displayedTasks.isEmpty()) {
      item {
        EmptyDownloadsCard(onExploreMovies = onExploreMovies)
      }
    } else {
      val subtitleEpisodeKeys =
        displayedTasks
          .filter { it.isSubtitle && it.isCompleted }
          .mapNotNull { it.episodeCode ?: it.folderName }
          .toSet()

      items(displayedTasks, key = { it.id }) { task ->
        val isSelected = task.id in selectedIds
        val hasMatchedSub =
          task.isVideo &&
            ((task.episodeCode != null && task.episodeCode in subtitleEpisodeKeys) ||
              (!task.isSeriesFolder && subtitleEpisodeKeys.isNotEmpty()))
        DownloadTaskCard(
          task = task,
          hasMatchedCc = hasMatchedSub,
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
          onSaveToGallery = {
            val uri = task.localUri
            if (!uri.isNullOrBlank()) {
              scope.launch {
                StreamDownloadManager.saveVideoToGallery(
                  context = context,
                  localUri = uri,
                  folderTitle = task.folderName,
                  displayTitle = task.title,
                ).onSuccess {
                  onNotify("Saved ${task.title} to Gallery/Movies/DecanMovie")
                }.onFailure { err ->
                  onNotify(err.localizedMessage ?: "Could not save to Gallery")
                }
              }
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
private fun EmptyDownloadsCard(onExploreMovies: () -> Unit) {
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
        text = "No Folders Downloaded Yet",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "Movies are placed in a Movies folder and multi-episode Series or Anime automatically create their own series folder with matched .srt subtitles.",
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

@Composable
private fun DownloadFolderCard(
  folder: DownloadFolderSummary,
  onOpenFolder: () -> Unit,
  onDeleteFolder: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .clickable(onClick = onOpenFolder)
        .testTag("download_folder_${folder.key}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color =
          if (folder.isSeriesFolder) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(48.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (folder.isSeriesFolder) Icons.Filled.VideoLibrary else Icons.Filled.Movie,
            contentDescription = null,
            tint =
              if (folder.isSeriesFolder) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = folder.folderName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        val sizeMb = folder.totalBytes / (1024.0 * 1024.0)
        Text(
          text = buildString {
            append("${folder.videoCount} video(s)")
            if (folder.subtitleCount > 0) append(" • ${folder.subtitleCount} CC (.srt)")
            if (folder.activeCount > 0) append(" • ${folder.activeCount} downloading")
            append(String.format(Locale.US, " • %.1f MB", sizeMb))
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(onClick = onDeleteFolder) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Remove folder downloads",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
  hasMatchedCc: Boolean,
  selectionMode: Boolean,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onPlayOffline: () -> Unit,
  onSaveToGallery: () -> Unit,
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
                  if (task.isSubtitle) Icons.Filled.ClosedCaption
                  else if (task.isCompleted) Icons.Filled.DownloadDone
                  else Icons.Filled.Downloading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
          val statusText =
            if (!task.reasonHint.isNullOrBlank()) "${task.statusLabel} • ${task.reasonHint}"
            else "${task.statusLabel} • $sizeSummary"
          Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color =
              when {
                task.isCompleted -> MaterialTheme.colorScheme.primary
                task.isFailed -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
          if (hasMatchedCc) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.ClosedCaption,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "Offline CC matched & ready",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (task.isCompleted && task.isVideo && !task.localUri.isNullOrBlank()) {
            IconButton(
              onClick = onSaveToGallery,
              modifier = Modifier.testTag("save_gallery_${task.id}"),
            ) {
              Icon(
                imageVector = Icons.Filled.PermMedia,
                contentDescription = "Save to Gallery",
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
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
