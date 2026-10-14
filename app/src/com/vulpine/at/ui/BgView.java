package com.vulpine.at.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import com.vulpine.at.App;
import com.vulpine.core.Session;
import org.json.JSONObject;

public class BgView extends View {
    static Bitmap mask;
    static int maskW;
    final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint mp = new Paint(Paint.FILTER_BITMAP_FLAG);
    boolean onlyPattern;
    float forcedOpacity = -1;
    String wallpaperOverride;
    String iconFillOverride;
    Bitmap wpImg;
    String wpImgUrl;
    public boolean showPattern = true;
    public boolean showWallpaper = true;

    public BgView(Context c, boolean patternOnly) {
        super(c);
        onlyPattern = patternOnly;
        mp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    public void patternOnly(float opacity) {
        onlyPattern = true;
        forcedOpacity = opacity;
        invalidate();
    }

    public void setGroupWallpaper(String wp, String iconFill) {
        wallpaperOverride = wp;
        iconFillOverride = iconFill;
        invalidate();
    }

    static synchronized Bitmap mask(int tileW) {
        if (mask != null && maskW == tileW) return mask;
        try {
            Bitmap src = BitmapFactory.decodeStream(App.ctx.getAssets().open("iconsback-p2.webp"));
            int th = Math.round(tileW * src.getHeight() / (float) src.getWidth());
            Bitmap sc = Bitmap.createScaledBitmap(src, tileW, th, true);
            Bitmap a = sc.extractAlpha();
            if (sc != src) sc.recycle();
            src.recycle();
            mask = a;
            maskW = tileW;
        } catch (Exception e) {
            mask = null;
        }
        return mask;
    }

    public static String wallpaper() {
        JSONObject s = Session.s;
        String w = s == null ? "" : s.optString("chatWallpaper", "");
        if (w.matches("^[0-9a-fA-F]{3,8}$")) w = "#" + w;
        return w;
    }

    public static boolean iconsOn() {
        return Session.settings().optBoolean("bgIconsEnabled", true);
    }

    public static float iconsOpacity() {
        double o = Session.settings().optDouble("bgIconsOpacity", 30);
        if (Double.isNaN(o) || o < 0 || o > 100) o = 30;
        return (float) (o / 100.0);
    }

    public static double wallpaperLum(String wp) {
        if (wp == null || wp.isEmpty()) return T.lum(T.bg);
        int[] g = Css.gradientColors(wp);
        if (g != null && g.length > 0) {
            double s = 0;
            for (int c : g) s += T.lum(c);
            return s / g.length;
        }
        Integer c = Css.color(wp);
        if (c != null) return T.lum(c);
        return .2;
    }

    public static int autoIconColor(String wp) {
        return wallpaperLum(wp) > .45 ? 0xff000000 : 0xffffffff;
    }

    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        String wp = wallpaperOverride != null ? wallpaperOverride : wallpaper();
        if (!onlyPattern) {
            c.drawColor(T.bg);
            if (showWallpaper && wp != null && !wp.isEmpty()) drawFill(c, wp, new RectF(0, 0, w, h));
        }
        boolean on = onlyPattern || iconsOn();
        if (!on || !showPattern) return;
        float op = forcedOpacity >= 0 ? forcedOpacity : iconsOpacity();
        if (op <= 0) return;
        int tileW = Math.round(T.dpf(600));
        Bitmap m = mask(tileW);
        if (m == null) return;
        int save = c.saveLayerAlpha(0, 0, w, h, Math.round(op * 255));
        JSONObject st = Session.settings();
        String fill = iconFillOverride;
        if (fill == null && !onlyPattern) {
            String c1 = st.optString("bgIconsColor", ""), c2 = st.optString("bgIconsColor2", "");
            if (!c1.isEmpty() && !c2.isEmpty()) fill = "linear-gradient(" + st.optDouble("bgIconsAngle", 135) + "deg, " + c1 + ", " + c2 + ")";
            else if (!c1.isEmpty()) fill = c1;
        }
        if (fill != null && !fill.isEmpty()) drawFill(c, fill, new RectF(0, 0, w, h));
        else c.drawColor(onlyPattern ? (T.dark ? 0xffffffff : 0xff000000) : autoIconColor(wp));
        BitmapShader bs = new BitmapShader(m, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix mx = new Matrix();
        mx.setTranslate((w - m.getWidth()) / 2f % m.getWidth(), (h - m.getHeight()) / 2f % m.getHeight());
        bs.setLocalMatrix(mx);
        mp.setShader(bs);
        c.drawRect(0, 0, w, h, mp);
        c.restoreToCount(save);
    }

    void drawFill(Canvas c, String css, RectF r) {
        int[] g = Css.gradientColors(css);
        Integer one = Css.color(css);
        if (one != null) {
            pt.setShader(null);
            pt.setColor(one);
            c.drawRect(r, pt);
            return;
        }
        if (g != null && g.length >= 2) {
            float ang = Css.gradientAngle(css);
            double rad = Math.toRadians(ang);
            float cx = r.centerX(), cy = r.centerY();
            float len = (float) (Math.abs(r.width() * Math.sin(rad)) + Math.abs(r.height() * Math.cos(rad))) / 2f;
            float dx = (float) Math.sin(rad) * len, dy = (float) -Math.cos(rad) * len;
            pt.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, g, null, Shader.TileMode.CLAMP));
            c.drawRect(r, pt);
            pt.setShader(null);
            return;
        }
        String url = urlOf(css);
        if (url != null) {
            if (!url.equals(wpImgUrl)) {
                wpImgUrl = url;
                wpImg = null;
                Img.get(com.vulpine.core.Http.abs(url), Math.max(getWidth(), getHeight()), false, (d, b) -> {
                    if (url.equals(wpImgUrl)) {
                        wpImg = b;
                        invalidate();
                    }
                });
            }
            if (wpImg != null) {
                float s = Math.max(r.width() / wpImg.getWidth(), r.height() / wpImg.getHeight());
                float dw = wpImg.getWidth() * s, dh = wpImg.getHeight() * s;
                c.drawBitmap(wpImg, null, new RectF(r.centerX() - dw / 2, r.centerY() - dh / 2, r.centerX() + dw / 2, r.centerY() + dh / 2), pt);
            }
        }
    }

    static String urlOf(String css) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("url\\(['\"]?([^'\")]+)['\"]?\\)").matcher(css);
        if (m.find()) return m.group(1);
        if (css.startsWith("/uploads/") || css.startsWith("http")) return css;
        return null;
    }
}
