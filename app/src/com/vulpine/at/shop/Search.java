package com.vulpine.at.shop;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class Search extends FrameLayout {
    public final EditText input;

    public Search(Context c, String hint) {
        super(c);
        setBackground(W.round(T.dark ? 0x3d767680 : 0x1f767680, 10));
        input = new EditText(c);
        input.setBackground(null);
        T.text(input, K.SM, 400, T.text);
        input.setHint(hint);
        input.setHintTextColor(T.textMuted);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        input.setPadding(T.dp(36), 0, T.dp(14), 0);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setContentDescription(hint);
        if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(T.accent));
        addView(input, new LayoutParams(W.MP, W.MP));
        Svg.Icon ic = new Svg.Icon(0, 0, 24, 24, T.dp(16), T.textMuted, 2.4f);
        ic.circle(10.5f, 10.5f, 6.5f, false);
        ic.add("M15.5 15.5 21 21", false);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(ic);
        LayoutParams lp = W.flp(T.dp(16), T.dp(16), Gravity.START | Gravity.CENTER_VERTICAL);
        lp.leftMargin = T.dp(12);
        addView(iv, lp);
    }
}
