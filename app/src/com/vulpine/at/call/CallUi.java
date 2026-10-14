package com.vulpine.at.call;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

public final class CallUi {
    public static final String[] KEMO = {"1f436", "1f431", "1f42d", "1f439", "1f430", "1f98a", "1f43b", "1f43c", "1f428", "1f42f", "1f981", "1f42e", "1f437", "1f438", "1f435", "1f414", "1f427", "1f426", "1f424", "1f986", "1f985", "1f989", "1f987", "1f43a", "1f417", "1f434", "1f984", "1f41d", "1f41b", "1f98b", "1f40c", "1f41e", "1f41c", "1f422", "1f40d", "1f98e", "1f419", "1f991", "1f990", "1f980", "1f421", "1f420", "1f41f", "1f42c", "1f433", "1f988", "1f40a", "1f405", "1f406", "1f993", "1f98d", "1f418", "1f99b", "1f42a", "1f992", "1f998", "1f403", "1f40e", "1f411", "1f410", "1f98c", "1f415", "1f408", "1f413", "1f983", "1f99a", "1f99c", "1f9a2", "1f407", "1f99d", "1f9a1", "1f401", "1f43f", "1f994", "1f335", "1f332", "1f333", "1f334", "1f331", "1f340", "1f341", "1f337", "1f339", "1f33b", "1f33c", "1f338", "1f31e", "1f31d", "1f31b", "1f30d", "1f4ab", "2b50", "1f31f", "2728", "26a1", "1f525", "1f308", "2600", "26c4", "2744", "1f4a7", "1f30a", "1f34f", "1f34e", "1f350", "1f34a", "1f34b", "1f34c", "1f349", "1f347", "1f353", "1f348", "1f352", "1f351", "1f96d", "1f34d", "1f965", "1f95d", "1f345", "1f346", "1f951", "1f966", "1f952", "1f336", "1f33d", "1f955", "1f954", "1f950"};

    public final MainActivity a;
    final Context c;
    public final FrameLayout layer;
    FrameLayout full, conn, calling, incoming, pill, popHost;
    CallBg bg, callingBg;
    Flow flow;
    ScrollView scroll;
    TextView waiting;
    Bar bar;
    FrameLayout keyChip, bgBtnWrap, titleChip;
    ImageView bgBtn;
    final Map<String, Tile> tiles = new HashMap<>();
    boolean drawerOpen, audioOpen;
    String pop;
    View popView;
    Rate rate;
    Ov.Menu menu;
    boolean fullPushed;
    String lastKeySig = "", lastCallId;
    final Runnable backMin = () -> Calls.minimize();

    public CallUi(MainActivity a) {
        this.a = a;
        this.c = a;
        layer = new FrameLayout(a);
        layer.setClipChildren(false);
        layer.setTranslationZ(T.dpf(20));
        a.host.addView(layer, new FrameLayout.LayoutParams(W.MP, W.MP));
        a.host.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            public void onChildViewAdded(View p, View ch) {
                if (ch == layer) return;
                for (com.vulpine.at.ui.Screen s : a.stack) if (s.root == ch) {
                    layer.post(layer::bringToFront);
                    return;
                }
            }

            public void onChildViewRemoved(View p, View ch) {
            }
        });
        layer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::relayout);
        });
    }

    public void detach() {
        try {
            a.host.setOnHierarchyChangeListener(null);
        } catch (Exception e) {
        }
        if (fullPushed) Ov.remove(full);
        fullPushed = false;
        for (Tile t : tiles.values()) t.unbind();
        tiles.clear();
        if (incoming != null) a.host.removeView(incoming);
        incoming = null;
        ((ViewGroup) layer.getParent()).removeView(layer);
    }

    int top() {
        return Math.max(Math.round(a.insetTop / T.density), 40);
    }

    int bot() {
        return Math.max(Math.round(a.insetBottom / T.density), 48);
    }

    void relayout() {
        if (scroll != null) flow.setPadding(T.dp(16), T.dp(top() + 70), T.dp(16), T.dp(bot() + 124));
        placeChips();
        if (bar != null) bar.requestLayout();
        if (pill != null) placePill(false);
    }

    public void refresh() {
        Sig s = Calls.sig;
        if (s == null) return;
        Sig.Call cur = s.cur;
        boolean showInc = s.inc != null && cur == null;
        if (showInc) showIncoming(s.inc);
        else hideIncoming();
        if (cur == null) {
            hideFull(true);
            hidePill();
            return;
        }
        if (!cur.callId.equals(lastCallId)) {
            lastCallId = cur.callId;
            drawerOpen = false;
            audioOpen = false;
            closePop();
        }
        if (Calls.minimized) {
            hideFull(false);
            showPill();
        } else {
            hidePill();
            showFull();
        }
    }

    void showFull() {
        if (full == null) {
            full = new FrameLayout(c) {
                boolean eat;

                @Override
                public boolean dispatchTouchEvent(MotionEvent e) {
                    int act = e.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) {
                        eat = false;
                        if (pop != null && !inside(popView, e)) {
                            closePop();
                            eat = true;
                        } else if (pop == null && drawerOpen && bar != null && !inside(bar.drawerRect(), e)) {
                            setDrawer(false);
                            eat = true;
                        }
                    }
                    if (eat) {
                        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) eat = false;
                        return true;
                    }
                    return super.dispatchTouchEvent(e);
                }
            };
            full.setClickable(true);
            layer.addView(full, 0, new FrameLayout.LayoutParams(W.MP, W.MP));
            full.setAlpha(0);
            full.animate().alpha(1).setDuration(300).start();
        }
        full.setVisibility(View.VISIBLE);
        if (!fullPushed) {
            Ov.push(full, backMin);
            fullPushed = true;
        }
        Sig.Call cur = Calls.sig.cur;
        if (cur.connected()) {
            if (calling != null) {
                full.removeView(calling);
                calling = null;
            }
            buildConn();
            updateConn();
        } else {
            if (conn != null) {
                full.removeView(conn);
                conn = null;
                for (Tile t : tiles.values()) t.unbind();
                tiles.clear();
            }
            buildCalling();
        }
    }

    void hideFull(boolean destroy) {
        closePop();
        if (drawerOpen && bar != null) setDrawer(false);
        if (fullPushed) {
            Ov.remove(full);
            fullPushed = false;
        }
        if (full == null) return;
        if (destroy) {
            for (Tile t : tiles.values()) t.unbind();
            tiles.clear();
            final View f = full;
            full = null;
            conn = null;
            calling = null;
            bar = null;
            pop = null;
            popView = null;
            f.animate().alpha(0).scaleX(.97f).scaleY(.97f).setDuration(240).setInterpolator(T.EASE_IN).withEndAction(() -> layer.removeView(f)).start();
        } else {
            closePop();
            full.setVisibility(View.GONE);
        }
    }

    boolean inside(View v, MotionEvent e) {
        if (v == null) return false;
        int[] p = new int[2];
        v.getLocationOnScreen(p);
        return e.getRawX() >= p[0] && e.getRawX() <= p[0] + v.getWidth() && e.getRawY() >= p[1] && e.getRawY() <= p[1] + v.getHeight();
    }

    boolean inside(RectF r, MotionEvent e) {
        return r != null && r.contains(e.getX(), e.getY());
    }

    void buildCalling() {
        Sig.Call cur = Calls.sig.cur;
        Sig.Part p0 = cur.parts.isEmpty() ? null : cur.parts.values().iterator().next();
        String name = p0 != null && p0.name != null ? p0.name : null;
        if (calling == null) {
            calling = new FrameLayout(c);
            callingBg = new CallBg(c, false);
            callingBg.custom = CallBg.saved();
            calling.addView(callingBg, new FrameLayout.LayoutParams(W.MP, W.MP));
            LinearLayout col = W.col(c);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            AvatarView av = new AvatarView(c);
            av.setTag("av");
            col.addView(av, W.lp(T.dp(96), T.dp(96)));
            TextView nm = W.text(c, "", 20, 600, T.text);
            nm.setTag("nm");
            nm.setGravity(Gravity.CENTER);
            T.lh(nm, 30);
            col.addView(nm, W.lpm(W.WC, W.WC, 0, 20, 0, 0));
            TextView sub = W.text(c, "Calling…", 14, 400, T.textMuted);
            T.lh(sub, 21);
            col.addView(sub, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
            Btn end = new Btn(c).set("call.hangup", "End call", false, true);
            end.setOnClickListener(v -> Calls.hangUp());
            col.addView(end, W.lpm(T.dp(48), T.dp(48), 0, 20, 0, 0));
            calling.addView(col, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.CENTER));
            full.addView(calling, new FrameLayout.LayoutParams(W.MP, W.MP));
        }
        AvatarView av = calling.findViewWithTag("av");
        av.set(name == null ? "Calling" : name, p0 == null ? null : p0.avatarColor, p0 == null ? null : p0.avatarUrl);
        ((TextView) calling.findViewWithTag("nm")).setText(name == null ? "Calling…" : name);
    }

    void buildConn() {
        if (conn != null) return;
        conn = new FrameLayout(c);
        conn.setClipChildren(false);
        bg = new CallBg(c, false);
        bg.custom = CallBg.saved();
        conn.addView(bg, new FrameLayout.LayoutParams(W.MP, W.MP));
        scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        flow = new Flow(c);
        flow.setPadding(T.dp(16), T.dp(top() + 70), T.dp(16), T.dp(bot() + 124));
        scroll.addView(flow, new FrameLayout.LayoutParams(W.MP, W.MP));
        conn.addView(scroll, new FrameLayout.LayoutParams(W.MP, W.MP));
        waiting = W.text(c, "Waiting for others to join…", 14, 400, T.textMuted);
        waiting.setGravity(Gravity.CENTER);
        T.lh(waiting, 21);
        keyChip = new FrameLayout(c);
        conn.addView(keyChip, new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        keyChip.setOnClickListener(v -> togglePop("key"));
        W.press(keyChip, .96f);
        bgBtnWrap = new FrameLayout(c);
        bgBtn = new ImageView(c);
        bgBtn.setScaleType(ImageView.ScaleType.CENTER);
        bgBtn.setContentDescription("Customize call background");
        bgBtnWrap.addView(bgBtn, new FrameLayout.LayoutParams(T.dp(38), T.dp(38)));
        bgBtn.setOnClickListener(v -> togglePop("bg"));
        W.press(bgBtn, .94f);
        conn.addView(bgBtnWrap, new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.END));
        titleChip = new FrameLayout(c);
        conn.addView(titleChip, new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.START));
        bar = new Bar(c, this);
        conn.addView(bar, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM));
        popHost = new FrameLayout(c);
        popHost.setClipChildren(false);
        conn.addView(popHost, new FrameLayout.LayoutParams(W.MP, W.MP));
        full.addView(conn, new FrameLayout.LayoutParams(W.MP, W.MP));
        placeChips();
    }

    void placeChips() {
        if (conn == null) return;
        int y = T.dp(top() + 18);
        ((FrameLayout.LayoutParams) keyChip.getLayoutParams()).topMargin = y;
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) bgBtnWrap.getLayoutParams();
        bp.topMargin = y;
        bp.rightMargin = T.dp(14);
        FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) titleChip.getLayoutParams();
        tp.topMargin = y;
        tp.leftMargin = T.dp(12);
        keyChip.requestLayout();
    }

    void updateConn() {
        Sig s = Calls.sig;
        Sig.Call cur = s.cur;
        CallMedia m = Calls.media;
        Set<String> sp = m == null ? new HashSet<>() : m.speaking();
        List<String> want = new ArrayList<>();
        want.add("local");
        want.addAll(cur.parts.keySet());
        for (String k : new ArrayList<>(tiles.keySet()))
            if (!want.contains(k)) {
                Tile t = tiles.remove(k);
                t.unbind();
                flow.removeView(t);
            }
        int idx = 0;
        for (String k : want) {
            Tile t = tiles.get(k);
            if (t == null) {
                t = new Tile(c, this, k);
                tiles.put(k, t);
            }
            if (flow.getChildAt(idx) != t) {
                if (t.getParent() != null) flow.removeView(t);
                flow.addView(t, idx, new ViewGroup.LayoutParams(T.dp(200), W.WC));
            }
            idx++;
            t.update(cur, sp);
        }
        if (cur.parts.isEmpty()) {
            if (waiting.getParent() == null) flow.addView(waiting, new ViewGroup.LayoutParams(W.MP, W.WC));
        } else if (waiting.getParent() != null) flow.removeView(waiting);
        flow.invalidate();
        updateKeyChip(cur);
        updateBgBtn();
        updateTitle(cur);
        bar.update();
        if (bg != null) {
            JSONObject sv = "bg".equals(pop) ? bg.custom : CallBg.saved();
            if (!"bg".equals(pop)) {
                bg.custom = sv;
                bg.invalidate();
            }
        }
        if ("audio".equals(pop) && popView != null) {
        }
    }

    public void speakingChanged() {
        if (conn == null || Calls.sig == null || Calls.sig.cur == null) return;
        Set<String> sp = Calls.media == null ? new HashSet<>() : Calls.media.speaking();
        for (Tile t : tiles.values()) t.speak(sp.contains(t.uid));
        flow.invalidate();
    }

    void updateKeyChip(Sig.Call cur) {
        List<Sig.Part> list = new ArrayList<>();
        for (Sig.Part p : cur.parts.values()) if (p.keys != null) list.add(p);
        boolean grp = cur.parts.size() > 1 || list.size() > 1;
        StringBuilder sig = new StringBuilder(grp ? "g" : "s");
        for (Sig.Part p : list) sig.append(p.uid).append(java.util.Arrays.toString(p.keys));
        String ks = sig.toString();
        if (ks.equals(lastKeySig)) return;
        lastKeySig = ks;
        keyChip.removeAllViews();
        keyChip.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        if (list.isEmpty()) return;
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(W.round(CallBg.chip(), 999));
        if (!grp) {
            row.setPadding(T.dp(12), T.dp(6), T.dp(12), T.dp(6));
            int[] k = list.get(0).keys;
            for (int i = 0; i < 4; i++) {
                View iv = cell(k[i], 20);
                row.addView(iv, W.lpm(T.dp(20), T.dp(20), i == 0 ? 0 : 6, 0, 0, 0));
                pop(iv, i * 60);
            }
        } else {
            row.setPadding(T.dp(12), T.dp(6), T.dp(12), T.dp(6));
            ImageView lk = new ImageView(c);
            lk.setImageDrawable(Ic.get("call.lock", 14, T.text));
            row.addView(lk, W.lp(T.dp(14), T.dp(14)));
            TextView t = W.text(c, "Encrypted", 13, 600, T.text);
            T.lh(t, 19.5f);
            row.addView(t, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
        }
        keyChip.addView(row);
    }

    static void pop(View v, int delay) {
        v.setAlpha(0);
        v.setScaleX(.5f);
        v.setScaleY(.5f);
        v.setTranslationY(T.dpf(4));
        v.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setStartDelay(delay).setDuration(340).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .3f, 1.3f)).start();
    }

    View cell(int idx, int size) {
        FrameLayout f = new FrameLayout(c);
        f.addView(emoji(idx, size), new FrameLayout.LayoutParams(W.MP, W.MP));
        return f;
    }

    ImageView emoji(int idx, int size) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String code = KEMO[Math.max(0, Math.min(127, idx))];
        Img.into(iv, Http.abs("/emoji/" + code + ".png"), T.dp(size * 2));
        return iv;
    }

    void updateBgBtn() {
        boolean on = "bg".equals(pop);
        bgBtn.setBackground(W.oval(on ? T.accent : CallBg.chip()));
        bgBtn.setImageDrawable(Ic.get("call.pencil", 18, on ? 0xffffffff : T.text));
    }

    void updateTitle(Sig.Call cur) {
        if (titleChip.getChildCount() > 0 && titleChip.getChildAt(0) instanceof android.widget.EditText) return;
        titleChip.removeAllViews();
        if (!cur.room()) return;
        JSONObject r = Rooms.room(cur.cid.substring(2));
        if (r == null) {
            Rooms.ensure(cur.cid.substring(2));
            return;
        }
        boolean can = Rooms.canRename(r);
        LinearLayout pillv = W.row(c);
        pillv.setGravity(Gravity.CENTER_VERTICAL);
        pillv.setPadding(T.dp(12), 0, T.dp(12), 0);
        pillv.setBackground(W.round(CallBg.chip(), 999));
        TextView t = W.text(c, r.optString("name"), 14, 600, T.text);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        pillv.addView(t, new LinearLayout.LayoutParams(W.WC, W.WC, 1));
        if (can) {
            ImageView pen = new ImageView(c);
            pen.setImageDrawable(Ic.get("call.pen", 13, T.text));
            pen.setAlpha(.6f);
            pillv.addView(pen, W.lpm(T.dp(13), T.dp(13), 6, 0, 0, 0));
            pillv.setOnClickListener(v -> Rooms.rename(this, r));
        }
        int w = (int) (layer.getWidth() / 2f - T.dp(74));
        titleChip.addView(pillv, new FrameLayout.LayoutParams(W.WC, T.dp(32)));
        t.setMaxWidth(Math.max(T.dp(40), w - T.dp(can ? 43 : 24)));
    }

    public void togglePop(String k) {
        if (k.equals(pop)) {
            closePop();
            return;
        }
        closePop();
        pop = k;
        View v;
        switch (k) {
            case "key":
                v = Pops.key(this);
                break;
            case "bg":
                v = Pops.bgEditor(this);
                break;
            case "audio":
                v = Pops.audio(this);
                break;
            default:
                v = null;
        }
        if (v == null) {
            pop = null;
            return;
        }
        popView = v;
        popHost.addView(v);
        pushKeepZ(v, this::closePop);
        updateBgBtn();
        if (bar != null) bar.update();
    }

    void showPop(String k, View v) {
        closePop();
        pop = k;
        popView = v;
        popHost.addView(v);
        pushKeepZ(v, this::closePop);
    }

    public void closePop() {
        if (menu != null) {
            menu.close();
            menu = null;
        }
        if (pop == null) return;
        String was = pop;
        pop = null;
        View v = popView;
        popView = null;
        if (v != null) Ov.remove(v);
        if (v != null) v.animate().alpha(0).setDuration(140).withEndAction(() -> {
            if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        }).start();
        if ("bg".equals(was) && bg != null) {
            bg.custom = CallBg.saved();
            bg.invalidate();
        }
        if (bgBtn != null) updateBgBtn();
        if (bar != null) bar.update();
    }

    final Runnable drawerBack = () -> setDrawer(false);

    static void pushKeepZ(View v, Runnable back) {
        float z = v.getTranslationZ();
        Ov.push(v, back);
        v.setTranslationZ(z);
    }

    public void setDrawer(boolean open) {
        if (drawerOpen == open) return;
        drawerOpen = open;
        if (open) pushKeepZ(bar, drawerBack);
        else Ov.remove(bar);
        if (!open && "audio".equals(pop)) closePop();
        bar.update();
        bar.animateDrawer(open);
    }

    public static final class Tile extends FrameLayout {
        final CallUi ui;
        final String uid;
        final AvatarView av;
        View video;
        final LinearLayout chip;
        final View dot;
        final TextView label;
        final LinearLayout ind;
        FrameLayout lock, flip;
        android.view.GestureDetector gd;
        boolean speaking, showVideo;
        float ar = 4f / 3f, downX = -1, downY = -1;
        Boolean lastMuted, lastDeaf;

        Tile(Context c, CallUi ui, String uid) {
            super(c);
            this.ui = ui;
            this.uid = uid;
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), T.dpf(12));
                }
            });
            av = new AvatarView(c);
            addView(av, new LayoutParams(T.dp(96), T.dp(96), Gravity.CENTER));
            chip = W.row(c);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(T.dp(8), T.dp(2), T.dp(8), T.dp(2));
            chip.setBackground(W.round(0x80000000, 999));
            dot = new View(c);
            dot.setBackground(W.oval(T.success));
            dot.setVisibility(GONE);
            chip.addView(dot, W.lpm(T.dp(6), T.dp(6), 0, 0, 4, 0));
            label = W.text(c, "", 13, 400, 0xffffffff);
            label.setSingleLine();
            label.setEllipsize(TextUtils.TruncateAt.END);
            T.lh(label, 19.5f);
            chip.addView(label);
            ind = W.row(c);
            ind.setGravity(Gravity.CENTER_VERTICAL);
            chip.addView(ind, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
            LayoutParams cp = new LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.START);
            cp.leftMargin = cp.topMargin = T.dp(4);
            cp.rightMargin = T.dp(36);
            addView(chip, cp);
            if ("local".equals(uid)) {
                flip = new FrameLayout(c);
                ImageView fi = new ImageView(c);
                fi.setScaleType(ImageView.ScaleType.CENTER);
                fi.setImageDrawable(Ic.get("call.flip", 18, 0xffffffff));
                flip.addView(fi, new LayoutParams(T.dp(34), T.dp(34)));
                flip.setBackground(W.oval(0x80000000));
                flip.setContentDescription("Flip camera");
                flip.setVisibility(GONE);
                LayoutParams fp = new LayoutParams(T.dp(34), T.dp(34), Gravity.TOP | Gravity.END);
                fp.topMargin = fp.rightMargin = T.dp(6);
                addView(flip, fp);
                W.press(flip, .9f);
                flip.setOnClickListener(v -> doFlip());
                gd = new android.view.GestureDetector(c, new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (flip.getVisibility() == VISIBLE) doFlip();
                        return true;
                    }
                });
                setOnTouchListener((v, e) -> showVideo && gd.onTouchEvent(e));
            }
            if (!"local".equals(uid)) {
                setOnLongClickListener(v -> {
                    try {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    } catch (Exception e) {
                    }
                    Pops.tileMenu(ui, this);
                    return true;
                });
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getRawX();
                downY = e.getRawY();
            }
            return super.dispatchTouchEvent(e);
        }

        void doFlip() {
            Calls.flipCam();
            View ic = ((ViewGroup) flip).getChildAt(0);
            ic.animate().cancel();
            ic.setRotation(0);
            ic.animate().rotation(-180).setDuration(320).setInterpolator(T.EASE).start();
            try {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } catch (Exception e) {
            }
        }

        void unbind() {
            if (video != null && Calls.media != null) Calls.media.unbindVideo(video);
        }

        void speak(boolean on) {
            if (on == speaking) return;
            speaking = on;
            dot.setVisibility(on ? VISIBLE : GONE);
        }

        void update(Sig.Call cur, Set<String> sp) {
            CallMedia m = Calls.media;
            Sig s = Calls.sig;
            boolean local = "local".equals(uid);
            Sig.Part p = local ? null : cur.parts.get(uid);
            String name = local ? "You" : p == null ? uid : p.name;
            if (local) {
                JSONObject me = Session.s;
                av.set("You", me == null ? null : me.optString("avatarColor", null), me == null || me.isNull("avatarUrl") ? null : me.optString("avatarUrl", null));
            } else if (p != null) av.set(p.name, p.avatarColor, p.avatarUrl);
            label.setText(name);
            speak(sp.contains(uid));
            boolean muted = local ? !s.mic : p != null && Boolean.TRUE.equals(p.rMuted);
            boolean deaf = local ? s.deaf : p != null && Boolean.TRUE.equals(p.rDeaf);
            if (!Boolean.valueOf(muted).equals(lastMuted) || !Boolean.valueOf(deaf).equals(lastDeaf)) {
                ind.removeAllViews();
                if (muted) {
                    ImageView i = new ImageView(getContext());
                    i.setImageDrawable(Ic.get("call.micOff", 12, 0xffff8a8a));
                    ind.addView(i, W.lp(T.dp(12), T.dp(12)));
                    if (lastMuted != null) indPop(i);
                }
                if (deaf) {
                    ImageView i = new ImageView(getContext());
                    i.setImageDrawable(Ic.get("call.headphonesOff", 12, 0xffff8a8a));
                    ind.addView(i, W.lpm(T.dp(12), T.dp(12), muted ? 3 : 0, 0, 0, 0));
                    if (lastDeaf != null) indPop(i);
                }
                ind.setVisibility(muted || deaf ? VISIBLE : GONE);
                lastMuted = muted;
                lastDeaf = deaf;
            }
            boolean vid;
            if (local) vid = m != null && m.localVideo();
            else vid = m != null && p != null && m.remoteVideo(uid) && !Boolean.FALSE.equals(p.rVideo) && !p.videoHidden && !p.streamDisabled;
            if (vid && video == null && m != null) {
                video = m.videoView(uid, (w, h) -> {
                    float nar = h > w ? Math.max(w / (float) h, 9f / 20f) : 4f / 3f;
                    if (Math.abs(nar - ar) > .001f) {
                        ar = nar;
                        requestLayout();
                    }
                });
                addView(video, 0, new LayoutParams(W.MP, W.MP));
            }
            if (video != null) {
                if (vid) m.bindVideo(video, uid);
                else m.unbindVideo(video);
                video.setVisibility(vid ? VISIBLE : GONE);
            }
            showVideo = vid;
            if (flip != null) flip.setVisibility(local && vid && m != null && m.camOn() && !m.screenOn() && m.canSwitchCam() ? VISIBLE : GONE);
            av.setVisibility(vid ? GONE : VISIBLE);
            setBackground(vid ? new android.graphics.drawable.ColorDrawable(0xff000000) : W.round(CallBg.tile(), 12));
            if (!local && p != null) {
                if (m != null) m.applyRemote(uid, p.volume, !p.mutedLocally && !p.streamDisabled, !p.videoHidden && !p.streamDisabled);
                boolean showLock = p.keys != null && cur.parts.size() > 1;
                if (showLock && lock == null) {
                    lock = new FrameLayout(getContext());
                    ImageView li = new ImageView(getContext());
                    li.setImageDrawable(Ic.get("call.lock", 14, 0xffffffff));
                    li.setScaleType(ImageView.ScaleType.CENTER);
                    lock.addView(li, new LayoutParams(T.dp(28), T.dp(22)));
                    lock.setBackground(W.round(0x80000000, 999));
                    LayoutParams lp = new LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.END);
                    lp.topMargin = lp.rightMargin = T.dp(4);
                    addView(lock, lp);
                    lock.setOnClickListener(v -> Pops.tileKey(ui, this));
                }
                if (lock != null) lock.setVisibility(showLock ? VISIBLE : GONE);
            }
            requestLayout();
        }

        static void indPop(View v) {
            v.setScaleX(.6f);
            v.setScaleY(.6f);
            v.setAlpha(0);
            v.animate().scaleX(1).scaleY(1).alpha(1).setDuration(220).setInterpolator(new android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start();
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int h = showVideo ? Math.round(w / ar) : T.dp(144);
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    static String incName(Sig.Incoming inc) {
        if (inc.name == null || inc.anon) return "Vulpine";
        boolean locked = !com.vulpine.at.Pin.unlocked && com.vulpine.at.Pin.hasPin();
        boolean hidden = com.vulpine.at.Sync.contains(Session.settings().optJSONArray("hiddenChatIds"), inc.cid);
        return locked || hidden ? "Vulpine" : inc.name;
    }

    void showIncoming(Sig.Incoming inc) {
        if (incoming != null) {
            ((TextView) incoming.findViewWithTag("nm")).setText(incName(inc));
            return;
        }
        incoming = new FrameLayout(c);
        incoming.setBackgroundColor(0xb3000000);
        incoming.setClickable(true);
        FrameLayout card = new BgCard(c);
        CallBg cb = new CallBg(c, true);
        cb.setRadius(T.dpf(16));
        card.addView(cb, new FrameLayout.LayoutParams(W.MP, W.MP));
        card.setElevation(T.dpf(18));
        card.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), T.dpf(16));
            }
        });
        LinearLayout col = W.col(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(T.dp(24), T.dp(24), T.dp(24), T.dp(24));
        col.setMinimumWidth(T.dp(320));
        ImageView ph = new ImageView(c);
        ph.setImageDrawable(Ic.get("call.phone", 40, T.accent));
        col.addView(ph, W.lpm(T.dp(40), T.dp(40), 0, 0, 0, 12));
        TextView nm = W.text(c, incName(inc), 20, 600, T.text);
        nm.setTag("nm");
        nm.setGravity(Gravity.CENTER);
        T.lh(nm, 30);
        col.addView(nm, W.lpm(W.WC, W.WC, 0, 0, 0, 4));
        TextView sub = W.text(c, "Incoming call…", 14, 400, T.textMuted);
        T.lh(sub, 21);
        col.addView(sub, W.lpm(W.WC, W.WC, 0, 0, 0, 16));
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.CENTER);
        Btn dec = new Btn(c).set("call.hangup", "Decline", false, true);
        dec.setOnClickListener(v -> Calls.decline());
        Btn acc = new Btn(c).set("call.phone", "Accept", true, false);
        acc.setOnClickListener(v -> Calls.accept());
        row.addView(dec, W.lp(T.dp(48), T.dp(48)));
        row.addView(acc, W.lpm(T.dp(48), T.dp(48), 16, 0, 0, 0));
        col.addView(row);
        card.addView(col, new FrameLayout.LayoutParams(W.WC, W.WC));
        incoming.addView(card, new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.CENTER));
        a.host.addView(incoming, new FrameLayout.LayoutParams(W.MP, W.MP));
        incoming.setTranslationZ(T.dpf(130));
        incoming.setAlpha(0);
        incoming.animate().alpha(1).setDuration(220).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        card.setScaleX(.94f);
        card.setScaleY(.94f);
        card.setTranslationY(T.dpf(8));
        card.animate().scaleX(1).scaleY(1).translationY(0).setDuration(300).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
    }

    void hideIncoming() {
        if (incoming == null) return;
        final View v = incoming;
        incoming = null;
        v.animate().alpha(0).setDuration(200).withEndAction(() -> a.host.removeView(v)).start();
    }

    void showPill() {
        if (pill != null) {
            updatePill();
            return;
        }
        pill = new FrameLayout(c);
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        row.setBackground(W.round(T.accent, 999));
        pill.setElevation(T.dpf(8));
        ImageView ph = new ImageView(c);
        ph.setImageDrawable(Ic.get("call.phone", 15, T.accentFg));
        row.addView(ph, W.lp(T.dp(15), T.dp(15)));
        TextView t = W.text(c, "Ongoing call", 14, 600, T.accentFg);
        t.setSingleLine();
        row.addView(t, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        ImageView mi = new ImageView(c);
        mi.setTag("mi");
        mi.setImageDrawable(Ic.get("call.micOff", 13, T.accentFg));
        mi.setAlpha(.9f);
        row.addView(mi, W.lpm(T.dp(13), T.dp(13), 8, 0, 0, 0));
        TextView ret = W.text(c, "▢", 13, 600, T.accentFg);
        ret.setGravity(Gravity.CENTER);
        ret.setBackground(W.oval(0x40ffffff));
        ret.setContentDescription("Return to call");
        ret.setOnClickListener(v -> Calls.maximize());
        row.addView(ret, W.lpm(T.dp(26), T.dp(26), 8, 0, 0, 0));
        TextView x = W.text(c, "✕", 12, 600, 0xffffffff);
        x.setGravity(Gravity.CENTER);
        x.setBackground(W.oval(T.danger));
        x.setContentDescription("Hang up");
        x.setOnClickListener(v -> Calls.hangUp());
        row.addView(x, W.lpm(T.dp(26), T.dp(26), 8, 0, 0, 0));
        pill.addView(row);
        final float[] st = new float[5];
        pill.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    st[0] = e.getRawX();
                    st[1] = e.getRawY();
                    st[2] = pill.getTranslationX();
                    st[3] = pill.getTranslationY();
                    st[4] = 0;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float nx = clampX(st[2] + e.getRawX() - st[0]), ny = clampY(st[3] + e.getRawY() - st[1]);
                    st[4] += Math.abs(nx - pill.getTranslationX()) + Math.abs(ny - pill.getTranslationY());
                    pill.setTranslationX(nx);
                    pill.setTranslationY(ny);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    App.prefs.edit().putString("vulpine.callfloat", "{\"x\":" + Math.round(pill.getTranslationX() / T.density) + ",\"y\":" + Math.round(pill.getTranslationY() / T.density) + "}").apply();
                    if (st[4] < T.dp(8) && e.getActionMasked() == MotionEvent.ACTION_UP) Calls.maximize();
                    return true;
            }
            return false;
        });
        layer.addView(pill, new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.START));
        placePill(true);
        updatePill();
        pill.setAlpha(0);
        pill.setScaleX(.88f);
        pill.setScaleY(.88f);
        pill.animate().alpha(1).scaleX(1).scaleY(1).setDuration(240).setInterpolator(new android.view.animation.PathInterpolator(.22f, .8f, .24f, 1f)).start();
    }

    float clampX(float x) {
        int w = pill.getWidth() > 0 ? pill.getWidth() : T.dp(190);
        return Math.max(T.dp(8), Math.min(layer.getWidth() - w - T.dp(8), x));
    }

    float clampY(float y) {
        int h = pill.getHeight() > 0 ? pill.getHeight() : T.dp(46);
        return Math.max(T.dp(8) + a.insetTop, Math.min(layer.getHeight() - h - T.dp(8) - a.insetBottom, y));
    }

    void placePill(boolean first) {
        pill.post(() -> {
            if (pill == null) return;
            float x = layer.getWidth() - T.dp(200), y = layer.getHeight() - T.dp(110);
            try {
                JSONObject o = new JSONObject(App.prefs.getString("vulpine.callfloat", ""));
                x = T.dp(o.optInt("x"));
                y = T.dp(o.optInt("y"));
            } catch (Exception e) {
            }
            pill.setTranslationX(clampX(x));
            pill.setTranslationY(clampY(y));
        });
    }

    void updatePill() {
        View mi = pill.findViewWithTag("mi");
        if (mi != null) mi.setVisibility(Calls.sig.mic ? View.GONE : View.VISIBLE);
    }

    void hidePill() {
        if (pill == null) return;
        final View v = pill;
        pill = null;
        v.animate().alpha(0).scaleX(.6f).scaleY(.6f).setDuration(160).setInterpolator(T.EASE_IN).withEndAction(() -> layer.removeView(v)).start();
    }

    public void showRate(String callId) {
        if (rate != null) rate.close();
        rate = new Rate(this, callId);
    }

    static final class BgCard extends FrameLayout {
        BgCard(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            View fg = getChildAt(1);
            fg.measure(ws, hs);
            int w = fg.getMeasuredWidth(), h = fg.getMeasuredHeight();
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            setMeasuredDimension(w, h);
        }
    }

    static final class Bar extends ViewGroup {
        final CallUi ui;
        final FrameLayout tab;
        final ImageView chev;
        final Btn cam, deaf, mic, end, min;
        final List<Btn> extras = new ArrayList<>();
        final List<TextView> labels = new ArrayList<>();
        final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        float open01;
        float dy0, dx0;
        boolean swiping;
        final RectF dr = new RectF();
        android.animation.ValueAnimator va;

        Bar(Context c, CallUi ui) {
            super(c);
            this.ui = ui;
            setWillNotDraw(false);
            setClipChildren(false);
            tab = new FrameLayout(c);
            chev = new ImageView(c);
            chev.setScaleType(ImageView.ScaleType.CENTER);
            chev.setImageDrawable(Ic.get("call.chev", 20, 0xffffffff));
            chev.setBackground(W.round(0x29ffffff, 999));
            tab.addView(chev, new FrameLayout.LayoutParams(T.dp(52), T.dp(24), Gravity.CENTER));
            tab.setContentDescription("More call options");
            tab.setOnClickListener(v -> ui.setDrawer(!ui.drawerOpen));
            addView(tab);
            cam = new Btn(c);
            cam.setOnClickListener(v -> Calls.toggleCam());
            deaf = new Btn(c);
            deaf.setOnClickListener(v -> Calls.toggleDeafen());
            mic = new Btn(c);
            mic.setOnClickListener(v -> Calls.toggleMic());
            end = new Btn(c).set("call.hangup", "Hang up", false, true);
            end.setOnClickListener(v -> Calls.hangUp());
            min = new Btn(c).set("call.minimize", "Minimize call", false, false);
            min.setOnClickListener(v -> Calls.minimize());
            for (View v : new View[]{cam, deaf, mic, end, min}) addView(v);
            sh.setColor(0xff000000);
        }

        void update() {
            Sig s = Calls.sig;
            CallMedia m = Calls.media;
            boolean camOn = m != null && m.camOn(), scr = m != null && m.screenOn();
            cam.set(camOn ? "call.video" : "call.videoOff", camOn ? "Turn off camera" : "Turn on camera", camOn, false);
            deaf.set(s.deaf ? "call.headphonesOff" : "call.headphones", s.deaf ? "Undeafen" : "Deafen", s.deaf, false);
            micSet(s.mic);
            List<Object[]> want = new ArrayList<>();
            boolean spk = CallAudio.speakerOn();
            int h = CallAudio.headset();
            String spkLabel = CallAudio.route == CallAudio.BLUETOOTH ? "Bluetooth" : CallAudio.route == CallAudio.WIRED ? "Headset" : "Speaker";
            want.add(new Object[]{spk || CallAudio.route >= 2 ? "call.speaker" : "call.speakerOff", spkLabel, spk || CallAudio.route >= 2, (Runnable) Calls::speakerTap, spkLabel});
            want.add(new Object[]{"call.screen", scr ? "Stop sharing" : "Share screen", scr, (Runnable) Calls::toggleScreen, scr ? "Stop sharing" : "Share screen"});
            want.add(new Object[]{"call.sound", "Share sound (tab audio)", false, (Runnable) Calls::shareSound, "Share sound"});
            if (camOn && m.canSwitchCam()) want.add(new Object[]{"call.flip", "Flip camera", false, (Runnable) Calls::flipCam, "Flip camera"});
            want.add(new Object[]{"call.sliders", "Audio settings", "audio".equals(ui.pop), (Runnable) () -> ui.togglePop("audio"), "Audio settings"});
            while (extras.size() < want.size()) {
                Btn b = new Btn(getContext());
                TextView l = W.text(getContext(), "", 11.5f, 500, 0xd9ffffff);
                l.setGravity(Gravity.CENTER_HORIZONTAL);
                l.setSingleLine();
                l.setEllipsize(TextUtils.TruncateAt.END);
                extras.add(b);
                labels.add(l);
                addView(b);
                addView(l);
            }
            while (extras.size() > want.size()) {
                removeView(extras.remove(extras.size() - 1));
                removeView(labels.remove(labels.size() - 1));
            }
            for (int i = 0; i < want.size(); i++) {
                Object[] w = want.get(i);
                Btn b = extras.get(i);
                b.set((String) w[0], (String) w[1], (Boolean) w[2], false);
                final Runnable r = (Runnable) w[3];
                b.setOnClickListener(v -> r.run());
                labels.get(i).setText((String) w[4]);
            }
            for (int i = 0; i < extras.size(); i++) {
                extras.get(i).setVisibility(open01 > 0 || ui.drawerOpen ? VISIBLE : GONE);
                labels.get(i).setVisibility(open01 > 0 || ui.drawerOpen ? VISIBLE : GONE);
            }
            chev.setRotation(ui.drawerOpen ? 180 : 0);
            chev.setBackground(W.round(ui.drawerOpen ? 0x1affffff : 0x29ffffff, 999));
            tab.setContentDescription("More call options");
            requestLayout();
            invalidate();
        }

        void micSet(boolean on) {
            if (mic.icon != null && mic.on == on) return;
            mic.title = on ? "Mute" : "Unmute";
            mic.setContentDescription(mic.title);
            mic.icon = on ? "call.mic" : "call.micOff";
            mic.on = on;
            mic.setBackground(W.oval(on ? T.accent : T.surface3));
            mic.iv.setImageDrawable(Ic.get(mic.icon, 22 * 1.14f, on ? T.accentFg : T.text));
        }

        void animateDrawer(boolean open) {
            if (va != null) va.cancel();
            va = android.animation.ValueAnimator.ofFloat(open01, open ? 1 : 0);
            va.setDuration(250);
            va.addUpdateListener(x -> {
                open01 = (float) x.getAnimatedValue();
                invalidate();
            });
            va.start();
            chev.animate().rotation(open ? 180 : 0).setDuration(250).start();
            if (open) for (int i = 0; i < extras.size(); i++) {
                for (View v : new View[]{extras.get(i), labels.get(i)}) {
                    v.setVisibility(VISIBLE);
                    v.setAlpha(0);
                    v.setTranslationY(T.dpf(10));
                    v.animate().alpha(1).translationY(0).setDuration(220).start();
                }
            }
            else {
                open01 = 0;
                update();
            }
        }

        int padB() {
            return T.dp(ui.bot() + 12);
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int W0 = MeasureSpec.getSize(ws);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                int w = v == tab ? W0 - T.dp(20) : v instanceof TextView ? T.dp(84) : v == mic ? T.dp(55) : T.dp(48);
                int h = v == tab ? T.dp(30) : v instanceof TextView ? T.dp(16) : v == mic ? T.dp(55) : T.dp(48);
                v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
            int h = padB() + T.dp(48) + T.dp(38) + (ui.drawerOpen ? T.dp(82) : 0);
            setMeasuredDimension(W0, h);
        }

        @Override
        protected void onLayout(boolean ch, int l, int t, int r, int b) {
            int W0 = r - l, H0 = b - t;
            float inner = W0 - T.dp(20), col = inner / 60f;
            int mainTop = H0 - padB() - T.dp(48);
            Btn[] main = {cam, deaf, mic, end, min};
            for (int i = 0; i < 5; i++) {
                float cx = T.dp(10) + col * (12 * i + 6);
                View v = main[i];
                int w = v.getMeasuredWidth();
                int top = mainTop + (T.dp(48) - v.getMeasuredHeight()) / 2;
                v.layout(Math.round(cx - w / 2f), top, Math.round(cx - w / 2f) + w, top + v.getMeasuredHeight());
            }
            int extTop = mainTop - T.dp(82);
            int n = extras.size();
            for (int i = 0; i < n; i++) {
                float cx = T.dp(10) + inner / n * (i + .5f);
                Btn bt = extras.get(i);
                bt.layout(Math.round(cx) - T.dp(24), extTop, Math.round(cx) + T.dp(24), extTop + T.dp(48));
                TextView lb = labels.get(i);
                lb.layout(Math.round(cx) - T.dp(42), extTop + T.dp(54), Math.round(cx) + T.dp(42), extTop + T.dp(70));
            }
            int tabTop = (ui.drawerOpen ? extTop : mainTop) - T.dp(38);
            tab.layout(T.dp(10), tabTop, W0 - T.dp(10), tabTop + T.dp(30));
        }

        RectF drawerRect() {
            float top = tab.getTop();
            dr.set(0, getTop() + top, getWidth(), getBottom());
            return dr;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (open01 <= 0) return;
            float top = tab.getTop();
            bg.setColor(T.mixA(0xff0b0d18, .62f * open01));
            Path p = new Path();
            float r = T.dpf(26);
            p.addRoundRect(new RectF(0, top, getWidth(), getHeight()), new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
            sh.setShader(new android.graphics.LinearGradient(0, top - T.dpf(34), 0, top, new int[]{0, T.mixA(0xff000000, .2f * open01)}, null, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, top - T.dpf(34), getWidth(), top, sh);
            c.drawPath(p, bg);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            return swipe(e, true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            swipe(e, false);
            return true;
        }

        boolean swipe(MotionEvent e, boolean intercept) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dy0 = e.getY();
                    dx0 = e.getX();
                    swiping = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getY() - dy0;
                    if (!swiping && Math.abs(dy) > T.dp(28) && Math.abs(dy) > Math.abs(e.getX() - dx0)) {
                        swiping = true;
                        if (dy < 0 && !ui.drawerOpen && !extras.isEmpty()) ui.setDrawer(true);
                        else if (dy > 0 && ui.drawerOpen) ui.setDrawer(false);
                        return true;
                    }
                    return swiping;
                }
            }
            return false;
        }
    }
}
