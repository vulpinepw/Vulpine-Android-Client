package com.vulpine.at.stories;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.widget.ImageView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.core.Http;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Media {
    public interface Picked {
        void on(File f, String mime, String name);
    }

    public static final class Stroke {
        public int color;
        public float w = .012f;
        public final java.util.ArrayList<float[]> p = new java.util.ArrayList<>();
    }

    public static final class Probe {
        public int w, h;
        public double dur;
    }

    static final Map<String, Bitmap> frames = new HashMap<>();

    public static void pick(MainActivity a, String[] mimes, boolean media, Picked cb) {
        Intent i;
        if (media && Build.VERSION.SDK_INT >= 33) {
            i = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(mimes.length == 1 ? mimes[0] : "*/*");
            if (mimes.length > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        }
        App.startForResult(a, i, (res, data) -> {
            if (res != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
            Uri u = data.getData();
            App.bg(() -> {
                String mime = a.getContentResolver().getType(u);
                String name = "file";
                try (Cursor c = a.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0);
                } catch (Exception e) {
                }
                if (mime == null) mime = com.vulpine.at.ui.Fmt.mimeFromName(name);
                File dir = new File(a.getCacheDir(), "pick");
                dir.mkdirs();
                File f = new File(dir, System.currentTimeMillis() + "-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (InputStream in = a.getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(f)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                } catch (Exception e) {
                    App.ui(() -> com.vulpine.at.ui.Ov.toast("Could not open the file", true));
                    return;
                }
                String m = mime, nm = name;
                App.ui(() -> cb.on(f, m, nm));
            });
        });
    }

    public static Map<String, String> headers() {
        return headers("/uploads/x");
    }

    public static Map<String, String> headers(String url) {
        Map<String, String> h = new HashMap<>();
        if (Http.token != null) h.put("Authorization", "Bearer " + Http.token);
        String abs = Http.abs(url);
        String lc = Http.cookieFor(abs);
        if (lc != null) h.put("Cookie", lc);
        else try {
            List<HttpCookie> cs = Http.cookies().getCookieStore().get(new URI(abs));
            StringBuilder b = new StringBuilder();
            for (HttpCookie c : cs) {
                if (b.length() > 0) b.append("; ");
                b.append(c.getName()).append('=').append(c.getValue());
            }
            if (b.length() > 0) h.put("Cookie", b.toString());
        } catch (Exception e) {
        }
        h.put("User-Agent", Http.userAgent);
        return h;
    }

    static Bitmap frameOf(MediaMetadataRetriever r) {
        Bitmap b = r.getFrameAtTime(100000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        if (b == null) b = r.getFrameAtTime();
        if (b != null && Math.max(b.getWidth(), b.getHeight()) > 480) {
            float s = 480f / Math.max(b.getWidth(), b.getHeight());
            b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * s), Math.round(b.getHeight() * s), true);
        }
        return b;
    }

    public static void frameInto(ImageView iv, String url) {
        iv.setTag(url);
        Bitmap hit;
        synchronized (frames) {
            hit = frames.get(url);
        }
        if (hit != null) {
            iv.setImageBitmap(hit);
            return;
        }
        App.bg(() -> {
            Bitmap b = null;
            File local = com.vulpine.at.ui.Img.cached(url);
            for (int k = local != null ? 1 : 0; k < 2 && b == null; k++) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    if (k == 0) r.setDataSource(Http.abs(url), headers(url));
                    else {
                        File f = local != null ? local : com.vulpine.at.ui.Img.file(url);
                        if (f == null) break;
                        r.setDataSource(f.getPath());
                    }
                    b = frameOf(r);
                } catch (Throwable e) {
                } finally {
                    try {
                        r.release();
                    } catch (Throwable e) {
                    }
                }
            }
            Bitmap fb = b;
            if (fb != null) synchronized (frames) {
                frames.put(url, fb);
            }
            App.ui(() -> {
                if (fb != null && url.equals(iv.getTag())) iv.setImageBitmap(fb);
            });
        });
    }

    public static Probe probe(File f) {
        Probe p = new Probe();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getPath());
            p.w = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            p.h = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rot = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (rot == 90 || rot == 270) {
                int t = p.w;
                p.w = p.h;
                p.h = t;
            }
            p.dur = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0;
        } catch (Throwable e) {
        } finally {
            try {
                r.release();
            } catch (Throwable e) {
            }
        }
        return p;
    }

    static int parse(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static Bitmap decode(File f, int maxSide) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (dec, info, src) -> {
                    dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    dec.setMutableRequired(true);
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    float sc = Math.min(1f, maxSide / (float) Math.max(w, h));
                    if (sc < 1f) dec.setTargetSize(Math.max(1, Math.round(w * sc)), Math.max(1, Math.round(h * sc)));
                });
            }
        } catch (Throwable e) {
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        int deg = 0;
        try {
            android.media.ExifInterface ex = new android.media.ExifInterface(f.getPath());
            int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            deg = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
        } catch (Exception e) {
        }
        float sc = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
        if (deg == 0 && sc >= 1f) return b;
        Matrix m = new Matrix();
        m.postScale(sc, sc);
        m.postRotate(deg);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    public static File flatten(File src, List<Stroke> strokes, File out, int[] dims) {
        Bitmap b = decode(src, 1280);
        if (b == null) return null;
        float sc = Math.min(1f, 1280f / Math.max(b.getWidth(), b.getHeight()));
        int cw = Math.max(1, Math.round(b.getWidth() * sc)), ch = Math.max(1, Math.round(b.getHeight() * sc));
        Bitmap o = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(o);
        c.drawBitmap(b, null, new android.graphics.Rect(0, 0, cw, ch), new Paint(Paint.FILTER_BITMAP_FLAG));
        drawStrokes(c, strokes, cw, ch);
        dims[0] = cw;
        dims[1] = ch;
        try (FileOutputStream fo = new FileOutputStream(out)) {
            Bitmap.CompressFormat fmt = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            if (!o.compress(fmt, 80, fo)) return null;
        } catch (Exception e) {
            return null;
        }
        return out;
    }

    public static void drawStrokes(Canvas c, List<Stroke> strokes, float w, float h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (Stroke s : strokes) {
            p.setColor(s.color);
            p.setStrokeWidth(s.w * w);
            Path path = new Path();
            for (int k = 0; k < s.p.size(); k++) {
                float[] pt = s.p.get(k);
                if (k == 0) path.moveTo(pt[0] * w, pt[1] * h);
                else path.lineTo(pt[0] * w, pt[1] * h);
            }
            if (s.p.size() == 1) path.lineTo(s.p.get(0)[0] * w + .01f, s.p.get(0)[1] * h);
            c.drawPath(path, p);
        }
    }

    public static File maybeTranscode(File f, long limit, Transcode.Progress pr) {
        long size = f.length();
        if (size <= limit) return f;
        Probe p = probe(f);
        if (p.dur <= 0) return f;
        File out = new File(f.getParentFile(), f.getName() + ".t.mp4");
        long budget = limit * 85 / 100 * 8;
        int vbr = (int) Math.max(800000, Math.min(4500000, budget / Math.max(1, p.dur) - 160000));
        try {
            if (Transcode.run(f, out, 1280, vbr, pr) && out.length() > 0 && out.length() < size && probe(out).dur > 0) return out;
        } catch (Throwable e) {
        }
        out.delete();
        return f;
    }
}
