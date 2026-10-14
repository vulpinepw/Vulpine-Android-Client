package com.vulpine.at.pro;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Sync;
import com.vulpine.at.shop.K;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProBuy extends Page {
    JSONObject st, pending;
    int sel = 1;
    boolean alive = true, busy;
    LinearLayout box;

    public ProBuy() {
        super("Pro Box");
    }

    public static void open(MainActivity a) {
        Pro.status(true, d -> {
            if (d == null) {
                Ov.toast("Could not load subscription info", true);
                return;
            }
            ProBuy p = new ProBuy();
            p.st = d;
            JSONObject pe = d.optJSONObject("pending");
            if (pe != null && !pe.optString("paymentUrl").isEmpty()) p.pending = pe;
            if (a != null) a.push(p);
        });
    }

    @Override
    protected void content(LinearLayout body) {
        if (titleView != null) {
            T.text(titleView, K.LG, 700, T.text);
        }
        box = W.col(a);
        body.addView(box, W.lp(W.MP, W.WC));
        render();
        if (pending != null) poll();
    }

    @Override
    public void onDestroy() {
        alive = false;
    }

    double price() {
        return st.optDouble("pricePerMonth", 3.99);
    }

    String total(int m) {
        return "$" + String.format(Locale.US, "%.2f", price() * m);
    }

    void render() {
        Context c = a;
        box.removeAllViews();
        if (pending != null) {
            if (titleView != null) titleView.setText("Waiting for payment");
            TextView hint = K.text(c, "Complete the payment in the OxaPay tab. This updates automatically.", K.XS, 400, T.textSecondary);
            box.addView(hint, W.lp(W.MP, W.WC));
            LinearLayout sw = W.row(c);
            sw.setGravity(Gravity.CENTER);
            K.Spin sp = new K.Spin(c, 3, T.surface3, T.accent);
            sw.addView(sp, W.lp(T.dp(34), T.dp(34)));
            box.addView(sw, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
            box.addView(totalRow(c, Pro.labelFor(pending.optInt("months", 1)), "$" + String.format(Locale.US, "%.2f", pending.optDouble("amountUsd"))), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
            TextView again = ProBox.vproBtn(c, "Reopen payment page", true, v -> K.openUrl(pending.optString("paymentUrl")));
            box.addView(again, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
            TextView close = W.text(c, "Close", 18, 400, T.textSecondary);
            close.setPadding(T.dp(8), T.dp(4), T.dp(8), T.dp(4));
            close.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 999));
            close.setOnClickListener(v -> a.onBackPressed());
            LinearLayout.LayoutParams cl = W.lpm(W.WC, W.WC, 0, 12, 0, 0);
            cl.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(close, cl);
            return;
        }
        if (titleView != null) titleView.setText("Pro Box");
        JSONArray opts = st.optJSONArray("monthOptions");
        int[] o;
        if (opts == null || opts.length() == 0) o = new int[]{1, 3, 6, 12, 24, 36};
        else {
            o = new int[opts.length()];
            for (int i = 0; i < o.length; i++) o[i] = opts.optInt(i);
        }
        LinearLayout row = null;
        for (int i = 0; i < o.length; i++) {
            if (i % 2 == 0) {
                row = W.row(c);
                box.addView(row, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 8, 0, 0));
            }
            int m = o[i];
            LinearLayout b = W.col(c);
            b.setBackground(W.ripple(W.round(m == sel ? T.accentSoft : T.surface2, 12), 12));
            b.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
            b.addView(K.text(c, Pro.labelFor(m), K.SM, 600, T.text));
            TextView s = K.text(c, total(m) + " · $" + String.format(Locale.US, "%.2f", price()) + "/mo", K.XS, 400, T.textSecondary);
            b.addView(s, W.lpm(W.WC, W.WC, 0, 2, 0, 0));
            b.setOnClickListener(v -> {
                sel = m;
                render();
            });
            K.pressBtn(b);
            LinearLayout.LayoutParams lp = W.lp(0, W.WC, 1);
            if (i % 2 == 1) lp.leftMargin = T.dp(8);
            row.addView(b, lp);
        }
        if (o.length % 2 == 1) {
            View sp = new View(c);
            LinearLayout.LayoutParams lp = W.lp(0, 1, 1);
            lp.leftMargin = T.dp(8);
            row.addView(sp, lp);
        }
        box.addView(totalRow(c, "Total (one-time)", total(sel)), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        TextView pay = ProBox.vproBtn(c, "Pay with crypto", false, v -> buy());
        K.enable(pay, !busy);
        box.addView(pay, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
    }

    View totalRow(Context c, String l, String r) {
        LinearLayout t = W.row(c);
        t.setPadding(0, T.dp(8), 0, T.dp(8));
        t.addView(K.text(c, l, K.SM, 400, T.text), W.lp(0, W.WC, 1));
        t.addView(K.text(c, r, K.MD, 700, T.text));
        return t;
    }

    void buy() {
        if (busy) return;
        busy = true;
        render();
        int m = sel;
        App.bg(() -> {
            Http.Resp r = Http.post("/pro/purchase", App.jo("months", m));
            App.ui(() -> {
                busy = false;
                if (!r.ok()) {
                    String e = r.obj().optString("error", "");
                    Ov.toast(e.isEmpty() ? "Purchase failed" : e, true);
                    render();
                    return;
                }
                JSONObject o = r.obj();
                K.openUrl(o.optString("paymentUrl"));
                pending = o;
                render();
                poll();
            });
        });
    }

    void poll() {
        App.H.postDelayed(() -> {
            if (!alive || pending == null) return;
            Pro.status(true, d -> {
                if (!alive) return;
                if (d != null && d.optBoolean("active")) {
                    Ov.toast("Pro Box activated. Thanks for the support!");
                    App.bg(() -> Sync.refreshMe(false));
                    if (a != null && a.top() == this) a.pop();
                } else poll();
            });
        }, 5000);
    }
}
