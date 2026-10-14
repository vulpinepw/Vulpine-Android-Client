package com.vulpine.at.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Pin;
import com.vulpine.at.Sync;
import com.vulpine.at.chat.ChatScreen;
import com.vulpine.at.ui.BgView;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Session;
import org.json.JSONObject;

public class Home extends Screen {
    public static Home I;
    public static final String[] TABS = {"chats", "contacts", "settings", "profile"};
    static final String[] LABELS = {"Chats", "Contacts", "Settings", "Profile"};
    FrameLayout root, content, header, nav;
    BgView bg;
    View navPill, fade, fab;
    TextView[] badges = new TextView[4];
    LinearLayout[] navBtns = new LinearLayout[4];
    public String view = "chats";
    final java.util.Map<String, Tab> tabs = new java.util.HashMap<>();
    public int chatsBadge, contactsBadge;
    LinearLayout headerRight;
    public FrameLayout headerLeft;
    View blank;

    public abstract static class Tab {
        public Home h;
        public View v;

        public abstract View build(Context c);

        public void onShow() {
        }

        public void onHide() {
        }

        public void onInsets(int top, int bottom) {
        }

        public boolean onBack() {
            return false;
        }

        public void scrollTop() {
        }
    }

    public View build() {
        I = this;
        hideNav = false;
        Context c = a;
        root = new FrameLayout(c) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                return pagerIntercept(e) || super.onInterceptTouchEvent(e);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return pagerTouch(e) || super.onTouchEvent(e);
            }
        };
        bg = new BgView(c, false);
        root.setClipChildren(false);
        root.addView(bg, new FrameLayout.LayoutParams(W.MP, W.MP));
        blank = new View(c);
        blank.setBackgroundColor(T.bg);
        root.addView(blank, new FrameLayout.LayoutParams(W.MP, W.MP));
        content = new FrameLayout(c);
        root.addView(content, new FrameLayout.LayoutParams(W.MP, W.MP));
        buildHeader(c);
        fade = new View(c);
        root.addView(fade, W.flp(W.MP, T.dp(142), Gravity.BOTTOM));
        buildNav(c);
        buildFab(c);
        select("chats", false);
        Sync.on(syncL);
        Chats.I.on(chatL);
        App.bg(this::loadContactsBadge);
        com.vulpine.core.Rt.I.on(wsL);
        com.vulpine.at.tour.Tour.maybeStart(this);
        return root;
    }

    final Sync.L syncL = w -> {
        if ("me".equals(w) || "settings".equals(w)) {
            bg.invalidate();
            for (Tab t : tabs.values()) if (t instanceof ChatsTab) ((ChatsTab) t).refresh();
        }
        if ("unread".equals(w)) for (Tab t : tabs.values()) if (t instanceof ChatsTab) ((ChatsTab) t).refresh();
    };

    final Chats.Listener chatL = (ev, cid, d) -> App.ui(() -> {
        if ("new".equals(ev) && d instanceof Chats.Msg && !((Chats.Msg) d).mine()) {
            String active = Chats.I.activeCid;
            if (cid != null && !cid.equals(active) && !Sync.contains(Session.settings().optJSONArray("mutedChatIds"), cid)) {
                if (!"chats".equals(view)) {
                    chatsBadge++;
                    updateBadges();
                }
            }
        }
    });

    final com.vulpine.core.Rt.Listener wsL = m -> {
        String t = m.optString("type");
        if ("connection.update".equals(t)) {
            App.bg(this::loadContactsBadge);
            String st = m.optJSONObject("connection") == null ? "" : m.optJSONObject("connection").optString("status");
            if ("pending".equals(st)) App.ui(() -> com.vulpine.at.ui.Ov.toast("New contact request received"));
            else if ("accepted".equals(st)) App.ui(() -> com.vulpine.at.ui.Ov.toast("A contact request was accepted"));
        }
        if ("mention".equals(t)) App.ui(() -> {
            String cid = m.optString("conversationId");
            if (!cid.equals(Chats.I.activeCid) && !Sync.contains(Session.settings().optJSONArray("mutedChatIds"), cid)) {
                if (!"chats".equals(view)) {
                    chatsBadge++;
                    updateBadges();
                }
                if (Session.settings().optBoolean("toastEnabled", true)) Ov.toast(m.optString("fromDisplayName", "Someone") + " mentioned you");
            }
        });
    };

    void loadContactsBadge() {
        com.vulpine.core.Http.Resp r = com.vulpine.core.Http.get("/connections");
        if (!r.ok()) return;
        JSONObject o = r.obj();
        int n = o.optJSONArray("incoming") == null ? 0 : o.optJSONArray("incoming").length();
        App.ui(() -> {
            contactsBadge = n;
            updateBadges();
        });
    }

    FrameLayout updBtn;

    void buildHeader(Context c) {
        header = new FrameLayout(c);
        headerLeft = new FrameLayout(c);
        header.addView(headerLeft, W.flp(W.WC, W.MP, Gravity.START | Gravity.CENTER_VERTICAL));
        headerRight = W.row(c);
        headerRight.setGravity(Gravity.CENTER_VERTICAL);
        updBtn = new FrameLayout(c);
        ImageView ui = new ImageView(c);
        ui.setImageDrawable(Svg.stroke(20, T.text, 2, "M12 3v12", "M7 10l5 5 5-5", "M5 21h14"));
        ui.setScaleType(ImageView.ScaleType.CENTER);
        updBtn.addView(ui, new FrameLayout.LayoutParams(W.MP, W.MP));
        TextView dot = W.text(c, "1", 10, 700, 0xffffffff);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(W.round(T.danger, 8));
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(T.dp(16), T.dp(16), Gravity.TOP | Gravity.END);
        dp.topMargin = -T.dp(3);
        dp.rightMargin = -T.dp(3);
        updBtn.addView(dot, dp);
        updBtn.setClipChildren(false);
        updBtn.setContentDescription("Update app");
        updBtn.setOnClickListener(v -> com.vulpine.at.Updater.sheet(a));
        W.pressNow(updBtn, .88f);
        updBtn.setVisibility(com.vulpine.at.Updater.available() ? View.VISIBLE : View.GONE);
        headerRight.addView(updBtn, W.lp(T.dp(36), T.dp(36)));
        com.vulpine.at.Updater.onAvailable = () -> {
            if (updBtn != null) updBtn.setVisibility(com.vulpine.at.Updater.available() ? View.VISIBLE : View.GONE);
        };
        headerRight.addView(hbtn(c, "lock", "Lock app", 22, v -> Pin.lockNow()));
        headerRight.addView(hbtn(c, "discover", "Discover groups", 24, v -> Discover.open(a)));
        headerRight.addView(hbtn(c, "more", "More options", 22, this::moreMenu));
        FrameLayout.LayoutParams rp = W.flp(W.WC, T.dp(40), Gravity.END | Gravity.CENTER_VERTICAL);
        rp.topMargin = T.dp(4);
        header.addView(headerRight, rp);
        header.setPadding(T.dp(12), 0, T.dp(12), 0);
        root.addView(header, W.flp(W.MP, T.dp(44), Gravity.TOP));
        StoriesPill.attach(this, headerLeft);
    }

    View hbtn(Context c, String icon, String label, float size, View.OnClickListener on) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(Ic.get(icon, size, T.text));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(label);
        b.setOnClickListener(on);
        W.pressNow(b, .88f);
        LinearLayout.LayoutParams p = W.lp(T.dp(40), T.dp(40));
        p.leftMargin = T.dp(2);
        b.setLayoutParams(p);
        return b;
    }

    void buildNav(Context c) {
        nav = new FrameLayout(c);
        nav.setBackground(new W.Pill(T.glassBg, T.glassStroke, .5f, T.dark ? 0x1affffff : 0xbfffffff).shadow(T.dark ? 0x4d000000 : 0x1a000000, 28, 8));
        nav.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getHeight() / 2f);
            }
        });
        nav.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        navPill = new View(c);
        navPill.setBackground(new W.Pill(T.mixA(T.text, .10f), T.mixA(T.text, .16f), .5f, 0x1affffff));
        nav.addView(navPill, new FrameLayout.LayoutParams(0, W.MP));
        LinearLayout row = W.row(c);
        for (int i = 0; i < 4; i++) {
            final int k = i;
            LinearLayout b = W.col(c);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            b.setPadding(T.dp(8), T.dp(4), T.dp(8), T.dp(4));
            FrameLayout iw = new FrameLayout(c);
            ImageView ic = new ImageView(c);
            ic.setImageDrawable(Ic.get("nav." + TABS[i], 22, T.textSecondary));
            iw.addView(ic, W.flp(T.dp(22), T.dp(22), Gravity.CENTER));
            b.addView(iw, W.lp(W.MP, T.dp(22)));
            TextView l = W.text(c, LABELS[i], 11, 400, T.textSecondary);
            l.setGravity(Gravity.CENTER);
            T.lh(l, 16.5f);
            b.addView(l, W.lpm(W.MP, W.WC, 0, 2, 0, 0));
            b.setContentDescription(LABELS[i]);
            b.setOnClickListener(v -> {
                if (TABS[k].equals(view)) {
                    Tab t = tabs.get(view);
                    if (t != null) t.scrollTop();
                } else select(TABS[k], true);
            });
            W.pressNow(b, .88f);
            navBtns[i] = b;
            FrameLayout cell = new FrameLayout(c);
            cell.addView(b, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.CENTER));
            TextView badge = W.text(c, "", 9, 700, T.accentFg);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(T.dp(15));
            badge.setPadding(T.dp(4), 0, T.dp(4), 0);
            badge.setBackground(W.round(T.accent, 999));
            badge.setVisibility(View.GONE);
            FrameLayout.LayoutParams bp = W.flp(W.WC, T.dp(15), Gravity.TOP | Gravity.END);
            bp.topMargin = -T.dp(2);
            cell.addView(badge, bp);
            badges[i] = badge;
            cell.setClipChildren(false);
            LinearLayout.LayoutParams cp = W.lp(0, W.WC, 1);
            if (i > 0) cp.leftMargin = T.dp(4);
            row.addView(cell, cp);
        }
        nav.addView(row, new FrameLayout.LayoutParams(W.MP, W.MP));
        nav.setClipChildren(false);
        row.setClipChildren(false);
        FrameLayout.LayoutParams np = W.flp(W.MP, T.dp(56.5f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        np.leftMargin = T.dp(12);
        np.rightMargin = T.dp(12);
        root.addView(nav, np);
        nav.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = nav.getWidth() - T.dp(8);
            int pw = (w - T.dp(12)) / 4;
            ViewGroup.LayoutParams pp = navPill.getLayoutParams();
            if (pp.width != pw) {
                pp.width = pw;
                navPill.setLayoutParams(pp);
                placePill(false);
            }
            for (int i = 0; i < 4; i++) {
                FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) badges[i].getLayoutParams();
                int m = Math.round(pw * .18f);
                if (bp.rightMargin != m) {
                    bp.rightMargin = m;
                    badges[i].setLayoutParams(bp);
                }
            }
        });
    }

    void placePill(boolean anim) {
        int i = java.util.Arrays.asList(TABS).indexOf(view);
        navPill.setVisibility(i < 0 ? View.INVISIBLE : View.VISIBLE);
        if (i < 0) return;
        float x = i * (navPill.getLayoutParams().width + T.dp(4));
        if (anim) navPill.animate().translationX(x).setDuration(340).setInterpolator(T.EASE).start();
        else navPill.setTranslationX(x);
    }

    void buildFab(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(W.accentBg(999));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Ic.get("fab.new", 26, T.accentFg));
        f.addView(ic, W.flp(T.dp(26), T.dp(26), Gravity.CENTER));
        f.setContentDescription("New chat");
        f.setOnClickListener(v -> NewGroup.open(a));
        FrameLayout.LayoutParams p = W.flp(T.dp(56), T.dp(56), Gravity.BOTTOM | Gravity.END);
        p.rightMargin = T.dp(16);
        root.addView(f, p);
        fab = f;
    }

    public void onInsets(int top, int bottom, int ime) {
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        hp.topMargin = Math.max(top, T.dp(24)) + T.dp(8);
        header.setLayoutParams(hp);
        int navBottom = Math.max(T.dp(40), bottom + T.dp(12));
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) nav.getLayoutParams();
        np.bottomMargin = navBottom;
        nav.setLayoutParams(np);
        int maxW = T.dp(460);
        int sw = a.getResources().getDisplayMetrics().widthPixels;
        if (sw - T.dp(24) > maxW) {
            np.leftMargin = np.rightMargin = (sw - maxW) / 2;
            nav.setLayoutParams(np);
        }
        FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) fab.getLayoutParams();
        fp.bottomMargin = navBottom + T.dp(68);
        fab.setLayoutParams(fp);
        FrameLayout.LayoutParams ff = (FrameLayout.LayoutParams) fade.getLayoutParams();
        ff.height = T.dp(142) + bottom;
        fade.setLayoutParams(ff);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{T.mixA(T.bg, .92f), T.mixA(T.bg, .55f), 0}));
        boolean kb = ime > 0;
        nav.setVisibility(kb ? View.GONE : View.VISIBLE);
        fab.setVisibility(kb || !"chats".equals(view) ? View.GONE : View.VISIBLE);
        for (Tab t : tabs.values()) t.onInsets(contentTop(), contentBottom());
    }

    public int contentTop() {
        return Math.max(a.insetTop, T.dp(24)) + T.dp(8) + T.dp(44) + T.dp(8);
    }

    public int contentBottom() {
        return Math.max(T.dp(40), a.insetBottom + T.dp(12)) + T.dp(56.5f) + T.dp(16);
    }

    public void select(String key, boolean anim) {
        String old = view;
        Tab prev = tabs.get(old);
        view = key;
        Tab t = ensure(key);
        if (pgAnim != null && pgAnim.isRunning() && anim) pgAnim.cancel();
        if (anim || pgState != 2) {
            pgState = 0;
            for (Tab x : tabs.values()) x.v.setTranslationX(0);
            fab.setTranslationX(0);
        }
        int ci = java.util.Arrays.asList(TABS).indexOf(key);
        for (java.util.Map.Entry<String, Tab> en : tabs.entrySet()) {
            int xi = java.util.Arrays.asList(TABS).indexOf(en.getKey());
            en.getValue().v.setVisibility(en.getValue() == t ? View.VISIBLE : ci >= 0 && xi >= 0 && Math.abs(xi - ci) == 1 ? View.INVISIBLE : View.GONE);
        }
        if (prev != null && prev != t) prev.onHide();
        t.onShow();
        boolean chats = "chats".equals(key);
        blank.setVisibility(View.VISIBLE);
        blank.setBackgroundColor(T.bg);
        bg.setVisibility(View.GONE);
        fab.setVisibility(chats && a.insetIme == 0 ? View.VISIBLE : View.GONE);
        if (chats) chatsBadge = 0;
        int io = java.util.Arrays.asList(TABS).indexOf(old), in = java.util.Arrays.asList(TABS).indexOf(key);
        if (anim && io >= 0 && in >= 0 && io != in) {
            int d = in > io ? 1 : -1;
            t.v.setAlpha(.4f);
            t.v.setTranslationX(d * T.dp(18));
            t.v.animate().alpha(1).translationX(0).setDuration(150).setInterpolator(new android.view.animation.PathInterpolator(.22f, 1f, .36f, 1f)).start();
        }
        for (int i = 0; i < 4; i++) {
            boolean on = TABS[i].equals(key);
            LinearLayout b = navBtns[i];
            ImageView ic = (ImageView) ((FrameLayout) b.getChildAt(0)).getChildAt(0);
            ((Ic.D) ic.getDrawable()).tint(on ? T.accent : T.textSecondary);
            TextView l = (TextView) b.getChildAt(1);
            l.setTextColor(on ? T.accent : T.textSecondary);
            l.setTypeface(on ? T.sb : T.r);
        }
        placePill(anim);
        updateBadges();
        prebuildNeighbours();
        headerLeft.setVisibility(chats || "contacts".equals(key) ? View.VISIBLE : View.INVISIBLE);
    }

    Tab make(String key) {
        switch (key) {
            case "contacts": return new ContactsTab();
            case "settings": return new com.vulpine.at.settings.SettingsTab();
            case "profile": return new com.vulpine.at.settings.ProfileTab();
            case "notes": return new com.vulpine.at.notes.NotesTab();
            case "shop": return new com.vulpine.at.shop.ShopTab();
            default: return new ChatsTab();
        }
    }

    public void updateBadges() {
        set(badges[0], "chats".equals(view) ? 0 : chatsBadge);
        set(badges[1], "contacts".equals(view) ? 0 : contactsBadge);
        badges[2].setVisibility(View.GONE);
        badges[3].setVisibility(View.GONE);
    }

    static void set(TextView b, int n) {
        b.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        b.setText(n > 9 ? "9+" : String.valueOf(n));
    }

    void moreMenu(View anchor) {
        Ov.Menu m = new Ov.Menu(a).more();
        String h = Session.s == null ? "" : Session.s.optString("handle");
        if (h.equals("tor") || h.equals("tortue974") || h.equals("doudou")) m.item("Admin", Ic.svg("<svg viewBox=\"0 -960 960 960\" fill=\"currentColor\"><path d=\"M400-120q-17 0-28.5-11.5T360-160v-480H160q0-83 58.5-141.5T360-840h240v120l120-120h80v320h-80L600-640v480q0 17-11.5 28.5T560-120H400Zm40-80h80v-240h-80v240Zm0-320h80v-240H360q-26 0-49 10.5T271-720h169v200Zm40 40Z\"/></svg>", 18, T.text), false, () -> Ov.toast("Admin is available on the web app"));
        m.item("Notes", Ic.get("menu.notes", 18, T.text), false, () -> select("notes", false));
        m.item("Shop", Ic.get("menu.shop", 20, T.text), false, () -> select("shop", false));
        m.item("Create folder", Ic.get("menu.folder", 18, T.text), false, () -> {
            select("chats", false);
            App.H.postDelayed(() -> Folders.create(a), 80);
        });
        m.item("Log out", null, true, () -> Ov.confirm("Log out?", "You'll need your token to log back in. Your recovery phrase is the only way to access your account on a new device.", "Log out", true, () -> App.logout(true)));
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        m.showAt(root.getWidth() - T.dp(12), T.dp(32) + Math.max(a.insetTop, T.dp(24)), true);
    }

    int pgState;
    float pgX, pgY, pgPos;
    int pgFrom;
    android.view.VelocityTracker pgVt;
    android.animation.ValueAnimator pgAnim;

    int tabIndex() {
        return java.util.Arrays.asList(TABS).indexOf(view);
    }

    Tab ensure(String key) {
        Tab t = tabs.get(key);
        if (t == null) {
            t = make(key);
            t.h = this;
            t.v = t.build(a);
            tabs.put(key, t);
            int ci = tabIndex(), xi = java.util.Arrays.asList(TABS).indexOf(key);
            t.v.setVisibility(ci >= 0 && xi >= 0 && Math.abs(xi - ci) == 1 ? View.INVISIBLE : View.GONE);
            content.addView(t.v, new FrameLayout.LayoutParams(W.MP, W.MP));
            t.onInsets(contentTop(), contentBottom());
        }
        return t;
    }

    void prebuildNeighbours() {
        int i = tabIndex();
        if (i < 0) return;
        App.H.postDelayed(() -> {
            int j = tabIndex();
            if (j < 0 || pgState == 2) return;
            if (j > 0) ensure(TABS[j - 1]);
            if (j < 3) ensure(TABS[j + 1]);
        }, 350);
    }

    static boolean hScroller(View v, float x, float y) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        if (x < l[0] || y < l[1] || x >= l[0] + v.getWidth() * Math.max(1f, v.getScaleX()) || y >= l[1] + v.getHeight()) return false;
        if (v instanceof android.widget.HorizontalScrollView && (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1))) return true;
        if (v instanceof ViewGroup) for (int k = ((ViewGroup) v).getChildCount() - 1; k >= 0; k--) if (hScroller(((ViewGroup) v).getChildAt(k), x, y)) return true;
        return false;
    }

    boolean pagerAllowed(MotionEvent e) {
        int i = tabIndex();
        if (i < 0 || Ov.any() || a.insetIme > 0) return false;
        Tab cur = tabs.get(view);
        if (cur instanceof ChatsTab && ((ChatsTab) cur).selecting()) return false;
        int[] hl = new int[2], nl = new int[2];
        header.getLocationOnScreen(hl);
        nav.getLocationOnScreen(nl);
        float y = e.getRawY();
        if (header.getVisibility() == View.VISIBLE && y < hl[1] + header.getHeight()) return false;
        if (nav.getVisibility() == View.VISIBLE && y >= nl[1]) return false;
        if (fab.getVisibility() == View.VISIBLE) {
            int[] fl = new int[2];
            fab.getLocationOnScreen(fl);
            if (e.getRawX() >= fl[0] && e.getRawX() < fl[0] + fab.getWidth() && y >= fl[1] && y < fl[1] + fab.getHeight()) return false;
        }
        return cur == null || !hScroller(cur.v, e.getRawX(), y);
    }

    boolean pagerIntercept(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (pgAnim != null && pgAnim.isRunning()) {
                    pgAnim.cancel();
                    pgState = 2;
                    pgX = e.getRawX() + (pgPos - pgFrom) * content.getWidth();
                    pgY = e.getRawY();
                    if (pgVt != null) pgVt.recycle();
                    pgVt = android.view.VelocityTracker.obtain();
                    pgVt.addMovement(e);
                    return true;
                }
                pgState = pagerAllowed(e) ? 1 : 0;
                pgX = e.getRawX();
                pgY = e.getRawY();
                if (pgVt != null) pgVt.recycle();
                pgVt = android.view.VelocityTracker.obtain();
                pgVt.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (pgState != 1) return pgState == 2;
                pgVt.addMovement(e);
                float dx = e.getRawX() - pgX, dy = e.getRawY() - pgY;
                int slop = android.view.ViewConfiguration.get(a).getScaledTouchSlop();
                if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) {
                    pgState = 0;
                    return false;
                }
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    startDrag();
                    pgX = e.getRawX();
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pgState == 1) pgState = 0;
                return pgState == 2;
        }
        return false;
    }

    void startDrag() {
        pgState = 2;
        pgFrom = tabIndex();
        pgPos = pgFrom;
        if (pgFrom > 0) ensure(TABS[pgFrom - 1]).v.setVisibility(View.VISIBLE);
        if (pgFrom < 3) ensure(TABS[pgFrom + 1]).v.setVisibility(View.VISIBLE);
        root.requestDisallowInterceptTouchEvent(false);
        layoutPages(pgPos);
    }

    boolean pagerTouch(MotionEvent e) {
        if (pgState != 2) return false;
        if (pgVt != null) pgVt.addMovement(e);
        float w = Math.max(1, content.getWidth());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float raw = pgFrom - (e.getRawX() - pgX) / w;
                if (raw < 0) raw = raw * .3f;
                else if (raw > 3) raw = 3 + (raw - 3) * .3f;
                pgPos = raw;
                layoutPages(pgPos);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float v = 0;
                if (pgVt != null) {
                    pgVt.computeCurrentVelocity(1);
                    v = pgVt.getXVelocity() / T.density;
                    pgVt.recycle();
                    pgVt = null;
                }
                int target;
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) target = Math.round(pgPos);
                else if (v < -.35f) target = (int) Math.ceil(pgPos - .02f);
                else if (v > .35f) target = (int) Math.floor(pgPos + .02f);
                else target = Math.round(pgPos);
                target = Math.max(0, Math.min(3, Math.max(pgFrom - 1, Math.min(pgFrom + 1, target))));
                settle(target, v);
                return true;
            }
        }
        return true;
    }

    void settle(int target, float vel) {
        float from = pgPos;
        float dist = Math.abs(target - from);
        long dur = Math.max(180, Math.min(380, (long) (dist * content.getWidth() / T.density / Math.max(1.2f, Math.abs(vel) * 1.4f) + 160)));
        if (dist < .001f) dur = 1;
        pgAnim = android.animation.ValueAnimator.ofFloat(from, target);
        pgAnim.setDuration(dur);
        pgAnim.setInterpolator(new android.view.animation.PathInterpolator(.22f, 1f, .36f, 1f));
        pgAnim.addUpdateListener(x -> {
            pgPos = (float) x.getAnimatedValue();
            layoutPages(pgPos);
        });
        pgAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;

            public void onAnimationCancel(android.animation.Animator x) {
                cancelled = true;
            }

            public void onAnimationEnd(android.animation.Animator x) {
                if (cancelled) return;
                pgState = 0;
                for (Tab t : tabs.values()) t.v.setTranslationX(0);
                fab.setTranslationX(0);
                if (target != tabIndex()) select(TABS[target], false);
                else select(view, false);
            }
        });
        pgAnim.start();
    }

    void layoutPages(float p) {
        float w = content.getWidth();
        for (int k = 0; k < 4; k++) {
            Tab t = tabs.get(TABS[k]);
            if (t == null) continue;
            t.v.setTranslationX((k - p) * w);
            t.v.setAlpha(1);
        }
        fab.setTranslationX(-p * w);
        int pw = navPill.getLayoutParams().width;
        float clamped = Math.max(-.15f, Math.min(3.15f, p));
        navPill.animate().cancel();
        navPill.setTranslationX(clamped * (pw + T.dp(4)));
    }

    public boolean onBack() {
        Tab t = tabs.get(view);
        if (t != null && t.onBack()) return true;
        if (!"chats".equals(view)) {
            select("chats", true);
            return true;
        }
        return false;
    }

    public void onShow() {
        Chats.I.activeCid = null;
        Tab t = tabs.get(view);
        if (t != null) t.onShow();
    }

    public void onDestroy() {
        Sync.off(syncL);
        Chats.I.off(chatL);
        com.vulpine.core.Rt.I.off(wsL);
        if (I == this) I = null;
    }

    public static void openChat(String cid) {
        MainActivity a = MainActivity.cur;
        if (a == null || cid == null) return;
        Home h = I;
        if (h != null) {
            if (a.top() instanceof ChatScreen && cid.equals(((ChatScreen) a.top()).cid)) return;
            a.popTo(h);
            h.select("chats", false);
        }
        try {
            JSONObject u = Sync.unread();
            u.remove(cid);
            Session.save();
        } catch (Exception e) {
        }
        a.push(new ChatScreen(cid));
    }

    public static void refreshLists() {
        App.ui(() -> {
            Home h = I;
            if (h == null) return;
            for (Tab t : h.tabs.values()) if (t instanceof ChatsTab) ((ChatsTab) t).refresh();
        });
    }

    public static void openLink(String url) {
        Links2.open(url);
    }
}
