package com.vulpine.at.call;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class Btn extends FrameLayout {
    final ImageView iv;
    public String title = "";
    boolean on, danger;
    String icon;

    public Btn(Context c) {
        super(c);
        iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        addView(iv, new LayoutParams(W.MP, W.MP, Gravity.CENTER));
        W.press(this, .92f);
    }

    public Btn set(String icon, String title, boolean on, boolean danger) {
        this.title = title;
        setContentDescription(title);
        if (icon.equals(this.icon) && on == this.on && danger == this.danger && getBackground() != null) return this;
        this.icon = icon;
        this.on = on;
        this.danger = danger;
        int bg = danger ? T.danger : on ? T.accent : T.surface3;
        int fg = danger ? 0xffffffff : on ? T.accentFg : T.text;
        setBackground(W.oval(bg));
        Drawable d = Ic.get(icon, 22, fg);
        iv.setImageDrawable(d);
        return this;
    }
}
