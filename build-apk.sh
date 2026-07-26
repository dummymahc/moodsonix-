#!/usr/bin/env bash
# Build script for Moodsonix APK — auto-installs Android SDK if missing
set -e

export ANDROID_HOME=/home/runner/android-sdk
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

echo "=== Moodsonix Build ==="
echo "Java: $(java -version 2>&1 | head -1)"
echo ""

# ── Auto-install Android SDK if missing ─────────────────────────────────────
if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
  echo "Android SDK not found — installing…"

  CMDTOOLS_ZIP=/tmp/cmdtools.zip
  CMDTOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -fsSL "$CMDTOOLS_URL" -o "$CMDTOOLS_ZIP"
  unzip -q "$CMDTOOLS_ZIP" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm "$CMDTOOLS_ZIP"

  # Accept licences and install required SDK components
  yes | sdkmanager --licenses > /dev/null 2>&1 || true
  sdkmanager \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;35.0.0" \
    "ndk;27.0.12077973"

  echo "Android SDK installed."
fi

# Write local.properties so Gradle finds the SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

# ── Generate debug keystore if missing ──────────────────────────────────────
KEYSTORE="$HOME/.android/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  echo "Generating debug keystore…"
  mkdir -p "$HOME/.android"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" 2>&1
  echo "Debug keystore created."
fi

echo "ANDROID_HOME: $ANDROID_HOME"
echo ""

# ── Build ────────────────────────────────────────────────────────────────────
# Variants:
#   assembleUniversalFossDebug   - Debug APK, no Google Cast (recommended for dev)
#   assembleUniversalFossRelease - Release APK, no Google Cast (requires signing)
#   assembleUniversalGmsDebug    - Debug APK, with Google Cast
#   assembleUniversalGmsRelease  - Release APK, with Google Cast

VARIANT=${1:-assembleUniversalFossDebug}
echo "Building: $VARIANT"
echo ""

./gradlew $VARIANT --no-daemon 2>&1

echo ""
echo "=== Build complete ==="
find app/build/outputs/apk -name "*.apk" 2>/dev/null | while read apk; do
  echo "APK: $apk ($(du -sh "$apk" | cut -f1))"
done
