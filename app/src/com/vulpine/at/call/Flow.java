package com.vulpine.at.call;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import com.vulpine.at.ui.T;
import java.util.ArrayList;
import java.util.List;

public class Flow extends ViewGroup {
    public int gap = T.dp(8);
    final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();

    public Flow(Context c) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(T.dpf(3));
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int W = MeasureSpec.getSize(ws), H = MeasureSpec.getSize(hs);
        int avail = W - getPaddingLeft() - getPaddingRight();
        int x = 0, rowH = 0, total = 0;
        boolean first = true;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            LayoutParams lp = v.getLayoutParams();
            int cw = lp.width > 0 ? Math.min(lp.width, avail) : avail;
            v.measure(MeasureSpec.makeMeasureSpec(cw, lp.width == LayoutParams.WRAP_CONTENT ? MeasureSpec.AT_MOST : MeasureSpec.EXACTLY), lp.height > 0 ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(H, MeasureSpec.AT_MOST));
            int mw = v.getMeasuredWidth();
            if (!first && x + gap + mw > avail) {
                total += rowH + gap;
                x = 0;
                rowH = 0;
                first = true;
            }
            x += (first ? 0 : gap) + mw;
            first = false;
            rowH = Math.max(rowH, v.getMeasuredHeight());
        }
        total += rowH;
        int mode = MeasureSpec.getMode(hs);
        int h = total + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(W, mode == MeasureSpec.EXACTLY ? Math.max(H, h) : mode == MeasureSpec.AT_MOST ? Math.max(Math.min(H, h), h) : h);
    }

    @Override
    protected void onLayout(boolean ch, int l, int t, int r, int b) {
        int W = r - l, H = b - t;
        int avail = W - getPaddingLeft() - getPaddingRight();
        List<List<View>> rows = new ArrayList<>();
        List<View> cur = new ArrayList<>();
        int x = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int mw = v.getMeasuredWidth();
            if (!cur.isEmpty() && x + gap + mw > avail) {
                rows.add(cur);
                cur = new ArrayList<>();
                x = 0;
            }
            x += (cur.isEmpty() ? 0 : gap) + mw;
            cur.add(v);
        }
        if (!cur.isEmpty()) rows.add(cur);
        int total = 0;
        for (List<View> row : rows) {
            int rh = 0;
            for (View v : row) rh = Math.max(rh, v.getMeasuredHeight());
            total += rh;
        }
        total += Math.max(0, rows.size() - 1) * gap;
        int availH = H - getPaddingTop() - getPaddingBottom();
        int y = getPaddingTop() + Math.max(0, (availH - total) / 2);
        for (List<View> row : rows) {
            int rw = 0, rh = 0;
            for (View v : row) {
                rw += v.getMeasuredWidth();
                rh = Math.max(rh, v.getMeasuredHeight());
            }
            rw += (row.size() - 1) * gap;
            int xx = getPaddingLeft() + (avail - rw) / 2;
            for (View v : row) {
                int vy = y + (rh - v.getMeasuredHeight()) / 2;
                v.layout(xx, vy, xx + v.getMeasuredWidth(), vy + v.getMeasuredHeight());
                xx += v.getMeasuredWidth() + gap;
            }
            y += rh + gap;
        }
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (!(v instanceof CallUi.Tile) || v.getVisibility() != VISIBLE || !((CallUi.Tile) v).speaking) continue;
            float s = T.dpf(1.5f);
            rf.set(v.getLeft() - s, v.getTop() - s, v.getRight() + s, v.getBottom() + s);
            ring.setColor(T.success);
            c.drawRoundRect(rf, T.dpf(12) + s, T.dpf(12) + s, ring);
        }
    }
}
