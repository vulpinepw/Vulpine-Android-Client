package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class Jumbo {
    public static View view(Context c, String text, int count, ChatScreen s) {
        int size = count == 1 ? 72 : count <= 3 ? 58 : 50;
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, T.dp(2), 0, T.dp(20));
        row.setMinimumWidth(T.dp(92));
        java.text.BreakIterator bi = java.text.BreakIterator.getCharacterInstance();
        bi.setText(text);
        int st = bi.first();
        for (int e = bi.next(); e != java.text.BreakIterator.DONE; st = e, e = bi.next()) {
            String g = text.substring(st, e);
            if (g.trim().isEmpty()) continue;
            View cell = Anim.emoji(c, g, size, s);
            LinearLayout.LayoutParams lp = W.lp(T.dp(size), T.dp(size));
            lp.leftMargin = lp.rightMargin = T.dp(1);
            row.addView(cell, lp);
        }
        return row;
    }
}
