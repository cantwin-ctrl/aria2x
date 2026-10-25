package com.ven.aria2x;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny design system. No AppCompat, no Material library. */
public final class Ui {

    public static final int BG = 0xFF0A0C10;
    public static final int CARD = 0xFF14181F;
    public static final int CARD2 = 0xFF1B212B;
    public static final int LINE = 0xFF232A35;
    public static final int TXT = 0xFFE8ECF1;
    public static final int MUT = 0xFF8792A2;
    public static final int ACC = 0xFF7C5CFF;
    public static final int ACC2 = 0xFF22D3EE;
    public static final int OK = 0xFF34D399;
    public static final int WARN = 0xFFFBBF24;
    public static final int ERR = 0xFFF87171;

    public static Typeface medium;
    public static Typeface mono;

    /** Opens the system page that grants all-files access. Returns false if
     *  the ROM has no such page (then the caller should explain, not crash). */
    public static boolean requestAllFiles(Activity a) {
        try {
            a.startActivity(new Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
            return true;
        } catch (Exception ignored) {
        }
        try {
            a.startActivity(new Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            return true;
        } catch (Exception ignored) {
        }
        try {
            a.startActivity(new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.getPackageName())));
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static void typefaces() {
        if (medium == null) medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if (mono == null) mono = Typeface.create("monospace", Typeface.NORMAL);
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(Context c, int color, int strokeColor, float radiusDp) {
        GradientDrawable d = round(c, color, radiusDp);
        d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    public static GradientDrawable bar(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, 99));
        return d;
    }

    public static void pressable(View v, GradientDrawable bg, int rippleColor) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), bg, null));
    }

    public static TextView text(Context c, String s, float sp, int color) {
        typefaces();
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(medium);
        return t;
    }

    public static TextView monoText(Context c, String s, float sp, int color) {
        typefaces();
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(mono);
        return t;
    }

    public static TextView bullet(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(round(c, CARD, 18));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static TextView pill(Context c, String s, int color) {
        TextView t = monoText(c, s, 11, color);
        t.setBackground(round(c, (color & 0x00FFFFFF) | 0x22000000, 99));
        int px = dp(c, 9), py = dp(c, 4);
        t.setPadding(px, py, px, py);
        return t;
    }

    public static TextView button(Context c, String s, int bg, int fg) {
        return button(c, s, bg, fg, 44);
    }

    public static TextView button(Context c, String s, int bg, int fg, int heightDp) {
        TextView t = text(c, s, 14, fg);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(c, bg, 14));
        t.setClickable(true);
        t.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp));
        t.setLayoutParams(lp);
        pressable(t, round(c, bg, 14), 0x33FFFFFF);
        return t;
    }

    public static EditText input(Context c, String hint, int lines) {
        typefaces();
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUT);
        e.setTextColor(TXT);
        e.setTextSize(14);
        e.setTypeface(lines > 1 ? mono : medium);
        e.setBackground(roundStroke(c, CARD2, LINE, 14));
        int p = dp(c, 14);
        e.setPadding(p, dp(c, 10), p, dp(c, 10));
        e.setLineSpacing(dp(c, 2), 1f);
        if (lines > 1) {
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setMinLines(lines);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        return e;
    }

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpMr(Context c, int w, int h, int mr) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.rightMargin = dp(c, mr);
        return p;
    }

    public static LinearLayout.LayoutParams weight(Context c, float wt, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h);
        p.weight = wt;
        return p;
    }

    // ------------------------------------------------------------- formatting

    public static String bytes(long n) {
        if (n <= 0) return "0 B";
        String[] u = {"B", "K", "M", "G", "T"};
        int i = 0;
        double v = n;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(v >= 100 || i == 0 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }

    public static String speed(long bps) {
        return bytes(bps) + "/s";
    }

    public static String eta(long remain, long speed) {
        if (speed <= 0 || remain <= 0) return "--";
        long s = remain / speed;
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m" + (s % 60) + "s";
        return (s / 3600) + "h" + ((s % 3600) / 60) + "m";
    }
}
