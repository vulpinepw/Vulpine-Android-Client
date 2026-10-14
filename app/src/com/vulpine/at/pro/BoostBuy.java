package com.vulpine.at.pro;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.shop.K;
import com.vulpine.at.shop.Seg;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class BoostBuy {
    final Context c;
    final JSONObject cfg;
    final Runnable done;
    Ov.Sheet s;
    int count = 1, months = 1;
    boolean busy, closed;
    String wait;
    LinearLayout area;

    BoostBuy(Context c, JSONObject cfg, Runnable done) {
        this.c = c;
        this.cfg = cfg;
        this.done = done;
    }

    static int[] ints(JSONArray a, int[] def) {
        if (a == null || a.length() == 0) return def;
        int[] r = new int[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = a.optInt(i);
        return r;
    }

    void show() {
        s = new Ov.Sheet(c);
        s.body.addView(K.text(c, "Get boosts", K.LG, 600, T.text), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        area = W.col(c);
        s.body.addView(area, W.lp(W.MP, W.WC));
        s.onClose = () -> closed = true;
        render();
        s.show();
    }

    void render() {
        area.removeAllViews();
        double price = cfg == null ? 1.99 : cfg.optDouble("priceUsd", 1.99);
        int[] counts = ints(cfg == null ? null : cfg.optJSONArray("counts"), new int[]{1, 3, 5, 10});
        int[] ms = ints(cfg == null ? null : cfg.optJSONArray("months"), new int[]{1, 3, 12});
        if (wait != null) {
            LinearLayout w = W.col(c);
            w.setGravity(Gravity.CENTER_HORIZONTAL);
            w.setPadding(0, T.dp(20), 0, T.dp(20));
            K.Spin sp = new K.Spin(c, 3, T.mixA(T.text, .15f), 0xff8b5cf6);
            w.addView(sp, W.lp(T.dp(28), T.dp(28)));
            TextView a = K.text(c, "Waiting for your payment…", K.SM, 400, T.text);
            a.setGravity(Gravity.CENTER);
            w.addView(a, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
            TextView b = K.text(c, "Finish paying in the new tab. Your boosts appear here automatically.", K.XS, 400, T.textMuted);
            b.setGravity(Gravity.CENTER);
            w.addView(b, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
            area.addView(w, W.lp(W.MP, W.WC));
        } else {
            area.addView(K.text(c, "Each boost lifts a group of your choice. Pro Box members get " + (cfg == null ? 2 : cfg.optInt("proFree", 2)) + " free boosts.", K.XS, 400, T.textMuted));
            area.addView(K.text(c, "Boosts", K.SM, 500, T.text), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
            String[] cl = new String[counts.length];
            int ci = 0;
            for (int i = 0; i < counts.length; i++) {
                cl[i] = String.valueOf(counts[i]);
                if (counts[i] == count) ci = i;
            }
            area.addView(new Seg(c, cl, ci, K.SM, i -> {
                count = counts[i];
                render();
            }), W.lpm(W.MP, T.dp(42), 0, 12, 0, 0));
            area.addView(K.text(c, "Duration", K.SM, 500, T.text), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
            String[] ml = new String[ms.length];
            int mi = 0;
            for (int i = 0; i < ms.length; i++) {
                ml[i] = ms[i] == 12 ? "1 year" : ms[i] + (ms[i] > 1 ? " months" : " month");
                if (ms[i] == months) mi = i;
            }
            area.addView(new Seg(c, ml, mi, K.SM, i -> {
                months = ms[i];
                render();
            }), W.lpm(W.MP, T.dp(42), 0, 12, 0, 0));
            LinearLayout tr = W.row(c);
            tr.addView(K.text(c, "Total (one-time)", K.SM, 400, T.text), W.lp(0, W.WC, 1));
            tr.addView(K.text(c, "$" + String.format(Locale.US, "%.2f", price * count * months), K.LG, 700, T.text));
            area.addView(tr, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        }
        LinearLayout acts = W.row(c);
        acts.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        acts.setPadding(0, T.dp(8), 0, T.dp(4));
        acts.addView(K.btn(c, wait != null ? "Close" : "Cancel", "ghost", v -> s.close()));
        if (wait == null) {
            TextView pay = K.btn(c, busy ? "Opening…" : "Pay with crypto", "primary", v -> pay());
            K.enable(pay, !busy);
            acts.addView(pay, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        }
        area.addView(acts, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
    }

    void pay() {
        if (busy) return;
        busy = true;
        render();
        App.bg(() -> {
            Http.Resp r = Http.post("/shop/boost/purchase", App.jo("count", count, "months", months));
            App.ui(() -> {
                busy = false;
                if (!r.ok()) {
                    String e = r.obj().optString("error");
                    Ov.toast("payments_unavailable".equals(e) ? "Payments are unavailable right now" : "too_many_pending_invoices".equals(e) ? "You have too many open invoices" : "slow_down".equals(e) ? "Please wait a few seconds" : "Could not create the invoice", true);
                    render();
                    return;
                }
                JSONObject js = r.obj();
                K.openUrl(js.optString("paymentUrl"));
                wait = js.optString("orderId");
                render();
                poll(wait);
            });
        });
    }

    void poll(String id) {
        App.H.postDelayed(() -> {
            if (closed || !id.equals(wait)) return;
            App.bg(() -> {
                Http.Resp r = Http.get("/shop/boost/order/" + android.net.Uri.encode(id));
                App.ui(() -> {
                    if (closed) return;
                    String st = r.ok() ? r.obj().optString("status") : "";
                    if ("paid".equals(st)) {
                        Ov.toast("Boosts added - thank you!");
                        s.close();
                        if (done != null) done.run();
                    } else if ("expired".equals(st) || "underpaid".equals(st)) {
                        wait = null;
                        render();
                        Ov.toast("underpaid".equals(st) ? "Payment was too low" : "Invoice expired", true);
                    } else poll(id);
                });
            });
        }, 5000);
    }
}
