package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class EmptyCard {
    static View view(ChatScreen s) {
        Context c = s.a;
        LinearLayout card = W.col(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(T.dp(20), T.dp(16), T.dp(20), T.dp(12));
        card.setBackground(W.round(T.dark ? 0x0fffffff : 0x0d000000, 16));
        TextView t = W.text(c, "No messages here yet...", 14, 600, T.text);
        t.setGravity(Gravity.CENTER);
        card.addView(t, W.lpm(W.WC, W.WC, 0, 0, 0, 4));
        boolean can = s.composer.canSend();
        TextView d = W.text(c, can ? "Send a message or tap the greeting below." : "Nobody has written here yet.", 13, 400, ChatScreen.wpMuted());
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(0, 1.3f);
        card.addView(d);
        if (can) {
            FrameLayout hi = new FrameLayout(c);
            hi.setContentDescription("Send a wave");
            View e = Anim.emoji(c, "👋", 88, s);
            hi.addView(e, new FrameLayout.LayoutParams(T.dp(96), T.dp(96), Gravity.CENTER));
            hi.setOnClickListener(v -> s.composer.sendText("👋"));
            W.press(hi, .94f);
            LinearLayout.LayoutParams lp = W.lpm(T.dp(112), T.dp(112), 0, 12, 0, 4);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            card.addView(hi, lp);
        }
        card.setLayoutParams(new FrameLayout.LayoutParams(T.dp(300), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        card.setAlpha(0);
        card.setTranslationY(T.dp(8));
        card.animate().alpha(1).translationY(0).setDuration(300).setInterpolator(new android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start();
        return card;
    }
}
