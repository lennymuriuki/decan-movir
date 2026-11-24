# Decan Movie (Android + Windows Desktop)

**Decan Movie** is a cinema streaming suite built with Kotlin & Jetpack Compose / Compose Multiplatform across **Android** (`:app`) and **Windows 10/11 64-bit Desktop** (`:desktop`), featuring:

- **Movies, Series, Anime & Dramas** discovery, multi-provider catalog search, personal Watchlists, Watch History resume, and organized offline downloads with synchronized `.srt` captions.
- **Dedicated Theater Player** (Movies, Series, Anime, Local Files) with fullscreen mode, seek/volume/speed controls, audio dubs, `.srt` Closed Captions, aspect ratios, and keyboard shortcuts.
- **TikTok-Style Vertical Drama Player (Dramas Only)** with portrait 9:16 staging, episode-by-episode vertical swipe / mouse wheel / arrow key navigation, per-episode resume memory, and next-episode preloading.
- **Independent GitHub Release Updaters** for both Android (`.apk`) and Windows Desktop (`.exe` / `.msi` / `-Portable.zip` with SHA-256 verification).

---

## Documentation
- [Windows Installation Guide](docs/WINDOWS_INSTALLATION.md)
- [User Guide (Desktop & Playback Modes)](docs/USER_GUIDE.md)
- [Developer & Build Guide](docs/DEVELOPER_GUIDE.md)

---

## Download Builds & Automated GitHub Releases

1. **Windows Desktop (`windows-latest` CI)**:
   - Every push/PR runs `.github/workflows/windows-desktop-build.yml` to test and build **`Decan-Movie-Setup.exe`**, **`Decan-Movie-Setup.msi`**, and **`Decan-Movie-Portable.zip`**.
   - Tagging a release (`v*` or `win-v*`) or triggering **Release Decan Movie Windows Desktop** attaches the Windows installers, portable ZIP, and `windows-checksums-sha256.txt` to the GitHub Release alongside the Android APKs.
2. **Android APK (`ubuntu-latest` CI)**:
   - Open **Actions → Build Decan Movie APK** to download **`decan-movie-debug-apk`** from workflow artifacts.
   - Tag and push a version (`git tag v1.1.0 && git push origin v1.1.0`), **or** open **Actions → Release Decan Movie → Run workflow** to publish `decan-movie-<tag>-debug.apk` (plus signed Release APK & AAB when signing secrets are configured).

> **Note on Firebase & Google Sign-In (Android):** Pushing from AI Studio omits `app/google-services.json` by default. Workflows still compile working APKs and Windows installers without it; add a repository secret named **`GOOGLE_SERVICES_JSON`** if you want Google Sign-In & Firestore sync in CI-built Android APKs.

---

## Integrated Features & APIs

- **Dedicated Theater Player**: Portrait/landscape on Android; resizable widescreen + fullscreen (`F`/`F11`) on Windows Desktop with `-10s`/`+10s` seek, Next/Prev Episode, synchronized `.srt` Closed Captions (`CC`), resolution switcher (`1080p`/`720p`/`480p`), speed (`0.5x–2.0x`), Fit/Fill/16:9/21:9 aspect modes, audio dubs, and local/external playback.
- **TikTok-Style Vertical Drama Player**: Dedicated vertical swipe feed exclusively for episodic dramas (`DramaExperienceDetector`), retaining per-episode resume timestamps and preloading upcoming episodes.
- **Structured Offline Downloads**: Automatically saves downloads under `Downloads/DecanMovie/<Movies|Series|Anime|Dramas>/<Title>/Season XX/` alongside matching `.srt` subtitle files.
- **MovieBox Mobile & H5 BFF + Jikan/AniList/TVMaze + iTunes**: Multi-source catalog discovery, Top Rankings, genre/region filters, recommendations, and fast multi-provider search.
