package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.io.File;
import java.io.FileOutputStream;

public final class Crop {
    public interface Cb {
        void on(File out, String mime);
    }

    static Modal chrome(Context c, String title) {
        Modal m = new Modal(c);
        m.card.setBackground(W.round(T.dark ? 0xff141416 : 0xffffffff, 16, T.dark ? 0xff2a2a2e : 0xffe3e2df, 1));
        m.card.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
        m.card.addView(W.text(c, title, 15, 600, T.dark ? 0xfff0f0f2 : T.text));
        return m;
    }

    static TextView btn(Context c, String label, boolean primary, View.OnClickListener on) {
        TextView b = W.text(c, label, 13, 600, primary ? (T.dark ? 0xff0b0b0c : 0xffffffff) : (T.dark ? 0xfff0f0f2 : T.text));
        b.setGravity(Gravity.CENTER);
        b.setPadding(T.dp(18), T.dp(8), T.dp(18), T.dp(8));
        b.setBackground(W.ripple(primary ? W.accentBg(10) : W.round(0, 10, T.dark ? 0xff3a3a40 : 0xffccc9c4, 1), 10));
        b.setOnClickListener(on);
        return b;
    }

    static LinearLayout actions(Context c, View a, View b) {
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        r.addView(a);
        LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
        p.leftMargin = T.dp(10);
        r.addView(b, p);
        return r;
    }

    public static void image(MainActivity a, File f, String mime, int size, Cb cb) {
        String m = Pick.sniff(f);
        if (m == null) {
            cb.on(f, mime);
            return;
        }
        if (m.equals("image/gif")) {
            gif(a, f, cb);
            return;
        }
        App.bg(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= 2048 || o.outHeight / (s * 2) >= 2048) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap bm = BitmapFactory.decodeFile(f.getPath(), o2);
            int rot = 0;
            try {
                android.media.ExifInterface ex = new android.media.ExifInterface(f.getPath());
                int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                rot = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
            } catch (Exception e) {
            }
            if (bm != null && rot != 0) {
                Matrix mx = new Matrix();
                mx.postRotate(rot);
                bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), mx, true);
            }
            Bitmap fb = bm;
            App.ui(() -> {
                if (fb == null) cb.on(f, mime);
                else crop(a, fb, size, cb);
            });
        });
    }

    static void gif(MainActivity a, File f, Cb cb) {
        Modal m = chrome(a, "Use this GIF?");
        ImageView iv = new ImageView(a);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackground(W.round(T.dark ? 0xff0b0b0c : 0xfff1f0ee, 12));
        iv.setClipToOutline(true);
        int maxH = Math.min(Math.round(a.getResources().getDisplayMetrics().heightPixels * .46f), T.dp(300));
        iv.setMaxHeight(maxH);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                Drawable d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(f));
                iv.setImageDrawable(d);
                if (d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
            } else iv.setImageBitmap(BitmapFactory.decodeFile(f.getPath()));
        } catch (Exception e) {
            iv.setImageBitmap(BitmapFactory.decodeFile(f.getPath()));
        }
        LinearLayout.LayoutParams ip = W.lp(W.WC, W.WC);
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        m.card.addView(iv, ip);
        S.add(m.card, W.text(a, "GIFs are uploaded as-is to keep the animation — no crop.", 12, 400, 0xff9a9aa0), 10);
        boolean[] done = {false};
        S.add(m.card, actions(a, btn(a, "Cancel", false, v -> m.close()), btn(a, "Use GIF", true, v -> {
            done[0] = true;
            m.close();
            cb.on(f, "image/gif");
        })), 14);
        m.show();
    }

    static void crop(MainActivity a, Bitmap bm, int size, Cb cb) {
        Modal m = chrome(a, "Crop image");
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int V = T.dp(Math.max(220, Math.min(380, (int) Math.floor(Math.min(dm.widthPixels / dm.density * .6f, dm.heightPixels / dm.density * .5f)))));
        int w = bm.getWidth(), h = bm.getHeight();
        float cov = Math.max(V / (float) w, V / (float) h);
        float[] st = {cov, (V - w * cov) / 2, (V - h * cov) / 2};
        S.Slider[] sl = new S.Slider[1];
        View cv = new View(a) {
            final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            final Path clip = new Path();
            float lx, ly;
            final ScaleGestureDetector sg = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                    float px = (d.getFocusX() - st[1]) / st[0], py = (d.getFocusY() - st[2]) / st[0];
                    st[0] *= d.getScaleFactor();
                    clamp();
                    st[1] = d.getFocusX() - px * st[0];
                    st[2] = d.getFocusY() - py * st[0];
                    clamp();
                    invalidate();
                    return true;
                }
            });

            void clamp() {
                st[0] = Math.max(cov, Math.min(cov * 20, st[0]));
                st[1] = Math.min(0, Math.max(V - w * st[0], st[1]));
                st[2] = Math.min(0, Math.max(V - h * st[0], st[2]));
                if (sl[0] != null) sl[0].setValue(Math.round(st[0] / cov * 10) / 10f);
            }

            protected void onMeasure(int a1, int b1) {
                setMeasuredDimension(V, V);
            }

            protected void onDraw(Canvas c) {
                clip.reset();
                clip.addRoundRect(new RectF(0, 0, V, V), T.dpf(12), T.dpf(12), Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                c.drawColor(T.dark ? 0xff0b0b0c : 0xfff1f0ee);
                Matrix mx = new Matrix();
                mx.setScale(st[0], st[0]);
                mx.postTranslate(st[1], st[2]);
                c.drawBitmap(bm, mx, p);
                Paint g = new Paint();
                g.setColor(0x47ffffff);
                g.setStrokeWidth(Math.max(1, T.dp(1)));
                for (int i = 1; i < 3; i++) {
                    float q = V * i / 3f;
                    c.drawLine(q, 0, q, V, g);
                    c.drawLine(0, q, V, q, g);
                }
                c.restore();
            }

            public boolean onTouchEvent(MotionEvent e) {
                getParent().requestDisallowInterceptTouchEvent(true);
                sg.onTouchEvent(e);
                if (e.getPointerCount() > 1 || sg.isInProgress()) {
                    lx = -1;
                    return true;
                }
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lx = e.getX();
                        ly = e.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (lx < 0) {
                            lx = e.getX();
                            ly = e.getY();
                            break;
                        }
                        st[1] += e.getX() - lx;
                        st[2] += e.getY() - ly;
                        lx = e.getX();
                        ly = e.getY();
                        clamp();
                        invalidate();
                        break;
                }
                return true;
            }
        };
        LinearLayout.LayoutParams cp = W.lp(V, V);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        m.card.addView(cv, cp);
        LinearLayout zr = W.row(a);
        zr.setPadding(T.dp(2), T.dp(12), T.dp(2), T.dp(2));
        zr.addView(W.text(a, "−", 16, 400, 0xff9a9aa0));
        sl[0] = new S.Slider(a, 1, 20, .1f, 1, v -> {
            float cx = V / 2f, cy = V / 2f;
            float px = (cx - st[1]) / st[0], py = (cy - st[2]) / st[0];
            st[0] = cov * v;
            st[0] = Math.max(cov, Math.min(cov * 20, st[0]));
            st[1] = cx - px * st[0];
            st[2] = cy - py * st[0];
            st[1] = Math.min(0, Math.max(V - w * st[0], st[1]));
            st[2] = Math.min(0, Math.max(V - h * st[0], st[2]));
            cv.invalidate();
        }, null);
        LinearLayout.LayoutParams sp = W.lp(0, T.dp(20), 1);
        sp.leftMargin = sp.rightMargin = T.dp(10);
        zr.addView(sl[0], sp);
        zr.addView(W.text(a, "+", 16, 400, 0xff9a9aa0));
        m.card.addView(zr);
        S.add(m.card, actions(a, btn(a, "Cancel", false, v -> m.close()), btn(a, "Apply", true, v -> {
            m.close();
            App.bg(() -> {
                Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas oc = new Canvas(out);
                float k = size / (float) V;
                Matrix mx = new Matrix();
                mx.setScale(st[0] * k, st[0] * k);
                mx.postTranslate(st[1] * k, st[2] * k);
                oc.drawBitmap(bm, mx, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
                File f = new File(a.getCacheDir(), "crop-" + System.currentTimeMillis() + ".webp");
                try (FileOutputStream o = new FileOutputStream(f)) {
                    if (android.os.Build.VERSION.SDK_INT >= 30) out.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, o);
                    else out.compress(Bitmap.CompressFormat.WEBP, 92, o);
                } catch (Exception e) {
                }
                App.ui(() -> cb.on(f, "image/webp"));
            });
        })), 14);
        m.show();
    }
}
