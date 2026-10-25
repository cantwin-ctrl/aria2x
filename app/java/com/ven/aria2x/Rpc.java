package com.ven.aria2x;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal JSON-RPC 2.0 client for a loopback aria2c. */
public final class Rpc {

    private final int port;
    private final String secret;

    public Rpc(int port, String secret) {
        this.port = port;
        this.secret = secret;
    }

    public static final class Item {
        public String gid = "", name = "", status = "", dir = "", error = "";
        public long total, done, downSpeed, upSpeed;
        public int conns;
        public boolean bt;

        public int pct() {
            if (total <= 0) return 0;
            long p = done * 100 / total;
            return (int) Math.max(0, Math.min(100, p));
        }
    }

    public static final class Snap {
        public long downSpeed, upSpeed;
        public int active, waiting, stopped;
        public final List<Item> items = new ArrayList<>();
    }

    // ---------- transport ----------

    private Object post(JSONObject req) throws IOException, JSONException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/jsonrpc").openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(5000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = drain(is);
            JSONObject res = new JSONObject(text);
            if (res.has("error") && !res.isNull("error")) {
                JSONObject e = res.optJSONObject("error");
                throw new IOException(e != null ? e.optString("message", "rpc error") : "rpc error");
            }
            return res.opt("result");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String drain(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private JSONArray tokenParams() {
        JSONArray p = new JSONArray();
        if (secret != null && !secret.isEmpty()) p.put("token:" + secret);
        return p;
    }

    private Object call(String method, JSONArray more) throws IOException, JSONException {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", "aria2x");
        req.put("method", method);
        JSONArray p = tokenParams();
        if (more != null) {
            for (int i = 0; i < more.length(); i++) p.put(more.get(i));
        }
        req.put("params", p);
        return post(req);
    }

    // ---------- high level ----------

    public String version() throws IOException, JSONException {
        JSONObject o = (JSONObject) call("aria2.getVersion", null);
        return o.optString("version", "?");
    }

    public void addUri(List<String> uris, String dir) throws IOException, JSONException {
        JSONArray arr = new JSONArray();
        for (String u : uris) arr.put(u);
        JSONArray p = new JSONArray();
        p.put(arr);
        JSONObject opt = new JSONObject();
        if (dir != null && !dir.isEmpty()) opt.put("dir", dir);
        if (opt.length() > 0) p.put(opt);
        call("aria2.addUri", p);
    }

    public void addTorrent(String base64, String dir) throws IOException, JSONException {
        JSONArray p = new JSONArray();
        p.put(base64);
        JSONArray uris = new JSONArray();
        p.put(uris);
        JSONObject opt = new JSONObject();
        if (dir != null && !dir.isEmpty()) opt.put("dir", dir);
        if (opt.length() > 0) p.put(opt);
        call("aria2.addTorrent", p);
    }

    public void pause(String gid) throws IOException, JSONException {
        call("aria2.forcePause", new JSONArray().put(gid));
    }

    public void resume(String gid) throws IOException, JSONException {
        call("aria2.unpause", new JSONArray().put(gid));
    }

    public void remove(String gid) throws IOException, JSONException {
        call("aria2.forceRemove", new JSONArray().put(gid));
    }

    public void purge(String gid) throws IOException, JSONException {
        try {
            call("aria2.removeDownloadResult", new JSONArray().put(gid));
        } catch (IOException ignored) {
        }
    }

    public void pauseAll() throws IOException, JSONException {
        call("aria2.forcePauseAll", null);
    }

    public void resumeAll() throws IOException, JSONException {
        call("aria2.unpauseAll", null);
    }

    public void purgeAll() throws IOException, JSONException {
        call("aria2.purgeDownloadResult", null);
    }

    public void changeGlobal(JSONObject opts) throws IOException, JSONException {
        call("aria2.changeGlobalOption", new JSONArray().put(opts));
    }

    /** One round-trip snapshot: active + waiting + stopped + global stat. */
    public Snap snapshot() throws IOException, JSONException {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", "aria2x");
        req.put("method", "system.multicall");

        JSONArray calls = new JSONArray();
        String tok = (secret == null || secret.isEmpty()) ? null : "token:" + secret;

        calls.put(mc("aria2.getGlobalStat", tok));
        calls.put(mc("aria2.tellActive", tok));
        calls.put(mc("aria2.tellWaiting", tok, 0, 200));
        calls.put(mc("aria2.tellStopped", tok, 0, 60));

        JSONArray params = new JSONArray();
        params.put(calls);
        req.put("params", params);

        Object result = post(req);
        if (!(result instanceof JSONArray)) throw new IOException("bad multicall result");
        JSONArray r = (JSONArray) result;

        if (r.optJSONObject(0) != null && r.optJSONObject(0).has("code")) {
            throw new IOException(r.optJSONObject(0).optString("message"));
        }

        Snap s = new Snap();
        JSONObject g = unwrap(r, 0);
        if (g != null) {
            s.downSpeed = num(g, "downloadSpeed");
            s.upSpeed = num(g, "uploadSpeed");
            s.active = (int) num(g, "numActive");
            s.waiting = (int) num(g, "numWaiting");
            s.stopped = (int) num(g, "numStopped");
        }
        collect(unwrapArray(r, 1), s.items, 0);
        collect(unwrapArray(r, 2), s.items, 1);
        collect(unwrapArray(r, 3), s.items, 2);
        return s;
    }

    private static JSONObject mc(String name, String tok, Object... extra)
            throws JSONException {
        JSONObject o = new JSONObject();
        o.put("methodName", name);
        JSONArray p = new JSONArray();
        if (tok != null) p.put(tok);
        for (Object e : extra) p.put(e);
        o.put("params", p);
        return o;
    }

    private static JSONObject unwrap(JSONArray r, int i) {
        JSONArray a = r.optJSONArray(i);
        if (a == null) return null;
        return a.optJSONObject(0);
    }

    private static JSONArray unwrapArray(JSONArray r, int i) {
        JSONArray a = r.optJSONArray(i);
        if (a == null) return null;
        return a.optJSONArray(0);
    }

    private static void collect(JSONArray arr, List<Item> out, int bucket) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.gid = o.optString("gid");
            it.status = o.optString("status");
            it.dir = o.optString("dir");
            it.error = o.optString("errorMessage");
            it.total = num(o, "totalLength");
            it.done = num(o, "completedLength");
            it.downSpeed = num(o, "downloadSpeed");
            it.upSpeed = num(o, "uploadSpeed");
            it.conns = (int) num(o, "connections");

            JSONObject bt = o.optJSONObject("bittorrent");
            it.bt = bt != null;
            if (bt != null) {
                JSONObject info = bt.optJSONObject("info");
                if (info != null) it.name = info.optString("name", "");
            }
            if (it.name.isEmpty()) {
                JSONArray files = o.optJSONArray("files");
                if (files != null && files.length() > 0) {
                    String p = files.optJSONObject(0).optString("path", "");
                    int slash = p.lastIndexOf('/');
                    it.name = slash >= 0 ? p.substring(slash + 1) : p;
                }
            }
            if (it.name == null || it.name.isEmpty()) it.name = it.gid;
            if (it.bt && bucket == 2 && o.has("followedBy")) it.status = "complete";
            out.add(it);
        }
    }

    private static long num(JSONObject o, String k) {
        String v = o.optString(k, "0");
        try {
            return Long.parseLong(v);
        } catch (Exception e) {
            return 0;
        }
    }
}
