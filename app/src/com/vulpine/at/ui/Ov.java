package com.vulpine.at.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import java.util.ArrayList;
import java.util.List;

public final class Ov {
    public interface Layer {
        void close();
    }

    static final List<Object[]> layers = new ArrayList<>();

    public static FrameLayout host() {
        MainActivity a = MainActivity.cur;
        return a == null ? null : a.host;
    }

    public static void push(View v, Runnable onBack) {
        layers.add(new Object[]{v, onBack});
        float z = T.dpf(16);
        FrameLayout h = host();
        if (h != null) for (int i = 0; i < h.getChildCount(); i++) {
            View ch = h.getChildAt(i);
            if (ch != v) z = Math.max(z, ch.getTranslationZ() + 1);
        }
        if (v.getTranslationZ() < z) v.setTranslationZ(z);
    }

    public static void remove(View v) {
        for (int i = layers.size() - 1; i >= 0; i--) if (layers.get(i)[0] == v) layers.remove(i);
    }

    public static boolean back() {
        if (layers.isEmpty()) return false;
        Object[] l = layers.get(layers.size() - 1);
        ((Runnable) l[1]).run();
        return true;
    }

    public static boolean any() {
        return !layers.isEmpty();
    }

    public static void toast(String msg) {
        toast(msg, false);
    }

    public static void toast(String msg, boolean error) {
        App.ui(() -> {
            FrameLayout h = host();
            if (h == null) return;
            Context c = h.getContext();
            TextView t = W.text(c, msg, 13, 600, error ? 0xffffffff : T.text);
            T.lh(t, 19.5f);
            t.setPadding(T.dp(16), t.getPaddingTop() + T.dp(9), T.dp(16), t.getPaddingBottom() + T.dp(9));
            t.setBackground(error ? W.round(0xffef4444, 99) : new W.Pill(T.glassBgStrong, T.glassStroke, .5f, T.dark ? 0x1affffff : 0xbfffffff));
            t.setGravity(Gravity.CENTER);
            t.setMaxWidth(Math.round((h.getWidth() > 0 ? h.getWidth() : c.getResources().getDisplayMetrics().widthPixels) * .92f));
            FrameLayout.LayoutParams p = W.flp(W.WC, W.WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            MainActivity a = MainActivity.cur;
            p.topMargin = (a == null ? 0 : a.insetTop) + T.dp(68);
            h.addView(t, p);
            t.setElevation(T.dpf(30));
            t.setOutlineProvider(null);
            t.setAlpha(0);
            t.setTranslationY(-T.dp(18));
            t.setScaleX(.96f);
            t.setScaleY(.96f);
            t.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(300).setInterpolator(T.EASE).start();
            t.postDelayed(() -> t.animate().alpha(0).setDuration(250).start(), 2000);
            t.postDelayed(() -> h.removeView(t), 2400);
        });
    }

    public static void toastCard(String kind, String msg) {
        App.ui(() -> {
            FrameLayout h = host();
            if (h == null) return;
            Context c = h.getContext();
            LinearLayout t = W.row(c);
            t.setPadding(T.dp(16), T.dp(12), T.dp(16), T.dp(12));
            t.setBackground(new W.Pill(T.glassBgStrong, T.glassStroke, .5f, T.dark ? 0x1affffff : 0xbfffffff, 12));
            boolean ok = "success".equals(kind), bad = "error".equals(kind);
            android.view.View ic;
            if (ok) {
                android.widget.ImageView iv = W.img(c, Svg.stroke(14, 0xffffffff, 2, "M20 6 9 17l-5-5"));
                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER);
                ic = iv;
            } else {
                TextView tv = W.text(c, bad ? "!" : "i", 14, 400, bad ? 0xffffffff : T.accentFg);
                tv.setGravity(Gravity.CENTER);
                ic = tv;
            }
            ic.setBackground(W.oval(ok ? T.success : bad ? T.danger : T.accent));
            t.addView(ic, W.lp(T.dp(24), T.dp(24)));
            TextView b = W.text(c, msg, 14, 400, T.text);
            T.lh(b, 21);
            t.addView(b, W.lpm(0, W.WC, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).weight = 1;
            int sw = h.getWidth() > 0 ? h.getWidth() : c.getResources().getDisplayMetrics().widthPixels;
            t.measure(android.view.View.MeasureSpec.makeMeasureSpec(sw - T.dp(32), android.view.View.MeasureSpec.AT_MOST), android.view.View.MeasureSpec.UNSPECIFIED);
            FrameLayout.LayoutParams p = W.flp(Math.min(sw - T.dp(32), t.getMeasuredWidth() + T.dp(1)), W.WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            MainActivity a = MainActivity.cur;
            p.topMargin = (a == null ? 0 : a.insetTop) + T.dp(78);
            h.addView(t, p);
            t.setElevation(T.dpf(30));
            t.setOutlineProvider(null);
            t.setAlpha(0);
            t.setTranslationX(T.dp(40));
            t.animate().alpha(1).translationX(0).setDuration(T.DUR).setInterpolator(T.EASE).start();
            Runnable out = () -> {
                if (t.getParent() == null || t.getTag() != null) return;
                t.setTag(1);
                t.animate().alpha(0).translationX(T.dp(40)).setDuration(T.DUR_FAST).setInterpolator(T.EASE).withEndAction(() -> h.removeView(t)).start();
            };
            t.setOnClickListener(v -> out.run());
            t.postDelayed(out, 3500);
        });
    }

    public static class Sheet implements Layer {
        public final FrameLayout scrim;
        public final LinearLayout card;
        public final LinearLayout body;
        boolean closed;
        public boolean apk;
        public Runnable onClose;

        public Sheet(Context c) {
            this(c, true);
        }

        public Sheet(Context c, boolean scroll) {
            scrim = new FrameLayout(c);
            scrim.setBackgroundColor(0x99000000);
            scrim.setClickable(true);
            scrim.setOnClickListener(v -> close());
            card = W.col(c);
            GradientDrawable cg = new GradientDrawable();
            cg.setColor(T.surface);
            float r = T.dpf(16);
            cg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            card.setBackground(cg);
            card.setClickable(true);
            MainActivity a = MainActivity.cur;
            int bot = a == null ? 0 : a.insetBottom;
            card.setPadding(T.dp(20), T.dp(12), T.dp(20), T.dp(36) + bot);
            View grab = new View(c);
            grab.setBackground(W.round(T.mixA(T.text, .22f), 3));
            LinearLayout.LayoutParams gp = W.lp(T.dp(36), T.dp(5));
            gp.gravity = Gravity.CENTER_HORIZONTAL;
            gp.bottomMargin = T.dp(12);
            card.addView(grab, gp);
            body = W.col(c);
            if (scroll) {
                ScrollView sv = new ScrollView(c);
                sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                sv.setVerticalScrollBarEnabled(false);
                sv.addView(body);
                card.addView(sv, W.lp(W.MP, W.WC));
            } else card.addView(body, W.lp(W.MP, W.WC));
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            FrameLayout.LayoutParams cp = W.flp(Math.max(0, sw - T.dp(16)), W.WC, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            cp.topMargin = (a == null ? 0 : a.insetTop) + T.dp(12);
            scrim.addView(card, cp);
            scrim.addOnLayoutChangeListener((v, l, t, rr, b, ol, ot, or, ob) -> {
                int w = apk ? Math.min(rr - l, T.dp(440)) : rr - l - T.dp(16);
                FrameLayout.LayoutParams q = (FrameLayout.LayoutParams) card.getLayoutParams();
                if (w > 0 && q.width != w) {
                    q.width = w;
                    card.setLayoutParams(q);
                }
            });
            drag();
        }

        void drag() {
            final float[] st = new float[3];
            final VelocityTracker[] vt = new VelocityTracker[1];
            card.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        st[0] = e.getRawY();
                        st[1] = 0;
                        vt[0] = VelocityTracker.obtain();
                        vt[0].addMovement(e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (vt[0] != null) vt[0].addMovement(e);
                        float dy = e.getRawY() - st[0];
                        if (st[1] == 0 && dy >= T.dp(8)) st[1] = 1;
                        if (st[1] == 1) card.setTranslationY(Math.max(0, dy));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        float dy = e.getRawY() - st[0];
                        float vel = 0;
                        if (vt[0] != null) {
                            vt[0].addMovement(e);
                            vt[0].computeCurrentVelocity(1);
                            vel = vt[0].getYVelocity();
                            vt[0].recycle();
                            vt[0] = null;
                        }
                        if (st[1] == 1 && (dy > T.dp(110) || (vel > .6f * T.density && dy > T.dp(30)))) close();
                        else card.animate().translationY(0).setDuration(260).setInterpolator(T.EASE).start();
                        return true;
                    }
                }
                return false;
            });
        }

        public Sheet title(String t) {
            TextView v = W.text(card.getContext(), t, 20, 600, T.text);
            T.lh(v, 30);
            body.addView(v, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
            return this;
        }

        public Sheet msg(String t) {
            TextView v = W.text(card.getContext(), t, 14, 400, T.textSecondary);
            W.lines(v, 21);
            body.addView(v, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
            return this;
        }

        public Sheet show() {
            FrameLayout h = host();
            if (h == null) return this;
            h.addView(scrim, new FrameLayout.LayoutParams(W.MP, W.MP));
            scrim.setAlpha(0);
            scrim.animate().alpha(1).setDuration(T.DUR_SLOW).start();
            card.setTranslationY(h.getHeight());
            card.post(() -> {
                card.setTranslationY(card.getHeight());
                card.animate().translationY(0).setDuration(T.DUR_SLOW).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
            });
            push(scrim, this::close);
            return this;
        }

        public void close() {
            if (closed) return;
            closed = true;
            remove(scrim);
            W.hideKb(card);
            FrameLayout h = host();
            card.animate().translationY(card.getHeight()).setDuration(260).setInterpolator(T.EASE_IN).start();
            scrim.animate().alpha(0).setDuration(260).withEndAction(() -> {
                if (h != null) h.removeView(scrim);
            }).start();
            if (onClose != null) onClose.run();
        }
    }

    public interface Choice {
        void on();
    }

    public static void confirm(String title, String msg, String ok, boolean danger, Choice yes) {
        App.ui(() -> {
            FrameLayout h = host();
            if (h == null) return;
            Context c = h.getContext();
            Sheet s = new Sheet(c, false);
            s.title(title);
            if (msg != null) s.msg(msg);
            LinearLayout r = W.row(c);
            r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            TextView cancel = W.btn(c, "Cancel", false, v -> s.close());
            TextView go = W.btn(c, ok, true, v -> {
                s.close();
                yes.on();
            });
            if (danger) go.setBackground(W.ripple(W.round(T.danger, 999), 999));
            if (danger) go.setTextColor(0xffffffff);
            r.addView(cancel);
            LinearLayout.LayoutParams gp = W.lp(W.WC, W.WC);
            gp.leftMargin = T.dp(8);
            r.addView(go, gp);
            r.setPadding(0, T.dp(8), 0, T.dp(4));
            s.body.addView(r);
            s.show();
        });
    }

    public static class Menu implements Layer {
        public final FrameLayout scrim;
        public final LinearLayout card;
        boolean closed;

        int st, iPadV = 9, iPadL = 12, iPadR = 16, iGap = 10, iWeight = 400, iH = 0;
        float iLh = 0;

        public Menu(Context c) {
            scrim = new FrameLayout(c);
            scrim.setClickable(true);
            scrim.setOnClickListener(v -> close());
            scrim.setClipChildren(false);
            card = W.col(c);
            card.setBackground(W.round(T.surface, 12));
            card.setElevation(T.dpf(12));
            card.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            card.setClickable(true);
            card.setMinimumWidth(T.dp(176));
        }

        public Menu more() {
            st = 1;
            card.setElevation(0);
            card.setBackground(new W.Pill(T.surface, 0, 0, 0, 12).shadow(0x59000000, 24, 8));
            card.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            card.setMinimumWidth(T.dp(176));
            iPadV = 8;
            iPadL = 12;
            iPadR = 12;
            iGap = 8;
            iWeight = 400;
            iLh = 21;
            return this;
        }

        public Menu dd() {
            st = 2;
            card.setElevation(0);
            card.setBackground(new W.Pill(T.glassBgStrong, T.glassStroke, .5f, T.dark ? 0x1affffff : 0xbfffffff, 12).shadow(T.dark ? 0x4d000000 : 0x1a000000, 28, 8));
            card.setPadding(T.dp(6), T.dp(6), T.dp(6), T.dp(6));
            card.setMinimumWidth(T.dp(236));
            iPadV = 10;
            iPadL = 12;
            iPadR = 12;
            iGap = 10;
            iWeight = 500;
            iLh = 21;
            return this;
        }

        public Menu ctx() {
            st = 3;
            card.setElevation(0);
            card.setBackground(new W.Pill(T.glassBgStrong, T.glassStroke, .5f, T.dark ? 0x1affffff : 0xbfffffff, 14).shadow(T.dark ? 0x4d000000 : 0x1a000000, 28, 8));
            card.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            card.setMinimumWidth(T.dp(180));
            iPadV = 0;
            iPadL = 12;
            iPadR = 12;
            iGap = 8;
            iWeight = 400;
            iH = 36;
            return this;
        }

        public Menu item(String label, android.graphics.drawable.Drawable icon, boolean danger, Runnable on) {
            Context c = card.getContext();
            LinearLayout r = W.row(c);
            r.setPadding(T.dp(iPadL), T.dp(iPadV), T.dp(iPadR), T.dp(iPadV));
            if (st == 0) r.setBackground(W.ripple(new ColorDrawable(0), 8));
            else r.setBackground(W.pressBg(st == 2 ? .08f : .08f, 8));
            if (icon != null) {
                if (icon instanceof Svg.Icon) ((Svg.Icon) icon).color(danger ? T.danger : T.text);
                android.widget.ImageView iv = W.img(c, icon);
                if (st == 3) iv.setAlpha(.85f);
                r.addView(iv, W.lp(icon.getIntrinsicWidth() > 0 && st != 0 ? icon.getIntrinsicWidth() : T.dp(18), icon.getIntrinsicHeight() > 0 && st != 0 ? icon.getIntrinsicHeight() : T.dp(18)));
                r.addView(new View(c), W.lp(T.dp(iGap), 1));
            }
            TextView tv = W.text(c, label, 14, iWeight, danger ? T.danger : T.text);
            if (iLh > 0) T.lh(tv, iLh);
            if (st != 0) tv.setSingleLine(true);
            r.addView(tv);
            r.setOnClickListener(v -> {
                close();
                on.run();
            });
            card.addView(r, W.lp(W.MP, iH > 0 ? T.dp(iH) : W.WC));
            return this;
        }

        public Menu sep() {
            View v = new View(card.getContext()) {
                protected void onMeasure(int w, int h) {
                    setMeasuredDimension(MeasureSpec.getMode(w) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(w) : 0, MeasureSpec.getSize(h));
                }
            };
            v.setBackgroundColor(st == 2 ? T.mixA(T.text, .10f) : T.border);
            LinearLayout.LayoutParams p = W.lp(W.MP, st == 2 ? Math.max(1, T.dp(1)) : Math.max(1, T.dp(.5f)));
            p.setMargins(T.dp(8), T.dp(4), T.dp(8), T.dp(4));
            card.addView(v, p);
            return this;
        }

        public Menu showAt(int x, int y, boolean right) {
            FrameLayout h = host();
            if (h == null) return this;
            h.addView(scrim, new FrameLayout.LayoutParams(W.MP, W.MP));
            FrameLayout.LayoutParams p = W.flp(W.WC, W.WC, Gravity.TOP | (right ? Gravity.END : Gravity.START));
            scrim.addView(card, p);
            card.setVisibility(View.INVISIBLE);
            card.post(() -> {
                int W0 = h.getWidth(), H0 = h.getHeight();
                int cw = card.getWidth(), ch = card.getHeight();
                int left = right ? Math.max(T.dp(8), Math.min(x - cw, W0 - cw - T.dp(8))) : Math.max(T.dp(8), Math.min(x, W0 - cw - T.dp(8)));
                MainActivity ma = MainActivity.cur;
                int sb = ma == null ? 0 : Math.max(ma.insetBottom, ma.insetIme), stp = ma == null ? 0 : ma.insetTop;
                int top = Math.max(stp + T.dp(8), Math.min(y, H0 - ch - T.dp(8) - sb));
                FrameLayout.LayoutParams q = W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START);
                q.leftMargin = left;
                q.topMargin = top;
                card.setLayoutParams(q);
                card.setPivotX(right ? cw : 0);
                card.setPivotY(y > top + ch / 2 ? ch : 0);
                card.setScaleX(.86f);
                card.setScaleY(.86f);
                card.setAlpha(0);
                card.setVisibility(View.VISIBLE);
                card.animate().scaleX(1).scaleY(1).alpha(1).setDuration(st == 2 ? 140 : st == 3 ? 200 : 180).setInterpolator(st == 2 ? T.EASE : st == 3 ? T.POP : new android.view.animation.PathInterpolator(.3f, 1.3f, .5f, 1f)).start();
            });
            push(scrim, this::close);
            return this;
        }

        public void close() {
            if (closed) return;
            closed = true;
            remove(scrim);
            FrameLayout h = host();
            card.animate().scaleX(.92f).scaleY(.92f).alpha(0).setDuration(140).setInterpolator(T.EASE_IN).withEndAction(() -> {
                if (h != null) h.removeView(scrim);
            }).start();
        }
    }

    public static void anim(View v, float from, float to, long dur, ValueAnimator.AnimatorUpdateListener l) {
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(dur);
        va.setInterpolator(T.EASE);
        va.addUpdateListener(l);
        va.start();
    }
}
