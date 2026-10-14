package com.vulpine.at.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import com.vulpine.at.App;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public final class Ic {
    static JSONObject src;
    static final Map<String, Doc> cache = new HashMap<>();

    static final class Shape {
        Path path;
        Integer fill, stroke;
        boolean fillCurrent, strokeCurrent;
        float sw = 1;
        float opacity = 1;
    }

    static final class Doc {
        float vx, vy, vw = 24, vh = 24;
        final List<Shape> shapes = new ArrayList<>();
    }

    static synchronized JSONObject src() {
        if (src == null) {
            src = new JSONObject();
            try {
                String[] fs = App.ctx.getAssets().list("");
                if (fs != null) for (String f : fs) {
                    if (!f.startsWith("icons") || !f.endsWith(".json")) continue;
                    try (InputStream in = App.ctx.getAssets().open(f)) {
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] b = new byte[16384];
                        int n;
                        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                        JSONObject o = new JSONObject(new String(bo.toByteArray(), "UTF-8"));
                        java.util.Iterator<String> it = o.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            src.put(k, o.get(k));
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
        return src;
    }

    public static boolean has(String key) {
        return src().has(key);
    }

    public static Drawable get(String key, float sizeDp, int color) {
        Doc d;
        synchronized (cache) {
            d = cache.get(key);
            if (d == null) {
                String s = src().optString(key, null);
                d = s == null ? new Doc() : parse(s);
                cache.put(key, d);
            }
        }
        return new D(d, T.dp(sizeDp), color);
    }

    public static Drawable svg(String svg, float sizeDp, int color) {
        return new D(parse(svg), T.dp(sizeDp), color);
    }

    static Integer col(String v, boolean[] current) {
        if (v == null) return null;
        v = v.trim();
        if (v.equals("none")) return 0;
        if (v.equals("currentColor")) {
            current[0] = true;
            return null;
        }
        return Css.color(v);
    }

    static float f(String v, float d) {
        if (v == null) return d;
        try {
            return Float.parseFloat(v.replace("px", "").trim());
        } catch (Exception e) {
            return d;
        }
    }

    static Doc parse(String svg) {
        Doc doc = new Doc();
        try {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(new java.io.StringReader(svg));
            Deque<Map<String, String>> st = new ArrayDeque<>();
            Map<String, String> base = new HashMap<>();
            base.put("fill", "black");
            st.push(base);
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    Map<String, String> a = new HashMap<>(st.peek());
                    a.remove("d");
                    Map<String, String> own = new HashMap<>();
                    for (int i = 0; i < p.getAttributeCount(); i++) own.put(p.getAttributeName(i), p.getAttributeValue(i));
                    String style = own.get("style");
                    if (style != null) for (String kv : style.split(";")) {
                        int c = kv.indexOf(':');
                        if (c > 0) own.put(kv.substring(0, c).trim(), kv.substring(c + 1).trim());
                    }
                    a.putAll(own);
                    st.push(a);
                    String n = p.getName();
                    if (n.equals("svg")) {
                        String vb = own.get("viewBox");
                        if (vb != null) {
                            String[] q = vb.trim().split("[\\s,]+");
                            if (q.length == 4) {
                                doc.vx = f(q[0], 0);
                                doc.vy = f(q[1], 0);
                                doc.vw = f(q[2], 24);
                                doc.vh = f(q[3], 24);
                            }
                        }
                        continue;
                    }
                    Path path = null;
                    switch (n) {
                        case "path":
                            if (own.get("d") != null) path = Svg.parse(own.get("d"));
                            break;
                        case "circle":
                            path = new Path();
                            path.addCircle(f(own.get("cx"), 0), f(own.get("cy"), 0), f(own.get("r"), 0), Path.Direction.CW);
                            break;
                        case "ellipse": {
                            path = new Path();
                            float cx = f(own.get("cx"), 0), cy = f(own.get("cy"), 0), rx = f(own.get("rx"), 0), ry = f(own.get("ry"), 0);
                            path.addOval(new RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW);
                            break;
                        }
                        case "rect": {
                            path = new Path();
                            float x = f(own.get("x"), 0), y = f(own.get("y"), 0), w = f(own.get("width"), 0), h = f(own.get("height"), 0);
                            float rx = f(own.get("rx"), f(own.get("ry"), 0)), ry = f(own.get("ry"), rx);
                            path.addRoundRect(new RectF(x, y, x + w, y + h), rx, ry, Path.Direction.CW);
                            break;
                        }
                        case "line":
                            path = new Path();
                            path.moveTo(f(own.get("x1"), 0), f(own.get("y1"), 0));
                            path.lineTo(f(own.get("x2"), 0), f(own.get("y2"), 0));
                            break;
                        case "polyline":
                        case "polygon": {
                            path = new Path();
                            String[] q = own.getOrDefault("points", "").trim().split("[\\s,]+");
                            for (int i = 0; i + 1 < q.length; i += 2) {
                                if (i == 0) path.moveTo(f(q[i], 0), f(q[i + 1], 0));
                                else path.lineTo(f(q[i], 0), f(q[i + 1], 0));
                            }
                            if (n.equals("polygon")) path.close();
                            break;
                        }
                    }
                    if (path != null) {
                        String tr = a.get("transform");
                        if (tr != null && own.containsKey("transform")) {
                            Matrix m = transform(tr);
                            if (m != null) path.transform(m);
                        }
                        Shape s = new Shape();
                        s.path = path;
                        boolean[] cur = {false};
                        s.fill = col(a.get("fill"), cur);
                        s.fillCurrent = cur[0];
                        cur[0] = false;
                        s.stroke = col(a.get("stroke"), cur);
                        s.strokeCurrent = cur[0];
                        s.sw = f(a.get("stroke-width"), 1);
                        s.opacity = f(a.get("opacity"), 1) * f(own.get("fill-opacity"), 1);
                        doc.shapes.add(s);
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (!st.isEmpty()) st.pop();
                }
            }
        } catch (Exception e) {
        }
        return doc;
    }

    static Matrix transform(String t) {
        Matrix m = new Matrix();
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(translate|rotate|scale|matrix)\\(([^)]*)\\)").matcher(t);
        while (mm.find()) {
            String[] q = mm.group(2).trim().split("[\\s,]+");
            float[] v = new float[q.length];
            for (int i = 0; i < q.length; i++) v[i] = f(q[i], 0);
            Matrix x = new Matrix();
            switch (mm.group(1)) {
                case "translate": x.setTranslate(v[0], v.length > 1 ? v[1] : 0); break;
                case "rotate": if (v.length >= 3) x.setRotate(v[0], v[1], v[2]); else x.setRotate(v[0]); break;
                case "scale": x.setScale(v[0], v.length > 1 ? v[1] : v[0]); break;
                case "matrix": if (v.length == 6) x.setValues(new float[]{v[0], v[2], v[4], v[1], v[3], v[5], 0, 0, 1}); break;
            }
            m.preConcat(x);
        }
        return m;
    }

    public static final class D extends Drawable {
        final Doc d;
        final int size;
        int color;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix mx = new Matrix();
        final Path tmp = new Path();
        int alpha = 255;

        D(Doc d, int size, int color) {
            this.d = d;
            this.size = size;
            this.color = color;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        public D tint(int c) {
            color = c;
            invalidateSelf();
            return this;
        }

        public int getIntrinsicWidth() {
            return size;
        }

        public int getIntrinsicHeight() {
            return size;
        }

        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float s = Math.min(b.width() / d.vw, b.height() / d.vh);
            mx.reset();
            mx.setTranslate(-d.vx, -d.vy);
            mx.postScale(s, s);
            mx.postTranslate(b.left + (b.width() - d.vw * s) / 2f, b.top + (b.height() - d.vh * s) / 2f);
            for (Shape sh : d.shapes) {
                tmp.reset();
                sh.path.transform(mx, tmp);
                int fc = sh.fillCurrent ? color : (sh.fill == null ? 0 : sh.fill);
                if (fc != 0 && (sh.fillCurrent || sh.fill != null)) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(fc);
                    p.setAlpha(Math.round(((fc >>> 24) / 255f) * sh.opacity * alpha));
                    c.drawPath(tmp, p);
                }
                int sc = sh.strokeCurrent ? color : (sh.stroke == null ? 0 : sh.stroke);
                if (sc != 0) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(sh.sw * s);
                    p.setColor(sc);
                    p.setAlpha(Math.round(((sc >>> 24) / 255f) * sh.opacity * alpha));
                    c.drawPath(tmp, p);
                }
            }
        }

        public void setAlpha(int a) {
            alpha = a;
            invalidateSelf();
        }

        public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
        }

        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
