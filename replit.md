# Moodsonix

A modern Android music app with ad-free streaming, synced lyrics, offline playback, mood-based AI playlist generation, and an intuitive user experience. Streams from YouTube Music's library. Built by Abhinav K Tony, based on the imported open-source project (GPL-3.0).

## Stack

- **Language:** Kotlin
- **Build system:** Gradle (wrapper: 9.3.1)
- **Min SDK:** 26 | **Target/Compile SDK:** 36
- **JDK:** 21 (OpenJDK)
- **NDK:** 27.0.12077973

## How to Build an APK

Run the build script from the Shell:

```bash
# Debug APK (no Google Cast, recommended for development)
bash build-apk.sh assembleUniversalFossDebug

# Release APK (requires signing keys — see SETUP.md)
bash build-apk.sh assembleUniversalFossRelease

# Debug APK with Google Cast
bash build-apk.sh assembleUniversalGmsDebug
```

The first build downloads all Gradle dependencies and will take **10–20 minutes**. Subsequent builds are much faster due to caching.

Output APKs appear in `app/build/outputs/apk/`.

## Environment

- **Android SDK:** `/home/runner/android-sdk`
- **SDK Config:** `local.properties` (already configured, do not commit)
- **Firebase:** Optional — place `app/google-services.json` to enable analytics/crash reporting

## Key Source Directories

- `app/src/main/kotlin/` — Kotlin source code
- `app/src/main/res/` — Resources (layouts, drawables, strings)
- `app/src/main/AndroidManifest.xml` — App manifest

## Build Variants

| Variant | Cast | Use |
|---------|------|-----|
| `UniversalFossDebug` | No | Development / sideloading |
| `UniversalFossRelease` | No | Production (no Google services) |
| `UniversalGmsDebug` | Yes | Development with Cast |
| `UniversalGmsRelease` | Yes | Production with Cast |

## Release Signing (Optional)

Set these env vars to sign release builds (see SETUP.md):
- `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

## User Preferences

- Make code changes and build APKs on Replit
