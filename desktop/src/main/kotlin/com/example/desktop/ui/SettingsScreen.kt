package com.example.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.desktop.AppState
import com.example.desktop.player.Vlc
import java.awt.Desktop
import java.net.URI
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

val APP_VERSION: String = System.getProperty("jpackage.app-version") ?: "dev"

@Composable
fun SettingsScreen(app: AppState) {
  val s = app.store.settings.value
  val scope = rememberCoroutineScope()
  var updateMsg by remember { mutableStateOf<String?>(null) }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    SectionTitle("Settings")

    SettingRow("Dark theme") { Switch(s.darkTheme, { v -> app.store.updateSettings { it.copy(darkTheme = v) } }) }
    SettingRow("Auto-play next episode") { Switch(s.autoPlayNext, { v -> app.store.updateSettings { it.copy(autoPlayNext = v) } }) }
    SettingRow("Show subtitles by default") { Switch(s.subtitlesOnByDefault, { v -> app.store.updateSettings { it.copy(subtitlesOnByDefault = v) } }) }

    Text("Default quality", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(360, 480, 720, 1080).forEach { q -> FilterChip(s.defaultQuality == q, { app.store.updateSettings { it.copy(defaultQuality = q) } }, { Text("${q}p") }) }
    }

    Text("Download folder", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        s.downloadDir, { v -> app.store.updateSettings { it.copy(downloadDir = v) } }, Modifier.weight(1f), singleLine = true,
        placeholder = { Text(app.downloads.downloadDir().absolutePath) },
      )
      OutlinedButton({
        val chooser = JFileChooser(app.downloads.downloadDir().parentFile).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) app.store.updateSettings { it.copy(downloadDir = chooser.selectedFile.absolutePath) }
      }) { Text("Choose…") }
    }

    Text("Video player", style = MaterialTheme.typography.titleMedium)
    Text(
      if (Vlc.available) "VLC detected — embedded playback is ready." else "VLC not found. Install VLC (64-bit) from videolan.org to play inside the app; otherwise titles open in your browser.",
      color = if (Vlc.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
    if (!Vlc.available) OutlinedButton({ runCatching { Desktop.getDesktop().browse(URI("https://www.videolan.org/vlc/")) } }) { Text("Get VLC") }

    Text("Updates", style = MaterialTheme.typography.titleMedium)
    Text("Version $APP_VERSION", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        s.githubRepo, { v -> app.store.updateSettings { it.copy(githubRepo = v) } }, Modifier.weight(1f), singleLine = true,
        label = { Text("GitHub repository (owner/repo)") },
      )
      OutlinedButton({
        scope.launch { updateMsg = "Checking…"; updateMsg = checkLatest(s.githubRepo) }
      }, enabled = s.githubRepo.contains("/")) { Text("Check for updates") }
    }
    updateMsg?.let { Text(it) }
  }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    control()
  }
}

private suspend fun checkLatest(repo: String): String =
  withContext(Dispatchers.IO) {
    runCatching {
      val slug = repo.trim().removePrefix("https://github.com/").trim('/')
      val req = Request.Builder().url("https://api.github.com/repos/$slug/releases/latest").header("Accept", "application/vnd.github+json").build()
      OkHttpClient().newCall(req).execute().use { r ->
        if (!r.isSuccessful) return@use "No release information found (HTTP ${r.code})."
        val o = JSONObject(r.body?.string().orEmpty())
        val tag = o.optString("tag_name")
        val url = o.optString("html_url")
        if (tag.trimStart('v') == APP_VERSION) "You're up to date ($tag)." else "Latest release: $tag — $url"
      }
    }.getOrElse { "Could not check for updates." }
  }
