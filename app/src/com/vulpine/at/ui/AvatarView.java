package com.vulpine.at.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import com.vulpine.core.Http;

public class AvatarView extends View {
    final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG), im = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    String letter = "?";
    int color = 0xff808080;
    Bitmap bmp;
    String url;
    float textPx;
    Drawable anim;
    public boolean animate;
    public int ring;

    public AvatarView(Context c) {
        super(c);
        tx.setTypeface(T.sb);
        tx.setColor(Color.WHITE);
        tx.setTextAlign(Paint.Align.CENTER);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) {
                int d = Math.min(v.getWidth(), v.getHeight());
                o.setOval((v.getWidth() - d) / 2, (v.getHeight() - d) / 2, (v.getWidth() + d) / 2, (v.getHeight() + d) / 2);
            }
        });
    }

    public static int hue(String name) {
        int n = 5381;
        String s = name.toLowerCase();
        for (int i = 0; i < s.length(); i++) {
            double m = (double) n * 33;
            n = toInt32(m) ^ s.charAt(i);
        }
        return Math.abs(n) % 360;
    }

    static int toInt32(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
        double t = d < 0 ? Math.ceil(d) : Math.floor(d);
        double m = t % 4294967296.0;
        if (m < 0) m += 4294967296.0;
        long l = (long) m;
        return (int) l;
    }

    public static int colorFor(String name, String avatarColor) {
        if (avatarColor != null && !avatarColor.isEmpty()) {
            Integer c = Css.color(avatarColor);
            if (c != null) return c;
        }
        if (avatarColor != null) return T.accent;
        float h = hue(name == null ? "" : name);
        return hsl(h, .70f, .45f);
    }

    static int hsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = l - c / 2;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; } else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    public void set(String name, String avatarColor, String avatarUrl) {
        String n = name == null ? "" : name.trim();
        letter = n.isEmpty() ? "?" : new String(Character.toChars(Character.toUpperCase(n.codePointAt(0))));
        color = colorFor(n, avatarColor);
        String u = avatarUrl == null || avatarUrl.isEmpty() || "null".equals(avatarUrl) ? null : Http.abs(avatarUrl);
        if (u == null) {
            url = null;
            bmp = null;
            anim = null;
            invalidate();
            return;
        }
        if (u.equals(url) && (bmp != null || anim != null)) return;
        url = u;
        bmp = null;
        anim = null;
        invalidate();
        int px = Math.max(64, getWidth() > 0 ? getWidth() : getLayoutParams() != null && getLayoutParams().width > 0 ? getLayoutParams().width : T.dp(128));
        boolean gif = u.toLowerCase().contains(".gif");
        if (gif && animate && !T.power) {
            String prev = u.replaceAll("#.*$", "") + ".vp.webp";
            Img.get(prev, px, true, (d, b) -> {
                if (!u.equals(url)) return;
                if (d != null) setAnim(d);
                else Img.get(u, px, true, (d2, b2) -> {
                    if (!u.equals(url)) return;
                    if (d2 != null) setAnim(d2);
                    else if (b2 != null) setBmp(b2);
                });
            });
            return;
        }
        Img.get(u, px, false, (d, b) -> {
            if (u.equals(url) && b != null) setBmp(b);
        });
    }

    void setBmp(Bitmap b) {
        bmp = b;
        anim = null;
        setClipToOutline(false);
        invalidate();
    }

    void setAnim(Drawable d) {
        anim = d;
        bmp = null;
        setClipToOutline(true);
        d.setCallback(this);
        if (android.os.Build.VERSION.SDK_INT >= 28 && d instanceof android.graphics.drawable.AnimatedImageDrawable) ((android.graphics.drawable.AnimatedImageDrawable) d).start();
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == anim || super.verifyDrawable(who);
    }

    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f;
        if (anim != null) {
            int save = c.save();
            if (!c.isHardwareAccelerated()) {
                android.graphics.Path p = new android.graphics.Path();
                p.addCircle(w / 2, h / 2, r, android.graphics.Path.Direction.CW);
                c.clipPath(p);
            }
            int iw = anim.getIntrinsicWidth(), ih = anim.getIntrinsicHeight();
            float s = Math.max(w / Math.max(1, iw), h / Math.max(1, ih));
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            anim.setBounds((int) (w - dw) / 2, (int) (h - dh) / 2, (int) (w + dw) / 2, (int) (h + dh) / 2);
            anim.draw(c);
            c.restoreToCount(save);
            return;
        }
        if (bmp != null) {
            BitmapShader s = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float sc = Math.max(w / bmp.getWidth(), h / bmp.getHeight());
            Matrix m = new Matrix();
            m.setScale(sc, sc);
            m.postTranslate((w - bmp.getWidth() * sc) / 2, (h - bmp.getHeight() * sc) / 2);
            s.setLocalMatrix(m);
            im.setShader(s);
            c.drawCircle(w / 2, h / 2, r, im);
            return;
        }
        bg.setColor(color);
        c.drawCircle(w / 2, h / 2, r, bg);
        tx.setTextSize(textPx > 0 ? textPx : Math.min(w, h) * .375f);
        Paint.FontMetrics fm = tx.getFontMetrics();
        c.drawText(letter, w / 2, h / 2 - (fm.ascent + fm.descent) / 2, tx);
    }

    public void textSize(float dp) {
        textPx = T.dpf(dp);
    }
}
