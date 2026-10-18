package com.vulpine.at.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public abstract class Page extends Screen {
    protected String title;
    protected ScrollView sv;
    protected LinearLayout body;
    protected FrameLayout frame;
    protected LinearLayout bar;
    protected TextView titleView;
    protected boolean scroll = true;

    public Page(String title) {
        this.title = title;
    }

    protected abstract void content(LinearLayout body);

    public View build() {
        Context c = a;
        frame = new EdgeFrame(c, this);
        frame.setBackgroundColor(T.bg);
        frame.setElevation(T.dpf(8));
        body = W.col(c);
        body.setPadding(T.dp(16), 0, T.dp(16), 0);
        LinearLayout outer = W.col(c);
        bar = W.row(c);
        ImageView back = new ImageView(c);
        back.setImageDrawable(Svg.stroke(22, T.text, 2, "M15 18l-6-6 6-6"));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackground(W.round(T.mixA(T.surface, .72f), 999, T.mixA(T.text, .12f), .5f));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> onBackButton());
        W.pressNow(back, .88f);
        bar.addView(back, W.lp(T.dp(40), T.dp(40)));
        if (title != null && !title.isEmpty()) {
            titleView = W.text(c, title, 20, 600, T.text);
            W.ellipsize(titleView);
            T.lh(titleView, 30);
            bar.addView(titleView, W.lpm(0, W.WC, 6, 0, 0, 0));
            ((LinearLayout.LayoutParams) titleView.getLayoutParams()).weight = 1;
        }
        bar.setPadding(T.dp(8), 0, T.dp(16), 0);
        outer.addView(bar, W.lpm(W.MP, T.dp(44), 0, 0, 0, 12));
        if (scroll) {
            sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setClipToPadding(false);
            sv.addView(body, new FrameLayout.LayoutParams(W.MP, W.WC));
            outer.addView(sv, W.lp(W.MP, 0, 1));
        } else outer.addView(body, W.lp(W.MP, 0, 1));
        frame.addView(outer, new FrameLayout.LayoutParams(W.MP, W.MP));
        content(body);
        return frame;
    }

    protected void onBackButton() {
        a.onBackPressed();
    }

    public void onInsets(int top, int bottom, int ime) {
        frame.setPadding(0, Math.max(top, T.dp(24)) + T.dp(8), 0, 0);
        int pb = T.dp(20) + Math.max(bottom, T.dp(16)) + ime;
        if (sv != null) sv.setPadding(0, 0, 0, pb);
        else body.setPadding(T.dp(16), 0, T.dp(16), pb);
    }

    public static class EdgeFrame extends FrameLayout {
        final Screen s;
        float x0, y0, lastDx;
        boolean engaged, aborted;
        VelocityTracker vt;
        Screen under;
        android.graphics.drawable.ColorDrawable dim;

        public EdgeFrame(Context c, Screen s) {
            super(c);
            this.s = s;
        }

        boolean scrollsLeft(View v, float rx, float ry) {
            if (v.getVisibility() != VISIBLE) return false;
            int[] l = new int[2];
            v.getLocationOnScreen(l);
            if (rx < l[0] || ry < l[1] || rx >= l[0] + v.getWidth() || ry >= l[1] + v.getHeight()) return false;
            if (v != this && v.canScrollHorizontally(-1)) return true;
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = g.getChildCount() - 1; i >= 0; i--) if (scrollsLeft(g.getChildAt(i), rx, ry)) return true;
            }
            return false;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                x0 = e.getRawX();
                y0 = e.getRawY();
                engaged = false;
                aborted = s.a == null || s.a.stack.size() < 2 || s.a.top() != s || Ov.any() || scrollsLeft(this, x0, y0);
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return false;
            }
            if (aborted) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                vt.addMovement(e);
                float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                if (Math.abs(dy) > T.dp(12) && Math.abs(dy) > 1.15f * Math.abs(dx)) {
                    aborted = true;
                    return false;
                }
                if (dx < -T.dp(10)) {
                    aborted = true;
                    return false;
                }
                if (dx >= T.dp(8) && dx > 1.05f * Math.abs(dy)) {
                    engaged = true;
                    x0 = e.getRawX();
                    begin();
                    return true;
                }
            }
            return false;
        }

        void begin() {
            under = s.a.stack.size() >= 2 ? s.a.stack.get(s.a.stack.size() - 2) : null;
            W.hideKb(this);
            if (under != null && under.root != null) {
                under.root.setVisibility(VISIBLE);
                under.root.setTranslationX(-getWidth() * .25f);
                dim = new android.graphics.drawable.ColorDrawable(0x00000000);
                if (android.os.Build.VERSION.SDK_INT >= 23) under.root.setForeground(dim);
            }
            setElevation(T.dpf(8));
        }

        void follow(float dx) {
            float w = Math.max(1, getWidth());
            setTranslationX(dx);
            float p = dx / w;
            if (under != null && under.root != null) {
                under.root.setTranslationX(-w * .25f * (1 - p));
                if (dim != null) dim.setColor(((int) (0x55 * (1 - p)) << 24));
            }
        }

        void cleanUnder() {
            if (under != null && under.root != null && android.os.Build.VERSION.SDK_INT >= 23) under.root.setForeground(null);
            dim = null;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!engaged) return super.onTouchEvent(e);
            if (vt != null) vt.addMovement(e);
            float dx = Math.max(0, e.getRawX() - x0);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    follow(dx);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float v = 0;
                    if (vt != null) {
                        vt.computeCurrentVelocity(1);
                        v = vt.getXVelocity() / T.density;
                    }
                    engaged = false;
                    float w = getWidth();
                    boolean go = e.getActionMasked() == MotionEvent.ACTION_UP && (dx > w * .24f || (v > .28f && dx > T.dp(12)));
                    if (v < -.3f) go = false;
                    float from = dx, to = go ? w : 0;
                    long dur = (long) Math.max(120, Math.min(300, Math.abs(to - from) / Math.max(1f, w) * 380));
                    android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(from, to);
                    va.setDuration(dur);
                    va.setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f));
                    va.addUpdateListener(x -> follow((float) x.getAnimatedValue()));
                    boolean fgo = go;
                    va.addListener(new android.animation.AnimatorListenerAdapter() {
                        public void onAnimationEnd(android.animation.Animator a) {
                            cleanUnder();
                            setElevation(0);
                            if (fgo && s.a != null && s.a.top() == s) {
                                if (!s.onBack()) s.a.finishSwipe(s);
                                else follow(0);
                            } else if (!fgo && under != null && under.root != null && s.opaque) {
                                under.root.setVisibility(INVISIBLE);
                                under.root.setTranslationX(-getWidth() * .25f);
                            }
                        }
                    });
                    va.start();
                    return true;
                }
            }
            return true;
        }
    }
}
