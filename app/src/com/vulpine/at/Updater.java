package com.vulpine.at;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.VCrypto;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import org.json.JSONObject;

public final class Updater {
    public static volatile JSONObject latest;
    static int shownVc;
    static long checkedAt;
    static File pending;
    static volatile boolean busy, cancelled;
    static android.content.BroadcastReceiver statusRx;
    static final String ACTION_STATUS = "com.vulpine.at.UPDATE_STATUS";
    public static Runnable onAvailable;

    public static boolean available() {
        return latest != null && latest.optInt("versionCode") > App.VERSION_CODE;
    }

    public static void check(MainActivity a) {
        if (System.currentTimeMillis() - checkedAt < 15 * 60000L && latest != null) return;
        checkedAt = System.currentTimeMillis();
        App.bg(() -> {
            try {
                byte[] b = Http.bytes(Http.origin + "/download/latest.json", 64 * 1024);
                JSONObject o = new JSONObject(new String(b, "UTF-8"));
                latest = o;
                if (available()) App.ui(() -> {
                    if (onAvailable != null) onAvailable.run();
                    if (shownVc != o.optInt("versionCode")) {
                        shownVc = o.optInt("versionCode");
                        App.H.postDelayed(() -> sheet(a), 1200);
                    }
                });
            } catch (Exception e) {
            }
        });
    }

    public static void sheet(MainActivity a) {
        if (!available()) return;
        Ov.Sheet s = new Ov.Sheet(a, false);
        s.scrim.setBackgroundColor(0x73000000);
        s.scrim.setOnClickListener(null);
        s.card.getChildAt(0).setVisibility(android.view.View.GONE);
        android.graphics.drawable.GradientDrawable cg = new android.graphics.drawable.GradientDrawable();
        cg.setColor(T.surface);
        float rr = T.dpf(18);
        cg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        s.card.setBackground(cg);
        s.card.setPadding(T.dp(20), T.dp(22), T.dp(20), T.dp(36) + a.insetBottom);
        android.widget.FrameLayout.LayoutParams cp = (android.widget.FrameLayout.LayoutParams) s.card.getLayoutParams();
        int sw = a.getResources().getDisplayMetrics().widthPixels;
        cp.width = Math.min(sw, T.dp(440));
        cp.gravity = Gravity.BOTTOM | Gravity.START;
        s.card.setLayoutParams(cp);
        s.apk = true;
        TextView h3 = W.text(a, "Update available", 17, 700, T.text);
        T.lh(h3, 25.5f);
        s.body.addView(h3, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
        String vn = latest.optString("versionName", "");
        TextView p = W.text(a, vn.isEmpty() ? "A new version of Vulpine is available." : "Version " + vn + " is available. Update to keep using the latest Vulpine.", 13.5f, 400, T.textSecondary);
        W.lines(p, 20.25f);
        p.setAlpha(.8f);
        s.body.addView(p, W.lpm(W.MP, W.WC, 0, 0, 0, 18));
        LinearLayout r = W.row(a);
        TextView later = btn2(a, "Later", T.mixA(T.text, .09f), T.text);
        later.setOnClickListener(v -> s.close());
        r.addView(later, W.lp(0, W.WC, 1));
        TextView go = btn2(a, "Update now", T.accent, T.accentFg);
        go.setBackground(W.accentBg(12));
        go.setOnClickListener(v -> {
            if (busy) return;
            Prog.into(a, s);
            download(a);
        });
        LinearLayout.LayoutParams gp = W.lp(0, W.WC, 1);
        gp.leftMargin = T.dp(10);
        r.addView(go, gp);
        s.body.addView(r);
        s.show();
    }

    static TextView btn2(MainActivity a, String label, int bg, int fg) {
        TextView t = W.text(a, label, 14, 700, fg);
        T.lh(t, 21);
        t.setPadding(T.dp(12), t.getPaddingTop() + T.dp(11), T.dp(12), t.getPaddingBottom() + T.dp(11));
        t.setGravity(Gravity.CENTER);
        t.setBackground(W.round(bg, 12));
        return t;
    }

    static void download(MainActivity a) {
        if (latest == null || busy) return;
        busy = true;
        cancelled = false;
        if (Prog.cur == null) Prog.open(a);
        Prog.downloading(0, 0);
        String apk = latest.optString("apk", "/download/vulpine-2.apk");
        String url = apk.startsWith("http") ? apk : Http.origin + apk;
        String sha = latest.optString("sha256", "");
        App.bg(() -> {
            File out = new File(new File(a.getCacheDir(), "share"), "vulpine-update-" + latest.optInt("versionCode") + ".apk");
            out.getParentFile().mkdirs();
            File[] old = out.getParentFile().listFiles();
            if (old != null) for (File x : old) if (x.getName().startsWith("vulpine-update") && !x.equals(out)) x.delete();
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", Http.userAgent);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long total = c.getContentLengthLong();
                if (total <= 0) total = latest.optLong("size", 0);
                final long tot = total;
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] b = new byte[65536];
                    int n;
                    long got = 0, last = 0;
                    while ((n = in.read(b)) > 0) {
                        if (cancelled) throw new java.io.InterruptedIOException("cancelled");
                        fo.write(b, 0, n);
                        md.update(b, 0, n);
                        got += n;
                        long now = System.currentTimeMillis();
                        if (now - last > 80) {
                            last = now;
                            final long g = got;
                            App.ui(() -> Prog.downloading(g, tot));
                        }
                    }
                    final long g = got;
                    App.ui(() -> Prog.downloading(g, tot));
                }
                StringBuilder hx = new StringBuilder();
                for (byte x : md.digest()) hx.append(String.format("%02x", x));
                if (!sha.isEmpty() && !sha.equalsIgnoreCase(hx.toString())) {
                    out.delete();
                    App.ui(() -> Prog.fail(a, "The download was damaged. Please try again."));
                    return;
                }
                App.ui(() -> install(a, out));
            } catch (Exception e) {
                out.delete();
                if (cancelled) App.ui(Prog::close);
                else App.ui(() -> Prog.fail(a, "Couldn't download the update. Check your connection and try again."));
            } finally {
                busy = false;
                if (c != null) c.disconnect();
            }
        });
    }

    static void install(MainActivity a, File f) {
        try {
            if (!a.getPackageManager().canRequestPackageInstalls()) {
                pending = f;
                Prog.status("Waiting for permission", "Allow installs from Vulpine, then come back to finish the update.");
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                return;
            }
            Prog.installing();
            App.bg(() -> {
                if (!session(a, f)) App.ui(() -> {
                    viewIntent(a, f);
                    Prog.closeSoon();
                });
            });
        } catch (Exception e) {
            Prog.fail(a, "Couldn't open the update file.");
        }
    }

    static void viewIntent(MainActivity a, File f) {
        try {
            Uri u = Uri.parse("content://" + Share.AUTH + "/" + Uri.encode(f.getName()));
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(i);
        } catch (Exception e) {
            Ov.toast("Could not open the update file", true);
        }
    }

    static boolean session(MainActivity a, File f) {
        android.content.pm.PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            android.content.pm.PackageInstaller.SessionParams sp = new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(a.getPackageName());
            sp.setSize(f.length());
            if (android.os.Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            id = pi.createSession(sp);
            try (android.content.pm.PackageInstaller.Session ss = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(f); java.io.OutputStream o = ss.openWrite("base.apk", 0, f.length())) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                    ss.fsync(o);
                }
                listen(a, f);
                Intent cb = new Intent(ACTION_STATUS).setPackage(a.getPackageName());
                int fl = android.app.PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? android.app.PendingIntent.FLAG_MUTABLE : 0);
                android.app.PendingIntent p = android.app.PendingIntent.getBroadcast(a, id, cb, fl);
                ss.commit(p.getIntentSender());
            }
            return true;
        } catch (Exception e) {
            if (id >= 0) try {
                pi.abandonSession(id);
            } catch (Exception x) {
            }
            return false;
        }
    }

    static synchronized void listen(MainActivity a, File f) {
        if (statusRx != null) return;
        statusRx = new android.content.BroadcastReceiver() {
            public void onReceive(android.content.Context c, Intent i) {
                int st = i.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999);
                if (st == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            c.startActivity(confirm);
                        } catch (Exception e) {
                            viewIntent(a, f);
                        }
                    }
                    Prog.closeSoon();
                    return;
                }
                if (st == android.content.pm.PackageInstaller.STATUS_SUCCESS) return;
                if (st == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Prog.close();
                    return;
                }
                String msg = i.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE);
                App.report(a, "update.txt", "Vulpine " + App.VERSION_NAME + " (" + App.VERSION_CODE + ")\nUPDATE STATUS " + st + " " + msg + "\n");
                String m = st == android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE ? "Not enough storage to install the update." : st == android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT || st == android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ? "This update can't replace the installed app." : "The update could not be installed.";
                if (Prog.cur != null) Prog.fail(a, m);
                else Ov.toast(m, true);
            }
        };
        android.content.IntentFilter flt = new android.content.IntentFilter(ACTION_STATUS);
        if (android.os.Build.VERSION.SDK_INT >= 33) App.ctx.registerReceiver(statusRx, flt, android.content.Context.RECEIVER_NOT_EXPORTED);
        else App.ctx.registerReceiver(statusRx, flt);
    }

    public static void resume(MainActivity a) {
        File f = pending;
        if (f == null) return;
        try {
            if (!a.getPackageManager().canRequestPackageInstalls()) return;
        } catch (Exception e) {
            return;
        }
        pending = null;
        install(a, f);
    }

    static final class Prog {
        static Prog cur;
        Ov.Sheet s;
        TextView title, sub, pct;
        Bar bar;
        LinearLayout btns;
        MainActivity a;

        static void open(MainActivity a) {
            Ov.Sheet s = new Ov.Sheet(a, false);
            s.scrim.setBackgroundColor(0x73000000);
            s.scrim.setOnClickListener(null);
            s.card.getChildAt(0).setVisibility(android.view.View.GONE);
            android.graphics.drawable.GradientDrawable cg = new android.graphics.drawable.GradientDrawable();
            cg.setColor(T.surface);
            float rr = T.dpf(18);
            cg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
            s.card.setBackground(cg);
            s.card.setPadding(T.dp(20), T.dp(22), T.dp(20), T.dp(36) + a.insetBottom);
            s.apk = true;
            into(a, s);
            s.show();
        }

        static void into(MainActivity a, Ov.Sheet s) {
            Prog p = new Prog();
            p.a = a;
            p.s = s;
            cur = p;
            s.body.removeAllViews();
            p.title = W.text(a, "Downloading update", 17, 700, T.text);
            T.lh(p.title, 25.5f);
            s.body.addView(p.title, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
            p.sub = W.text(a, "Starting…", 13.5f, 400, T.textSecondary);
            W.lines(p.sub, 20.25f);
            p.sub.setAlpha(.8f);
            s.body.addView(p.sub, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
            p.bar = new Bar(a);
            s.body.addView(p.bar, W.lpm(W.MP, T.dp(8), 0, 0, 0, 8));
            p.pct = W.text(a, "", 12.5f, 600, T.textSecondary);
            p.pct.setFontFeatureSettings("tnum");
            p.pct.setGravity(Gravity.END);
            s.body.addView(p.pct, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
            p.btns = W.row(a);
            s.body.addView(p.btns, W.lp(W.MP, W.WC));
            p.cancelBtn();
            s.onClose = () -> {
                if (cur == p) cur = null;
                if (busy) cancelled = true;
            };
        }

        void cancelBtn() {
            btns.removeAllViews();
            TextView c = btn2(a, "Cancel", T.mixA(T.text, .09f), T.text);
            c.setOnClickListener(v -> {
                cancelled = true;
                close();
            });
            btns.addView(c, W.lp(0, W.WC, 1));
        }

        static void downloading(long got, long total) {
            Prog p = cur;
            if (p == null) return;
            p.title.setText("Downloading update");
            String mb = String.format(java.util.Locale.US, "%.1f", got / 1048576.0);
            if (total > 0) {
                float f = Math.min(1f, got / (float) total);
                p.bar.set(f);
                p.sub.setText(mb + " MB of " + String.format(java.util.Locale.US, "%.1f", total / 1048576.0) + " MB");
                p.pct.setText(Math.round(f * 100) + "%");
            } else {
                p.bar.set(-1);
                p.sub.setText(got > 0 ? mb + " MB" : "Starting…");
                p.pct.setText("");
            }
        }

        static void installing() {
            Prog p = cur;
            if (p == null) return;
            p.title.setText("Installing update");
            p.sub.setText("Opening the installer…");
            p.pct.setText("");
            p.bar.set(-1);
            p.btns.removeAllViews();
        }

        static void status(String t, String sub) {
            Prog p = cur;
            if (p == null) return;
            p.title.setText(t);
            p.sub.setText(sub);
            p.pct.setText("");
            p.bar.set(-1);
            p.cancelBtn();
        }

        static void fail(MainActivity a, String msg) {
            Prog p = cur;
            if (p == null) {
                Ov.toast(msg, true);
                return;
            }
            p.title.setText("Update failed");
            p.sub.setText(msg);
            p.pct.setText("");
            p.bar.error();
            p.btns.removeAllViews();
            TextView c = btn2(a, "Close", T.mixA(T.text, .09f), T.text);
            c.setOnClickListener(v -> close());
            p.btns.addView(c, W.lp(0, W.WC, 1));
            TextView again = btn2(a, "Try again", T.accent, T.accentFg);
            again.setBackground(W.accentBg(12));
            again.setOnClickListener(v -> {
                if (!busy) download(a);
            });
            LinearLayout.LayoutParams gp = W.lp(0, W.WC, 1);
            gp.leftMargin = T.dp(10);
            p.btns.addView(again, gp);
        }

        static void close() {
            Prog p = cur;
            cur = null;
            if (p != null) p.s.close();
        }

        static void closeSoon() {
            Prog p = cur;
            if (p == null) return;
            App.H.postDelayed(() -> {
                if (cur == p) close();
            }, 700);
        }
    }

    static final class Bar extends android.view.View {
        final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF rc = new android.graphics.RectF();
        float target = -1, shown;
        long t0 = android.os.SystemClock.uptimeMillis();

        Bar(android.content.Context c) {
            super(c);
            track.setColor(T.mixA(T.text, .09f));
            fill.setColor(T.accent);
        }

        void set(float f) {
            fill.setColor(T.accent);
            if (f >= 0 && target < 0) shown = 0;
            target = f;
            invalidate();
        }

        void error() {
            fill.setColor(0xffef4444);
            target = 1;
            shown = 1;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            rc.set(0, 0, w, h);
            c.drawRoundRect(rc, r, r, track);
            if (target < 0) {
                float t = ((android.os.SystemClock.uptimeMillis() - t0) % 1300) / 1300f;
                float e = (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
                float seg = w * .32f;
                float x = -seg + (w + seg) * e;
                c.save();
                c.clipRect(0, 0, w, h);
                rc.set(Math.max(0, x), 0, Math.min(w, x + seg), h);
                if (rc.width() > 0) c.drawRoundRect(rc, r, r, fill);
                c.restore();
                if (isAttachedToWindow()) postInvalidateOnAnimation();
                return;
            }
            shown += (target - shown) * .18f;
            if (Math.abs(target - shown) < .002f) shown = target;
            rc.set(0, 0, Math.max(h, w * shown), h);
            c.drawRoundRect(rc, r, r, fill);
            if (shown != target && isAttachedToWindow()) postInvalidateOnAnimation();
        }
    }
}
