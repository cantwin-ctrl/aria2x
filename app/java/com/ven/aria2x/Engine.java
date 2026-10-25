package com.ven.aria2x;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns the aria2c process. Foreground service so Android does not freeze the
 * downloader when the screen turns off.
 */
public class Engine extends Service {

    public static final String ACTION_START = "com.ven.aria2x.START";
    public static final String ACTION_STOP = "com.ven.aria2x.STOP";

    private static final String TAG = "aria2x";
    private static final String CHANNEL = "engine";
    private static final int NID = 1001;

    private static volatile boolean running;
    private static volatile String lastError = "";
    private static volatile int pid = -1;
    private static volatile String activeDir = "";

    private Process proc;
    private Thread pump;
    private Thread watchdog;
    private PowerManager.WakeLock wake;

    public static boolean isRunning() { return running; }
    public static String lastError() { return lastError; }
    public static int pid() { return pid; }
    public static String activeDir() { return activeDir; }

    public static void start(Context c) {
        Intent i = new Intent(c, Engine.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, Engine.class));
    }

    public static void restart(Context c) {
        final Context app = c.getApplicationContext();
        stop(app);
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> start(app), 700);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        channel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NID, buildNotification("engine starting"));

        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }

        extractCert();
        if (!running) launch();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ---------------------------------------------------------------- engine

    private void launch() {
        File bin = new File(getApplicationInfo().nativeLibraryDir, "libaria2c.so");
        if (!bin.exists()) {
            fail("aria2c binary not found in " + bin.getParent());
            return;
        }

        Prefs prefs = new Prefs(this);
        File conf;
        try {
            conf = prefs.writeConf(this);
        } catch (IOException e) {
            fail("config write failed: " + e.getMessage());
            return;
        }

        File ddir = prefs.downloadDir(this);
        activeDir = ddir.getAbsolutePath();
        String[] entries = ddir.list();
        Log.i(TAG, "download dir=" + ddir.getAbsolutePath()
                + " isDir=" + ddir.isDirectory()
                + " writable=" + ddir.canWrite()
                + " entries=" + (entries == null ? -1 : entries.length));
        if (entries != null) {
            for (String n : entries) Log.i(TAG, "  · " + n);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(bin.getAbsolutePath());
        cmd.add("--conf-path=" + conf.getAbsolutePath());
        File session = new File(getFilesDir(), "aria2.session");
        if (prefs.getBool("saveSession") && session.exists() && session.length() > 0) {
            cmd.add("--input-file=" + session.getAbsolutePath());
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // Never use the download dir as cwd: it can be on unready external
            // storage, and ProcessBuilder dies with error=2 if it is missing.
            pb.directory(getFilesDir());
            Map<String, String> env = pb.environment();
            env.put("HOME", getFilesDir().getAbsolutePath());
            env.put("TMPDIR", getCacheDir().getAbsolutePath());
            String ca = prefs.caCertPath(this);
            if (ca != null) env.put("SSL_CERT_FILE", ca);
            pb.redirectErrorStream(true);

            proc = pb.start();
            running = true;
            pid = pidOf(proc);
            lastError = "";
            acquireWake();
            pumpOutput();
            watch();
            Log.i(TAG, "engine up pid=" + pid + " conf=" + conf);
            updateNotification("running · pid " + pid);
        } catch (Exception e) {
            fail("launch failed: " + e.getMessage());
        }
    }

    private void pumpOutput() {
        final Process p = proc;
        pump = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) Log.i(TAG, "aria2c: " + line);
            } catch (IOException ignored) {
            }
        }, "aria2x-pump");
        pump.setDaemon(true);
        pump.start();
    }

    private void watch() {
        final Process p = proc;
        watchdog = new Thread(() -> {
            int code;
            try {
                code = p.waitFor();
            } catch (InterruptedException e) {
                return;
            }
            running = false;
            pid = -1;
            Log.w(TAG, "engine exited code=" + code);
            if (proc == p) {
                lastError = "engine exited (" + code + ")";
                updateNotification("stopped · code " + code);
            }
            releaseWake();
            stopSelf();
        }, "aria2x-watch");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void shutdown() {
        running = false;
        if (proc != null) {
            try {
                proc.destroy();
            } catch (Exception ignored) {
            }
            proc = null;
        }
        pid = -1;
        releaseWake();
    }

    private void fail(String msg) {
        lastError = msg;
        Log.e(TAG, msg);
        running = false;
        updateNotification("error: " + msg);
        stopForeground(true);
        stopSelf();
    }

    private static int pidOf(Process p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return (int) f.get(p);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- wake lock

    private void acquireWake() {
        if (wake != null) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aria2x:engine");
        wake.setReferenceCounted(false);
        wake.acquire();
    }

    private void releaseWake() {
        if (wake != null && wake.isHeld()) wake.release();
        wake = null;
    }

    // ---------------------------------------------------------------- cert

    /** Extracts the bundled CA bundle once so aria2 can verify TLS. */
    private void extractCert() {
        File out = new File(getFilesDir(), "cacert.pem");
        Prefs p = new Prefs(this);
        if (!"bundled".equals(p.get("caCert"))) return;
        if (out.exists() && out.length() > 1000) return;
        try (java.io.InputStream in = getAssets().open("cacert.pem");
             java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            Log.i(TAG, "ca bundle extracted: " + out.length() + " bytes");
        } catch (IOException e) {
            Log.w(TAG, "ca bundle extract failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- notification

    private void channel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Engine",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("aria2x")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        return b.build();
    }

    private void updateNotification(String text) {
        updateNotification(this, text);
    }

    public static void updateNotification(Context c, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Engine e = null;
        PendingIntent pi = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        b.setContentTitle("aria2x")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        try {
            nm.notify(NID, b.build());
        } catch (Exception ignored) {
        }
    }
}
