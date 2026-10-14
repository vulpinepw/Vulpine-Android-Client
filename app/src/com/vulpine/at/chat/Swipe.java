package com.vulpine.at.chat;

import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;

public final class Swipe {
    static float x0, y0;
    static boolean claimed, dead;
    static View bubble;
    static Chats.Msg msg;
    static ImageView hint;

    static boolean active() {
        return claimed;
    }

    static boolean hScroller(View v, float rx, float ry) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        if (rx < l[0] || ry < l[1] || rx >= l[0] + v.getWidth() || ry >= l[1] + v.getHeight()) return false;
        if (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)) return true;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) if (hScroller(g.getChildAt(i), rx, ry)) return true;
        }
        return false;
    }

    static boolean intercept(ChatScreen s, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = e.getX();
                y0 = e.getY();
                claimed = false;
                dead = s.selectMode;
                msg = s.msgAt(e.getX(), e.getY());
                bubble = s.bubbleAt(e.getX(), e.getY());
                if (msg == null || bubble == null) dead = true;
                else {
                    int[] ll = new int[2];
                    s.list.getLocationOnScreen(ll);
                    if (hScroller(bubble, e.getX() + ll[0], e.getY() + ll[1])) dead = true;
                }
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (dead || claimed) return claimed;
                float dx = e.getX() - x0, dy = e.getY() - y0;
                if (Math.abs(dy) > T.dp(8) && Math.abs(dy) > Math.abs(dx)) {
                    dead = true;
                    return false;
                }
                if (-dx > T.dp(12) && -dx > 1.5f * Math.abs(dy)) {
                    claimed = true;
                    s.list.requestDisallowInterceptTouchEvent(true);
                    hint = new ImageView(s.a);
                    hint.setImageDrawable(Svg.stroke(18, 0xffffffff, 2, "M9 14L4 9l5-5", "M20 20v-7a4 4 0 0 0-4-4H4"));
                    hint.setScaleType(ImageView.ScaleType.CENTER);
                    hint.setBackground(W.oval(T.accent));
                    hint.setElevation(T.dpf(6));
                    int[] bl = new int[2], rl = new int[2];
                    bubble.getLocationInWindow(bl);
                    s.root.getLocationInWindow(rl);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(T.dp(34), T.dp(34));
                    lp.leftMargin = Math.min(s.root.getWidth() - T.dp(42), bl[0] - rl[0] + bubble.getWidth() + T.dp(10));
                    lp.topMargin = bl[1] - rl[1] + bubble.getHeight() / 2 - T.dp(17);
                    s.root.addView(hint, lp);
                    hint.setAlpha(.25f);
                    hint.setScaleX(.5f);
                    hint.setScaleY(.5f);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    static boolean touch(ChatScreen s, MotionEvent e) {
        float dx = Math.max(0, x0 - e.getX());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float vis = dx <= T.dp(96) ? dx : T.dp(96) + (dx - T.dp(96)) * .2f;
                bubble.setTranslationX(-vis);
                if (hint != null) hint.setTranslationX(-vis * .4f);
                float p = Math.min(1f, dx / T.dp(64));
                if (hint != null) {
                    hint.setAlpha(.25f + .75f * p);
                    hint.setScaleX(.5f + .5f * p);
                    hint.setScaleY(.5f + .5f * p);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                bubble.animate().translationX(0).setDuration(220).setInterpolator(new android.view.animation.PathInterpolator(.22f, .61f, .36f, 1f)).start();
                ImageView h = hint;
                if (h != null) h.animate().alpha(0).setDuration(160).withEndAction(() -> s.root.removeView(h)).start();
                hint = null;
                claimed = false;
                if (e.getActionMasked() == MotionEvent.ACTION_UP && dx >= T.dp(64) && msg != null) s.composer.reply(msg);
                return true;
            }
        }
        return true;
    }
}
