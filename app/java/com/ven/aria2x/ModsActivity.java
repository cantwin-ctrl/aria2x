package com.ven.aria2x;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** What this binary does that stock aria2c does not. */
public class ModsActivity extends Activity {

    private static final String[][] MODS = {
            {"-x  --max-connection-per-server", "1 … 16", "1 … 256"},
            {"-k  --min-split-size", "1M floor, 20M default", "256K floor, 1M default"},
            {"--piece-length", "1M floor / 1M default", "256K floor / 256K default"},
            {"--disk-cache", "16M", "64M"},
            {"-j  --max-concurrent-downloads", "5", "10"},
            {"--connect-timeout", "60s", "15s"},
            {"-m  --max-tries", "5", "10"},
    };

    private static final String[][] BUILD = {
            {"engine", "aria2 1.37.0, C++17"},
            {"tls", "OpenSSL 3.5.2 (static, no-shared)"},
            {"dns", "c-ares 1.34.5 (static)"},
            {"compress", "zlib 1.3.1 (static)"},
            {"toolchain", "NDK r27d · clang · aarch64"},
            {"flags", "-O3 · gc-sections · strip"},
            {"abi", "arm64-v8a only (API 24+)"},
            {"link", "static deps, dynamic bionic libc"},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setBackgroundDrawableResource(android.R.color.black);
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        Ui.typefaces();
        setContentView(build());
    }

    private ScrollView build() {
        int pad = Ui.dp(this, 16);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 24));

        LinearLayout head = Ui.row(this);
        TextView back = Ui.text(this, "‹", 26, Ui.TXT);
        back.setPadding(0, 0, Ui.dp(this, 12), Ui.dp(this, 6));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        LinearLayout tt = Ui.col(this);
        TextView title = Ui.text(this, "Mods", 20, Ui.TXT);
        title.setTypeface(Ui.medium, android.graphics.Typeface.BOLD);
        tt.addView(title);
        tt.addView(Ui.text(this, "7 upstream limits removed", 11, Ui.MUT));
        head.addView(tt, Ui.weight(this, 1, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(head);

        root.addView(Ui.spacer(this, 14));

        LinearLayout table = Ui.card(this);
        for (int i = 0; i < MODS.length; i++) {
            if (i > 0) table.addView(Ui.divider(this));
            table.addView(modRow(MODS[i]));
        }
        root.addView(table);

        root.addView(Ui.spacer(this, 20));
        root.addView(Ui.monoText(this, "BUILD", 11, Ui.ACC2));
        root.addView(Ui.spacer(this, 8));

        LinearLayout bi = Ui.card(this);
        for (int i = 0; i < BUILD.length; i++) {
            if (i > 0) bi.addView(Ui.divider(this));
            LinearLayout r = Ui.row(this);
            TextView k = Ui.monoText(this, BUILD[i][0], 12, Ui.MUT);
            r.addView(k, Ui.weight(this, 0.9f, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView v = Ui.monoText(this, BUILD[i][1], 12, Ui.TXT);
            v.setTextIsSelectable(true);
            r.addView(v, Ui.weight(this, 1.6f, ViewGroup.LayoutParams.WRAP_CONTENT));
            bi.addView(r);
        }
        root.addView(bi);

        root.addView(Ui.spacer(this, 20));
        root.addView(Ui.text(this, "verify on device", 13, Ui.TXT));
        root.addView(Ui.spacer(this, 6));

        String hint = "adb shell run-as com.ven.aria2x \\\n"
                + "  $PWD/../lib/arm64/libaria2c.so --version\n\n"
                + "look for: enabling epoll, OpenSSL, c-ares\n"
                + "and: aria2c -x 256 -s 256 <url>";
        TextView h = Ui.monoText(this, hint, 11, Ui.MUT);
        h.setTextIsSelectable(true);
        h.setBackground(Ui.round(this, Ui.CARD, 14));
        h.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        root.addView(h);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private ViewGroup modRow(String[] m) {
        LinearLayout c = Ui.col(this);
        TextView name = Ui.monoText(this, m[0], 12, Ui.TXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(name);

        LinearLayout v = Ui.row(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);

        TextView stock = Ui.monoText(this, m[1], 11, Ui.ERR);
        v.addView(stock);

        TextView arrow = Ui.monoText(this, "  →  ", 11, Ui.MUT);
        v.addView(arrow);

        TextView mod = Ui.monoText(this, m[2], 11, Ui.OK);
        v.addView(mod);
        c.addView(v, lp);
        return c;
    }
}
