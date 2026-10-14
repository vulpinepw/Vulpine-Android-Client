package com.vulpine.at.deco;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.shop.K;
import com.vulpine.at.shop.Search;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class Section extends Page {
    LinearLayout box;
    String q = "";
    String busy;
    final Deco.Cb<JSONObject> catL = c -> render();

    public Section() {
        super("Decorations");
    }

    @Override
    protected void content(LinearLayout body) {
        box = W.col(a);
        body.addView(box, W.lp(W.MP, W.WC));
        Deco.onCatalog(catL);
        if (Deco.cat() != null) render();
        Deco.catalog(false, c -> render());
    }

    @Override
    public void onDestroy() {
        Deco.offCatalog(catL);
    }

    Search search;

    void render() {
        JSONObject cat = Deco.cat();
        if (cat == null || box == null) return;
        Context c = a;
        JSONObject owned = cat.optJSONObject("owned"), applied = cat.optJSONObject("applied");
        JSONArray all = cat.optJSONArray("items");
        List<JSONObject> mine = new ArrayList<>();
        if (all != null) for (int i = 0; i < all.length(); i++) {
            JSONObject it = all.optJSONObject(i);
            if (it != null && owned != null && owned.has(it.optString("id"))) mine.add(it);
        }
        boolean focus = search != null && search.input.hasFocus();
        box.removeAllViews();
        if (!mine.isEmpty()) {
            if (search == null) {
                search = new Search(c, "Search decorations");
                search.input.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a1, int b, int d) {
                    }

                    public void onTextChanged(CharSequence s, int a1, int b, int d) {
                    }

                    public void afterTextChanged(Editable e) {
                        q = e.toString();
                        render();
                    }
                });
            }
            if (search.getParent() != null) ((LinearLayout) search.getParent()).removeView(search);
            box.addView(search, W.lp(W.MP, T.dp(36)));
            if (focus) search.input.requestFocus();
        } else {
            LinearLayout e = W.col(c);
            e.addView(K.text(c, "You don't own any decorations yet.", K.SM, 400, T.textMuted));
            TextView b = K.btn(c, "Browse decorations", "secondary", v -> {
                a.pop();
                Deco.openShop();
            });
            e.addView(b, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
            box.addView(e, W.lp(W.MP, W.WC));
            return;
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        int groups = 0;
        for (String k : new String[]{"frame", "avatar", "effect"}) {
            List<JSONObject> list = new ArrayList<>();
            for (JSONObject it : mine) if (k.equals(it.optString("kind")) && (needle.isEmpty() || (it.optString("name") + " " + it.optString("packName") + " " + it.optString("kind")).toLowerCase(Locale.ROOT).contains(needle))) list.add(it);
            if (list.isEmpty()) continue;
            groups++;
            TextView gl = K.text(c, Deco.kindLabel(k, true).toUpperCase(Locale.ROOT), K.XS, 600, T.textMuted);
            gl.setLetterSpacing(.06f);
            box.addView(gl, W.lpm(W.MP, W.WC, 0, 12, 0, 8));
            LinearLayout row = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % 2 == 0) {
                    row = W.row(c);
                    row.setGravity(Gravity.TOP);
                    box.addView(row, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 8, 0, 0));
                }
                JSONObject it = list.get(i);
                View tile = tile(c, it, owned.optJSONObject(it.optString("id")), applied != null && it.optString("id").equals(applied.optString(k)));
                LinearLayout.LayoutParams lp = W.lp(0, W.WC, 1);
                if (i % 2 == 1) lp.leftMargin = T.dp(8);
                row.addView(tile, lp);
            }
            if (list.size() % 2 == 1) {
                View sp = new View(c);
                LinearLayout.LayoutParams lp = W.lp(0, 1, 1);
                lp.leftMargin = T.dp(8);
                row.addView(sp, lp);
            }
        }
        if (groups == 0) box.addView(K.text(c, "No decorations found.", K.SM, 400, T.textMuted), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        TextView more = K.btn(c, "Get more decorations", "ghost", v -> {
            a.pop();
            Deco.openShop();
        });
        box.addView(more, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
    }

    View tile(Context c, JSONObject it, JSONObject own, boolean on) {
        LinearLayout t = W.col(c);
        t.setBackground(W.ripple(W.round(on ? T.accentSoft : T.surface, 14), 14));
        t.setPadding(T.dp(10), T.dp(10), T.dp(10), T.dp(8));
        String id = it.optString("id"), k = it.optString("kind");
        DecoCard card = new DecoCard(c).set(null, "frame".equals(k) ? id : null, "avatar".equals(k) ? id : null, "effect".equals(k) ? id : null, true);
        t.addView(card, W.lp(W.MP, W.WC));
        LinearLayout nm = W.row(c);
        TextView n = K.text(c, it.optString("name"), K.XS, 600, T.text);
        nm.addView(n, W.lp(0, W.WC, 1));
        TextView s = K.text(c, Deco.serialLabel(own == null ? 0 : own.optInt("serial")), K.XS, 600, T.accent);
        nm.addView(s, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
        t.addView(nm, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        t.setContentDescription(it.optString("name"));
        t.setOnClickListener(v -> {
            if (busy != null) return;
            busy = id;
            Deco.apply(k, on ? null : id, ok -> {
                busy = null;
                if (!ok) Ov.toast("Could not apply", true);
            });
        });
        K.pressBtn(t);
        return t;
    }
}
