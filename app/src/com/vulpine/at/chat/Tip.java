package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class Tip {
    static float tx, ty;
    static View cur;

    static boolean ping(String handle) {
        return "everyone".equals(handle) || "here".equals(handle);
    }

    static void ping(FrameLayout root, String handle) {
        show(root, "@" + handle, "everyone".equals(handle) ? "Admins use it to notify every member of this group, even people on Do Not Disturb. Muting the group turns it off." : "Admins use it to notify every member who is online right now, even on Do Not Disturb. Muting the group turns it off.");
    }

    static void show(FrameLayout root, String title, String desc) {
        hide();
        Context c = root.getContext();
        LinearLayout tip = W.col(c);
        tip.setGravity(Gravity.CENTER_HORIZONTAL);
        tip.setPadding(T.dp(10), T.dp(5), T.dp(10), T.dp(6));
        tip.setBackground(W.round(T.dark ? 0xff1e1f25 : 0xffffffff, 8));
        tip.setElevation(T.dpf(10));
        TextView t = W.text(c, title, 12, 600, T.text);
        t.setGravity(Gravity.CENTER);
        tip.addView(t, W.lp(W.WC, W.WC));
        TextView d = W.text(c, desc, 11, 400, T.text);
        d.setAlpha(.65f);
        d.setGravity(Gravity.CENTER);
        tip.addView(d, W.lpm(W.WC, W.WC, 0, 1, 0, 0));
        int maxW = Math.min(T.dp(260), root.getWidth() - T.dp(24));
        tip.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        int w = tip.getMeasuredWidth(), h = tip.getMeasuredHeight();
        int[] rl = new int[2];
        root.getLocationInWindow(rl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, W.WC);
        lp.leftMargin = Math.max(T.dp(12), Math.min(root.getWidth() - w - T.dp(12), Math.round(tx - rl[0] - w / 2f)));
        int top = Math.round(ty - rl[1] - h - T.dp(14));
        lp.topMargin = top < T.dp(80) ? Math.round(ty - rl[1] + T.dp(18)) : top;
        root.addView(tip, lp);
        cur = tip;
        tip.setAlpha(0);
        tip.animate().alpha(1).setDuration(150).start();
        Runnable gone = () -> {
            if (cur == tip) hide();
        };
        App.H.postDelayed(gone, 3500);
    }

    static void hide() {
        View v = cur;
        cur = null;
        if (v == null) return;
        v.animate().alpha(0).setDuration(150).withEndAction(() -> {
            if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
        }).start();
    }
}
