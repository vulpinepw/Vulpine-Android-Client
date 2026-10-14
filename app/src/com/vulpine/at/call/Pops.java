package com.vulpine.at.call;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

final class Pops {
    static final String[][] PRESETS = {{"Mango", "#F97316"}, {"Cherry", "#EF4444"}, {"Peach", "#F59E0B"}, {"Kiwi", "#22C55E"}, {"Berry", "#06B6D4"}, {"Blueberry", "#3B82F6"}, {"Plum", "#8B5CF6"}, {"Guava", "#EC4899"}, {"Lychee", "#FF6B9D", "#FFC371", "135"}, {"Dragonfruit", "#FF0080", "#7928CA", "135"}, {"Watermelon", "#84CC16", "#EF4444", "135"}, {"Coconut", "#F5F5DC", "#A0522D", "135"}, {"Passion", "#6B21A8", "#F97316", "135"}, {"Apricot", "#FBBF24", "#F97316", "135"}};
    static final String[] SWATCH = {"#F97316", "#EF4444", "#F59E0B", "#22C55E", "#06B6D4", "#3B82F6", "#8B5CF6", "#EC4899", "#64748B"};

    static LinearLayout card(Context c, int padDp) {
        LinearLayout v = W.col(c);
        v.setBackground(W.round(T.surface, 16));
        v.setElevation(T.dpf(16));
        v.setPadding(T.dp(padDp), T.dp(padDp), T.dp(padDp), T.dp(padDp));
        v.setClickable(true);
        return v;
    }

    static void anim(View v, boolean right) {
        v.setAlpha(0);
        v.setScaleX(.96f);
        v.setScaleY(.96f);
        v.setTranslationY(-T.dpf(6));
        v.post(() -> {
            v.setPivotX(right ? v.getWidth() : v.getWidth() / 2f);
            v.setPivotY(0);
            v.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setDuration(220).setInterpolator(new android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start();
        });
    }

    static View key(CallUi ui) {
        Sig.Call cur = Calls.sig.cur;
        List<Sig.Part> list = new ArrayList<>();
        for (Sig.Part p : cur.parts.values()) if (p.keys != null) list.add(p);
        if (list.isEmpty()) return null;
        boolean grp = cur.parts.size() > 1 || list.size() > 1;
        View v = keyCard(ui, list, grp, false);
        int w = Math.min(T.dp(300), ui.layer.getWidth() - T.dp(32));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, W.WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = T.dp(ui.top() + 18 + 32 + 8);
        v.setLayoutParams(lp);
        anim(v, false);
        return v;
    }

    static View keyCard(CallUi ui, List<Sig.Part> list, boolean grp, boolean tile) {
        Context c = ui.c;
        LinearLayout v = card(c, 16);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = W.text(c, "End-to-end encrypted", 14, 600, T.text);
        T.lh(t, 21);
        v.addView(t, W.lpm(W.WC, W.WC, 0, 0, 0, 10));
        int ix = 0;
        for (Sig.Part p : list) {
            LinearLayout r = W.col(c);
            r.setGravity(Gravity.CENTER_HORIZONTAL);
            if (grp || tile) {
                TextView n = W.text(c, p.name, 13, 400, T.textSecondary);
                T.lh(n, 19.5f);
                r.addView(n, W.lpm(W.WC, W.WC, 0, 0, 0, 4));
            }
            LinearLayout big = W.row(c);
            for (int i = 0; i < 4; i++) {
                View iv = ui.cell(p.keys[i], 34);
                big.addView(iv, W.lpm(T.dp(34), T.dp(34), i == 0 ? 0 : 10, 0, 0, 0));
                CallUi.pop(iv, (ix * 4 + i) * 50);
            }
            r.addView(big, W.lp(W.WC, W.WC));
            v.addView(r, W.lpm(W.WC, W.WC, 0, 0, 0, 10));
            ix++;
        }
        String txt = grp && !tile ? "Each person sees the emoji of their own link with you. If they match on both sides, that link is end-to-end encrypted." : "If " + (list.get(0).name == null ? "the other person" : list.get(0).name) + " sees the same emoji, this call is end-to-end encrypted.";
        TextView tx = W.text(c, txt, 13, 400, T.textSecondary);
        tx.setGravity(Gravity.CENTER);
        T.lh(tx, 18.85f);
        v.addView(tx);
        return v;
    }

    static void tileKey(CallUi ui, CallUi.Tile t) {
        Sig.Part p = Calls.sig.part(t.uid);
        if (p == null || p.keys == null) return;
        List<Sig.Part> l = new ArrayList<>();
        l.add(p);
        View v = keyCard(ui, l, false, true);
        int w = Math.min(T.dp(260), ui.layer.getWidth() - T.dp(32));
        int[] loc = new int[2], h = new int[2];
        t.getLocationInWindow(loc);
        ui.popHost.getLocationInWindow(h);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, W.WC, Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.max(T.dp(16), loc[0] - h[0] + t.getWidth() - T.dp(4) - w);
        lp.topMargin = loc[1] - h[1] + T.dp(4 + 24 + 8);
        v.setLayoutParams(lp);
        anim(v, true);
        ui.showPop("tilekey", v);
    }

    static TextView pillToggle(Context c, boolean on, View.OnClickListener l) {
        TextView b = W.text(c, on ? "On" : "Off", 13, 500, on ? T.accentFg : T.text);
        b.setPadding(T.dp(12), T.dp(4), T.dp(12), T.dp(4));
        T.lh(b, 19.5f);
        b.setPadding(T.dp(12), b.getPaddingTop() + T.dp(4), T.dp(12), b.getPaddingBottom() + T.dp(4));
        b.setBackground(W.round(on ? T.accent : T.surface3, 999));
        b.setOnClickListener(l);
        return b;
    }

    static LinearLayout between(Context c, String label, View right) {
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = W.text(c, label, 14, 400, T.text);
        r.addView(t, W.lp(0, W.WC, 1));
        r.addView(right);
        return r;
    }

    static View audio(CallUi ui) {
        Context c = ui.c;
        CallMedia m = Calls.media;
        LinearLayout v = card(c, 16);
        build(ui, v);
        int w = Math.max(T.dp(320), Math.min(T.dp(400), ui.layer.getWidth() - T.dp(32)));
        w = Math.min(w, ui.layer.getWidth());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w > T.dp(320) ? T.dp(320) : w, W.WC, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = T.dp(90);
        v.setLayoutParams(lp);
        v.setElevation(T.dpf(12));
        v.setAlpha(0);
        v.animate().alpha(1).setDuration(160).start();
        return v;
    }

    static void build(CallUi ui, LinearLayout v) {
        Context c = ui.c;
        CallMedia m = Calls.media;
        v.removeAllViews();
        if (m == null) return;
        Runnable re = () -> build(ui, v);
        TextView t = W.text(c, "Audio settings", 14, 600, T.text);
        T.lh(t, 21);
        v.addView(t, W.lpm(W.MP, W.WC, 0, 0, 0, 4));
        v.addView(between(c, "Noise suppression", pillToggle(c, m.ns, x -> {
            m.ns = !m.ns;
            m.applyAudio(false);
            re.run();
        })), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        if (m.ns) {
            LinearLayout seg = W.row(c);
            seg.setBackground(W.round(T.surface2, 12));
            seg.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            String[] ls = {"Normal", "Aggressive"};
            for (int i = 0; i < 2; i++) {
                boolean on = (i == 1) == m.aggressive;
                TextView b = W.text(c, ls[i], 13, 500, on ? T.text : T.textSecondary);
                b.setGravity(Gravity.CENTER);
                T.lh(b, 19.5f);
                b.setPadding(0, 0, 0, 0);
                if (on) b.setBackground(W.round(T.surface, 999));
                final boolean ag = i == 1;
                b.setOnClickListener(x -> {
                    if (m.aggressive == ag) return;
                    m.aggressive = ag;
                    m.applyAudio(true);
                    re.run();
                });
                seg.addView(b, W.lpm(W.WC, T.dp(28), i == 0 ? 0 : 4, 0, 0, 0));
            }
            v.addView(between(c, "Level", seg), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        }
        v.addView(between(c, "Echo cancellation", pillToggle(c, m.ec, x -> {
            m.ec = !m.ec;
            m.applyAudio(false);
            re.run();
        })), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        v.addView(between(c, "Auto gain control", pillToggle(c, m.agc, x -> {
            m.agc = !m.agc;
            m.applyAudio(false);
            re.run();
        })), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        View div = new View(c);
        div.setBackgroundColor(T.border);
        v.addView(div, W.lpm(W.MP, Math.max(1, T.dp(1)), 0, 16, 0, 0));
        TextView eq = W.text(c, "Equalizer", 14, 500, T.text);
        T.lh(eq, 21);
        v.addView(eq, W.lpm(W.WC, W.WC, 0, 12, 0, 8));
        v.addView(eqRow(c, "Bass", m.eqBass, x -> {
            m.eqBass = x;
            m.applyEq();
        }));
        v.addView(eqRow(c, "Mid", m.eqMid, x -> {
            m.eqMid = x;
            m.applyEq();
        }));
        v.addView(eqRow(c, "Treble", m.eqTreble, x -> {
            m.eqTreble = x;
            m.applyEq();
        }));
    }

    interface IntCb {
        void on(int v);
    }

    static View eqRow(Context c, String label, int val, IntCb cb) {
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = W.text(c, label, 13, 400, T.textSecondary);
        l.setMinWidth(T.dp(44));
        r.addView(l);
        TextView n = W.text(c, (val > 0 ? "+" : "") + val + " dB", 13, 400, T.textSecondary);
        n.setGravity(Gravity.END);
        n.setMinWidth(T.dp(36));
        Slider s = new Slider(c, -12, 12, 1, val, x -> {
            n.setText(((int) x > 0 ? "+" : "") + (int) x + " dB");
            cb.on((int) x);
        });
        r.addView(s, W.lpm(0, T.dp(19.5f), 8, 0, 8, 0));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).weight = 1;
        T.lh(l, 19.5f);
        T.lh(n, 19.5f);
        r.addView(n);
        LinearLayout.LayoutParams p = W.lp(W.MP, W.WC);
        p.bottomMargin = T.dp(8);
        r.setLayoutParams(p);
        return r;
    }

    static final class Slider extends View {
        interface Cb {
            void on(float v);
        }

        final float min, max, step;
        float val;
        final Cb cb;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean vol;

        Slider(Context c, float min, float max, float step, float val, Cb cb) {
            super(c);
            this.min = min;
            this.max = max;
            this.step = step;
            this.val = val;
            this.cb = cb;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = T.dpf(vol ? 8 : 6), cy = h / 2f;
            float x0 = r, x1 = w - r, f = (val - min) / (max - min), x = x0 + (x1 - x0) * f;
            float th = T.dpf(vol ? 6 : 4);
            p.setColor(vol ? T.mixA(T.text, .14f) : T.surface3);
            c.drawRoundRect(x0 - r / 2, cy - th / 2, x1 + r / 2, cy + th / 2, th / 2, th / 2, p);
            if (vol) {
                p.setColor(T.accent);
                c.drawRoundRect(x0 - r / 2, cy - th / 2, x, cy + th / 2, th / 2, th / 2, p);
                p.setColor(T.mixA(T.text, .45f));
                float mx = x0 + (x1 - x0) * .5f;
                c.drawRect(mx - T.dpf(1), cy - th / 2, mx + T.dpf(1), cy + th / 2, p);
            }
            p.setColor(T.accent);
            if (vol) p.setShadowLayer(T.dpf(2), 0, T.dpf(1), 0x59000000);
            c.drawCircle(x, cy, r, p);
            p.clearShadowLayer();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            float r = T.dpf(8), f = Math.max(0, Math.min(1, (e.getX() - r) / (getWidth() - 2 * r)));
            float v = min + Math.round(f * (max - min) / step) * step;
            if (vol && Math.abs(v - 1) < .04f) v = 1;
            if (v != val) {
                val = v;
                invalidate();
                cb.on(v);
            }
            return true;
        }
    }

    static String draftMode;
    static String dCol = "#3B82F6", dFrom = "#1d4f9c", dTo = "#5b2c95";
    static int dAng = 135;

    static JSONObject draft() {
        try {
            if ("color".equals(draftMode)) return new JSONObject().put("mode", "color").put("color", dCol);
            if ("gradient".equals(draftMode)) return new JSONObject().put("mode", "gradient").put("from", dFrom).put("to", dTo).put("angle", dAng);
        } catch (Exception e) {
        }
        return null;
    }

    static View bgEditor(CallUi ui) {
        Context c = ui.c;
        boolean pro = Session.s != null && Session.s.optBoolean("pro");
        JSONObject sv = pro ? Session.settings().optJSONObject("callBg") : null;
        draftMode = sv != null && ("color".equals(sv.optString("mode")) || "gradient".equals(sv.optString("mode"))) ? sv.optString("mode") : "default";
        if (sv != null) {
            if (sv.has("color")) dCol = sv.optString("color");
            if (sv.has("from")) dFrom = sv.optString("from");
            if (sv.has("to")) dTo = sv.optString("to");
            if (sv.has("angle")) dAng = sv.optInt("angle");
        }
        LinearLayout v = card(c, 16);
        int w = Math.min(T.dp(340), ui.layer.getWidth() - T.dp(28));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, W.WC, Gravity.TOP | Gravity.END);
        lp.topMargin = T.dp(ui.top() + 18 + 38 + 8);
        lp.rightMargin = T.dp(14);
        v.setLayoutParams(lp);
        bgBody(ui, v, pro);
        anim(v, true);
        return v;
    }

    static void bgBody(CallUi ui, LinearLayout v, boolean pro) {
        Context c = ui.c;
        v.removeAllViews();
        Runnable re = () -> bgBody(ui, v, pro);
        TextView t = W.text(c, "Call background", 14, 600, T.text);
        T.lh(t, 21);
        v.addView(t, W.lpm(W.WC, W.WC, 0, 0, 0, 4));
        TextView h = W.text(c, "Only you see this background.", 13, 400, T.textMuted);
        T.lh(h, 19.5f);
        v.addView(h, W.lpm(W.WC, W.WC, 0, 0, 0, 12));
        if (!pro) {
            LinearLayout lk = W.row(c);
            lk.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
            lk.setBackground(W.round(T.mixA(T.text, .06f), 12));
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(W.grad(new int[]{0xfff59e0b, 0xfff97316}, 135, 10));
            ImageView li = new ImageView(c);
            li.setImageDrawable(Ic.get("call.lockPro", 18, 0xffffffff));
            ic.addView(li, new FrameLayout.LayoutParams(T.dp(18), T.dp(18), Gravity.CENTER));
            lk.addView(ic, W.lp(T.dp(34), T.dp(34)));
            LinearLayout tx = W.col(c);
            tx.addView(W.text(c, "Pro Box feature", 14, 600, T.text));
            TextView d = W.text(c, "Get Pro Box to customize your call background with colors and gradients.", 13, 400, T.textMuted);
            tx.addView(d, W.lpm(W.WC, W.WC, 0, 2, 0, 0));
            lk.addView(tx, W.lpm(0, W.WC, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
            v.addView(lk);
            return;
        }
        String[][] modes = {{"default", "Default"}, {"color", "Color"}, {"gradient", "Gradient"}};
        int sel = "color".equals(draftMode) ? 1 : "gradient".equals(draftMode) ? 2 : 0;
        W.Segmented seg = new W.Segmented(c, new String[]{"Default", "Color", "Gradient"}, sel, i -> {
            draftMode = modes[i][0];
            applyDraft(ui);
            v.post(re);
        });
        v.addView(seg, W.lpm(W.MP, W.WC, 0, 0, 0, 12));
        CallBg prev = new CallBg(c, false);
        prev.preview = true;
        prev.custom = draft();
        prev.setRadius(T.dpf(12));
        v.addView(prev, W.lpm(W.MP, T.dp(64), 0, 0, 0, 12));
        if (!"default".equals(draftMode)) {
            TextView pt = W.text(c, "Presets", 13, 400, T.textMuted);
            v.addView(pt, W.lpm(W.WC, W.WC, 0, 0, 0, 8));
            LinearLayout grid = W.col(c);
            LinearLayout row = null;
            int per = 5, k = 0;
            for (String[] ps : PRESETS) {
                boolean solid = ps.length == 2;
                if (solid != "color".equals(draftMode)) continue;
                if (k % per == 0) {
                    row = W.row(c);
                    grid.addView(row, W.lpm(W.MP, W.WC, 0, k == 0 ? 0 : 8, 0, 0));
                }
                LinearLayout cell = W.col(c);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                TextView nm = W.text(c, ps[0], 10, 400, T.textSecondary);
                nm.setSingleLine();
                cell.addView(nm, W.lpm(W.WC, W.WC, 0, 0, 0, 4));
                View sw = new View(c);
                boolean on = solid ? dCol.equalsIgnoreCase(ps[1]) : dFrom.equalsIgnoreCase(ps[1]) && dTo.equalsIgnoreCase(ps[2]);
                android.graphics.drawable.GradientDrawable g = solid ? W.oval(Css.color(ps[1]) | 0xff000000) : W.grad(new int[]{Css.color(ps[1]) | 0xff000000, Css.color(ps[2]) | 0xff000000}, 135, 999);
                if (!solid) g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                sw.setBackground(ring(g, on));
                cell.addView(sw, W.lp(T.dp(on ? 32 : 24), T.dp(on ? 32 : 24)));
                if (on) ((LinearLayout.LayoutParams) sw.getLayoutParams()).setMargins(0, -T.dp(4), 0, -T.dp(4));
                cell.setOnClickListener(x -> {
                    if (solid) dCol = ps[1];
                    else {
                        dFrom = ps[1];
                        dTo = ps[2];
                        dAng = Integer.parseInt(ps[3]);
                    }
                    applyDraft(ui);
                    re.run();
                });
                row.addView(cell, W.lp(0, W.WC, 1));
                k++;
            }
            if (row != null) while (row.getChildCount() < per) row.addView(new View(c), W.lp(0, 1, 1));
            v.addView(grid, W.lpm(W.MP, W.WC, 0, 0, 0, 12));
            if ("color".equals(draftMode)) {
                LinearLayout sr = W.row(c);
                sr.setGravity(Gravity.CENTER_VERTICAL);
                for (String s : SWATCH) {
                    View sw = new View(c);
                    boolean on = dCol.equalsIgnoreCase(s);
                    sw.setBackground(ring(W.oval(Css.color(s) | 0xff000000), on));
                    sw.setOnClickListener(x -> {
                        dCol = s;
                        applyDraft(ui);
                        re.run();
                    });
                    sr.addView(sw, W.lpm(T.dp(on ? 36 : 28), T.dp(on ? 36 : 28), on ? 4 : 8, 0, on ? -4 : 0, 0));
                }
                View cust = colorWell(c, dCol, col -> {
                    dCol = col;
                    applyDraft(ui);
                    re.run();
                });
                sr.addView(cust, W.lpm(T.dp(36), T.dp(28), 8, 0, 0, 0));
                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
                hs.setHorizontalScrollBarEnabled(false);
                sr.setPadding(0, T.dp(4), T.dp(4), T.dp(4));
                hs.addView(sr);
                v.addView(hs, W.lpm(W.MP, W.WC, -8, 0, 0, 8));
            } else {
                LinearLayout gr = W.row(c);
                gr.setGravity(Gravity.CENTER_VERTICAL);
                gr.addView(colorWell(c, dFrom, col -> {
                    dFrom = col;
                    applyDraft(ui);
                    re.run();
                }), W.lp(T.dp(36), T.dp(28)));
                gr.addView(colorWell(c, dTo, col -> {
                    dTo = col;
                    applyDraft(ui);
                    re.run();
                }), W.lpm(T.dp(36), T.dp(28), 12, 0, 0, 0));
                TextView deg = W.text(c, dAng + "°", 13, 400, T.textMuted);
                deg.setMinWidth(T.dp(32));
                Slider s = new Slider(c, 0, 360, 1, dAng, x -> {
                    dAng = (int) x;
                    deg.setText(dAng + "°");
                    applyDraft(ui);
                    prev.custom = draft();
                    prev.invalidate();
                });
                gr.addView(s, W.lpm(0, T.dp(24), 12, 0, 12, 0));
                ((LinearLayout.LayoutParams) s.getLayoutParams()).weight = 1;
                gr.addView(deg);
                v.addView(gr, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
            }
        }
        LinearLayout acts = W.row(c);
        acts.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = W.btn(c, "Cancel", false, x -> ui.closePop());
        acts.addView(cancel, W.lp(W.WC, T.dp(36)));
        TextView apply = W.text(c, "Apply", 14, 500, T.text);
        apply.setGravity(Gravity.CENTER);
        apply.setPadding(T.dp(20), 0, T.dp(20), 0);
        apply.setBackground(W.round(T.surface2, 999));
        apply.setOnClickListener(x -> save(ui));
        acts.addView(apply, W.lpm(W.WC, T.dp(36), 8, 0, 0, 0));
        v.addView(acts, W.lpm(W.MP, W.WC, 0, 0, 0, 0));
    }

    static android.graphics.drawable.Drawable ring(android.graphics.drawable.GradientDrawable inner, boolean on) {
        if (!on) return inner;
        android.graphics.drawable.GradientDrawable outer = W.oval(T.accent);
        android.graphics.drawable.GradientDrawable mid = W.oval(T.surface);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{outer, mid, inner});
        ld.setLayerInset(1, T.dp(2), T.dp(2), T.dp(2), T.dp(2));
        ld.setLayerInset(2, T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        return ld;
    }

    interface ColCb {
        void on(String hex);
    }

    static View colorWell(Context c, String hex, ColCb cb) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(W.round(T.surface2, 6, T.border, 1));
        f.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        View in = new View(c);
        in.setBackground(W.round(Css.color(hex) | 0xff000000, 3));
        f.addView(in, new FrameLayout.LayoutParams(W.MP, W.MP));
        f.setOnClickListener(x -> com.vulpine.at.settings.ColorPick.show(c, Css.color(hex) | 0xff000000, col -> cb.on(String.format("#%06x", col & 0xffffff))));
        return f;
    }

    static void applyDraft(CallUi ui) {
        if (ui.bg != null) {
            ui.bg.custom = draft();
            ui.bg.invalidate();
        }
    }

    static void save(CallUi ui) {
        JSONObject b = draft();
        try {
            Session.settings().put("callBg", b == null ? JSONObject.NULL : b);
        } catch (Exception e) {
        }
        Session.save();
        Sync.save();
        Ov.toast(b != null ? "Call background updated" : "Call background reset");
        ui.closePop();
    }

    static final String[] P_USER = {"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8"};
    static final String[] P_MIC = {"M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3", "M6 11a6 6 0 0 0 12 0", "M12 17v4"};
    static final String[] P_VID = {"M3 7h11v10H3z", "M14 10l7-3v10l-7-3"};
    static final String[] P_STREAM = {"M4 4h16v12H4z", "M9 20h6", "M12 16v4"};
    static final String[] P_FULL = {"M8 3H5a2 2 0 0 0-2 2v3", "M16 3h3a2 2 0 0 1 2 2v3", "M16 21h3a2 2 0 0 0 2-2v-3", "M8 21H5a2 2 0 0 1-2-2v-3"};

    static String[] slash(String[] p, boolean on) {
        if (!on) return p;
        String[] o = java.util.Arrays.copyOf(p, p.length + 1);
        o[p.length] = "M4 4l16 16";
        return o;
    }

    static void menuItem(Ov.Menu mm, String label, String[] icon, Runnable run) {
        mm.item(label, com.vulpine.at.ui.Svg.stroke(15, T.text, 1.8f, icon), false, run);
    }

    static void tileMenu(CallUi ui, CallUi.Tile t) {
        Sig.Part p = Calls.sig.part(t.uid);
        if (p == null) return;
        Context c = ui.c;
        Ov.Menu mm = new Ov.Menu(c).ctx();
        mm.scrim.setBackgroundColor(T.dark ? 0x33000000 : 0x14000000);
        LinearLayout vol = W.row(c);
        vol.setGravity(Gravity.CENTER_VERTICAL);
        vol.setPadding(T.dp(12), 0, T.dp(12), 0);
        ImageView vi = new ImageView(c);
        vi.setImageDrawable(Ic.get(p.volume <= 0 ? "call.volOff" : "call.volume", 16, T.textSecondary));
        vol.addView(vi, W.lp(T.dp(16), T.dp(16)));
        TextView pct = W.text(c, Math.round(p.volume * 100) + "%", 13, p.volume > 1 ? 700 : 400, p.volume > 1 ? T.accent : T.textSecondary);
        T.lh(pct, 19.5f);
        pct.setGravity(Gravity.END);
        pct.setMinWidth(T.dp(38));
        Slider s = new Slider(c, 0, 2, .01f, (float) p.volume, x -> {
            p.volume = x;
            pct.setText(Math.round(x * 100) + "%");
            pct.setTextColor(x > 1 ? T.accent : T.textSecondary);
            pct.setTypeface(T.w(x > 1 ? 700 : 400));
            vi.setImageDrawable(Ic.get(x <= 0 ? "call.volOff" : "call.volume", 16, T.textSecondary));
            if (Calls.media != null) Calls.media.applyRemote(p.uid, p.volume, !p.mutedLocally && !p.streamDisabled, !p.videoHidden && !p.streamDisabled);
        });
        s.vol = true;
        s.setContentDescription("Volume for " + (p.name == null ? "this person" : p.name));
        vol.addView(s, W.lpm(0, T.dp(24), 10, 0, 10, 0));
        ((LinearLayout.LayoutParams) s.getLayoutParams()).weight = 1;
        vol.addView(pct);
        mm.card.addView(vol, W.lpm(T.dp(227), T.dp(31.5f), 0, 2, 0, 4));
        menuItem(mm, "View profile", P_USER, () -> {
            Calls.minimize();
            com.vulpine.at.people.Profile.open(ui.a, p.uid);
        });
        menuItem(mm, p.mutedLocally ? "Unmute locally" : "Mute locally", slash(P_MIC, !p.mutedLocally), () -> {
            p.mutedLocally = !p.mutedLocally;
            ui.refresh();
        });
        menuItem(mm, p.videoHidden ? "Show video locally" : "Hide video locally", slash(P_VID, !p.videoHidden), () -> {
            p.videoHidden = !p.videoHidden;
            ui.refresh();
        });
        menuItem(mm, p.streamDisabled ? "Enable stream locally" : "Disable stream locally", slash(P_STREAM, !p.streamDisabled), () -> {
            p.streamDisabled = !p.streamDisabled;
            ui.refresh();
        });
        if (t.showVideo) menuItem(mm, "Fullscreen", P_FULL, () -> fullscreen(ui, t.uid));
        int[] h = new int[2];
        ui.a.host.getLocationOnScreen(h);
        float x = t.downX, y = t.downY;
        if (x < 0) {
            int[] loc = new int[2];
            t.getLocationOnScreen(loc);
            x = loc[0] + t.getWidth() / 2f;
            y = loc[1] + t.getHeight() / 2f;
        }
        ui.closePop();
        ui.menu = mm;
        mm.showAt(Math.round(x - h[0]), Math.round(y - h[1]), false);
    }

    static void fullscreen(CallUi ui, String uid) {
        CallMedia m = Calls.media;
        if (m == null) return;
        FrameLayout f = new FrameLayout(ui.c);
        f.setBackgroundColor(0xff000000);
        View[] ref = new View[1];
        View vv = m.videoView(uid, (w, h) -> {
            int W0 = f.getWidth(), H0 = f.getHeight();
            if (W0 <= 0 || H0 <= 0 || w <= 0 || h <= 0 || ref[0] == null) return;
            float sc = Math.min(W0 / (float) w, H0 / (float) h);
            ref[0].setLayoutParams(new FrameLayout.LayoutParams(Math.round(w * sc), Math.round(h * sc), Gravity.CENTER));
        });
        ref[0] = vv;
        f.addView(vv, new FrameLayout.LayoutParams(W.MP, W.MP, Gravity.CENTER));
        m.bindVideo(vv, uid);
        f.setClickable(true);
        Runnable close = () -> {
            m.unbindVideo(vv);
            Ov.remove(f);
            ui.layer.removeView(f);
        };
        f.setOnClickListener(x -> close.run());
        ui.layer.addView(f, new FrameLayout.LayoutParams(W.MP, W.MP));
        Ov.push(f, close);
    }
}
