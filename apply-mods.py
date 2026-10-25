#!/usr/bin/env python3
"""Apply aria2x patches to aria2 1.37.0 source.

Each edit is exact-match and asserted. Idempotent: if the new text is already
present the edit is reported as "done" instead of failing, so re-running is safe.
"""
import sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent / "src" / "aria2-1.37.0"

OHF = "src/OptionHandlerFactory.cc"
PLAT = "src/Platform.cc"

MODS = [
    # ---- upstream limit relaxations -------------------------------------
    (OHF, "x: max-connection-per-server ceiling 16 -> 256",
     '''    OptionHandler* op(new NumberOptionHandler(PREF_MAX_CONNECTION_PER_SERVER,
                                              TEXT_MAX_CONNECTION_PER_SERVER,
                                              "1", 1, 16, 'x'));''',
     '''    OptionHandler* op(new NumberOptionHandler(PREF_MAX_CONNECTION_PER_SERVER,
                                              TEXT_MAX_CONNECTION_PER_SERVER,
                                              "1", 1, 256, 'x'));'''),

    (OHF, "k: min-split-size floor 1M -> 256K, default 20M -> 1M",
     '''        PREF_MIN_SPLIT_SIZE, TEXT_MIN_SPLIT_SIZE, "20M", 1_m, 1_g, 'k'));''',
     '''        PREF_MIN_SPLIT_SIZE, TEXT_MIN_SPLIT_SIZE, "1M", 256_k, 1_g, 'k'));'''),

    (OHF, "--piece-length floor 1M -> 256K, default 1M -> 256K",
     '''        PREF_PIECE_LENGTH, TEXT_PIECE_LENGTH, "1M", 1_m, 1_g));''',
     '''        PREF_PIECE_LENGTH, TEXT_PIECE_LENGTH, "256K", 256_k, 1_g));'''),

    (OHF, "--disk-cache default 16M -> 64M",
     '''                                                  "16M",
#endif
                                                  0));''',
     '''                                                  "64M",
#endif
                                                  0));'''),

    (OHF, "j: max-concurrent-downloads default 5 -> 10",
     '''    OptionHandler* op(new NumberOptionHandler(PREF_MAX_CONCURRENT_DOWNLOADS,
                                              TEXT_MAX_CONCURRENT_DOWNLOADS,
                                              "5", 1, -1, 'j'));''',
     '''    OptionHandler* op(new NumberOptionHandler(PREF_MAX_CONCURRENT_DOWNLOADS,
                                              TEXT_MAX_CONCURRENT_DOWNLOADS,
                                              "10", 1, -1, 'j'));'''),

    (OHF, "--connect-timeout default 60 -> 15",
     '''        PREF_CONNECT_TIMEOUT, TEXT_CONNECT_TIMEOUT, "60", 1, 600));''',
     '''        PREF_CONNECT_TIMEOUT, TEXT_CONNECT_TIMEOUT, "15", 1, 600));'''),

    (OHF, "m: max-tries default 5 -> 10",
     '''    OptionHandler* op(new NumberOptionHandler(PREF_MAX_TRIES, TEXT_MAX_TRIES,
                                              "5", 0, -1, 'm'));''',
     '''    OptionHandler* op(new NumberOptionHandler(PREF_MAX_TRIES, TEXT_MAX_TRIES,
                                              "10", 0, -1, 'm'));'''),

    # ---- Android robustness --------------------------------------------
    # The legacy provider only carries RC4, used by BitTorrent MSE. Treating a
    # missing provider as fatal makes the whole engine refuse to start, which is
    # far worse than losing RC4 encrypted peers.
    (PLAT, "openssl: legacy provider is optional, not fatal",
     '''  legacy_provider_ = OSSL_PROVIDER_load(nullptr, "legacy");
  if (!legacy_provider_) {
    throw DL_ABORT_EX("OSSL_PROVIDER_load 'legacy' failed.");
  }''',
     '''  legacy_provider_ = OSSL_PROVIDER_load(nullptr, "legacy");
  if (!legacy_provider_) {
    // RC4-only provider. Missing legacy degrades BT MSE to plaintext-capable
    // ciphers; it must never stop aria2 from starting.
    std::cerr << "aria2c: warning: OSSL_PROVIDER_load 'legacy' failed; "
                 "RC4 (BitTorrent MSE) unavailable" << std::endl;
  }'''),
]


def main():
    by_file = {}
    for f, label, old, new in MODS:
        by_file.setdefault(f, []).append((label, old, new))

    patched = skipped = 0
    for rel, items in by_file.items():
        path = ROOT / rel
        if not path.exists():
            sys.exit(f"missing {path}")
        text = path.read_text()
        orig = text
        for label, old, new in items:
            n_old, n_new = text.count(old), text.count(new)
            if n_old == 1:
                text = text.replace(old, new, 1)
                print(f"  patch  {label}")
                patched += 1
            elif n_old == 0 and n_new == 1:
                print(f"  done   {label}")
                skipped += 1
            else:
                sys.exit(f"FAIL [{label}]: old={n_old} new={n_new} in {rel}")
        if text != orig:
            bak = path.with_suffix(path.suffix + ".orig")
            if not bak.exists():
                bak.write_text(orig)
            path.write_text(text)
            print(f"wrote {path}")

    print(f"\n{patched} patched, {skipped} already applied")


if __name__ == "__main__":
    main()
