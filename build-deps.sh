#!/bin/bash
# Build static zlib + openssl + c-ares for aarch64 Android
set -euo pipefail
. "$(dirname "$0")/env.sh"

JOBS=$(nproc)
cd "$SRC"

echo "===== zlib skipped (built) ====="; #
true
cd zlib-1.3.1
true
true
cd "$SRC"

echo "===== openssl 3.5.2 ====="
rm -rf openssl-3.5.2 && tar xf openssl-3.5.2.tar.gz
cd openssl-3.5.2
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

echo "===== c-ares 1.34.5 ====="
rm -rf c-ares-1.34.5 && tar xf c-ares-1.34.5.tar.gz
cd c-ares-1.34.5
./configure --host="$TARGET" --prefix="$PREFIX" \
    --disable-shared --enable-static \
    --disable-tests --disable-tools \
    CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
    CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS"
make -j"$JOBS"
make install
cd "$SRC"

echo "===== deps done ====="
ls -la "$PREFIX/lib"/*.a
