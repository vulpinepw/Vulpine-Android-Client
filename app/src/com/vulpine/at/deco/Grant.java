package com.vulpine.at.deco;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.shop.K;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

final class Grant {
    static void show(JSONArray items0, String orderId) {
        List<JSONObject> items = new ArrayList<>();
        if (items0 != null) for (int i = 0; i < items0.length(); i++) {
            JSONObject x = items0.optJSONObject(i);
            if (x != null && Deco.validId(x.optString("item"))) items.add(x);
        }
        if (items.isEmpty()) return;
        if (orderId != null) {
            if (Deco.shownOrders.contains(orderId)) return;
            Deco.shownOrders.add(orderId);
        }
        Deco.catalog(true, c -> build(items));
    }

    static void build(List<JSONObject> items) {
        FrameLayout h = Ov.host();
        if (h == null) return;
        Context c = h.getContext();
        FrameLayout ov = new FrameLayout(c);
        ov.setBackgroundColor(0x73000000);
        ov.setClickable(true);
        ov.setPadding(T.dp(24), T.dp(24), T.dp(24), T.dp(24));
        LinearLayout card = W.col(c);
        card.setBackground(W.round(T.surface, 22));
        card.setPadding(T.dp(22), T.dp(26), T.dp(22), T.dp(20));
        card.setElevation(T.dpf(24));
        card.setClickable(true);
        Map<String, String> opts = new LinkedHashMap<>();
        for (JSONObject x : items) opts.put(Deco.itemInfo(x.optString("item")).optString("kind"), x.optString("item"));
        DecoCard prev = new DecoCard(c).set(Deco.selfUser(), opts.get("frame"), opts.get("avatar"), opts.get("effect"), false);
        card.addView(prev, W.lp(W.MP, W.WC));
        TextView title = K.text(c, "Added to your decorations", K.LG, 600, T.text);
        title.setGravity(Gravity.CENTER);
        card.addView(title, W.lpm(W.MP, W.WC, 0, 14, 0, 0));
        LinearLayout list = W.col(c);
        for (int i = 0; i < items.size(); i++) {
            JSONObject x = items.get(i);
            JSONObject info = Deco.itemInfo(x.optString("item"));
            LinearLayout row = W.row(c);
            row.setBackground(W.round(T.surface2, 12));
            row.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
            TextView n = K.text(c, info.optString("name"), K.SM, 600, T.text);
            row.addView(n, W.lp(0, W.WC, 1));
            TextView k = K.text(c, Deco.kindLabel(info.optString("kind"), false), K.XS, 400, T.textMuted);
            row.addView(k, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
            TextView s = K.text(c, Deco.serialLabel(x.optInt("serial")), K.SM, 600, T.accent);
            row.addView(s, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
            list.addView(row, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 6, 0, 0));
        }
        card.addView(list, W.lpm(W.MP, W.WC, 0, 14, 0, 0));
        LinearLayout acts = W.row(c);
        Runnable[] close = new Runnable[1];
        TextView later = gbtn(c, "Keep for later", false);
        TextView now = gbtn(c, "Apply now", true);
        later.setOnClickListener(v -> close[0].run());
        now.setOnClickListener(v -> {
            K.enable(now, false);
            List<String> kinds = new ArrayList<>(opts.keySet());
            applySeq(kinds, opts, 0, ok -> {
                if (ok) {
                    close[0].run();
                    Ov.toast("Decoration applied");
                } else {
                    K.enable(now, true);
                    Ov.toast("Could not apply", true);
                }
            });
        });
        acts.addView(later, W.lp(0, T.dp(42), 1));
        LinearLayout.LayoutParams np = W.lp(0, T.dp(42), 1);
        np.leftMargin = T.dp(10);
        acts.addView(now, np);
        card.addView(acts, W.lpm(W.MP, W.WC, 0, 14, 0, 0));
        FrameLayout.LayoutParams cp = W.flp(Math.min(T.dp(400), h.getWidth() - T.dp(48)), W.WC, Gravity.CENTER);
        ov.addView(card, cp);
        boolean[] closed = {false};
        close[0] = () -> {
            if (closed[0]) return;
            closed[0] = true;
            Ov.remove(ov);
            ov.animate().alpha(0).setDuration(200).setInterpolator(T.EASE_IN).withEndAction(() -> h.removeView(ov)).start();
            card.animate().scaleX(.96f).scaleY(.96f).setDuration(200).start();
        };
        ov.setOnClickListener(v -> close[0].run());
        h.addView(ov, new FrameLayout.LayoutParams(W.MP, W.MP));
        ov.setAlpha(0);
        ov.animate().alpha(1).setDuration(240).setInterpolator(T.EASE).start();
        card.setScaleX(.94f);
        card.setScaleY(.94f);
        card.setTranslationY(T.dp(12));
        card.animate().scaleX(1).scaleY(1).translationY(0).setDuration(320).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        Ov.push(ov, close[0]);
    }

    static TextView gbtn(Context c, String label, boolean primary) {
        TextView t = W.text(c, label, K.SM, 600, primary ? T.accentFg : T.text);
        t.setGravity(Gravity.CENTER);
        t.setBackground(W.ripple(primary ? W.accentBg(999) : W.round(T.surface2, 999), 999));
        K.pressBtn(t);
        return t;
    }

    static void applySeq(List<String> kinds, Map<String, String> opts, int i, Deco.Cb<Boolean> done) {
        if (i >= kinds.size()) {
            done.on(true);
            return;
        }
        String k = kinds.get(i);
        Deco.apply(k, opts.get(k), ok -> {
            if (!ok) done.on(false);
            else applySeq(kinds, opts, i + 1, done);
        });
    }
}
