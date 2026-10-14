package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class VoicePill {
    static void render(ChatScreen s, FrameLayout host, Voice.Track t) {
        Context c = s.a;
        host.removeAllViews();
        host.setBackground(W.round(T.glassBg, 18, T.glassStroke, .5f));
        host.setElevation(T.dpf(5));
        LinearLayout row = W.row(c);
        row.setPadding(T.dp(8), T.dp(6), T.dp(6), T.dp(6));
        FrameLayout pp = new FrameLayout(c);
        pp.setBackground(W.oval(T.accent));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(t.playing ? Svg.fill(16, T.accentFg, "M6 5h4v14H6zM14 5h4v14h-4z") : Svg.fill(16, T.accentFg, "M8 5v14l11-7z"));
        ic.setScaleType(ImageView.ScaleType.CENTER);
        pp.addView(ic, new FrameLayout.LayoutParams(W.MP, W.MP));
        pp.setOnClickListener(v -> {
            if (t.playing) Voice.pause(t);
            else Voice.play(null, t);
        });
        row.addView(pp, W.lp(T.dp(30), T.dp(30)));
        LinearLayout main = W.col(c);
        main.setPadding(T.dp(4), 0, T.dp(4), 0);
        TextView n = W.text(c, t.name == null ? "Voice message" : t.name, 13, 600, T.accent);
        W.ellipsize(n);
        main.addView(n);
        int ct = t.mp != null && t.prepared ? t.mp.getCurrentPosition() : 0;
        TextView sb = W.text(c, (t.sender == null ? "Voice message" : t.sender) + " · " + Fmt.dur(ct / 1000.0) + (t.durMs > 0 ? " / " + Fmt.dur(t.durMs / 1000.0) : ""), 14, 400, T.textSecondary);
        sb.setFontFeatureSettings("tnum");
        W.ellipsize(sb);
        main.addView(sb);
        main.setOnClickListener(v -> {
            if (t.mid != null) ChatScreen.gotoMsg(t.cid, t.mid);
        });
        row.addView(main, W.lpm(0, W.WC, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) main.getLayoutParams()).weight = 1;
        TextView rate = W.text(c, (t.rate == 1.25f ? "1.25" : String.valueOf((int) t.rate)) + "x", 10, 700, T.text);
        rate.setPadding(T.dp(6), T.dp(2), T.dp(6), T.dp(2));
        rate.setBackground(W.round(0, 99, T.text, 1));
        rate.setAlpha(.7f);
        rate.setOnClickListener(v -> {
            float[] sp = {1f, 1.25f, 2f};
            int i = 0;
            for (int k = 0; k < sp.length; k++) if (sp[k] == t.rate) i = k;
            t.rate = sp[(i + 1) % sp.length];
            Voice.applyRate(t);
            Voice.fire();
        });
        row.addView(rate, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
        ImageView x = new ImageView(c);
        x.setImageDrawable(Svg.stroke(18, T.textSecondary, 2.2f, "M6 6l12 12", "M18 6L6 18"));
        x.setScaleType(ImageView.ScaleType.CENTER);
        x.setContentDescription("Close");
        x.setOnClickListener(v -> Voice.stop());
        row.addView(x, W.lpm(T.dp(32), T.dp(32), 6, 0, 0, 0));
        host.addView(row, new FrameLayout.LayoutParams(W.MP, W.WC));
        View track = new View(c);
        track.setBackgroundColor(T.mixA(T.accent, .22f));
        host.addView(track, new FrameLayout.LayoutParams(W.MP, T.dp(2), Gravity.BOTTOM));
        View fill = new View(c);
        fill.setBackgroundColor(T.accent);
        fill.setPivotX(0);
        fill.setScaleX(t.durMs > 0 ? ct / (float) t.durMs : 0);
        host.addView(fill, new FrameLayout.LayoutParams(W.MP, T.dp(2), Gravity.BOTTOM));
        if (t.playing) host.postDelayed(() -> {
            if (host.isAttachedToWindow() && Voice.cur == t) render(s, host, t);
        }, 500);
    }
}
