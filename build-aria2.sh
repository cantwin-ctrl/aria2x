#!/bin/bash
# Cross-compile modded aria2c for aarch64 Android
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)" && export ROOT
. "$ROOT/env.sh"

JOBS=$(nproc)
cd "$SRC"

if [ ! -d aria2-1.37.0 ]; then
    [ -f aria2-1.37.0.tar.gz ] || { echo "missing aria2-1.37.0.tar.gz — run ./fetch-sources.sh"; exit 1; }
    echo "===== extracting aria2 ====="
    tar xf aria2-1.37.0.tar.gz
fi
cd aria2-1.37.0

if [ "${SKIP_MODS:-0}" != "1" ]; then
    echo "===== applying mods ====="
    python3 "$ROOT/apply-mods.py"
fi

echo "===== configure ====="
make distclean >/dev/null 2>&1 || true
./configure \
    --host="$TARGET" \
    --prefix="$PREFIX" \
    --disable-nls \
    --without-gnutls \
    --without-libnettle \
    --without-libxml2 \
    --without-libexpat \
    --without-libssh2 \
    --without-sqlite3 \
    --with-openssl \
    --with-libcares \
    --with-zlib="$PREFIX" \
    CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" NM="$NM" \
    CFLAGS="$CFLAGS" CXXFLAGS="$CXXFLAGS" LDFLAGS="$LDFLAGS" \
    2>&1 | tee /tmp/aria2-configure.log | tail -30

echo "===== make ====="
grep -E "^  (OpenSSL|Zlib|c-ares|SSL|libaria2)" -A1 /tmp/aria2-configure.log || true
# Build only the websocket helper and the binary — skips po/ and doc/.
make -j"$JOBS" -C deps/wslay 2>&1 | tee /tmp/aria2-build.log | tail -5
make -j"$JOBS" -C src aria2c 2>&1 | tee -a /tmp/aria2-build.log | tail -20

echo "===== result ====="
ls -la src/aria2c
"$STRIP" --strip-unneeded src/aria2c -o "$DIST/aria2c"
file "$DIST/aria2c" 2>/dev/null || true
ls -la "$DIST/aria2c"
