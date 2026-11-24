# Decan Movie — Developer & Build Guide

## Multi-Module Project Structure
- `:app` — Existing Android Jetpack Compose application (Media3/ExoPlayer, TikTok-style Vertical Drama Pager, MovieBox/Jikan/AniList/iTunes clients, Firebase Firestore watchlist sync, Android APK GitHub updater).
- `:desktop` — Standalone Kotlin + Compose Multiplatform Desktop (`1.12.2`) + JavaFX 21 Media (`javafx-media`, `javafx-swing`) application targeting Windows 10/11 (x64).

### Key `:desktop` Packages
- `com.decanmovie.desktop.MainKt` — Undecorated Compose Desktop shell (`DecanDesktopShell`), custom draggable title bar, system tray lifecycle, global shortcuts.
- `com.decanmovie.desktop.model` — `DesktopDestination`, `Movie`, `DramaExperienceDetector`, `DesktopSettings`, `DownloadTaskState`, `WatchlistEntry`, `PlaybackHistoryItem`.
- `com.decanmovie.desktop.platform` — `WindowsAppPaths`, `WindowsSystemHelper` (native file/folder pickers, Explorer integration, HKCU startup registry, `.exe`/`.msi` installer launcher), `DesktopTrayAndNotifier`.
- `com.decanmovie.desktop.data.remote` — `MovieBoxEngine`, `JikanTvMazeService`, `ItunesMovieClient`, `DesktopMovieCatalogRepository`, `DesktopGitHubUpdateRepository`.
- `com.decanmovie.desktop.data.download` — `DesktopDownloadManager` (structured category/season folders, HTTP `Range` resume, `.part` atomic writes, `.srt` pairing).
- `com.decanmovie.desktop.media` — `DesktopImageLoader` (LRU + disk cache) & `DesktopMediaController` / `DesktopVideoSurface` (JavaFX `MediaPlayer` inside Swing `JFXPanel`).
- `com.decanmovie.desktop.ui` — `DecanDesktopTheme`, `DesktopComponents`, and modular screens (`HomeScreen`, `CatalogBrowseScreen`, `SearchScreen`, `MovieDetailScreen`, `DesktopTheaterPlayerScreen`, `DesktopVerticalDramaPlayerScreen`, `LibraryScreens`, `SettingsAndUpdateScreens`).

## Local Development Commands

### Desktop (Current Host OS)
```bash
# Compile Desktop module and generate multi-res Windows .ico / .png icon
gradle :desktop:compileKotlin :desktop:generateDesktopIcon --no-daemon

# Run Desktop unit + headless Compose UI tests
gradle :desktop:test --no-daemon

# Build distributable app-image & Portable ZIP
gradle :desktop:packagePortableZip --no-daemon
```

### Windows Target Packaging (On Windows x64 with WiX Toolset installed)
```powershell
gradle :desktop:test :desktop:packageExe :desktop:packageMsi :desktop:packagePortableZip --no-daemon -PtargetOs=windows -PappVersionName=1.1.0
```
Outputs:
- `desktop/build/compose/binaries/main/exe/*.exe` (Windows Installer `.exe`)
- `desktop/build/compose/binaries/main/msi/*.msi` (Windows Installer `.msi`)
- `desktop/build/distributions/Decan-Movie-Portable.zip` (Portable ZIP)

### Android Module Verification
```bash
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

## GitHub Actions CI/CD Workflows
- `.github/workflows/android-build.yml` & `.github/workflows/android-release.yml` — Android APK & AAB build and release workflows (unchanged).
- `.github/workflows/windows-desktop-build.yml` — Runs `:desktop:test`, `:desktop:packageExe`, `:desktop:packageMsi`, `:desktop:packagePortableZip` on `windows-latest`.
- `.github/workflows/windows-desktop-release.yml` — On tag push (`v*` or `win-v*`) or manual `workflow_dispatch`, packages Windows `.exe`, `.msi`, `-Portable.zip`, generates `windows-checksums-sha256.txt`, and attaches them to the GitHub Release without overwriting existing Android APKs.
