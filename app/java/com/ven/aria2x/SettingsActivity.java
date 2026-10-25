package com.ven.aria2x;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Every option in Prefs.FIELDS is rendered here automatically. */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private final Map<String, Object> widgets = new HashMap<>();
    private final Map<String, String> snapshot = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setBackgroundDrawableResource(android.R.color.black);
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);

        prefs = new Prefs(this);
        Ui.typefaces();
        setContentView(build());
    }

    private View build() {
        int pad = Ui.dp(this, 16);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(pad, Ui.dp(this, 10), pad, 0);

        LinearLayout head = Ui.row(this);
        TextView back = Ui.text(this, "‹", 26, Ui.TXT);
        back.setPadding(0, 0, Ui.dp(this, 12), Ui.dp(this, 6));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        head.addView(back);

        LinearLayout tt = Ui.col(this);
        TextView title = Ui.text(this, "Settings", 20, Ui.TXT);
        title.setTypeface(Ui.medium, android.graphics.Typeface.BOLD);
        tt.addView(title);
        tt.addView(Ui.text(this, "changes restart the engine", 11, Ui.MUT));
        head.addView(tt, Ui.weight(this, 1, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView reset = Ui.pill(this, "RESET", Ui.ERR);
        reset.setClickable(true);
        reset.setOnClickListener(v -> resetAll());
        head.addView(reset);

        root.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout body = Ui.col(this);
        body.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 24));
        sv.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (Prefs.Field f : Prefs.FIELDS) {
            if (f.type == Prefs.SECTION) {
                body.addView(sectionHeader(f.label));
                if ("STORAGE".equals(f.label)) {
                    TextView info = Ui.monoText(this,
                            prefs.downloadDir(this).getAbsolutePath(), 11, Ui.MUT);
                    info.setTextIsSelectable(true);
                    info.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 8));
                    body.addView(info);
                }
                continue;
            }
            body.addView(row(f));
        }

        body.addView(sectionHeader("STORAGE TARGET"));
        body.addView(storageCard());
        return root;
    }

    /** Where files actually land, and an optional route to public storage. */
    private View storageCard() {
        LinearLayout card = Ui.card(this);
        File dir = prefs.downloadDir(this);

        card.addView(Ui.monoText(this, "resolved folder", 10, Ui.MUT));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.dp(this, 4);
        TextView path = Ui.monoText(this, dir.getAbsolutePath(), 11, Ui.TXT);
        path.setTextIsSelectable(true);
        card.addView(path, plp);

        String want = prefs.get("dir").trim();
        String wantAbs = want.isEmpty() ? "" : new File(want).getAbsolutePath();
        File resolved = prefs.downloadDir(this);
        if (!wantAbs.isEmpty() && !wantAbs.equals(resolved.getAbsolutePath())) {
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wlp.topMargin = Ui.dp(this, 8);
            TextView warn = Ui.text(this,
                    "requested folder is not writable — falling back to the "
                            + "resolved folder above", 11, Ui.WARN);
            warn.setLineSpacing(Ui.dp(this, 2), 1f);
            card.addView(warn, wlp);
        }

        card.addView(Ui.divider(this));

        boolean all = Prefs.hasAllFilesAccess();
        String label = all
                ? "all-files access: granted"
                : "all-files access: not granted  ·  tap to enable";
        TextView t = Ui.text(this, label, 12, all ? Ui.OK : Ui.ACC2);
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        if (!all) {
            t.setClickable(true);
            t.setOnClickListener(v -> requestAllFiles());
        }
        card.addView(t);

        if (!all) {
            TextView hint = Ui.text(this,
                    "without it, downloads stay in app storage and other apps "
                            + "cannot see them. granting it moves new downloads "
                            + "to /sdcard/Download/aria2x.", 11, Ui.MUT);
            hint.setLineSpacing(Ui.dp(this, 2), 1f);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = Ui.dp(this, 6);
            card.addView(hint, hlp);
        }
        return card;
    }

    private void requestAllFiles() {
        if (!Ui.requestAllFiles(this)) {
            Toast.makeText(this, "this ROM has no all-files access page",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private TextView sectionHeader(String s) {
        TextView t = Ui.monoText(this, s, 11, Ui.ACC2);
        t.setLetterSpacing(0.14f);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 8));
        return t;
    }

    private View row(Prefs.Field f) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        card.setLayoutParams(lp);

        switch (f.type) {
            case Prefs.SWITCH: {
                LinearLayout r = Ui.row(this);
                LinearLayout t = Ui.col(this);
                t.addView(Ui.text(this, f.label, 14, Ui.TXT));
                if (f.hint != null) t.addView(Ui.text(this, f.hint, 11, Ui.MUT));
                r.addView(t, Ui.weight(this, 1, ViewGroup.LayoutParams.WRAP_CONTENT));

                Switch sw = new Switch(this);
                sw.setChecked(prefs.getBool(f.key));
                sw.setThumbTintList(android.content.res.ColorStateList.valueOf(
                        prefs.getBool(f.key) ? Ui.ACC : Ui.MUT));
                sw.setOnCheckedChangeListener((v, c) ->
                        ((Switch) v).setThumbTintList(
                                android.content.res.ColorStateList.valueOf(
                                        c ? Ui.ACC : Ui.MUT)));
                r.addView(sw);
                widgets.put(f.key, sw);
                card.addView(r);
                break;
            }
            case Prefs.CHOICE: {
                card.addView(Ui.text(this, f.label, 14, Ui.TXT));
                if (f.hint != null) card.addView(Ui.text(this, f.hint, 11, Ui.MUT));
                LinearLayout chips = Ui.row(this);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.topMargin = Ui.dp(this, 10);
                chips.setLayoutParams(clp);

                Map<String, TextView> group = new HashMap<>();
                String cur = prefs.get(f.key);
                for (String c : f.choices) {
                    TextView chip = chip(c, c.equals(cur), () -> {
                    });
                    group.put(c, chip);
                    chips.addView(chip, Ui.lpMr(this,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 8));
                }
                for (String c : f.choices) {
                    final String choice = c;
                    group.get(c).setOnClickListener(v -> {
                        for (Map.Entry<String, TextView> e : group.entrySet()) {
                            boolean on = e.getKey().equals(choice);
                            e.getValue().setBackground(Ui.round(this,
                                    on ? Ui.ACC : Ui.CARD2, 10));
                            e.getValue().setTextColor(on ? 0xFFFFFFFF : Ui.MUT);
                        }
                    });
                }
                card.addView(chips);
                widgets.put(f.key, new ChoiceHolder(group, f.choices));
                break;
            }
            case Prefs.MULTI: {
                card.addView(Ui.text(this, f.label, 14, Ui.TXT));
                if (f.hint != null) card.addView(Ui.text(this, f.hint, 11, Ui.MUT));
                EditText e = Ui.input(this, "max-download-limit=1M", 4);
                e.setText(prefs.get(f.key));
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                elp.topMargin = Ui.dp(this, 8);
                card.addView(e, elp);
                widgets.put(f.key, e);
                break;
            }
            default: {
                card.addView(Ui.text(this, f.label, 14, Ui.TXT));
                if (f.hint != null) card.addView(Ui.text(this, f.hint, 11, Ui.MUT));
                EditText e = Ui.input(this, f.def == null ? "" : f.def, 1);
                e.setSingleLine(true);
                if (f.type == Prefs.NUM) e.setInputType(InputType.TYPE_CLASS_NUMBER);
                e.setText(prefs.get(f.key));
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
                elp.topMargin = Ui.dp(this, 8);
                card.addView(e, elp);
                widgets.put(f.key, e);
                break;
            }
        }
        return card;
    }

    private static final class ChoiceHolder {
        final Map<String, TextView> views;
        final String[] order;

        ChoiceHolder(Map<String, TextView> views, String[] order) {
            this.views = views;
            this.order = order;
        }

        String selected() {
            for (String o : order) {
                TextView t = views.get(o);
                if (t != null && t.getCurrentTextColor() == 0xFFFFFFFF) return o;
            }
            return order[0];
        }
    }

    private TextView chip(String label, boolean on, Runnable r) {
        TextView t = Ui.monoText(this, label, 12, on ? 0xFFFFFFFF : Ui.MUT);
        t.setBackground(Ui.round(this, on ? Ui.ACC : Ui.CARD2, 10));
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        t.setClickable(true);
        return t;
    }

    private void resetAll() {
        for (Prefs.Field f : Prefs.FIELDS) {
            if (f.type != Prefs.SECTION) prefs.set(f.key, f.def == null ? "" : f.def);
        }
        Toast.makeText(this, "reset to defaults", Toast.LENGTH_SHORT).show();
        recreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // returning from the all-files-access screen can change the target dir
        if (Engine.isRunning() && prefs != null) {
            String now = prefs.downloadDir(this).getAbsolutePath();
            if (!now.equals(Engine.activeDir())) Engine.restart(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        boolean changed = false;
        for (Prefs.Field f : Prefs.FIELDS) {
            if (f.type == Prefs.SECTION) continue;
            Object w = widgets.get(f.key);
            if (w == null) continue;
            String v;
            if (w instanceof Switch) {
                v = ((Switch) w).isChecked() ? "1" : "0";
            } else if (w instanceof ChoiceHolder) {
                v = ((ChoiceHolder) w).selected();
            } else if (w instanceof EditText) {
                Editable e = ((EditText) w).getText();
                v = e == null ? "" : e.toString().trim();
            } else {
                v = prefs.get(f.key);
            }
            String old = prefs.get(f.key);
            if (!v.equals(old)) {
                prefs.set(f.key, v);
                changed = true;
            }
        }
        if (changed && Engine.isRunning()) {
            Engine.restart(this);
            Toast.makeText(this, "engine restarted", Toast.LENGTH_SHORT).show();
        }
    }
}
