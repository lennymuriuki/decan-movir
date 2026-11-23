# Decan Movie Windows Desktop — Implementation Notes

This repository now contains two separated application targets:

- `app/` — the existing Android application.
- `desktop/` — a Windows 10/11 x64 Electron desktop application.

The desktop client has its own packaging, icon, local settings/history/watchlist storage, native Windows file selection, tray support, notifications, GitHub release update checker, SHA-256 verification, NSIS installer and portable target.

## Existing Android compatibility

No Android source files were removed or converted to desktop code. The desktop target does not depend on the Android Gradle module at runtime. Existing Android workflows remain in `.github/workflows/android-build.yml` and `.github/workflows/android-release.yml`.

## Release strategy

Tagging `v1.0.4` (for example) can continue to publish Android assets through the existing Android release workflow. The Windows workflow then uploads `Decan-Movie-1.0.4-*.exe` plus the checksum manifest to the same GitHub Release. This preserves both platform assets under one recognizable version tag.

## Testing performed in this source package

- JavaScript syntax validation with Node `--check`.
- Package configuration review.
- Android source structure review before adding the desktop target.
- Release workflow review for Android/Windows asset separation.

A signed Windows installer cannot be truthfully claimed as built inside this Linux authoring environment. The included Windows GitHub Actions workflow is the reproducible build path and produces the `.exe` installer and portable executable on `windows-latest`.
