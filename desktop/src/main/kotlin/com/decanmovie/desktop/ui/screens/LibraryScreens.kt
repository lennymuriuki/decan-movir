package com.decanmovie.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.decanmovie.desktop.model.DownloadTaskState
import com.decanmovie.desktop.model.DownloadTaskStatus
import com.decanmovie.desktop.model.LocalMediaItem
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.PlaybackHistoryItem
import com.decanmovie.desktop.model.WatchStatus
import com.decanmovie.desktop.model.WatchlistEntry
import com.decanmovie.desktop.platform.WindowsSystemHelper
import com.decanmovie.desktop.ui.components.AsyncDesktopImage
import com.decanmovie.desktop.ui.components.DesktopMediaCard
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import java.io.File

private val WATCHLIST_TABS = listOf("All", "Watchlist", "Watching", "Watched", "Favorites")

@Composable
fun WatchlistScreen(
  entries: List<WatchlistEntry>,
  compactCards: Boolean,
  onMovieClick: (Movie) -> Unit,
  onQuickPlay: (Movie) -> Unit,
  onRemove: (String) -> Unit,
  onSaveReview: (movie: Movie, status: WatchStatus, favorite: Boolean, rating: Int, note: String) -> Unit,
) {
  var selectedTab by remember { mutableStateOf("All") }
  var editingEntry by remember { mutableStateOf<WatchlistEntry?>(null) }

  val filtered =
    remember(entries, selectedTab) {
      when (selectedTab) {
        "Watchlist" -> entries.filter { it.watchStatus == WatchStatus.WANT_TO_WATCH }
        "Watching" -> entries.filter { it.watchStatus == WatchStatus.WATCHING }
        "Watched" -> entries.filter { it.watchStatus == WatchStatus.WATCHED }
        "Favorites" -> entries.filter { it.favorite }
        else -> entries
      }
    }

  val minCellWidth = if (compactCards) 156.dp else 182.dp

  Column(modifier = Modifier.fillMaxSize().testTag("watchlist_screen")) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("My Watchlist & Saved Titles", style = MaterialTheme.typography.headlineMedium, color = DecanDesktopColors.Mist)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WATCHLIST_TABS.forEach { tab ->
          FilterPillChip(label = tab, selected = selectedTab == tab, onClick = { selectedTab = tab })
        }
      }
    }

    if (filtered.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.Bookmark, contentDescription = null, tint = DecanDesktopColors.Slate.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
          Text("No titles in $selectedTab yet.", style = MaterialTheme.typography.titleMedium, color = DecanDesktopColors.Slate)
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellWidth),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(filtered, key = { it.movie.id }) { entry ->
          DesktopMediaCard(
            movie = entry.movie,
            onClick = { onMovieClick(entry.movie) },
            onQuickPlay = { onQuickPlay(entry.movie) },
            isInWatchlist = true,
            onToggleWatchlist = { onRemove(entry.movie.id) },
            cardWidth = minCellWidth,
            subtitleOverride = buildString {
              append(entry.watchStatus.label)
              if (entry.personalRating > 0) append(" • ${entry.personalRating}★")
            },
          )
        }
      }
    }

    editingEntry?.let { cur ->
      WatchlistReviewDialog(
        movie = cur.movie,
        existing = cur,
        onDismiss = { editingEntry = null },
        onSave = { st, fav, rating, note ->
          onSaveReview(cur.movie, st, fav, rating, note)
          editingEntry = null
        },
      )
    }
  }
}

@Composable
fun DownloadsScreen(
  downloadRoot: String,
  tasks: List<DownloadTaskState>,
  localItems: List<LocalMediaItem>,
  onRefreshLibrary: () -> Unit,
  onPickLocalFile: () -> Unit,
  onPauseTask: (String) -> Unit,
  onResumeTask: (String) -> Unit,
  onCancelTask: (String) -> Unit,
  onRemoveTask: (String) -> Unit,
  onPlayLocalItem: (LocalMediaItem) -> Unit,
) {
  val grouped = remember(localItems) { localItems.groupBy { it.category }.toSortedMap() }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("downloads_screen"),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Downloads & Local Library", style = MaterialTheme.typography.headlineMedium, color = DecanDesktopColors.Mist)
          Text(
            text = "Organized automatically in $downloadRoot (Movies, Series, Anime, Dramas + .srt captions)",
            style = MaterialTheme.typography.bodySmall,
            color = DecanDesktopColors.Slate,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { WindowsSystemHelper.openFolderInExplorer(downloadRoot) },
            border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DecanDesktopColors.Mist),
          ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Folder")
          }
          Button(
            onClick = onPickLocalFile,
            colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
          ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Browse Video")
          }
          IconButton(onClick = onRefreshLibrary) {
            Icon(Icons.Filled.Refresh, contentDescription = "Scan Downloads", tint = DecanDesktopColors.Mist)
          }
        }
      }
    }

    if (tasks.isNotEmpty()) {
      item {
        Text("Download Queue", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
      }
      items(tasks, key = { it.id }) { task ->
        Surface(
          color = DecanDesktopColors.SurfaceElevated,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, color = DecanDesktopColors.Mist)
                Text(
                  text = "${task.displayLabel} • ${task.status.name} • ${formatBytes(task.bytesDownloaded)}" +
                    if (task.totalBytes > 0) " / ${formatBytes(task.totalBytes)}" else "",
                  style = MaterialTheme.typography.bodySmall,
                  color = DecanDesktopColors.Slate,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (task.status) {
                  DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.QUEUED -> {
                    IconButton(onClick = { onPauseTask(task.id) }) {
                      Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = DecanDesktopColors.Mist)
                    }
                    IconButton(onClick = { onCancelTask(task.id) }) {
                      Icon(Icons.Filled.Cancel, contentDescription = "Cancel", tint = DecanDesktopColors.CrimsonBright)
                    }
                  }
                  DownloadTaskStatus.PAUSED, DownloadTaskStatus.FAILED -> {
                    IconButton(onClick = { onResumeTask(task.id) }) {
                      Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = DecanDesktopColors.EmeraldOk)
                    }
                    IconButton(onClick = { onRemoveTask(task.id) }) {
                      Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = DecanDesktopColors.Slate)
                    }
                  }
                  else -> {
                    IconButton(onClick = { onRemoveTask(task.id) }) {
                      Icon(Icons.Filled.Delete, contentDescription = "Clear", tint = DecanDesktopColors.Slate)
                    }
                  }
                }
              }
            }
            if (task.status == DownloadTaskStatus.DOWNLOADING || task.status == DownloadTaskStatus.PAUSED) {
              LinearProgressIndicator(
                progress = { task.progress },
                color = DecanDesktopColors.Crimson,
                trackColor = DecanDesktopColors.Obsidian,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
              )
            }
            if (!task.errorMessage.isNullOrBlank()) {
              Text(task.errorMessage, style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.CrimsonBright)
            }
          }
        }
      }
    }

    if (grouped.isEmpty()) {
      item {
        Surface(
          color = DecanDesktopColors.Surface,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Filled.Download, contentDescription = null, tint = DecanDesktopColors.Slate, modifier = Modifier.size(40.dp))
            Text("No downloaded videos found in $downloadRoot", style = MaterialTheme.typography.titleMedium, color = DecanDesktopColors.Mist)
            Text(
              "Download movies, series, anime, or dramas from any detail screen — or click Browse Video to play any local MP4/MKV file.",
              style = MaterialTheme.typography.bodySmall,
              color = DecanDesktopColors.Slate,
            )
          }
        }
      }
    } else {
      grouped.forEach { (category, itemsInCat) ->
        item {
          Text("$category (${itemsInCat.size})", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
        }
        items(itemsInCat, key = { it.filePath }) { item ->
          Surface(
            color = DecanDesktopColors.SurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DecanDesktopColors.Outline),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlayLocalItem(item) },
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Surface(color = DecanDesktopColors.CrimsonContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = DecanDesktopColors.CrimsonBright)
                }
              }
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, color = DecanDesktopColors.Mist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  text = listOfNotNull(item.titleFolder, item.seasonFolder, formatBytes(item.sizeBytes)).joinToString(" • "),
                  style = MaterialTheme.typography.bodySmall,
                  color = DecanDesktopColors.Slate,
                )
              }
              if (item.subtitlePath != null) {
                Surface(color = DecanDesktopColors.SkyContainer, shape = RoundedCornerShape(6.dp)) {
                  Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.ClosedCaption, contentDescription = null, tint = DecanDesktopColors.SkyAccent, modifier = Modifier.size(14.dp))
                    Text("SRT", style = MaterialTheme.typography.labelMedium, color = Color.White)
                  }
                }
              }
              IconButton(onClick = { WindowsSystemHelper.openFolderInExplorer(File(item.filePath).parentFile?.absolutePath ?: item.filePath) }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Show in Explorer", tint = DecanDesktopColors.Slate)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun HistoryScreen(
  history: List<PlaybackHistoryItem>,
  onResume: (PlaybackHistoryItem) -> Unit,
  onOpenDetail: (PlaybackHistoryItem) -> Unit,
  onRemoveItem: (String) -> Unit,
  onClearAll: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().testTag("history_screen")) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text("Watch History", style = MaterialTheme.typography.headlineMedium, color = DecanDesktopColors.Mist)
        Text("Resume any movie, series, anime, or drama episode", style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.Slate)
      }
      if (history.isNotEmpty()) {
        OutlinedButton(
          onClick = onClearAll,
          border = BorderStroke(1.dp, DecanDesktopColors.Crimson.copy(alpha = 0.7f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = DecanDesktopColors.CrimsonBright),
        ) {
          Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Clear History")
        }
      }
    }

    if (history.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.History, contentDescription = null, tint = DecanDesktopColors.Slate.copy(alpha = 0.5f), modifier = Modifier.size(46.dp))
          Text("No recent playback history yet.", style = MaterialTheme.typography.titleMedium, color = DecanDesktopColors.Slate)
        }
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(history, key = { it.movieId }) { item ->
          Surface(
            color = DecanDesktopColors.SurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, DecanDesktopColors.Outline),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenDetail(item) },
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Surface(shape = RoundedCornerShape(10.dp), modifier = Modifier.size(width = 66.dp, height = 92.dp)) {
                AsyncDesktopImage(imageUrl = item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize())
              }
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = DecanDesktopColors.Mist)
                Text(
                  text = buildString {
                    append(item.mediaType)
                    if (item.season > 0 && item.episode > 0) append(" • S%02dE%02d".format(item.season, item.episode))
                    append(" • Stopped at ${formatTimeMs(item.positionMs)}")
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = DecanDesktopColors.Slate,
                )
                LinearProgressIndicator(
                  progress = { item.progressFraction() },
                  color = DecanDesktopColors.Crimson,
                  trackColor = DecanDesktopColors.Obsidian,
                  modifier = Modifier.fillMaxWidth(0.7f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
              }
              Button(
                onClick = { onResume(item) },
                colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
              ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Resume")
              }
              IconButton(onClick = { onRemoveItem(item.movieId) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = DecanDesktopColors.Slate)
              }
            }
          }
        }
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0L) return "0 MB"
  val mb = bytes / (1024.0 * 1024.0)
  return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
