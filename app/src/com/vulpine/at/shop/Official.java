package com.vulpine.at.shop;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.deco.Deco;
import com.vulpine.at.deco.DecoCard;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class Official extends ShopTab.Part {
    Context c;
    JSONObject cat;
    boolean err;
    String q = "", filter = "all";
    int limit = 8;
    LinearLayout box, sections;
    Search search;
    final List<TextView> pills = new ArrayList<>();
    View more;
    final Map<String, View> cells = new HashMap<>();
    int seq;
    final Deco.Cb<JSONObject> catL = x -> {
        if (x != null) {
            cat = x;
            cells.clear();
            render();
        }
    };
    static final String[][] FILTERS = {{"all", "All"}, {"bundles", "Bundles"}, {"frame", "Frames"}, {"avatar", "Avatars"}, {"effect", "Effects"}};

    @Override
    public void build(Context c, LinearLayout body) {
        this.c = c;
        box = W.col(c);
        body.addView(box, W.lp(W.MP, W.WC));
        Deco.onCatalog(catL);
        box.addView(K.spinCenter(c, 20, 40), W.lp(W.MP, W.WC));
        Deco.catalog(true, x -> {
            if (x == null) err = true;
            else cat = x;
            render();
        });
    }

    @Override
    public void onHide() {
        Deco.offCatalog(catL);
    }

    @Override
    public void onShow() {
        Deco.onCatalog(catL);
    }

    void top() {
        LinearLayout t = W.col(c);
        search = new Search(c, "Search decorations");
        search.input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                q = e.toString();
                limit = 8;
                renderSections();
            }
        });
        t.addView(search, W.lp(W.MP, T.dp(36)));
        LinearLayout pr = W.row(c);
        for (String[] f : FILTERS) {
            TextView p = pill(c, f[1], filter.equals(f[0]));
            p.setOnClickListener(v -> {
                if (filter.equals(f[0])) return;
                filter = f[0];
                limit = 8;
                for (int i = 0; i < pills.size(); i++) K.variant(pills.get(i), FILTERS[i][0].equals(filter) ? "primary" : "ghost");
                renderSections();
            });
            pills.add(p);
            LinearLayout.LayoutParams lp = W.lp(W.WC, T.dp(30));
            if (pr.getChildCount() > 0) lp.leftMargin = T.dp(4);
            pr.addView(p, lp);
        }
        t.addView(pr, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        box.addView(t, W.lp(W.MP, W.WC));
    }

    static TextView pill(Context c, String label, boolean on) {
        TextView p = K.btn(c, label, on ? "primary" : "ghost", 30, K.XS, null);
        p.setPadding(T.dp(8), 0, T.dp(8), 0);
        return p;
    }

    void render() {
        if (box == null) return;
        if (cat == null) {
            if (err) {
                box.removeAllViews();
                LinearLayout card = K.card(c);
                card.addView(K.empty(c, "Could not load decorations", "Check your connection and try again."));
                box.addView(card, W.lp(W.MP, W.WC));
                K.in(card, 0);
            }
            return;
        }
        if (sections == null) {
            box.removeAllViews();
            top();
            sections = W.col(c);
            box.addView(sections, W.lp(W.MP, W.WC));
        }
        renderSections();
    }

    static String lc(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    void renderSections() {
        if (sections == null || cat == null) return;
        sections.removeAllViews();
        String needle = lc(q.trim());
        JSONArray itemsA = cat.optJSONArray("items"), bundlesA = cat.optJSONArray("bundles");
        JSONObject owned = cat.optJSONObject("owned");
        List<JSONObject> items = new ArrayList<>(), bundles = new ArrayList<>();
        Map<String, JSONObject> byId = new HashMap<>();
        for (int i = 0; i < itemsA.length(); i++) {
            JSONObject it = itemsA.optJSONObject(i);
            byId.put(it.optString("id"), it);
            if (!"all".equals(filter) && !"bundles".equals(filter) && !filter.equals(it.optString("kind"))) continue;
            if (!needle.isEmpty() && !lc(it.optString("name") + " " + it.optString("packName") + " " + it.optString("kind")).contains(needle)) continue;
            items.add(it);
        }
        for (int i = 0; i < bundlesA.length(); i++) {
            JSONObject b = bundlesA.optJSONObject(i);
            if (!"all".equals(filter) && !"bundles".equals(filter)) continue;
            if (!needle.isEmpty()) {
                StringBuilder names = new StringBuilder();
                JSONArray ids = b.optJSONArray("items");
                for (int k = 0; ids != null && k < ids.length(); k++) {
                    JSONObject it = byId.get(ids.optString(k));
                    names.append(k > 0 ? " " : "").append(it == null ? "" : it.optString("name"));
                }
                if (!lc(b.optString("name") + " bundle " + names).contains(needle)) continue;
            }
            bundles.add(b);
        }
        int[] left = {limit};
        int[] total = {0};
        List<JSONObject> sb = take(bundles, left, total);
        if (!sb.isEmpty()) section("Bundles", sb, true, byId, owned);
        if (!"bundles".equals(filter)) {
            for (String k : new String[]{"frame", "avatar", "effect"}) {
                List<JSONObject> l = new ArrayList<>();
                for (JSONObject it : items) if (k.equals(it.optString("kind"))) l.add(it);
                List<JSONObject> s = take(l, left, total);
                if (!s.isEmpty()) section(Deco.kindLabel(k, true), s, false, byId, owned);
            }
        }
        more = null;
        if (total[0] > limit) {
            more = K.spinCenter(c, 18, 16);
            sections.addView(more, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
            sections.post(this::onScroll);
        }
        if (sections.getChildCount() == 0) sections.addView(K.empty(c, "No decorations found", "Try another search."), W.lpm(W.MP, W.WC, 0, 16, 0, 0));
    }

    static List<JSONObject> take(List<JSONObject> l, int[] left, int[] total) {
        total[0] += l.size();
        List<JSONObject> out = new ArrayList<>(l.subList(0, Math.max(0, Math.min(l.size(), left[0]))));
        left[0] -= out.size();
        return out;
    }

    void section(String title, List<JSONObject> list, boolean bundle, Map<String, JSONObject> byId, JSONObject owned) {
        TextView h = K.text(c, title, K.SM, 600, T.textSecondary);
        sections.addView(h, W.lpm(W.MP, W.WC, 0, 24, 0, 0));
        int width = sv.getWidth() > 0 ? sv.getWidth() - T.dp(32) : c.getResources().getDisplayMetrics().widthPixels - T.dp(32);
        int gap = T.dp(12);
        int cols = Math.max(1, (width + gap) / (T.dp(150) + gap));
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                row = W.row(c);
                row.setGravity(Gravity.TOP);
                sections.addView(row, W.lpm(W.MP, W.WC, 0, i == 0 ? 16 : 12, 0, 0));
            }
            JSONObject o = list.get(i);
            String key = (bundle ? "b-" : "") + o.optString("id");
            View cell = cells.get(key);
            boolean fresh = cell == null;
            if (cell == null) {
                cell = bundle ? bundleCell(o, byId, owned) : itemCell(o, owned);
                cells.put(key, cell);
            }
            if (cell.getParent() != null) ((ViewGroup) cell.getParent()).removeView(cell);
            LinearLayout.LayoutParams lp = W.lp(0, W.WC, 1);
            if (i % cols > 0) lp.leftMargin = gap;
            row.addView(cell, lp);
            if (fresh) K.in(cell, Math.min(i * 25, 300));
        }
        int rem = list.size() % cols;
        if (rem > 0) for (int k = rem; k < cols; k++) {
            View sp = new View(c);
            LinearLayout.LayoutParams lp = W.lp(0, 1, 1);
            lp.leftMargin = gap;
            row.addView(sp, lp);
        }
    }

    static boolean bought(JSONObject owned, String id) {
        JSONObject o = owned == null ? null : owned.optJSONObject(id);
        return o != null && o.optInt("serial") > 0;
    }

    public static double bundlePrice(JSONObject cat, JSONObject b) {
        JSONObject owned = cat.optJSONObject("owned");
        JSONArray items = cat.optJSONArray("items");
        double val = 0;
        JSONArray ids = b.optJSONArray("items");
        for (int i = 0; ids != null && i < ids.length(); i++) {
            String id = ids.optString(i);
            if (!bought(owned, id)) continue;
            for (int k = 0; items != null && k < items.length(); k++) if (id.equals(items.optJSONObject(k).optString("id"))) val += items.optJSONObject(k).optDouble("price");
        }
        return Math.max(0, Math.round((b.optDouble("price") - val) * 100) / 100.0);
    }

    LinearLayout cellBase(String[] kinds) {
        LinearLayout cell = W.col(c);
        cell.setBackground(W.ripple(W.round(T.surface, 18), 18));
        cell.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(14));
        DecoCard card = new DecoCard(c).set(null, kinds[0], kinds[1], kinds[2], true).thumb(true);
        cell.addView(card, W.lp(W.MP, W.WC));
        return cell;
    }

    View meta(String name, String sub, String right, boolean owned) {
        LinearLayout m = W.row(c);
        m.setGravity(Gravity.BOTTOM);
        m.setPadding(T.dp(16), 0, T.dp(16), 0);
        LinearLayout l = W.col(c);
        l.addView(K.text(c, name, K.SM, 600, T.text));
        l.addView(K.text(c, sub, K.XS, 400, T.textMuted));
        m.addView(l, W.lp(0, W.WC, 1));
        TextView r = owned ? K.text(c, right, K.XS, 600, T.accent) : K.text(c, right, K.SM, 600, T.text);
        r.setSingleLine(true);
        r.setFontFeatureSettings("tnum");
        m.addView(r, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        return m;
    }

    View itemCell(JSONObject it, JSONObject owned) {
        String id = it.optString("id"), k = it.optString("kind");
        LinearLayout cell = cellBase(new String[]{"frame".equals(k) ? id : null, "avatar".equals(k) ? id : null, "effect".equals(k) ? id : null});
        JSONObject minted = cat.optJSONObject("minted");
        int sold = minted == null ? 0 : minted.optInt(id);
        String sub = it.optString("packName") + " · " + Deco.kindLabel(k, false) + (sold > 0 ? " · " + sold + " sold" : "");
        boolean o = bought(owned, id);
        cell.addView(meta(it.optString("name"), sub, o ? "Owned #" + owned.optJSONObject(id).optInt("serial") : K.price(it.optDouble("price")), o), W.lpm(W.MP, W.WC, 0, 10, 0, 0));
        cell.setContentDescription(it.optString("name"));
        cell.setOnClickListener(v -> BuySheet.open(shop, cat, "item", id));
        return cell;
    }

    View bundleCell(JSONObject b, Map<String, JSONObject> byId, JSONObject owned) {
        String[] kinds = new String[3];
        JSONArray ids = b.optJSONArray("items");
        boolean all = true;
        for (int i = 0; ids != null && i < ids.length(); i++) {
            JSONObject it = byId.get(ids.optString(i));
            if (it == null) continue;
            int k = "frame".equals(it.optString("kind")) ? 0 : "avatar".equals(it.optString("kind")) ? 1 : 2;
            kinds[k] = it.optString("id");
            if (!bought(owned, it.optString("id"))) all = false;
        }
        LinearLayout cell = cellBase(kinds);
        double bp = bundlePrice(cat, b);
        int n = ids == null ? 0 : ids.length();
        String sub = n + " decorations" + (bp < b.optDouble("price") && !all ? " · was " + K.price(b.optDouble("price")) : "");
        cell.addView(meta(b.optString("name") + " bundle", sub, all ? "Owned" : K.price(bp), all), W.lpm(W.MP, W.WC, 0, 10, 0, 0));
        cell.setContentDescription(b.optString("name") + " bundle");
        String bid = b.optString("id");
        cell.setOnClickListener(v -> BuySheet.open(shop, cat, "bundle", bid));
        return cell;
    }

    @Override
    public void onScroll() {
        if (more == null || sv == null) return;
        int[] loc = new int[2];
        more.getLocationInWindow(loc);
        int[] sl = new int[2];
        sv.getLocationInWindow(sl);
        if (loc[1] - (sl[1] + sv.getHeight()) < T.dp(600)) {
            more = null;
            limit += 8;
            sections.post(this::renderSections);
        }
    }
}
