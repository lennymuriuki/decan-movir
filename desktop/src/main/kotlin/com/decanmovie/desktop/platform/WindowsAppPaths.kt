package com.decanmovie.desktop.platform

import java.io.File

object WindowsAppPaths {
  val isWindows: Boolean
    get() = System.getProperty("os.name").orEmpty().lowercase().contains("win")

  fun isPortableExecution(appDir: File = File(System.getProperty("user.dir") ?: ".")): Boolean {
    if (System.getenv("DECAN_PORTABLE").equals("true", ignoreCase = true)) return true
    if (File(appDir, "portable.mode").exists()) return true
    val parent = appDir.parentFile
    if (parent != null && File(parent, "portable.mode").exists()) return true
    return !isWindowsInstalledPath(appDir) && File(appDir, "Decan Movie.exe").exists()
  }

  private fun isWindowsInstalledPath(dir: File): Boolean {
    val p = dir.absolutePath.lowercase()
    return p.contains("\\program files") ||
      p.contains("\\appdata\\local\\programs") ||
      p.contains("\\appdata\\local\\decan movie")
  }

  fun appDataDirectory(customBase: File? = null): File {
    if (customBase != null) {
      customBase.mkdirs()
      return customBase
    }
    val overrideDir = System.getProperty("decan.appdata.dir")?.takeIf { it.isNotBlank() }
    if (overrideDir != null) {
      return File(overrideDir).apply { mkdirs() }
    }
    val root =
      if (isWindows) {
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        if (localAppData != null) File(localAppData, "DecanMovie")
        else File(System.getProperty("user.home"), "AppData/Local/DecanMovie")
      } else {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        if (xdg != null) File(xdg, "DecanMovie")
        else File(System.getProperty("user.home"), ".local/share/DecanMovie")
      }
    root.mkdirs()
    return root
  }

  fun cacheDirectory(baseDir: File = appDataDirectory()): File =
    File(baseDir, "cache").apply { mkdirs() }

  fun updatesDirectory(baseDir: File = appDataDirectory()): File =
    File(baseDir, "updates").apply { mkdirs() }

  fun defaultDownloadsDirectory(): File {
    val overrideDir = System.getProperty("decan.downloads.dir")?.takeIf { it.isNotBlank() }
    if (overrideDir != null) {
      return File(overrideDir).apply { mkdirs() }
    }
    val userHome = File(System.getProperty("user.home") ?: ".")
    val downloads = File(userHome, "Downloads/DecanMovie")
    downloads.mkdirs()
    return downloads
  }
}
