package com.vulpine.at.stories;

import android.graphics.Paint;
import android.widget.TextView;
import com.vulpine.at.ui.T;

public final class Lh {
    public static void set(TextView t, float lineDp) {
        Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int natural = fm.descent - fm.ascent;
        int target = T.dp(lineDp * T.scale);
        int extra = Math.max(0, target - natural);
        int top = extra / 2;
        t.setLineSpacing(extra, 1f);
        t.setIncludeFontPadding(false);
        t.setPadding(t.getPaddingLeft(), top, t.getPaddingRight(), extra - top);
    }
}
