package com.vulpine.at.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import com.vulpine.core.Http;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Img {
    public interface Cb {
        void on(Drawable d, Bitmap b);
    }

    static final Handler H = new Handler(Looper.getMainLooper());
    static final ExecutorService NET = Executors.newFixedThreadPool(4);
    static final ExecutorService DEC = Executors.newFixedThreadPool(2);
    static LruCache<String, Bitmap> mem;
    static File dir;
    static int trimTick;
    static final Map<String, Object> inflight = new HashMap<>();

    public static void init(Context c) {
        int max = (int) (Runtime.getRuntime().maxMemory() / 6);
        mem = new LruCache<String, Bitmap>(max) {
            protected int sizeOf(String k, Bitmap v) {
                return v.getByteCount();
            }
        };
        dir = new File(c.getCacheDir(), "img");
        dir.mkdirs();
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public static void prime(String url, File local) {
        try {
            if (local == null || !local.exists()) return;
            for (String u : new String[]{url.replaceAll("#.*$", ""), Http.abs(url).replaceAll("#.*$", "")}) {
                File f = new File(dir, hash(u));
                if (f.exists() && f.length() > 0) continue;
                File t = new File(dir, hash(u) + ".tmp");
                java.nio.file.Files.copy(local.toPath(), t.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                t.renameTo(f);
            }
        } catch (Exception e) {
        }
    }

    public static File file(String url) {
        if (url.startsWith("file:")) {
            File f = new File(url.substring(5).replaceAll("#.*$", ""));
            return f.exists() ? f : null;
        }
        if (url.startsWith("asset:")) {
            File f = new File(dir, "asset-" + url.substring(6).replace('/', '_'));
            if (f.exists() && f.length() > 0) return f;
            try (java.io.InputStream in = com.vulpine.at.App.ctx.getAssets().open(url.substring(6)); FileOutputStream o = new FileOutputStream(f)) {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
                return f;
            } catch (Exception e) {
                return null;
            }
        }
        String u = url.replaceAll("#.*$", "");
        File f = new File(dir, hash(u));
        if (f.exists() && f.length() > 0) return f;
        try {
            byte[] b = Http.bytes(u, 40 * 1024 * 1024);
            File t = new File(dir, hash(u) + ".tmp");
            try (FileOutputStream o = new FileOutputStream(t)) {
                o.write(b);
            }
            t.renameTo(f);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    public static File cached(String url) {
        if (url.startsWith("file:")) return file(url);
        File f = new File(dir, hash(url.replaceAll("#.*$", "")));
        return f.exists() && f.length() > 0 ? f : null;
    }

    public static void get(String url0, int maxPx, boolean anim, Cb cb) {
        if (url0 == null) {
            cb.on(null, null);
            return;
        }
        final String url = url0.startsWith("asset:") || url0.startsWith("file:") ? url0 : Http.abs(url0);
        String key = url + "@" + maxPx;
        Bitmap hit = anim ? null : mem.get(key);
        if (hit != null) {
            cb.on(null, hit);
            return;
        }
        NET.execute(() -> {
            File f = file(url);
            if (++trimTick % 200 == 0) com.vulpine.at.settings.StoragePage.trim();
            if (f == null) {
                H.post(() -> cb.on(null, null));
                return;
            }
            DEC.execute(() -> {
                try {
                    if (anim && Build.VERSION.SDK_INT >= 28 && isAnimated(f)) {
                        ImageDecoder.Source src = ImageDecoder.createSource(ByteBuffer.wrap(Files.readAllBytes(f.toPath())));
                        Drawable d = ImageDecoder.decodeDrawable(src, (dec, info, s) -> {
                            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                            float sc = Math.min(1f, maxPx / (float) Math.max(w, h));
                            if (sc < 1f) dec.setTargetSize(Math.max(1, Math.round(w * sc)), Math.max(1, Math.round(h * sc)));
                        });
                        H.post(() -> cb.on(d, null));
                        return;
                    }
                    Bitmap b = decode(f, maxPx);
                    if (b != null) mem.put(key, b);
                    H.post(() -> cb.on(null, b));
                } catch (Throwable e) {
                    H.post(() -> cb.on(null, null));
                }
            });
        });
    }

    static boolean isAnimated(File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] h = new byte[40];
            int n = in.read(h);
            if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F') return true;
            if (n >= 30 && h[0] == 'R' && h[8] == 'W' && h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == 'X') return (h[20] & 0x02) != 0;
        } catch (Exception e) {
        }
        return false;
    }

    public static Bitmap decode(File f, int maxPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null && Build.VERSION.SDK_INT >= 28) {
            try {
                b = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (dec, info, src) -> {
                    dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    float sc = Math.min(1f, maxPx / (float) Math.max(w, h));
                    if (sc < 1f) dec.setTargetSize(Math.max(1, Math.round(w * sc)), Math.max(1, Math.round(h * sc)));
                });
            } catch (Exception e) {
            }
        }
        return b;
    }

    public static void into(ImageView v, String url, int maxPx) {
        v.setTag(url);
        get(url, maxPx, false, (d, b) -> {
            if (url.equals(v.getTag()) && b != null) v.setImageBitmap(b);
        });
    }

    public static void anim(ImageView v, String url, int maxPx, boolean play) {
        v.setTag(url);
        get(url, maxPx, true, (d, b) -> {
            if (!url.equals(v.getTag())) return;
            if (d != null) {
                v.setImageDrawable(d);
                if (play && Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
            } else if (b != null) v.setImageBitmap(b);
        });
    }
}
