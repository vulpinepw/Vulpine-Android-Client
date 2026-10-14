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
    static LinearLayout bar, badge;
    static LinearLayout lockPanel;
    static boolean locked, holding;
    static float y0;
    static Runnable ticker;
    static Composer owner;

    static void attach(Composer cp, ImageView mic) {
        mic.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    holding = true;
                    locked = false;
                    y0 = e.getRawY();
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
                    if (!locked && Voice.Recorder.active && y0 - e.getRawY() > T.dp(56)) lock(cp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holding = false;
                    if (!locked) finish(cp, e.getActionMasked() == MotionEvent.ACTION_UP);
                    return true;
            }
            return false;
        });
    }

    static void start(Composer cp) {
        if (!Voice.Recorder.start(cp.s.a)) return;
        owner = cp;
        Context c = cp.s.a;
        bar = pill(c, "slide up to lock");
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bp.bottomMargin = T.dp(84) + cp.navH;
        cp.s.root.addView(bar, bp);
        ticker = new Runnable() {
            public void run() {
                long ms = Voice.Recorder.elapsed();
                for (LinearLayout p : new LinearLayout[]{bar, badge}) if (p != null) ((TextView) p.getChildAt(1)).setText(Fmt.dur(ms / 1000.0));
                if (Voice.Recorder.active) App.H.postDelayed(this, 200);
            }
        };
        App.H.post(ticker);
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

    static void lock(Composer cp) {
        locked = true;
        Context c = cp.s.a;
        if (bar != null) cp.s.root.removeView(bar);
        bar = null;
        badge = pill(c, "locked");
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bp.bottomMargin = T.dp(84) + cp.navH;
        cp.s.root.addView(badge, bp);
        lockPanel = W.col(c);
        ImageView trash = new ImageView(c);
        trash.setImageDrawable(Svg.stroke(22, 0xffffffff, 2, "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"));
        trash.setScaleType(ImageView.ScaleType.CENTER);
        trash.setBackground(W.oval(0xffef4444));
        trash.setElevation(T.dpf(8));
        trash.setContentDescription("Cancel");
        trash.setOnClickListener(v -> finish(cp, false));
        lockPanel.addView(trash, W.lp(T.dp(56), T.dp(56)));
        ImageView snd = new ImageView(c);
        snd.setImageDrawable(Svg.stroke(22, T.accentFg, 2.4f, "M22 2 11 13", "M22 2 15 22l-4-9-9-4 20-7z"));
        snd.setScaleType(ImageView.ScaleType.CENTER);
        snd.setBackground(W.accentBg(999));
        snd.setElevation(T.dpf(8));
        snd.setContentDescription("Send");
        snd.setOnClickListener(v -> finish(cp, true));
        lockPanel.addView(snd, W.lpm(T.dp(56), T.dp(56), 0, 14, 0, 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = T.dp(18);
        lp.bottomMargin = T.dp(84) + cp.navH;
        cp.s.root.addView(lockPanel, lp);
        cp.s.list.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    static void clearUi(Composer cp) {
        for (View v : new View[]{bar, badge, lockPanel}) if (v != null && v.getParent() != null) ((FrameLayout) v.getParent()).removeView(v);
        bar = badge = lockPanel = null;
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
