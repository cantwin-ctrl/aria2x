#!/bin/bash
# Build the APK with aapt2 + javac + d8 + apksigner. No Gradle, no AndroidX.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
APP="$ROOT/app"
DIST="$ROOT/dist"
B="$ROOT/build/apk"
PKG=com.ven.aria2x

[ -f "$DIST/aria2c" ] || { echo "missing $DIST/aria2c — run build-aria2.sh first"; exit 1; }

echo "== icon =="
python3 "$ROOT/make-icon.py"

echo "== CA bundle =="
mkdir -p "$APP/assets"
if [ ! -s "$APP/assets/cacert.pem" ]; then
    curl -fsSL -o "$APP/assets/cacert.pem" https://curl.se/ca/cacert.pem
fi
grep -c "BEGIN CERTIFICATE" "$APP/assets/cacert.pem"

echo "== clean =="
rm -rf "$B"
mkdir -p "$B/obj" "$B/pkg" "$B/pkg/lib/arm64-v8a" "$B/pkg/assets"

echo "== native =="
cp "$DIST/aria2c" "$B/pkg/lib/arm64-v8a/libaria2c.so"
chmod 755 "$B/pkg/lib/arm64-v8a/libaria2c.so"

echo "== assets =="
cp "$APP/assets/"*.pem "$APP/assets/"*.txt "$B/pkg/assets/"

echo "== resources =="
"$BT/aapt2" compile --dir "$APP/res" -o "$B/res.zip"
"$BT/aapt2" link \
    -o "$B/base.apk" \
    -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    -R "$B/res.zip" \
    --min-sdk-version 24 --target-sdk-version 34 \
    --auto-add-overlay \
    --no-version-vectors

echo "== java =="
find "$APP/java" -name '*.java' > "$B/sources.txt"
javac -nowarn -source 11 -target 11 \
    -classpath "$PLATFORM" \
    -d "$B/obj" @"$B/sources.txt"

echo "== dex =="
(cd "$B/obj" && jar cf "$B/classes.jar" .)
"$BT/d8" --release --min-api 24 --lib "$PLATFORM" --output "$B" "$B/classes.jar"
cp "$B/classes.dex" "$B/pkg/classes.dex"

echo "== package =="
cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/pkg" && zip -q -r -X "$B/unsigned.apk" classes.dex lib assets)
"$BT/zipalign" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"

echo "== sign =="
KS="$ROOT/keystore.jks"
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -alias aria2x \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -storepass aria2x -keypass aria2x \
        -dname "CN=aria2x, O=ven, C=KH" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:aria2x --key-pass pass:aria2x \
    --ks-key-alias aria2x --out "$DIST/aria2x.apk" "$B/aligned.apk"
"$BT/apksigner" verify --print-certs "$DIST/aria2x.apk" | head -4

echo "== done =="
ls -la "$DIST/aria2x.apk"
"$BT/aapt2" dump badging "$DIST/aria2x.apk" 2>/dev/null | head -5
