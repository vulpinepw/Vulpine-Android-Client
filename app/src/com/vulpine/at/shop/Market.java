package com.vulpine.at.shop;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class Market extends ShopTab.Part {
    static final String[][] SORTS = {{"newest", "Newest"}, {"price_asc", "Price low"}, {"price_desc", "Price high"}, {"handle", "A-Z"}};
    Context c;
    List<JSONObject> data;
    int total;
    String q = "", sort = "newest";
    boolean busy;
    int gen;
    LinearLayout content;
    final List<TextView> pills = new ArrayList<>();
    final Runnable deb = () -> load(true);

    @Override
    public void build(Context c, LinearLayout body) {
        this.c = c;
        W.Field f = new W.Field(c, "Search handles", null);
        f.input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        f.input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                q = e.toString();
                body.removeCallbacks(deb);
                body.postDelayed(deb, 350);
            }
        });
        body.addView(f, W.lp(W.MP, W.WC));
        LinearLayout pr = W.row(c);
        for (String[] s : SORTS) {
            TextView p = Official.pill(c, s[1], sort.equals(s[0]));
            p.setOnClickListener(v -> {
                if (sort.equals(s[0])) return;
                sort = s[0];
                for (int i = 0; i < pills.size(); i++) K.variant(pills.get(i), SORTS[i][0].equals(sort) ? "primary" : "ghost");
                data = null;
                total = 0;
                render();
                busy = false;
                load(true);
            });
            pills.add(p);
            LinearLayout.LayoutParams lp = W.lp(W.WC, T.dp(30));
            if (pr.getChildCount() > 0) lp.leftMargin = T.dp(4);
            pr.addView(p, lp);
        }
        body.addView(pr, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        content = W.col(c);
        body.addView(content, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        render();
        load(true);
    }

    void load(boolean reset) {
        if (busy && !reset) return;
        busy = true;
        int g = ++gen;
        int off = reset || data == null ? 0 : data.size();
        String path = "/shop/market?limit=24&offset=" + off + "&sort=" + sort + (q.isEmpty() ? "" : "&q=" + android.net.Uri.encode(q));
        App.bg(() -> {
            Http.Resp r = Http.get(path);
            App.ui(() -> {
                if (g != gen) return;
                busy = false;
                if (!r.ok()) return;
                JSONObject o = r.obj();
                JSONArray items = o.optJSONArray("items");
                total = o.optInt("total");
                List<JSONObject> got = new ArrayList<>();
                for (int i = 0; items != null && i < items.length(); i++) got.add(items.optJSONObject(i));
                if (reset || data == null) data = got;
                else {
                    Set<String> have = new HashSet<>();
                    for (JSONObject x : data) have.add(x.optString("handle"));
                    for (JSONObject x : got) if (!have.contains(x.optString("handle"))) data.add(x);
                }
                render();
            });
        });
    }

    void render() {
        content.removeAllViews();
        if (data == null) {
            content.addView(K.spinCenter(c, 20, 40), W.lp(W.MP, W.WC));
            return;
        }
        if (data.isEmpty()) {
            boolean qq = !q.isEmpty();
            content.addView(K.empty(c, qq ? "No handles match your search." : "Nothing is for sale right now.", qq ? "Try a different search." : "List one of yours from the Wallet tab."), W.lp(W.MP, W.WC));
            return;
        }
        for (int i = 0; i < data.size(); i++) {
            View r = row(data.get(i));
            content.addView(r, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 8, 0, 0));
            K.in(r, Math.min(i * 30, 270));
        }
        if (total > data.size()) {
            TextView more = K.btn(c, "Load more (" + data.size() + " of " + total + ")", "ghost", 32, K.XS, v -> load(false));
            more.setPadding(T.dp(16), 0, T.dp(16), 0);
            LinearLayout w = W.row(c);
            w.setGravity(Gravity.CENTER);
            w.setPadding(0, T.dp(12), 0, T.dp(12));
            w.addView(more, W.lp(W.WC, T.dp(32)));
            content.addView(w, W.lp(W.MP, W.WC));
        }
    }

    View row(JSONObject it) {
        LinearLayout r = W.row(c);
        r.setBackground(W.round(T.surface, 12));
        r.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        JSONObject seller = it.optJSONObject("seller");
        if (seller == null) seller = new JSONObject();
        AvatarView av = new AvatarView(c);
        av.setLayoutParams(W.lp(T.dp(32), T.dp(32)));
        av.textSize(13);
        String au = seller.optString("avatarUrl", "");
        av.set(it.optString("handle"), seller.optString("avatarColor", "").isEmpty() ? null : seller.optString("avatarColor"), au);
        r.addView(av);
        LinearLayout tx = W.col(c);
        TextView h = K.mono(c, "@" + it.optString("handle"), K.SM, 600, T.text);
        W.ellipsize(h);
        tx.addView(h);
        TextView s = K.text(c, it.optBoolean("mine") ? "Your listing" : "by @" + seller.optString("handle"), K.XS, 400, T.textMuted);
        W.ellipsize(s);
        tx.addView(s);
        r.addView(tx, W.lpm(0, W.WC, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        Object p = it.opt("priceUsd");
        boolean offer = p == null || p == JSONObject.NULL;
        TextView pr = K.text(c, K.price(offer ? null : p), K.SM, 600, offer ? T.textMuted : T.text);
        r.addView(pr, W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        if (!it.optBoolean("mine")) {
            String handle = it.optString("handle");
            TextView b = K.btn(c, "Message seller", "secondary", 30, K.XS, v -> contact(handle));
            r.addView(b, W.lpm(W.WC, T.dp(30), 12, 0, 0, 0));
        }
        return r;
    }

    void contact(String handle) {
        Ov.toast("Opening chat…");
        App.bg(() -> {
            Http.Resp r = Http.post("/shop/contact", App.jo("handle", handle));
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast(K.shopErr(r.error()), true);
                    return;
                }
                String cid = r.obj().optString("conversationId", null);
                if (cid != null) Home.openChat(cid);
            });
        });
    }
}
