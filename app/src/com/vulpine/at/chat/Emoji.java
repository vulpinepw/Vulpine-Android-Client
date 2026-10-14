package com.vulpine.at.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;
import android.view.View;
import com.vulpine.at.App;
import com.vulpine.at.ui.Img;
import com.vulpine.core.Http;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Emoji {
    public static volatile String cid;
    static final Map<String, Map<String, String>> groups = new ConcurrentHashMap<>();
    static final Map<String, Long> fetched = new ConcurrentHashMap<>();
    public static final String[] REACTIONS = {"👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "👎"};

    public static String custom(String name) {
        if ("sob".equals(name)) return "/assets/emoji-sob.png";
        if ("skully".equals(name)) return "/assets/emoji-skully.png";
        String c = cid;
        if (c == null) return null;
        Map<String, String> m = groups.get(c);
        return m == null ? null : m.get(name);
    }

    public static Map<String, String> group(String c) {
        return groups.get(c);
    }

    public static void load(String c, Runnable after) {
        Long t = fetched.get(c);
        if (t != null && System.currentTimeMillis() - t < 60000) {
            if (after != null) App.ui(after);
            return;
        }
        fetched.put(c, System.currentTimeMillis());
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations/" + c + "/emojis");
            if (!r.ok()) return;
            JSONArray a = r.obj().optJSONArray("emojis");
            Map<String, String> m = new ConcurrentHashMap<>();
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) m.put(o.optString("name"), o.optString("url"));
            }
            groups.put(c, m);
            if (after != null) App.ui(after);
        });
    }

    public static void invalidate(String c) {
        fetched.remove(c);
    }

    public static String pngUrl(String emoji) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < emoji.length()) {
            int cp = emoji.codePointAt(i);
            i += Character.charCount(cp);
            if (b.length() > 0) b.append('-');
            b.append(Integer.toHexString(cp));
        }
        return "/emoji/" + b + ".png";
    }

    public static String noFe0f(String emoji) {
        return emoji.replace("️", "");
    }

    public static String lottieUrl(String emoji) {
        StringBuilder b = new StringBuilder();
        String e = noFe0f(emoji);
        int i = 0;
        while (i < e.length()) {
            int cp = e.codePointAt(i);
            i += Character.charCount(cp);
            if (b.length() > 0) b.append('_');
            b.append(Integer.toHexString(cp));
        }
        return "/assets/anemoji/" + b + ".json";
    }

    public static class ImgSpan extends ReplacementSpan {
        final View host;
        final String url;
        final float em;
        Bitmap bmp;
        boolean requested;

        public ImgSpan(View host, String url, float em) {
            this.host = host;
            this.url = url;
            this.em = em;
        }

        public int getSize(Paint p, CharSequence t, int s, int e, Paint.FontMetricsInt fm) {
            int sz = Math.round(p.getTextSize() * em);
            if (fm != null) {
                Paint.FontMetricsInt pf = p.getFontMetricsInt();
                fm.ascent = pf.ascent - (sz - (pf.descent - pf.ascent)) / 2 - 1;
                fm.top = fm.ascent;
                fm.descent = fm.ascent + sz;
                fm.bottom = fm.descent;
            }
            return sz + Math.round(p.getTextSize() * .16f);
        }

        public void draw(Canvas c, CharSequence t, int s, int e, float x, int top, int y, int bottom, Paint p) {
            int sz = Math.round(p.getTextSize() * em);
            if (bmp == null && !requested) {
                requested = true;
                Img.get(url, sz * 2, false, (d, b) -> {
                    bmp = b;
                    if (host != null) {
                        host.invalidate();
                        if (host instanceof android.widget.TextView) ((android.widget.TextView) host).setText(((android.widget.TextView) host).getText());
                    }
                });
            }
            if (bmp == null) return;
            float cy = (top + bottom) / 2f;
            float ox = x + p.getTextSize() * .08f;
            c.drawBitmap(bmp, null, new RectF(ox, cy - sz / 2f, ox + sz, cy + sz / 2f), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        }
    }
}
