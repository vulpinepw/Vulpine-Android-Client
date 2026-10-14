package com.vulpine.at.shop;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class Flow extends ViewGroup {
    final int hgap, vgap;
    public boolean center = true;

    public Flow(Context c, int hgapPx, int vgapPx) {
        super(c);
        hgap = hgapPx;
        vgap = vgapPx;
    }

    public static class LP extends ViewGroup.LayoutParams {
        public boolean grow, breakBefore;

        public LP(int w, int h) {
            super(w, h);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LP(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof LP;
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new LP(p.width, p.height);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int maxW = MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            LayoutParams lp = v.getLayoutParams();
            int cw = lp.width == LayoutParams.MATCH_PARENT ? MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.EXACTLY) : lp.width >= 0 ? MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST);
            int ch = lp.height >= 0 ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            v.measure(cw, ch);
            int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
            boolean brk = lp instanceof LP && ((LP) lp).breakBefore;
            if (x > 0 && (x + w > maxW || brk)) {
                x = 0;
                y += lineH + vgap;
                lineH = 0;
            }
            x += w + hgap;
            lineH = Math.max(lineH, h);
        }
        setMeasuredDimension(MeasureSpec.getSize(ws), y + lineH + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = getPaddingTop();
        int start = 0;
        int n = getChildCount();
        while (start < n) {
            int lineH = 0, end = start, xx = 0;
            for (int i = start; i < n; i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) {
                    end = i + 1;
                    continue;
                }
                LayoutParams lp = v.getLayoutParams();
                boolean brk = lp instanceof LP && ((LP) lp).breakBefore;
                if (xx > 0 && (xx + v.getMeasuredWidth() > maxW || brk)) break;
                xx += v.getMeasuredWidth() + hgap;
                lineH = Math.max(lineH, v.getMeasuredHeight());
                end = i + 1;
            }
            x = getPaddingLeft();
            int extra = Math.max(0, maxW - (xx - hgap));
            int grows = 0;
            for (int i = start; i < end; i++) {
                LayoutParams lp = getChildAt(i).getLayoutParams();
                if (lp instanceof LP && ((LP) lp).grow && getChildAt(i).getVisibility() != GONE) grows++;
            }
            for (int i = start; i < end; i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                int top = center ? y + (lineH - v.getMeasuredHeight()) / 2 : y;
                v.layout(x, top, x + v.getMeasuredWidth(), top + v.getMeasuredHeight());
                x += v.getMeasuredWidth() + hgap;
                LayoutParams lp = v.getLayoutParams();
                if (grows > 0 && lp instanceof LP && ((LP) lp).grow) x += extra / grows;
            }
            y += lineH + vgap;
            if (end == start) end = start + 1;
            start = end;
        }
    }
}
