# Decan Movie — Windows Desktop

A genuine Electron desktop client for Windows 10/11 x64, maintained alongside the existing Android application in the same repository.

## Architecture

- `src/main.js` — Windows process, tray, settings, native dialogs, GitHub update checks, checksum-verified downloads.
- `src/preload.js` — restricted IPC bridge; renderer has no Node.js access.
- `src/renderer.js` — desktop catalog, search, details, watchlist/history, player and drama-only vertical experience.
- `src/index.html` / `src/styles.css` — desktop UI.
- `assets/` — Decan Movie artwork.
- `build/icon.ico` — Windows application icon.

The Android Gradle project remains under `app/` and is not imported into the desktop runtime.

## Local Windows development

Requirements: Windows 10/11 x64 and Node.js 22+.

```powershell
cd desktop
npm ci
npm run check
npm start
```

## Build

```powershell
npm run dist:installer
npm run dist:portable
npm run dist:win
```

Build output is placed in `desktop/release/`.

## GitHub Releases

The workflow `.github/workflows/windows-desktop-release.yml` runs for `v*` tags and can also be started manually. It builds the NSIS installer and portable executable, generates `checksums-sha256.txt`, and uploads only Windows assets to the existing release tag. Android APK/AAB assets are not replaced.

The application only considers `.exe` and `.zip`/Windows-labelled assets as desktop update candidates and explicitly rejects `.apk` files.

## Update verification

When a release exposes a checksum, the desktop updater verifies SHA-256 before making the downloaded file available. If the checksum is missing, the download is still validated for HTTP success but no cryptographic comparison is possible.

## Data

Settings, watch history and the local watchlist are stored under the Electron user-data directory. Uninstalling through NSIS does not intentionally delete this data.

## Important runtime note

Catalog and playback availability depends on the existing Decan Movie service endpoints used by the Android application. The desktop client does not include Android-only Firebase/Compose components. It also does not bypass service authentication or transform protected media into unrestricted URLs.
