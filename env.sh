#!/bin/sh
# aria2-android — arm64-v8a cross build environment
set -e

export NDK="${NDK:-$HOME/android-ndk-r27d}"
export API="${API:-24}"
export TARGET="aarch64-linux-android"
export TRIPLE="${TARGET}${API}"
export TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
export SYSROOT="$TC/sysroot"

export CC="$TC/bin/${TRIPLE}-clang"
export CXX="$TC/bin/${TRIPLE}-clang++"
export AR="$TC/bin/llvm-ar"
export RANLIB="$TC/bin/llvm-ranlib"
export STRIP="$TC/bin/llvm-strip"
export NM="$TC/bin/llvm-nm"

ROOT="$HOME/rev/aria2-android"
export PREFIX="$ROOT/build/arm64"
export SRC="$ROOT/src"
export DIST="$ROOT/dist"

export CFLAGS="-O3 -fPIC -ffunction-sections -fdata-sections"
export CXXFLAGS="$CFLAGS -std=c++17"
export LDFLAGS="-Wl,--gc-sections -static-libstdc++ -L$PREFIX/lib"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"

mkdir -p "$PREFIX" "$DIST"
