package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class Anim {
    public static View emoji(Context c, String glyph, int sizeDp, ChatScreen s) {
        FrameLayout f = new FrameLayout(c);
        TextView t = new TextView(c);
        t.setText(glyph);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDp * .8f * .78f);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        f.addView(t, new FrameLayout.LayoutParams(W.MP, W.MP));
        Lottie.attach(f, t, glyph, sizeDp, s);
        return f;
    }
}
