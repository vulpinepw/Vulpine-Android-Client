package com.vulpine.at.deco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import com.vulpine.at.Prefs;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.T;
import org.json.JSONArray;
import org.json.JSONObject;

public class DecoCard extends View {
    public static final float CARD_W = 562;
    JSONObject user;
    final String[] ids = new String[3];
    final DecoItem[] its = new DecoItem[3];
    boolean still, thumbMode;
    Bitmap thumb, avatar, cache;
    String thumbKey, cacheKey;
    long t0 = SystemClock.uptimeMillis();
    float cardH = 220;
    StaticLayout bioL;
    float txtH;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    int fg;
    Integer bgColor;
    int[] bgGrad;
    float bgAngle;

    public DecoCard(Context c) {
        super(c);
    }

    public DecoCard set(JSONObject user, String frame, String av, String effect, boolean still) {
        this.user = user;
        this.still = still;
        String[] n = {frame, av, effect};
        for (int i = 0; i < 3; i++) {
            if (n[i] == null ? ids[i] != null : !n[i].equals(ids[i])) {
                ids[i] = n[i];
                its[i] = null;
                if (n[i] != null) {
                    final int k = i;
                    final String id = n[i];
                    DecoItem c = Deco.peekItem(id);
                    if (c != null) its[k] = c;
                    else Deco.item(id, x -> {
                        if (id.equals(ids[k])) {
                            its[k] = x;
                            cache = null;
                            invalidate();
                        }
                    });
                }
            }
        }
        cache = null;
        bgColor = null;
        bgGrad = null;
        fg = T.text;
        avatar = null;
        if (user != null) {
            String bg = user.optString("profileBackground", "");
            if (!bg.isEmpty()) {
                bgGrad = Css.gradientColors(bg);
                if (bgGrad != null && bgGrad.length > 0) bgAngle = Css.gradientAngle(bg);
                else {
                    bgGrad = null;
                    bgColor = Css.color(bg);
                }
                fg = textOn(bg);
            }
            String au = user.optString("avatarUrl", "");
            if (!au.isEmpty() && !"null".equals(au)) Img.get(au, 320, false, (d, b) -> {
                this.avatar = b;
                cache = null;
                invalidate();
            });
        }
        thumb = null;
        thumbKey = null;
        t0 = SystemClock.uptimeMillis();
        requestLayout();
        invalidate();
        return this;
    }

    public DecoCard thumb(boolean on) {
        thumbMode = on;
        if (on && user == null) {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (String s : ids) if (s != null) l.add(s);
            java.util.Collections.sort(l);
            String key = TextUtils.join("+", l);
            if (!key.isEmpty() && !key.equals(thumbKey)) {
                thumbKey = key;
                String url = "/assets/deco/thumb/" + key + ".webp?v=5";
                Img.get(url, 1024, false, (d, b) -> {
                    if (key.equals(thumbKey) && b != null) {
                        thumb = b;
                        invalidate();
                    }
                });
            }
        }
        return this;
    }

    static int textOn(String bg) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(bg);
        double t = 0;
        int n = 0;
        while (m.find()) {
            int c = Integer.parseInt(m.group().substring(1), 16);
            t += (.299 * ((c >> 16) & 255) + .587 * ((c >> 8) & 255) + .114 * (c & 255)) / 255;
            n++;
        }
        if (n == 0) return 0xffffffff;
        return t / n > .55 ? 0xff1a1a1a : 0xffffffff;
    }

    boolean hasFrame() {
        return ids[0] != null;
    }

    float stageW() {
        return CARD_W + (hasFrame() ? 112 : 56);
    }

    float padTop() {
        return hasFrame() ? 76 : 28;
    }

    float padBottom() {
        return hasFrame() ? 40 : 28;
    }

    void layoutCard() {
        float colW = (CARD_W - 40) * .4f - 8;
        float txtW = CARD_W - 40 - colW - 16;
        txtH = 60;
        bioL = null;
        if (user != null) {
            float h = 24 + 21;
            String bio = user.optString("bio", "");
            if (!bio.isEmpty()) {
                bio = bio.replaceAll("\\*\\*|__|~~|\\|\\||`", "").replaceAll("(^|\\s)[*_]+|[*_]+(\\s|$)", "$1$2");
                tp.setTypeface(T.r);
                tp.setTextSize(14);
                bioL = StaticLayout.Builder.obtain(bio, 0, bio.length(), tp, Math.max(10, (int) txtW)).setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(21 - 14 * 1.21f, 1f).setIncludePad(false).build();
                h += 16 + bioL.getLineCount() * 21;
            }
            h += 16 + 19.5f;
            txtH = h;
        }
        cardH = 24 + Math.max(104, txtH) + 16 + 16 + 40 + 20;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        layoutCard();
        int w = MeasureSpec.getSize(ws);
        if (w <= 0) w = T.dp(300);
        float zoom = zoomFor(w);
        float sh = padTop() + cardH + padBottom();
        setMeasuredDimension(w, Math.round(sh * zoom * T.density));
    }

    float zoomFor(int wPx) {
        float k = Math.min(1f, wPx / T.density / stageW());
        return (float) Math.floor(k * 1000) / 1000f;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        boolean live = !still && !Prefs.powerSaving();
        if (!live && thumbMode && thumb != null) {
            drawCard(c, w, -1);
            p.setAlpha(255);
            rf.set(0, 0, w, h);
            c.drawBitmap(thumb, null, rf, p);
            return;
        }
        if (!live) {
            String key = w + "x" + h + (its[0] != null) + (its[1] != null) + (its[2] != null) + (avatar != null);
            if (cache == null || !key.equals(cacheKey)) {
                cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                drawCard(new Canvas(cache), w, 0);
                cacheKey = key;
            }
            c.drawBitmap(cache, 0, 0, null);
            return;
        }
        drawCard(c, w, SystemClock.uptimeMillis() - t0);
        postInvalidateDelayed(DecoView.FRAME_MS);
    }

    void drawCard(Canvas c, int w, float t) {
        float d = T.density;
        float zoom = zoomFor(w);
        float sw = stageW() * zoom * d;
        int sv = c.save();
        c.translate((w - sw) / 2f, 0);
        c.scale(zoom * d, zoom * d);
        float px = hasFrame() ? 56 : 28, py = padTop();
        c.translate(px, py);
        rf.set(0, 0, CARD_W, cardH);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0);
        p.setShadowLayer(25, 0, 20, 0x59000000);
        p.setColor(T.surface);
        c.drawRoundRect(rf, 16, 16, p);
        p.clearShadowLayer();
        if (bgGrad != null) {
            double a = Math.toRadians(bgAngle);
            float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
            float len = Math.abs(CARD_W * dx) + Math.abs(cardH * dy);
            float cx = CARD_W / 2, cy = cardH / 2;
            p.setShader(new LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2, bgGrad.length == 1 ? new int[]{bgGrad[0], bgGrad[0]} : bgGrad, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(rf, 16, 16, p);
            p.setShader(null);
        } else if (bgColor != null) {
            p.setColor(bgColor);
            c.drawRoundRect(rf, 16, 16, p);
        }
        float colW = (CARD_W - 40) * .4f - 8;
        float bodyH = Math.max(104, txtH);
        float avX = 20 + (colW - 104) / 2, avY = 24 + (bodyH - 104) / 2;
        drawAvatar(c, avX, avY);
        float tx = 20 + colW + 16, ty = 24 + (bodyH - txtH) / 2;
        if (user == null) {
            float tw = CARD_W - 40 - colW - 16;
            p.setColor(T.mixA(T.text, .14f));
            bar(c, tx, ty, tw * .7f, 14, 1);
            bar(c, tx, ty + 24, tw * .48f, 11, .75f);
            bar(c, tx, ty + 51, tw * .62f, 9, .5f);
        } else drawText(c, tx, ty);
        float by = 24 + bodyH + 16 + 16;
        rf.set(20, by, CARD_W - 20, by + 40);
        p.setColor(user == null ? T.mixA(T.text, .12f) : T.accent);
        c.drawRoundRect(rf, 20, 20, p);
        if (user != null) {
            tp.setTypeface(T.sb);
            tp.setTextSize(14);
            tp.setColor(T.accentFg);
            tp.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = tp.getFontMetrics();
            c.drawText("Add friend", CARD_W / 2, by + 20 - (fm.ascent + fm.descent) / 2, tp);
            tp.setTextAlign(Paint.Align.LEFT);
        }
        if (t >= 0) {
            if (its[2] != null) its[2].draw(c, t, CARD_W, cardH, 0, 0, 1);
            if (its[1] != null) its[1].draw(c, t, 104, 104, avX, avY, 1);
            if (its[0] != null) its[0].draw(c, t, CARD_W, cardH, 0, 0, 1);
        }
        c.restoreToCount(sv);
    }

    void bar(Canvas c, float x, float y, float w, float h, float a) {
        int col = T.mixA(T.text, .14f * a);
        p.setColor(col);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, 7, 7, p);
    }

    void drawAvatar(Canvas c, float x, float y) {
        if (user == null) {
            p.setColor(T.mixA(T.text, .16f));
            c.drawCircle(x + 52, y + 52, 52, p);
            return;
        }
        if (avatar != null) {
            BitmapShader s = new BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float sc = Math.max(104f / avatar.getWidth(), 104f / avatar.getHeight());
            Matrix m = new Matrix();
            m.setScale(sc, sc);
            m.postTranslate(x + (104 - avatar.getWidth() * sc) / 2, y + (104 - avatar.getHeight() * sc) / 2);
            s.setLocalMatrix(m);
            p.setShader(s);
            c.drawCircle(x + 52, y + 52, 52, p);
            p.setShader(null);
            return;
        }
        String name = user.optString("displayName", "");
        if (name.isEmpty()) name = user.optString("handle", "");
        String ac = user.optString("avatarColor", "");
        p.setColor(ac.isEmpty() ? T.accent : AvatarView.colorFor(name, ac));
        c.drawCircle(x + 52, y + 52, 52, p);
        String n = name.trim();
        String letter = n.isEmpty() ? "?" : new String(Character.toChars(Character.toUpperCase(n.codePointAt(0))));
        tp.setTypeface(T.sb);
        tp.setTextSize(34);
        tp.setColor(Color.WHITE);
        tp.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = tp.getFontMetrics();
        c.drawText(letter, x + 52, y + 52 - (fm.ascent + fm.descent) / 2, tp);
        tp.setTextAlign(Paint.Align.LEFT);
    }

    void drawText(Canvas c, float x, float y) {
        float colW = (CARD_W - 40) * .4f - 8;
        float tw = CARD_W - 40 - colW - 16;
        String name = user.optString("displayName", "");
        if (name.isEmpty()) name = user.optString("handle", "");
        tp.setTypeface(T.b);
        tp.setTextSize(16);
        tp.setColor(fg);
        line(c, name, x, y, 24, tw);
        JSONArray hs = user.optJSONArray("handles");
        StringBuilder b = new StringBuilder();
        if (hs != null && hs.length() > 0) for (int i = 0; i < hs.length(); i++) b.append(i > 0 ? " " : "").append("@").append(hs.optString(i));
        else if (!user.optString("handle").isEmpty()) b.append("@").append(user.optString("handle"));
        tp.setTypeface(T.r);
        tp.setTextSize(14);
        tp.setColor(T.mixA(fg, .8f * Color.alpha(fg) / 255f));
        line(c, b.toString(), x, y + 24, 21, tw);
        float yy = y + 45;
        if (bioL != null) {
            yy += 16;
            int sv = c.save();
            c.translate(x, yy);
            bioL.getPaint().setColor(T.mixA(fg, .9f));
            bioL.draw(c);
            c.restoreToCount(sv);
            yy += bioL.getLineCount() * 21;
        }
        yy += 16;
        tp.setTypeface(T.r);
        tp.setTextSize(13);
        tp.setColor(T.mixA(fg, .7f));
        line(c, "End-to-end encrypted contact", x, yy, 19.5f, tw);
    }

    void line(Canvas c, String s, float x, float y, float lh, float maxW) {
        CharSequence e = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END);
        Paint.FontMetrics fm = tp.getFontMetrics();
        c.drawText(e, 0, e.length(), x, y + lh / 2 - (fm.ascent + fm.descent) / 2, tp);
    }
}
