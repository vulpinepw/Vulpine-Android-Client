package com.vulpine.at.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class Skeleton {
    static View view(Context c) {
        LinearLayout col = W.col(c);
        col.setPadding(T.dp(16), T.dp(8), T.dp(16), T.dp(8));
        int[] w = {62, 44, 70, 38, 56, 48}, h = {40, 58, 40, 76, 40, 52};
        int sw = c.getResources().getDisplayMetrics().widthPixels - T.dp(32);
        for (int i = 0; i < 6; i++) {
            View b = new View(c);
            b.setBackground(W.round(T.surface2, 18));
            LinearLayout.LayoutParams lp = W.lp(Math.round(sw * w[i] / 100f), T.dp(h[i]));
            lp.gravity = i % 2 == 1 ? Gravity.END : Gravity.START;
            lp.topMargin = i > 0 ? T.dp(8) : 0;
            col.addView(b, lp);
        }
        ValueAnimator va = ValueAnimator.ofFloat(.35f, .6f);
        va.setDuration(700);
        va.setRepeatMode(ValueAnimator.REVERSE);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.addUpdateListener(x -> col.setAlpha((float) x.getAnimatedValue()));
        va.start();
        col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {
            }

            public void onViewDetachedFromWindow(View v) {
                va.cancel();
            }
        });
        return col;
    }
}
