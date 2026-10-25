#!/bin/bash
# Downloads the exact upstream sources this project builds from.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
mkdir -p "$SRC"
cd "$SRC"

WANT="$*"   # optional: aria2 zlib openssl c-ares

want() {
    [ -z "$WANT" ] && return 0
    case " $WANT " in *" $1 "*) return 0 ;; esac
    return 1
}

fetch() {
    local url="$1" out="$2" sha="$3"
    if [ -f "$out" ]; then
        echo "have   $out"
    else
        echo "fetch  $out"
        curl -fsSL -o "$out" "$url"
    fi
    if [ -n "$sha" ]; then
        echo "$sha  $out" | sha256sum -c - >/dev/null && echo "  sha256 ok"
    fi
}

want aria2 && fetch \
    https://github.com/aria2/aria2/releases/download/release-1.37.0/aria2-1.37.0.tar.gz \
    aria2-1.37.0.tar.gz \
    8e7021c6d5e8f8240c9cc19482e0c8589540836747744724d86bf8af5a21f0e8
want zlib && fetch https://zlib.net/fossils/zlib-1.3.1.tar.gz zlib-1.3.1.tar.gz ""
want openssl && fetch \
    https://github.com/openssl/openssl/releases/download/openssl-3.5.2/openssl-3.5.2.tar.gz \
    openssl-3.5.2.tar.gz ""
want c-ares && fetch \
    https://github.com/c-ares/c-ares/releases/download/v1.34.5/c-ares-1.34.5.tar.gz \
    c-ares-1.34.5.tar.gz ""

echo
ls -la "$SRC"/*.tar.gz
