package com.vulpine.at.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.List;

public final class Svg {
    public static Path parse(String d) {
        Path p = new Path();
        int i = 0, n = d.length();
        char cmd = 'M';
        float cx = 0, cy = 0, sx = 0, sy = 0, lcx = 0, lcy = 0;
        char prev = 0;
        float[] a = new float[7];
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            if (Character.isLetter(c) && c != 'e' && c != 'E') {
                cmd = c;
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.close();
                    cx = sx;
                    cy = sy;
                    prev = cmd;
                }
                continue;
            }
            int cnt;
            switch (Character.toUpperCase(cmd)) {
                case 'M': case 'L': case 'T': cnt = 2; break;
                case 'H': case 'V': cnt = 1; break;
                case 'C': cnt = 6; break;
                case 'S': case 'Q': cnt = 4; break;
                case 'A': cnt = 7; break;
                default: return p;
            }
            int[] pos = {i};
            for (int k = 0; k < cnt; k++) {
                if (Character.toUpperCase(cmd) == 'A' && (k == 3 || k == 4)) a[k] = flag(d, pos);
                else a[k] = num(d, pos);
            }
            i = pos[0];
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? cx : 0, oy = rel ? cy : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M':
                    cx = a[0] + ox;
                    cy = a[1] + oy;
                    p.moveTo(cx, cy);
                    sx = cx;
                    sy = cy;
                    cmd = rel ? 'l' : 'L';
                    break;
                case 'L':
                    cx = a[0] + ox;
                    cy = a[1] + oy;
                    p.lineTo(cx, cy);
                    break;
                case 'H':
                    cx = a[0] + (rel ? cx : 0);
                    p.lineTo(cx, cy);
                    break;
                case 'V':
                    cy = a[0] + (rel ? cy : 0);
                    p.lineTo(cx, cy);
                    break;
                case 'C':
                    p.cubicTo(a[0] + ox, a[1] + oy, a[2] + ox, a[3] + oy, a[4] + ox, a[5] + oy);
                    lcx = a[2] + ox;
                    lcy = a[3] + oy;
                    cx = a[4] + ox;
                    cy = a[5] + oy;
                    break;
                case 'S': {
                    float x1 = cx, y1 = cy;
                    char pu = Character.toUpperCase(prev);
                    if (pu == 'C' || pu == 'S') {
                        x1 = 2 * cx - lcx;
                        y1 = 2 * cy - lcy;
                    }
                    p.cubicTo(x1, y1, a[0] + ox, a[1] + oy, a[2] + ox, a[3] + oy);
                    lcx = a[0] + ox;
                    lcy = a[1] + oy;
                    cx = a[2] + ox;
                    cy = a[3] + oy;
                    break;
                }
                case 'Q':
                    p.quadTo(a[0] + ox, a[1] + oy, a[2] + ox, a[3] + oy);
                    lcx = a[0] + ox;
                    lcy = a[1] + oy;
                    cx = a[2] + ox;
                    cy = a[3] + oy;
                    break;
                case 'T': {
                    float x1 = cx, y1 = cy;
                    char pu = Character.toUpperCase(prev);
                    if (pu == 'Q' || pu == 'T') {
                        x1 = 2 * cx - lcx;
                        y1 = 2 * cy - lcy;
                    }
                    p.quadTo(x1, y1, a[0] + ox, a[1] + oy);
                    lcx = x1;
                    lcy = y1;
                    cx = a[0] + ox;
                    cy = a[1] + oy;
                    break;
                }
                case 'A': {
                    float x2 = a[5] + ox, y2 = a[6] + oy;
                    arc(p, cx, cy, x2, y2, a[0], a[1], a[2], a[3] != 0, a[4] != 0);
                    cx = x2;
                    cy = y2;
                    break;
                }
            }
            prev = cmd;
        }
        return p;
    }

    static float flag(String d, int[] pos) {
        int i = pos[0];
        while (i < d.length() && (Character.isWhitespace(d.charAt(i)) || d.charAt(i) == ',')) i++;
        float v = d.charAt(i) == '1' ? 1 : 0;
        pos[0] = i + 1;
        return v;
    }

    static float num(String d, int[] pos) {
        int i = pos[0], n = d.length();
        while (i < n && (Character.isWhitespace(d.charAt(i)) || d.charAt(i) == ',')) i++;
        int s = i;
        if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
        boolean dot = false, exp = false;
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isDigit(c)) i++;
            else if (c == '.' && !dot && !exp) {
                dot = true;
                i++;
            } else if ((c == 'e' || c == 'E') && !exp) {
                exp = true;
                i++;
                if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
            } else break;
        }
        pos[0] = i;
        try {
            return Float.parseFloat(d.substring(s, i));
        } catch (Exception e) {
            pos[0] = n;
            return 0;
        }
    }

    static void arc(Path p, float x0, float y0, float x, float y, float rx, float ry, float angle, boolean large, boolean sweep) {
        if (rx == 0 || ry == 0) {
            p.lineTo(x, y);
            return;
        }
        double dx2 = (x0 - x) / 2.0, dy2 = (y0 - y) / 2.0;
        double ang = Math.toRadians(angle % 360.0), cos = Math.cos(ang), sin = Math.sin(ang);
        double x1 = cos * dx2 + sin * dy2, y1 = -sin * dx2 + cos * dy2;
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double Prx = rx * rx, Pry = ry * ry, Px1 = x1 * x1, Py1 = y1 * y1;
        double chk = Px1 / Prx + Py1 / Pry;
        if (chk > 1) {
            rx = (float) (Math.sqrt(chk) * rx);
            ry = (float) (Math.sqrt(chk) * ry);
            Prx = rx * rx;
            Pry = ry * ry;
        }
        double sign = large == sweep ? -1 : 1;
        double sq = ((Prx * Pry) - (Prx * Py1) - (Pry * Px1)) / ((Prx * Py1) + (Pry * Px1));
        sq = sq < 0 ? 0 : sq;
        double coef = sign * Math.sqrt(sq);
        double cx1 = coef * ((rx * y1) / ry), cy1 = coef * -((ry * x1) / rx);
        double sx2 = (x0 + x) / 2.0, sy2 = (y0 + y) / 2.0;
        double cx = sx2 + (cos * cx1 - sin * cy1), cy = sy2 + (sin * cx1 + cos * cy1);
        double ux = (x1 - cx1) / rx, uy = (y1 - cy1) / ry, vx = (-x1 - cx1) / rx, vy = (-y1 - cy1) / ry;
        double n = Math.sqrt(ux * ux + uy * uy);
        double start = Math.toDegrees((uy < 0 ? -1.0 : 1.0) * Math.acos(ux / n));
        n = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        double pp = ux * vx + uy * vy;
        double ext = Math.toDegrees(((ux * vy - uy * vx) < 0 ? -1.0 : 1.0) * Math.acos(Math.max(-1, Math.min(1, pp / n))));
        if (!sweep && ext > 0) ext -= 360;
        else if (sweep && ext < 0) ext += 360;
        ext %= 360;
        start %= 360;
        RectF oval = new RectF((float) (cx - rx), (float) (cy - ry), (float) (cx + rx), (float) (cy + ry));
        if (angle == 0) {
            if (Math.abs(ext) >= 359.99) {
                p.arcTo(oval, (float) start, 180f, false);
                p.arcTo(oval, (float) start + 180f, (float) ext - 180f, false);
            } else p.arcTo(oval, (float) start, (float) ext, false);
            return;
        }
        Path a = new Path();
        a.addArc(oval, (float) start, (float) ext);
        if (angle != 0) {
            Matrix m = new Matrix();
            m.setRotate(angle, (float) cx, (float) cy);
            a.transform(m);
        }
        p.lineTo(x0, y0);
        p.addPath(a);
        p.moveTo(x, y);
    }

    public static final class Icon extends Drawable {
        final List<Path> paths = new ArrayList<>();
        final List<Boolean> fills = new ArrayList<>();
        final float vx, vy, vw, vh;
        float stroke;
        int color;
        int size;
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix mx = new Matrix();
        final Path tmp = new Path();

        public Icon(float vx, float vy, float vw, float vh, int sizePx, int color, float stroke) {
            this.vx = vx;
            this.vy = vy;
            this.vw = vw;
            this.vh = vh;
            this.size = sizePx;
            this.color = color;
            this.stroke = stroke;
            pt.setStrokeCap(Paint.Cap.ROUND);
            pt.setStrokeJoin(Paint.Join.ROUND);
        }

        public Icon add(String d, boolean fill) {
            paths.add(parse(d));
            fills.add(fill);
            return this;
        }

        public Icon circle(float cx, float cy, float r, boolean fill) {
            Path p = new Path();
            p.addCircle(cx, cy, r, Path.Direction.CW);
            paths.add(p);
            fills.add(fill);
            return this;
        }

        public Icon rect(float x, float y, float w, float h, float rx, boolean fill) {
            Path p = new Path();
            p.addRoundRect(new RectF(x, y, x + w, y + h), rx, rx, Path.Direction.CW);
            paths.add(p);
            fills.add(fill);
            return this;
        }

        public Icon color(int c) {
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
            float s = Math.min(b.width() / vw, b.height() / vh);
            mx.reset();
            mx.setTranslate(-vx, -vy);
            mx.postScale(s, s);
            mx.postTranslate(b.left + (b.width() - vw * s) / 2f, b.top + (b.height() - vh * s) / 2f);
            pt.setColor(color);
            for (int i = 0; i < paths.size(); i++) {
                tmp.reset();
                paths.get(i).transform(mx, tmp);
                if (fills.get(i)) {
                    pt.setStyle(Paint.Style.FILL);
                } else {
                    pt.setStyle(Paint.Style.STROKE);
                    pt.setStrokeWidth(stroke * s);
                }
                c.drawPath(tmp, pt);
            }
        }

        public void setAlpha(int a) {
            pt.setAlpha(a);
        }

        public void setColorFilter(ColorFilter f) {
            pt.setColorFilter(f);
        }

        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static Icon stroke(int sizeDp, int color, float sw, String... ds) {
        Icon i = new Icon(0, 0, 24, 24, T.dp(sizeDp), color, sw);
        for (String d : ds) i.add(d, false);
        return i;
    }

    public static Icon fill(int sizeDp, int color, String... ds) {
        Icon i = new Icon(0, 0, 24, 24, T.dp(sizeDp), color, 0);
        for (String d : ds) i.add(d, true);
        return i;
    }

    public static Icon material(int sizeDp, int color, String d) {
        Icon i = new Icon(0, -960, 960, 960, T.dp(sizeDp), color, 0);
        i.add(d, true);
        return i;
    }
}
