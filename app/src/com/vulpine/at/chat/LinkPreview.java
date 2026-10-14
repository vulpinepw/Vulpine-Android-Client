package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public final class LinkPreview {
    static final Map<String, Object> cache = new ConcurrentHashMap<>();
    static final Object NONE = new Object();

    static String pick(List<String> urls, String text) {
        if (text == null || text.length() > 8000 || text.startsWith("📎|") || text.startsWith("»|")) return null;
        for (String u : urls) {
            if (u.matches("(?i)^https?://(?:www\\.)?vulpine\\.(?:pw|at)/(?:join|call|app)\\b.*")) continue;
            if (u.matches("(?i).*\\.(?:gif|png|jpe?g|webp|avif|bmp|svg|mp4|webm|mov|mp3|ogg|wav|m4a|pdf|zip|apk|exe)(?:[?#].*)?$")) continue;
            if (u.replaceFirst("^https?://", "").matches("(?i)^(?:[^/]*\\.)?giphy\\.com/.*")) continue;
            return u;
        }
        return null;
    }

    static View card(ChatScreen s, String url) {
        Context c = s.a;
        FrameLayout host = new FrameLayout(c);
        host.setLayoutParams(W.lpm(W.WC, W.WC, 0, 0, 0, 0));
        Object hit = cache.get(url);
        if (hit instanceof JSONObject) fill(s, host, url, (JSONObject) hit);
        else if (hit == null) App.bg(() -> {
            Http.Resp r;
            try {
                r = Http.get("/link-preview?url=" + URLEncoder.encode(url, "UTF-8"));
            } catch (Exception e) {
                return;
            }
            JSONObject d = r.ok() ? r.obj().optJSONObject("preview") : null;
            cache.put(url, d == null ? NONE : d);
            if (d != null) App.ui(() -> {
                fill(s, host, url, d);
                host.setAlpha(0);
                host.setTranslationY(T.dp(3));
                host.animate().alpha(1).translationY(0).setDuration(250).start();
            });
        });
        return host;
    }

    static String str(JSONObject d, String k) {
        return d.isNull(k) ? "" : d.optString(k, "").trim();
    }

    static void fill(ChatScreen s, FrameLayout host, String url, JSONObject d) {
        Context c = s.a;
        boolean big = d.optBoolean("large");
        LinearLayout card = big ? W.col(c) : W.row(c);
        card.setGravity(big ? Gravity.START : Gravity.TOP);
        card.setPadding(T.dp(10), T.dp(8), T.dp(10), T.dp(8));
        card.setBackground(W.ripple(W.round(T.mixA(T.text, .07f), 12), 12));
        LinearLayout txt = W.col(c);
        String site = str(d, "siteName"), title = str(d, "title"), desc = str(d, "description");
        if (!site.isEmpty()) {
            TextView t = W.text(c, site, 13, 700, T.accent);
            W.ellipsize(t);
            txt.addView(t);
        }
        if (!title.isEmpty()) {
            TextView t = W.text(c, title, 14, 600, T.text);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setLineSpacing(0, 1.15f);
            txt.addView(t, W.lpm(W.WC, W.WC, 0, 2, 0, 0));
        }
        if (!desc.isEmpty()) {
            TextView t = W.text(c, desc, 13, 400, T.textSecondary);
            t.setMaxLines(3);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setLineSpacing(0, 1.2f);
            txt.addView(t, W.lpm(W.WC, W.WC, 0, 2, 0, 0));
        }
        String img = d.isNull("image") ? "" : d.optString("image", "");
        if (big) {
            card.addView(txt);
            if (!img.isEmpty()) {
                Media.RoundImage iv = new Media.RoundImage(c);
                iv.corners(8, 8, 8, 8);
                iv.setAdjustViewBounds(true);
                iv.setMaxHeight(T.dp(220));
                Img.into(iv, img.startsWith("http") ? img : "/api" + img, T.dp(340));
                card.addView(iv, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
            }
        } else {
            card.addView(txt, W.lp(0, W.WC, 1));
            if (!img.isEmpty()) {
                Media.RoundImage iv = new Media.RoundImage(c);
                iv.corners(8, 8, 8, 8);
                Img.into(iv, img.startsWith("http") ? img : "/api" + img, T.dp(128));
                card.addView(iv, W.lpm(T.dp(64), T.dp(64), 10, 0, 0, 0));
            }
        }
        card.setOnClickListener(v -> s.links.url(url, true));
        host.removeAllViews();
        host.addView(card, new FrameLayout.LayoutParams(W.MP, W.WC));
        LinearLayout.LayoutParams lp = W.lpm(W.WC, W.WC, 0, 6, 0, 2);
        host.setLayoutParams(lp);
        card.setLayoutParams(new FrameLayout.LayoutParams(W.MP, W.WC));
        for (int i = 0; i < txt.getChildCount(); i++) ((TextView) txt.getChildAt(i)).setMaxWidth(T.dp(big ? 320 : 246));
    }
}
