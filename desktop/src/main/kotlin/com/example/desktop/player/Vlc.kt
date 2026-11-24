package com.example.desktop.player

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/** Detects a locally installed VLC (needed by vlcj for embedded playback). */
object Vlc {
  val available: Boolean by lazy { runCatching { NativeDiscovery().discover() }.getOrDefault(false) }
}
