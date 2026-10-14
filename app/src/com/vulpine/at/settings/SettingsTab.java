package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class SettingsTab extends Home.Tab {
    ScrollView sv;
    LinearLayout list;

    public View build(Context c) {
        sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        list = W.col(c);
        list.setPadding(T.dp(8), 0, T.dp(8), 0);
        sv.addView(list, new FrameLayout.LayoutParams(W.MP, W.WC));
        TextView title = W.text(c, "Settings", 16, 600, T.text);
        T.lh(title, 24);
        title.setPadding(T.dp(4), title.getPaddingTop(), 0, title.getPaddingBottom());
        list.addView(title, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        String group = null;
        LinearLayout grp = null;
        for (String[] r : Settings.ROWS) {
            if (!r[4].equals(group)) {
                group = r[4];
                TextView h = W.text(c, group, 13, 600, T.textMuted);
                h.setLetterSpacing(.04f);
                h.setPadding(T.dp(12), T.dp(4), T.dp(12), T.dp(8));
                T.lh(h, 19.5f);
                h.setPadding(T.dp(12), T.dp(4) + h.getPaddingTop(), T.dp(12), T.dp(8) + h.getPaddingBottom());
                list.addView(h, W.lp(W.MP, W.WC));
                grp = W.col(c);
                grp.setBackground(W.round(T.mixA(T.text, .05f), 16));
                grp.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
                LinearLayout.LayoutParams gp = W.lpm(W.MP, W.WC, 8, 0, 8, 16);
                list.addView(grp, gp);
            }
            View row = row(c, r);
            LinearLayout.LayoutParams rp = W.lp(W.MP, T.dp(52));
            if (grp.getChildCount() > 0) rp.topMargin = T.dp(2);
            grp.addView(row, rp);
        }
        return sv;
    }

    View row(Context c, String[] r) {
        LinearLayout row = W.row(c);
        row.setClipChildren(false);
        row.setPadding(T.dp(12), 0, T.dp(12), 0);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, W.round(T.mixA(T.text, .08f), 12));
        sl.addState(new int[]{}, new ColorDrawable(0));
        row.setBackground(sl);
        FrameLayout ic = new FrameLayout(c);
        Integer col = Css.color(r[3]);
        ic.setBackground(new S.Glossy(col == null ? T.accent : col, !r[2].startsWith("set.")));
        if (r[2].startsWith("set.")) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(Ic.get(r[2], r[2].equals("set.qr") ? 17 : 18, 0xffffffff));
            iv.setScaleType(ImageView.ScaleType.CENTER);
            ic.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
        } else {
            TextView g = W.icon(c, r[2], 17, 0xffffffff);
            ic.addView(g, new FrameLayout.LayoutParams(W.MP, W.MP));
        }
        row.addView(ic, W.lp(T.dp(28), T.dp(28)));
        TextView l = W.text(c, r[1], 14, 500, T.text);
        LinearLayout.LayoutParams lp = W.lp(0, W.WC, 1);
        lp.leftMargin = T.dp(12);
        row.addView(l, lp);
        row.setContentDescription(r[1]);
        row.setClickable(true);
        row.setOnClickListener(v -> Settings.openPage(h.a, r[0]));
        return row;
    }

    public void onInsets(int top, int bottom) {
        android.view.ViewGroup.LayoutParams lp = sv.getLayoutParams();
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            ((android.view.ViewGroup.MarginLayoutParams) lp).topMargin = top;
            sv.setLayoutParams(lp);
            sv.setPadding(0, T.dp(8), 0, bottom);
        } else sv.setPadding(0, top + T.dp(8), 0, bottom);
    }

    public void scrollTop() {
        sv.smoothScrollTo(0, 0);
    }
}
