package com.vulpine.at.call;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.T;
import com.vulpine.core.Session;
import org.json.JSONObject;

public class CallBg extends View {
    static final PathInterpolator IO = new PathInterpolator(.42f, 0f, .58f, 1f);
    final boolean card;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Matrix m = new Matrix();
    ValueAnimator anim;
    float t;
    public JSONObject custom;
    public boolean preview;
    float radius;

    public CallBg(Context c, boolean card) {
        super(c);
        this.card = card;
    }

    public void setRadius(float r) {
        radius = r;
        invalidate();
    }

    public static int base() {
        return T.dark ? 0xff121633 : 0xffe9edff;
    }

    public static int tile() {
        return T.dark ? 0x14ffffff : 0x8cffffff;
    }

    public static int chip() {
        return T.dark ? 0x6b0a0c1e : 0xb8ffffff;
    }

    static int[] g() {
        return T.dark ? new int[]{0xff1b2a6b, 0xff3b2a86, 0xff1d4f9c, 0xff5b2c95} : new int[]{0xffc9d8ff, 0xffddd0ff, 0xffb9d3fb, 0xffe6d6ff};
    }

    public static JSONObject saved() {
        if (Session.s == null || !Session.s.optBoolean("pro")) return null;
        JSONObject b = Session.settings().optJSONObject("callBg");
        return css(b) == null ? null : b;
    }

    public static Object css(JSONObject b) {
        if (b == null) return null;
        String mode = b.optString("mode");
        if ("color".equals(mode)) {
            String c = b.optString("color");
            return c.matches("^#[0-9a-fA-F]{6}$") ? c : null;
        }
        if ("gradient".equals(mode)) {
            String f = b.optString("from"), to = b.optString("to");
            if (!f.matches("^#[0-9a-fA-F]{6}$") || !to.matches("^#[0-9a-fA-F]{6}$")) return null;
            return new Object[]{f, to, Math.max(0, Math.min(360, b.optInt("angle", 0)))};
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
        anim = null;
    }

    @Override
    protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        if (vis == VISIBLE && isAttachedToWindow()) start();
        else if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    void start() {
        if (T.lowfx || anim != null) return;
        anim = ValueAnimator.ofFloat(0, 1);
        anim.setDuration(card ? 18000 : 26000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (radius > 0) {
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(0, 0, w, h, radius, radius, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
        }
        Object cs = css(custom);
        if (cs instanceof String) {
            c.drawColor(Css.color((String) cs) | 0xff000000);
            return;
        }
        if (cs instanceof Object[]) {
            Object[] g = (Object[]) cs;
            int a = (Integer) g[2];
            double rad = Math.toRadians(a);
            float dx = (float) Math.sin(rad), dy = (float) -Math.cos(rad);
            float half = (float) (Math.abs(w * dx) + Math.abs(h * dy)) / 2f;
            float cx = w / 2, cy = h / 2;
            p.setShader(new LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, Css.color((String) g[0]) | 0xff000000, Css.color((String) g[1]) | 0xff000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            return;
        }
        c.drawColor(base());
        float ins = card ? .4f : .25f;
        float bw = w * (1 + 2 * ins), bh = h * (1 + 2 * ins);
        float tx, ty, rot, sc;
        if (t < .5f) {
            float e = IO.getInterpolation(t / .5f);
            tx = lerp(-4, 3, e);
            ty = lerp(-3, 2, e);
            rot = lerp(0, 8, e);
            sc = lerp(1, 1.06f, e);
        } else {
            float e = IO.getInterpolation((t - .5f) / .5f);
            tx = lerp(3, -2, e);
            ty = lerp(2, 4, e);
            rot = lerp(8, -6, e);
            sc = lerp(1.06f, 1.02f, e);
        }
        if (T.lowfx) {
            tx = 0;
            ty = 0;
            rot = 0;
            sc = 1;
        }
        c.save();
        c.translate(w / 2 + tx / 100f * bw, h / 2 + ty / 100f * bh);
        c.rotate(rot);
        c.scale(sc, sc);
        c.translate(-bw / 2, -bh / 2);
        int[] g = g();
        if (card) {
            blob(c, bw, bh, .45f, .50f, .25f, .30f, g[2], .70f);
            blob(c, bw, bh, .50f, .55f, .75f, .70f, g[3], .72f);
            blob(c, bw, bh, .40f, .45f, .80f, .20f, g[1], .70f);
        } else {
            blob(c, bw, bh, .42f, .46f, .22f, .28f, g[2], .70f);
            blob(c, bw, bh, .46f, .50f, .78f, .24f, g[1], .72f);
            blob(c, bw, bh, .50f, .48f, .30f, .80f, g[3], .72f);
            blob(c, bw, bh, .48f, .52f, .80f, .78f, g[0], .70f);
        }
        c.restore();
    }

    static float lerp(float a, float b, float e) {
        return a + (b - a) * e;
    }

    void blob(Canvas c, float bw, float bh, float rx, float ry, float cx, float cy, int col, float stop) {
        RadialGradient rg = new RadialGradient(0, 0, 1, new int[]{col, col & 0x00ffffff}, new float[]{0, stop}, Shader.TileMode.CLAMP);
        m.reset();
        m.setScale(rx * bw, ry * bh);
        m.postTranslate(cx * bw, cy * bh);
        rg.setLocalMatrix(m);
        p.setShader(rg);
        c.drawRect(0, 0, bw, bh, p);
        p.setShader(null);
    }
}
