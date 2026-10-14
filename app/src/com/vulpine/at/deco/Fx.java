package com.vulpine.at.deco;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import com.vulpine.at.App;
import com.vulpine.at.Prefs;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

public class Fx extends View {
    String fx = "";
    String col = "", col2 = "", icon = "paw";
    float spd = 1;
    float op = -1;
    int tick;
    int defColor = 0;
    float radiusDp = -1;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Random rnd = new Random();
    final Path clip = new Path();
    final RectF rf = new RectF();
    static final String CH = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$";
    static List<Object[]> cats;
    static final Map<String, Path[]> icons = new HashMap<>();

    public Fx(Context c) {
        super(c);
    }

    public static synchronized List<Object[]> categories() {
        if (cats != null) return cats;
        cats = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(DecoItem.read("deco/vicons.json"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray c = a.getJSONArray(i);
                JSONArray l = c.getJSONArray(1);
                List<String> ids = new ArrayList<>();
                for (int k = 0; k < l.length(); k++) {
                    JSONArray it = l.getJSONArray(k);
                    String id = it.getString(0);
                    JSONArray ps = it.getJSONArray(1);
                    Path[] pp = new Path[ps.length()];
                    for (int q = 0; q < pp.length; q++) pp[q] = Svg.parse(ps.getString(q));
                    icons.put(id, pp);
                    ids.add(id);
                }
                cats.add(new Object[]{c.getString(0), ids});
            }
        } catch (Exception e) {
        }
        return cats;
    }

    public static Path[] iconPaths(String id) {
        categories();
        Path[] p = icons.get(id);
        return p != null ? p : icons.get("paw");
    }

    public static void drawIcon(Canvas c, Paint p, String id, float cx, float cy, float size, float rot, int color, float alpha) {
        Path[] ps = iconPaths(id);
        if (ps == null) return;
        int s = c.save();
        c.translate(cx, cy);
        c.rotate(rot);
        float k = size / 24f;
        c.scale(k, k);
        c.translate(-12, -12);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        p.setAlpha(Math.round(Color.alpha(color) * alpha));
        for (Path q : ps) c.drawPath(q, p);
        c.restoreToCount(s);
    }

    static String hex(Object o) {
        if (!(o instanceof String)) return "";
        String s = (String) o;
        if (!s.matches("^#?[0-9a-fA-F]{6}$")) return "";
        return s.startsWith("#") ? s : "#" + s;
    }

    public void set(String fx, JSONObject cfg) {
        this.fx = fx == null ? "" : fx;
        if (cfg == null) cfg = new JSONObject();
        col = hex(cfg.opt("color"));
        Object s = cfg.opt("speed");
        spd = s instanceof Number && ((Number) s).doubleValue() >= .25 && ((Number) s).doubleValue() <= 4 ? ((Number) s).floatValue() : 1;
        Object o = cfg.opt("opacity");
        op = o instanceof Number && ((Number) o).doubleValue() >= .05 && ((Number) o).doubleValue() <= 1 ? ((Number) o).floatValue() : -1;
        icon = cfg.optString("icon", "paw");
        if (icon.isEmpty()) icon = "paw";
        String c2 = hex(cfg.opt("color2"));
        col2 = col.isEmpty() ? "" : c2;
        setAlpha(op < 0 ? 1 : op);
        setVisibility(this.fx.isEmpty() ? GONE : VISIBLE);
        invalidate();
    }

    public void setDefaultColor(int c) {
        defColor = c;
        invalidate();
    }

    public void setRadius(float dp) {
        radiusDp = dp;
        invalidate();
    }

    static int parse(String h) {
        return 0xff000000 | Integer.parseInt(h.substring(1), 16);
    }

    static int mix(String a, String b, float t) {
        if (a.isEmpty()) return 0;
        if (b.isEmpty()) return parse(a);
        t = Math.max(0, Math.min(1, t));
        int x = parse(a), y = parse(b);
        return Color.rgb(Math.round(Color.red(x) + (Color.red(y) - Color.red(x)) * t), Math.round(Color.green(x) + (Color.green(y) - Color.green(x)) * t), Math.round(Color.blue(x) + (Color.blue(y) - Color.blue(x)) * t));
    }

    final Runnable step = () -> {
        tick++;
        invalidate();
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(step);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || fx.isEmpty()) return;
        float d = T.density;
        int sv = c.save();
        if (radiusDp > 0) {
            clip.reset();
            rf.set(0, 0, w, h);
            clip.addRoundRect(rf, radiusDp * d, radiusDp * d, Path.Direction.CW);
            c.clipPath(clip);
        }
        if ("pixels".equals(fx)) pixels(c, w, h, d);
        else if ("ascii".equals(fx)) ascii(c, w, h, d);
        else if ("icons".equals(fx)) icons(c, w, h, d);
        c.restoreToCount(sv);
        if (("pixels".equals(fx) || "ascii".equals(fx)) && !Prefs.powerSaving()) {
            removeCallbacks(step);
            postDelayed(step, Math.round(("ascii".equals(fx) ? 180 : 230) / spd));
        }
    }

    void pixels(Canvas c, int w, int h, float d) {
        int pw = Math.max(60, (int) Math.ceil(w / d)), ph = Math.max(40, (int) Math.ceil(h / d));
        float sx = w / (float) pw, sy = h / (float) ph;
        p.setStyle(Paint.Style.FILL);
        int base = col.isEmpty() ? 0xffffffff : parse(col);
        for (int y = 0; y < ph; y += 3) {
            for (int x = 0; x < pw; x += 3) {
                if (rnd.nextFloat() < .14f) {
                    if (rnd.nextFloat() < .5f) p.setColor((base & 0xffffff) | (Math.round((.12f + rnd.nextFloat() * .34f) * .5f * 255) << 24));
                    else p.setColor(Math.round((.10f + rnd.nextFloat() * .28f) * .5f * 255) << 24);
                    c.drawRect(x * sx, y * sy, Math.min(w, (x + 3) * sx), Math.min(h, (y + 3) * sy), p);
                }
            }
        }
    }

    void ascii(Canvas c, int w, int h, float d) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(12 * d);
        p.setLetterSpacing(2f / 12f);
        float adv = p.measureText("01234567890123456789") / 20f;
        if (adv < 1) adv = 9.2f * d;
        int rows = (int) Math.ceil((h / d + 16) / 16), cols = (int) Math.ceil((w + 8 * d) / adv);
        int color = col.isEmpty() ? Color.argb(Math.round(.5f * 255), 200, 200, 212) : (parse(col) & 0xffffff) | (Math.round(.55f * 255) << 24);
        p.setColor(color);
        p.setShadowLayer(2 * d, 0, 0, 0x40000000);
        Paint.FontMetrics fm = p.getFontMetrics();
        StringBuilder b = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            b.setLength(0);
            for (int x = 0; x < cols; x++) b.append(rnd.nextFloat() < .55f ? CH.charAt(rnd.nextInt(9)) : CH.charAt(rnd.nextInt(CH.length())));
            float base = r * 16 * d + (16 * d - (fm.descent - fm.ascent)) / 2 - fm.ascent;
            c.drawText(b.toString(), 0, base, p);
        }
        p.clearShadowLayer();
        p.setLetterSpacing(0);
    }

    int seed;

    float rnd(float n) {
        seed = seed * 1664525 + 1013904223;
        return (float) ((seed & 0xffffffffL) / 4294967296.0 * n);
    }

    void icons(Canvas c, int w0, int h0, float d) {
        float w = w0 / d, h = h0 / d;
        if (w < 70 || h < 70) return;
        int s = (int) 2166136261L;
        for (int i = 0; i < icon.length(); i++) {
            s ^= icon.charAt(i);
            s = s * 16777619;
        }
        seed = s;
        float band = Math.max(16, Math.min(46, Math.min(w, h) * .2f));
        float step = 92;
        List<float[]> items = new ArrayList<>();
        int def = defColor != 0 ? defColor : T.text;
        for (float x = step * .65f; x < w - 24; x += step * (.8f + rnd(.7f))) {
            place(items, x, 9 + rnd(Math.max(2, band - 14)), w, h, band);
            place(items, x + rnd(44) - 22, h - 9 - rnd(Math.max(2, band - 14)), w, h, band);
        }
        for (float y = step * .75f; y < h - 24; y += step * (.8f + rnd(.7f))) {
            place(items, 9 + rnd(Math.max(2, band - 14)), y, w, h, band);
            place(items, w - 9 - rnd(Math.max(2, band - 14)), y + rnd(30) - 15, w, h, band);
        }
        int n = Math.min(26, items.size());
        float gop = op < 0 ? 1 : op;
        for (int i = 0; i < n; i++) {
            float[] it = items.get(i);
            int color = col.isEmpty() ? def : mix(col, col2, (it[0] / w + it[1] / h) / 2);
            drawIcon(c, p, icon, it[0] * d, it[1] * d, Math.round(it[3]) * d, it[2], color, gop * it[4]);
        }
    }

    void place(List<float[]> items, float px, float py, float w, float h, float band) {
        float s = 12 + rnd(9), half = s * .75f + 3;
        px = Math.min(Math.max(px, half), w - half);
        py = Math.min(Math.max(py, half), h - half);
        float edge = Math.min(Math.min(px, w - px), Math.min(py, h - py));
        float fade = Math.min(1, Math.max(0, edge / band));
        float r = rnd(34) - 17;
        items.add(new float[]{px, py, r, s, .2f + .55f * fade});
    }
}
