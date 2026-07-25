#!/usr/bin/env bash
# Build script for Echo Music APK
set -e

export ANDROID_HOME=/home/runner/android-sdk
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

echo "=== Echo Music Build ==="
echo "Java: $(java -version 2>&1 | head -1)"
echo "ANDROID_HOME: $ANDROID_HOME"
echo ""

# Choose build variant:
#   assembleUniversalFossDebug   - Debug APK, no Google Cast (recommended for dev)
#   assembleUniversalFossRelease - Release APK, no Google Cast (requires signing keys)
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
