#!/bin/bash
# Build the static dependencies aria2c links against: zlib, OpenSSL, c-ares.
# All three are cross-compiled for aarch64 Android and installed into $PREFIX.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)" && export ROOT
. "$ROOT/env.sh"

JOBS=$(nproc)
cd "$SRC"

ZLIB=zlib-1.3.1
OPENSSL=openssl-3.5.2
CARES=c-ares-1.34.5

for t in "$ZLIB" "$OPENSSL" "$CARES"; do
    [ -f "$t.tar.gz" ] || { echo "missing $t.tar.gz — run ./fetch-sources.sh first"; exit 1; }
done

echo "===== $ZLIB ====="
rm -rf "$ZLIB" && tar xf "$ZLIB.tar.gz"
cd "$ZLIB"
CHOST="$TARGET" ./configure --prefix="$PREFIX" --static
make -j"$JOBS"
make install
cd "$SRC"

echo "===== $OPENSSL ====="
rm -rf "$OPENSSL" && tar xf "$OPENSSL.tar.gz"
cd "$OPENSSL"
# OpenSSL's android targets probe $PATH for the NDK toolchain; the wrapper
# compilers must come first or it dies with "no NDK aarch64-linux-android-gcc".
export ANDROID_NDK_ROOT="$NDK"
export PATH="$TC/bin:$PATH"
./Configure android-arm64 \
    -D__ANDROID_API__="$API" \
    --prefix="$PREFIX" \
    --openssldir="$PREFIX/ssl" \
    no-shared no-tests no-apps no-docs \
    -O3 -ffunction-sections -fdata-sections
make -j"$JOBS" build_libs
make install_dev
cd "$SRC"

echo "===== $CARES ====="
rm -rf "$CARES" && tar xf "$CARES.tar.gz"
cd "$CARES"
./configure --host="$TARGET" --prefix="$PREFIX" \
    --disable-shared --enable-static \
    --disable-tests --disable-tools \
    CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
    CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS"
make -j"$JOBS"
make install
cd "$SRC"

echo "===== deps done ====="
for lib in libz.a libssl.a libcrypto.a libcares.a; do
    [ -f "$PREFIX/lib/$lib" ] || { echo "FAIL: $lib was not produced"; exit 1; }
    ls -la "$PREFIX/lib/$lib"
done
