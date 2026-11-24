package com.example.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.desktop.AppState
import com.example.desktop.PlayRequest
import com.example.desktop.data.DownloadStatus
import java.awt.Desktop
import java.io.File

@Composable
fun DownloadsScreen(app: AppState) {
  val tasks = app.downloads.tasks
  val doneCount = tasks.count { it.status == DownloadStatus.DONE }
  var files by remember { mutableStateOf<List<File>>(emptyList()) }
  androidx.compose.runtime.LaunchedEffect(doneCount) { files = app.downloads.listLocalVideos() }

  LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("Downloads", Modifier.weight(1f))
        OutlinedButton({ runCatching { Desktop.getDesktop().open(app.downloads.downloadDir().apply { mkdirs() }) } }) { Text("Open folder") }
      }
      Text("Saving to ${app.downloads.downloadDir()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(tasks.toList(), key = { it.id }) { t ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.fileName, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyLarge)
            if (t.status == DownloadStatus.RUNNING || t.status == DownloadStatus.QUEUED)
              IconButton({ app.downloads.cancel(t) }) { Icon(Icons.Filled.Close, "Cancel") }
            else IconButton({ app.downloads.remove(t) }) { Icon(Icons.Filled.Close, "Dismiss") }
          }
          when (t.status) {
            DownloadStatus.RUNNING -> {
              if (t.total > 0) LinearProgressIndicator({ t.progress }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
              Text("${formatBytes(t.bytes)}${if (t.total > 0) " / ${formatBytes(t.total)}" else ""}", style = MaterialTheme.typography.bodySmall)
            }
            DownloadStatus.QUEUED -> Text("Queued…", style = MaterialTheme.typography.bodySmall)
            DownloadStatus.DONE -> Text("Completed • ${formatBytes(t.bytes)}", style = MaterialTheme.typography.bodySmall)
            DownloadStatus.FAILED -> Text("Failed: ${t.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            DownloadStatus.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
    item { SectionTitle("On this computer") }
    if (files.isEmpty()) item { Message("No downloaded videos yet.") }
    items(files, key = { it.absolutePath }) { f ->
      Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(f.nameWithoutExtension, maxLines = 1)
            Text(formatBytes(f.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          IconButton({ app.play(PlayRequest(f.nameWithoutExtension, localFile = f)) }) { Icon(Icons.Filled.PlayArrow, "Play") }
          IconButton({
            runCatching { f.delete(); File(f.parentFile, f.nameWithoutExtension + ".srt").delete() }
            files = app.downloads.listLocalVideos()
          }) { Icon(Icons.Filled.Delete, "Delete") }
        }
      }
    }
  }
}
