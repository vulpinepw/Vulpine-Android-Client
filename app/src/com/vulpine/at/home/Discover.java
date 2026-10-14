package com.vulpine.at.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.groups.Join;
import com.vulpine.at.people.U;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Discover extends Page {
    static int lastPage = 1;
    static String lastQ = "";
    int page = 1;
    String q = "";
    JSONObject data;
    String error;
    boolean loading;
    String joining;
    LinearLayout list;
    LinearLayout foot;
    U.Field search;
    int seq;

    Discover() {
        super(null);
    }

    public static void open(MainActivity a) {
        a.push(new Discover());
    }

    protected void content(LinearLayout body) {
        Context c = a;
        LinearLayout hd = W.col(c);
        LinearLayout tr = W.row(c);
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Ic.get("discover", 22, T.accent));
        tr.addView(ic, W.lp(T.dp(22), T.dp(22)));
        TextView tt = U.txt(c, "Discover Groups", 24, 700, T.text);
        T.lh(tt, 36);
        tr.addView(tt, W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        hd.addView(tr);
        TextView sub = U.muted(c, "Browse and join public groups", 13);
        T.lh(sub, 20);
        hd.addView(sub, W.lp(W.WC, W.WC));
        bar.addView(hd, W.lpm(0, W.WC, 12, 2, 0, 0));
        ((LinearLayout.LayoutParams) hd.getLayoutParams()).weight = 1;
        bar.setGravity(Gravity.TOP);
        bar.setClipChildren(false);
        LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) bar.getLayoutParams();
        bp.height = W.WC;
        bp.bottomMargin = T.dp(20);
        FrameLayout sw = new FrameLayout(c);
        search = new U.Field(c, "Search groups…", null, 64, false);
        search.input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.input.setPadding(T.dp(2), T.dp(8), T.dp(26), T.dp(8));
        sw.addView(search, new FrameLayout.LayoutParams(W.MP, W.WC));
        TextView clr = U.txt(c, "✕", 11, 400, T.textMuted);
        clr.setGravity(Gravity.CENTER);
        clr.setContentDescription("Clear search");
        clr.setVisibility(View.GONE);
        FrameLayout.LayoutParams cp = W.flp(T.dp(22), T.dp(22), Gravity.TOP | Gravity.END);
        cp.topMargin = T.dp(27);
        sw.addView(clr, cp);
        android.view.ViewGroup outer = (android.view.ViewGroup) bar.getParent();
        outer.addView(sw, outer.indexOfChild(bar) + 1, W.lpm(W.MP, W.WC, 16, 0, 16, 8));
        clr.setOnClickListener(v -> {
            search.input.setText("");
            q = "";
            W.showKb(search.input);
            load(1);
        });
        final Runnable[] deb = new Runnable[1];
        search.onText(() -> {
            clr.setVisibility(search.value().length() > 0 ? View.VISIBLE : View.GONE);
            if (deb[0] != null) App.H.removeCallbacks(deb[0]);
            deb[0] = () -> {
                q = search.value().trim();
                load(1);
            };
            App.H.postDelayed(deb[0], 250);
        });
        search.input.setOnEditorActionListener((v, act, e) -> {
            if (deb[0] != null) App.H.removeCallbacks(deb[0]);
            q = search.value().trim();
            load(1);
            W.hideKb(v);
            return true;
        });
        list = W.col(c);
        body.addView(list, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        foot = W.row(c);
        foot.setGravity(Gravity.CENTER);
        foot.setBackgroundColor(T.bg);
        foot.setPadding(T.dp(24), T.dp(16), T.dp(24), T.dp(16));
        frame.addView(foot, W.flp(W.MP, W.WC, Gravity.BOTTOM));
        q = lastQ;
        if (!q.isEmpty()) search.set(q);
        load(lastPage);
    }

    int insB, insIme;
    boolean footHooked;

    public void onInsets(int top, int bottom, int ime) {
        super.onInsets(top, bottom, ime);
        insB = bottom;
        insIme = ime;
        int fb = Math.max(bottom, T.dp(16)) + T.dp(20);
        foot.setPadding(T.dp(24), T.dp(16), T.dp(24), T.dp(16) + fb);
        if (!footHooked) {
            footHooked = true;
            foot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (b - t != ob - ot) foot.post(this::padList);
            });
        }
        padList();
    }

    void padList() {
        if (sv == null || foot == null) return;
        int def = T.dp(20) + Math.max(insB, T.dp(16)) + insIme;
        int fh = foot.getVisibility() == View.VISIBLE ? foot.getHeight() : 0;
        if (foot.getVisibility() == View.VISIBLE && fh == 0) {
            foot.post(this::padList);
            return;
        }
        int want = Math.max(def, fh > 0 ? fh + T.dp(16) + insIme : 0);
        if (sv.getPaddingBottom() != want) sv.setPadding(0, 0, 0, want);
    }

    void load(int n) {
        page = Math.max(1, n);
        lastPage = page;
        lastQ = q;
        loading = true;
        error = null;
        render();
        int my = ++seq;
        String qq = q;
        int pg = page;
        App.bg(() -> {
            String enc;
            try {
                enc = java.net.URLEncoder.encode(qq, "UTF-8").replace("+", "%20");
            } catch (Exception e) {
                enc = "";
            }
            Http.Resp r = Http.get("/groups/public?page=" + pg + "&limit=12&q=" + enc);
            App.ui(() -> {
                if (my != seq) return;
                loading = false;
                if (r.ok()) data = r.obj();
                else error = r.status == 0 ? "Failed to load" : U.err(r, "Failed to load");
                render();
            });
        });
    }

    void render() {
        Context c = a;
        list.removeAllViews();
        foot.removeAllViews();
        foot.setVisibility(View.GONE);
        if (loading) {
            list.addView(state(c, spinner(c), "Loading groups…", null), W.lp(W.MP, W.WC));
            return;
        }
        if (error != null) {
            list.addView(state(c, U.txt(c, "⚠️", 40, 400, T.text), error, null), W.lp(W.MP, W.WC));
            return;
        }
        JSONArray gs = data == null ? null : data.optJSONArray("groups");
        if (gs == null || gs.length() == 0) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(Ic.get("ppl.groups", 44, T.textMuted));
            TextView t = U.txt(c, q.isEmpty() ? "No public groups yet" : "No groups match your search", 14, 600, T.text);
            list.addView(state(c, iv, q.isEmpty() ? "When a group is set to public, it will show up here." : "Try another name or keyword.", t), W.lp(W.MP, W.WC));
            return;
        }
        for (int i = 0; i < gs.length(); i++) {
            JSONObject g = gs.optJSONObject(i);
            LinearLayout.LayoutParams lp = W.lp(W.MP, W.WC);
            if (i > 0) lp.topMargin = T.dp(16);
            list.addView(card(c, g), lp);
        }
        int pages = data.optInt("totalPages", 1), total = data.optInt("total", 0), pg = data.optInt("page", page);
        foot.setVisibility(View.VISIBLE);
        foot.addView(pageBtn(c, "‹ Prev", pg > 1, () -> load(pg - 1)));
        TextView info = U.txt(c, "Page " + pg + " of " + pages + " · " + total + " group" + (total == 1 ? "" : "s"), 14, 400, T.textSecondary);
        info.setGravity(Gravity.CENTER);
        info.setMinWidth(T.dp(130));
        foot.addView(info, W.lpm(W.WC, W.WC, 16, 0, 16, 0));
        foot.addView(pageBtn(c, "Next ›", pg < pages, () -> load(pg + 1)));
        foot.post(this::padList);
    }

    View spinner(Context c) {
        W.Spin s = new W.Spin(c, T.accent, 3);
        s.setLayoutParams(W.lp(T.dp(26), T.dp(26)));
        return s;
    }

    View state(Context c, View icon, String msg, TextView title) {
        LinearLayout l = W.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(T.dp(40), T.dp(40), T.dp(40), T.dp(40));
        l.addView(icon);
        ((LinearLayout.LayoutParams) icon.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        if (title != null) {
            title.setGravity(Gravity.CENTER);
            l.addView(title, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        }
        TextView t = U.muted(c, msg, 14);
        t.setGravity(Gravity.CENTER);
        l.addView(t, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        return l;
    }

    TextView pageBtn(Context c, String l, boolean on, Runnable r) {
        TextView t = U.txt(c, l, 14, 500, T.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(T.dp(12), 0, T.dp(12), 0);
        t.setMinWidth(T.dp(36));
        t.setBackground(W.ripple(W.round(T.surface2, 999), 999));
        t.setEnabled(on);
        t.setAlpha(on ? 1 : .4f);
        if (on) t.setOnClickListener(v -> {
            r.run();
            sv.scrollTo(0, 0);
        });
        t.setLayoutParams(W.lp(W.WC, T.dp(36)));
        return t;
    }

    View card(Context c, JSONObject g) {
        LinearLayout card = W.col(c);
        card.setBackground(W.round(T.surface, 16));
        card.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        LinearLayout top = W.row(c);
        String name = g.optString("name");
        String url = g.isNull("avatarUrl") ? null : g.optString("avatarUrl", null);
        if (url != null && !url.matches("(?i)^(/uploads/|[a-z]+://|data:image/).*")) url = null;
        String color = g.isNull("avatarColor") ? null : g.optString("avatarColor", null);
        if (color != null && !color.matches("(?i)^#[0-9a-f]{3,8}$")) color = null;
        FrameLayout av = new FrameLayout(c);
        Integer col = U.hex(color);
        av.setBackground(W.round(col != null ? col : T.accent, 12));
        av.setClipToOutline(true);
        String letter = name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase();
        TextView lt = U.txt(c, letter, 20, 700, 0xffffffff);
        lt.setGravity(Gravity.CENTER);
        av.addView(lt, new FrameLayout.LayoutParams(W.MP, W.MP));
        if (url != null) {
            ImageView iv = new U.RoundImg(c, 12);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            av.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
            Img.into(iv, url, T.dp(96));
            lt.setVisibility(View.INVISIBLE);
        }
        top.addView(av, W.lp(T.dp(48), T.dp(48)));
        LinearLayout m = W.col(c);
        LinearLayout nr = W.row(c);
        TextView nm = U.txt(c, name, 16, 600, T.text);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        nr.addView(nm, new LinearLayout.LayoutParams(0, W.WC, 1f) {{
            weight = 0;
            width = W.WC;
        }});
        String badge = Badges.forGroup(g.optString("id"));
        if (badge != null) {
            ImageView b = new ImageView(c);
            b.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Img.anim(b, badge, T.dp(32), true);
            nr.addView(b, W.lpm(T.dp(16), T.dp(16), 6, 0, 0, 0));
            nm.setMaxWidth(c.getResources().getDisplayMetrics().widthPixels - T.dp(32 + 40 + 48 + 12 + 22));
        }
        m.addView(nr);
        int mc = g.optInt("memberCount");
        m.addView(U.muted(c, mc + " member" + (mc == 1 ? "" : "s"), 13), W.lpm(W.WC, W.WC, 0, 2, 0, 0));
        top.addView(m, W.lpm(0, W.WC, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) m.getLayoutParams()).weight = 1;
        card.addView(top);
        String desc = g.isNull("description") ? "" : g.optString("description", "");
        if (!desc.isEmpty()) {
            TextView d = U.txt(c, desc.trim().replaceAll("\\s+", " "), 14, 400, T.textSecondary);
            d.setMaxLines(2);
            d.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(d, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        }
        boolean done = g.optBoolean("isMember");
        TextView j = U.txt(c, done ? "✓ Joined" : "Join", 14, 600, done ? T.textSecondary : T.accentFg);
        j.setGravity(Gravity.CENTER);
        j.setPadding(T.dp(16), 0, T.dp(16), 0);
        j.setBackground(done ? W.round(T.surface3, 999) : W.ripple(W.accentBg(999), 999));
        if (done) {
            j.setEnabled(false);
            j.setAlpha(.6f);
        } else {
            W.press(j, .96f);
            j.setOnClickListener(v -> join(g, j));
        }
        card.addView(j, W.lpm(W.WC, T.dp(34), 0, 12, 0, 0));
        return card;
    }

    void join(JSONObject g, TextView btn) {
        if (joining != null) return;
        String id = g.optString("id");
        joining = id;
        CharSequence prev = btn.getText();
        btn.setEnabled(false);
        btn.setAlpha(.6f);
        btn.setText("Joining…");
        Join.joinId(id, (ok, res, err) -> {
            joining = null;
            if (ok) {
                btn.setText("✓ Joined");
                btn.setTextColor(T.textSecondary);
                btn.setBackground(W.round(T.surface3, 999));
                try {
                    g.put("isMember", true);
                } catch (Exception e) {
                }
                boolean requested = res != null && res.optBoolean("requested") && !res.optBoolean("alreadyMember");
                Ov.toast("Joined! Find it in your Chats.");
                if (requested) App.H.postDelayed(() -> Ov.toast("Join request sent - pending owner acceptance"), 400);
                App.bg(() -> com.vulpine.core.Chats.I.loadList(0));
            } else {
                btn.setEnabled(true);
                btn.setAlpha(1);
                btn.setText(prev);
                if (err != null) Ov.toast(err, true);
            }
        });
    }
}
