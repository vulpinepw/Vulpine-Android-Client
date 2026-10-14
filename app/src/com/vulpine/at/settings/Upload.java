package com.vulpine.at.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.io.File;

public final class Upload {
    public interface Cb {
        void on(Http.Resp r);
    }

    public static void run(String path, String field, File f, String name, String mime, String label, Cb cb) {
        FrameLayout host = Ov.host();
        View[] row = new View[1];
        View[] bar = new View[1];
        TextView[] pct = new TextView[1];
        boolean[] done = {false};
        Runnable show = () -> {
            if (done[0] || host == null) return;
            Context c = host.getContext();
            LinearLayout card = W.col(c);
            card.setBackground(W.round(T.surface, 12, T.border, 1));
            card.setElevation(T.dpf(8));
            card.setPadding(T.dp(14), T.dp(12), T.dp(14), T.dp(12));
            card.addView(W.text(c, label, 13, 600, T.text), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
            FrameLayout track = new FrameLayout(c);
            track.setBackground(W.round(T.surface3, 999));
            View b = new View(c);
            b.setBackground(W.round(T.accent, 999));
            track.addView(b, new FrameLayout.LayoutParams(0, W.MP));
            card.addView(track, W.lp(W.MP, T.dp(6)));
            TextView p = W.text(c, "0%", 13, 400, T.textMuted);
            card.addView(p, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
            MainActivity a = MainActivity.cur;
            FrameLayout.LayoutParams lp = W.flp(W.MP, W.WC, Gravity.BOTTOM);
            lp.leftMargin = lp.rightMargin = T.dp(16);
            lp.bottomMargin = T.dp(24) + (a == null ? 0 : a.insetBottom);
            host.addView(card, lp);
            row[0] = card;
            bar[0] = b;
            pct[0] = p;
        };
        App.H.postDelayed(show, 250);
        App.bg(() -> {
            Http.Resp r = Http.upload(path, field, f, name, mime, (sent, total) -> App.ui(() -> {
                if (bar[0] == null || total <= 0) return;
                int pc = (int) Math.max(0, Math.min(100, Math.round(sent * 100.0 / total)));
                View parent = (View) bar[0].getParent();
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bar[0].getLayoutParams();
                p.width = parent.getWidth() * pc / 100;
                bar[0].setLayoutParams(p);
                pct[0].setText(pc + "%");
            }));
            App.ui(() -> {
                done[0] = true;
                App.H.removeCallbacks(show);
                if (row[0] != null && host != null) host.removeView(row[0]);
                cb.on(r);
            });
        });
    }
}
