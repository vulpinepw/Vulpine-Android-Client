package com.vulpine.at.groups;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.auth.CapWidget;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class Captcha {
    public interface Cb {
        void on(String token);
    }

    public static void ask(Cb cb) {
        App.ui(() -> {
            FrameLayout h = Ov.host();
            if (h == null) {
                cb.on(null);
                return;
            }
            Context c = h.getContext();
            FrameLayout ov = new FrameLayout(c);
            ov.setBackgroundColor(0x9e000000);
            ov.setClickable(true);
            ov.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
            LinearLayout card = W.col(c);
            card.setBackground(W.round(T.vdCard, 16, T.vdBtn, 1));
            card.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
            card.setClickable(true);
            TextView t = W.text(c, "Verify you're human", 15, 600, T.text);
            card.addView(t, W.lpm(W.WC, W.WC, 0, 0, 0, 12));
            final boolean[] done = {false};
            final Runnable[] close = new Runnable[1];
            CapWidget w = new CapWidget(c, tok -> {
                if (tok == null || done[0]) return;
                done[0] = true;
                close[0].run();
                cb.on(tok);
            });
            card.addView(w, W.lp(W.MP, W.WC));
            int sw = h.getWidth() > 0 ? h.getWidth() : c.getResources().getDisplayMetrics().widthPixels;
            FrameLayout.LayoutParams cp = W.flp(Math.min(Math.round(sw * .92f), T.dp(420)), W.WC, Gravity.CENTER);
            ov.addView(card, cp);
            close[0] = () -> {
                Ov.remove(ov);
                ov.animate().alpha(0).setDuration(160).withEndAction(() -> h.removeView(ov)).start();
            };
            Runnable cancel = () -> {
                if (done[0]) return;
                done[0] = true;
                close[0].run();
                cb.on(null);
            };
            ov.setOnClickListener(v -> cancel.run());
            h.addView(ov, new FrameLayout.LayoutParams(W.MP, W.MP));
            ov.setAlpha(0);
            ov.animate().alpha(1).setDuration(T.DUR).start();
            Ov.push(ov, cancel);
        });
    }
}
