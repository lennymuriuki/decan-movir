package com.example.desktop.data

import java.io.File

/** Where the desktop app keeps its data (settings, watchlist, caches). */
object AppPaths {
  val dataDir: File =
    run {
      val home = System.getProperty("user.home") ?: "."
      val os = System.getProperty("os.name").orEmpty().lowercase()
      val base =
        when {
          os.contains("win") -> File(System.getenv("APPDATA") ?: home, "DecanMovie")
          os.contains("mac") -> File(home, "Library/Application Support/DecanMovie")
          else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "DecanMovie")
        }
      base.apply { mkdirs() }
    }

  val defaultDownloadDir: File
    get() {
      val home = System.getProperty("user.home") ?: "."
      val videos = File(home, "Videos").takeIf { it.isDirectory } ?: File(home, "Movies").takeIf { it.isDirectory } ?: File(home)
      return File(videos, "DecanMovie")
    }
}
