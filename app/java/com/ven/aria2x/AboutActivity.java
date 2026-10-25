package com.ven.aria2x;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Version, developer, license and component attribution. */
public class AboutActivity extends Activity {

    private static final String[][] COMPONENTS = {
            {"aria2", "1.37.0", "GPL-2.0-or-later + OpenSSL exception"},
            {"OpenSSL", "3.5.2", "Apache-2.0"},
            {"c-ares", "1.34.5", "MIT"},
            {"zlib", "1.3.1", "Zlib"},
            {"aria2x app", "see version", "GPL-2.0-or-later"},
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

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private long versionCode() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private ScrollView build() {
        int pad = Ui.dp(this, 16);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 24));

        // ---- header
        LinearLayout head = Ui.row(this);
        TextView back = Ui.text(this, "‹", 26, Ui.TXT);
        back.setPadding(0, 0, Ui.dp(this, 12), Ui.dp(this, 6));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        head.addView(back);

        LinearLayout tt = Ui.col(this);
        TextView title = Ui.text(this, "About", 20, Ui.TXT);
        title.setTypeface(Ui.medium, android.graphics.Typeface.BOLD);
        tt.addView(title);
        tt.addView(Ui.text(this, "aria2x", 11, Ui.MUT));
        head.addView(tt, Ui.weight(this, 1, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ver = Ui.pill(this, "v" + versionName(), Ui.ACC);
        head.addView(ver);
        root.addView(head);

        root.addView(Ui.spacer(this, 16));

        // ---- hero card
        LinearLayout hero = Ui.card(this);
        LinearLayout hrow = Ui.row(this);
        TextView name = Ui.text(this, "aria2x", 26, Ui.TXT);
        name.setTypeface(Ui.medium, android.graphics.Typeface.BOLD);
        hrow.addView(name);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = Ui.dp(this, 10);
        hrow.addView(Ui.pill(this, "MOD", Ui.ACC2), mlp);
        hero.addView(hrow);

        TextView tag = Ui.text(this, "aria2c compiled from source, limits removed",
                12, Ui.MUT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 6);
        hero.addView(tag, tlp);
        root.addView(hero);

        // ---- version
        root.addView(Ui.spacer(this, 18));
        root.addView(Ui.monoText(this, "VERSION", 11, Ui.ACC2));
        root.addView(Ui.spacer(this, 8));

        LinearLayout vc = Ui.card(this);
        kv(vc, "app version", versionName(), true);
        vc.addView(Ui.divider(this));
        kv(vc, "version code", String.valueOf(versionCode()), true);
        vc.addView(Ui.divider(this));
        kv(vc, "engine", "aria2 1.37.0", true);
        vc.addView(Ui.divider(this));
        kv(vc, "package", getPackageName(), true);
        vc.addView(Ui.divider(this));
        kv(vc, "abi", "arm64-v8a", true);
        vc.addView(Ui.divider(this));
        kv(vc, "min sdk", "24  ·  target 34", true);
        root.addView(vc);

        // ---- developer
        root.addView(Ui.spacer(this, 18));
        root.addView(Ui.monoText(this, "DEVELOPER", 11, Ui.ACC2));
        root.addView(Ui.spacer(this, 8));

        LinearLayout dc = Ui.card(this);
        kv(dc, "built by", "Ven", true);
        dc.addView(Ui.divider(this));
        kv(dc, "for", "V", true);
        dc.addView(Ui.divider(this));
        kv(dc, "upstream", "aria2 · Tatsuhiro Tsujikawa", true);
        dc.addView(Ui.divider(this));
        kv(dc, "toolchain", "NDK r27d · clang · aarch64", true);
        dc.addView(Ui.divider(this));
        kv(dc, "host", "Debian 13 · x86_64", true);
        root.addView(dc);

        // ---- license
        root.addView(Ui.spacer(this, 18));
        root.addView(Ui.monoText(this, "LICENSE", 11, Ui.ACC2));
        root.addView(Ui.spacer(this, 8));

        LinearLayout lc = Ui.card(this);
        TextView gplTitle = Ui.text(this, "GPL-2.0-or-later", 15, Ui.TXT);
        gplTitle.setTypeface(Ui.medium, android.graphics.Typeface.BOLD);
        lc.addView(gplTitle);
        LinearLayout.LayoutParams ltp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ltp.topMargin = Ui.dp(this, 6);
        TextView gplBody = Ui.text(this,
                "This app bundles aria2c, which is licensed under the GNU General "
                        + "Public License v2 or later. A combined work that "
                        + "distributes the binary must itself be GPL-2.0-or-later, "
                        + "so that is the license of this app as a whole.\n\n"
                        + "aria2 carries an exception permitting linkage with "
                        + "OpenSSL. Corresponding source for the engine and the app "
                        + "is kept in the project tree and available on request.",
                12, Ui.MUT);
        gplBody.setLineSpacing(Ui.dp(this, 3), 1f);
        lc.addView(gplBody, ltp);

        lc.addView(Ui.divider(this));
        lc.addView(action("View full GPL-2.0 text", () -> showAsset("gpl-2.0.txt", "GPL-2.0")));
        lc.addView(Ui.divider(this));
        lc.addView(action("View third-party notices", () -> showAsset("NOTICE.txt", "Notices")));
        lc.addView(Ui.divider(this));
        lc.addView(action("gnu.org/licenses/gpl-2.0", () ->
                open("https://www.gnu.org/licenses/old-licenses/gpl-2.0.html")));
        root.addView(lc);

        // ---- components
        root.addView(Ui.spacer(this, 18));
        root.addView(Ui.monoText(this, "COMPONENTS", 11, Ui.ACC2));
        root.addView(Ui.spacer(this, 8));

        LinearLayout cmp = Ui.card(this);
        for (int i = 0; i < COMPONENTS.length; i++) {
            if (i > 0) cmp.addView(Ui.divider(this));
            LinearLayout r = Ui.row(this);
            LinearLayout left = Ui.col(this);
            left.addView(Ui.monoText(this, COMPONENTS[i][0], 12, Ui.TXT));
            left.addView(Ui.monoText(this, COMPONENTS[i][1], 10, Ui.MUT));
            r.addView(left, Ui.weight(this, 1f, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView lic = Ui.monoText(this, COMPONENTS[i][2], 10, Ui.OK);
            lic.setGravity(Gravity.END);
            r.addView(lic);
            cmp.addView(r);
        }
        root.addView(cmp);

        root.addView(Ui.spacer(this, 22));
        TextView foot = Ui.text(this,
                "no telemetry · no ads · no account", 11, Ui.MUT);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private void kv(LinearLayout parent, String k, String v, boolean selectable) {
        LinearLayout r = Ui.row(this);
        TextView kk = Ui.monoText(this, k, 12, Ui.MUT);
        r.addView(kk, Ui.weight(this, 0.85f, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView vv = Ui.monoText(this, v, 12, Ui.TXT);
        vv.setTextIsSelectable(selectable);
        vv.setGravity(Gravity.END);
        r.addView(vv, Ui.weight(this, 1.15f, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(r);
    }

    private TextView action(String label, Runnable r) {
        TextView t = Ui.text(this, label, 13, Ui.ACC2);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        t.setClickable(true);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void showAsset(String asset, String title) {
        String text;
        try (InputStream in = getAssets().open(asset)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            text = bo.toString("UTF-8");
        } catch (Exception e) {
            text = "asset " + asset + " missing";
        }
        ScrollView sv = new ScrollView(this);
        TextView tv = Ui.monoText(this, text, 10, Ui.TXT);
        tv.setTextIsSelectable(true);
        int p = Ui.dp(this, 12);
        tv.setPadding(p, p, p, p);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("close", null)
                .show();
    }
}
