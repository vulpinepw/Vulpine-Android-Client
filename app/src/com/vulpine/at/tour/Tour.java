package com.vulpine.at.tour;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Pin;
import com.vulpine.at.Prefs;
import com.vulpine.at.Sync;
import com.vulpine.at.home.Home;
import com.vulpine.at.settings.S;
import com.vulpine.at.settings.Settings;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class Tour {
    static final String[][] PRESETS = {{"ember", "Ember", "dark", "#F97316", "", "1"}, {"midnight", "Midnight", "dark", "#3B82F6", "#0f172a", "1"}, {"forest", "Forest", "dark", "#22C55E", "#0f1f17", "1"}, {"sakura", "Sakura", "light", "#EC4899", "#fdf2f8", "1"}, {"ocean", "Ocean", "light", "#06B6D4", "#ecfeff", "1"}, {"mono", "Mono", "system", "#c3c6c9", "", "0"}};
    static final String[][] THEMES = {{"dark", "Dark"}, {"light", "Light"}, {"system", "System"}};
    static final float[] SIZES = {.9f, 1f, 1.15f, 1.3f};
    static final String[] SIZEL = {"Small", "Default", "Large", "Larger"};
    static final PathInterpolator MOVE = new PathInterpolator(.2f, .9f, .25f, 1f);
    static String shownFor;
    static Tour cur;

    final Home h;
    final MainActivity a;
    FrameLayout root;
    LinearLayout card;
    Spot spot;
    ImageView hand;
    final List<String[]> list = new ArrayList<>();
    int idx;
    boolean busy;
    String dTheme, dPreset;
    float dSize = 1;
    FrameLayout prevBox;

    Tour(Home h) {
        this.h = h;
        this.a = h.a;
    }

    public static void maybeStart(Home h) {
        if (h == null) return;
        App.H.postDelayed(() -> check(h), 600);
    }

    static void check(Home h) {
        if (Home.I != h || h.a == null) return;
        String uid = Session.userId();
        if (uid.isEmpty() || cur != null) return;
        JSONObject st = Session.settings();
        String key = "tourPending." + uid;
        if (Sync.newAccount) App.prefs.edit().putBoolean(key, true).apply();
        boolean pend = App.prefs.getBoolean(key, false);
        if (st.optBoolean("tourDone")) return;
        if (!(Sync.newAccount || st.optBoolean("tourPending") || pend)) {
            App.H.postDelayed(() -> check(h), 2000);
            return;
        }
        if (uid.equals(shownFor)) return;
        if (blocked(h)) {
            App.H.postDelayed(() -> check(h), 700);
            return;
        }
        shownFor = uid;
        App.H.postDelayed(() -> {
            if (blocked(h)) {
                shownFor = null;
                check(h);
                return;
            }
            open(h);
        }, 600);
    }

    static boolean blocked(Home h) {
        if (Pin.fake) return true;
        if (!Pin.unlocked && Pin.hasPin()) return true;
        if (Ov.any()) return true;
        return h.a.top() != h;
    }

    public static void open(Home h) {
        if (h == null || cur != null || h.a == null) return;
        if (h.a.top() != h) h.a.popTo(h);
        h.select("chats", false);
        Tour t = new Tour(h);
        cur = t;
        Object sc = Session.settings().opt("textScale");
        t.dSize = sc instanceof Number ? ((Number) sc).floatValue() : 1f;
        App.H.postDelayed(t::start, 350);
    }

    static View find(View v, java.util.function.Predicate<View> p) {
        if (v == null || v.getVisibility() != View.VISIBLE) return null;
        if (p.test(v)) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View r = find(((ViewGroup) v).getChildAt(i), p);
            if (r != null) return r;
        }
        return null;
    }

    static boolean visible(View v) {
        if (v == null || !v.isShown() || v.getWidth() < T.dp(4) || v.getHeight() < T.dp(4)) return false;
        int[] l = new int[2];
        v.getLocationInWindow(l);
        return l[1] + v.getHeight() > 0 && l[1] < v.getRootView().getHeight();
    }

    View homeRoot() {
        return ((com.vulpine.at.ui.Screen) h).root;
    }

    ListView chatList() {
        return (ListView) find(homeRoot(), x -> x instanceof ListView && x.isShown());
    }

    View target(String id) {
        View r = homeRoot();
        switch (id) {
            case "chats": {
                ListView lv = chatList();
                if (lv == null) return null;
                int hc = lv.getHeaderViewsCount();
                for (int i = 0; i < lv.getChildCount(); i++) {
                    int pos = lv.getFirstVisiblePosition() + i;
                    if (pos >= hc && pos < lv.getCount() - lv.getFooterViewsCount() && visible(lv.getChildAt(i))) return lv.getChildAt(i);
                }
                return lv;
            }
            case "stories": {
                FrameLayout hl = h.headerLeft;
                if (hl == null || !hl.isShown()) return null;
                View v = find(hl, x -> x != hl && visible(x) && (x.isClickable() || x.hasOnClickListeners()));
                if (v == null && hl.getChildCount() > 0 && visible(hl.getChildAt(0))) v = hl.getChildAt(0);
                return v;
            }
            case "search":
                return chatList();
            case "new":
                return find(r, x -> ("New chat".contentEquals(String.valueOf(x.getContentDescription())) || "New group".contentEquals(String.valueOf(x.getContentDescription()))) && visible(x));
            case "discover":
                return find(r, x -> "Discover groups".contentEquals(String.valueOf(x.getContentDescription())) && visible(x));
            case "settings":
                return find(r, x -> "Settings".contentEquals(String.valueOf(x.getContentDescription())) && visible(x) && x.getHeight() < T.dp(80));
        }
        return null;
    }

    void start() {
        if (cur != this) return;
        String[][] coach = {
                {"hi", "c", "Welcome to Vulpine", "Your chats are end-to-end encrypted. Let's take a 30-second look around.", "Show me around"},
                {"chats", "", "Your chats", "All your conversations live here. Tap one to open it.", ""},
                {"stories", "", "Stories", "See what your contacts share, or post your own story. It disappears after 24 hours.", ""},
                {"search", "", "Search", "Pull the chat list down to show stories, pull again to search everything.", ""},
                {"new", "", "Start something", "Start a new chat or create a group.", ""},
                {"discover", "", "Discover", "Find and join public groups.", ""},
                {"settings", "", "Settings", "Privacy, notifications, themes and more - all in Settings.", ""},
                {"looks", "b", "", "", ""},
                {"done", "c", "You're all set!", "Enjoy Vulpine. You can replay this tour in Settings › Appearance.", "Start chatting"}};
        for (String[] s : coach) if (!s[1].isEmpty() || target(s[0]) != null) list.add(s);
        Context c = a;
        root = new FrameLayout(c);
        root.setClickable(true);
        spot = new Spot(c);
        root.addView(spot, new FrameLayout.LayoutParams(W.MP, W.MP));
        spot.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                String[] s = list.get(idx);
                if (!s[1].equals("b")) go(1);
            }
            return true;
        });
        card = W.col(c);
        card.setClickable(true);
        root.addView(card, W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START));
        root.setElevation(T.dpf(40));
        a.host.addView(root, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.setAlpha(0);
        root.animate().alpha(1).setDuration(250).start();
        Ov.push(root, this::back);
        draw();
    }

    void back() {
        if (idx > 0) go(-1);
        else finish(false);
    }

    void go(int d) {
        if (busy) return;
        int n = idx + d;
        if (n < 0) return;
        if (n >= list.size()) {
            finish(true);
            return;
        }
        idx = n;
        draw();
    }

    boolean center() {
        return list.get(idx)[1].equals("c");
    }

    boolean big() {
        return list.get(idx)[1].equals("b");
    }

    void draw() {
        Context c = a;
        String[] s = list.get(idx);
        card.removeAllViews();
        if (hand != null) {
            root.removeView(hand);
            hand = null;
        }
        boolean cen = center(), bg = big();
        card.setBackground(new CardBg(cen || bg ? 0 : 1));
        card.setElevation(T.dpf(18));
        int w = Math.min(T.dp(cen ? 340 : bg ? 760 : 320), root.getWidth() - T.dp(cen ? 32 : 24));
        if (w <= 0) w = T.dp(320);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        lp.width = w;
        card.setLayoutParams(lp);
        if (bg) {
            card.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
            looks(c);
        } else {
            card.setPadding(T.dp(cen ? 18 : 16), T.dp(cen ? 22 : 16), T.dp(cen ? 18 : 16), T.dp(cen ? 14 : 12));
            FrameLayout head = new FrameLayout(c);
            TextView t = W.text(c, s[2], cen ? 20 : 16, 700, T.text);
            if (cen) t.setGravity(Gravity.CENTER_HORIZONTAL);
            else t.setPadding(0, 0, T.dp(40), 0);
            head.addView(t, new FrameLayout.LayoutParams(W.MP, W.WC));
            card.addView(head);
            TextView p = W.text(c, s[3], 14, 400, T.textSecondary);
            p.setLineSpacing(0, 1.45f);
            if (cen) p.setGravity(Gravity.CENTER_HORIZONTAL);
            card.addView(p, W.lpm(W.MP, W.WC, 0, 4, 0, 0));
            LinearLayout acts = W.row(c);
            if (idx > 0 && !cen) acts.addView(btn(c, "Back", false, v -> go(-1)));
            String nx = !s[4].isEmpty() ? s[4] : idx == list.size() - 1 ? "Done" : "Next";
            TextView next = btn(c, nx, true, v -> go(1));
            LinearLayout.LayoutParams np = W.lp(W.WC, T.dp(34));
            np.leftMargin = acts.getChildCount() > 0 ? T.dp(6) : 0;
            acts.addView(next, np);
            if (cen) {
                LinearLayout col = W.col(c);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.addView(acts);
                col.addView(dots(c), W.lpm(W.WC, W.WC, 0, 8, 0, 0));
                card.addView(col, W.lpm(W.MP, W.WC, 0, 14, 0, 0));
            } else {
                LinearLayout row = W.row(c);
                row.addView(dots(c), W.lp(0, W.WC, 1));
                row.addView(acts);
                card.addView(row, W.lpm(W.MP, W.WC, 0, 14, 0, 0));
            }
            if (s[0].equals("search")) {
                hand = new ImageView(c);
                hand.setImageDrawable(Svg.stroke(34, 0xffffffff, 1.8f, "M9 11V5.5a1.5 1.5 0 0 1 3 0V10", "M12 9.5a1.5 1.5 0 0 1 3 0V11", "M15 10.5a1.5 1.5 0 0 1 3 0V14a6 6 0 0 1-6 6h-1a6 6 0 0 1-4.8-2.4L4 14.5a1.5 1.5 0 0 1 2.3-1.9L9 15"));
                hand.setElevation(T.dpf(4));
                root.addView(hand, W.flp(T.dp(34), T.dp(34), Gravity.TOP | Gravity.START));
            }
        }
        if (idx < list.size() - 1) {
            TextView skip = W.text(c, "Skip", 13, 500, T.textMuted);
            skip.setPadding(T.dp(8), T.dp(4), T.dp(8), T.dp(4));
            skip.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 999));
            skip.setOnClickListener(v -> finish(false));
            card.post(() -> {
                if (skip.getParent() != null) ((ViewGroup) skip.getParent()).removeView(skip);
                FrameLayout.LayoutParams sp = W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START);
                root.addView(skip, sp);
                skip.setTag("skip");
                placeSkip(skip);
            });
        }
        for (int i = root.getChildCount() - 1; i >= 0; i--) if ("skip".equals(root.getChildAt(i).getTag())) root.removeViewAt(i);
        card.setAlpha(0);
        card.setTranslationY(T.dp(6));
        card.setScaleX(.97f);
        card.setScaleY(.97f);
        card.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(320).setInterpolator(MOVE).start();
        root.post(this::place);
    }

    void placeSkip(TextView skip) {
        skip.setElevation(T.dpf(19));
        skip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) skip.getLayoutParams();
        FrameLayout.LayoutParams cl = (FrameLayout.LayoutParams) card.getLayoutParams();
        lp.leftMargin = cl.leftMargin + card.getWidth() - skip.getMeasuredWidth() - T.dp(10);
        lp.topMargin = cl.topMargin + T.dp(10);
        skip.setLayoutParams(lp);
    }

    TextView btn(Context c, String label, boolean primary, View.OnClickListener on) {
        TextView b = S.btn(c, label, primary ? S.PRIMARY : S.GHOST, 34, 14, on);
        b.setPadding(T.dp(16), 0, T.dp(16), 0);
        return b;
    }

    View dots(Context c) {
        LinearLayout d = W.row(c);
        for (int i = 0; i < list.size(); i++) {
            View v = new View(c);
            boolean on = i == idx;
            v.setBackground(W.round(on ? T.accent : T.mixA(T.text, .2f), 3));
            LinearLayout.LayoutParams p = W.lp(T.dp(on ? 16 : 6), T.dp(6));
            if (i > 0) p.leftMargin = T.dp(4);
            d.addView(v, p);
        }
        return d;
    }

    void place() {
        if (root == null) return;
        String[] s = list.get(idx);
        int W0 = root.getWidth(), H0 = root.getHeight();
        card.measure(View.MeasureSpec.makeMeasureSpec(((FrameLayout.LayoutParams) card.getLayoutParams()).width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H0 - T.dp(32), View.MeasureSpec.AT_MOST));
        int cw = card.getMeasuredWidth(), ch = card.getMeasuredHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        if (center() || big()) {
            spot.to(new RectF(W0 / 2f, H0 / 2f, W0 / 2f, H0 / 2f), false);
            lp.leftMargin = (W0 - cw) / 2;
            lp.topMargin = Math.max(T.dp(16), (H0 - ch) / 2);
            card.setLayoutParams(lp);
            ((CardBg) card.getBackground()).arrow = 0;
            fixSkip();
            return;
        }
        View t = target(s[0]);
        if (t == null) return;
        int[] l = new int[2], rl = new int[2];
        t.getLocationInWindow(l);
        root.getLocationInWindow(rl);
        float pad = T.dpf(6);
        float L = Math.max(T.dpf(4), l[0] - rl[0] - pad), Tp = Math.max(T.dpf(4), l[1] - rl[1] - pad);
        float Wd = Math.min(W0 - T.dpf(8), t.getWidth() + pad * 2), Ht = Math.min(H0 - T.dpf(8), t.getHeight() + pad * 2);
        if (s[0].equals("search")) Ht = Math.min(Ht, T.dpf(200));
        spot.to(new RectF(L, Tp, L + Wd, Tp + Ht), true);
        float gap = T.dpf(14);
        boolean below = Tp + Ht + gap + ch < H0 - T.dpf(12);
        float top = below ? Tp + Ht + gap : Math.max(T.dpf(12), Tp - gap - ch);
        if (!below && top + ch > Tp - T.dpf(4)) top = Math.max(T.dpf(12), Math.min(H0 - ch - T.dpf(12), Tp + Ht / 2 - ch / 2f));
        float left = Math.max(T.dpf(12), Math.min(W0 - cw - T.dpf(12), L + Wd / 2 - cw / 2f));
        CardBg cb = (CardBg) card.getBackground();
        cb.arrow = below ? 1 : -1;
        cb.ax = Math.max(T.dpf(18), Math.min(cw - T.dpf(18), L + Wd / 2 - left));
        card.invalidate();
        animMargins(Math.round(left), Math.round(top));
        if (hand != null) {
            FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) hand.getLayoutParams();
            hp.leftMargin = Math.round(L + Wd / 2 - T.dp(17));
            hp.topMargin = Math.round(Tp + Math.min(Ht, T.dp(120)) / 2 - T.dp(17));
            hand.setLayoutParams(hp);
            ValueAnimator va = ValueAnimator.ofFloat(0, 1);
            va.setDuration(1800);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new PathInterpolator(.4f, 0f, .2f, 1f));
            va.addUpdateListener(x -> {
                float f = (float) x.getAnimatedValue();
                hand.setTranslationY(T.dp(60) * (f < .7f ? f / .7f : 1f));
                hand.setAlpha(f < .1f ? f / .1f : f > .8f ? Math.max(0, (1 - f) / .2f) : 1f);
            });
            va.start();
        }
    }

    void animMargins(int left, int top) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        int l0 = lp.leftMargin, t0 = lp.topMargin;
        if (l0 == 0 && t0 == 0) {
            lp.leftMargin = left;
            lp.topMargin = top;
            card.setLayoutParams(lp);
            fixSkip();
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.setDuration(420);
        va.setInterpolator(MOVE);
        va.addUpdateListener(x -> {
            float f = (float) x.getAnimatedValue();
            lp.leftMargin = Math.round(l0 + (left - l0) * f);
            lp.topMargin = Math.round(t0 + (top - t0) * f);
            card.setLayoutParams(lp);
            fixSkip();
        });
        va.start();
    }

    void fixSkip() {
        for (int i = 0; i < root.getChildCount(); i++) if ("skip".equals(root.getChildAt(i).getTag())) placeSkip((TextView) root.getChildAt(i));
    }

    void looks(Context c) {
        TextView t = W.text(c, "Make it yours", 16, 700, T.text);
        t.setPadding(0, 0, T.dp(40), 0);
        card.addView(t);
        TextView p = W.text(c, "Pick a theme and a look. You can fine-tune everything later in Settings.", 14, 400, T.textSecondary);
        p.setLineSpacing(0, 1.45f);
        card.addView(p, W.lpm(W.MP, W.WC, 0, 4, 0, 0));
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout inner = W.col(c);
        sv.addView(inner);
        prevBox = new FrameLayout(c);
        inner.addView(prevBox, W.lpm(W.MP, T.dp(200), 0, 12, 0, 0));
        refreshPreview();
        String curTheme = dTheme != null ? dTheme : Prefs.theme();
        inner.addView(sec(c, "Theme"), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        LinearLayout tr = W.row(c);
        for (int i = 0; i < 3; i++) {
            String[] th = THEMES[i];
            LinearLayout b = tile(c, th[0].equals(curTheme));
            View sw = new ThemeSw(c, th[0]);
            b.addView(sw, W.lp(W.MP, T.dp(60)));
            TextView l = W.text(c, th[1], 14, 500, T.text);
            l.setGravity(Gravity.CENTER);
            b.addView(l, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
            b.setOnClickListener(v -> {
                dTheme = th[0];
                draw();
            });
            LinearLayout.LayoutParams lp = W.lp(0, W.WC, 1);
            if (i > 0) lp.leftMargin = T.dp(12);
            tr.addView(b, lp);
        }
        inner.addView(tr, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        inner.addView(sec(c, "Look"), W.lpm(W.MP, W.WC, 0, 14, 0, 0));
        S.Grid g = new S.Grid(c, 90, 8, 8);
        for (String[] pr : PRESETS) {
            LinearLayout b = tile(c, pr[0].equals(dPreset));
            b.addView(new LookSw(c, pr), W.lp(W.MP, T.dp(52)));
            TextView l = W.text(c, pr[1], 14, 500, T.text);
            l.setGravity(Gravity.CENTER);
            b.addView(l, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
            b.setOnClickListener(v -> {
                dPreset = pr[0];
                dTheme = pr[2];
                draw();
            });
            g.addView(b);
        }
        inner.addView(g, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        inner.addView(sec(c, "Message text size"), W.lpm(W.MP, W.WC, 0, 14, 0, 0));
        int sel = 1;
        for (int i = 0; i < 4; i++) if (Math.abs(dSize - SIZES[i]) < .01) sel = i;
        inner.addView(S.seg(c, SIZEL, sel, 420, i -> {
            dSize = SIZES[i];
            refreshPreview();
        }), W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        card.addView(sv, W.lp(W.MP, W.WC));
        sv.getLayoutParams().height = W.WC;
        LinearLayout row = W.row(c);
        row.addView(dots(c), W.lp(0, W.WC, 1));
        row.addView(btn(c, "Back", false, v -> go(-1)));
        TextView ap = btn(c, "Apply", true, v -> go(1));
        LinearLayout.LayoutParams apl = W.lp(W.WC, T.dp(34));
        apl.leftMargin = T.dp(6);
        row.addView(ap, apl);
        card.addView(row, W.lpm(W.MP, W.WC, 0, 14, 0, 0));
        card.post(() -> {
            int lim = root.getHeight() - T.dp(32) - (card.getHeight() - sv.getHeight());
            if (sv.getHeight() > lim && lim > 0) {
                sv.getLayoutParams().height = lim;
                sv.requestLayout();
            }
        });
    }

    TextView sec(Context c, String s) {
        TextView t = W.text(c, s.toUpperCase(), 13, 600, T.textMuted);
        t.setLetterSpacing(.04f);
        return t;
    }

    LinearLayout tile(Context c, boolean on) {
        LinearLayout b = W.col(c);
        b.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        b.setBackground(on ? W.round(T.surface, 12, T.accent, 2) : W.round(T.surface, 12));
        return b;
    }

    String[] preset() {
        if (dPreset == null) return null;
        for (String[] p : PRESETS) if (p[0].equals(dPreset)) return p;
        return null;
    }

    static boolean resolvedDark(String t) {
        if ("system".equals(t)) return Prefs.systemDark();
        return !"light".equals(t);
    }

    void refreshPreview() {
        if (prevBox == null) return;
        prevBox.removeAllViews();
        Context c = a;
        String[] p = preset();
        String th = dTheme != null ? dTheme : p != null ? p[2] : Prefs.theme();
        boolean dark = resolvedDark(th);
        JSONObject s = Session.s == null ? new JSONObject() : Session.s;
        String ac = p != null ? p[3] : s.optString("accentColor", "").startsWith("#") ? s.optString("accentColor") : "#F97316";
        String wall = p != null ? p[4] : s.optString("chatWallpaper", "").startsWith("#") ? s.optString("chatWallpaper") : "";
        int bg = dark ? 0xff121212 : 0xfff1f0ee, surf = dark ? 0xff1e1e1e : 0xfffaf9f7, tx = dark ? 0xfff5f5f5 : 0xff26262a, mu = dark ? 0xffa0a0a0 : 0xff74737c, in = dark ? 0xff262626 : 0xffffffff;
        int acc = com.vulpine.at.ui.Css.color(ac) == null ? 0xfff97316 : com.vulpine.at.ui.Css.color(ac);
        Integer wc = wall.isEmpty() ? null : com.vulpine.at.ui.Css.color(wall);
        LinearLayout box = W.col(c);
        box.setBackground(W.round(wc != null ? wc : bg, 16));
        box.setClipToOutline(true);
        LinearLayout head = W.row(c);
        head.setPadding(T.dp(6), T.dp(6), T.dp(12), T.dp(6));
        head.setBackground(W.round(T.mixA(surf, .85f), 999));
        TextView av = W.text(c, "F", 13, 700, 0xffffffff);
        av.setGravity(Gravity.CENTER);
        av.setBackground(W.oval(acc));
        head.addView(av, W.lp(T.dp(30), T.dp(30)));
        LinearLayout nm = W.col(c);
        nm.addView(W.text(c, "Foxy", 13, 700, tx));
        nm.addView(W.text(c, "online", 11, 400, mu));
        head.addView(nm, W.lpm(W.WC, W.WC, 10, 0, 0, 0));
        LinearLayout.LayoutParams hp = W.lpm(W.WC, W.WC, 10, 10, 10, 0);
        box.addView(head, hp);
        LinearLayout body = W.col(c);
        body.setGravity(Gravity.BOTTOM);
        body.setPadding(T.dp(10), T.dp(10), T.dp(10), T.dp(10));
        String[][] msgs = {{"Hey! Welcome to Vulpine", "in"}, {"Thanks! Looks great", "me"}, {"Everything here is end-to-end encrypted.", "in"}};
        for (String[] m : msgs) {
            TextView b = W.text(c, m[0], 13 * dSize, 400, tx);
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13 * dSize);
            b.setLineSpacing(0, 1.35f);
            b.setPadding(T.dp(11), T.dp(7), T.dp(11), T.dp(7));
            boolean me = m[1].equals("me");
            b.setBackground(W.round(me ? T.mix(in, acc, dark ? .24f : .16f) : in, 16));
            LinearLayout.LayoutParams bp = W.lp(W.WC, W.WC);
            bp.gravity = me ? Gravity.END : Gravity.START;
            if (body.getChildCount() > 0) bp.topMargin = T.dp(6);
            body.addView(b, bp);
        }
        box.addView(body, W.lp(W.MP, 0, 1));
        LinearLayout comp = W.row(c);
        comp.setPadding(T.dp(14), T.dp(8), T.dp(8), T.dp(8));
        comp.setBackground(W.round(surf, 999));
        comp.addView(W.text(c, "Message", 12, 400, mu), W.lp(0, W.WC, 1));
        View send = new View(c);
        send.setBackground(W.oval(acc));
        comp.addView(send, W.lp(T.dp(26), T.dp(26)));
        box.addView(comp, W.lpm(W.MP, W.WC, 10, 0, 10, 10));
        prevBox.addView(box, new FrameLayout.LayoutParams(W.MP, W.MP));
    }

    void finish(boolean apply) {
        if (busy) return;
        busy = true;
        JSONObject st = Session.settings();
        String[] p = preset();
        boolean rer = false;
        try {
            st.put("tourDone", true);
            st.put("tourPending", false);
            if (apply) {
                if (dTheme != null && !dTheme.equals(Prefs.theme())) {
                    Session.s.put("theme", dTheme);
                    Prefs.setTheme(dTheme);
                    String th = dTheme;
                    App.bg(() -> Http.patch("/me", App.jo("theme", th)));
                    rer = true;
                }
                st.put("textScale", (double) dSize);
                if (p != null) {
                    st.put("bgIconsEnabled", p[5].equals("1"));
                    Session.s.put("accentColor", p[3]);
                    Session.s.put("chatWallpaper", p[4]);
                    Sync.patchMe(App.jo("accentColor", p[3], "chatWallpaper", p[4]), null, null);
                    rer = true;
                }
            }
        } catch (Exception e) {
        }
        Session.save();
        Sync.save();
        Sync.newAccount = false;
        App.prefs.edit().remove("tourPending." + Session.userId()).apply();
        boolean fr = rer;
        close(() -> {
            if (fr) {
                Settings.rerender(a, null);
                if (Home.I != null) Home.I.select("chats", false);
            }
        });
    }

    void close(Runnable after) {
        Ov.remove(root);
        FrameLayout r = root;
        root.animate().alpha(0).setDuration(240).withEndAction(() -> {
            a.host.removeView(r);
            cur = null;
            if (after != null) after.run();
        }).start();
    }

    class Spot extends View {
        RectF hole = new RectF(), from = new RectF(), to = new RectF();
        boolean ring;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        float pulse;
        ValueAnimator pa;

        Spot(Context c) {
            super(c);
            pa = ValueAnimator.ofFloat(0, 1);
            pa.setDuration(1600);
            pa.setRepeatCount(ValueAnimator.INFINITE);
            pa.addUpdateListener(x -> {
                pulse = (float) x.getAnimatedValue();
                if (ring) invalidate();
            });
            pa.start();
        }

        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            pa.cancel();
        }

        void to(RectF r, boolean withRing) {
            ring = withRing;
            from = new RectF(hole);
            to = r;
            if (hole.isEmpty() && hole.left == 0 && hole.top == 0) {
                hole = new RectF(r);
                invalidate();
                return;
            }
            ValueAnimator va = ValueAnimator.ofFloat(0, 1);
            va.setDuration(420);
            va.setInterpolator(MOVE);
            va.addUpdateListener(x -> {
                float f = (float) x.getAnimatedValue();
                hole = new RectF(from.left + (to.left - from.left) * f, from.top + (to.top - from.top) * f, from.right + (to.right - from.right) * f, from.bottom + (to.bottom - from.bottom) * f);
                invalidate();
            });
            va.start();
        }

        protected void onDraw(Canvas c) {
            path.reset();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            float r = T.dpf(16);
            if (hole.width() > 0 && hole.height() > 0) path.addRoundRect(hole, r, r, Path.Direction.CW);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x94000000);
            c.drawPath(path, p);
            if (ring && hole.width() > 0) {
                float s = 1 + .06f * pulse;
                c.save();
                c.scale(s, s, hole.centerX(), hole.centerY());
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(2));
                p.setColor(T.mixA(T.accent, .9f * (1 - pulse)));
                RectF o = new RectF(hole.left - T.dpf(4), hole.top - T.dpf(4), hole.right + T.dpf(4), hole.bottom + T.dpf(4));
                c.drawRoundRect(o, T.dpf(18), T.dpf(18), p);
                c.restore();
            }
        }
    }

    static class CardBg extends android.graphics.drawable.Drawable {
        int arrow;
        float ax;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        CardBg(int arrow) {
            this.arrow = arrow;
        }

        public void draw(Canvas c) {
            RectF r = new RectF(getBounds());
            p.setColor(T.surface);
            c.drawRoundRect(r, T.dpf(16), T.dpf(16), p);
            if (arrow != 0) {
                float x = ax > 0 ? ax : r.width() / 2;
                float y = arrow > 0 ? r.top + T.dpf(2) : r.bottom - T.dpf(2);
                c.save();
                c.translate(x, y);
                c.rotate(45);
                float h = T.dpf(8);
                c.drawRoundRect(-h, -h, h, h, T.dpf(3), T.dpf(3), p);
                c.restore();
            }
        }

        public void setAlpha(int a) {
        }

        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        public boolean getPadding(android.graphics.Rect pad) {
            return false;
        }
    }

    static class ThemeSw extends View {
        final String t;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        ThemeSw(Context c, String t) {
            super(c);
            this.t = t;
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = T.dpf(8);
            Path clip = new Path();
            clip.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            if (t.equals("system")) {
                p.setColor(0xff1e1e1e);
                c.drawRect(0, 0, w, h, p);
                Path tri = new Path();
                tri.moveTo(w, 0);
                tri.lineTo(w, h);
                tri.lineTo(0, h);
                tri.close();
                p.setColor(0xfffaf9f7);
                c.drawPath(tri, p);
            } else {
                p.setColor(t.equals("light") ? 0xfffaf9f7 : 0xff1e1e1e);
                c.drawRect(0, 0, w, h, p);
            }
            p.setColor(t.equals("light") ? 0xffe7e5e1 : 0xff2e2e2e);
            c.drawRoundRect(T.dpf(10), T.dpf(14), T.dpf(10) + w * .55f, T.dpf(26), T.dpf(6), T.dpf(6), p);
            p.setColor(T.accent);
            c.drawRoundRect(w - T.dpf(10) - w * .45f, T.dpf(34), w - T.dpf(10), T.dpf(46), T.dpf(6), T.dpf(6), p);
            c.restore();
        }
    }

    static class LookSw extends View {
        final String[] pr;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        LookSw(Context c, String[] pr) {
            super(c);
            this.pr = pr;
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = T.dpf(8);
            boolean dark = resolvedDark(pr[2]);
            int bg = dark ? 0xff121212 : 0xfff1f0ee, in = dark ? 0xff262626 : 0xffffffff;
            Integer wc = pr[4].isEmpty() ? null : com.vulpine.at.ui.Css.color(pr[4]);
            int acc = com.vulpine.at.ui.Css.color(pr[3]);
            Path clip = new Path();
            clip.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            p.setColor(wc != null ? wc : bg);
            c.drawRect(0, 0, w, h, p);
            p.setColor(in);
            c.drawRoundRect(T.dpf(8), T.dpf(12), T.dpf(8) + w * .55f, T.dpf(24), T.dpf(6), T.dpf(6), p);
            p.setColor(T.mix(in, acc, .7f));
            c.drawRoundRect(w - T.dpf(8) - w * .45f, T.dpf(34), w - T.dpf(8), T.dpf(46), T.dpf(6), T.dpf(6), p);
            c.restore();
        }
    }
}
