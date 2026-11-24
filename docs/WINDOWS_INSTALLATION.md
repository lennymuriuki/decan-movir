# Decan Movie — Windows 10 & Windows 11 Installation Guide

## System Requirements
- **Operating System:** Windows 10 (64-bit, build 1909+) or Windows 11 (64-bit)
- **Architecture:** x64 (`AMD64`)
- **Memory:** 4 GB RAM minimum (8 GB recommended for 1080p streaming)
- **Runtime:** Bundled (Java 21 + JavaFX 21 Media runtime are packaged inside both the installer and portable ZIP — no separate Java install required)

## Installation Options

### Option 1: Windows Installer (`Decan-Movie-Setup.exe` or `Decan-Movie-Setup.msi`)
1. Download `Decan-Movie-Setup.exe` (or `Decan-Movie-Setup.msi`) from the [GitHub Releases](https://github.com/lennymuriuki/decan-movir/releases) page.
2. Double-click the installer and follow the setup wizard.
   - Creates a **Desktop shortcut** (`Decan Movie`).
   - Adds a **Start Menu** entry under `Decan Movie`.
   - Registers a standard Windows uninstaller under **Settings → Apps → Installed apps** (`Decan Movie`).
3. Launch **Decan Movie** from the Start Menu or Desktop shortcut.

### Option 2: Portable Version (`Decan-Movie-Portable.zip`)
1. Download `Decan-Movie-Portable.zip` from [GitHub Releases](https://github.com/lennymuriuki/decan-movir/releases).
2. Extract the archive to any folder (e.g., `C:\Apps\DecanMovie\` or a USB drive).
3. Run `Decan Movie.exe` inside the extracted `Decan Movie` folder.
4. Portable mode automatically keeps settings, watchlists, and playback history alongside the extracted directory when writable, or under `%LOCALAPPDATA%\DecanMovie\`.

## Updating Decan Movie on Windows
- **Automatic Update Check:** Decan Movie periodically checks `https://github.com/lennymuriuki/decan-movir/releases` (every 6 hours) for newer Windows `.exe`, `.msi`, or `-Portable.zip` packages.
- **Manual Check:** Open **Settings → Decan Movie Windows Updater → Check for Updates**.
- **Checksum Verification:** When `windows-checksums-sha256.txt` or SHA-256 digests are included on the release, the desktop updater verifies both the SHA-256 digest and the Windows PE (`MZ`) / MSI / ZIP binary header before launching the installer.
- **Android APKs Excluded:** The Windows updater strictly filters out `.apk` and `.aab` assets so only Windows packages are ever downloaded.

## Uninstalling
- **Installer version:** Open **Windows Settings → Apps → Installed apps → Decan Movie → Uninstall**.
- **Portable version:** Delete the extracted portable folder (and optionally `%LOCALAPPDATA%\DecanMovie` if you want to remove local settings/cache).
