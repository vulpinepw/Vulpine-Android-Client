package com.vulpine.at.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.widget.ListView;
import com.vulpine.at.ui.T;

public class ClipList extends ListView {
    public int clipTop = -1;
    final Paint fade = new Paint();
    int fadeAt = -1, fadeH;

    public ClipList(Context c) {
        super(c);
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        int top = clipTop >= 0 ? clipTop : getPaddingTop();
        int fh = Math.min(T.dp(28), top);
        int start = top - fh;
        int w = getWidth(), h = getHeight();
        int s = c.save();
        c.clipRect(0, top, w, h);
        super.dispatchDraw(c);
        c.restoreToCount(s);
        if (fh <= 0) return;
        if (fadeAt != start || fadeH != fh) {
            fadeAt = start;
            fadeH = fh;
            fade.setShader(new LinearGradient(0, start, 0, top, 0x00000000, 0xff000000, Shader.TileMode.CLAMP));
        }
        int l = c.saveLayer(0, start, w, top, null);
        c.clipRect(0, start, w, top);
        super.dispatchDraw(c);
        c.drawRect(0, start, w, top, fade);
        c.restoreToCount(l);
    }
}
