package com.vulpine.at.people;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.T;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.json.JSONObject;

public class Fx extends View {
    static final String CH = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$";
    final String fx;
    final Integer col, col2;
    final float spd;
    final Float op;
    final String icon;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Random rnd = new Random();
    final int fg;
    List<It> items;

    static final class It {
        float x, y, rot, o, s;
        Integer c;
    }
    int iw, ih;
    boolean running;

    public static Fx make(Context c, String fx, JSONObject cfg, int fg) {
        if (fx == null || !(fx.equals("ascii") || fx.equals("pixels") || fx.equals("icons"))) return null;
        return new Fx(c, fx, cfg == null ? new JSONObject() : cfg, fg);
    }

    Fx(Context c, String fx, JSONObject cfg, int fg) {
        super(c);
        this.fx = fx;
        this.fg = fg;
        col = color(cfg.optString("color", ""));
        col2 = col == null ? null : color(cfg.optString("color2", ""));
        double s = cfg.optDouble("speed", 1);
        spd = s >= .25 && s <= 4 ? (float) s : 1f;
        double o = cfg.optDouble("opacity", Double.NaN);
        op = !Double.isNaN(o) && o >= .05 && o <= 1 ? (float) o : null;
        icon = cfg.optString("icon", "paw");
        if (op != null) setAlpha(op);
        setWillNotDraw(false);
    }

    static Integer color(String s) {
        if (s == null || !s.matches("#?[0-9a-fA-F]{6}")) return null;
        return 0xff000000 | Integer.parseInt(s.replace("#", ""), 16);
    }

    final Runnable tick = new Runnable() {
        public void run() {
            invalidate();
            if (running && !T.power) postDelayed(this, (long) ((fx.equals("ascii") ? 180 : 230) / spd));
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!fx.equals("icons")) {
            running = true;
            postDelayed(tick, (long) ((fx.equals("ascii") ? 180 : 230) / spd));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        removeCallbacks(tick);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        switch (fx) {
            case "pixels": pixels(c, w, h); break;
            case "ascii": ascii(c, w, h); break;
            case "icons": icons(c, w, h); break;
        }
    }

    void pixels(Canvas c, int w, int h) {
        float cell = T.dpf(3);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        for (float y = 0; y < h; y += cell) {
            for (float x = 0; x < w; x += cell) {
                if (rnd.nextFloat() < .14f) {
                    float a;
                    int base;
                    if (rnd.nextFloat() < .5f) {
                        a = .12f + rnd.nextFloat() * .34f;
                        base = col != null ? col : 0xffffffff;
                    } else {
                        a = .10f + rnd.nextFloat() * .28f;
                        base = 0xff000000;
                    }
                    p.setColor(T.mixA(base, a * .5f));
                    c.drawRect(x, y, x + cell, y + cell, p);
                }
            }
        }
    }

    void ascii(Canvas c, int w, int h) {
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(T.dpf(12));
        p.setLetterSpacing(2f / 12f);
        p.setColor(col != null ? T.mixA(col, .55f) : 0x80c8c8d4);
        p.setShadowLayer(T.dpf(2), 0, 0, 0x40000000);
        float adv = p.measureText("01234567890123456789") / 20f;
        if (adv < 1) adv = T.dpf(9.2f);
        int rows = (int) Math.ceil((h + T.dp(16)) / T.dpf(16)), cols = (int) Math.ceil((w + T.dp(8)) / adv);
        Paint.FontMetrics fm = p.getFontMetrics();
        float base = (T.dpf(16) - (fm.descent - fm.ascent)) / 2 - fm.ascent;
        StringBuilder sb = new StringBuilder(cols);
        for (int r = 0; r < rows; r++) {
            sb.setLength(0);
            for (int k = 0; k < cols; k++) sb.append(rnd.nextFloat() < .55f ? CH.charAt(rnd.nextInt(9)) : CH.charAt(rnd.nextInt(CH.length())));
            c.drawText(sb.toString(), 0, r * T.dpf(16) + base, p);
        }
        p.clearShadowLayer();
    }

    void icons(Canvas c, int w, int h) {
        float dw = w / T.density, dh = h / T.density;
        if (dw < 70 || dh < 70) return;
        if (items == null || iw != w || ih != h) layout(dw, dh);
        String key = Ic.has("vi." + icon) ? "vi." + icon : "vi.paw";
        for (It it : items) {
            int color = it.c == null ? fg : it.c;
            Drawable d = Ic.get(key, Math.round(it.s), color);
            int s = T.dp(Math.round(it.s));
            int save = c.save();
            c.translate(T.dpf(it.x), T.dpf(it.y));
            c.rotate(it.rot);
            d.setBounds(-s / 2, -s / 2, s - s / 2, s - s / 2);
            d.setAlpha(Math.round(Math.max(0, Math.min(1, it.o)) * 255));
            d.draw(c);
            c.restoreToCount(save);
        }
    }

    long seed;

    float r(float n) {
        seed = (seed * 1664525L + 1013904223L) & 0xffffffffL;
        return (seed / 4294967296f) * n;
    }

    void layout(float w, float h) {
        iw = getWidth();
        ih = getHeight();
        long s = 2166136261L;
        for (int i = 0; i < icon.length(); i++) {
            s ^= icon.charAt(i);
            s = (s * 16777619L) & 0xffffffffL;
        }
        seed = s;
        float band = Math.max(16, Math.min(46, Math.min(w, h) * .2f)), step = 92;
        items = new ArrayList<>();
        for (float x = step * .65f; x < w - 24; x += step * (.8f + r(.7f))) {
            place(x, 9 + r(Math.max(2, band - 14)), w, h, band);
            place(x + r(44) - 22, h - 9 - r(Math.max(2, band - 14)), w, h, band);
        }
        for (float y = step * .75f; y < h - 24; y += step * (.8f + r(.7f))) {
            place(9 + r(Math.max(2, band - 14)), y, w, h, band);
            place(w - 9 - r(Math.max(2, band - 14)), y + r(30) - 15, w, h, band);
        }
        if (items.size() > 26) items = new ArrayList<>(items.subList(0, 26));
    }

    void place(float px, float py, float w, float h, float band) {
        float s = 12 + r(9), half = s * .75f + 3;
        px = Math.min(Math.max(px, half), w - half);
        py = Math.min(Math.max(py, half), h - half);
        float edge = Math.min(Math.min(px, w - px), Math.min(py, h - py));
        float fade = Math.min(1, Math.max(0, edge / band));
        float rot = r(34) - 17;
        float o = (op != null ? op : 1) * (.2f + .55f * fade);
        It it = new It();
        it.x = px;
        it.y = py;
        it.rot = rot;
        it.o = o;
        it.s = s;
        if (col != null) it.c = col2 == null ? col : T.mix(col, col2, Math.max(0, Math.min(1, (px / w + py / h) / 2)));
        items.add(it);
    }
}
