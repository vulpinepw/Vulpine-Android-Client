package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Links {
    static final Pattern INTERNAL = Pattern.compile("(?i)^https?://(?:www\\.)?vulpine\\.(?:pw|at)/(join|call)/([A-Za-z0-9_-]{2,64})");

    public static void open(MainActivity a, String url) {
        Matcher m = INTERNAL.matcher(url);
        if (m.find()) {
            if ("join".equalsIgnoreCase(m.group(1))) com.vulpine.at.groups.Join.openToken(a, m.group(2));
            else com.vulpine.at.home.Links2.open(url);
            return;
        }
        confirm(a, url, null);
    }

    public static void confirm(MainActivity a, String url, String shownText) {
        Context c = a;
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception e) {
            host = url;
        }
        if (host == null) host = url;
        int line = T.dark ? 0x14ffffff : 0x14000000;
        android.widget.FrameLayout mask = new android.widget.FrameLayout(c);
        mask.setBackgroundColor(0x80000000);
        mask.setClickable(true);
        mask.setElevation(T.dpf(60));
        LinearLayout card = W.col(c);
        card.setClickable(true);
        card.setPadding(T.dp(18), T.dp(18), T.dp(18), T.dp(14));
        card.setBackground(W.round(T.surface, 16, line, 1));
        card.setElevation(T.dpf(18));
        TextView t = W.text(c, "Open link?", 16, 700, T.text);
        T.lh(t, 24);
        card.addView(t);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("This link leaves Vulpine and opens ");
        int st = sb.length();
        sb.append(host);
        sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), st, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(T.text), st, sb.length(), 0);
        sb.append(".");
        TextView d = W.text(c, "", 13, 400, T.textSecondary);
        d.setText(sb);
        T.lh(d, 18.85f);
        card.addView(d, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        TextView u = W.text(c, url, 12.5f, 400, T.text);
        u.setTypeface(Typeface.MONOSPACE);
        u.setPadding(T.dp(10), T.dp(8), T.dp(10), T.dp(8));
        u.setBackground(W.round(T.surface2, 10, line, 1));
        card.addView(u, W.lpm(W.MP, W.WC, 0, 9, 0, 0));
        if (shownText != null && shownText.matches("(?i)^(https?://)?[a-z0-9.-]+\\.[a-z]{2,}.*") && !shownText.toLowerCase().contains(host.toLowerCase())) {
            TextView w = W.text(c, "The link text shows " + shownText + " but it opens a different site.", 13, 500, T.danger);
            card.addView(w, W.lpm(W.MP, W.WC, 0, 10, 0, 0));
        }
        Runnable[] close = new Runnable[1];
        close[0] = () -> {
            Ov.remove(mask);
            mask.animate().alpha(0).setDuration(160).withEndAction(() -> {
                if (mask.getParent() != null) ((android.view.ViewGroup) mask.getParent()).removeView(mask);
            }).start();
        };
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.END);
        TextView cancel = W.text(c, "Cancel", 14, 600, T.text);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(T.dp(16), 0, T.dp(16), 0);
        cancel.setMinWidth(T.dp(88));
        cancel.setBackground(W.ripple(W.round(T.surface2, 10, line, 1), 10));
        cancel.setOnClickListener(v -> close[0].run());
        TextView ok = W.text(c, "Open", 14, 600, T.accentFg);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(T.dp(16), 0, T.dp(16), 0);
        ok.setMinWidth(T.dp(88));
        ok.setBackground(W.ripple(W.round(T.accent, 10), 10));
        ok.setOnClickListener(v -> {
            close[0].run();
            Download.openExternal(a, url);
        });
        r.addView(cancel, W.lp(W.WC, T.dp(40)));
        r.addView(ok, W.lpm(W.WC, T.dp(40), 8, 0, 0, 0));
        card.addView(r, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        mask.setOnClickListener(v -> close[0].run());
        android.widget.FrameLayout.LayoutParams cp = new android.widget.FrameLayout.LayoutParams(W.MP, W.WC, Gravity.CENTER);
        cp.leftMargin = cp.rightMargin = T.dp(16);
        mask.addView(card, cp);
        a.host.addView(mask, new android.widget.FrameLayout.LayoutParams(W.MP, W.MP));
        Ov.push(mask, close[0]);
        mask.setAlpha(0);
        mask.animate().alpha(1).setDuration(200).start();
        card.setScaleX(.96f);
        card.setScaleY(.96f);
        card.animate().scaleX(1).scaleY(1).setDuration(240).setInterpolator(T.POP).start();
    }
}
