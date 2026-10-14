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
    static boolean shown;
    static long checkedAt;
    static File pending;
    public static Runnable onAvailable;

    public static boolean available() {
        return latest != null && latest.optInt("versionCode") > App.VERSION_CODE;
    }

    public static void check(MainActivity a) {
        if (System.currentTimeMillis() - checkedAt < 6 * 3600000L && latest != null) return;
        checkedAt = System.currentTimeMillis();
        App.bg(() -> {
            try {
                byte[] b = Http.bytes(Http.origin + "/download/latest.json", 64 * 1024);
                JSONObject o = new JSONObject(new String(b, "UTF-8"));
                latest = o;
                if (available()) App.ui(() -> {
                    if (onAvailable != null) onAvailable.run();
                    if (!shown) {
                        shown = true;
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
            s.close();
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
        if (latest == null) return;
        Ov.toast("Downloading update…");
        String apk = latest.optString("apk", "/download/vulpine-2.apk");
        String url = apk.startsWith("http") ? apk : Http.origin + apk;
        String sha = latest.optString("sha256", "");
        App.bg(() -> {
            File out = new File(new File(a.getCacheDir(), "share"), "vulpine-update.apk");
            out.getParentFile().mkdirs();
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", Http.userAgent);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        fo.write(b, 0, n);
                        md.update(b, 0, n);
                    }
                }
                StringBuilder hx = new StringBuilder();
                for (byte x : md.digest()) hx.append(String.format("%02x", x));
                if (!sha.isEmpty() && !sha.equalsIgnoreCase(hx.toString())) {
                    out.delete();
                    App.ui(() -> Ov.toast("Update failed integrity check", true));
                    return;
                }
                App.ui(() -> install(a, out));
            } catch (Exception e) {
                App.ui(() -> Ov.toast("Update download failed", true));
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    static void install(MainActivity a, File f) {
        try {
            if (!a.getPackageManager().canRequestPackageInstalls()) {
                pending = f;
                Ov.toast("Allow installs from Vulpine, then come back to install");
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                return;
            }
            Uri u = Uri.parse("content://" + Share.AUTH + "/" + Uri.encode(f.getName()));
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(i);
        } catch (Exception e) {
            Ov.toast("Could not open the update file", true);
        }
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
}
