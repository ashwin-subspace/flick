#!/usr/bin/env bash
# Gradle-free Android build: aapt2 -> javac -> d8 -> aapt add -> zipalign -> apksigner
set -euo pipefail

has_javac() {
  [ -n "${1:-}" ] && { [ -x "$1/bin/javac" ] || [ -x "$1/bin/javac.exe" ]; }
}

# Prefer JAVA_HOME. Fall back to this machine's JDKs only when that one is missing.
if ! has_javac "${JAVA_HOME:-}"; then
  if has_javac "F:/android studio/jbr"; then
    export JAVA_HOME="F:/android studio/jbr"
  elif has_javac "C:/Program Files/Android/openjdk/jdk-21.0.8"; then
    export JAVA_HOME="C:/Program Files/Android/openjdk/jdk-21.0.8"
  else
    echo "No JDK found. Set JAVA_HOME to a full JDK." >&2
    exit 1
  fi
fi

if [ -n "${ANDROID_HOME:-}" ]; then
  SDK="$ANDROID_HOME"
elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  SDK="$ANDROID_SDK_ROOT"
elif [ -d "C:/Users/hopel/AppData/Local/Android/Sdk/build-tools/36.0.0" ]; then
  SDK="C:/Users/hopel/AppData/Local/Android/Sdk"
elif [ -d "F:/CLAUDE_PROJECTS/.sdk" ]; then
  SDK="F:/CLAUDE_PROJECTS/.sdk"
else
  SDK="C:/Users/hopel/AppData/Local/Android/Sdk"
fi

BT="$SDK/build-tools/36.0.0"
AJ="$SDK/platforms/android-36/android.jar"
KS="${HOME}/.android/debug.keystore"
OUT=build
APK=Flick-debug.apk

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/7] aapt2 compile"
"$BT/aapt2.exe" compile --dir res -o "$OUT/res.zip"

echo "[2/7] aapt2 link"
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$AJ" --manifest AndroidManifest.xml -R "$OUT/res.zip" --java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 36 --version-code 1 --version-name 1.0 --auto-add-overlay

echo "[3/7] javac"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
set +e
"$JAVA_HOME/bin/javac" -nowarn -Xlint:none -source 8 -target 8 -bootclasspath "$AJ" -classpath "$AJ" -d "$OUT/classes" "@$OUT/sources.txt" >"$OUT/javac.log" 2>&1
javac_status=$?
set -e
grep -v "^warning:" "$OUT/javac.log" | grep -v "bootstrap class path" || true
if [ "$javac_status" -ne 0 ]; then
  echo "javac failed" >&2
  exit 1
fi

echo "[4/7] jar + d8"
"$JAVA_HOME/bin/jar" cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8.bat" --min-api 24 --output "$OUT/dex" "$OUT/classes.jar"

echo "[5/7] add dex"
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"
( cd "$OUT" && "$BT/aapt.exe" add -f base.apk classes.dex >/dev/null )

echo "[6/7] zipalign"
"$BT/zipalign.exe" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7/7] sign"
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "$APK" "$OUT/aligned.apk"

echo "OK -> $APK  ($(stat -c%s "$APK") bytes)"
