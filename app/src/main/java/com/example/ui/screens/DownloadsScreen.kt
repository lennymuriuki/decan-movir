package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.download.DownloadFolderSummary
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.StreamDownloadManager
import com.example.data.settings.DownloadStorageLocation
import com.example.data.settings.DownloadWatchProgress
import com.example.data.settings.PlaybackProgressStore
import com.example.ui.components.MoviePosterImage
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
  progressStore: PlaybackProgressStore? = null,
  previewTasks: List<DownloadTaskInfo>? = null,
  onChangeStorageLocation: (DownloadStorageLocation) -> Unit = {},
  onPlayLocalFile: (localUri: String, title: String) -> Unit = { _, _ -> },
  onPlayDownloadedTask: ((task: DownloadTaskInfo, resumePositionMs: Long) -> Unit)? = null,
  onRemoveDownloads: ((List<Long>) -> Int)? = null,
  onExploreMovies: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var tasks by remember(previewTasks) { mutableStateOf(previewTasks ?: emptyList()) }
  var filter by remember { mutableStateOf(DownloadFilter.FOLDERS) }
  var openFolderKey by remember { mutableStateOf<String?>(null) }
  var selectionMode by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
  var selectedFolderKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

  val watchProgressMap: Map<String, DownloadWatchProgress> =
    if (progressStore != null) {
      progressStore.downloadProgressMap.collectAsStateWithLifecycle().value
    } else {
      emptyMap()
    }

  LaunchedEffect(previewTasks) {
    if (previewTasks != null) {
      tasks = previewTasks
      return@LaunchedEffect
    }
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

  BackHandler(enabled = openedFolder != null || selectionMode) {
    if (selectionMode) {
      selectionMode = false
      selectedIds = emptySet()
      selectedFolderKeys = emptySet()
    } else if (openedFolder != null) {
      openFolderKey = null
    }
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

  val isFolderListMode = openedFolder == null && filter == DownloadFilter.FOLDERS

  fun performDeleteTasks(tasksToRemove: List<DownloadTaskInfo>): Int {
    if (tasksToRemove.isEmpty()) return 0
    val ids = tasksToRemove.map { it.id }
    val removedCount =
      onRemoveDownloads?.invoke(ids)
        ?: StreamDownloadManager.removeDownload(context, *ids.toLongArray())
    tasksToRemove.forEach { t ->
      progressStore?.clearDownloadWatchProgress(
        movieId = t.movieId,
        season = t.seasonNumber,
        episode = t.episodeNumber,
        localUri = t.localUri.orEmpty(),
        relativePath = t.relativePath,
        title = t.title,
      )
    }
    tasks =
      if (previewTasks != null) tasks.filterNot { it.id in ids.toSet() }
      else StreamDownloadManager.queryAllDownloads(context)
    return removedCount
  }

  val activeCount = tasks.count { it.isActive }
  val completedVideoCount = tasks.count { it.isCompleted && it.isVideo }
  val downloadedMb = tasks.sumOf { it.bytesDownloaded } / (1024.0 * 1024.0)
  val hasItemsForCurrentView =
    if (isFolderListMode) folders.isNotEmpty() else displayedTasks.isNotEmpty()

  Column(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth().testTag("downloads_list"),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
    // HEADER WITH DECAN MOVIE LOGO + SELECT MODE TOGGLE
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (openedFolder != null) {
            IconButton(
              onClick = {
                openFolderKey = null
                selectionMode = false
                selectedIds = emptySet()
                selectedFolderKeys = emptySet()
              },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("downloads_folder_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to folders",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          } else {
            Surface(
              shape = MaterialTheme.shapes.medium,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
              modifier = Modifier.size(44.dp).testTag("downloads_brand_logo"),
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_decan_launcher),
                contentDescription = "Decan Movie Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
          Column {
            Text(
              text = openedFolder?.folderName ?: "Downloads Hub",
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                if (openedFolder != null) {
                  "${openedFolder.videoCount} video(s) • ${openedFolder.subtitleCount} CC subtitle(s)"
                } else {
                  "Decan Offline Library • ${storageLocation.pathHint}"
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (tasks.isNotEmpty()) {
            IconButton(
              onClick = {
                selectionMode = !selectionMode
                if (!selectionMode) {
                  selectedIds = emptySet()
                  selectedFolderKeys = emptySet()
                }
              },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("downloads_select_mode_button"),
            ) {
              Icon(
                imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.Checklist,
                contentDescription = if (selectionMode) "Exit selection mode" else "Select downloads",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
          IconButton(
            onClick = {
              tasks =
                if (previewTasks != null) tasks
                else StreamDownloadManager.queryAllDownloads(context)
            },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("downloads_refresh_button"),
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
                label = if (activeCount > 0) "Active" else "Videos",
                value = if (activeCount > 0) activeCount.toString() else completedVideoCount.toString(),
                modifier = Modifier.weight(1f),
              )
              DownloadStatTile(
                label = "Storage",
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
                selectedFolderKeys = emptySet()
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

    if (isFolderListMode) {
      if (folders.isEmpty()) {
        item {
          EmptyDownloadsCard(onExploreMovies = onExploreMovies)
        }
      } else {
        items(folders, key = { it.key }) { folder ->
          val folderSelected = folder.key in selectedFolderKeys
          val folderWatchSummary =
            remember(folder.items, watchProgressMap) {
              summarizeFolderWatchProgress(folder, watchProgressMap)
            }
          DownloadFolderCard(
            folder = folder,
            watchSummary = folderWatchSummary,
            selectionMode = selectionMode,
            isSelected = folderSelected,
            onToggleSelect = {
              selectedFolderKeys =
                if (folderSelected) selectedFolderKeys - folder.key
                else selectedFolderKeys + folder.key
            },
            onLongPress = {
              if (!selectionMode) {
                selectionMode = true
                selectedFolderKeys = setOf(folder.key)
              }
            },
            onOpenFolder = {
              openFolderKey = folder.key
              selectionMode = false
              selectedIds = emptySet()
              selectedFolderKeys = emptySet()
            },
            onDeleteFolder = {
              val removed = performDeleteTasks(folder.items)
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
        val watchProgress =
          remember(task, watchProgressMap) {
            if (task.isVideo && task.isCompleted) {
              PlaybackProgressStore.findInMap(
                records = watchProgressMap,
                movieId = task.movieId,
                season = task.seasonNumber,
                episode = task.episodeNumber,
                localUri = task.localUri.orEmpty(),
                relativePath = task.relativePath,
                title = task.title,
              )
            } else null
          }

        DownloadTaskCard(
          task = task,
          hasMatchedCc = hasMatchedSub,
          watchProgress = watchProgress,
          selectionMode = selectionMode,
          isSelected = isSelected,
          onToggleSelect = {
            selectedIds =
              if (isSelected) selectedIds - task.id else selectedIds + task.id
          },
          onLongPress = {
            if (!selectionMode) {
              selectionMode = true
              selectedIds = setOf(task.id)
            }
          },
          onPlayOffline = {
            val uri = task.localUri
            if (!uri.isNullOrBlank()) {
              val resumeMs =
                if (watchProgress != null && !watchProgress.isFinished) watchProgress.positionMs
                else 0L
              if (onPlayDownloadedTask != null) {
                onPlayDownloadedTask(task, resumeMs)
              } else {
                onPlayLocalFile(uri, task.title)
              }
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
            performDeleteTasks(listOf(task))
            onNotify("Removed ${task.title}")
          },
        )
      }
    }
    }

    // STICKY MULTI-SELECT DELETE ACTION BAR FOR BOTH FOLDERS & FILES
    if (selectionMode && hasItemsForCurrentView) {
      val selectedCount =
        if (isFolderListMode) selectedFolderKeys.size else selectedIds.size
      val allSelected =
        if (isFolderListMode) selectedFolderKeys.size == folders.size
        else selectedIds.size == displayedTasks.size
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag("downloads_selection_bar"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (isFolderListMode) "$selectedCount folder(s) selected"
              else "$selectedCount file(s) selected",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            TextButton(
              onClick = {
                if (isFolderListMode) {
                  selectedFolderKeys =
                    if (allSelected) emptySet() else folders.map { it.key }.toSet()
                } else {
                  selectedIds =
                    if (allSelected) emptySet() else displayedTasks.map { it.id }.toSet()
                }
              },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("downloads_select_all_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.SelectAll,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (allSelected) "Clear" else "Select All")
            }
            FilledTonalButton(
              enabled = selectedCount > 0,
              onClick = {
                val toDelete =
                  if (isFolderListMode) {
                    folders.filter { it.key in selectedFolderKeys }.flatMap { it.items }
                  } else {
                    displayedTasks.filter { it.id in selectedIds }
                  }
                val removed = performDeleteTasks(toDelete)
                selectedIds = emptySet()
                selectedFolderKeys = emptySet()
                selectionMode = false
                onNotify("Deleted $removed download(s)")
              },
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("downloads_delete_selected_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = "Delete selected downloads",
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Delete ($selectedCount)")
            }
          }
        }
      }
    }
  }
}

private data class FolderWatchSummary(
  val label: String,
  val progressFraction: Float,
  val isFinished: Boolean,
  val hasPartialProgress: Boolean,
)

private fun summarizeFolderWatchProgress(
  folder: DownloadFolderSummary,
  records: Map<String, DownloadWatchProgress>,
): FolderWatchSummary? {
  val completedVideos = folder.items.filter { it.isVideo && it.isCompleted }
  if (completedVideos.isEmpty()) return null

  val paired =
    completedVideos.map { v ->
      v to
        PlaybackProgressStore.findInMap(
          records = records,
          movieId = v.movieId,
          season = v.seasonNumber,
          episode = v.episodeNumber,
          localUri = v.localUri.orEmpty(),
          relativePath = v.relativePath,
          title = v.title,
        )
    }
  val finishedCount = paired.count { it.second?.isFinished == true }
  if (finishedCount == completedVideos.size) {
    val label =
      if (completedVideos.size == 1) "Finished"
      else "Finished • All $finishedCount watched"
    return FolderWatchSummary(
      label = label,
      progressFraction = 1f,
      isFinished = true,
      hasPartialProgress = false,
    )
  }

  val mostRecentActive =
    paired
      .filter { it.second?.isInProgress == true }
      .maxByOrNull { it.second?.updatedAt ?: 0L }

  if (mostRecentActive != null) {
    val (video, prog) = mostRecentActive
    val pct = prog!!.percentWatched
    val prefix =
      if (finishedCount > 0) "$finishedCount/${completedVideos.size} finished • " else ""
    val epPart = video.episodeCode?.let { "$it • " } ?: ""
    val pctPart = if (pct > 0) "$pct% watched" else "In progress"
    return FolderWatchSummary(
      label = "${prefix}Continue Watching • $epPart$pctPart",
      progressFraction = prog.fraction.coerceIn(0.02f, 1f),
      isFinished = false,
      hasPartialProgress = true,
    )
  }

  if (finishedCount > 0) {
    return FolderWatchSummary(
      label = "$finishedCount/${completedVideos.size} finished",
      progressFraction = finishedCount.toFloat() / completedVideos.size.toFloat(),
      isFinished = false,
      hasPartialProgress = true,
    )
  }

  return FolderWatchSummary(
    label = "Ready to watch",
    progressFraction = 0f,
    isFinished = false,
    hasPartialProgress = false,
  )
}

@Composable
private fun DownloadArtworkThumbnail(
  artworkUrl: String,
  localVideoUri: String?,
  isSubtitle: Boolean,
  isSeriesFolder: Boolean,
  title: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var extractedFrame by remember(localVideoUri, artworkUrl) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(localVideoUri, artworkUrl, isSubtitle) {
    if (artworkUrl.isBlank() && !isSubtitle && !localVideoUri.isNullOrBlank()) {
      extractedFrame = StreamDownloadManager.loadVideoFrameThumbnail(context, localVideoUri)
    }
  }

  Box(
    modifier =
      modifier
        .size(width = 60.dp, height = 88.dp)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium),
  ) {
    when {
      artworkUrl.isNotBlank() -> {
        MoviePosterImage(
          imageUrl = artworkUrl,
          contentDescription = title,
          modifier = Modifier.fillMaxSize(),
        )
      }
      extractedFrame != null -> {
        Image(
          bitmap = extractedFrame!!.asImageBitmap(),
          contentDescription = title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      else -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector =
              when {
                isSubtitle -> Icons.Filled.ClosedCaption
                isSeriesFolder -> Icons.Filled.VideoLibrary
                else -> Icons.Filled.Movie
              },
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
          )
        }
      }
    }

    // Decan Movie corner logo badge on every downloaded item/folder card
    Surface(
      shape = MaterialTheme.shapes.extraSmall,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
      modifier =
        Modifier.align(Alignment.TopStart)
          .padding(4.dp)
          .size(18.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_decan_launcher),
        contentDescription = "Decan Movie",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
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
        text = "Movies are placed in a Movies folder and multi-episode Series, Anime, or Dramas automatically create their own folder with matched .srt subtitles.",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadFolderCard(
  folder: DownloadFolderSummary,
  watchSummary: FolderWatchSummary?,
  selectionMode: Boolean,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onLongPress: () -> Unit,
  onOpenFolder: () -> Unit,
  onDeleteFolder: () -> Unit,
) {
  val firstCompletedVideo =
    remember(folder.items) {
      folder.items.firstOrNull { it.isVideo && it.isCompleted && !it.localUri.isNullOrBlank() }
    }
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selectionMode && isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surfaceContainer,
      ),
    border =
      if (selectionMode && isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
      else null,
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .combinedClickable(
          onClick = { if (selectionMode) onToggleSelect() else onOpenFolder() },
          onLongClick = onLongPress,
        )
        .testTag("download_folder_${folder.key}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (selectionMode) {
          Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            modifier = Modifier.testTag("folder_checkbox_${folder.key}"),
          )
        }

        DownloadArtworkThumbnail(
          artworkUrl = folder.displayArtworkUrl,
          localVideoUri = firstCompletedVideo?.localUri,
          isSubtitle = false,
          isSeriesFolder = folder.isSeriesFolder,
          title = folder.folderName,
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
              modifier = Modifier.weight(1f, fill = false),
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = MaterialTheme.shapes.extraSmall,
            ) {
              Text(
                text = folder.mediaType.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
            if (folder.releaseYear.isNotBlank()) {
              Text(
                text = folder.releaseYear,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (folder.genre.isNotBlank()) {
              Text(
                text = folder.genre.substringBefore(",").trim(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
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

          if (watchSummary != null) {
            Text(
              text = watchSummary.label,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color =
                when {
                  watchSummary.isFinished -> MaterialTheme.colorScheme.tertiary
                  watchSummary.hasPartialProgress -> MaterialTheme.colorScheme.primary
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        if (!selectionMode) {
          IconButton(
            onClick = onDeleteFolder,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("delete_folder_${folder.key}"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Remove folder downloads",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (watchSummary != null && (watchSummary.hasPartialProgress || watchSummary.isFinished)) {
        LinearProgressIndicator(
          progress = { watchSummary.progressFraction.coerceIn(0f, 1f) },
          color =
            if (watchSummary.isFinished) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadTaskCard(
  task: DownloadTaskInfo,
  hasMatchedCc: Boolean,
  watchProgress: DownloadWatchProgress?,
  selectionMode: Boolean,
  isSelected: Boolean,
  onToggleSelect: () -> Unit,
  onLongPress: () -> Unit,
  onPlayOffline: () -> Unit,
  onSaveToGallery: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selectionMode && isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surfaceContainer,
      ),
    border =
      if (selectionMode && isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
      else null,
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .combinedClickable(
          onClick = {
            if (selectionMode) onToggleSelect()
            else if (task.isCompleted && task.isVideo) onPlayOffline()
          },
          onLongClick = onLongPress,
        )
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
          Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            modifier = Modifier.testTag("download_checkbox_${task.id}"),
          )
        }

        DownloadArtworkThumbnail(
          artworkUrl = task.displayArtworkUrl,
          localVideoUri = if (task.isCompleted && task.isVideo) task.localUri else null,
          isSubtitle = task.isSubtitle,
          isSeriesFolder = task.isSeriesFolder,
          title = task.cleanDisplayTitle,
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = task.cleanDisplayTitle,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (!task.episodeCode.isNullOrBlank()) {
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
              ) {
                Text(
                  text = task.episodeCode!!,
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            if (task.qualityLabel.isNotBlank()) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.extraSmall,
              ) {
                Text(
                  text = task.qualityLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = MaterialTheme.shapes.extraSmall,
            ) {
              Text(
                text = if (task.isSubtitle) "CC .SRT" else task.mediaType.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }

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
                task.isFailed -> MaterialTheme.colorScheme.error
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

          if (task.isCompleted && task.isVideo) {
            val watchLabel = watchProgress?.statusLabel ?: "Ready to watch"
            val isFinished = watchProgress?.isFinished == true
            val isInProgress = watchProgress?.isInProgress == true
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.testTag("download_watch_status_${task.id}"),
            ) {
              if (isFinished) {
                Icon(
                  imageVector = Icons.Filled.DoneAll,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(14.dp),
                )
              }
              Text(
                text = watchLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color =
                  when {
                    isFinished -> MaterialTheme.colorScheme.tertiary
                    isInProgress -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                  },
              )
            }
          }
        }

        if (!selectionMode) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (task.isCompleted && task.isVideo && !task.localUri.isNullOrBlank()) {
              IconButton(
                onClick = onSaveToGallery,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("save_gallery_${task.id}"),
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
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("play_offline_${task.id}"),
              ) {
                Icon(
                  imageVector =
                    if (watchProgress?.isFinished == true) Icons.Filled.Replay
                    else Icons.Filled.PlayArrow,
                  contentDescription =
                    when {
                      watchProgress?.isFinished == true -> "Rewatch offline"
                      watchProgress?.isInProgress == true -> "Continue watching offline"
                      else -> "Play offline"
                    },
                )
              }
            } else if (task.isCompleted) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Completed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp).size(20.dp),
              )
            }

            IconButton(
              onClick = onDelete,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("delete_download_${task.id}"),
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel or remove download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
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
      } else if (task.isCompleted && task.isVideo && watchProgress != null && (watchProgress.isInProgress || watchProgress.isFinished)) {
        LinearProgressIndicator(
          progress = {
            if (watchProgress.isFinished) 1f else watchProgress.fraction.coerceIn(0.02f, 1f)
          },
          color =
            if (watchProgress.isFinished) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
        )
      }
    }
  }
}
