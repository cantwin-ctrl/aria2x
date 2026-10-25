package com.ven.aria2x;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private Prefs prefs;
    private Rpc rpc;

    private TextView dot, statDown, statUp, statCount, engineNote, emptyHint;
    private TextView storageBanner;
    private EditText urlInput;
    private LinearLayout list;
    private ProgressBar engineBar;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, Row> rows = new HashMap<>();

    private volatile boolean alive;
    private boolean firstPaint = true;

    // ------------------------------------------------------------------ model

    private static final class Row {
        LinearLayout root;
        TextView name, meta, pct, act;
        ProgressBar bar;
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setBackgroundDrawableResource(android.R.color.black);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);

        prefs = new Prefs(this);
        rpc = new Rpc(prefs.port(), prefs.secret());
        Ui.typefaces();

        setContentView(buildUi());
        askNotifications();
        askStorageOnce();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // settings may have changed the port
        rpc = new Rpc(prefs.port(), prefs.secret());
        String nowDir = prefs.downloadDir(this).getAbsolutePath();
        if (Engine.isRunning() && !nowDir.equals(Engine.activeDir())) Engine.restart(this);
        if (!Engine.isRunning()) Engine.start(this);
        alive = true;
        refreshStorageBanner();
        tick();
    }

    @Override
    protected void onPause() {
        super.onPause();
        alive = false;
        ui.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        io.shutdownNow();
        super.onDestroy();
    }

    /** One-time nudge so the grant is discoverable without hunting in Settings. */
    private void askStorageOnce() {
        if (Prefs.hasAllFilesAccess()) return;
        if (prefs.getBool("askedAllFiles")) return;
        prefs.set("askedAllFiles", "1");
        new AlertDialog.Builder(this)
                .setTitle("Where should files go?")
                .setMessage("aria2x can save downloads to /sdcard/Aria2X so you can "
                        + "get at them from any file manager.\n\n"
                        + "That needs all-files access. Without it, downloads are "
                        + "kept in app storage and other apps cannot see them.\n\n"
                        + "You can change this later in Settings.")
                .setPositiveButton("Allow", (d, w) -> grantStorage())
                .setNegativeButton("Not now", null)
                .show();
    }

    private void grantStorage() {
        if (!Ui.requestAllFiles(this)) {
            toast("this ROM has no all-files access page — use a folder you own");
            return;
        }
        toast("enable all-files access, then come back");
    }

    private void refreshStorageBanner() {
        if (storageBanner == null) return;
        if (Prefs.hasAllFilesAccess()) {
            storageBanner.setVisibility(View.GONE);
            return;
        }
        File d = prefs.downloadDir(this);
        storageBanner.setText("Downloads are saved in app storage ("
                + d.getAbsolutePath() + ").  Tap to grant access and use /sdcard/Aria2X.");
        storageBanner.setVisibility(View.VISIBLE);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    private void handleIntent(Intent i) {
        if (i == null || !Intent.ACTION_VIEW.equals(i.getAction())) return;
        Uri d = i.getData();
        if (d == null) return;
        String s = d.toString();
        if (s.startsWith("content:") || s.endsWith(".torrent")) {
            addTorrentFromUri(d);
            return;
        }
        urlInput.setText(s);
        urlInput.setSelection(s.length());
    }

    // ------------------------------------------------------------------- UI

    private View buildUi() {
        int pad = Ui.dp(this, 16);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 6));

        // ---- header
        LinearLayout head = Ui.row(this);

        LinearLayout titles = Ui.col(this);
        TextView title = Ui.text(this, "aria2x", 22, Ui.TXT);
        title.setTypeface(Ui.medium, android.graphics.Typeface.BOLD);
        LinearLayout l1 = Ui.row(this);
        l1.addView(title);

        TextView mod = Ui.pill(this, "MOD", Ui.ACC2);
        LinearLayout.LayoutParams mlp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = Ui.dp(this, 8);
        l1.addView(mod, mlp);

        dot = Ui.monoText(this, "●", 11, Ui.MUT);
        LinearLayout.LayoutParams dlp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = Ui.dp(this, 8);
        l1.addView(dot, dlp);

        titles.addView(l1);
        titles.addView(Ui.text(this, "aria2c arm64 · from source", 11, Ui.MUT));
        head.addView(titles, Ui.weight(this, 1, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView menu = Ui.text(this, "⋮", 24, Ui.MUT);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 12));
        menu.setClickable(true);
        menu.setOnClickListener(this::showMenu);
        head.addView(menu);

        root.addView(head);

        // ---- stats
        LinearLayout stats = Ui.card(this);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sc.topMargin = Ui.dp(this, 12);
        stats.setLayoutParams(sc);

        LinearLayout srow = Ui.row(this);
        statDown = Ui.monoText(this, "0 B/s", 20, Ui.TXT);
        srow.addView(statDown);
        TextView arr = Ui.monoText(this, "  ↓   ", 14, Ui.MUT);
        srow.addView(arr);

        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = Ui.dp(this, 14);
        statUp = Ui.monoText(this, "0 B/s  ↑", 14, Ui.MUT);
        srow.addView(statUp, nlp);
        stats.addView(srow);

        statCount = Ui.text(this, "0 active · 0 waiting · 0 done", 12, Ui.MUT);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 6);
        stats.addView(statCount, clp);

        engineBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        engineBar.setIndeterminate(true);
        engineBar.setVisibility(View.GONE);
        engineBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACC));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3));
        blp.topMargin = Ui.dp(this, 10);
        stats.addView(engineBar, blp);

        engineNote = Ui.text(this, "", 12, Ui.WARN);
        engineNote.setVisibility(View.GONE);
        stats.addView(engineNote);

        root.addView(stats);

        // ---- add bar
        LinearLayout addCard = Ui.card(this);
        LinearLayout.LayoutParams ac = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ac.topMargin = Ui.dp(this, 12);
        addCard.setLayoutParams(ac);
        addCard.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));

        urlInput = Ui.input(this, "https://  ·  magnet:  ·  .torrent", 1);
        urlInput.setSingleLine(true);
        urlInput.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));
        addCard.addView(urlInput);

        LinearLayout btnRow = Ui.row(this);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brp.topMargin = Ui.dp(this, 10);
        btnRow.setLayoutParams(brp);

        TextView paste = Ui.button(this, "PASTE", Ui.CARD2, Ui.TXT, 40);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (s != null) urlInput.setText(s.toString().trim());
            }
        });
        btnRow.addView(paste, Ui.lpMr(this, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        ((LinearLayout.LayoutParams) paste.getLayoutParams()).width = 0;
        ((LinearLayout.LayoutParams) paste.getLayoutParams()).weight = 1;

        TextView torrent = Ui.button(this, "TORRENT", Ui.CARD2, Ui.TXT, 40);
        torrent.setOnClickListener(v -> pickTorrent());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.weight = 1;
        tlp.rightMargin = Ui.dp(this, 8);
        btnRow.addView(torrent, tlp);

        TextView add = Ui.button(this, "DOWNLOAD", Ui.ACC, 0xFFFFFFFF, 40);
        add.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.weight = 1.4f;
        btnRow.addView(add, alp);

        addCard.addView(btnRow);
        root.addView(addCard);

        storageBanner = Ui.text(this, "", 12, Ui.WARN);
        storageBanner.setBackground(Ui.round(this, 0x33FBBF24, 12));
        storageBanner.setPadding(Ui.dp(this, 12), Ui.dp(this, 10),
                Ui.dp(this, 12), Ui.dp(this, 10));
        storageBanner.setVisibility(View.GONE);
        storageBanner.setClickable(true);
        storageBanner.setOnClickListener(v -> grantStorage());
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbp.topMargin = Ui.dp(this, 10);
        root.addView(storageBanner, sbp);

        // ---- list
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        svlp.weight = 1;
        svlp.topMargin = Ui.dp(this, 12);
        sv.addView(Ui.col(this), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout listWrap = Ui.col(this);
        list = listWrap;

        emptyHint = Ui.text(this, "nothing queued yet.\npaste a link and hit download.", 13, Ui.MUT);
        emptyHint.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 24));
        emptyHint.setGravity(Gravity.CENTER);
        list.addView(emptyHint);

        ((ViewGroup) sv.getChildAt(0)).addView(listWrap);
        root.addView(sv, svlp);

        // ---- footer
        LinearLayout foot = Ui.row(this);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(this, 8);
        foot.setLayoutParams(flp);

        foot.addView(smallBtn("PAUSE ALL", () -> task(() -> rpc.pauseAll())),
                margin(this, 4));
        foot.addView(smallBtn("RESUME ALL", () -> task(() -> rpc.resumeAll())),
                margin(this, 4));
        foot.addView(smallBtn("CLEAR DONE", () -> task(() -> {
            rpc.purgeAll();
            ui.post(this::rebuildRows);
        })), margin(this, 4));

        root.addView(foot);
        return root;
    }

    private LinearLayout.LayoutParams margin(Activity a, int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = 1;
        p.rightMargin = Ui.dp(a, dp);
        return p;
    }

    private interface Job {
        void run() throws Exception;
    }

    private TextView smallBtn(String label, Job r) {
        TextView t = Ui.text(this, label, 11, Ui.MUT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        t.setBackground(Ui.round(this, Ui.CARD, 12));
        t.setClickable(true);
        Ui.pressable(t, Ui.round(this, Ui.CARD, 12), 0x22FFFFFF);
        t.setOnClickListener(v -> task(r));
        return t;
    }

    // ----------------------------------------------------------------- menu

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Settings");
        m.getMenu().add(0, 2, 1, "Mods & limits");
        m.getMenu().add(0, 3, 2, "Engine log");
        m.getMenu().add(0, 4, 3, Engine.isRunning() ? "Restart engine" : "Start engine");
        m.getMenu().add(0, 5, 4, "Stop engine");
        m.getMenu().add(0, 6, 5, "About · version / license");
        m.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1:
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                case 2:
                    startActivity(new Intent(this, ModsActivity.class));
                    return true;
                case 3:
                    showLog();
                    return true;
                case 4:
                    Engine.restart(this);
                    toast("engine restarting");
                    ui.postDelayed(this::tick, 1200);
                    return true;
                case 5:
                    Engine.stop(this);
                    toast("engine stopped");
                    return true;
                case 6:
                    startActivity(new Intent(this, AboutActivity.class));
                    return true;
                default:
                    return false;
            }
        });
        m.show();
    }

    private void showLog() {
        String text;
        try {
            java.io.File f = new java.io.File(getFilesDir(), "aria2.log");
            if (!f.exists()) {
                text = "(no log yet)";
            } else {
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
                long len = raf.length();
                long start = Math.max(0, len - 24000);
                raf.seek(start);
                byte[] buf = new byte[(int) (len - start)];
                raf.readFully(buf);
                raf.close();
                text = new String(buf, "UTF-8");
            }
        } catch (Exception e) {
            text = "log read failed: " + e;
        }
        ScrollView sv = new ScrollView(this);
        TextView tv = Ui.monoText(this, text, 10, Ui.TXT);
        tv.setTextIsSelectable(true);
        tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("engine log")
                .setView(sv)
                .setPositiveButton("close", null)
                .show();
    }

    // ---------------------------------------------------------------- actions

    private void submit() {
        String raw = urlInput.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            toast("nothing to download");
            return;
        }
        List<String> uris = new ArrayList<>();
        for (String line : raw.split("\\s+")) {
            if (line.length() > 4) uris.add(line);
        }
        urlInput.setText("");
        task(() -> {
            rpc.addUri(uris, prefs.downloadDir(this).getAbsolutePath());
            ui.post(() -> {
                toast("queued " + uris.size());
                tick();
            });
        });
    }

    private void pickTorrent() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, 21);
        } catch (Exception e) {
            toast("no file picker");
        }
    }

    private void addTorrentFromUri(Uri uri) {
        task(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                String b64 = android.util.Base64.encodeToString(bo.toByteArray(),
                        android.util.Base64.NO_WRAP);
                rpc.addTorrent(b64, prefs.downloadDir(this).getAbsolutePath());
                ui.post(() -> {
                    toast("torrent queued");
                    tick();
                });
            } catch (Exception e) {
                ui.post(() -> toast("torrent failed: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 21 && res == RESULT_OK && data != null && data.getData() != null) {
            addTorrentFromUri(data.getData());
        }
    }

    private void task(Job r) {
        io.execute(() -> {
            try {
                r.run();
            } catch (Exception e) {
                ui.post(() -> toast(String.valueOf(e.getMessage())));
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ poll

    private void tick() {
        if (!alive) return;
        io.execute(() -> {
            Rpc.Snap s = null;
            String err = null;
            try {
                s = rpc.snapshot();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final Rpc.Snap fs = s;
            final String fe = err;
            ui.post(() -> {
                if (!alive) return;
                if (fs != null) {
                    engineNote.setVisibility(View.GONE);
                    engineBar.setVisibility(View.GONE);
                    apply(fs);
                } else {
                    engineNote.setVisibility(View.VISIBLE);
                    String why = Engine.lastError();
                    engineNote.setText(Engine.isRunning()
                            ? "rpc not ready…"
                            : "engine offline" + (why.isEmpty() ? "" : " \u00b7 " + why));
                    engineBar.setVisibility(View.VISIBLE);
                    dot.setTextColor(Engine.isRunning() ? Ui.WARN : Ui.ERR);
                }
                ui.postDelayed(this::tick, 1000);
            });
        });
    }

    private void apply(Rpc.Snap s) {
        dot.setTextColor(Ui.OK);
        statDown.setText(Ui.speed(s.downSpeed));
        statUp.setText(Ui.speed(s.upSpeed) + "  ↑");
        statCount.setText(s.active + " active · " + s.waiting + " waiting · "
                + s.stopped + " done");
        Engine.updateNotification(this, Ui.speed(s.downSpeed) + " · "
                + s.active + " active");
        rebuildRows(s);
    }

    private void rebuildRows() {
        try {
            apply(rpc.snapshot());
        } catch (Exception ignored) {
        }
    }

    private void rebuildRows(Rpc.Snap s) {
        emptyHint.setVisibility(s.items.isEmpty() ? View.VISIBLE : View.GONE);

        List<String> seen = new ArrayList<>();
        int index = 0;
        for (Rpc.Item it : s.items) {
            seen.add(it.gid);
            Row r = rows.get(it.gid);
            View cardView;
            if (r == null) {
                r = new Row();
                cardView = buildRow(r, it);
                rows.put(it.gid, r);
            } else {
                cardView = r.root;
            }
            bindRow(r, it);
            if (list.indexOfChild(cardView) < 0) list.addView(cardView);
            if (list.indexOfChild(cardView) != index) {
                list.removeView(cardView);
                list.addView(cardView, index);
            }
            index++;
        }

        for (Map.Entry<String, Row> e : new ArrayList<>(rows.entrySet())) {
            if (!seen.contains(e.getKey())) {
                list.removeView(e.getValue().root);
                rows.remove(e.getKey());
            }
        }
    }

    private View buildRow(Row r, Rpc.Item it) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        card.setLayoutParams(lp);
        r.root = card;

        LinearLayout top = Ui.row(this);
        r.name = Ui.text(this, it.name, 14, Ui.TXT);
        r.name.setSingleLine(true);
        r.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        top.addView(r.name, Ui.weight(this, 1, ViewGroup.LayoutParams.WRAP_CONTENT));

        r.pct = Ui.monoText(this, "0%", 12, Ui.ACC2);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = Ui.dp(this, 10);
        top.addView(r.pct, plp);
        card.addView(top);

        r.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        r.bar.setMax(1000);
        r.bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACC));
        r.bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.CARD2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        blp.topMargin = Ui.dp(this, 10);
        card.addView(r.bar, blp);

        LinearLayout bottom = Ui.row(this);
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brlp.topMargin = Ui.dp(this, 8);
        bottom.setLayoutParams(brlp);

        r.meta = Ui.monoText(this, "", 11, Ui.MUT);
        bottom.addView(r.meta, Ui.weight(this, 1, ViewGroup.LayoutParams.WRAP_CONTENT));

        r.act = Ui.monoText(this, "", 15, Ui.MUT);
        r.act.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 4), 0);
        r.act.setClickable(true);
        r.act.setOnClickListener(v -> {
            String gid = (String) v.getTag();
            Row row = rows.get(gid);
            if (row == null) return;
            boolean paused = "paused".equals(row.meta.getTag());
            task(() -> {
                if (paused) rpc.resume(gid);
                else rpc.pause(gid);
                ui.post(this::tick);
            });
        });
        bottom.addView(r.act);

        TextView del = Ui.monoText(this, "✕", 15, Ui.ERR);
        del.setPadding(Ui.dp(this, 10), 0, 0, 0);
        del.setClickable(true);
        del.setOnClickListener(v -> {
            String gid = (String) v.getTag();
            task(() -> {
                try {
                    rpc.remove(gid);
                } catch (Exception ignored) {
                }
                rpc.purge(gid);
                ui.post(this::tick);
            });
        });
        bottom.addView(del);

        card.addView(bottom);

        card.setClickable(true);
        card.setOnClickListener(v -> detail(it.gid));

        card.setTag(it);
        r.act.setTag(it.gid);
        del.setTag(it.gid);
        return card;
    }

    private void bindRow(Row r, Rpc.Item it) {
        r.name.setText(it.name);
        r.pct.setText(it.pct() + "%");
        r.bar.setProgress(it.total > 0 ? (int) (it.done * 1000 / it.total) : 0);

        StringBuilder m = new StringBuilder();
        m.append(Ui.bytes(it.done)).append(" / ")
                .append(it.total > 0 ? Ui.bytes(it.total) : "?");
        if ("active".equals(it.status)) {
            m.append("  ·  ").append(Ui.speed(it.downSpeed));
            if (it.conns > 0) m.append("  ·  ").append(it.conns).append("c");
            m.append("  ·  ").append(Ui.eta(it.total - it.done, it.downSpeed)).append(" left");
        } else if ("paused".equals(it.status)) {
            m.append("  ·  paused");
        } else if ("waiting".equals(it.status)) {
            m.append("  ·  queued");
        } else if ("error".equals(it.status)) {
            m.append("  ·  ").append(it.error);
        } else if ("complete".equals(it.status)) {
            m.append("  ·  done");
        } else {
            m.append("  ·  ").append(it.status);
        }
        r.meta.setText(m.toString());
        r.meta.setTag(it.status);

        int color = "active".equals(it.status) ? Ui.ACC
                : "paused".equals(it.status) ? Ui.WARN
                : "error".equals(it.status) ? Ui.ERR
                : "complete".equals(it.status) ? Ui.OK : Ui.MUT;
        r.pct.setTextColor(color);
        r.act.setText("active".equals(it.status) ? "⏸" : "▶");
        r.act.setTextColor(color);
    }

    private void detail(String gid) {
        Row r = rows.get(gid);
        if (r == null) return;
        Rpc.Item it = (Rpc.Item) r.root.getTag();
        StringBuilder b = new StringBuilder();
        b.append("status   ").append(it.status).append('\n');
        b.append("gid      ").append(it.gid).append('\n');
        b.append("size     ").append(Ui.bytes(it.total)).append('\n');
        b.append("done     ").append(Ui.bytes(it.done)).append('\n');
        b.append("conns    ").append(it.conns).append('\n');
        b.append("speed    ").append(Ui.speed(it.downSpeed)).append('\n');
        b.append("dir      ").append(it.dir).append('\n');
        if (!it.error.isEmpty()) b.append("error    ").append(it.error).append('\n');

        new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setMessage(b.toString())
                .setPositiveButton("copy gid", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("gid", it.gid));
                    toast("gid copied");
                })
                .setNegativeButton("close", null)
                .show();
    }
}
