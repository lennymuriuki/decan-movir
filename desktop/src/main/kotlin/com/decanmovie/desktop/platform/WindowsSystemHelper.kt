package com.decanmovie.desktop.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WindowsSystemHelper {
  @Volatile private var lafInitialized = false

  private fun ensureSystemLookAndFeel() {
    if (lafInitialized) return
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    lafInitialized = true
  }

  suspend fun pickDownloadDirectory(currentPath: String): String? =
    withContext(Dispatchers.IO) {
      var selected: String? = null
      runCatching {
        SwingUtilities.invokeAndWait {
          ensureSystemLookAndFeel()
          val chooser =
            JFileChooser(File(currentPath).takeIf { it.exists() } ?: WindowsAppPaths.defaultDownloadsDirectory())
          chooser.dialogTitle = "Select Decan Movie Download Folder"
          chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
          chooser.isAcceptAllFileFilterUsed = false
          if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            selected = chooser.selectedFile?.absolutePath
          }
        }
      }
      selected
    }

  suspend fun pickLocalVideoFile(initialDir: String? = null): File? =
    withContext(Dispatchers.IO) {
      var chosen: File? = null
      runCatching {
        SwingUtilities.invokeAndWait {
          ensureSystemLookAndFeel()
          if (WindowsAppPaths.isWindows) {
            val dialog = FileDialog(null as Frame?, "Open Video File — Decan Movie", FileDialog.LOAD)
            if (!initialDir.isNullOrBlank() && File(initialDir).exists()) {
              dialog.directory = initialDir
            }
            dialog.file = "*.mp4;*.mkv;*.m4v;*.mov;*.webm;*.avi;*.ts"
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (!dir.isNullOrBlank() && !file.isNullOrBlank()) {
              chosen = File(dir, file)
            }
          } else {
            val chooser =
              JFileChooser(initialDir?.let(::File)?.takeIf { it.exists() } ?: WindowsAppPaths.defaultDownloadsDirectory())
            chooser.dialogTitle = "Open Local Video File"
            chooser.fileFilter =
              FileNameExtensionFilter("Video Files", "mp4", "mkv", "m4v", "mov", "webm", "avi", "ts")
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
              chosen = chooser.selectedFile
            }
          }
        }
      }
      chosen?.takeIf { it.exists() && it.isFile }
    }

  fun openFolderInExplorer(path: String): Boolean =
    runCatching {
      val target = File(path)
      if (!target.exists()) return false
      if (WindowsAppPaths.isWindows) {
        if (target.isFile) {
          ProcessBuilder("explorer.exe", "/select,${target.absolutePath}").start()
        } else {
          ProcessBuilder("explorer.exe", target.absolutePath).start()
        }
        true
      } else if (Desktop.isDesktopSupported()) {
        val dir = if (target.isFile) target.parentFile ?: target else target
        Desktop.getDesktop().open(dir)
        true
      } else {
        false
      }
    }.getOrDefault(false)

  fun openExternalUrl(url: String): Boolean =
    runCatching {
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
        true
      } else if (WindowsAppPaths.isWindows) {
        ProcessBuilder("cmd", "/c", "start", "", url).start()
        true
      } else {
        false
      }
    }.getOrDefault(false)

  fun openInExternalMediaPlayer(fileOrUrl: String): Boolean =
    runCatching {
      val file = File(fileOrUrl)
      if (file.exists() && Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file)
        true
      } else if (WindowsAppPaths.isWindows) {
        ProcessBuilder("cmd", "/c", "start", "", fileOrUrl).start()
        true
      } else {
        false
      }
    }.getOrDefault(false)

  fun launchWindowsInstaller(installerFile: File): Result<Unit> =
    runCatching {
      require(installerFile.exists() && installerFile.length() > 0L) {
        "Installer file not found"
      }
      if (!WindowsAppPaths.isWindows) {
        openFolderInExplorer(installerFile.parentFile?.absolutePath ?: installerFile.absolutePath)
        return@runCatching
      }
      if (installerFile.name.endsWith(".exe", ignoreCase = true)) {
        ProcessBuilder(installerFile.absolutePath).start()
      } else if (installerFile.name.endsWith(".msi", ignoreCase = true)) {
        ProcessBuilder("msiexec", "/i", installerFile.absolutePath).start()
      } else {
        openFolderInExplorer(installerFile.absolutePath)
      }
    }

  /**
   * Configures Windows HKCU Run key cleanly so Decan Movie optionally starts at Windows login.
   * No elevated admin permissions required. Safe no-op on non-Windows OS.
   */
  fun setWindowsStartupEnabled(enabled: Boolean): Boolean {
    if (!WindowsAppPaths.isWindows) return true
    return runCatching {
      val regKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
      val valueName = "DecanMovie"
      if (enabled) {
        val exePath = resolveCurrentExecutablePath() ?: return false
        val exitCode =
          ProcessBuilder(
              "reg",
              "add",
              regKey,
              "/v",
              valueName,
              "/t",
              "REG_SZ",
              "/d",
              "\"$exePath\" --minimized",
              "/f",
            )
            .redirectErrorStream(true)
            .start()
            .waitFor()
        exitCode == 0
      } else {
        val exitCode =
          ProcessBuilder("reg", "delete", regKey, "/v", valueName, "/f")
            .redirectErrorStream(true)
            .start()
            .waitFor()
        exitCode == 0 || exitCode == 1
      }
    }.getOrDefault(false)
  }

  private fun resolveCurrentExecutablePath(): String? =
    runCatching {
      ProcessHandle.current().info().command().orElse(null)?.takeIf {
        it.endsWith("Decan Movie.exe", ignoreCase = true)
      }
    }.getOrNull()
}
