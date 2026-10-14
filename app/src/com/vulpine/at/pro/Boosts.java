package com.vulpine.at.pro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.shop.Flow;
import com.vulpine.at.shop.K;
import com.vulpine.at.shop.Seg;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class Boosts extends Page {
    static final String[] ROCKET = {"M5 15c-1.5 1.3-2 5-2 5s3.7-.5 5-2c.7-.8.7-2.1-.1-2.9a2.2 2.2 0 0 0-2.9-.1z", "m12 15-3-3a22 22 0 0 1 2-3.9A12.9 12.9 0 0 1 22 2c0 2.7-.8 7.5-6 11a22.4 22.4 0 0 1-4 2z", "M9 12H4s.6-3 2-4c1.6-1.1 5 0 5 0", "M12 15v5s3-.6 4-2c1.1-1.6 0-5 0-5"};
    static final String[][] LV = {{}, {"Group accent color", "Booster badges in chat"}, {"6 call rooms", "Background icon color"}, {"Chat wallpaper for everyone"}, {"7 call rooms", "Animated group photo"}, {"200 custom emoji", "Group stories"}, {"8 call rooms", "Group name font"}, {"Group page effect"}, {"10 call rooms", "Boosters skip slow mode"}, {"Custom wallpaper image"}, {"Animated level badge"}};
    static final String[][] ACC = {{"#f97316", "Orange"}, {"#3b82f6", "Blue"}, {"#22c55e", "Green"}, {"#ec4899", "Pink"}, {"#8b5cf6", "Purple"}, {"#06b6d4", "Cyan"}, {"#ef4444", "Red"}, {"#f59e0b", "Amber"}};
    static final String[][] BG = {{"#f97316", "Mango"}, {"#ef4444", "Cherry"}, {"#f59e0b", "Peach"}, {"#22c55e", "Kiwi"}, {"#06b6d4", "Berry"}, {"#3b82f6", "Blueberry"}, {"#8b5cf6", "Plum"}, {"#ec4899", "Guava"}, {"linear-gradient(135deg, #ff6b9d, #ffc371)", "Lychee"}, {"linear-gradient(135deg, #ff0080, #7928ca)", "Dragonfruit"}, {"linear-gradient(135deg, #84cc16, #ef4444)", "Watermelon"}, {"linear-gradient(135deg, #f5f5dc, #a0522d)", "Coconut"}, {"linear-gradient(135deg, #6b21a8, #f97316)", "Passion"}, {"linear-gradient(135deg, #fbbf24, #f97316)", "Apricot"}};
    static final String[][] WALL0 = {{"#0f172a", "Night"}, {"#111827", "Graphite"}, {"#0f1f17", "Pine"}, {"#1e1b4b", "Indigo"}, {"#fdf2f8", "Blush"}, {"#ecfeff", "Mist"}, {"#fefce8", "Cream"}, {"#f1f5f9", "Cloud"}};

    final String gid;
    JSONObject d, me, group;
    boolean busy, pick, alive = true;
    String saving = "";
    LinearLayout box;
    TextView goBtn;
    final Rt.Listener ws = m -> {
        String t = m.optString("type");
        if (("group.boosts".equals(t) && gidEq(m.optString("conversationId"))) || "boosts.granted".equals(t)) App.ui(this::load);
    };

    boolean gidEq(String c) {
        return gid.equals(c);
    }

    public Boosts(String gid) {
        super("Boosts");
        this.gid = gid;
    }

    public static void open(MainActivity a, String groupId) {
        if (a == null || groupId == null) return;
        a.push(new Boosts(groupId));
    }

    public static Drawable rocket(int sizeDp, int color) {
        return Svg.stroke(sizeDp, color, 2, ROCKET);
    }

    static int[] PURPLE = {0xffa78bfa, 0xff7c3aed};

    static GradientDrawable purple(float rDp, GradientDrawable.Orientation o) {
        GradientDrawable g = new GradientDrawable(o, PURPLE);
        g.setCornerRadius(T.dpf(rDp));
        return g;
    }

    public static View row(MainActivity a, String gid, JSONObject boost) {
        Context c = a;
        JSONObject b = boost == null ? App.jo("level", 0, "boosts", 0, "next", 1, "prev", 0) : boost;
        LinearLayout r = W.row(c);
        r.setBackground(W.ripple(W.round(T.surface2, 12), 12));
        r.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(purple(11, GradientDrawable.Orientation.TL_BR));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(rocket(20, 0xffffffff));
        ic.addView(iv, W.flp(T.dp(20), T.dp(20), Gravity.CENTER));
        r.addView(ic, W.lp(T.dp(36), T.dp(36)));
        LinearLayout tx = W.col(c);
        int lv = b.optInt("level"), bs = b.optInt("boosts");
        boolean hasNext = !b.isNull("next") && b.has("next");
        tx.addView(W.text(c, lv > 0 ? "Boosts · Level " + lv : "Boost this group", K.SM, 600, T.text));
        tx.addView(W.text(c, bs + " boost" + (bs == 1 ? "" : "s") + (hasNext ? " · " + (b.optInt("next") - bs) + " more for Level " + (lv + 1) : " · Max level"), K.XS, 400, T.textMuted));
        r.addView(tx, W.lpm(0, W.WC, 12, 0, 12, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        FrameLayout mini = new FrameLayout(c);
        mini.setBackground(W.round(T.mixA(T.text, .12f), 3));
        View fill = new View(c);
        fill.setBackground(purple(3, GradientDrawable.Orientation.LEFT_RIGHT));
        mini.addView(fill, new FrameLayout.LayoutParams(Math.round(T.dp(54) * pct(b)), W.MP));
        r.addView(mini, W.lp(T.dp(54), T.dp(6)));
        r.setOnClickListener(v -> open(a, gid));
        return r;
    }

    public static TextView levelChip(Context c, int level) {
        TextView t = W.text(c, "Lv " + level, 11, 700, 0xffffffff);
        t.setPadding(T.dp(7), T.dp(1), T.dp(7), T.dp(1));
        if (level >= 10) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xfff97316, 0xffec4899, 0xff8b5cf6, 0xff3b82f6, 0xff22c55e, 0xfff97316});
            g.setCornerRadius(T.dpf(999));
            t.setBackground(g);
        } else t.setBackground(purple(999, GradientDrawable.Orientation.TL_BR));
        return t;
    }

    public static View boosterChip(Context c, int count, boolean large) {
        LinearLayout r = W.row(c);
        r.setBackground(purple(999, GradientDrawable.Orientation.TL_BR));
        r.setPadding(T.dp(large ? 6 : 4), T.dp(large ? 2 : 1), T.dp(large ? 8 : 6), T.dp(large ? 2 : 1));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(rocket(large ? 13 : 11, 0xffffffff));
        r.addView(iv, W.lp(T.dp(large ? 13 : 11), T.dp(large ? 13 : 11)));
        if (large || count > 1) {
            TextView t = W.text(c, large ? "×" + count : String.valueOf(count), large ? K.XS : 10, 600, 0xffffffff);
            r.addView(t, W.lpm(W.WC, W.WC, 2, 0, 0, 0));
        }
        r.setContentDescription(count + " boost" + (count == 1 ? "" : "s"));
        return r;
    }

    static float pct(JSONObject b) {
        if (b == null || b.isNull("next") || !b.has("next")) return 1;
        float p = (b.optInt("boosts") - b.optInt("prev")) / (float) Math.max(1, b.optInt("next") - b.optInt("prev"));
        return Math.max(0, Math.min(1, p));
    }

    static String rel(long ts) {
        long d = Math.max(0, ts - System.currentTimeMillis());
        long h = d / 3600000, m = (long) Math.ceil((d % 3600000) / 60000.0);
        return h > 0 ? "in " + h + "h " + m + "m" : "in " + m + "m";
    }

    @Override
    protected void content(LinearLayout body) {
        box = W.col(a);
        body.addView(box, W.lp(W.MP, W.WC));
        Chats.Conv cv = Chats.I.conv(gid);
        group = cv == null ? new JSONObject() : cv.o;
        Rt.I.on(ws);
        render();
        load();
    }

    @Override
    public void onDestroy() {
        alive = false;
        Rt.I.off(ws);
    }

    String groupName() {
        Chats.Conv cv = Chats.I.conv(gid);
        String n = cv == null ? null : cv.title();
        return n == null || n.isEmpty() ? "Group" : n;
    }

    void load() {
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations/" + android.net.Uri.encode(gid) + "/boosts");
            Http.Resp m = Http.get("/me/boosts");
            App.ui(() -> {
                if (!alive) return;
                if (r.ok()) d = r.obj();
                if (m.ok()) me = m.obj();
                render();
            });
        });
    }

    JSONObject boost() {
        if (d != null && d.optJSONObject("boost") != null) return d.optJSONObject("boost");
        JSONObject g = group == null ? null : group.optJSONObject("boost");
        return g != null ? g : App.jo("level", 0, "boosts", 0, "next", 1, "prev", 0);
    }

    JSONObject cfg() {
        if (d != null && d.optJSONObject("config") != null) return d.optJSONObject("config");
        return me == null ? null : me.optJSONObject("config");
    }

    LinearLayout card(String title) {
        LinearLayout k = W.col(a);
        k.setBackground(W.round(T.surface, 16));
        k.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
        if (title != null) {
            TextView tt = W.text(a, title, K.SM, 600, T.text);
            T.lh(tt, 21);
            k.addView(tt, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        }
        box.addView(k, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        return k;
    }

    void render() {
        if (box == null) return;
        int y = sv == null ? 0 : sv.getScrollY();
        box.removeAllViews();
        Context c = a;
        JSONObject b = boost();
        int lv = b.optInt("level"), bs = b.optInt("boosts");
        boolean hasNext = b.has("next") && !b.isNull("next");
        int mine = d == null ? 0 : d.optInt("mine");
        JSONArray slots = me == null ? new JSONArray() : me.optJSONArray("slots") == null ? new JSONArray() : me.optJSONArray("slots");
        int free = 0;
        for (int i = 0; i < slots.length(); i++) if (slots.optJSONObject(i).isNull("groupId") || slots.optJSONObject(i).optString("groupId").isEmpty()) free++;
        JSONObject cfg = cfg();
        LinearLayout hero = W.col(c);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(T.dp(16), T.dp(20), T.dp(16), T.dp(16));
        hero.setBackground(new HeroBg());
        hero.setClipChildren(false);
        hero.addView(new Badge(c, lv), W.lpm(T.dp(116), T.dp(116), 0, -20, 0, -20));
        TextView gn = K.text(c, groupName(), K.LG, 700, T.text);
        gn.setGravity(Gravity.CENTER);
        hero.addView(gn, W.lpm(W.WC, W.WC, 0, 12, 0, 0));
        int more = b.optInt("next") - bs;
        TextView sub = K.text(c, hasNext ? more + " more boost" + (more == 1 ? "" : "s") + " to unlock Level " + (lv + 1) : "This group reached the highest level", K.SM, 400, T.textSecondary);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(W.round(T.mixA(T.text, .10f), 999));
        View fill = new View(c);
        fill.setBackground(purple(999, GradientDrawable.Orientation.LEFT_RIGHT));
        bar.addView(fill, new FrameLayout.LayoutParams(T.dp(30), W.MP));
        float p = pct(b);
        bar.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int w = Math.max(T.dp(30), Math.round((r - l) * p));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
            if (lp.width != w) {
                lp.width = w;
                fill.post(() -> fill.setLayoutParams(lp));
            }
        });
        LinearLayout cnt = W.row(c);
        ImageView ri = new ImageView(c);
        ri.setImageDrawable(rocket(14, 0xffffffff));
        cnt.addView(ri, W.lp(T.dp(14), T.dp(14)));
        cnt.addView(W.text(c, String.valueOf(bs), K.XS, 600, 0xffffffff), W.lpm(W.WC, W.WC, 4, 0, 0, 0));
        FrameLayout.LayoutParams cl = W.flp(W.WC, W.WC, Gravity.START | Gravity.CENTER_VERTICAL);
        cl.leftMargin = T.dp(10);
        bar.addView(cnt, cl);
        hero.addView(bar, W.lpm(W.MP, T.dp(30), 0, 16, 0, 0));
        LinearLayout bl = W.row(c);
        TextView bl0 = W.text(c, "Level " + lv, K.XS, 400, T.textMuted), bl1 = W.text(c, hasNext ? "Level " + (lv + 1) : "Max", K.XS, 400, T.textMuted);
        T.lh(bl0, 19.5f);
        T.lh(bl1, 19.5f);
        bl.addView(bl0, W.lp(0, W.WC, 1));
        bl.addView(bl1);
        hero.addView(bl, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        LinearLayout acts = W.row(c);
        goBtn = K.btn(c, mine > 0 ? "Boost again" : "Boost group", "primary", v -> boost(null));
        K.enable(goBtn, !busy);
        acts.addView(goBtn, W.lp(0, T.dp(36), 1));
        TextView get = K.btn(c, "Get boosts", "secondary", v -> buy());
        LinearLayout.LayoutParams gl = W.lp(0, T.dp(36), 1);
        gl.leftMargin = T.dp(8);
        acts.addView(get, gl);
        hero.addView(acts, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        String note = mine > 0 ? "You are boosting this group with " + mine + " boost" + (mine > 1 ? "s" : "") + "." + (free > 0 ? " You have " + free + " free boost" + (free > 1 ? "s" : "") + "." : "") : me != null && me.optBoolean("pro") ? "Your Pro Box includes " + (cfg == null ? 2 : cfg.optInt("proFree", 2)) + " free boosts." : "Boost with Pro Box or get boosts to lift this group.";
        TextView nt = K.text(c, note, K.XS, 400, T.textMuted);
        nt.setGravity(Gravity.CENTER);
        hero.addView(nt, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        box.addView(hero, W.lp(W.MP, W.WC));
        if (pick) {
            LinearLayout k = card("Move a boost here");
            k.addView(K.text(c, "All your boosts are in use. Moving one starts a 24h cooldown for that boost.", K.XS, 400, T.textMuted), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
            for (int i = 0; i < slots.length(); i++) {
                JSONObject s = slots.optJSONObject(i);
                String g = s.optString("groupId", "");
                if (s.isNull("groupId") || g.isEmpty() || g.equals(gid)) continue;
                boolean cd = s.optLong("movableAt") > System.currentTimeMillis();
                LinearLayout r = W.row(c);
                r.setPadding(0, T.dp(6), 0, T.dp(6));
                LinearLayout tt = W.col(c);
                tt.addView(W.text(c, s.optString("groupName", "Group").isEmpty() ? "Group" : s.optString("groupName"), K.SM, 700, T.text));
                tt.addView(W.text(c, "pro".equals(s.optString("source")) ? "Pro Box boost" : "Until " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(s.optLong("expiresAt"))), K.XS, 400, T.textMuted));
                r.addView(tt, W.lp(0, W.WC, 1));
                String sid = s.optString("id");
                TextView mv = K.btn(c, cd ? rel(s.optLong("movableAt")) : "Move here", "secondary", v -> boost(sid));
                K.enable(mv, !cd && !busy);
                r.addView(mv, W.lpm(W.WC, T.dp(36), 12, 0, 0, 0));
                k.addView(r, W.lp(W.MP, W.WC));
            }
            TextView cc = K.btn(c, "Cancel", "ghost", v -> {
                pick = false;
                render();
            });
            k.addView(cc, W.lp(W.MP, T.dp(36)));
        }
        boolean canEdit = d != null && d.optBoolean("canEdit");
        if (canEdit && lv >= 5) {
            LinearLayout k = card("Group stories");
            k.addView(K.text(c, "Post a story as the group. Members see it at the top of their chat list.", K.XS, 400, T.textMuted));
            TextView ps = K.btn(c, "Post a group story", "secondary", v -> composeStory());
            k.addView(ps, W.lpm(W.MP, T.dp(36), 0, 8, 0, 0));
        }
        if (canEdit) customize(c, lv, cfg);
        LinearLayout lvk = card("Levels");
        JSONArray lvs = cfg == null ? null : cfg.optJSONArray("levels");
        for (int n = 1; n <= 10; n++) {
            boolean got = lv >= n;
            LinearLayout r = W.row(c);
            r.setPadding(0, T.dp(6), 0, T.dp(6));
            r.setAlpha(got ? 1 : .62f);
            TextView num = W.text(c, String.valueOf(n), K.XS, 700, got ? 0xffffffff : T.textSecondary);
            num.setGravity(Gravity.CENTER);
            num.setBackground(got ? oval(PURPLE) : W.oval(T.mixA(T.text, .10f)));
            r.addView(num, W.lp(T.dp(28), T.dp(28)));
            LinearLayout tt = W.col(c);
            tt.addView(W.text(c, String.join(" · ", LV[n]), K.SM, 500, T.text));
            int need = lvs == null ? n : lvs.optInt(n, n);
            tt.addView(W.text(c, need + " boost" + (need == 1 ? "" : "s"), K.XS, 400, T.textMuted));
            r.addView(tt, W.lpm(0, W.WC, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) tt.getLayoutParams()).weight = 1;
            if (got) r.addView(W.text(c, "✓", K.SM, 700, 0xffa78bfa), W.lpm(W.WC, W.WC, 12, 0, 0, 0));
            lvk.addView(r, W.lp(W.MP, W.WC));
        }
        JSONArray us = d == null ? null : d.optJSONArray("boosters");
        if (us != null && us.length() > 0) {
            LinearLayout k = card("Boosters");
            for (int i = 0; i < us.length(); i++) {
                JSONObject u = us.optJSONObject(i);
                LinearLayout r = W.row(c);
                r.setPadding(0, T.dp(4), 0, T.dp(4));
                AvatarView av = new AvatarView(c);
                av.setLayoutParams(W.lp(T.dp(32), T.dp(32)));
                av.textSize(13);
                String ac = u.optString("avatarColor", "");
                av.set(u.optString("displayName"), ac.isEmpty() ? null : ac, u.optString("avatarUrl", ""));
                r.addView(av);
                LinearLayout tt = W.col(c);
                TextView nm = W.text(c, u.optString("displayName"), K.SM, 500, T.text);
                W.ellipsize(nm);
                tt.addView(nm);
                tt.addView(W.text(c, "@" + u.optString("handle"), K.XS, 400, T.textMuted));
                r.addView(tt, W.lpm(0, W.WC, 12, 0, 12, 0));
                ((LinearLayout.LayoutParams) tt.getLayoutParams()).weight = 1;
                r.addView(boosterChip(c, u.optInt("count"), true));
                k.addView(r, W.lp(W.MP, W.WC));
            }
        }
        if (sv != null) sv.post(() -> sv.scrollTo(0, y));
    }

    static GradientDrawable oval(int[] cols) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, cols);
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    void composeStory() {
        try {
            Class<?> s = Class.forName("com.vulpine.at.stories.Stories");
            s.getMethod("composeGroup", MainActivity.class, String.class).invoke(null, a, gid);
        } catch (Throwable e) {
            Ov.toast("Stories are not available", true);
        }
    }

    void boost(String slotId) {
        if (busy) return;
        busy = true;
        if (goBtn != null) K.enable(goBtn, false);
        int lvBefore = boost().optInt("level");
        App.bg(() -> {
            Http.Resp r = Http.post("/conversations/" + android.net.Uri.encode(gid) + "/boost", slotId == null ? new JSONObject() : App.jo("slotId", slotId));
            App.ui(() -> {
                busy = false;
                if (!alive) return;
                if (!r.ok()) {
                    JSONObject js = r.obj();
                    String e = js.optString("error");
                    if ("no_slots".equals(e)) buy();
                    else if ("no_free_slot".equals(e)) {
                        boolean other = false;
                        JSONArray slots = me == null ? null : me.optJSONArray("slots");
                        for (int i = 0; slots != null && i < slots.length(); i++) {
                            String g = slots.optJSONObject(i).optString("groupId", "");
                            if (!slots.optJSONObject(i).isNull("groupId") && !g.isEmpty() && !g.equals(gid)) other = true;
                        }
                        if (other) pick = true;
                        else buy();
                    } else if ("cooldown".equals(e)) Ov.toast("You can move this boost " + rel(js.optLong("movableAt")), true);
                    else Ov.toast("Could not boost this group", true);
                    render();
                    return;
                }
                pick = false;
                JSONObject nb = r.obj().optJSONObject("boost");
                burst();
                Ov.toast(nb != null && nb.optInt("level") > lvBefore ? "Level " + nb.optInt("level") + " unlocked!" : "Thanks for boosting!");
                if (nb != null && d != null) {
                    try {
                        d.put("boost", nb);
                        d.put("mine", r.obj().optInt("mine"));
                    } catch (Exception ex) {
                    }
                }
                render();
                load();
            });
        });
    }

    void burst() {
        FrameLayout h = Ov.host();
        View btn = goBtn;
        if (h == null || btn == null) return;
        int[] bl = new int[2], hl = new int[2];
        btn.getLocationInWindow(bl);
        h.getLocationInWindow(hl);
        float cx = bl[0] - hl[0] + btn.getWidth() / 2f, cy = bl[1] - hl[1] + btn.getHeight() / 2f;
        Burst v = new Burst(h.getContext(), cx, cy);
        h.addView(v, new FrameLayout.LayoutParams(W.MP, W.MP));
        h.postDelayed(() -> h.removeView(v), 1100);
        btn.animate().cancel();
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.setDuration(420);
        va.addUpdateListener(x -> {
            float f = (float) x.getAnimatedValue();
            float s = f < 1 / 3f ? 1 - .06f * (f * 3) : f < 2 / 3f ? .94f + .10f * ((f - 1 / 3f) * 3) : 1.04f - .04f * ((f - 2 / 3f) * 3);
            btn.setScaleX(s);
            btn.setScaleY(s);
        });
        va.start();
    }

    static final class Burst extends View {
        final float cx, cy;
        final float[] ang = new float[22], rad = new float[22], dur = new float[22];
        final long t0 = SystemClock.uptimeMillis();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(.2f, .8f, .3f, 1f);

        Burst(Context c, float cx, float cy) {
            super(c);
            this.cx = cx;
            this.cy = cy;
            java.util.Random r = new java.util.Random();
            for (int i = 0; i < 22; i++) {
                ang[i] = (float) (Math.PI * 2 * i / 22 + r.nextFloat() * .4);
                rad[i] = T.dpf(70 + r.nextFloat() * 80);
                dur[i] = 700 + r.nextFloat() * 300;
            }
        }

        protected void onDraw(Canvas c) {
            float t = SystemClock.uptimeMillis() - t0;
            boolean any = false;
            for (int i = 0; i < 22; i++) {
                float f = Math.min(1, t / dur[i]);
                if (f < 1) any = true;
                float e = ease.getInterpolation(f);
                int col = i % 3 == 0 ? 0xfff97316 : i % 3 == 1 ? 0xffec4899 : 0xffa78bfa;
                float size = T.dpf(i % 3 == 1 ? 6 : 9) * (1 - .7f * e);
                p.setColor(col);
                p.setAlpha(Math.round(255 * (1 - e)));
                c.drawCircle(cx + (float) Math.cos(ang[i]) * rad[i] * e, cy + (float) Math.sin(ang[i]) * rad[i] * e, size / 2, p);
            }
            if (any) postInvalidateOnAnimation();
        }
    }

    final class HeroBg extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();

        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            r.set(b);
            float w = b.width(), h = b.height();
            float rx = Math.max(1, 1.2f * w), ry = Math.max(1, .9f * h);
            RadialGradient g = new RadialGradient(0, 0, rx, new int[]{T.mix(T.surface, 0xff7c3aed, .26f), T.surface, T.surface}, new float[]{0, .7f, 1}, Shader.TileMode.CLAMP);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(1, ry / rx);
            m.postTranslate(b.left + w / 2f, b.top);
            g.setLocalMatrix(m);
            p.setShader(g);
            c.drawRoundRect(r, T.dpf(16), T.dpf(16), p);
        }

        public void setAlpha(int a) {
        }

        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    static final class Badge extends View {
        final int lv;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Drawable rk = rocket(20, 0xffffffff);
        final long t0 = SystemClock.uptimeMillis();
        static final android.view.animation.PathInterpolator EO = new android.view.animation.PathInterpolator(0f, 0f, .58f, 1f);

        Badge(Context c, int lv) {
            super(c);
            this.lv = lv;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            tp.setTypeface(T.b);
            tp.setTextSize(T.dpf(26));
            tp.setColor(0xffffffff);
            setScaleX(.6f);
            setScaleY(.6f);
            setAlpha(0);
            animate().scaleX(1).scaleY(1).alpha(1).setDuration(500).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1.3f)).start();
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = T.dpf(38);
            p.setShader(null);
            p.setColor(0);
            p.setShadowLayer(T.dpf(15), 0, T.dpf(10), 0x737c3aed);
            p.setColor(0xff7c3aed);
            c.drawCircle(w / 2, h / 2, r, p);
            p.clearShadowLayer();
            if (lv >= 10) {
                float rot = ((SystemClock.uptimeMillis() - t0) % 6000) / 6000f * 360f;
                SweepGradient g = new SweepGradient(w / 2, h / 2, new int[]{0xfff97316, 0xffec4899, 0xff8b5cf6, 0xff3b82f6, 0xff22c55e, 0xfff97316}, null);
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setRotate(rot - 90, w / 2, h / 2);
                g.setLocalMatrix(m);
                p.setShader(g);
                postInvalidateDelayed(33);
            } else {
                double a = Math.toRadians(160);
                float dx = (float) Math.sin(a) * r, dy = (float) -Math.cos(a) * r;
                p.setShader(new LinearGradient(w / 2 - dx, h / 2 - dy, w / 2 + dx, h / 2 + dy, 0xffa78bfa, 0xff6d28d9, Shader.TileMode.CLAMP));
            }
            c.drawCircle(w / 2, h / 2, r, p);
            p.setShader(null);
            float ph = T.power ? 0 : ((SystemClock.uptimeMillis() - t0) % 2400) / 2400f;
            float e = EO.getInterpolation(ph);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(T.dpf(2));
            p.setColor(0xa78bfa | (Math.round(255 * .45f * .9f * (1 - e)) << 24));
            c.drawCircle(w / 2, h / 2, (r + T.dpf(5)) * (.92f + .33f * e), p);
            p.setStyle(Paint.Style.FILL);
            if (!T.power) postInvalidateDelayed(33);
            String s = String.valueOf(lv);
            float tw = tp.measureText(s), iw = T.dpf(20), gap = T.dpf(2);
            float x = (w - (iw + gap + tw)) / 2;
            rk.setBounds((int) x, (int) (h / 2 - iw / 2), (int) (x + iw), (int) (h / 2 + iw / 2));
            rk.draw(c);
            Paint.FontMetrics fm = tp.getFontMetrics();
            c.drawText(s, x + iw + gap, h / 2 - (fm.ascent + fm.descent) / 2, tp);
        }
    }

    void save(String k, Object v) {
        if (!saving.isEmpty()) return;
        saving = k;
        App.bg(() -> {
            Http.Resp r = Http.patch("/conversations/" + android.net.Uri.encode(gid), App.jo(k, v));
            App.ui(() -> {
                saving = "";
                if (!r.ok()) {
                    JSONObject o = r.obj();
                    String m = o.optString("message", o.optString("error", "Could not update"));
                    Ov.toast(m.isEmpty() ? "Could not update" : m, true);
                    return;
                }
                load();
            });
        });
    }

    View lockChip(int n) {
        TextView t = W.text(a, "Level " + n, 11, 600, T.textSecondary);
        T.lh(t, 16.5f);
        t.setPadding(T.dp(8), t.getPaddingTop() + T.dp(1), T.dp(8), t.getPaddingBottom() + T.dp(1));
        t.setBackground(W.round(T.mixA(T.text, .10f), 999));
        return t;
    }

    LinearLayout cz(LinearLayout k, String label, int need, int lv) {
        LinearLayout z = W.col(a);
        boolean gap = k.getChildCount() > 1;
        z.setPadding(0, T.dp(8), 0, T.dp(8));
        LinearLayout h = W.row(a);
        TextView hl = W.text(a, label, K.SM, 400, T.text);
        T.lh(hl, 21);
        h.addView(hl, W.lp(0, W.WC, 1));
        if (lv < need) h.addView(lockChip(need));
        z.addView(h, W.lp(W.MP, W.WC));
        k.addView(z, W.lpm(W.MP, W.WC, 0, gap ? 8 : 0, 0, 0));
        return z;
    }

    void swatches(LinearLayout k, String key, String[][] list, String cur, int need, String label, int lv) {
        LinearLayout z = cz(k, label, need, lv);
        Flow f = new Flow(a, T.dp(8), T.dp(8));
        boolean off = lv < need;
        f.addView(sw(null, cur == null || cur.isEmpty(), off, () -> save(key, "")), new Flow.LP(T.dp(32), T.dp(32)));
        for (String[] x : list) f.addView(sw(x[0], x[0].equals(cur), off, () -> save(key, x[0])), new Flow.LP(T.dp(32), T.dp(32)));
        if (off) f.setAlpha(.45f);
        f.setClipChildren(false);
        z.setClipChildren(false);
        k.setClipChildren(false);
        z.addView(f, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
    }

    View sw(String css, boolean on, boolean off, Runnable click) {
        View v = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            protected void onDraw(Canvas c) {
                float w = getWidth(), h = getHeight();
                float cx = w / 2, cy = h / 2, r = T.dpf(16);
                if (on) {
                    p.setShader(null);
                    p.setColor(T.text);
                    c.drawCircle(cx, cy, r + T.dpf(4), p);
                    p.setColor(T.surface);
                    c.drawCircle(cx, cy, r + T.dpf(2), p);
                }
                if (css == null) {
                    p.setShader(null);
                    p.setColor(0);
                    int sv = c.save();
                    android.graphics.Path cp = new android.graphics.Path();
                    cp.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
                    c.clipPath(cp);
                    p.setColor(T.mixA(T.text, .18f));
                    p.setStrokeWidth(T.dpf(1));
                    float step = T.dpf(6) * 1.414f;
                    for (float x = -h; x < w + h; x += step) c.drawLine(x, 0, x + h, h, p);
                    c.restoreToCount(sv);
                } else {
                    int[] g = Css.gradientColors(css);
                    if (g != null && g.length > 1) p.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, g, null, Shader.TileMode.CLAMP));
                    else {
                        p.setShader(null);
                        Integer col = Css.color(css);
                        p.setColor(col == null ? 0 : col);
                    }
                    c.drawCircle(cx, cy, r, p);
                }
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(.5f));
                p.setColor(T.mixA(T.text, .18f));
                c.drawCircle(cx, cy, r - T.dpf(.25f), p);
                p.setStyle(Paint.Style.FILL);
            }
        };
        v.setContentDescription(css == null ? "None" : css);
        if (!off) v.setOnClickListener(x -> click.run());
        return v;
    }

    void customize(Context c, int lv, JSONObject cfg) {
        JSONObject st = d.optJSONObject("settings") == null ? new JSONObject() : d.optJSONObject("settings");
        LinearLayout k = card("Customize the group");
        swatches(k, "boostAccent", ACC, st.optString("accent", ""), 1, "Accent color", lv);
        swatches(k, "boostIcons", BG, st.optString("icons", ""), 2, "Background icons color", lv);
        String[][] wall = new String[WALL0.length + 6][];
        System.arraycopy(WALL0, 0, wall, 0, WALL0.length);
        System.arraycopy(BG, 8, wall, WALL0.length, 6);
        String wp = st.optString("wallpaper", "");
        swatches(k, "boostWallpaper", wall, wp.startsWith("/uploads/") ? "" : wp, 3, "Chat wallpaper", lv);
        LinearLayout z9 = cz(k, "Wallpaper image", 9, lv);
        TextView up = K.btn(c, wp.startsWith("/uploads/") ? "Replace image" : "Upload image", "secondary", v -> pickWallpaper());
        if (lv < 9) {
            up.setAlpha(.45f);
            up.setEnabled(false);
        }
        z9.addView(up, W.lpm(W.WC, T.dp(36), 0, 8, 0, 0));
        LinearLayout z6 = cz(k, "Group name font", 6, lv);
        JSONArray fonts = cfg == null ? null : cfg.optJSONArray("fonts");
        LinearLayout grid = W.col(c);
        java.util.List<String> fl = new java.util.ArrayList<>();
        fl.add("");
        for (int i = 0; fonts != null && i < fonts.length(); i++) fl.add(fonts.optString(i));
        String name = groupName();
        String sample = name.length() > 18 ? name.substring(0, 18) : name;
        LinearLayout row = null;
        for (int i = 0; i < fl.size(); i++) {
            if (i % 2 == 0) {
                row = W.row(c);
                grid.addView(row, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 8, 0, 0));
            }
            String f = fl.get(i);
            TextView b = W.text(c, f.isEmpty() ? "Default" : sample, K.SM, 400, T.text);
            if (!f.isEmpty()) b.setTypeface(com.vulpine.at.people.U.font(f, 400));
            W.ellipsize(b);
            b.setPadding(T.dp(10), T.dp(8), T.dp(10), T.dp(8));
            boolean on = f.equals(st.optString("font", ""));
            GradientDrawable bg = W.round(T.mixA(T.text, .07f), 12);
            if (on) bg.setStroke(T.dp(2), T.accent);
            b.setBackground(bg);
            if (lv >= 6) b.setOnClickListener(v -> save("boostFont", f));
            LinearLayout.LayoutParams lp = W.lp(0, W.WC, 1);
            if (i % 2 == 1) lp.leftMargin = T.dp(8);
            row.addView(b, lp);
        }
        if (fl.size() % 2 == 1) {
            View sp = new View(c);
            LinearLayout.LayoutParams lp = W.lp(0, 1, 1);
            lp.leftMargin = T.dp(8);
            row.addView(sp, lp);
        }
        if (lv < 6) grid.setAlpha(.45f);
        z6.addView(grid, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        LinearLayout z7 = cz(k, "Group page effect", 7, lv);
        String[] fxk = {"", "pixels", "ascii", "icons"};
        String curFx = st.optString("effect", "");
        int sel = Math.max(0, java.util.Arrays.asList(fxk).indexOf(curFx));
        Seg seg = new Seg(c, new String[]{"None", "Pixels", "ASCII", "Icons"}, sel, K.SM, i -> save("boostEffect", fxk[i]));
        if (lv < 7) {
            seg.setAlpha(.45f);
            for (int i = 0; i < seg.getChildCount(); i++) seg.getChildAt(i).setEnabled(false);
            seg.setEnabled(false);
        }
        z7.addView(seg, W.lpm(W.MP, T.dp(42), 0, 8, 0, 0));
        if ("icons".equals(curFx) && lv >= 7) {
            FxPick fp = new FxPick(c, st.optJSONObject("effectConfig"), n -> save("boostEffectConfig", n));
            z7.addView(fp, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        }
    }

    void pickWallpaper() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/jpeg", "image/webp"});
        App.startForResult(a, i, (res, data) -> {
            if (data == null || data.getData() == null) return;
            android.net.Uri u = data.getData();
            App.bg(() -> {
                try {
                    String mime = a.getContentResolver().getType(u);
                    if (mime == null) mime = "image/jpeg";
                    java.io.File f = new java.io.File(a.getCacheDir(), "bwall-" + System.currentTimeMillis());
                    try (java.io.InputStream in = a.getContentResolver().openInputStream(u); java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
                        byte[] b = new byte[65536];
                        int n;
                        while ((n = in.read(b)) > 0) o.write(b, 0, n);
                    }
                    String ext = mime.contains("png") ? "png" : mime.contains("webp") ? "webp" : "jpg";
                    Http.Resp r = Http.upload("/messages/" + android.net.Uri.encode(gid) + "/attachment", "file", f, "wallpaper." + ext, mime, (Http.Progress) null);
                    f.delete();
                    App.ui(() -> {
                        if (!r.ok()) {
                            Ov.toast(r.obj().optString("message", "Upload failed"), true);
                            return;
                        }
                        String url = r.obj().optString("url", "");
                        if (!url.isEmpty()) save("boostWallpaper", url);
                    });
                } catch (Exception e) {
                    App.ui(() -> Ov.toast("Upload failed", true));
                }
            });
        });
    }

    void buy() {
        new BoostBuy(a, cfg(), this::load).show();
    }
}
