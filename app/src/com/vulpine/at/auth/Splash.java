package com.vulpine.at.auth;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class Splash extends Screen {
    TextView t;

    public View build() {
        Context c = a;
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundColor(T.bg);
        LinearLayout col = W.col(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        t = W.text(c, "Loading", 14, 600, T.textMuted);
        t.setLetterSpacing(.02f);
        col.addView(t);
        col.addView(new W.LoadBar(c), W.lpm(T.dp(130), T.dp(3), 0, 18, 0, 0));
        f.addView(col, W.flp(W.WC, W.WC, Gravity.CENTER));
        App.H.postDelayed(() -> {
            if (t.isAttachedToWindow()) t.setText("Still loading…");
        }, 3500);
        App.H.postDelayed(() -> {
            if (t.isAttachedToWindow()) t.setText("This is taking longer than usual…");
        }, 15500);
        return f;
    }
}
