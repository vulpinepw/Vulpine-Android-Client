package com.vulpine.at.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;

public final class Blur extends Drawable {
    final View host;
    final View[] sources;
    final float[] radii;
    final int tint, stroke;
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG), sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final RectF rf = new RectF();
    final int[] hl = new int[2], sl = new int[2];
    Object node;
    boolean drawing;

    public static boolean on() {
        return Build.VERSION.SDK_INT >= 31 && !T.power;
    }

    public static int tint(float a) {
        return T.dark ? T.mixA(T.surface, a) : T.mixA(0xffffffff, Math.min(1f, a + .04f));
    }

    public static void apply(View host, float rDp, View... sources) {
        apply(host, new float[]{rDp, rDp, rDp, rDp}, .58f, sources);
    }

    public static void apply(View host, float[] rDp, float a, View... sources) {
        if (!on()) return;
        Blur b = new Blur(host, rDp, tint(a), T.glassStroke, sources);
        host.setBackground(b);
        ViewTreeObserver.OnPreDrawListener pd = () -> {
            if (host.isShown()) host.invalidate();
            return true;
        };
        host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {
                v.getViewTreeObserver().addOnPreDrawListener(pd);
            }

            public void onViewDetachedFromWindow(View v) {
                v.getViewTreeObserver().removeOnPreDrawListener(pd);
            }
        });
        if (host.isAttachedToWindow()) host.getViewTreeObserver().addOnPreDrawListener(pd);
    }

    Blur(View host, float[] rDp, int tint, int stroke, View[] sources) {
        this.host = host;
        this.sources = sources;
        this.tint = tint;
        this.stroke = stroke;
        radii = new float[8];
        for (int i = 0; i < 4; i++) radii[i * 2] = radii[i * 2 + 1] = T.dpf(rDp[i]);
        tp.setColor(tint);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(Math.max(1, T.dpf(.5f)));
        sp.setColor(stroke);
    }

    @Override
    public void draw(Canvas c) {
        int w = getBounds().width(), h = getBounds().height();
        if (w <= 0 || h <= 0) return;
        path.reset();
        rf.set(0, 0, w, h);
        path.addRoundRect(rf, radii, Path.Direction.CW);
        if (!drawing && c.isHardwareAccelerated() && Build.VERSION.SDK_INT >= 31) {
            drawing = true;
            try {
                backdrop(c, w, h);
            } catch (Throwable e) {
            }
            drawing = false;
        }
        c.drawPath(path, tp);
        if ((stroke >>> 24) > 0) {
            rf.inset(sp.getStrokeWidth() / 2, sp.getStrokeWidth() / 2);
            path.reset();
            path.addRoundRect(rf, radii, Path.Direction.CW);
            c.drawPath(path, sp);
        }
    }

    void backdrop(Canvas c, int w, int h) {
        RenderNode n = (RenderNode) node;
        if (n == null) {
            n = new RenderNode("vblur");
            n.setRenderEffect(RenderEffect.createBlurEffect(T.dpf(22), T.dpf(22), Shader.TileMode.CLAMP));
            node = n;
        }
        int pad = T.dp(24);
        n.setPosition(-pad, -pad, w + pad, h + pad);
        host.getLocationInWindow(hl);
        Canvas rc = n.beginRecording();
        rc.drawColor(T.bg);
        for (View s : sources) {
            if (s == null || s.getVisibility() != View.VISIBLE || s.getWidth() == 0) continue;
            s.getLocationInWindow(sl);
            int k = rc.save();
            rc.translate(sl[0] - hl[0] + pad, sl[1] - hl[1] + pad);
            s.draw(rc);
            rc.restoreToCount(k);
        }
        n.endRecording();
        int k = c.save();
        c.clipPath(path);
        c.drawRenderNode(n);
        c.restoreToCount(k);
    }

    @Override
    public void getOutline(Outline o) {
        o.setRoundRect(getBounds(), radii[0]);
    }

    @Override
    public void setAlpha(int a) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
