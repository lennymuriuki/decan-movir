# Decan Movie — Desktop (Windows / macOS / Linux)

Compose Multiplatform Desktop app that shares the Android app's catalog, MovieBox engine,
crypto, SRT parser and models (compiled straight from `app/src/main/java`, see `desktop/build.gradle.kts`).

## Run
    gradle :desktop:run -PdesktopOnly=true        # JDK 17+; -PdesktopOnly skips the Android module (no SDK needed)

## Build installers
    gradle :desktop:packageDistributionForCurrentOS -PdesktopOnly=true -PappVersion=1.0.0
Output: `desktop/build/compose/binaries/main/{msi,exe,dmg,deb}`. Windows installers must be built on Windows
(use the `Build Decan Movie Desktop` GitHub workflow to get all three OSes).

## Needs
* **VLC (64-bit)** installed on the PC for in-app playback (otherwise "Open in browser" is offered).

## Differences from Android
Home, Movies/Series/Anime, Search, Details (seasons/episodes), Player (speed, quality, CC, resume, shortcuts:
Space / ←→ / F / Esc), Downloads (+ .srt), Watchlist (local file) and Settings are included.
Google sign-in / Firestore sync and notifications are not (Firebase/Credentials are Android-only).
