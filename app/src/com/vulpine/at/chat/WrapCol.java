package com.vulpine.at.chat;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

public class WrapCol extends LinearLayout {
    public static final Object STRETCH = new Object();

    public WrapCol(Context c) {
        super(c);
        setOrientation(VERTICAL);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        super.onMeasure(ws, hs);
        int inner = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE || v.getTag(0x7f0e0001) != STRETCH) continue;
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            int w = inner - lp.leftMargin - lp.rightMargin;
            if (w > 0 && v.getMeasuredWidth() != w) v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(v.getMeasuredHeight(), MeasureSpec.EXACTLY));
        }
    }

    public static View stretch(View v) {
        v.setTag(0x7f0e0001, STRETCH);
        return v;
    }
}
