package com.vulpine.at.deco;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import com.vulpine.at.App;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DecoItem {
    public final String id, kind;
    public final float W0, H0;
    final Bitmap[] atlas;
    final float[][] spr;
    final Layer[] layers;
    final int[][] kids;
    final int[] roots;

    static final class Track {
        float P, ts;
        int n;
        boolean d2;
        float[] c = new float[16];
        float[][] v = new float[16][];
        float[][] gw = new float[16][];
        float[][] gh = new float[16][];
        boolean[] jump;
        float[] pre;
        int[] ents;

        void eval(float t, float dw, float dh, float[] out) {
            int j = 0, j1 = 0;
            float a = 0;
            boolean usePre = false;
            if (n > 1 && P > 0) {
                float tt = t - ts;
                if (tt < 0 && pre != null) usePre = true;
                float ph = tt % P;
                if (ph < 0) ph += P;
                float f = ph / P * n;
                j = (int) f;
                if (j >= n) j = n - 1;
                a = f - j;
                j1 = j + 1 == n ? 0 : j + 1;
                if (jump != null && jump[j]) {
                    if (a < .5f) a = 0;
                    else {
                        j = j1;
                        a = 0;
                    }
                }
            }
            for (int e : ents) {
                float x;
                if (usePre) x = pre[idx(e)];
                else {
                    float[] vv = v[e];
                    x = vv == null ? c[e] : vv[j] + (vv[j1] - vv[j]) * a;
                }
                float[] w = gw[e];
                if (w != null) x += (w.length == 1 ? w[0] : w[j] + (w[j1] - w[j]) * a) * dw;
                float[] h = gh[e];
                if (h != null) x += (h.length == 1 ? h[0] : h[j] + (h[j1] - h[j]) * a) * dh;
                out[e] = x;
            }
        }

        int idx(int e) {
            for (int i = 0; i < ents.length; i++) if (ents[i] == e) return i;
            return 0;
        }
    }

    static final class Layer {
        int p;
        boolean svg, p3d;
        Track A, O;
        float[] persp;
        float[] box;
        float[] rbox;
        int elastic;
        int clip;
        float[] rad;
        int mask = -1;
        float[] mb;
        int sprite = -1;
        int[] frames;
        float fP;
    }

    static final int[] E2 = {0, 1, 4, 5, 12, 13};
    static final int[] E16 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    static float[] fa(JSONArray a) {
        float[] r = new float[a.length()];
        for (int i = 0; i < r.length; i++) r[i] = (float) a.optDouble(i);
        return r;
    }

    static Track track(JSONObject o, boolean op) {
        Track t = new Track();
        t.P = (float) o.optDouble("P", 0);
        t.ts = (float) o.optDouble("ts", 0);
        t.n = o.optInt("n", 1);
        t.d2 = o.optInt("d2", 1) == 1;
        t.ents = op ? new int[]{0} : t.d2 ? E2 : E16;
        if (!op) {
            t.c[0] = t.c[5] = t.c[10] = t.c[15] = 1;
        }
        JSONObject c = o.optJSONObject("c");
        if (c != null) for (Iterator<String> it = c.keys(); it.hasNext(); ) {
            String k = it.next();
            t.c[Integer.parseInt(k)] = (float) c.optDouble(k);
        }
        JSONObject v = o.optJSONObject("v");
        if (v != null) for (Iterator<String> it = v.keys(); it.hasNext(); ) {
            String k = it.next();
            t.v[Integer.parseInt(k)] = fa(v.optJSONArray(k));
        }
        JSONObject gw = o.optJSONObject("gw");
        if (gw != null) for (Iterator<String> it = gw.keys(); it.hasNext(); ) {
            String k = it.next();
            t.gw[Integer.parseInt(k)] = fa(gw.optJSONArray(k));
        }
        JSONObject gh = o.optJSONObject("gh");
        if (gh != null) for (Iterator<String> it = gh.keys(); it.hasNext(); ) {
            String k = it.next();
            t.gh[Integer.parseInt(k)] = fa(gh.optJSONArray(k));
        }
        JSONArray j = o.optJSONArray("j");
        if (j != null && t.n > 1) {
            t.jump = new boolean[t.n];
            for (int i = 0; i < j.length(); i++) {
                int x = j.optInt(i);
                if (x >= 0 && x < t.n) t.jump[x] = true;
            }
        }
        JSONArray pre = o.optJSONArray("pre");
        if (pre != null) t.pre = fa(pre);
        return t;
    }

    static String read(String path) throws Exception {
        try (InputStream in = App.ctx.getAssets().open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        }
    }

    public static boolean exists(String id) {
        try {
            String[] l = App.ctx.getAssets().list("deco");
            if (l != null) for (String s : l) if (s.equals(id + ".json")) return true;
        } catch (Exception e) {
        }
        return false;
    }

    public static DecoItem load(String id) {
        return load(id, 40L << 20);
    }

    public static DecoItem load(String id, long budget) {
        try {
            return new DecoItem(new JSONObject(read("deco/" + id + ".json")), budget);
        } catch (Throwable e) {
            android.util.Log.w("deco", "load " + id, e);
            return null;
        }
    }

    int ss = 1;

    DecoItem(JSONObject o, long budget) throws Exception {
        id = o.getString("id");
        kind = o.getString("kind");
        W0 = (float) o.optDouble("W0", 104);
        H0 = (float) o.optDouble("H0", 104);
        JSONArray at = o.getJSONArray("atlas");
        atlas = new Bitmap[at.length()];
        long total = 0;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        for (int i = 0; i < atlas.length; i++) {
            try (InputStream in = App.ctx.getAssets().open("deco/" + at.getString(i))) {
                BitmapFactory.decodeStream(in, null, bounds);
                total += 4L * Math.max(0, bounds.outWidth) * Math.max(0, bounds.outHeight);
            }
        }
        while (ss < 16 && total / ((long) ss * ss) > budget) ss *= 2;
        while (true) {
            BitmapFactory.Options bo = new BitmapFactory.Options();
            bo.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bo.inSampleSize = ss;
            try {
                for (int i = 0; i < atlas.length; i++) {
                    try (InputStream in = App.ctx.getAssets().open("deco/" + at.getString(i))) {
                        atlas[i] = BitmapFactory.decodeStream(in, null, bo);
                    }
                }
                break;
            } catch (OutOfMemoryError e) {
                for (int i = 0; i < atlas.length; i++) {
                    if (atlas[i] != null) atlas[i].recycle();
                    atlas[i] = null;
                }
                if (ss >= 32) throw new Exception("deco oom");
                ss *= 2;
            }
        }
        JSONArray sp = o.getJSONArray("sprites");
        spr = new float[sp.length()][];
        for (int i = 0; i < spr.length; i++) {
            JSONArray a = sp.optJSONArray(i);
            spr[i] = a == null ? null : fa(a);
        }
        JSONArray ls = o.getJSONArray("layers");
        layers = new Layer[ls.length()];
        int[] cnt = new int[layers.length];
        int nr = 0;
        for (int i = 0; i < layers.length; i++) {
            JSONObject l = ls.getJSONObject(i);
            Layer L = new Layer();
            L.p = l.optInt("p", -1);
            L.svg = "s".equals(l.optString("t"));
            L.p3d = l.optInt("p3d", 0) == 1;
            L.A = track(l.getJSONObject("A"), false);
            L.O = track(l.getJSONObject("O"), true);
            if (l.has("persp")) L.persp = fa(l.getJSONArray("persp"));
            if (l.has("box")) L.box = fa(l.getJSONArray("box"));
            if (l.has("rbox")) L.rbox = fa(l.getJSONArray("rbox"));
            L.elastic = l.optInt("elastic", 0);
            JSONObject cl = l.optJSONObject("clip");
            if (cl != null) {
                if ("m".equals(cl.optString("t"))) {
                    L.clip = 2;
                    L.mask = cl.optInt("s", -1);
                    JSONArray mb = cl.optJSONArray("mb");
                    if (mb != null) L.mb = fa(mb);
                } else {
                    L.clip = 1;
                    JSONArray r = cl.optJSONArray("rad");
                    L.rad = r == null ? new float[]{0} : fa(r);
                }
            }
            L.sprite = l.optInt("s", -1);
            JSONObject f = l.optJSONObject("f");
            if (f != null) {
                L.fP = (float) f.optDouble("P", 1000);
                JSONArray s = f.getJSONArray("s");
                L.frames = new int[s.length()];
                for (int k = 0; k < L.frames.length; k++) L.frames[k] = s.optInt(k, -1);
            }
            layers[i] = L;
            if (L.p >= 0) cnt[L.p]++;
            else nr++;
        }
        kids = new int[layers.length][];
        for (int i = 0; i < layers.length; i++) kids[i] = new int[cnt[i]];
        roots = new int[nr];
        int[] fill = new int[layers.length];
        int rr = 0;
        for (int i = 0; i < layers.length; i++) {
            int p = layers[i].p;
            if (p >= 0) kids[p][fill[p]++] = i;
            else roots[rr++] = i;
        }
    }

    public long bytes() {
        long n = 0;
        for (Bitmap b : atlas) if (b != null) n += b.getByteCount();
        return n;
    }

    static void mul(float[] a, float[] b, float[] out) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
            }
        }
    }

    static void flatten(float[] m) {
        m[2] = 0;
        m[6] = 0;
        m[14] = 0;
        m[8] = 0;
        m[9] = 0;
        m[11] = 0;
        m[10] = 1;
    }

    static void ident(float[] m) {
        for (int i = 0; i < 16; i++) m[i] = (i % 5 == 0) ? 1 : 0;
    }

    final class Ctx {
        Canvas cv;
        float t, W, H, dw, dh;
        final float[][] acc = new float[layers.length][16];
        final float[][] plane = new float[layers.length][16];
        final float[][] draw = new float[layers.length][16];
        final float[] alpha = new float[layers.length];
        final float[] tmp = new float[16], tmp2 = new float[16], loc = new float[16], op = new float[16];
        final float[] base = new float[16];
        final Matrix mx = new Matrix();
        final float[] mv = new float[9];
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final Paint mpaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final Rect src = new Rect();
        final RectF dst = new RectF();
        final Path path = new Path();
        final RectF rf = new RectF();
        final float[] radii = new float[8];

        Ctx() {
            mpaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }
    }

    Ctx ctx;

    public synchronized void draw(Canvas cv, float t, float W, float H, float x, float y, float scale) {
        if (ctx == null) ctx = new Ctx();
        Ctx c = ctx;
        c.cv = cv;
        c.t = t;
        c.W = W;
        c.H = H;
        c.dw = W - W0;
        c.dh = H - H0;
        ident(c.base);
        c.base[0] = scale;
        c.base[5] = scale;
        c.base[10] = scale;
        c.base[12] = x;
        c.base[13] = y;
        for (int r : roots) layer(c, r, c.base, null, false, 1f);
    }

    void layer(Ctx c, int i, float[] parentDraw, float[] parentAcc, boolean parentP3d, float parentAlpha) {
        Layer L = layers[i];
        L.O.eval(c.t, c.dw, c.dh, c.op);
        float a = parentAlpha * Math.max(0f, Math.min(1f, c.op[0]));
        if (a <= .002f) return;
        float[] loc = c.loc;
        System.arraycopy(L.A.c, 0, loc, 0, 16);
        L.A.eval(c.t, c.dw, c.dh, loc);
        if (L.p >= 0 && layers[L.p].persp != null) {
            float[] pp = layers[L.p].persp;
            float ox = pp[1] + pp[2] * c.dw + pp[3] * c.dh, oy = pp[4] + pp[5] * c.dw + pp[6] * c.dh, d = pp[0];
            float[] q = c.tmp;
            ident(q);
            q[8] = -ox / d;
            q[9] = -oy / d;
            q[11] = -1f / d;
            mul(q, loc, c.tmp2);
            System.arraycopy(c.tmp2, 0, loc, 0, 16);
        }
        float[] acc = c.acc[i], plane = c.plane[i], draw = c.draw[i];
        if (parentP3d && parentAcc != null) {
            mul(parentAcc, loc, acc);
            System.arraycopy(c.plane[L.p], 0, plane, 0, 16);
        } else {
            System.arraycopy(loc, 0, acc, 0, 16);
            System.arraycopy(parentDraw, 0, plane, 0, 16);
        }
        float[] fl = c.tmp;
        System.arraycopy(acc, 0, fl, 0, 16);
        flatten(fl);
        mul(plane, fl, draw);
        float bw = 0, bh = 0;
        if (L.box != null) {
            bw = L.box[0];
            bh = L.box[1];
            if (L.box.length >= 6) {
                bw += L.box[2] * c.dw + L.box[3] * c.dh;
                bh += L.box[4] * c.dw + L.box[5] * c.dh;
            }
        }
        int save = -1;
        Canvas cv = c.cv;
        if (L.clip == 1 && kids[i].length > 0) {
            save = cv.save();
            setMatrix(c, draw);
            c.path.reset();
            c.rf.set(0, 0, bw, bh);
            float r0 = L.rad.length > 0 ? L.rad[0] : 0;
            for (int k = 0; k < 4; k++) {
                float r = L.rad.length > k ? L.rad[k] : r0;
                c.radii[k * 2] = r;
                c.radii[k * 2 + 1] = r;
            }
            c.path.addRoundRect(c.rf, c.radii, Path.Direction.CW);
            c.path.transform(c.mx);
            cv.clipPath(c.path);
        } else if (L.clip == 2 && L.mask >= 0 && L.mask < spr.length && spr[L.mask] != null && kids[i].length > 0) {
            float[] ms = spr[L.mask];
            setMatrix(c, draw);
            float sx = msx(L, bw), sy = msy(L, bh);
            c.rf.set(ms[5] * sx, ms[6] * sy, (ms[5] + ms[3] / ms[7]) * sx, (ms[6] + ms[4] / ms[7]) * sy);
            c.mx.mapRect(c.rf);
            save = cv.saveLayer(c.rf, null);
        }
        int sp = L.sprite;
        if (L.frames != null && L.frames.length > 0) {
            float ph = c.t % L.fP;
            if (ph < 0) ph += L.fP;
            int k = (int) (ph / L.fP * L.frames.length);
            sp = L.frames[Math.min(k, L.frames.length - 1)];
        }
        if (sp >= 0 && sp < spr.length && spr[sp] != null) drawSprite(c, L, spr[sp], draw, a, bw, bh);
        for (int k : kids[i]) layer(c, k, draw, acc, L.p3d, a);
        if (L.clip == 2 && save >= 0) blit(c, spr[L.mask], draw, c.mpaint, msx(L, bw), msy(L, bh));
        if (save >= 0) cv.restoreToCount(save);
    }

    static float msx(Layer L, float bw) {
        return L.mb == null || L.mb[0] <= 0 || bw <= 0 ? 1 : bw / L.mb[0];
    }

    static float msy(Layer L, float bh) {
        return L.mb == null || L.mb[1] <= 0 || bh <= 0 ? 1 : bh / L.mb[1];
    }

    void setMatrix(Ctx c, float[] m) {
        c.mv[0] = m[0];
        c.mv[1] = m[4];
        c.mv[2] = m[12];
        c.mv[3] = m[1];
        c.mv[4] = m[5];
        c.mv[5] = m[13];
        c.mv[6] = m[3];
        c.mv[7] = m[7];
        c.mv[8] = m[15];
        c.mx.setValues(c.mv);
    }

    void blit(Ctx c, float[] s, float[] m, Paint p, float sx, float sy) {
        Bitmap b = atlas[(int) s[0]];
        if (b == null) return;
        setMatrix(c, m);
        Canvas cv = c.cv;
        int sv = cv.save();
        cv.concat(c.mx);
        if (sx != 1 || sy != 1) cv.scale(sx, sy);
        float k = s[7];
        c.src.set((int) (s[1] / ss), (int) (s[2] / ss), (int) ((s[1] + s[3]) / ss), (int) ((s[2] + s[4]) / ss));
        c.dst.set(s[5], s[6], s[5] + s[3] / k, s[6] + s[4] / k);
        cv.drawBitmap(b, c.src, c.dst, p);
        cv.restoreToCount(sv);
    }

    void drawSprite(Ctx c, Layer L, float[] s, float[] m, float a, float bw, float bh) {
        c.paint.setAlpha(Math.round(a * 255));
        if (L.elastic == 0 || L.rbox == null || bw <= 0 || bh <= 0) {
            blit(c, s, m, c.paint, 1, 1);
            return;
        }
        float rw = L.rbox[0], rh = L.rbox[1];
        if (L.elastic == 1) {
            blit(c, s, m, c.paint, bw / rw, bh / rh);
            return;
        }
        Bitmap b = atlas[(int) s[0]];
        if (b == null) return;
        setMatrix(c, m);
        Canvas cv = c.cv;
        int sv = cv.save();
        cv.concat(c.mx);
        float k = s[7];
        float ox = s[5], oy = s[6], sw = s[3] / k, sh = s[4] / k;
        float ins = Math.min(48f, Math.min(rw, rh) / 2f - 1f);
        float[] xs = cuts(ox, ox + sw, ins, rw - ins);
        float[] ys = cuts(oy, oy + sh, ins, rh - ins);
        for (int yi = 0; yi + 1 < ys.length; yi++) {
            for (int xi = 0; xi + 1 < xs.length; xi++) {
                float x0 = xs[xi], x1 = xs[xi + 1], y0 = ys[yi], y1 = ys[yi + 1];
                if (x1 - x0 <= 0 || y1 - y0 <= 0) continue;
                c.src.set((int) Math.floor((s[1] + (x0 - ox) * k) / ss), (int) Math.floor((s[2] + (y0 - oy) * k) / ss), (int) Math.ceil((s[1] + (x1 - ox) * k) / ss), (int) Math.ceil((s[2] + (y1 - oy) * k) / ss));
                c.dst.set(map(x0, ins, rw, bw), map(y0, ins, rh, bh), map(x1, ins, rw, bw), map(y1, ins, rh, bh));
                cv.drawBitmap(b, c.src, c.dst, c.paint);
            }
        }
        cv.restoreToCount(sv);
    }

    static float[] cuts(float a, float b, float c1, float c2) {
        float[] r = new float[4];
        int n = 0;
        r[n++] = a;
        if (c1 > a && c1 < b) r[n++] = c1;
        if (c2 > a && c2 < b && c2 > c1) r[n++] = c2;
        r[n++] = b;
        float[] o = new float[n];
        System.arraycopy(r, 0, o, 0, n);
        return o;
    }

    static float map(float x, float ins, float r, float b) {
        if (x <= ins) return x;
        if (x >= r - ins) return x + (b - r);
        float mid = r - 2 * ins;
        if (mid <= 0) return x;
        return ins + (x - ins) * (b - 2 * ins) / mid;
    }
}
