package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;
import com.vulpine.at.ui.T;

public class Bubble extends FrameLayout {
    final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path clip = new Path();
    final float[] radii = new float[8];
    int color;
    int[] grad;
    float gradAngle;
    public boolean flash;
    float flashA;
    public int maxW;
    boolean selected;

    public Bubble(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    public void corners(float tl, float tr, float br, float bl) {
        radii[0] = radii[1] = T.dpf(tl);
        radii[2] = radii[3] = T.dpf(tr);
        radii[4] = radii[5] = T.dpf(br);
        radii[6] = radii[7] = T.dpf(bl);
        invalidate();
    }

    public void fill(int c) {
        color = c;
        grad = null;
        invalidate();
    }

    public void gradient(int[] g, float angle) {
        grad = g;
        gradAngle = angle;
        invalidate();
    }

    public void select(boolean on) {
        selected = on;
        invalidate();
    }

    public void flashNow() {
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(.3f, 0f);
        va.setDuration(1400);
        va.addUpdateListener(x -> {
            flashA = (float) x.getAnimatedValue();
            invalidate();
        });
        va.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        if (maxW > 0 && MeasureSpec.getSize(w) > maxW) w = MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.getMode(w) == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
        super.onMeasure(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        RectF r = new RectF(0, 0, getWidth(), getHeight());
        clip.reset();
        clip.addRoundRect(r, radii, Path.Direction.CW);
        if (selected) {
            bg.setShader(null);
            bg.setColor(T.accent);
        } else if (grad != null && grad.length >= 2) {
            double rad = Math.toRadians(gradAngle);
            float cx = r.centerX(), cy = r.centerY();
            float len = (float) (Math.abs(r.width() * Math.sin(rad)) + Math.abs(r.height() * Math.cos(rad))) / 2f;
            float dx = (float) Math.sin(rad) * len, dy = (float) -Math.cos(rad) * len;
            bg.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, grad, null, Shader.TileMode.CLAMP));
        } else {
            bg.setShader(null);
            bg.setColor(color);
        }
        if (Color.alpha(color) > 0 || grad != null || selected) c.drawPath(clip, bg);
        if (!T.dark && !selected && Color.alpha(color) > 0) {
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setStyle(Paint.Style.STROKE);
            sh.setStrokeWidth(Math.max(1, T.dpf(.5f)));
            sh.setColor(0x0d000000);
            c.drawPath(clip, sh);
        }
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        int s = c.save();
        c.clipPath(clip);
        super.dispatchDraw(c);
        if (flashA > 0) {
            Paint f = new Paint();
            f.setColor(T.mixA(T.accent, flashA));
            c.drawRect(0, 0, getWidth(), getHeight(), f);
        }
        c.restoreToCount(s);
    }

    static final class Color {
        static int alpha(int c) {
            return c >>> 24;
        }
    }
}
