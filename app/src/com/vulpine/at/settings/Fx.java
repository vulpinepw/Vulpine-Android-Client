package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import com.vulpine.at.Prefs;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.T;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONObject;

public class Fx extends View {
    static final String CH = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$";
    String fx = "";
    JSONObject cfg = new JSONObject();
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Random rnd = new Random();
    boolean ticking;
    float radiusDp;
    public int fg;
    final android.graphics.Path clip = new android.graphics.Path();

    public Fx(Context c, float radiusDp) {
        super(c);
        this.radiusDp = radiusDp;
    }

    public void set(String fx, JSONObject cfg) {
        this.fx = fx == null ? "" : fx;
        this.cfg = cfg == null ? new JSONObject() : cfg;
        float op = opacity();
        setAlpha(op < 0 ? 1f : op);
        icons = null;
        invalidate();
        schedule();
    }

    String col(String k) {
        String v = cfg.optString(k, "");
        if (v.matches("^#?[0-9a-fA-F]{6}$")) return v.startsWith("#") ? v : "#" + v;
        return "";
    }

    float speed() {
        double s = cfg.optDouble("speed", 1);
        return s >= .25 && s <= 4 ? (float) s : 1f;
    }

    float opacity() {
        double o = cfg.optDouble("opacity", -1);
        return o >= .05 && o <= 1 ? (float) o : -1f;
    }

    final Runnable tick = new Runnable() {
        public void run() {
            ticking = false;
            invalidate();
            schedule();
        }
    };

    void schedule() {
        removeCallbacks(tick);
        ticking = false;
        if (!isAttachedToWindow() || !(fx.equals("ascii") || fx.equals("pixels")) || Prefs.powerSaving()) return;
        ticking = true;
        postDelayed(tick, (long) ((fx.equals("ascii") ? 180 : 230) / speed()));
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedule();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
    }

    protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        if (vis == VISIBLE) schedule();
        else removeCallbacks(tick);
    }

    protected void onSizeChanged(int w, int h, int ow, int oh) {
        icons = null;
    }

    protected void onDraw(Canvas c) {
        if (fx.isEmpty()) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        clip.reset();
        clip.addRoundRect(0, 0, w, h, T.dpf(radiusDp), T.dpf(radiusDp), android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (fx.equals("pixels")) pixels(c, w, h);
        else if (fx.equals("ascii")) ascii(c, w, h);
        else if (fx.equals("icons")) icons(c, w, h);
        c.restore();
    }

    void pixels(Canvas c, int w, int h) {
        String cl = col("color");
        Integer base = cl.isEmpty() ? null : Css.color(cl);
        float s = T.dpf(3);
        p.setStyle(Paint.Style.FILL);
        for (float y = 0; y < h; y += s)
            for (float x = 0; x < w; x += s) {
                if (rnd.nextFloat() >= .14f) continue;
                float a;
                int rgb;
                if (rnd.nextFloat() < .5f) {
                    a = .12f + rnd.nextFloat() * .34f;
                    rgb = base == null ? 0xffffff : base & 0xffffff;
                } else {
                    a = .10f + rnd.nextFloat() * .28f;
                    rgb = 0;
                }
                p.setColor(rgb | (Math.round(a * .5f * 255) << 24));
                c.drawRect(x, y, x + s, y + s, p);
            }
    }

    void ascii(Canvas c, int w, int h) {
        String cl = col("color");
        Integer base = cl.isEmpty() ? null : Css.color(cl);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(T.dpf(12));
        p.setLetterSpacing(2f / 12f);
        p.setColor(base == null ? 0x80c8c8d4 : (base & 0xffffff) | 0x8c000000);
        float adv = p.measureText("0");
        if (adv <= 0) adv = T.dpf(9.2f);
        int rows = (int) Math.ceil((h + T.dpf(16)) / T.dpf(16));
        int cols = (int) Math.ceil((w + T.dpf(8)) / adv);
        Paint.FontMetrics fm = p.getFontMetrics();
        float lh = T.dpf(16), off = (lh - (fm.descent - fm.ascent)) / 2 - fm.ascent;
        char[] line = new char[cols];
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < cols; k++) line[k] = rnd.nextFloat() < .55f ? CH.charAt(rnd.nextInt(9)) : CH.charAt(rnd.nextInt(CH.length()));
            c.drawText(line, 0, cols, 0, r * lh + off, p);
        }
        p.setLetterSpacing(0);
    }

    List<float[]> icons;
    Drawable iconD;
    String iconKey;

    void icons(Canvas c, int w, int h) {
        float dw = w / T.density, dh = h / T.density;
        if (dw < 70 || dh < 70) return;
        String icon = cfg.optString("icon", "paw");
        if (icon.isEmpty()) icon = "paw";
        if (icons == null) {
            icons = new ArrayList<>();
            long[] seed = {2166136261L};
            for (int i = 0; i < icon.length(); i++) {
                seed[0] ^= icon.charAt(i);
                seed[0] = (seed[0] * 16777619L) & 0xffffffffL;
            }
            java.util.function.DoubleUnaryOperator rn = n -> {
                seed[0] = (seed[0] * 1664525L + 1013904223L) & 0xffffffffL;
                return seed[0] / 4294967296.0 * n;
            };
            double band = Math.max(16, Math.min(46, Math.min(dw, dh) * .2)), step = 92;
            float op = opacity();
            String c1 = col("color"), c2 = c1.isEmpty() ? "" : col("color2");
            java.util.function.BiConsumer<Double, Double> place = (px0, py0) -> {
                double s = 12 + rn.applyAsDouble(9), half = s * .75 + 3;
                double px = Math.min(Math.max(px0, half), dw - half);
                double py = Math.min(Math.max(py0, half), dh - half);
                double edge = Math.min(Math.min(px, dw - px), Math.min(py, dh - py));
                double fade = Math.min(1, Math.max(0, edge / band));
                double t = (px / dw + py / dh) / 2;
                int colr = mix(c1, c2, t);
                double rot = rn.applyAsDouble(34) - 17;
                icons.add(new float[]{(float) px, (float) py, colr, (float) rot, (float) s, (float) ((op >= 0 ? op : 1) * (.2 + .55 * fade))});
            };
            for (double x = step * .65; x < dw - 24; x += step * (.8 + rn.applyAsDouble(.7))) {
                place.accept(x, 9 + rn.applyAsDouble(Math.max(2, band - 14)));
                place.accept(x + rn.applyAsDouble(44) - 22, dh - 9 - rn.applyAsDouble(Math.max(2, band - 14)));
            }
            for (double y = step * .75; y < dh - 24; y += step * (.8 + rn.applyAsDouble(.7))) {
                place.accept(9 + rn.applyAsDouble(Math.max(2, band - 14)), y);
                place.accept(dw - 9 - rn.applyAsDouble(Math.max(2, band - 14)), y + rn.applyAsDouble(30) - 15);
            }
            if (icons.size() > 26) icons = new ArrayList<>(icons.subList(0, 26));
        }
        boolean svg = Ic.has("vi." + icon);
        for (float[] it : icons) {
            int sz = Math.round(it[4]);
            int color = it[2] == 0 ? (fg != 0 ? fg : T.text) : (int) it[2];
            c.save();
            c.translate(T.dpf(it[0]), T.dpf(it[1]));
            c.rotate(it[3]);
            if (svg) {
                Drawable d = Ic.get("vi." + icon, sz, color);
                int px = T.dp(sz);
                d.setBounds(-px / 2, -px / 2, px - px / 2, px - px / 2);
                d.setAlpha(Math.round(it[5] * 255));
                d.draw(c);
            } else {
                p.setTypeface(Typeface.DEFAULT);
                p.setTextSize(T.dpf(sz));
                p.setColor((color & 0xffffff) | (Math.round(it[5] * 255) << 24));
                p.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText(icon, 0, -(fm.ascent + fm.descent) / 2, p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            c.restore();
        }
    }

    static int mix(String a, String b, double t) {
        if (a.isEmpty()) return 0;
        Integer ca = Css.color(a);
        if (ca == null) return 0;
        if (b.isEmpty()) return ca;
        Integer cb = Css.color(b);
        if (cb == null) return ca;
        return T.mix(ca, cb, (float) Math.max(0, Math.min(1, t))) | 0xff000000;
    }
}
