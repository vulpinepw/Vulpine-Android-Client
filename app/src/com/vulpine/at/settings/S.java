package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONObject;

public final class S {
    public static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2, DANGER = 3;

    public interface Bool {
        void on(boolean v);
    }

    public interface Str {
        void on(String v);
    }

    public interface Num {
        void on(float v);
    }

    public static TextView text(Context c, CharSequence s, float px, int weight, int color, float lineDp) {
        TextView t = W.text(c, s, px, weight, color);
        lh(t, lineDp);
        return t;
    }

    public static void lh(TextView t, float lineDp) {
        Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int natural = fm.descent - fm.ascent;
        int target = T.dp(lineDp * T.scale);
        int extra = Math.max(0, target - natural);
        t.setLineSpacing(extra, 1f);
        t.setPadding(t.getPaddingLeft(), extra / 2, t.getPaddingRight(), extra - extra / 2);
    }

    public static void lh(TextView t, float lineDp, int padTopDp, int padBotDp) {
        Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int natural = fm.descent - fm.ascent;
        int target = T.dp(lineDp * T.scale);
        int extra = Math.max(0, target - natural);
        t.setLineSpacing(extra, 1f);
        t.setPadding(t.getPaddingLeft(), T.dp(padTopDp) + extra / 2, t.getPaddingRight(), T.dp(padBotDp) + extra - extra / 2);
    }

    public static int accText(int inherit) {
        return T.accentGrad != null && T.accentGrad.length > 1 ? inherit : T.accent;
    }

    public static TextView muted(Context c, String s) {
        return text(c, s, 13, 400, T.textMuted, 19.5f);
    }

    public static TextView secondary(Context c, String s, float px) {
        return text(c, s, px, 400, T.textSecondary, px * 1.5f);
    }

    public static TextView label(Context c, String s, int weight) {
        return text(c, s, 14, weight, T.text, 21);
    }

    public static LinearLayout card(Context c, int padH, int padV) {
        LinearLayout l = W.col(c);
        l.setBackground(W.round(T.surface, 16));
        l.setPadding(T.dp(padH), T.dp(padV), T.dp(padH), T.dp(padV));
        return l;
    }

    public static LinearLayout card(Context c) {
        return card(c, 20, 20);
    }

    public static LinearLayout.LayoutParams gap(int topDp) {
        LinearLayout.LayoutParams p = W.lp(W.MP, W.WC);
        p.topMargin = T.dp(topDp);
        return p;
    }

    public static View add(LinearLayout parent, View v, int topDp) {
        parent.addView(v, gap(topDp));
        return v;
    }

    public static View addW(LinearLayout parent, View v, int topDp) {
        LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
        p.topMargin = T.dp(topDp);
        parent.addView(v, p);
        return v;
    }

    public static TextView btn(Context c, String label, int variant, View.OnClickListener on) {
        return btn(c, label, variant, 36, 14, on);
    }

    public static TextView btn(Context c, String label, int variant, float hDp, float fs, View.OnClickListener on) {
        int fg = variant == PRIMARY ? T.accentFg : variant == SECONDARY ? T.text : variant == DANGER ? 0xffffffff : T.textSecondary;
        TextView t = W.text(c, label, fs, 500, fg);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(T.dp(20), 0, T.dp(20), 0);
        t.setMinHeight(T.dp(hDp));
        t.setHeight(T.dp(hDp));
        Drawable bg = variant == PRIMARY ? W.accentBg(999) : variant == SECONDARY ? W.round(T.surface2, 999) : variant == DANGER ? W.round(T.danger, 999) : new ColorDrawable(0);
        t.setBackground(W.ripple(bg, 999));
        t.setOnClickListener(on);
        t.setOnTouchListener((x, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) x.setTranslationY(T.dpf(1));
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) x.setTranslationY(0);
            return false;
        });
        return t;
    }

    public static void busy(TextView b, boolean busy, String idle, String working) {
        b.setText(busy ? working : idle);
        W.enable(b, !busy);
    }

    public static TextView smallBtn(Context c, String label, boolean danger, View.OnClickListener on) {
        TextView t = W.text(c, label, 13, 600, danger ? T.danger : T.text);
        t.setGravity(Gravity.CENTER);
        t.setPadding(T.dp(12), 0, T.dp(12), 0);
        lh(t, 19.5f, 8, 8);
        t.setBackground(W.ripple(W.round(danger ? T.dangerSoft : T.surface2, 8), 8));
        t.setOnClickListener(on);
        return t;
    }

    public static Typeface mono() {
        return Typeface.MONOSPACE;
    }

    public static class Switch extends View {
        boolean on;
        float pos;
        Bool cb;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Switch(Context c, boolean on, Bool cb) {
            super(c);
            this.on = on;
            this.cb = cb;
            pos = on ? 1 : 0;
            setOnClickListener(v -> {
                set(!this.on, true);
                if (this.cb != null) this.cb.on(this.on);
            });
            setContentDescription("switch");
        }

        public void set(boolean v, boolean anim) {
            on = v;
            if (!anim) {
                pos = v ? 1 : 0;
                invalidate();
                return;
            }
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(pos, v ? 1 : 0);
            va.setDuration(T.DUR_FAST);
            va.addUpdateListener(a -> {
                pos = (float) a.getAnimatedValue();
                invalidate();
            });
            va.start();
        }

        public boolean on() {
            return on;
        }

        protected void onMeasure(int w, int h) {
            setMeasuredDimension(T.dp(42), T.dp(24));
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (T.accentGrad != null && T.accentGrad.length > 1) {
                p.setColor(T.surface3);
                c.drawRoundRect(0, 0, w, h, h / 2, h / 2, p);
                if (pos > 0) {
                    float cx = w / 2, cy = h / 2;
                    double a = Math.toRadians(135);
                    float len = (float) (Math.abs(w * Math.sin(a)) + Math.abs(h * Math.cos(a))) / 2;
                    float dx = (float) Math.sin(a) * len, dy = (float) -Math.cos(a) * len;
                    p.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, T.accentGrad, null, Shader.TileMode.CLAMP));
                    p.setAlpha(Math.round(pos * 255));
                    c.drawRoundRect(0, 0, w, h, h / 2, h / 2, p);
                    p.setShader(null);
                    p.setAlpha(255);
                }
            } else {
                p.setColor(T.mix(T.surface3, T.accent, pos));
                c.drawRoundRect(0, 0, w, h, h / 2, h / 2, p);
            }
            p.setColor(0xffffffff);
            float r = T.dpf(9), cx = T.dpf(3) + r + pos * T.dpf(18);
            c.drawCircle(cx, h / 2, r, p);
        }
    }

    public static LinearLayout switchCard(Context c, String label, String desc, boolean on, Bool cb) {
        LinearLayout card = W.row(c);
        card.setBackground(W.round(T.surface, 16));
        card.setPadding(T.dp(16), T.dp(12), T.dp(16), T.dp(12));
        LinearLayout tx = W.col(c);
        tx.addView(label(c, label, 500));
        if (desc != null) S.add(tx, muted(c, desc), 2);
        card.addView(tx, W.lp(0, W.WC, 1));
        Switch sw = new Switch(c, on, cb);
        LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
        p.leftMargin = T.dp(12);
        card.addView(sw, p);
        card.setTag(sw);
        return card;
    }

    public static LinearLayout valueCard(Context c, String label, String value, boolean mono, String reveal) {
        LinearLayout card = W.row(c);
        card.setBackground(W.round(T.surface, 16));
        card.setPadding(T.dp(16), T.dp(12), T.dp(16), T.dp(12));
        LinearLayout tx = W.col(c);
        tx.addView(text(c, label, 13, 400, T.textSecondary, 19.5f));
        TextView v = W.text(c, value, 14, 400, T.text);
        if (mono) v.setTypeface(mono());
        lh(v, 21);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.setMaxLines(reveal == null ? 1 : 6);
        if (!mono || reveal == null) v.setSingleLine(true);
        v.setTextIsSelectable(false);
        tx.addView(v);
        card.addView(tx, W.lp(0, W.WC, 1));
        if (reveal != null) {
            boolean[] shown = {false};
            TextView b = btn(c, "Reveal", GHOST, 30, 14, null);
            b.setOnClickListener(x -> {
                shown[0] = !shown[0];
                v.setText(shown[0] ? reveal : value);
                v.setSingleLine(!shown[0]);
                v.setTextIsSelectable(shown[0]);
                b.setText(shown[0] ? "Hide" : "Reveal");
            });
            LinearLayout.LayoutParams p = W.lp(W.WC, T.dp(30));
            p.leftMargin = T.dp(12);
            card.addView(b, p);
        }
        return card;
    }

    public static View seg(Context c, String[] labels, int sel, int maxWDp, W.Segmented.On on) {
        W.Segmented s = new W.Segmented(c, labels, sel, on);
        if (maxWDp <= 0) return s;
        FrameLayout f = new FrameLayout(c) {
            protected void onMeasure(int wm, int hm) {
                int w = MeasureSpec.getSize(wm);
                int cw = Math.min(w, T.dp(maxWDp));
                s.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), hm);
                setMeasuredDimension(w, s.getMeasuredHeight());
            }
        };
        f.addView(s, new FrameLayout.LayoutParams(W.MP, W.WC));
        f.setTag(s);
        return f;
    }

    public static W.Segmented segOf(View v) {
        return v instanceof W.Segmented ? (W.Segmented) v : (W.Segmented) v.getTag();
    }

    public static class Swatch extends View {
        String css;
        int size;
        public boolean selected, dashed, lock;
        public int ringMode;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Swatch(Context c, String css, int sizeDp) {
            super(c);
            this.css = css;
            this.size = sizeDp;
        }

        public void setCss(String s) {
            css = s;
            invalidate();
        }

        protected void onMeasure(int w, int h) {
            setMeasuredDimension(T.dp(size), T.dp(size));
        }

        protected void onDraw(Canvas c) {
            float ext = 0;
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(ext, ext, w - ext, h - ext);
            float rad = r.width() / 2;
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            if (css != null && !css.isEmpty()) {
                fill(p, css, r);
                c.drawOval(r, p);
                p.setShader(null);
            }
            if (ringMode == 0 && (selected || dashed)) {
                p.setStyle(Paint.Style.STROKE);
                float sw = T.dpf(3);
                p.setStrokeWidth(sw);
                p.setColor(selected ? T.text : T.borderStrong);
                if (dashed && !selected) p.setPathEffect(new android.graphics.DashPathEffect(new float[]{T.dpf(4), T.dpf(3)}, 0));
                RectF in = new RectF(r.left + sw / 2, r.top + sw / 2, r.right - sw / 2, r.bottom - sw / 2);
                c.drawOval(in, p);
                p.setPathEffect(null);
            } else if (ringMode == 1 && selected) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(2));
                p.setColor(T.accent);
                c.drawOval(new RectF(r.left + T.dpf(1), r.top + T.dpf(1), r.right - T.dpf(1), r.bottom - T.dpf(1)), p);
            } else if (ringMode == 2 && selected) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(2));
                p.setColor(T.surface);
                c.drawCircle(w / 2, h / 2, rad + T.dpf(1), p);
                p.setColor(T.accent);
                c.drawCircle(w / 2, h / 2, rad + T.dpf(3), p);
            } else if (ringMode == 3 && selected) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(3));
                p.setColor(T.text);
                c.drawCircle(w / 2, h / 2, rad - T.dpf(1.5f), p);
                p.setStrokeWidth(T.dpf(2));
                p.setColor(T.bg);
                c.drawCircle(w / 2, h / 2, rad + T.dpf(1), p);
            }
            p.setStyle(Paint.Style.FILL);
            if (lock) {
                Drawable d = com.vulpine.at.ui.Svg.stroke(10, 0xffffffff, 2, "M7 11V7a5 5 0 0 1 10 0v4").rect(3, 11, 18, 11, 2, false);
                int s = T.dp(10);
                d.setBounds((int) (w - s) / 2, (int) (h - s) / 2, (int) (w + s) / 2, (int) (h + s) / 2);
                d.draw(c);
            }
        }
    }

    public static void fill(Paint p, String css, RectF r) {
        int[] g = Css.gradientColors(css);
        if (g != null && g.length > 1) {
            float a = (float) Math.toRadians(Css.gradientAngle(css));
            float cx = r.centerX(), cy = r.centerY();
            float len = (float) (Math.abs(r.width() * Math.sin(a)) + Math.abs(r.height() * Math.cos(a))) / 2;
            float dx = (float) Math.sin(a) * len, dy = (float) -Math.cos(a) * len;
            p.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, g, null, Shader.TileMode.CLAMP));
            p.setColor(0xff000000);
            return;
        }
        Integer col = Css.color(css);
        p.setShader(null);
        p.setColor(col == null ? T.surface2 : col);
    }

    public static Drawable cssDrawable(String css, float radiusDp) {
        return new Drawable() {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            public void draw(Canvas c) {
                RectF r = new RectF(getBounds());
                if (css == null || css.isEmpty()) return;
                fill(p, css, r);
                c.drawRoundRect(r, T.dpf(radiusDp), T.dpf(radiusDp), p);
            }

            public void setAlpha(int a) {
            }

            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    public static class Glossy extends Drawable {
        final int ic;
        final boolean gloss;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Glossy(int ic) {
            this(ic, true);
        }

        public Glossy(int ic, boolean gloss) {
            this.ic = ic;
            this.gloss = gloss;
        }

        public void draw(Canvas c) {
            RectF r = new RectF(getBounds());
            float rad = T.dpf(8);
            p.setColor(0xff000000);
            p.setShadowLayer(T.dpf(4) * 1.5f, 0, T.dpf(2), (Math.round(.35f * 255) << 24) | (ic & 0xffffff));
            c.save();
            c.clipRect(r.left, r.top, r.right + T.dpf(40), r.bottom);
            c.drawRoundRect(r, rad, rad, p);
            c.restore();
            p.clearShadowLayer();
            double a = Math.toRadians(150);
            float cx = r.centerX(), cy = r.centerY();
            float len = (float) (Math.abs(r.width() * Math.sin(a)) + Math.abs(r.height() * Math.cos(a))) / 2;
            float dx = (float) Math.sin(a) * len, dy = (float) -Math.cos(a) * len;
            p.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, new int[]{T.mix(ic, 0xffffffff, .18f), ic, T.mix(ic, 0xff000000, .2f)}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            Path base = new Path();
            base.addRoundRect(r, rad, rad, Path.Direction.CW);
            Path sh = new Path();
            sh.addRoundRect(new RectF(r.left, r.top + T.dpf(1), r.right, r.bottom + T.dpf(1)), rad, rad, Path.Direction.CW);
            Path band = new Path();
            band.op(base, sh, Path.Op.DIFFERENCE);
            p.setColor(0x61ffffff);
            c.drawPath(band, p);
            sh.reset();
            sh.addRoundRect(new RectF(r.left, r.top - T.dpf(1), r.right, r.bottom - T.dpf(1)), rad, rad, Path.Direction.CW);
            band.reset();
            band.op(base, sh, Path.Op.DIFFERENCE);
            p.setColor(0x1f000000);
            c.drawPath(band, p);
            if (gloss) {
                p.setColor(0xff000000);
                p.setShader(new LinearGradient(0, r.top, 0, r.centerY(), 0x52ffffff, 0x0affffff, Shader.TileMode.CLAMP));
                Path hp = new Path();
                hp.addRoundRect(new RectF(r.left, r.top, r.right, r.centerY()), new float[]{rad, rad, rad, rad, T.dpf(12), T.dpf(6), T.dpf(12), T.dpf(6)}, Path.Direction.CW);
                c.drawPath(hp, p);
                p.setShader(null);
            }
        }

        public void setAlpha(int a) {
        }

        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    public static class Slider extends View {
        float min, max, step, val;
        Num onChange, onDone;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean drag;

        public Slider(Context c, float min, float max, float step, float val, Num onChange, Num onDone) {
            super(c);
            this.min = min;
            this.max = max;
            this.step = step;
            this.val = val;
            this.onChange = onChange;
            this.onDone = onDone;
        }

        public void setValue(float v) {
            val = v;
            invalidate();
        }

        public float value() {
            return val;
        }

        public float trackDp = 6;

        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), T.dp(Math.max(20, trackDp)));
        }

        float th() {
            return T.dpf(8);
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = th(), tr = T.dpf(trackDp) / 2;
            p.setShader(null);
            p.setColor(T.surface3);
            c.drawRoundRect(0, h / 2 - tr, w, h / 2 + tr, tr, tr, p);
            float t = max > min ? (val - min) / (max - min) : 0;
            float x = r + t * (w - 2 * r);
            p.setColor(0x4d000000);
            p.setShadowLayer(T.dpf(1.5f) * 1.5f, 0, T.dpf(1), 0x4d000000);
            c.drawCircle(x, h / 2, r, p);
            p.clearShadowLayer();
            p.setColor(T.surface);
            c.drawCircle(x, h / 2, r, p);
            float ir = r - T.dpf(2);
            if (T.accentGrad != null && T.accentGrad.length > 1) {
                float d = ir * (float) Math.sqrt(.5);
                p.setShader(new android.graphics.LinearGradient(x - d, h / 2 - d, x + d, h / 2 + d, T.accentGrad, null, Shader.TileMode.CLAMP));
            } else p.setColor(T.accent);
            c.drawCircle(x, h / 2, ir, p);
            p.setShader(null);
        }

        public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            float r = th(), t = Math.max(0, Math.min(1, (e.getX() - r) / Math.max(1, getWidth() - 2 * r)));
            float v = min + t * (max - min);
            if (step > 0) v = min + Math.round((v - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    drag = true;
                case MotionEvent.ACTION_MOVE:
                    if (v != val) {
                        val = v;
                        invalidate();
                        if (onChange != null) onChange.on(val);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    drag = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (onDone != null) onDone.on(val);
                    return true;
            }
            return true;
        }
    }

    public static class ColorBox extends View {
        public int color;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int wDp, hDp;

        public ColorBox(Context c, int color, int wDp, int hDp, Num unused, ColorPick.Cb cb) {
            super(c);
            this.color = color;
            this.wDp = wDp;
            this.hDp = hDp;
            setOnClickListener(v -> ColorPick.show(c, this.color, col -> {
                this.color = col;
                invalidate();
                if (cb != null) cb.on(col);
            }));
        }

        protected void onMeasure(int w, int h) {
            setMeasuredDimension(T.dp(wDp), T.dp(hDp));
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color | 0xff000000);
            c.drawRoundRect(0, 0, w, h, T.dpf(8), T.dpf(8), p);
        }
    }

    public static String hex(int c) {
        return String.format("#%06x", c & 0xffffff);
    }

    public static boolean pro() {
        return Session.s != null && Session.s.optBoolean("pro");
    }

    public static String err(Http.Resp r, String fallback) {
        if (r == null) return fallback;
        JSONObject o = r.obj();
        String d = o.optString("detail", "");
        if (!d.isEmpty() && !d.equals("null")) return d;
        String m = o.optString("message", "");
        if (!m.isEmpty()) return m;
        String e = o.optString("error", "");
        if (!e.isEmpty()) return e;
        return r.status == 0 ? "Network error" : fallback;
    }

    public static View wrapRow(Context c, int gapDp, View... vs) {
        Flow f = new Flow(c, gapDp, gapDp);
        for (View v : vs) f.addView(v);
        return f;
    }

    public static class Flow extends ViewGroup {
        final int hg, vg;
        final java.util.Map<View, Integer> xs = new java.util.HashMap<>();

        public Flow(Context c, int hgDp, int vgDp) {
            super(c);
            hg = T.dp(hgDp);
            vg = T.dp(vgDp);
        }

        protected void onMeasure(int wm, int hm) {
            int W0 = MeasureSpec.getSize(wm);
            int x = 0, y = 0, lineH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.measure(MeasureSpec.makeMeasureSpec(W0, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > W0) {
                    x = 0;
                    y += lineH + vg;
                    lineH = 0;
                }
                x += cw + hg;
                lineH = Math.max(lineH, chh);
            }
            setMeasuredDimension(W0, y + lineH);
        }

        protected void onLayout(boolean ch0, int l, int t, int r, int b) {
            int W0 = r - l;
            int x = 0, y = 0, lineH = 0;
            java.util.List<View> line = new java.util.ArrayList<>();
            xs.clear();
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > W0) {
                    place(line, y, lineH);
                    line.clear();
                    x = 0;
                    y += lineH + vg;
                    lineH = 0;
                }
                xs.put(ch, x);
                line.add(ch);
                x += cw + hg;
                lineH = Math.max(lineH, chh);
            }
            place(line, y, lineH);
        }

        void place(java.util.List<View> line, int y, int lineH) {
            for (View ch : line) {
                int x = xs.get(ch);
                int off = (lineH - ch.getMeasuredHeight()) / 2;
                ch.layout(x, y + off, x + ch.getMeasuredWidth(), y + off + ch.getMeasuredHeight());
            }
        }
    }

    public static class Grid extends ViewGroup {
        final int minW, gap, vgap;

        public Grid(Context c, int minWDp, int gapDp, int vgapDp) {
            super(c);
            minW = T.dp(minWDp);
            gap = T.dp(gapDp);
            vgap = T.dp(vgapDp);
        }

        int cols(int W0) {
            return Math.max(1, (W0 + gap) / (minW + gap));
        }

        protected void onMeasure(int wm, int hm) {
            int W0 = MeasureSpec.getSize(wm);
            int n = cols(W0);
            int cw = (W0 - gap * (n - 1)) / n;
            int y = 0, lineH = 0, k = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                lineH = Math.max(lineH, ch.getMeasuredHeight());
                if (++k % n == 0) {
                    y += lineH + vgap;
                    lineH = 0;
                }
            }
            if (k % n != 0) y += lineH;
            else if (k > 0) y -= vgap;
            setMeasuredDimension(W0, y);
        }

        protected void onLayout(boolean ch0, int l, int t, int r, int b) {
            int W0 = r - l;
            int n = cols(W0);
            int cw = (W0 - gap * (n - 1)) / n;
            int y = 0, lineH = 0, k = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                int col = k % n;
                ch.layout(col * (cw + gap), y, col * (cw + gap) + cw, y + ch.getMeasuredHeight());
                lineH = Math.max(lineH, ch.getMeasuredHeight());
                if (++k % n == 0) {
                    y += lineH + vgap;
                    lineH = 0;
                }
            }
        }
    }

    public static View pressBg(View v, float rDp) {
        v.setBackground(W.ripple(new ColorDrawable(0), rDp));
        return v;
    }

    public static GradientDrawable pill(int color) {
        return W.round(color, 999);
    }
}
