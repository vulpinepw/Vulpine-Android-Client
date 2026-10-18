package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Rt;
import java.util.List;

public final class PinBar {
    static String preview(Chats.Msg m) {
        if (m.text == null) return "🔒 Encrypted message";
        Fmt.Parsed p = Fmt.parse(m.text);
        String cap = (p.body == null ? "" : p.body).replaceAll("(?s)\\|\\|.+?\\|\\|", "▒▒▒▒").replaceAll("(?m)^>+\\s?", "").replaceAll("\\s+", " ").trim();
        if (!p.atts.isEmpty()) {
            Fmt.Att a = p.atts.get(0);
            String l = Fmt.sticker(a) ? "Sticker" : a.image() ? "Photo" : a.video() ? "Video" : a.audio() ? "Audio" : (a.name.isEmpty() ? "File" : a.name);
            return cap.isEmpty() ? l : l + ", " + cap;
        }
        return cap.isEmpty() ? "Message" : cap;
    }

    static void render(ChatScreen s, FrameLayout host, List<Chats.Msg> pins) {
        Context c = s.a;
        host.removeAllViews();
        host.setBackground(W.round(T.glassBg, 18, T.glassStroke, .5f));
        host.setElevation(T.dpf(5));
        int L = pins.size();
        int P = L - 1 - Math.min(s.pinCycle % L, L - 1);
        Chats.Msg m = pins.get(P);
        LinearLayout row = W.row(c);
        LinearLayout main = W.row(c);
        main.setPadding(T.dp(12), T.dp(8), T.dp(6), T.dp(8));
        LinearLayout segs = W.col(c);
        int SN = Math.min(L, 4);
        int SW = L <= 4 ? 0 : Math.max(0, Math.min(P - 1, L - 4));
        for (int i = 0; i < SN; i++) {
            View sg = new View(c);
            sg.setBackground(W.round(i == P - SW ? T.accent : T.mixA(T.accent, .32f), 2));
            LinearLayout.LayoutParams lp = W.lp(T.dp(2), 0, 1);
            if (i > 0) lp.topMargin = T.dp(2);
            segs.addView(sg, lp);
        }
        main.addView(segs, W.lp(T.dp(2), T.dp(36)));
        LinearLayout tx = W.col(c);
        TextView t = W.text(c, L > 1 ? "Pinned message #" + (P + 1) : "Pinned message", 13, 600, T.accent);
        T.lh(t, 16.25f);
        tx.addView(t);
        TextView pv = W.text(c, preview(m), 14, 400, T.text);
        W.ellipsize(pv);
        T.lh(pv, 18.2f);
        tx.addView(pv);
        main.addView(tx, W.lpm(0, W.WC, 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        main.setOnClickListener(v -> {
            s.jumpTo(m.id);
            s.pinCycle = (s.pinCycle + 1) % L;
            render(s, host, pins);
        });
        row.addView(main, W.lp(0, W.WC, 1));
        ImageView x = new ImageView(c);
        x.setImageDrawable(Svg.stroke(14, T.textSecondary, 2.2f, "M6 6l12 12", "M18 6L6 18"));
        x.setScaleType(ImageView.ScaleType.CENTER);
        x.setContentDescription("Unpin this message");
        x.setBackground(W.ripple(W.oval(0), 99));
        x.setOnClickListener(v -> {
            if (!Rt.I.authed()) {
                Ov.toast("Not connected - try again", true);
                return;
            }
            m.pinned = false;
            Chats.I.pin(s.cid, m.id, false);
            Ov.toast("Message unpinned");
            s.rebuild(false);
        });
        row.addView(x, W.lpm(T.dp(32), T.dp(32), 0, 0, 6, 0));
        host.addView(row, new FrameLayout.LayoutParams(W.MP, W.WC));
    }
}
