---
name: Android SDK build environment
description: Replit session persistence issues for the Moodsonix Android build and how build-apk.sh handles them.
---

## Rule
`/home/runner` is NOT persisted between Replit sessions. Both the Android SDK (`/home/runner/android-sdk`) and the debug keystore (`/home/runner/.android/debug.keystore`) are wiped on every new session.

**Why:** Replit only persists the workspace (`/home/runner/workspace`). Everything else under `/home/runner` resets.

**How to apply:** `build-apk.sh` already handles this — it checks for the SDK and keystore before invoking Gradle and reinstalls/regenerates them if missing. Do not remove those checks. If the build fails with "SDK location not found" or "Keystore file not found", the fix is already in the script — just restart the Build APK workflow.

## Details
- SDK install URL: `https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip`
- SDK components needed: `platform-tools`, `platforms;android-36`, `build-tools;35.0.0`, `ndk;27.0.12077973`
- Keystore: generated with `keytool`, alias `androiddebugkey`, storepass/keypass both `android`
- `local.properties` (`sdk.dir=...`) is in the workspace so it persists, but the path it points to does not — `build-apk.sh` rewrites it each run anyway.
- On the 7.8 GiB Replit runner, a full APK build may need `--max-workers=2` with reduced Gradle/Kotlin daemon heaps; the source can compile successfully under those limits even when the default 4 GiB daemon is killed.
