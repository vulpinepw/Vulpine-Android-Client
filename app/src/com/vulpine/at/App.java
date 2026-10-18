package com.vulpine.at;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.core.Chats;
import com.vulpine.core.FileKv;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import com.vulpine.core.VCrypto;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class App {
    public static Context ctx;
    public static final Handler H = new Handler(Looper.getMainLooper());
    static final java.util.concurrent.atomic.AtomicInteger bgN = new java.util.concurrent.atomic.AtomicInteger();
    public static final ExecutorService BG = new java.util.concurrent.ThreadPoolExecutor(6, 64, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(), r -> {
        Thread t = new Thread(r, "vbg-" + bgN.incrementAndGet());
        t.setDaemon(true);
        return t;
    }, new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    public static SharedPreferences prefs;
    public static FileKv kv;
    public static boolean fg;
    static boolean inited;
    public static final int VERSION_CODE = 63;
    public static final String VERSION_NAME = "3.3.1";

    public interface PermCb {
        void on(boolean granted);
    }

    public interface ResultCb {
        void on(int res, Intent data);
    }

    static final java.util.Map<Integer, PermCb> perms = new java.util.HashMap<>();
    static final java.util.Map<Integer, ResultCb> results = new java.util.HashMap<>();
    static int reqSeq = 100;

    public static synchronized void init(Context c) {
        if (inited) return;
        inited = true;
        ctx = c;
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((th, ex) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                String rep = "Vulpine " + VERSION_NAME + " (" + VERSION_CODE + ") Android " + android.os.Build.VERSION.RELEASE + " " + android.os.Build.MODEL + "\nthread " + th.getName() + "\n" + sw;
                java.nio.file.Files.write(new File(c.getFilesDir(), "crash.txt").toPath(), rep.getBytes("UTF-8"));
            } catch (Throwable e) {
            }
            if (prev != null) prev.uncaughtException(th, ex);
        });
        prefs = c.getSharedPreferences("vulpine", Context.MODE_PRIVATE);
        startWatchdog(c);
        kv = new FileKv(new File(c.getFilesDir(), "kv"));
        Session.kv = kv;
        String host = prefs.getString("host", "https://vulpine.pw");
        Http.origin = host;
        Http.onMain = () -> Looper.myLooper() == Looper.getMainLooper();
        Http.mediaCookie = kv.get("vulpine.mediaCookie");
        Http.onMediaCookie = v -> kv.put("vulpine.mediaCookie", v);
        Http.strict = Boolean.getBoolean("vulpine.strict");
        Rt.I.url = host.replaceFirst("^http", "ws") + "/ws";
        Http.userAgent = "Mozilla/5.0 (Linux; Android " + android.os.Build.VERSION.RELEASE + "; " + android.os.Build.MODEL + ") Vulpine/" + VERSION_NAME + " vulpineapk/" + VERSION_CODE + " native";
        Http.init(new PersistCookies(kv));
        try {
            List<String> w = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("bip39.txt")))) {
                String l;
                while ((l = r.readLine()) != null) if (!l.isEmpty()) w.add(l);
            }
            VCrypto.words = w.toArray(new String[0]);
        } catch (Exception e) {
        }
        T.init(c);
        Img.init(c);
        Session.load();
        Prefs.applyTheme();
        Http.onUnauthorized = () -> BG.execute(App::authLost);
        Chats.I.wire();
        com.vulpine.at.secret.SecretScreen.clearTmp();
    }

    static volatile long authLostAt;

    static void authLost() {
        if (System.currentTimeMillis() - authLostAt < 5000) return;
        authLostAt = System.currentTimeMillis();
        String tok = Http.token;
        if (tok == null) return;
        Http.Resp r = Http.raw("GET", Http.api("/me"), null, tok, 15000);
        if (r.status == 401 && tok.equals(Http.token)) H.post(() -> logout(false));
    }

    public static void boot(MainActivity a) {
        crashReport(a);
        if (!Session.loggedIn()) {
            String old = ctx.getSharedPreferences("vpush", Context.MODE_PRIVATE).getString("tok", "");
            if (!old.isEmpty() && !prefs.getBoolean("migrated", false)) {
                prefs.edit().putBoolean("migrated", true).apply();
                a.setRoot(new com.vulpine.at.auth.Splash());
                BG.execute(() -> migrate(a, old));
                return;
            }
            a.setRoot(new com.vulpine.at.auth.AuthScreen());
            return;
        }
        if (Session.id == null) {
            a.setRoot(new com.vulpine.at.auth.Splash());
            BG.execute(() -> {
                Http.Resp r = Http.post("/auth/token-login", jo("token", Session.token()));
                VCrypto.Identity id = r.ok() ? VCrypto.Identity.from(r.obj()) : null;
                H.post(() -> {
                    if (id != null) {
                        Session.id = id;
                        Session.save();
                        enterApp(a);
                    } else if (r.status == 401) logout(false);
                    else enterApp(a);
                });
            });
            return;
        }
        enterApp(a);
    }

    static void migrate(MainActivity a, String tok) {
        Http.Resp tl = Http.raw("POST", Http.api("/auth/token-login"), jo("token", tok), null, 20000);
        Http.Resp me = tl.ok() ? Http.raw("GET", Http.api("/me"), null, tok, 20000) : null;
        H.post(() -> {
            if (tl.ok() && me != null && me.ok()) {
                JSONObject m = me.obj(), s = new JSONObject();
                try {
                    java.util.Iterator<String> it = m.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        s.put(k, m.get(k));
                    }
                    s.put("token", tok).put("userId", m.optString("id"));
                    JSONObject st = Sync.defaults();
                    JSONObject ms = m.optJSONObject("settings");
                    if (ms != null) {
                        java.util.Iterator<String> i2 = ms.keys();
                        while (i2.hasNext()) {
                            String k = i2.next();
                            st.put(k, ms.get(k));
                        }
                    }
                    s.put("settings", st);
                } catch (Exception e) {
                }
                if (m.has("hasPin")) kv.put("vulpine.pinState", jo("u", m.optString("id"), "p", m.optBoolean("hasPin") ? 1 : 0).toString());
                Session.login(s, VCrypto.Identity.from(tl.obj()));
                afterLogin(a);
            } else a.setRoot(new com.vulpine.at.auth.AuthScreen());
        });
    }

    static volatile long tick;
    static volatile boolean stuckSaved, bgSaved;

    static void startWatchdog(Context c) {
        Thread w = new Thread(() -> {
            while (true) {
                long sent = System.currentTimeMillis();
                H.post(() -> tick = System.currentTimeMillis());
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    return;
                }
                long[] probe = {0};
                try {
                    BG.execute(() -> probe[0] = 1);
                } catch (Throwable e) {
                }
                if (tick < sent && !stuckSaved) {
                    stuckSaved = true;
                    StringBuilder b = new StringBuilder("Vulpine " + VERSION_NAME + " (" + VERSION_CODE + ") Android " + android.os.Build.VERSION.RELEASE + " " + android.os.Build.MODEL + "\nFROZEN main thread for 6s+\n");
                    for (StackTraceElement e : Looper.getMainLooper().getThread().getStackTrace()) b.append("    at ").append(e).append('\n');
                    report(c, "stuck.txt", b.toString());
                } else if (tick >= sent) stuckSaved = false;
                if (probe[0] == 0 && !bgSaved) {
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (probe[0] == 0) {
                        bgSaved = true;
                        StringBuilder b = new StringBuilder("Vulpine " + VERSION_NAME + " (" + VERSION_CODE + ")\nBACKGROUND POOL STALLED\n");
                        for (java.util.Map.Entry<Thread, StackTraceElement[]> en : Thread.getAllStackTraces().entrySet()) {
                            if (!en.getKey().getName().startsWith("vbg")) continue;
                            b.append(en.getKey().getName()).append('\n');
                            int k = 0;
                            for (StackTraceElement e : en.getValue()) if (k++ < 14) b.append("    at ").append(e).append('\n');
                        }
                        report(c, "stuck.txt", b.toString());
                    }
                }
            }
        }, "vulpine-watchdog");
        w.setDaemon(true);
        w.start();
    }

    public static void report(Context c, String name, String text) {
        try {
            java.nio.file.Files.write(new File(c.getFilesDir(), name).toPath(), text.getBytes("UTF-8"));
        } catch (Throwable e) {
        }
    }

    static String take(String name) {
        File f = new File(ctx.getFilesDir(), name);
        if (!f.exists()) return "";
        try {
            String s = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            f.delete();
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    static boolean reported;

    static void crashReport(MainActivity a) {
        if (reported) return;
        reported = true;
        String rep = take("crash.txt");
        String st = take("stuck.txt");
        String ex = take("ext.txt");
        if (!st.isEmpty()) rep = rep + (rep.isEmpty() ? "" : "\n\n") + st;
        if (!ex.isEmpty()) rep = rep + (rep.isEmpty() ? "" : "\n\n") + ex;
        if (rep.isEmpty()) return;
        String r = rep.length() > 6000 ? rep.substring(0, 6000) : rep;
        H.postDelayed(() -> {
            Ov.Sheet s = new Ov.Sheet(a);
            s.title("Vulpine had a problem");
            s.msg("Copy this report and send it to the developer so it can be fixed.");
            android.widget.TextView t = com.vulpine.at.ui.W.text(a, r, 11, 400, T.textSecondary);
            t.setTypeface(android.graphics.Typeface.MONOSPACE);
            t.setMaxLines(14);
            s.body.addView(t);
            android.widget.LinearLayout row = com.vulpine.at.ui.W.row(a);
            row.setGravity(android.view.Gravity.END);
            row.setPadding(0, T.dp(16), 0, 0);
            row.addView(com.vulpine.at.ui.W.btn(a, "Close", false, v -> s.close()));
            android.widget.TextView cp = com.vulpine.at.ui.W.btn(a, "Copy report", true, v -> {
                ((android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("crash", r));
                Ov.toast("Report copied");
                s.close();
            });
            android.widget.LinearLayout.LayoutParams lp = com.vulpine.at.ui.W.lp(com.vulpine.at.ui.W.WC, com.vulpine.at.ui.W.WC);
            lp.leftMargin = T.dp(8);
            row.addView(cp, lp);
            s.body.addView(row);
            s.show();
            s.scrim.setElevation(T.dpf(200));
        }, 1500);
    }

    public static void enterApp(MainActivity a) {
        Pin.gate(a, () -> {
            a.setRoot(new com.vulpine.at.home.Home());
            Rt.I.start();
            com.vulpine.at.secret.Sec.wire();
            com.vulpine.at.people.Pres.start();
            com.vulpine.at.call.Calls.init();
            BG.execute(com.vulpine.at.settings.StoragePage::trim);
            Sync.start();
            Push.ensure(ctx);
            Updater.check(a);
            Changelog.maybeShow(a);
            askNotif(a);
            crashReport(a);
        });
    }

    public static void afterLogin(MainActivity a) {
        Prefs.applyTheme();
        a.applyBars();
        enterApp(a);
    }

    public static void logout(boolean server) {
        String tok = Http.token;
        if (server && tok != null) BG.execute(() -> Http.raw("POST", Http.api("/auth/logout"), null, tok, 8000));
        com.vulpine.at.call.Calls.reset();
        Rt.I.stop();
        Sync.stop();
        Push.stop(ctx);
        Session.logout();
        Chats.I.reset();
        com.vulpine.at.secret.Sec.reset();
        kv.clear("vulpine.msgs.");
        kv.clear("vulpine.convlist.");
        kv.clear("vulpine.gk.");
        kv.remove("vulpine.pinState");
        kv.remove("vulpine.mediaCookie");
        Http.mediaCookie = null;
        Http.cookies().getCookieStore().removeAll();
        MainActivity a = MainActivity.cur;
        if (a != null) {
            Prefs.applyTheme();
            a.applyBars();
            a.setRoot(new com.vulpine.at.auth.AuthScreen());
        }
    }

    static final Runnable bgStop = () -> {
        if (!fg && !Calls.active()) Rt.I.stop();
    };

    public static void foreground(boolean on) {
        fg = on;
        Rt.I.foregroundHint = on;
        H.removeCallbacks(bgStop);
        if (on) {
            if (Session.loggedIn() && Pin.unlocked) Rt.I.start();
            Rt.I.wakeUp(false);
            Sync.onForeground();
            Pin.onResume();
            MainActivity a = MainActivity.cur;
            if (a != null) Updater.resume(a);
        } else {
            Pin.onPause();
            H.postDelayed(bgStop, 60000);
        }
    }

    static void askNotif(MainActivity a) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notif.asked", false)) {
            prefs.edit().putBoolean("notif.asked", true).apply();
            H.postDelayed(() -> requestPerms(a, new String[]{"android.permission.POST_NOTIFICATIONS"}, ok -> askBattery(a)), 1500);
        } else askBattery(a);
    }

    static void askBattery(MainActivity a) {
        try {
            if (prefs.getBoolean("batteryAsked", false)) return;
            android.os.PowerManager pm = (android.os.PowerManager) a.getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isIgnoringBatteryOptimizations(a.getPackageName())) return;
            prefs.edit().putBoolean("batteryAsked", true).apply();
            a.startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
        }
    }

    public static boolean overlayBack() {
        return Ov.back();
    }

    public static void handleIntent(MainActivity a, Intent i) {
        Links.handle(a, i);
    }

    public static void requestPerms(MainActivity a, String[] p, PermCb cb) {
        boolean all = true;
        for (String x : p) if (a.checkSelfPermission(x) != android.content.pm.PackageManager.PERMISSION_GRANTED) all = false;
        if (all) {
            cb.on(true);
            return;
        }
        int code = reqSeq++;
        perms.put(code, cb);
        a.requestPermissions(p, code);
    }

    static void permResult(int code, String[] p, int[] res) {
        PermCb cb = perms.remove(code);
        if (cb == null) return;
        boolean ok = res.length > 0;
        for (int r : res) if (r != android.content.pm.PackageManager.PERMISSION_GRANTED) ok = false;
        cb.on(ok);
    }

    public static void startForResult(MainActivity a, Intent i, ResultCb cb) {
        int code = reqSeq++;
        results.put(code, cb);
        try {
            a.startActivityForResult(i, code);
        } catch (Exception e) {
            results.remove(code);
            cb.on(android.app.Activity.RESULT_CANCELED, null);
        }
    }

    static void activityResult(MainActivity a, int req, int res, Intent data) {
        ResultCb cb = results.remove(req);
        if (cb != null) cb.on(res, data);
    }

    public static JSONObject jo(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
        } catch (Exception e) {
        }
        return o;
    }

    public static void ui(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else H.post(r);
    }

    public static void bg(Runnable r) {
        BG.execute(r);
    }
}
