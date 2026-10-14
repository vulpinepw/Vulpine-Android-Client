package com.vulpine.at.stories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import org.json.JSONObject;

public final class Ring {
    public static final int NONE = 0, NEW = 1, CLOSE = 2, SEEN = 3, SELF = 4;
    static final int[] NEW_C = {0xfff97316, 0xffec4899, 0xff8b5cf6, 0xff3b82f6, 0xfff97316};
    static final int[] CLOSE_C = {0xff22c55e, 0xff84cc16, 0xff10b981, 0xff22c55e};

    public static Shader conic(float cx, float cy, int[] cols) {
        SweepGradient g = new SweepGradient(cx, cy, cols, null);
        Matrix m = new Matrix();
        m.setRotate(110, cx, cy);
        g.setLocalMatrix(m);
        return g;
    }

    public static Shader conicNew(float cx, float cy) {
        return conic(cx, cy, NEW_C);
    }

    public static int kind(JSONObject e) {
        if (Store.count(e) == 0) return NONE;
        if (e.optBoolean("self")) return SELF;
        if (!e.optBoolean("unseen")) return SEEN;
        return e.optBoolean("close") ? CLOSE : NEW;
    }

    public static AvatarView avatar(Context c, JSONObject e, int sizeDp, float fontDp) {
        AvatarView a = new AvatarView(c);
        a.setLayoutParams(new FrameLayout.LayoutParams(T.dp(sizeDp), T.dp(sizeDp)));
        a.animate = true;
        a.textSize(fontDp);
        a.set(Store.name(e), Store.avatarColor(e), Store.avatarUrl(e));
        return a;
    }

    public static final class GradCircle extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            p.setShader(conicNew(b.exactCenterX(), b.exactCenterY()));
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), Math.min(b.width(), b.height()) / 2f, p);
        }

        public void setAlpha(int a) {
            p.setAlpha(a);
        }

        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    public static final class RingAv extends FrameLayout {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public int kind;
        public AvatarView av;

        public RingAv(Context c, JSONObject e, int boxDp, int avDp, float fontDp) {
            super(c);
            setWillNotDraw(false);
            setClipChildren(false);
            kind = kind(e);
            p.setStyle(Paint.Style.STROKE);
            av = avatar(c, e, avDp, fontDp);
            addView(av, W.flp(T.dp(avDp), T.dp(avDp), Gravity.CENTER));
        }

        protected void onDraw(Canvas c) {
            if (kind == NONE) return;
            float w = getWidth(), h = getHeight();
            float sw = T.dpf(kind == SEEN || kind == SELF ? 1.5f : 2.5f);
            p.setStrokeWidth(sw);
            if (kind == NEW || kind == CLOSE) {
                p.setShader(conic(w / 2, h / 2, kind == NEW ? NEW_C : CLOSE_C));
            } else {
                p.setShader(null);
                p.setColor(T.mixA(T.text, .26f));
            }
            c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - sw / 2, p);
        }
    }

    public static final class MiniRing extends FrameLayout {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), s = new Paint(Paint.ANTI_ALIAS_FLAG);
        final boolean unseen;
        final RectF r = new RectF();
        final AvatarView av;

        public MiniRing(Context c, JSONObject e) {
            super(c);
            setWillNotDraw(false);
            unseen = e.optBoolean("unseen");
            av = avatar(c, e, 28, 10);
            FrameLayout.LayoutParams lp = W.flp(T.dp(28), T.dp(28), Gravity.TOP | Gravity.START);
            lp.leftMargin = Math.round(T.dpf(1.5f));
            lp.topMargin = Math.round(T.dpf(1.5f));
            addView(av, lp);
        }

        public void dim(boolean on) {
            setAlpha(on ? .55f : 1f);
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (unseen) p.setShader(conicNew(w / 2, h / 2));
            else {
                p.setShader(null);
                p.setColor(T.mixA(T.text, .26f));
            }
            r.set(0, 0, w, h);
            c.drawOval(r, p);
            s.setColor(T.surface);
            float cx = T.dpf(1.5f) + T.dpf(14);
            c.drawCircle(cx, cx, T.dpf(15.5f), s);
        }
    }
}
