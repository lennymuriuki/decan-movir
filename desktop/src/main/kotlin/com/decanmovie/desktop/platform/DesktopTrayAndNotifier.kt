package com.decanmovie.desktop.platform

import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.imageio.ImageIO

class DesktopTrayAndNotifier(
  private val onRestoreWindow: () -> Unit,
  private val onOpenDownloads: () -> Unit,
  private val onCheckUpdates: () -> Unit,
  private val onQuitApp: () -> Unit,
) {
  private var trayIcon: TrayIcon? = null

  fun install(): Boolean {
    if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return false
    if (trayIcon != null) return true

    return runCatching {
      val imageStream =
        DesktopTrayAndNotifier::class.java.getResourceAsStream("/icons/decan-movie.png")
          ?: return false
      val img: Image = ImageIO.read(imageStream).getScaledInstance(16, 16, Image.SCALE_SMOOTH)

      val popup = PopupMenu()
      val openItem = MenuItem("Open Decan Movie").apply { addActionListener { onRestoreWindow() } }
      val downloadsItem = MenuItem("Downloads").apply { addActionListener { onRestoreWindow(); onOpenDownloads() } }
      val updateItem = MenuItem("Check for Updates").apply { addActionListener { onRestoreWindow(); onCheckUpdates() } }
      val exitItem = MenuItem("Exit Decan Movie").apply { addActionListener { onQuitApp() } }
      popup.add(openItem)
      popup.add(downloadsItem)
      popup.add(updateItem)
      popup.addSeparator()
      popup.add(exitItem)

      val icon =
        TrayIcon(img, "Decan Movie", popup).apply {
          isImageAutoSize = true
          addActionListener { onRestoreWindow() }
        }
      SystemTray.getSystemTray().add(icon)
      trayIcon = icon
      true
    }.getOrDefault(false)
  }

  fun showDesktopNotification(
    title: String,
    message: String,
    enabled: Boolean = true,
  ) {
    if (!enabled) return
    val icon = trayIcon ?: return
    runCatching {
      icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }
  }

  fun uninstall() {
    val icon = trayIcon ?: return
    trayIcon = null
    runCatching {
      if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
        SystemTray.getSystemTray().remove(icon)
      }
    }
  }
}
