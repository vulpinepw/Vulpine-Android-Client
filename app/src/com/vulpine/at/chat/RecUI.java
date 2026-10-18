package com.vulpine.at.chat;

import android.Manifest;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.io.File;

public final class RecUI {
    static LinearLayout bar, badge, row;
    static TextView rowTime, rowHint;
    static final java.util.List<View> hidden = new java.util.ArrayList<>();
    static LinearLayout lockPanel;
    static boolean locked, holding, lockGesture;
    static ImageView micV;
    static android.graphics.drawable.Drawable micIcon;
    static View lockHint;
    static final String[] SEND = {"M22 2 11 13", "M22 2 15 22l-4-9-9-4 20-7z"};
    static final String[] TRASH = {"M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"};
    static final String[] LOCK = {"M7 11V7a5 5 0 0 1 10 0v4"};
    static float y0, x0;
    static boolean cancelled;
    static Runnable ticker;
    static Composer owner;

    static void attach(Composer cp, ImageView mic) {
        micV = mic;
        mic.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (locked) {
                        lockGesture = false;
                        return true;
                    }
                    holding = true;
                    locked = false;
                    cancelled = false;
                    y0 = e.getRawY();
                    x0 = e.getRawX();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    App.requestPerms(cp.s.a, new String[]{Manifest.permission.RECORD_AUDIO}, ok -> {
                        if (!ok) {
                            com.vulpine.at.ui.Ov.toast("Microphone access denied", true);
                            holding = false;
                            return;
                        }
                        if (!holding && !locked) return;
                        start(cp);
                    });
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!locked && !cancelled && Voice.Recorder.active) {
                        float dx = x0 - e.getRawX();
                        if (row != null) row.setTranslationX(-Math.max(0, Math.min(dx, T.dp(120))) * .6f);
                        if (rowHint != null) rowHint.setAlpha(1f - Math.max(0, Math.min(1f, dx / T.dp(120))) * .7f);
                        if (dx > T.dp(120)) {
                            cancelled = true;
                            finish(cp, false);
                            try {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            } catch (Exception ex) {
                            }
                            return true;
                        }
                        float dy = y0 - e.getRawY();
                        if (lockHint != null) lockHint.setTranslationY(-Math.max(0, Math.min(dy, T.dp(56))) * .5f);
                        if (dy > T.dp(56)) {
                            lockGesture = true;
                            lock(cp);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holding = false;
                    if (locked) {
                        if (lockGesture) lockGesture = false;
                        else if (e.getActionMasked() == MotionEvent.ACTION_UP) finish(cp, true);
                        return true;
                    }
                    if (cancelled) {
                        cancelled = false;
                        return true;
                    }
                    finish(cp, e.getActionMasked() == MotionEvent.ACTION_UP);
                    return true;
            }
            return false;
        });
    }

    static void start(Composer cp) {
        if (!Voice.Recorder.start(cp.s.a)) return;
        owner = cp;
        Context c = cp.s.a;
        buildRow(cp, c);
        showLockHint(cp, c);
        ticker = new Runnable() {
            public void run() {
                long ms = Voice.Recorder.elapsed();
                if (rowTime != null) rowTime.setText(Fmt.dur(ms / 1000.0));
                for (LinearLayout p : new LinearLayout[]{bar, badge}) if (p != null) ((TextView) p.getChildAt(1)).setText(Fmt.dur(ms / 1000.0));
                if (Voice.Recorder.active) App.H.postDelayed(this, 200);
            }
        };
        App.H.post(ticker);
    }

    static void buildRow(Composer cp, Context c) {
        hidden.clear();
        for (int i = 0; i < cp.form.getChildCount(); i++) {
            View v = cp.form.getChildAt(i);
            if (v != cp.sendWrap && v.getVisibility() == View.VISIBLE) {
                v.setVisibility(View.GONE);
                hidden.add(v);
            }
        }
        row = W.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(T.dp(10), 0, T.dp(4), 0);
        View dot = new View(c);
        dot.setBackground(W.oval(0xffef4444));
        android.animation.ObjectAnimator pa = android.animation.ObjectAnimator.ofFloat(dot, "alpha", 1f, .3f);
        pa.setDuration(500);
        pa.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pa.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pa.start();
        row.addView(dot, W.lp(T.dp(10), T.dp(10)));
        rowTime = W.text(c, "0:00", 14, 400, T.text);
        rowTime.setFontFeatureSettings("tnum");
        row.addView(rowTime, W.lpm(T.dp(40), W.WC, 10, 0, 0, 0));
        LinearLayout.LayoutParams wp = W.lpm(0, T.dp(30), 6, 0, 6, 0);
        wp.weight = 1;
        row.addView(new Wave(c), wp);
        rowHint = W.text(c, "‹ Slide to cancel", 12, 400, T.textSecondary);
        rowHint.setSingleLine(true);
        row.addView(rowHint, W.lpm(W.WC, W.WC, 4, 0, 0, 0));
        cp.form.addView(row, 0, W.lp(0, T.dp(40), 1));
        row.setAlpha(0f);
        row.setTranslationY(T.dp(4));
        row.animate().alpha(1f).translationY(0).setDuration(160).start();
    }

    static final class Wave extends View {
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final float[] lv = new float[400];
        int n;
        float cur;
        long last;

        Wave(Context c) {
            super(c);
            p.setColor(T.accent);
            last = android.os.SystemClock.uptimeMillis();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float l = Voice.Recorder.level();
            cur = Math.max(l, cur * .85f);
            long now = android.os.SystemClock.uptimeMillis();
            if (now - last >= 70) {
                if (n == lv.length) {
                    System.arraycopy(lv, 1, lv, 0, n - 1);
                    n--;
                }
                lv[n++] = cur;
                last = now;
                cur = l;
            }
            float bw = T.dpf(3), gap = T.dpf(2), step = bw + gap, w = getWidth(), h = getHeight(), rr = bw / 2;
            float shift = Math.min(1f, (now - last) / 70f) * step;
            for (int i = 0; ; i++) {
                float x = w - step - i * step + (step - shift);
                if (x + bw < 0) break;
                int k = n - 1 - i;
                float v = k >= 0 ? lv[k] : 0;
                if (i == 0) v = Math.max(v, cur);
                float bh = Math.max(T.dpf(3), v * (h - T.dpf(6)));
                p.setAlpha(k >= 0 || i == 0 ? 255 : 90);
                c.drawRoundRect(x, (h - bh) / 2, x + bw, (h + bh) / 2, rr, rr, p);
            }
            if (Voice.Recorder.active && isAttachedToWindow()) postInvalidateOnAnimation();
        }
    }

    static LinearLayout pill(Context c, String label) {
        LinearLayout p = W.row(c);
        p.setPadding(T.dp(16), T.dp(10), T.dp(16), T.dp(10));
        p.setBackground(W.round(T.dark ? 0xff161618 : 0xffffffff, 999));
        p.setElevation(T.dpf(12));
        View dot = new View(c);
        dot.setBackground(W.oval(0xffef4444));
        android.animation.ObjectAnimator pa = android.animation.ObjectAnimator.ofFloat(dot, "alpha", 1f, .3f);
        pa.setDuration(500);
        pa.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pa.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pa.start();
        p.addView(dot, W.lp(T.dp(10), T.dp(10)));
        TextView t = W.text(c, "0:00", 14, 400, T.dark ? 0xffffffff : T.text);
        t.setFontFeatureSettings("tnum");
        p.addView(t, W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        TextView l = W.text(c, label, 12, 400, T.dark ? 0xffffffff : T.text);
        l.setAlpha(.6f);
        p.addView(l, W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        return p;
    }

    static void showLockHint(Composer cp, Context c) {
        LinearLayout h = W.col(c);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setPadding(0, T.dp(10), 0, T.dp(8));
        h.setBackground(W.round(T.dark ? 0xff26262a : 0xffffffff, 999));
        h.setElevation(T.dpf(10));
        ImageView lk = new ImageView(c);
        lk.setImageDrawable(Svg.stroke(18, T.text, 2, LOCK).rect(3, 11, 18, 11, 2, false));
        h.addView(lk, W.lp(T.dp(18), T.dp(18)));
        ImageView up = new ImageView(c);
        up.setImageDrawable(Svg.stroke(14, T.textSecondary, 2, "M6 15l6-6 6 6"));
        h.addView(up, W.lpm(T.dp(14), T.dp(14), 0, 6, 0, 0));
        int[] loc = new int[2], root = new int[2];
        cp.sendWrap.getLocationInWindow(loc);
        cp.s.root.getLocationInWindow(root);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(T.dp(40), W.WC, Gravity.TOP | Gravity.START);
        lp.leftMargin = loc[0] - root[0];
        lp.topMargin = loc[1] - root[1] - T.dp(78);
        cp.s.root.addView(h, lp);
        h.setTranslationZ(T.dpf(30));
        h.bringToFront();
        h.setAlpha(0f);
        h.animate().alpha(1f).setDuration(160).start();
        lockHint = h;
    }

    static void lock(Composer cp) {
        locked = true;
        Context c = cp.s.a;
        if (lockHint != null && lockHint.getParent() != null) ((android.view.ViewGroup) lockHint.getParent()).removeView(lockHint);
        lockHint = null;
        if (row != null) row.animate().translationX(0).setDuration(140).start();
        if (rowHint != null) {
            rowHint.setAlpha(1f);
            rowHint.setText("Locked");
            rowHint.setCompoundDrawablePadding(T.dp(4));
            rowHint.setCompoundDrawablesRelativeWithIntrinsicBounds(Svg.stroke(13, T.textSecondary, 2, LOCK).rect(3, 11, 18, 11, 2, false), null, null, null);
        }
        if (row != null) {
            ImageView trash = new ImageView(c);
            trash.setImageDrawable(Svg.stroke(20, T.danger, 2, TRASH));
            trash.setScaleType(ImageView.ScaleType.CENTER);
            trash.setContentDescription("Cancel");
            trash.setOnClickListener(v -> finish(cp, false));
            W.press(trash, .88f);
            row.addView(trash, 0, W.lpm(T.dp(32), T.dp(32), 0, 0, 6, 0));
        }
        if (micV != null) {
            micIcon = micV.getDrawable();
            micV.setImageDrawable(Svg.stroke(18, T.accentFg, 2.2f, SEND));
            micV.setContentDescription("Send voice message");
        }
        cp.s.list.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    static void clearUi(Composer cp) {
        for (View v : new View[]{bar, badge, lockPanel, row, lockHint}) if (v != null && v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
        bar = badge = lockPanel = row = null;
        lockHint = null;
        if (micV != null && micIcon != null) {
            micV.setImageDrawable(micIcon);
            micV.setContentDescription("Record voice message");
        }
        micIcon = null;
        lockGesture = false;
        rowTime = rowHint = null;
        for (View v : hidden) v.setVisibility(View.VISIBLE);
        hidden.clear();
        locked = false;
    }

    static void finish(Composer cp, boolean send) {
        if (!Voice.Recorder.active) {
            clearUi(cp);
            return;
        }
        File f = Voice.Recorder.stop(send);
        clearUi(cp);
        if (f != null) Attach.uploadFile(cp.s, f, f.getName(), f.getName().endsWith(".ogg") ? "audio/ogg" : "audio/mp4", "Voice message", null);
    }

    static void cancel(Composer cp) {
        if (Voice.Recorder.active && owner == cp) {
            Voice.Recorder.stop(false);
            clearUi(cp);
        }
    }
}
