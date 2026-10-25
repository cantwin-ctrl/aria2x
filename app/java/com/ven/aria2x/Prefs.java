package com.ven.aria2x;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Option store + aria2.conf emitter.
 * Every field declared here is rendered automatically by SettingsActivity.
 */
public final class Prefs {

    public static final int TEXT = 0, NUM = 1, CHOICE = 2, SWITCH = 3, SECTION = 4, MULTI = 5;

    public static final class Field {
        public final String key, label, hint, def;
        public final int type;
        public final String[] choices;

        Field(String key, String label, String hint, String def, int type, String... choices) {
            this.key = key; this.label = label; this.hint = hint; this.def = def;
            this.type = type; this.choices = choices;
        }
    }

    static Field section(String title) {
        return new Field(title, title, null, null, SECTION);
    }

    /** Rendered top-to-bottom in settings. */
    public static final Field[] FIELDS = {
        section("ENGINE"),
        new Field("dir", "Download directory", "absolute path", "", TEXT),
        new Field("maxConcurrent", "Max concurrent downloads", "-j", "10", NUM),
        new Field("maxConnPerServer", "Connections per server", "-x  (stock cap: 16 / mod cap: 256)", "16", NUM),
        new Field("split", "Split count", "-s  pieces per download", "16", NUM),
        new Field("minSplitSize", "Min split size", "-k  floor lowered to 256K by mod", "1M", TEXT),
        new Field("pieceLength", "Piece length", "split granularity (256K mod default)", "256K", TEXT),
        new Field("diskCache", "Disk cache", "mod default 64M (stock 16M)", "64M", TEXT),
        new Field("fileAllocation", "File allocation", null, "prealloc", CHOICE,
                "prealloc", "none", "trunc", "falloc"),
        new Field("logLevel", "Log level", null, "notice", CHOICE,
                "debug", "info", "notice", "warn", "error"),

        section("SPEED & RETRY"),
        new Field("maxOverallDown", "Overall download limit", "e.g. 5M  (0 = unlimited)", "0", TEXT),
        new Field("maxDownPerDownload", "Per-download limit", "e.g. 2M  (0 = unlimited)", "0", TEXT),
        new Field("maxOverallUp", "Overall upload limit", "0 = unlimited", "0", TEXT),
        new Field("connectTimeout", "Connect timeout", "seconds (mod default 15)", "15", NUM),
        new Field("timeout", "Socket timeout", "seconds", "60", NUM),
        new Field("maxTries", "Max tries", "mod default 10", "10", NUM),
        new Field("retryWait", "Retry wait", "seconds", "1", NUM),
        new Field("lowestSpeedLimit", "Lowest speed limit", "0 = off", "0", TEXT),

        section("NETWORK"),
        new Field("userAgent", "User-Agent", null, "aria2/VenMod", TEXT),
        new Field("header", "Extra header", "Name: value", "", TEXT),
        new Field("proxy", "Proxy", "http://host:port or socks5://host:port", "", TEXT),
        new Field("checkCert", "Verify TLS certificate", null, "1", SWITCH),
        new Field("caCert", "CA bundle", null, "bundled", CHOICE,
                "bundled", "system", "off"),
        new Field("asyncDns", "Async DNS (c-ares)", null, "0", SWITCH),
        new Field("socketRecvBuf", "Socket recv buffer", "0 = system default", "0", TEXT),
        new Field("maxConnTotal", "Global connection ceiling", "0 = unlimited", "0", TEXT),

        section("BITTORRENT"),
        new Field("btMaxPeers", "Max peers", null, "100", NUM),
        new Field("seedRatio", "Seed ratio", "0 = seed forever", "0", TEXT),
        new Field("seedTime", "Seed time", "minutes, 0 = forever", "0", NUM),
        new Field("enableDht", "Enable DHT", "needed for magnets", "1", SWITCH),
        new Field("btRequireCrypto", "Require encryption", null, "0", SWITCH),
        new Field("btSaveMetadata", "Save .torrent metadata", null, "1", SWITCH),

        section("STORAGE"),
        new Field("continueDownload", "Resume partial files", null, "1", SWITCH),
        new Field("autoRename", "Auto rename duplicates", null, "1", SWITCH),
        new Field("remoteTime", "Use remote file time", null, "0", SWITCH),
        new Field("saveSession", "Persist queue across restarts", null, "1", SWITCH),

        section("ADVANCED"),
        new Field("extraOptions", "Raw aria2 options", "one key=value per line", "", MULTI),
        new Field("rpcPort", "JSON-RPC port", "loopback only", "6800", NUM),
    };

    private static final Map<String, String> DEF = new LinkedHashMap<>();
    static {
        for (Field f : FIELDS) if (f.type != SECTION) DEF.put(f.key, f.def);
    }

    public static String def(String key) { return DEF.get(key); }

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("opts", Context.MODE_PRIVATE);
    }

    public String get(String key) {
        return sp.getString(key, DEF.containsKey(key) ? DEF.get(key) : "");
    }

    public int getInt(String key) {
        try { return Integer.parseInt(get(key).trim()); } catch (Exception e) { return 0; }
    }

    public boolean getBool(String key) {
        String v = get(key);
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    public void set(String key, String value) { sp.edit().putString(key, value).apply(); }

    public String secret() {
        String s = sp.getString("rpcSecret", null);
        if (s == null || s.length() < 16) {
            StringBuilder sb = new StringBuilder();
            java.util.Random r = new java.util.Random();
            for (int i = 0; i < 32; i++) sb.append("0123456789abcdef".charAt(r.nextInt(16)));
            s = sb.toString();
            sp.edit().putString("rpcSecret", s).apply();
        }
        return s;
    }

    public int port() {
        int p = getInt("rpcPort");
        return (p >= 1024 && p <= 65535) ? p : 6800;
    }

    public File defaultDir(Context c) {
        if (hasAllFilesAccess()) {
            File pub = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (pub != null) {
                File d = new File(pub, "aria2x");
                if (ensureDir(d)) return d;
            }
        }
        File ext = c.getExternalFilesDir(null);
        if (ext != null) {
            File d = new File(ext, "Downloads");
            if (ensureDir(d)) return d;
        }
        File internal = new File(c.getFilesDir(), "Downloads");
        ensureDir(internal);
        return internal;
    }

    /** Optional: lets the app write /sdcard/Download instead of app storage. */
    public static boolean hasAllFilesAccess() {
        try {
            return android.os.Environment.isExternalStorageManager();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean ensureDir(File d) {
        try {
            if (d.isDirectory()) return true;
            return d.mkdirs() || d.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    /** Always returns an existing, absolute, writable directory. */
    public File downloadDir(Context c) {
        String p = get("dir").trim();
        if (p.startsWith("/")) {
            File f = new File(p);
            if (ensureDir(f) && f.isDirectory() && f.canWrite()) return f;
        }
        return defaultDir(c);
    }

    /** Writes aria2.conf; returns the file. */
    public File writeConf(Context c) throws IOException {
        File dir = downloadDir(c);
        StringBuilder b = new StringBuilder();
        b.append("# generated by aria2x\n");
        b.append("dir=").append(dir.getAbsolutePath()).append('\n');
        b.append("continue=").append(getBool("continueDownload")).append('\n');
        b.append("auto-file-renaming=").append(getBool("autoRename")).append('\n');
        b.append("remote-time=").append(getBool("remoteTime")).append('\n');

        b.append("max-concurrent-downloads=").append(nz(get("maxConcurrent"), "10")).append('\n');
        b.append("max-connection-per-server=").append(nz(get("maxConnPerServer"), "16")).append('\n');
        b.append("split=").append(nz(get("split"), "16")).append('\n');
        b.append("min-split-size=").append(nz(get("minSplitSize"), "1M")).append('\n');
        b.append("piece-length=").append(nz(get("pieceLength"), "256K")).append('\n');
        b.append("disk-cache=").append(nz(get("diskCache"), "64M")).append('\n');
        b.append("file-allocation=").append(nz(get("fileAllocation"), "prealloc")).append('\n');

        b.append("max-overall-download-limit=").append(nz(get("maxOverallDown"), "0")).append('\n');
        b.append("max-download-limit=").append(nz(get("maxDownPerDownload"), "0")).append('\n');
        b.append("max-overall-upload-limit=").append(nz(get("maxOverallUp"), "0")).append('\n');
        b.append("connect-timeout=").append(nz(get("connectTimeout"), "15")).append('\n');
        b.append("timeout=").append(nz(get("timeout"), "60")).append('\n');
        b.append("max-tries=").append(nz(get("maxTries"), "10")).append('\n');
        b.append("retry-wait=").append(nz(get("retryWait"), "1")).append('\n');
        b.append("lowest-speed-limit=").append(nz(get("lowestSpeedLimit"), "0")).append('\n');

        String ua = get("userAgent").trim();
        if (!ua.isEmpty()) b.append("user-agent=").append(ua).append('\n');
        String hdr = get("header").trim();
        if (!hdr.isEmpty()) b.append("header=").append(hdr).append('\n');
        String proxy = get("proxy").trim();
        if (!proxy.isEmpty()) b.append("all-proxy=").append(proxy).append('\n');
        b.append("check-certificate=").append(getBool("checkCert")).append('\n');
        String ca = caCertPath(c);
        if (ca != null) b.append("ca-certificate=").append(ca).append('\n');
        b.append("async-dns=").append(getBool("asyncDns")).append('\n');
        b.append("socket-recv-buffer-size=").append(nz(get("socketRecvBuf"), "0")).append('\n');

        b.append("bt-max-peers=").append(nz(get("btMaxPeers"), "100")).append('\n');
        b.append("seed-ratio=").append(nz(get("seedRatio"), "0")).append('\n');
        b.append("seed-time=").append(nz(get("seedTime"), "0")).append('\n');
        b.append("enable-dht=").append(getBool("enableDht")).append('\n');
        b.append("bt-require-crypto=").append(getBool("btRequireCrypto")).append('\n');
        b.append("bt-save-metadata=").append(getBool("btSaveMetadata")).append('\n');
        b.append("bt-enable-lpd=false\n");
        b.append("listen-port=6881-6999\ndht-listen-port=6881-6999\n");

        b.append("save-session=").append(new File(c.getFilesDir(), "aria2.session").getAbsolutePath()).append('\n');
        b.append("save-session-interval=30\nauto-save-interval=30\n");
        b.append("log=").append(new File(c.getFilesDir(), "aria2.log").getAbsolutePath()).append('\n');
        b.append("log-level=").append(nz(get("logLevel"), "notice")).append('\n');
        b.append("console-log-level=warn\n");
        b.append("summary-interval=0\n");
        b.append("enable-rpc=true\nrpc-listen-all=false\nrpc-allow-origin-all=true\n");
        b.append("rpc-listen-port=").append(port()).append('\n');
        b.append("rpc-secret=").append(secret()).append('\n');

        for (String line : get("extraOptions").split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
            b.append(t).append('\n');
        }

        File f = new File(c.getFilesDir(), "aria2.conf");
        FileOutputStream out = new FileOutputStream(f);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.close();
        return f;
    }

    private static String nz(String v, String d) {
        String t = v == null ? "" : v.trim();
        return t.isEmpty() ? d : t;
    }

    /** bundled -> extracted asset file, system -> Android CA dir, off -> null */
    public String caCertPath(Context c) {
        String mode = get("caCert");
        if ("off".equals(mode)) return null;
        if ("system".equals(mode)) return "/system/etc/security/cacerts";
        File f = new File(c.getFilesDir(), "cacert.pem");
        return f.exists() ? f.getAbsolutePath() : null;
    }

    public String summary() {
        return String.format(Locale.US, "-x %s  -s %s  -k %s  -j %s",
                get("maxConnPerServer"), get("split"), get("minSplitSize"), get("maxConcurrent"));
    }
}
