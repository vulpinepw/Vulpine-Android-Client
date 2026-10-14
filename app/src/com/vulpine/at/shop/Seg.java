package com.vulpine.at.shop;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class Seg extends FrameLayout {
    public interface On {
        void on(int i);
    }

    final View pill;
    final TextView[] tabs;
    int sel;
    final On on;

    public Seg(Context c, String[] labels, int initial, float fs, On on) {
        super(c);
        this.on = on;
        setBackground(W.round(T.surface2, 12));
        setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        pill = new View(c);
        pill.setBackground(W.round(T.surface, 999));
        addView(pill, new LayoutParams(0, T.dp(34)));
        LinearLayout row = W.row(c);
        tabs = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView t = W.text(c, labels[i], fs, 500, T.textSecondary);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setOnClickListener(v -> select(k, true));
            tabs[i] = t;
            LinearLayout.LayoutParams p = W.lp(0, T.dp(34), 1);
            if (i > 0) p.leftMargin = T.dp(4);
            row.addView(t, p);
        }
        addView(row, new LayoutParams(W.MP, T.dp(34)));
        sel = initial;
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place(false));
    }

    void place(boolean anim) {
        TextView t = tabs[sel];
        if (t.getWidth() == 0) return;
        ViewGroup.LayoutParams p = pill.getLayoutParams();
        if (p.width != t.getWidth()) {
            p.width = t.getWidth();
            pill.setLayoutParams(p);
        }
        for (int i = 0; i < tabs.length; i++) tabs[i].setTextColor(i == sel ? T.text : T.textSecondary);
        if (anim) pill.animate().translationX(t.getLeft()).setDuration(160).setInterpolator(T.EASE).start();
        else pill.setTranslationX(t.getLeft());
    }

    public void select(int i, boolean fire) {
        if (i == sel && fire) return;
        sel = i;
        place(true);
        if (fire && on != null) on.on(i);
    }

    public int selected() {
        return sel;
    }
}
