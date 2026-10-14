package com.vulpine.at.home;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class SearchField extends LinearLayout {
    public final EditText input;
    final TextView label, hidBadge;
    final ImageView clear, mag;
    final FrameLayout box;
    final ChatsTab tab;
    final FrameLayout hidBtn;

    public SearchField(Context c, ChatsTab tab) {
        super(c);
        this.tab = tab;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        box = new FrameLayout(c);
        box.setBackground(W.round(T.dark ? 0x3d767680 : 0x1f767680, 10));
        input = new EditText(c);
        input.setBackground(null);
        T.text(input, 14, 400, T.text);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(T.dp(34), 0, T.dp(34), 0);
        box.addView(input, new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout lab = W.row(c);
        mag = new ImageView(c);
        mag.setImageDrawable(Svg.stroke(16, T.textMuted, 2.4f, "M15.5 15.5L21 21", "M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z"));
        lab.addView(mag, W.lp(T.dp(16), T.dp(16)));
        label = W.text(c, "Search conversations…", 14, 400, T.textMuted);
        lab.addView(label, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
        lab.setTag("lab");
        box.addView(lab, W.flp(W.WC, W.WC, Gravity.CENTER));
        clear = new ImageView(c);
        clear.setImageDrawable(Svg.stroke(10, T.surface, 1.8f, "M6 6l12 12", "M18 6L6 18"));
        clear.setScaleType(ImageView.ScaleType.CENTER);
        clear.setBackground(W.oval(T.textMuted));
        clear.setContentDescription("Clear search");
        clear.setScaleX(.6f);
        clear.setScaleY(.6f);
        clear.setAlpha(0f);
        clear.setOnClickListener(v -> {
            input.setText("");
            W.showKb(input);
        });
        FrameLayout.LayoutParams cp = W.flp(T.dp(18), T.dp(18), Gravity.END | Gravity.CENTER_VERTICAL);
        cp.rightMargin = T.dp(9);
        box.addView(clear, cp);
        addView(box, W.lp(0, T.dp(36), 1));
        hidBtn = new FrameLayout(c);
        ImageView hi = new ImageView(c);
        hi.setImageDrawable(Ic.get("hidden", 16, T.textSecondary));
        hi.setScaleType(ImageView.ScaleType.CENTER);
        hidBtn.addView(hi, new FrameLayout.LayoutParams(W.MP, W.MP));
        hidBadge = W.text(c, "", 9, 700, T.accent);
        hidBadge.setGravity(Gravity.CENTER);
        hidBadge.setMinWidth(T.dp(13));
        hidBadge.setPadding(T.dp(3), 0, T.dp(3), 0);
        hidBadge.setBackground(W.round(T.accentSoft, 999));
        FrameLayout.LayoutParams hb = W.flp(W.WC, T.dp(13), Gravity.TOP | Gravity.END);
        hb.topMargin = T.dp(3);
        hb.rightMargin = T.dp(2);
        hidBtn.addView(hidBadge, hb);
        hidBtn.setContentDescription("Hidden chats");
        hidBtn.setOnClickListener(v -> tab.setHidden(true));
        addView(hidBtn, W.lpm(T.dp(32), T.dp(38), 4, 0, 0, 0));
        input.setOnFocusChangeListener((v, f) -> {
            update();
            if (f) Spotlight.open(tab, this);
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                tab.query = e.toString();
                update();
                tab.refresh();
                Spotlight.query(tab, e.toString());
                if (!tab.query.isEmpty() && com.vulpine.core.Chats.I.total > com.vulpine.core.Chats.I.order.size()) App.bg(() -> {
                    for (int i = 0; i < 100 && com.vulpine.core.Chats.I.total > com.vulpine.core.Chats.I.order.size(); i++) {
                        if (!com.vulpine.core.Chats.I.loadList(com.vulpine.core.Chats.I.order.size())) break;
                        try {
                            Thread.sleep(80);
                        } catch (InterruptedException x) {
                            break;
                        }
                    }
                });
            }
        });
    }

    void update() {
        boolean f = input.hasFocus() || input.length() > 0;
        View lab = box.findViewWithTag("lab");
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) lab.getLayoutParams();
        int g = f ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER;
        if (p.gravity != g) {
            p.gravity = g;
            p.leftMargin = f ? T.dp(11) : 0;
            lab.setLayoutParams(p);
        }
        label.setAlpha(input.length() > 0 ? 0 : 1);
        boolean show = input.length() > 0;
        clear.animate().alpha(show ? 1 : 0).scaleX(show ? 1 : .6f).scaleY(show ? 1 : .6f).setDuration(180).start();
    }

    void hiddenCount(int n) {
        hidBadge.setVisibility(n > 0 ? VISIBLE : GONE);
        hidBadge.setText(String.valueOf(n));
    }

    void setVsp(float v) {
        float op = Math.max(0, Math.min(1, (v - .35f) / .55f));
        setAlpha(op);
        setScaleX(.9f + .1f * Math.min(1, v));
        setScaleY(.9f + .1f * Math.min(1, v));
    }

    boolean busy() {
        return input.hasFocus() || input.length() > 0;
    }
}
