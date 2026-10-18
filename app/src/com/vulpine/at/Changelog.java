package com.vulpine.at;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Changelog {
    static boolean shown;

    static JSONObject entry(int code) {
        try (InputStream in = App.ctx.getAssets().open("changelog.json")) {
            byte[] b = new byte[in.available()];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            return new JSONObject(new String(b, 0, n, "UTF-8")).optJSONObject(String.valueOf(code));
        } catch (Exception e) {
            return null;
        }
    }

    static boolean freshInstall() {
        try {
            android.content.pm.PackageInfo pi = App.ctx.getPackageManager().getPackageInfo(App.ctx.getPackageName(), 0);
            return pi.firstInstallTime == pi.lastUpdateTime;
        } catch (Exception e) {
            return false;
        }
    }

    public static void maybeShow(MainActivity a) {
        if (shown) return;
        int seen = App.prefs.getInt("cl.seen", 0);
        if (seen >= App.VERSION_CODE) return;
        App.prefs.edit().putInt("cl.seen", App.VERSION_CODE).apply();
        if (App.prefs.getBoolean("cl.off", false) || (seen == 0 && freshInstall())) return;
        JSONObject e = entry(App.VERSION_CODE);
        if (e == null) return;
        shown = true;
        App.H.postDelayed(() -> {
            if (MainActivity.cur == a && !a.isFinishing()) show(a, e);
        }, 700);
    }

    public static void show(MainActivity a, JSONObject e) {
        Ov.Sheet s = new Ov.Sheet(a, true);
        s.scrim.setBackgroundColor(0x73000000);
        s.card.getChildAt(0).setVisibility(android.view.View.GONE);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(T.surface);
        float rr = T.dpf(18);
        cg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        s.card.setBackground(cg);
        s.card.setPadding(T.dp(20), T.dp(22), T.dp(20), T.dp(36) + a.insetBottom);
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) s.card.getLayoutParams();
        int sw = a.getResources().getDisplayMetrics().widthPixels;
        cp.width = Math.min(sw, T.dp(440));
        cp.gravity = Gravity.BOTTOM | Gravity.START;
        s.card.setLayoutParams(cp);
        s.apk = true;
        TextView h3 = W.text(a, "What's new", 17, 700, T.text);
        T.lh(h3, 25.5f);
        s.body.addView(h3, W.lpm(W.MP, W.WC, 0, 0, 0, 2));
        String date = e.optString("date", "");
        TextView sub = W.text(a, "Version " + e.optString("name", App.VERSION_NAME) + " · Changelog #" + App.VERSION_CODE + (date.isEmpty() ? "" : " · " + date), 12.5f, 500, T.textMuted);
        s.body.addView(sub, W.lpm(W.MP, W.WC, 0, 0, 0, 14));
        JSONArray items = e.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            LinearLayout row = W.row(a);
            row.setGravity(Gravity.TOP);
            TextView dot = W.text(a, "•", 14, 700, T.accent);
            T.lh(dot, 21);
            row.addView(dot, W.lpm(W.WC, W.WC, 0, 0, 10, 0));
            TextView t = W.text(a, items.optString(i), 14, 400, T.text);
            W.lines(t, 21);
            row.addView(t, W.lp(0, W.WC, 1));
            s.body.addView(row, W.lpm(W.MP, W.WC, 0, 0, 0, 10));
        }
        TextView sign = W.text(a, "Sincerely,\nThe Vulpine Team", 13.5f, 500, T.textSecondary);
        W.lines(sign, 20.25f);
        sign.setTypeface(sign.getTypeface(), android.graphics.Typeface.ITALIC);
        s.body.addView(sign, W.lpm(W.MP, W.WC, 0, 8, 0, 18));
        LinearLayout chk = W.row(a);
        chk.setGravity(Gravity.CENTER_VERTICAL);
        TextView box = W.text(a, "", 12, 700, T.accentFg);
        box.setGravity(Gravity.CENTER);
        boolean[] on = {false};
        Runnable paint = () -> {
            box.setText(on[0] ? "✓" : "");
            box.setBackground(on[0] ? W.round(T.accent, 6) : W.round(0, 6, T.mixA(T.text, .35f), 1.5f));
        };
        paint.run();
        chk.addView(box, W.lp(T.dp(20), T.dp(20)));
        TextView cl = W.text(a, "Don't show next time", 13.5f, 500, T.text);
        chk.addView(cl, W.lpm(W.WC, W.WC, 10, 0, 0, 0));
        chk.setPadding(0, T.dp(4), 0, T.dp(4));
        chk.setOnClickListener(v -> {
            on[0] = !on[0];
            paint.run();
        });
        s.body.addView(chk, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        TextView ok = Updater.btn2(a, "Got it", T.accent, T.accentFg);
        ok.setBackground(W.accentBg(12));
        ok.setOnClickListener(v -> s.close());
        s.onClose = () -> App.prefs.edit().putBoolean("cl.off", on[0]).apply();
        s.body.addView(ok, W.lp(W.MP, W.WC));
        s.show();
    }
}
