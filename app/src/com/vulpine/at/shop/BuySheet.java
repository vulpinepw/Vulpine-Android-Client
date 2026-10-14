package com.vulpine.at.shop;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.deco.Deco;
import com.vulpine.at.deco.DecoCard;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import org.json.JSONArray;
import org.json.JSONObject;

public final class BuySheet {
    final ShopTab shop;
    JSONObject cat;
    final String type, id;
    Ov.Sheet sheet;
    LinearLayout acts;
    JSONObject wait;
    boolean busy, closed;
    Context c;

    BuySheet(ShopTab shop, JSONObject cat, String type, String id) {
        this.shop = shop;
        this.cat = cat;
        this.type = type;
        this.id = id;
    }

    public static void open(ShopTab shop, JSONObject cat, String type, String id) {
        new BuySheet(shop, cat, type, id).show();
    }

    static JSONObject find(JSONArray a, String id) {
        for (int i = 0; a != null && i < a.length(); i++) if (id.equals(a.optJSONObject(i).optString("id"))) return a.optJSONObject(i);
        return null;
    }

    static String errText(String m) {
        switch (m == null ? "" : m) {
            case "payments_unavailable": return "Payments are unavailable right now.";
            case "too_many_pending_invoices": return "You already have unpaid invoices. Finish or let them expire first.";
            case "slow_down": return "Please wait a few seconds and try again.";
            case "already_owned": return "You already own this.";
            case "payment_provider_error": return "The payment provider is not responding. Try again later.";
            default: return "Something went wrong. Try again.";
        }
    }

    void show() {
        android.widget.FrameLayout h = Ov.host();
        if (h == null) return;
        c = h.getContext();
        JSONArray items = cat.optJSONArray("items");
        JSONObject applied = cat.optJSONObject("applied");
        String[] prev = new String[3];
        String[] K3 = {"frame", "avatar", "effect"};
        for (int i = 0; i < 3; i++) {
            String v = applied == null ? null : applied.optString(K3[i], null);
            prev[i] = v == null || "null".equals(v) ? null : v;
        }
        String title, sub;
        JSONObject item = null, bundle;
        if ("item".equals(type)) {
            item = find(items, id);
            if (item == null) return;
            prev[idx(item.optString("kind"))] = item.optString("id");
            bundle = find(cat.optJSONArray("bundles"), item.optString("pack"));
            title = item.optString("name");
            sub = item.optString("packName") + " · " + Deco.kindLabel(item.optString("kind"), false);
        } else {
            bundle = find(cat.optJSONArray("bundles"), id);
            if (bundle == null) return;
            JSONArray ids = bundle.optJSONArray("items");
            StringBuilder b = new StringBuilder();
            for (int i = 0; ids != null && i < ids.length(); i++) {
                JSONObject it = find(items, ids.optString(i));
                if (it != null) prev[idx(it.optString("kind"))] = it.optString("id");
                b.append(i > 0 ? " · " : "").append(it == null ? ids.optString(i) : it.optString("name"));
            }
            title = bundle.optString("name") + " bundle";
            sub = b.toString();
        }
        sheet = new Ov.Sheet(c);
        DecoCard card = new DecoCard(c).set(Deco.selfUser(), prev[0], prev[1], prev[2], false);
        sheet.body.addView(card, W.lp(W.MP, W.WC));
        LinearLayout tb = W.col(c);
        tb.addView(K.text(c, title, K.LG, 600, T.text));
        tb.addView(K.text(c, sub, K.XS, 400, T.textMuted));
        sheet.body.addView(tb, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        acts = W.col(c);
        sheet.body.addView(acts, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        JSONObject fi = item, fb = bundle;
        sheet.onClose = () -> closed = true;
        renderActs(fi, fb);
        sheet.show();
    }

    static int idx(String k) {
        return "frame".equals(k) ? 0 : "avatar".equals(k) ? 1 : 2;
    }

    void add(View v) {
        LinearLayout.LayoutParams lp = W.lp(W.MP, W.WC);
        if (acts.getChildCount() > 0) lp.topMargin = T.dp(8);
        acts.addView(v, lp);
    }

    void renderActs(JSONObject item, JSONObject bundle) {
        acts.removeAllViews();
        JSONObject owned = cat.optJSONObject("owned"), applied = cat.optJSONObject("applied");
        if (wait != null) {
            LinearLayout w = W.row(c);
            w.addView(K.spin(c, 18));
            TextView t = K.text(c, "Waiting for payment. Complete it in the OxaPay tab, this updates automatically.", K.SM, 400, T.textSecondary);
            w.addView(t, W.lpm(0, W.WC, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
            add(w);
            add(K.btn(c, "Reopen payment page", "secondary", v -> K.openUrl(wait.optString("paymentUrl"))));
        } else {
            if (item != null) {
                JSONObject o = owned == null ? null : owned.optJSONObject(item.optString("id"));
                if (o != null) {
                    boolean on = applied != null && item.optString("id").equals(applied.optString(item.optString("kind")));
                    String lab = (on ? "Applied · " : "Apply · ") + Deco.serialLabel(o.optInt("serial"));
                    add(K.btn(c, lab, Official.bought(owned, item.optString("id")) ? "primary" : "secondary", v -> Deco.apply(item.optString("kind"), item.optString("id"), ok -> {
                        if (ok) {
                            Ov.toast("Decoration applied");
                            sheet.close();
                        } else Ov.toast("Could not apply", true);
                    })));
                }
                if (!Official.bought(owned, item.optString("id"))) {
                    TextView b = K.btn(c, "Buy " + item.optString("name") + " · " + K.price(item.optDouble("price")), "primary", v -> buy("item", item.optString("id"), item, bundle));
                    K.enable(b, !busy);
                    add(b);
                }
            }
            if (bundle != null) {
                double bp = Official.bundlePrice(cat, bundle);
                boolean all = true;
                JSONArray ids = bundle.optJSONArray("items");
                for (int i = 0; ids != null && i < ids.length(); i++) if (!Official.bought(owned, ids.optString(i))) all = false;
                if (!all && bp > 0) {
                    TextView b = K.btn(c, "Get the " + bundle.optString("name") + " bundle · " + K.price(bp), item != null ? "secondary" : "primary", v -> buy("bundle", bundle.optString("id"), item, bundle));
                    K.enable(b, !busy);
                    add(b);
                } else if (item == null) {
                    TextView o = K.text(c, "You own this whole bundle", K.XS, 600, T.accent);
                    add(o);
                }
            }
        }
        add(K.btn(c, wait != null ? "Close" : "Cancel", "ghost", v -> sheet.close()));
    }

    void buy(String kind, String bid, JSONObject item, JSONObject bundle) {
        if (busy) return;
        busy = true;
        renderActs(item, bundle);
        App.bg(() -> {
            Http.Resp r = Http.post("/shop/deco/purchase", App.jo(kind, bid));
            App.ui(() -> {
                busy = false;
                if (!r.ok()) {
                    Ov.toast(errText(r.error()), true);
                    renderActs(item, bundle);
                    return;
                }
                JSONObject o = r.obj();
                K.openUrl(o.optString("paymentUrl"));
                wait = o;
                renderActs(item, bundle);
                poll(o.optString("orderId"), item, bundle);
            });
        });
    }

    void poll(String orderId, JSONObject item, JSONObject bundle) {
        App.H.postDelayed(() -> {
            if (closed || wait == null || !orderId.equals(wait.optString("orderId"))) return;
            App.bg(() -> {
                Http.Resp r = Http.get("/shop/deco/order/" + android.net.Uri.encode(orderId));
                App.ui(() -> {
                    if (closed) return;
                    String st = r.ok() ? r.obj().optString("status") : "";
                    if ("paid".equals(st)) {
                        sheet.close();
                        Deco.catalog(true, null);
                        Deco.showGrant(r.obj().optJSONArray("items"), orderId);
                    } else if ("expired".equals(st) || "underpaid".equals(st)) {
                        wait = null;
                        renderActs(item, bundle);
                        Ov.toast("expired".equals(st) ? "The invoice expired." : "The payment was lower than the price.", true);
                    } else poll(orderId, item, bundle);
                });
            });
        }, 5000);
    }
}
