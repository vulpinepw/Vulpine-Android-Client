package com.vulpine.at.stories;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Stories {
    static final PathInterpolator OPEN_EASE = new PathInterpolator(.2f, .9f, .25f, 1f);
    static WeakReference<LinearLayout> miniRef = new WeakReference<>(null);
    static final java.util.concurrent.CopyOnWriteArrayList<WeakReference<Strip>> strips = new java.util.concurrent.CopyOnWriteArrayList<>();
    static boolean open;
    static String miniSig;
    static float dragH;
    static ValueAnimator anim;
    static final Runnable render = Stories::render;

    public static void attachPill(Home h, FrameLayout left) {
        Store.wire();
        Context c = left.getContext();
        LinearLayout mini = W.row(c);
        mini.setBackground(W.round(T.mixA(T.text, .07f), 999));
        mini.setPadding(T.dp(4), T.dp(4), T.dp(12), T.dp(4));
        mini.setContentDescription("Stories");
        mini.setOnClickListener(v -> miniTap());
        mini.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == android.view.MotionEvent.ACTION_DOWN) x.animate().scaleX(.92f).scaleY(.92f).setDuration(200).setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1.2f)).start();
            else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) x.animate().scaleX(1).scaleY(1).setDuration(200).setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1.2f)).start();
            return false;
        });
        FrameLayout.LayoutParams lp = W.flp(W.WC, W.WC, Gravity.START | Gravity.CENTER_VERTICAL);
        lp.leftMargin = T.dp(6);
        left.addView(mini, lp);
        mini.setVisibility(View.INVISIBLE);
        miniRef = new WeakReference<>(mini);
        miniSig = null;
        Store.off(render);
        Store.on(render);
        render();
        Store.load();
    }

    public static View strip(Context c, Home.Tab t) {
        Strip s = new Strip(c);
        s.tab = new WeakReference<>(t);
        s.pinned = t instanceof com.vulpine.at.home.ContactsTab;
        for (WeakReference<Strip> w : strips) if (w.get() == null || w.get().tab.get() == t) strips.remove(w);
        strips.add(new WeakReference<>(s));
        boolean on = open || s.pinned;
        s.setLayoutParams(new LinearLayout.LayoutParams(W.MP, on ? T.dp(104) : 0));
        s.setAlpha(on ? 1 : 0);
        s.rebuild();
        return s;
    }

    static List<Strip> all() {
        List<Strip> l = new ArrayList<>();
        for (WeakReference<Strip> w : strips) {
            Strip s = w.get();
            if (s == null) strips.remove(w);
            else l.add(s);
        }
        return l;
    }

    static Strip of(Home.Tab t) {
        for (Strip s : all()) if (s.tab.get() == t) return s;
        return null;
    }

    static Strip active() {
        for (Strip s : all()) {
            Home.Tab t = s.tab.get();
            if (t != null && t.v != null && t.v.isShown()) return s;
        }
        return null;
    }

    static ListView list(Strip s) {
        Home.Tab t = s == null ? null : s.tab.get();
        if (t == null || !(t.v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) t.v;
        for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i) instanceof ListView) return (ListView) g.getChildAt(i);
        return null;
    }

    public static void onListScroll(Home.Tab t) {
        Strip s = of(t);
        if (!open || s == null || s.pinned || anim != null && anim.isRunning() || !s.isShown()) return;
        ListView l = list(s);
        if (l == null) return;
        int first = l.getFirstVisiblePosition();
        boolean far;
        if (first > 0) far = true;
        else {
            View h = l.getChildAt(0);
            if (h == null) return;
            int stripTop = h.getTop() + s.getTop();
            far = l.getPaddingTop() - stripTop > s.getHeight() + T.dp(8);
        }
        if (!far) return;
        int hpx = s.getHeight();
        View c0 = l.getChildAt(0);
        int y0 = c0 == null ? 0 : c0.getTop();
        open = false;
        for (Strip x : all()) if (!x.pinned) {
            setH(x, 0);
            x.setAlpha(0);
        }
        if (first == 0) l.setSelectionFromTop(0, y0 + hpx);
        renderMini();
    }

    public static boolean pullFirst(Home.Tab t) {
        Strip s = of(t);
        return !open && s != null && !s.pinned && s.isAttachedToWindow() && !Store.miniList().isEmpty();
    }

    public static void dragStrip(Home.Tab t, float dy) {
        Strip s = of(t);
        if (s == null || s.pinned) return;
        if (anim != null) anim.cancel();
        float full = T.dp(104);
        dragH = Math.max(0, Math.min(full, dy * .7f));
        setH(s, Math.round(dragH));
        s.setAlpha(Math.min(1f, dragH / (full * .6f)));
    }

    public static void releaseStrip(Home.Tab t) {
        if (of(t) == null || of(t).pinned) return;
        setOpen(dragH > T.dp(40), false);
        dragH = 0;
    }

    public static void openComposer(MainActivity a) {
        Editor.compose(a, null);
    }

    public static void composeGroup(MainActivity a, String groupId) {
        Editor.compose(a, groupId);
    }

    public static void openUser(MainActivity a, String userId) {
        openUser(a, userId, null);
    }

    public static void openUser(MainActivity a, String userId, View from) {
        if (a == null || userId == null) return;
        JSONObject e = Store.entry(userId);
        if (e != null) {
            openViewer(a, userId, from);
            return;
        }
        App.bg(() -> {
            Http.Resp r = Http.get("/stories/user/" + userId);
            if (!r.ok()) return;
            JSONObject j = r.obj();
            JSONArray st = j.optJSONArray("stories");
            if (st == null || st.length() == 0) return;
            App.ui(() -> {
                try {
                    JSONObject u = j.optJSONObject("user");
                    JSONObject en = u == null ? new JSONObject() : new JSONObject(u.toString());
                    en.put("key", userId).put("stories", st).put("self", userId.equals(Session.userId()));
                    List<JSONObject> l = new ArrayList<>();
                    l.add(en);
                    Viewer.open(a, l, 0, from, 0);
                } catch (Exception ex) {
                }
            });
        });
    }

    static void openViewer(MainActivity a, String key, View from) {
        List<JSONObject> l = new ArrayList<>();
        int ui = -1;
        for (JSONObject e : Store.entries()) {
            if (Store.count(e) == 0) continue;
            if (key.equals(e.optString("key")) && ui < 0) ui = l.size();
            l.add(e);
        }
        if (ui < 0) return;
        Viewer.open(a, l, ui, from, -1);
    }

    static void miniTap() {
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        if (Store.miniList().isEmpty()) {
            Sheets.intro(a);
            return;
        }
        Strip cur = active();
        if (cur != null && cur.pinned) {
            ListView l = list(cur);
            if (l != null) l.smoothScrollToPositionFromTop(0, 0, 300);
            return;
        }
        setOpen(!open, true);
    }

    static void setH(View s, int h) {
        ViewGroup.LayoutParams p = s.getLayoutParams();
        if (p == null || p.height == h) return;
        p.height = h;
        s.setLayoutParams(p);
    }

    static void setOpen(boolean v, boolean scrollTop) {
        Strip s0 = active();
        Strip s = s0 != null && s0.pinned ? null : s0;
        open = v;
        renderMini();
        if (anim != null) anim.cancel();
        for (Strip x : all()) if (x != s && !x.pinned) {
            setH(x, v ? T.dp(104) : 0);
            x.setAlpha(v ? 1f : 0f);
        }
        if (s == null) return;
        int from = s.getLayoutParams() == null ? 0 : s.getLayoutParams().height, to = v ? T.dp(104) : 0;
        float a0 = s.getAlpha();
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.setDuration(T.lowfx ? 160 : 340);
        va.setInterpolator(OPEN_EASE);
        va.addUpdateListener(x -> {
            float f = (float) x.getAnimatedValue();
            setH(s, Math.round(from + (to - from) * f));
            float af = Math.min(1f, x.getCurrentPlayTime() / 220f);
            s.setAlpha(a0 + ((v ? 1f : 0f) - a0) * af);
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            public void onAnimationEnd(android.animation.Animator x) {
                setH(s, to);
                s.setAlpha(v ? 1f : 0f);
            }
        });
        anim = va;
        va.start();
        if (v && scrollTop) {
            ListView l = list(s);
            if (l != null) l.smoothScrollToPositionFromTop(0, 0, 300);
        }
    }

    static void render() {
        renderMini();
        for (Strip s : all()) s.rebuild();
    }

    static void renderMini() {
        LinearLayout mini = miniRef.get();
        if (mini == null) return;
        if (Store.self == null) {
            mini.setVisibility(View.INVISIBLE);
            return;
        }
        mini.setVisibility(View.VISIBLE);
        List<JSONObject> list = Store.miniList();
        StringBuilder sb = new StringBuilder();
        for (JSONObject e : list) sb.append(e.optString("key")).append(e.optString("avatarUrl")).append(e.optBoolean("unseen") ? 1 : 0).append(',');
        int nNew = Store.unseenCount();
        sb.append('|').append(nNew).append('|').append(T.dark).append(T.accent);
        String sig = sb.toString();
        if (!sig.equals(miniSig)) {
            miniSig = sig;
            mini.removeAllViews();
            Context c = mini.getContext();
            if (list.isEmpty()) {
                FrameLayout add = new FrameLayout(c);
                add.setBackground(new Ring.GradCircle());
                ImageView pl = new ImageView(c);
                pl.setImageDrawable(Ic.get("st.stories.plus", 12, 0xffffffff));
                add.addView(pl, W.flp(T.dp(12), T.dp(12), Gravity.CENTER));
                mini.addView(add, W.lp(T.dp(28), T.dp(28)));
                mini.addView(label(c, "Add yours"));
                mini.setContentDescription("Stories");
            } else {
                LinearLayout stack = W.row(c);
                for (int i = 0; i < list.size(); i++) {
                    Ring.MiniRing r = new Ring.MiniRing(c, list.get(i));
                    LinearLayout.LayoutParams p = W.lp(Math.round(T.dpf(31)), T.dp(36));
                    if (i > 0) p.leftMargin = -T.dp(9);
                    stack.addView(r, p);
                }
                mini.addView(stack);
                mini.addView(label(c, nNew > 0 ? nNew + " new" : "Tap to view"));
            }
        }
        if (!list.isEmpty()) {
            mini.setContentDescription(open ? "Hide stories" : "Show stories");
            View st = mini.getChildAt(0);
            if (st instanceof LinearLayout) for (int i = 0; i < ((LinearLayout) st).getChildCount(); i++) ((LinearLayout) st).getChildAt(i).setAlpha(open ? .55f : 1f);
        }
    }

    static View label(Context c, String sub) {
        LinearLayout l = W.col(c);
        TextView b = W.text(c, "Stories", 14, 600, T.text);
        T.lh(b, 16.1f);
        TextView s = W.text(c, sub, 11, 500, T.accent);
        T.lh(s, 12.65f);
        l.addView(b);
        l.addView(s);
        LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
        p.leftMargin = T.dp(8);
        l.setLayoutParams(p);
        return l;
    }

    static final class Strip extends FrameLayout {
        final HorizontalScrollView hs;
        final LinearLayout row;
        String sig;
        boolean pinned;
        WeakReference<Home.Tab> tab = new WeakReference<>(null);

        Strip(Context c) {
            super(c);
            setContentDescription("Stories");
            hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            row = W.row(c);
            row.setGravity(Gravity.TOP);
            row.setPadding(T.dp(8), T.dp(6), T.dp(8), T.dp(4));
            hs.addView(row, new FrameLayout.LayoutParams(W.WC, T.dp(96)));
            FrameLayout.LayoutParams hp = W.flp(W.MP, T.dp(96), Gravity.TOP);
            hp.topMargin = T.dp(8);
            addView(hs, hp);
        }

        void rebuild() {
            if (Store.self == null) return;
            StringBuilder sb = new StringBuilder();
            sb.append(Store.count(Store.self)).append(Store.self.optString("avatarUrl")).append('|');
            for (JSONObject e : Store.feed) sb.append(e.optString("key")).append(e.optBoolean("unseen")).append(e.optBoolean("hidden")).append(Store.count(e)).append(e.optString("avatarUrl")).append(e.optBoolean("close")).append(',');
            sb.append(T.dark).append(T.accent);
            String s = sb.toString();
            if (s.equals(sig)) return;
            sig = s;
            row.removeAllViews();
            row.addView(item(Store.self, true));
            for (JSONObject e : Store.feed) {
                View it = item(e, false);
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) it.getLayoutParams();
                p.leftMargin = T.dp(4);
                row.addView(it);
            }
        }

        View item(JSONObject e, boolean isSelf) {
            Context c = getContext();
            LinearLayout it = W.col(c);
            it.setGravity(Gravity.CENTER_HORIZONTAL);
            it.setLayoutParams(W.lp(T.dp(72), T.dp(86)));
            it.setClipChildren(false);
            if (e.optBoolean("hidden")) it.setAlpha(.5f);
            FrameLayout box = new FrameLayout(c);
            box.setClipChildren(false);
            Ring.RingAv ring = new Ring.RingAv(c, e, 64, 56, 14);
            box.addView(ring, new FrameLayout.LayoutParams(T.dp(64), T.dp(64)));
            if (isSelf) {
                FrameLayout pl = new FrameLayout(c);
                pl.setBackground(W.oval(T.bg));
                FrameLayout in = new FrameLayout(c);
                in.setBackground(W.oval(T.accent));
                ImageView ic = new ImageView(c);
                ic.setImageDrawable(Ic.get("st.stories.plus", 14, T.accentFg));
                in.addView(ic, W.flp(T.dp(14), T.dp(14), Gravity.CENTER));
                pl.addView(in, W.flp(T.dp(22), T.dp(22), Gravity.CENTER));
                FrameLayout.LayoutParams pp = W.flp(T.dp(28), T.dp(28), Gravity.BOTTOM | Gravity.END);
                pp.rightMargin = -T.dp(4);
                pp.bottomMargin = -T.dp(4);
                box.addView(pl, pp);
                pl.setOnClickListener(v -> Editor.compose(MainActivity.cur, null));
            }
            it.addView(box, W.lp(T.dp(64), T.dp(64)));
            TextView nm = W.text(c, isSelf ? "My story" : Store.name(e), 12, 400, T.textSecondary);
            W.ellipsize(nm);
            nm.setMaxWidth(T.dp(70));
            nm.setGravity(Gravity.CENTER);
            T.lh(nm, 18);
            it.addView(nm, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
            W.press(it, .94f);
            it.setOnClickListener(v -> {
                MainActivity a = MainActivity.cur;
                if (a == null) return;
                if (isSelf && Store.count(e) == 0) {
                    Editor.compose(a, null);
                    return;
                }
                openViewer(a, e.optString("key"), ring);
            });
            return it;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Store.maybeRefresh();
        }
    }

    public static void profileRing(FrameLayout avw, View av, String uid, int sizeDp) {
        if (uid == null || uid.isEmpty()) return;
        App.bg(() -> {
            Http.Resp r = Http.get("/stories/user/" + uid);
            if (!r.ok() || !r.obj().optBoolean("active")) return;
            boolean unseen = r.obj().optBoolean("unseen");
            App.ui(() -> {
                if (!avw.isAttachedToWindow() && avw.getParent() == null) return;
                View ring = new View(avw.getContext()) {
                    final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                    @Override
                    protected void onDraw(android.graphics.Canvas c) {
                        float cx = getWidth() / 2f, cy = getHeight() / 2f, R = Math.min(cx, cy);
                        if (unseen) p.setShader(Ring.conicNew(cx, cy));
                        else {
                            p.setShader(null);
                            p.setColor(T.mixA(T.text, .26f));
                        }
                        c.drawCircle(cx, cy, R, p);
                        p.setShader(null);
                        p.setColor(T.surface);
                        c.drawCircle(cx, cy, R - T.dpf(3), p);
                    }
                };
                int pad = T.dp(6);
                FrameLayout.LayoutParams lp = W.flp(T.dp(sizeDp) + pad * 2, T.dp(sizeDp) + pad * 2, Gravity.TOP | Gravity.START);
                lp.leftMargin = -pad;
                lp.topMargin = -pad;
                avw.setClipChildren(false);
                if (avw.getParent() instanceof ViewGroup) ((ViewGroup) avw.getParent()).setClipChildren(false);
                avw.addView(ring, 0, lp);
                av.setContentDescription("View story");
                av.setOnClickListener(v -> openUser(MainActivity.cur, uid, av));
            });
        });
    }

    public static View posts(Context c, String uid) {
        LinearLayout card = W.col(c);
        card.setBackground(W.round(T.surface, 16));
        card.setPadding(T.dp(16), T.dp(12), T.dp(16), T.dp(16));
        card.setVisibility(View.GONE);
        TextView h = W.text(c, "Stories", 14, 600, T.text);
        card.addView(h, W.lpm(W.WC, W.WC, 0, 0, 0, 8));
        App.bg(() -> {
            Http.Resp r = Http.get("/stories/user/" + uid);
            if (!r.ok()) return;
            JSONObject j = r.obj();
            JSONArray st = j.optJSONArray("stories");
            List<JSONObject> pinned = new ArrayList<>();
            if (st != null) for (int i = 0; i < st.length(); i++) if (st.optJSONObject(i).optBoolean("pinned")) pinned.add(st.optJSONObject(i));
            if (pinned.isEmpty()) return;
            App.ui(() -> {
                card.addView(Sheets.grid(c, pinned, false, (k, cell) -> {
                    try {
                        JSONObject u = j.optJSONObject("user");
                        JSONObject en = u == null ? new JSONObject() : new JSONObject(u.toString());
                        en.put("key", uid).put("stories", new JSONArray(pinned)).put("self", uid.equals(Session.userId()));
                        List<JSONObject> l = new ArrayList<>();
                        l.add(en);
                        Viewer.open(MainActivity.cur, l, 0, cell, k);
                    } catch (Exception e) {
                    }
                }));
                card.setVisibility(View.VISIBLE);
            });
        });
        return card;
    }
}
